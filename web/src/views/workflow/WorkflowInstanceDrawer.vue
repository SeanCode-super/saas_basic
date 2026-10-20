<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Check, Close, Right, Share } from "@element-plus/icons-vue";
import { cancelWorkflowInstance, getWorkflowInstanceDetail, handleWorkflowTask } from "@/api/modules/workflow";
import type { FormFieldConfig, ProcessInstanceDetail, WorkflowTaskRow } from "@/api/modules/workflow";
import { useAuthStore } from "@/stores/modules/auth";

const props = defineProps<{
  visible: boolean;
  instanceId?: number | null;
}>();

const emit = defineEmits<{
  "update:visible": [boolean];
  refresh: [];
}>();

const authStore = useAuthStore();
const currentUserId = computed(() => Number(authStore.currentUser?.userId ?? 0));

const loading = ref(false);
const submitting = ref(false);
const detail = ref<ProcessInstanceDetail | null>(null);

const handleForm = ref({
  action: "AGREE" as "AGREE" | "REJECT" | "TRANSFER",
  comment: "",
  transferToUserId: undefined as number | undefined
});

// 计算当前登录人是否有该审批单的待办任务
const myPendingTask = computed<WorkflowTaskRow | undefined>(() => {
  if (!detail.value) return undefined;
  return detail.value.tasks.find(
    (t) => t.status === "PENDING" && t.nodeType === "APPROVER" && Number(t.assigneeId) === currentUserId.value
  );
});

// 计算发起人是否为本人且处于进行中
const canCancel = computed(() => {
  if (!detail.value) return false;
  const inst = detail.value.instance;
  return inst.status === "RUNNING" && Number(inst.initiatorId) === currentUserId.value;
});

// 表单字段解析
const parsedFormData = computed<Array<{ label: string; value: string }>>(() => {
  if (!detail.value || !detail.value.formDataJson) return [];
  try {
    const data = JSON.parse(detail.value.formDataJson) as Record<string, unknown>;
    const schemaStr = detail.value.definition?.formSchemaJson;
    const schema = schemaStr ? (JSON.parse(schemaStr) as FormFieldConfig[]) : [];

    return Object.entries(data).map(([key, val]) => {
      const fieldDef = schema.find((s) => s.fieldCode === key);
      const label = fieldDef ? fieldDef.fieldName : key;
      const displayVal = val === null || val === undefined || val === "" ? "--" : String(val);
      return { label, value: displayVal };
    });
  } catch {
    return [];
  }
});

watch(
  () => [props.visible, props.instanceId],
  async ([vis, id]) => {
    if (vis && id) {
      await loadDetail();
    } else {
      detail.value = null;
      handleForm.value.comment = "";
    }
  },
  { immediate: true }
);

async function loadDetail() {
  if (!props.instanceId) return;
  loading.value = true;
  try {
    detail.value = await getWorkflowInstanceDetail(props.instanceId);
  } finally {
    loading.value = false;
  }
}

async function doHandle(action: "AGREE" | "REJECT") {
  if (!myPendingTask.value) return;

  const actionText = action === "AGREE" ? "同意" : "驳回";
  if (action === "REJECT" && !handleForm.value.comment.trim()) {
    ElMessage.warning("驳回申请时，请务必填写驳回原因与说明");
    return;
  }

  try {
    await ElMessageBox.confirm(`确认【${actionText}】当前审批单吗？`, "操作确认", {
      confirmButtonText: "确认",
      cancelButtonText: "取消",
      type: action === "AGREE" ? "info" : "warning"
    });

    submitting.value = true;
    await handleWorkflowTask(myPendingTask.value.id, {
      action,
      comment: handleForm.value.comment.trim() || (action === "AGREE" ? "同意，审核通过。" : "驳回申请。")
    });

    ElMessage.success(`审批已【${actionText}】`);
    handleForm.value.comment = "";
    emit("refresh");
    await loadDetail();
  } catch {
    // cancelled
  } finally {
    submitting.value = false;
  }
}

async function handleCancel() {
  if (!detail.value) return;
  try {
    const { value: reason } = await ElMessageBox.prompt("请输入撤回该审批单的理由（选填）：", "撤回申请确认", {
      confirmButtonText: "确认撤回",
      cancelButtonText: "取消"
    });

    submitting.value = true;
    await cancelWorkflowInstance(detail.value.instance.id, reason);
    ElMessage.success("审批单已撤回");
    emit("refresh");
    await loadDetail();
  } catch {
    // cancelled
  } finally {
    submitting.value = false;
  }
}

function resolveStatusType(status: string): "primary" | "success" | "warning" | "danger" | "info" {
  switch (status) {
    case "APPROVED":
      return "success";
    case "RUNNING":
      return "primary";
    case "REJECTED":
      return "danger";
    case "CANCELLED":
      return "info";
    default:
      return "info";
  }
}

function resolveStatusLabel(status: string): string {
  switch (status) {
    case "APPROVED":
      return "已审批通过";
    case "RUNNING":
      return "流转审批中";
    case "REJECTED":
      return "已驳回";
    case "CANCELLED":
      return "已撤回";
    default:
      return status;
  }
}

function resolveActionType(action: string): "primary" | "success" | "warning" | "danger" | "info" {
  switch (action) {
    case "SUBMIT":
      return "primary";
    case "AGREE":
    case "END":
      return "success";
    case "REJECT":
      return "danger";
    case "CANCEL":
      return "info";
    default:
      return "primary";
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="审批单据详情与流转记录"
    size="680px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading" class="detail-container">
      <template v-if="detail">
        <!-- 1. 顶部单据基本信息卡片 -->
        <div class="instance-hero">
          <div class="hero-top">
            <div class="hero-title-box">
              <h3>{{ detail.instance.title }}</h3>
              <span class="hero-no">{{ detail.instance.instanceNo }}</span>
            </div>
            <el-tag :type="resolveStatusType(detail.instance.status)" size="large" effect="dark">
              {{ resolveStatusLabel(detail.instance.status) }}
            </el-tag>
          </div>

          <div class="hero-meta-grid">
            <div class="meta-item">
              <span>发起人</span>
              <strong>{{ detail.instance.initiatorName }}</strong>
            </div>
            <div class="meta-item">
              <span>归属部门</span>
              <strong>{{ detail.instance.initiatorDeptName || '默认业务部' }}</strong>
            </div>
            <div class="meta-item">
              <span>所属业务流程</span>
              <strong>{{ detail.instance.processName }}</strong>
            </div>
            <div class="meta-item">
              <span>发起时间</span>
              <strong class="time-text">{{ detail.instance.startTime }}</strong>
            </div>
          </div>
        </div>

        <!-- 2. 业务表单数据明细卡片 -->
        <div class="section-card">
          <div class="section-card__header">
            <h4>单据填报明细</h4>
            <span>发起人真实填报数据</span>
          </div>
          <div class="form-values-grid">
            <div v-for="item in parsedFormData" :key="item.label" class="form-val-item">
              <span class="val-label">{{ item.label }}</span>
              <span class="val-content">{{ item.value }}</span>
            </div>
          </div>
        </div>

        <!-- 3. 流转时间轴与审批记录 -->
        <div class="section-card">
          <div class="section-card__header">
            <h4>流转进度与审批时光轴</h4>
            <span>全链路审计留痕</span>
          </div>

          <div class="timeline-wrapper">
            <el-timeline>
              <el-timeline-item
                v-for="log in detail.logs"
                :key="log.id"
                :type="resolveActionType(log.action)"
                size="large"
                :timestamp="log.createdAt"
              >
                <div class="timeline-node">
                  <div class="node-head">
                    <strong class="operator-name">{{ log.operatorName }}</strong>
                    <el-tag size="small" :type="resolveActionType(log.action)" effect="plain">
                      {{ log.actionLabel }}
                    </el-tag>
                    <span class="node-badge">环节: {{ log.nodeName }}</span>
                  </div>
                  <p v-if="log.comment" class="node-comment">
                    “{{ log.comment }}”
                  </p>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </div>

        <!-- 4. 当前待办办理操作区（如果当前人是审批人） -->
        <div v-if="myPendingTask" class="action-footer-panel">
          <div class="panel-header">
            <div class="panel-tag">待您审批</div>
            <span>当前审批环节：<strong>{{ myPendingTask.nodeName }}</strong></span>
          </div>

          <el-input
            v-model="handleForm.comment"
            type="textarea"
            :rows="2"
            placeholder="请在此输入您的审批意见（如：同意，按流程执行。驳回请说明理由）"
          />

          <div class="panel-actions">
            <el-button
              type="danger"
              plain
              :icon="Close"
              :loading="submitting"
              @click="doHandle('REJECT')"
            >
              驳回申请
            </el-button>
            <el-button
              type="success"
              :icon="Check"
              :loading="submitting"
              @click="doHandle('AGREE')"
            >
              同意审批
            </el-button>
          </div>
        </div>

        <!-- 5. 发起人撤回操作条 -->
        <div v-else-if="canCancel" class="action-footer-panel cancel-only">
          <div class="panel-header">
            <span>您是本审批单的发起人，在审批流转完成前可随时撤回。</span>
          </div>
          <el-button type="info" plain :loading="submitting" @click="handleCancel">
            撤回审批单
          </el-button>
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.detail-container {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 200px;
}

.instance-hero {
  padding: 16px 20px;
  border-radius: var(--sb-radius-md);
  background: #f8fafc;
  border: 1px solid #e2e8f0;

  .hero-top {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 14px;

    .hero-title-box {
      h3 {
        margin: 0 0 4px;
        font-size: 17px;
        font-weight: 600;
        color: #0f172a;
      }

      .hero-no {
        font-size: 12px;
        color: #64748b;
        font-family: var(--sb-font-mono, monospace);
      }
    }
  }

  .hero-meta-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    padding-top: 12px;
    border-top: 1px solid #e2e8f0;

    .meta-item {
      span {
        display: block;
        font-size: 12px;
        color: #64748b;
        margin-bottom: 2px;
      }

      strong {
        font-size: 13px;
        color: #1e293b;
      }

      .time-text {
        font-size: 12px;
        color: #475569;
      }
    }
  }
}

.section-card {
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-md);
  background: #fff;
  padding: 16px;

  &__header {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    margin-bottom: 14px;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--sb-border-color);

    h4 {
      margin: 0;
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;
    }

    span {
      font-size: 12px;
      color: #64748b;
    }
  }
}

.form-values-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;

  .form-val-item {
    background: #f8fafc;
    padding: 10px 14px;
    border-radius: 6px;
    border: 1px solid #edf2f7;

    .val-label {
      display: block;
      font-size: 12px;
      color: #64748b;
      margin-bottom: 3px;
    }

    .val-content {
      font-size: 13px;
      font-weight: 500;
      color: #0f172a;
      white-space: pre-wrap;
    }
  }
}

.timeline-wrapper {
  padding: 6px 10px 0;

  .timeline-node {
    .node-head {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 4px;

      .operator-name {
        font-size: 13px;
        color: #0f172a;
      }

      .node-badge {
        font-size: 12px;
        color: #64748b;
        background: #f1f5f9;
        padding: 2px 6px;
        border-radius: 4px;
      }
    }

    .node-comment {
      margin: 4px 0 0;
      padding: 6px 12px;
      border-radius: 4px;
      background: #f8fafc;
      border-left: 3px solid #cbd5e1;
      font-size: 13px;
      color: #334155;
      line-height: 1.4;
    }
  }
}

.action-footer-panel {
  position: sticky;
  bottom: 0;
  background: #fff;
  padding: 16px;
  border-radius: var(--sb-radius-md);
  border: 1px solid #cbd5e1;
  box-shadow: 0 4px 12px rgb(0 0 0 / 0.08);
  display: flex;
  flex-direction: column;
  gap: 12px;

  .panel-header {
    display: flex;
    align-items: center;
    gap: 10px;

    .panel-tag {
      padding: 2px 8px;
      border-radius: 4px;
      background: #eff6ff;
      color: #2563eb;
      font-size: 12px;
      font-weight: 600;
    }

    span {
      font-size: 13px;
      color: #334155;
    }
  }

  .panel-actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }

  &.cancel-only {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
}
</style>
