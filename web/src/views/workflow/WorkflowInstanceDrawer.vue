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
import { fetchIamUsers } from "@/api/modules/iam/service";
import type { IamUserRow } from "@/api/modules/iam/types";
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

// 企业人员名录与搜索
const systemUsers = ref<IamUserRow[]>([]);
const loadingUsers = ref(false);

async function ensureSystemUsers() {
  if (systemUsers.value.length > 0) return;
  loadingUsers.value = true;
  try {
    const list = await fetchIamUsers();
    systemUsers.value = list || [];
  } catch (e) {
    console.error("加载人员名录失败:", e);
  } finally {
    loadingUsers.value = false;
  }
}

// 排除当前登录人以及被禁用的账号
const candidateUsers = computed(() => {
  return systemUsers.value.filter(
    (u) => Number(u.id) !== currentUserId.value && u.status !== "DISABLED"
  );
});

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

// 委派代审弹窗
const delegateDialogVisible = ref(false);
const delegateForm = ref<{ targetUserId?: number; comment: string }>({
  targetUserId: undefined,
  comment: ""
});

// 加签会签弹窗
const addSignDialogVisible = ref(false);
const addSignForm = ref<{ targetUserId?: number; signType: "BEFORE" | "AFTER"; comment: string }>({
  targetUserId: undefined,
  signType: "AFTER",
  comment: ""
});

// 转办移交弹窗
const transferDialogVisible = ref(false);
const transferForm = ref<{ targetUserId?: number; comment: string }>({
  targetUserId: undefined,
  comment: ""
});

// 计算当前登录人是否有该审批单的待办任务（支持主审 APPROVER 与受托 DELEGATE）
const myPendingTask = computed<WorkflowTaskRow | undefined>(() => {
  if (!detail.value) return undefined;
  return detail.value.tasks.find(
    (t) =>
      t.status === "PENDING" &&
      (t.nodeType === "APPROVER" || t.nodeType === "DELEGATE") &&
      Number(t.assigneeId) === currentUserId.value
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
      ensureSystemUsers().catch(() => {});
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

async function openDelegateDialog() {
  if (!myPendingTask.value) return;
  delegateForm.value = {
    targetUserId: undefined,
    comment: handleForm.value.comment.trim()
  };
  await ensureSystemUsers();
  delegateDialogVisible.value = true;
}

async function confirmDelegate() {
  if (!myPendingTask.value) return;
  if (!delegateForm.value.targetUserId) {
    ElMessage.warning("请在企业人员列表中选择受托人");
    return;
  }
  const target = systemUsers.value.find((u) => u.id === delegateForm.value.targetUserId);
  if (!target) {
    ElMessage.warning("所选人员不存在");
    return;
  }
  const targetName = target.nickname || target.username;
  submitting.value = true;
  try {
    await handleWorkflowTask(myPendingTask.value.id, {
      action: "DELEGATE",
      delegateUserId: target.id,
      delegateUserName: targetName,
      comment: delegateForm.value.comment.trim() || `委派说明：委托同事 ${targetName} 协助核验并出具意见。`
    });
    ElMessage.success(`审批任务已成功委派给「${targetName}」！`);
    delegateDialogVisible.value = false;
    handleForm.value.comment = "";
    emit("refresh");
    await loadDetail();
  } finally {
    submitting.value = false;
  }
}

async function openAddSignDialog() {
  if (!myPendingTask.value) return;
  addSignForm.value = {
    targetUserId: undefined,
    signType: "AFTER",
    comment: handleForm.value.comment.trim()
  };
  await ensureSystemUsers();
  addSignDialogVisible.value = true;
}

async function confirmAddSign() {
  if (!myPendingTask.value) return;
  if (!addSignForm.value.targetUserId) {
    ElMessage.warning("请在企业人员列表中选择加签协办人");
    return;
  }
  const target = systemUsers.value.find((u) => u.id === addSignForm.value.targetUserId);
  if (!target) {
    ElMessage.warning("所选人员不存在");
    return;
  }
  const targetName = target.nickname || target.username;
  submitting.value = true;
  try {
    await handleWorkflowTask(myPendingTask.value.id, {
      action: "ADD_SIGN",
      addSignUserId: target.id,
      addSignUserName: targetName,
      addSignType: addSignForm.value.signType,
      comment: addSignForm.value.comment.trim() || `加签协同：邀请 ${targetName} 参与会签。`
    });
    ElMessage.success(`已成功邀请「${targetName}」进行加签会签！`);
    addSignDialogVisible.value = false;
    handleForm.value.comment = "";
    emit("refresh");
    await loadDetail();
  } finally {
    submitting.value = false;
  }
}

async function openTransferDialog() {
  if (!myPendingTask.value) return;
  transferForm.value = {
    targetUserId: undefined,
    comment: handleForm.value.comment.trim()
  };
  await ensureSystemUsers();
  transferDialogVisible.value = true;
}

async function confirmTransfer() {
  if (!myPendingTask.value) return;
  if (!transferForm.value.targetUserId) {
    ElMessage.warning("请在企业人员列表中选择转办承接人");
    return;
  }
  const target = systemUsers.value.find((u) => u.id === transferForm.value.targetUserId);
  if (!target) {
    ElMessage.warning("所选人员不存在");
    return;
  }
  const targetName = target.nickname || target.username;
  submitting.value = true;
  try {
    await handleWorkflowTask(myPendingTask.value.id, {
      action: "TRANSFER",
      transferToUserId: target.id,
      transferToUserName: targetName,
      comment: transferForm.value.comment.trim() || `转办说明：因业务职责分工，将该审批转交由 ${targetName} 全权办理。`
    });
    ElMessage.success(`审批任务已全权转交给「${targetName}」！`);
    transferDialogVisible.value = false;
    handleForm.value.comment = "";
    emit("refresh");
    await loadDetail();
  } finally {
    submitting.value = false;
  }
}

async function handleDelegateResolve() {
  if (!myPendingTask.value) return;
  try {
    await ElMessageBox.confirm("确认完成代审并将审批单交回原主审人吗？", "完成代审确认", {
      confirmButtonText: "确认交回",
      cancelButtonText: "取消",
      type: "info"
    });
    submitting.value = true;
    await handleWorkflowTask(myPendingTask.value.id, {
      action: "DELEGATE_RESOLVE",
      comment: handleForm.value.comment.trim() || "已完成受托事项初审，出具核验意见并交回原主审人最终审批。"
    });
    ElMessage.success("已完成代审并将任务交回原主审人！");
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
    case "TRANSFER":
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
            <div class="panel-tag" :class="{ 'tag-delegate': myPendingTask.nodeType === 'DELEGATE' }">
              {{ myPendingTask.nodeType === 'DELEGATE' ? '受托待办' : '待您审批' }}
            </div>
            <span>当前审批环节：<strong>{{ myPendingTask.nodeName }}</strong></span>
          </div>

          <el-input
            v-model="handleForm.comment"
            type="textarea"
            :rows="2"
            placeholder="请在此输入审批意见（如：同意，按流程执行。驳回或回退请注明理由）"
          />

          <div class="panel-actions">
            <!-- 如果是受托代审环节 -->
            <template v-if="myPendingTask.nodeType === 'DELEGATE'">
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
                type="primary"
                :icon="Check"
                :loading="submitting"
                @click="handleDelegateResolve"
              >
                完成初审并交回
              </el-button>
            </template>

            <!-- 常规主审环节 -->
            <template v-else>
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

              <!-- 转办全权移交 -->
              <el-button
                type="default"
                plain
                :icon="Right"
                :loading="submitting"
                @click="openTransferDialog"
              >
                转办移交
              </el-button>

              <!-- 委派代审 -->
              <el-button
                type="info"
                plain
                :icon="Share"
                :loading="submitting"
                @click="openDelegateDialog"
              >
                委派代审
              </el-button>

              <!-- 加签会签 -->
              <el-button
                type="primary"
                plain
                :icon="Plus"
                :loading="submitting"
                @click="openAddSignDialog"
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
            </template>
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

    <!-- 1. 自由回退选择弹窗 -->
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

    <!-- 2. 审批任务委派弹窗 (委托同事代审，审结后交回) -->
    <el-dialog
      v-model="delegateDialogVisible"
      title="审批任务委派 (Delegate)"
      width="540px"
      append-to-body
    >
      <div class="user-action-dialog-body">
        <div class="action-alert-tip tip-info">
          <el-icon><Share /></el-icon>
          <div>
            <strong>委派代审机制说明</strong>
            <p>将当前审批任务委托给指定同事协助审阅并出具初审意见。受托人审结后，流程将自动交回给您做最终裁决。</p>
          </div>
        </div>

        <el-form label-position="top">
          <el-form-item label="选择受托人 (支持输入姓名 / 账号 / 工号检索)" required>
            <el-select
              v-model="delegateForm.targetUserId"
              placeholder="请搜索或选择受托人"
              filterable
              clearable
              style="width: 100%"
              :loading="loadingUsers"
            >
              <el-option
                v-for="u in candidateUsers"
                :key="u.id"
                :label="`${u.nickname || u.username} (${u.username})${u.userCode ? ' - ' + u.userCode : ''}`"
                :value="u.id"
              >
                <div class="user-select-row">
                  <div class="user-avatar-tag">{{ (u.nickname || u.username || 'U').slice(0, 1) }}</div>
                  <div class="user-meta">
                    <div class="name-line">
                      <span class="nickname">{{ u.nickname || u.username }}</span>
                      <span class="username">@{{ u.username }}</span>
                      <el-tag v-if="u.userType === 'SUPER_ADMIN'" size="small" type="danger" effect="plain" class="type-tag">超管</el-tag>
                      <el-tag v-else-if="u.userType === 'TENANT_ADMIN'" size="small" type="warning" effect="plain" class="type-tag">管理员</el-tag>
                    </div>
                    <div class="sub-line">
                      <span v-if="u.userCode">工号: {{ u.userCode }}</span>
                      <span v-if="u.email" class="email">{{ u.email }}</span>
                    </div>
                  </div>
                </div>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="委派说明与核验指示">
            <el-input
              v-model="delegateForm.comment"
              type="textarea"
              :rows="3"
              placeholder="请输入委派指示说明（例如：请协助核验该单据技术指标与采购合规材料）"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-actions">
          <el-button @click="delegateDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="confirmDelegate">
            确认委派
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 3. 审批加签会签弹窗 (邀请同事会签) -->
    <el-dialog
      v-model="addSignDialogVisible"
      title="审批加签协同 (Add Sign)"
      width="540px"
      append-to-body
    >
      <div class="user-action-dialog-body">
        <div class="action-alert-tip tip-primary">
          <el-icon><Plus /></el-icon>
          <div>
            <strong>加签会签机制说明</strong>
            <p>在当前环节动态邀请其他同事协同参与评审。会签完成后，结果自动汇流并入主流程流转。</p>
          </div>
        </div>

        <el-form label-position="top">
          <el-form-item label="选择加签协办人 (支持输入姓名 / 账号 / 工号检索)" required>
            <el-select
              v-model="addSignForm.targetUserId"
              placeholder="请搜索或选择加签协办人"
              filterable
              clearable
              style="width: 100%"
              :loading="loadingUsers"
            >
              <el-option
                v-for="u in candidateUsers"
                :key="u.id"
                :label="`${u.nickname || u.username} (${u.username})${u.userCode ? ' - ' + u.userCode : ''}`"
                :value="u.id"
              >
                <div class="user-select-row">
                  <div class="user-avatar-tag">{{ (u.nickname || u.username || 'U').slice(0, 1) }}</div>
                  <div class="user-meta">
                    <div class="name-line">
                      <span class="nickname">{{ u.nickname || u.username }}</span>
                      <span class="username">@{{ u.username }}</span>
                      <el-tag v-if="u.userType === 'SUPER_ADMIN'" size="small" type="danger" effect="plain" class="type-tag">超管</el-tag>
                      <el-tag v-else-if="u.userType === 'TENANT_ADMIN'" size="small" type="warning" effect="plain" class="type-tag">管理员</el-tag>
                    </div>
                    <div class="sub-line">
                      <span v-if="u.userCode">工号: {{ u.userCode }}</span>
                      <span v-if="u.email" class="email">{{ u.email }}</span>
                    </div>
                  </div>
                </div>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="加签流转方式" required>
            <el-radio-group v-model="addSignForm.signType" class="sign-type-group">
              <el-radio value="AFTER" class="sign-radio-card">
                <div class="radio-card-content">
                  <span class="radio-title">后加签 (推荐)</span>
                  <span class="radio-desc">本人完成审批后，自动流转至加签协办人会签审核</span>
                </div>
              </el-radio>
              <el-radio value="BEFORE" class="sign-radio-card">
                <div class="radio-card-content">
                  <span class="radio-title">前加签</span>
                  <span class="radio-desc">优先流转至加签协办人，加签人审核通过后再由本人终审</span>
                </div>
              </el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="加签说明与协同要点">
            <el-input
              v-model="addSignForm.comment"
              type="textarea"
              :rows="3"
              placeholder="请输入加签协同说明（例如：请技术委员会 / 法务专员核验合同约束与合规风险）"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-actions">
          <el-button @click="addSignDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="confirmAddSign">
            确认加签
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 4. 审批任务转办弹窗 (全权移交给他人) -->
    <el-dialog
      v-model="transferDialogVisible"
      title="审批任务转办 (Transfer)"
      width="540px"
      append-to-body
    >
      <div class="user-action-dialog-body">
        <div class="action-alert-tip tip-warning">
          <el-icon><Right /></el-icon>
          <div>
            <strong>转办全权移交说明</strong>
            <p>将该待办全权移交给所选同事办理。转办后该待办将从您的待办列表中移出，完全由转办承接人执行后续批示。</p>
          </div>
        </div>

        <el-form label-position="top">
          <el-form-item label="选择转办承接人 (支持输入姓名 / 账号 / 工号检索)" required>
            <el-select
              v-model="transferForm.targetUserId"
              placeholder="请搜索或选择转办承接人"
              filterable
              clearable
              style="width: 100%"
              :loading="loadingUsers"
            >
              <el-option
                v-for="u in candidateUsers"
                :key="u.id"
                :label="`${u.nickname || u.username} (${u.username})${u.userCode ? ' - ' + u.userCode : ''}`"
                :value="u.id"
              >
                <div class="user-select-row">
                  <div class="user-avatar-tag">{{ (u.nickname || u.username || 'U').slice(0, 1) }}</div>
                  <div class="user-meta">
                    <div class="name-line">
                      <span class="nickname">{{ u.nickname || u.username }}</span>
                      <span class="username">@{{ u.username }}</span>
                      <el-tag v-if="u.userType === 'SUPER_ADMIN'" size="small" type="danger" effect="plain" class="type-tag">超管</el-tag>
                      <el-tag v-else-if="u.userType === 'TENANT_ADMIN'" size="small" type="warning" effect="plain" class="type-tag">管理员</el-tag>
                    </div>
                    <div class="sub-line">
                      <span v-if="u.userCode">工号: {{ u.userCode }}</span>
                      <span v-if="u.email" class="email">{{ u.email }}</span>
                    </div>
                  </div>
                </div>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="转办说明与交接事由">
            <el-input
              v-model="transferForm.comment"
              type="textarea"
              :rows="3"
              placeholder="请输入转办交接说明（例如：因业务线分工调整，交由专项业务负责人全权办理）"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-actions">
          <el-button @click="transferDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="confirmTransfer">
            确认转办
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

      &.tag-delegate {
        background: #fef3c7;
        color: #d97706;
      }
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

.user-action-dialog-body {
  .action-alert-tip {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 12px 14px;
    border-radius: 8px;
    margin-bottom: 16px;
    font-size: 13px;
    line-height: 1.5;

    .el-icon {
      font-size: 20px;
      margin-top: 2px;
      flex-shrink: 0;
    }

    strong {
      display: block;
      font-size: 13px;
      margin-bottom: 2px;
    }

    p {
      margin: 0;
      font-size: 12px;
      opacity: 0.9;
    }

    &.tip-info {
      background: #eff6ff;
      border: 1px solid #bfdbfe;
      color: #1e40af;
      .el-icon {
        color: #2563eb;
      }
    }

    &.tip-primary {
      background: #f5f3ff;
      border: 1px solid #ddd6fe;
      color: #5b21b6;
      .el-icon {
        color: #7c3aed;
      }
    }

    &.tip-warning {
      background: #fffbeb;
      border: 1px solid #fde68a;
      color: #92400e;
      .el-icon {
        color: #d97706;
      }
    }
  }

  .sign-type-group {
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: 100%;

    .sign-radio-card {
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

      .radio-card-content {
        display: flex;
        flex-direction: column;
        gap: 2px;
        margin-left: 6px;

        .radio-title {
          font-size: 13px;
          font-weight: 600;
          color: #1e293b;
        }

        .radio-desc {
          font-size: 12px;
          color: #64748b;
        }
      }
    }
  }
}

.user-select-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;

  .user-avatar-tag {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    background: #e0e7ff;
    color: #4338ca;
    font-size: 13px;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .user-meta {
    display: flex;
    flex-direction: column;
    gap: 2px;
    line-height: 1.2;

    .name-line {
      display: flex;
      align-items: center;
      gap: 6px;

      .nickname {
        font-size: 13px;
        font-weight: 500;
        color: #1e293b;
      }

      .username {
        font-size: 12px;
        color: #64748b;
      }

      .type-tag {
        font-size: 10px;
        height: 18px;
        padding: 0 4px;
      }
    }

    .sub-line {
      font-size: 11px;
      color: #94a3b8;
      display: flex;
      gap: 8px;
    }
  }
}
</style>
