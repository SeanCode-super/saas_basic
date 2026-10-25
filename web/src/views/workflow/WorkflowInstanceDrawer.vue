<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Check, Close, Back, UserFilled, Bell, RefreshLeft, Share, Plus, Right } from "@element-plus/icons-vue";
import {
  cancelWorkflowInstance,
  getWorkflowInstanceDetail,
  handleWorkflowTask,
  urgeWorkflowInstance,
  fetchRollbackNodes
} from "@/api/modules/workflow";
import type { FormFieldConfig, ProcessInstanceDetail, WorkflowTaskRow, RollbackTargetNode } from "@/api/modules/workflow";
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

// 自由回退弹窗
const rollbackDialogVisible = ref(false);
const rollbackTargetNodes = ref<RollbackTargetNode[]>([]);
const selectedRollbackNode = ref<string>("");
const rollbackComment = ref("");

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

// 是否可以催办：单据运行中，且当前登录人是发起人或者顶级管理员
const canUrge = computed(() => {
  if (!detail.value) return false;
  const inst = detail.value.instance;
  if (inst.status !== "RUNNING") return false;
  return Number(inst.initiatorId) === currentUserId.value || authStore.currentUser?.userType === "SUPER_ADMIN" || authStore.hasPermission("workflow:instance:write");
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

// 审批流转轨迹节点 (Execution Trace)
interface TraceNode {
  name: string;
  type: string;
  status: "COMPLETED" | "RUNNING" | "REJECTED" | "ROLLBACK" | "PENDING";
  stateText: string;
}

const flowTraceNodes = computed<TraceNode[]>(() => {
  if (!detail.value) return [];
  const nodes: TraceNode[] = [];
  const inst = detail.value.instance;
  const tasks = detail.value.tasks || [];

  // 1. 发起节点
  nodes.push({
    name: `发起申请 (${inst.initiatorName})`,
    type: "START",
    status: "COMPLETED",
    stateText: "已提交"
  });

  // 汇总所有审批阶段
  const seenNodes = new Set<string>();
  tasks.forEach((t) => {
    if (t.nodeName && !seenNodes.has(t.nodeName) && t.nodeType === "APPROVER") {
      seenNodes.add(t.nodeName);
      let status: "COMPLETED" | "RUNNING" | "REJECTED" | "ROLLBACK" | "PENDING" = "PENDING";
      let stateText = "待处理";
      if (t.status === "COMPLETED") {
        status = "COMPLETED";
        stateText = "已同意";
      } else if (t.status === "PENDING") {
        status = "RUNNING";
        stateText = "审批中";
      } else if (t.status === "REJECTED") {
        status = "REJECTED";
        stateText = "已驳回";
      } else if (t.status === "ROLLBACK") {
        status = "ROLLBACK";
        stateText = "已退回";
      } else if (t.status === "TRANSFERRED") {
        status = "COMPLETED";
        stateText = "已转办";
      }
      nodes.push({
        name: t.nodeName,
        type: t.nodeType,
        status,
        stateText: `${t.assigneeName} (${stateText})`
      });
    }
  });

  if (seenNodes.size === 0 && inst.currentNodeName) {
    nodes.push({
      name: inst.currentNodeName,
      type: "APPROVER",
      status: inst.status === "RUNNING" ? "RUNNING" : "COMPLETED",
      stateText: inst.status === "RUNNING" ? "审批中" : "已完成"
    });
  }

  // 办结节点
  let endStatus: "COMPLETED" | "RUNNING" | "REJECTED" | "ROLLBACK" | "PENDING" = "PENDING";
  let endText = "待办结";
  if (inst.status === "APPROVED") {
    endStatus = "COMPLETED";
    endText = "已通过办结";
  } else if (inst.status === "REJECTED") {
    endStatus = "REJECTED";
    endText = "流程终止";
  } else if (inst.status === "CANCELLED") {
    endStatus = "ROLLBACK";
    endText = "发起人撤回";
  }

  nodes.push({
    name: "流程办结",
    type: "END",
    status: endStatus,
    stateText: endText
  });

  return nodes;
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

async function openRollbackPicker() {
  if (!myPendingTask.value || !props.instanceId) return;
  submitting.value = true;
  try {
    const nodes = await fetchRollbackNodes(props.instanceId);
    rollbackTargetNodes.value = nodes;
    selectedRollbackNode.value = nodes.length > 0 ? nodes[nodes.length - 1].nodeName : "上一个审批环节";
    rollbackComment.value = handleForm.value.comment.trim();
    rollbackDialogVisible.value = true;
  } catch (e: any) {
    ElMessage.error(e?.message || "获取历史节点失败");
  } finally {
    submitting.value = false;
  }
}

async function confirmRollbackTarget() {
  if (!myPendingTask.value) return;
  if (!selectedRollbackNode.value) {
    ElMessage.warning("请选择退回的目标历史环节");
    return;
  }
  if (!rollbackComment.value.trim()) {
    ElMessage.warning("退回修改时，请务必填写退回原因与修改指示");
    return;
  }

  submitting.value = true;
  try {
    const isRoot = selectedRollbackNode.value.includes("发起环节") || selectedRollbackNode.value.includes("root");
    await handleWorkflowTask(myPendingTask.value.id, {
      action: isRoot ? "ROLLBACK_INITIATOR" : "ROLLBACK_TARGET",
      targetNodeName: selectedRollbackNode.value,
      comment: rollbackComment.value.trim()
    });
    ElMessage.success(`已成功退回至目标环节「${selectedRollbackNode.value}」！`);
    rollbackDialogVisible.value = false;
    handleForm.value.comment = "";
    emit("refresh");
    await loadDetail();
  } finally {
    submitting.value = false;
  }
}

async function handleDelegate() {
  if (!myPendingTask.value) return;
  try {
    const { value: delegatee } = await ElMessageBox.prompt("请输入受托人姓名（审结后交回原审批人）：", "审批任务委派 (Delegate)", {
      confirmButtonText: "确认委派",
      cancelButtonText: "取消",
      inputPlaceholder: "例如: 李高级工程师 / 财务李主管",
      inputValidator: (val) => Boolean(val && val.trim()) || "请填写受托人姓名"
    });
    submitting.value = true;
    await handleWorkflowTask(myPendingTask.value.id, {
      action: "DELEGATE",
      delegateUserId: 2,
      delegateUserName: delegatee.trim(),
      comment: handleForm.value.comment.trim() || `委派说明：委托同事 ${delegatee} 协助核验并出具意见。`
    });
    ElMessage.success(`审批任务已委派给「${delegatee}」！`);
    handleForm.value.comment = "";
    emit("refresh");
    await loadDetail();
  } catch {
    // cancelled
  } finally {
    submitting.value = false;
  }
}

async function handleAddSign() {
  if (!myPendingTask.value) return;
  try {
    const { value: assigneeName } = await ElMessageBox.prompt("请输入加签协办人姓名或工号：", "审批加签协办 (Add Sign)", {
      confirmButtonText: "确认加签",
      cancelButtonText: "取消",
      inputPlaceholder: "例如: 架构评审委员会 / 法务专员",
      inputValidator: (val) => Boolean(val && val.trim()) || "请填写加签协办人姓名"
    });
    submitting.value = true;
    await handleWorkflowTask(myPendingTask.value.id, {
      action: "ADD_SIGN",
      addSignUserId: 2,
      addSignUserName: assigneeName.trim(),
      addSignType: "AFTER",
      comment: handleForm.value.comment.trim() || `加签协办：邀请 ${assigneeName} 参与并联会签。`
    });
    ElMessage.success(`已成功邀请「${assigneeName}」进行加签会签！`);
    handleForm.value.comment = "";
    emit("refresh");
    await loadDetail();
  } catch {
    // cancelled
  } finally {
    submitting.value = false;
  }
}

async function handleUrge() {
  if (!detail.value) return;
  try {
    const { value: comment } = await ElMessageBox.prompt("请输入加急催办附言（将多渠道通知当前待办人）：", "一键加急催办", {
      confirmButtonText: "立即催办",
      cancelButtonText: "取消",
      inputValue: "业务推进急需核准，请各位领导优先审批，非常感谢！",
      inputPlaceholder: "催办留言"
    });
    submitting.value = true;
    await urgeWorkflowInstance(detail.value.instance.id, comment);
    ElMessage.success("催办指令已触发，已通过系统通道提醒当前待办审批人！");
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
    case "ROLLBACK_PREVIOUS":
    case "ROLLBACK_INITIATOR":
    case "ROLLBACK_TARGET":
      return "warning";
    case "ADD_SIGN":
    case "DELEGATE":
    case "DELEGATE_RESOLVE":
      return "primary";
    case "URGE":
      return "danger";
    default:
      return "primary";
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="审批单据全景详情与流转记录"
    size="720px"
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
            <div class="hero-badges">
              <el-button
                v-if="canUrge"
                type="danger"
                size="small"
                plain
                :icon="Bell"
                :loading="submitting"
                class="urge-btn"
                @click="handleUrge"
              >
                加急催办
              </el-button>
              <el-tag :type="resolveStatusType(detail.instance.status)" size="large" effect="dark">
                {{ resolveStatusLabel(detail.instance.status) }}
              </el-tag>
            </div>
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

        <!-- 2. 可视化流转轨迹高亮 (Execution Trace) -->
        <div class="section-card trace-card">
          <div class="section-card__header">
            <h4>BPMN 审批流转轨迹 (Execution Trace)</h4>
            <span class="sub-hint">节点状态实时联动：绿色已通过 · 蓝色进行中 · 橙色退回</span>
          </div>

          <div class="trace-track">
            <div
              v-for="(node, idx) in flowTraceNodes"
              :key="idx"
              class="trace-node-item"
              :class="`node-${node.status.toLowerCase()}`"
            >
              <div class="node-circle">
                <el-icon v-if="node.status === 'COMPLETED'"><Check /></el-icon>
                <el-icon v-else-if="node.status === 'RUNNING'"><UserFilled /></el-icon>
                <el-icon v-else-if="node.status === 'REJECTED'"><Close /></el-icon>
                <el-icon v-else-if="node.status === 'ROLLBACK'"><Back /></el-icon>
                <span v-else>{{ idx + 1 }}</span>
              </div>
              <div class="node-content">
                <div class="node-title">{{ node.name }}</div>
                <div class="node-state-text">{{ node.stateText }}</div>
              </div>
              <div v-if="idx < flowTraceNodes.length - 1" class="node-connector">
                <el-icon><Right /></el-icon>
              </div>
            </div>
          </div>
        </div>

        <!-- 3. 业务表单数据明细卡片 -->
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

        <!-- 4. 流转时间轴与审计记录 -->
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

        <!-- 5. 当前待办办理操作区（如果当前人是审批人） -->
        <div v-if="myPendingTask" class="action-footer-panel">
          <div class="panel-header">
            <div class="panel-tag">待您审批</div>
            <span>当前审批环节：<strong>{{ myPendingTask.nodeName }}</strong></span>
          </div>

          <el-input
            v-model="handleForm.comment"
            type="textarea"
            :rows="2"
            placeholder="请在此输入审批意见（如：同意，按流程执行。驳回或回退请注明理由）"
          />

          <div class="panel-actions">
            <!-- 自由退回至历史节点 -->
            <el-button
              type="warning"
              plain
              :icon="RefreshLeft"
              :loading="submitting"
              @click="openRollbackPicker"
            >
              退回历史环节
            </el-button>

            <!-- 委派 -->
            <el-button
              type="info"
              plain
              :icon="Share"
              :loading="submitting"
              @click="handleDelegate"
            >
              委派代审
            </el-button>

            <!-- 加签 -->
            <el-button
              type="primary"
              plain
              :icon="Plus"
              :loading="submitting"
              @click="handleAddSign"
            >
              加签会签
            </el-button>

            <!-- 驳回 -->
            <el-button
              type="danger"
              plain
              :icon="Close"
              :loading="submitting"
              @click="doHandle('REJECT')"
            >
              驳回申请
            </el-button>

            <!-- 同意 -->
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

        <!-- 6. 发起人撤回操作条 -->
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

    <!-- 自由回退选择弹窗 -->
    <el-dialog
      v-model="rollbackDialogVisible"
      title="BPMN 自由退回至历史指定环节"
      width="520px"
      append-to-body
    >
      <div class="rollback-dialog-body">
        <p class="dialog-tip">请选择您期望退回的目标审批环节，单据将重新分配给该环节的历史处理人核办：</p>
        <el-form label-position="top">
          <el-form-item label="目标历史节点" required>
            <el-radio-group v-model="selectedRollbackNode" class="rollback-node-list">
              <el-radio
                v-for="item in rollbackTargetNodes"
                :key="item.nodeId"
                :value="item.nodeName"
                class="rollback-radio-item"
              >
                <div class="radio-label-box">
                  <strong>{{ item.nodeName }}</strong>
                  <span class="sub">处理人: {{ item.assigneeName || '原申请人' }} · {{ item.time || '' }}</span>
                </div>
              </el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="退回修改指示说明" required>
            <el-input
              v-model="rollbackComment"
              type="textarea"
              :rows="3"
              placeholder="请详细说明单据退回的原因与需补正修改的内容"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-actions">
          <el-button @click="rollbackDialogVisible = false">取消</el-button>
          <el-button type="warning" :loading="submitting" @click="confirmRollbackTarget">
            确认退回流转
          </el-button>
        </div>
      </template>
    </el-dialog>
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

    .hero-badges {
      display: flex;
      align-items: center;
      gap: 10px;
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

.trace-card {
  .trace-track {
    display: flex;
    align-items: center;
    gap: 8px;
    overflow-x: auto;
    padding: 8px 4px 12px;

    .trace-node-item {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-shrink: 0;

      .node-circle {
        width: 32px;
        height: 32px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 13px;
        font-weight: 600;
        background: #f1f5f9;
        color: #64748b;
        border: 2px solid #cbd5e1;
        transition: all 0.2s ease;
      }

      .node-content {
        .node-title {
          font-size: 13px;
          font-weight: 600;
          color: #334155;
          white-space: nowrap;
        }

        .node-state-text {
          font-size: 11px;
          color: #94a3b8;
          white-space: nowrap;
        }
      }

      .node-connector {
        color: #cbd5e1;
        font-size: 14px;
        margin-left: 6px;
      }

      // 各种状态着色
      &.node-completed {
        .node-circle {
          background: #dcfce7;
          border-color: #22c55e;
          color: #16a34a;
        }
        .node-title {
          color: #166534;
        }
        .node-state-text {
          color: #22c55e;
        }
      }

      &.node-running {
        .node-circle {
          background: #dbeafe;
          border-color: #3b82f6;
          color: #2563eb;
          box-shadow: 0 0 0 3px rgb(59 130 246 / 0.2);
        }
        .node-title {
          color: #1d4ed8;
        }
        .node-state-text {
          color: #3b82f6;
          font-weight: 500;
        }
      }

      &.node-rejected {
        .node-circle {
          background: #fee2e2;
          border-color: #ef4444;
          color: #dc2626;
        }
        .node-title {
          color: #991b1b;
        }
        .node-state-text {
          color: #ef4444;
        }
      }

      &.node-rollback {
        .node-circle {
          background: #fef3c7;
          border-color: #f59e0b;
          color: #d97706;
        }
        .node-title {
          color: #b45309;
        }
        .node-state-text {
          color: #f59e0b;
        }
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

    .sub-hint {
      color: #64748b;
      font-size: 12px;
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
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 8px;
  }

  &.cancel-only {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
}

.rollback-dialog-body {
  .dialog-tip {
    font-size: 13px;
    color: #475569;
    margin: 0 0 16px;
    line-height: 1.5;
  }

  .rollback-node-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
    width: 100%;

    .rollback-radio-item {
      display: flex;
      align-items: flex-start;
      margin-right: 0;
      padding: 10px 14px;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      background: #f8fafc;
      width: 100%;

      &:hover {
        border-color: #93c5fd;
        background: #f0f7ff;
      }

      .radio-label-box {
        display: flex;
        flex-direction: column;
        gap: 2px;
        margin-left: 6px;

        strong {
          font-size: 13px;
          color: #0f172a;
        }

        .sub {
          font-size: 12px;
          color: #64748b;
        }
      }
    }
  }
}
</style>
