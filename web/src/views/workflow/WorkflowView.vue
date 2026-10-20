<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Calendar,
  Check,
  CircleCheck,
  Connection,
  Document,
  Key,
  Money,
  Plus,
  RefreshRight,
  Tickets,
  Timer,
  WarningFilled
} from "@element-plus/icons-vue";
import {
  checkWorkflowHealth,
  deleteWorkflowDefinition,
  fetchCcTasks,
  fetchMyCompletedTasks,
  fetchMyPendingTasks,
  fetchWorkflowDefinitions,
  fetchWorkflowInstances,
  handleWorkflowTask,
  toggleWorkflowDefinitionStatus
} from "@/api/modules/workflow";
import type {
  ProcessDefinitionRow,
  ProcessInstanceRow,
  WorkflowTaskRow
} from "@/api/modules/workflow";
import WorkflowApplyDialog from "./WorkflowApplyDialog.vue";
import WorkflowDesignerDialog from "./WorkflowDesignerDialog.vue";
import WorkflowInstanceDrawer from "./WorkflowInstanceDrawer.vue";

const activeMainTab = ref<"apply" | "workspace" | "definitions">("workspace");

// 1. Definition List State
const definitionsLoading = ref(false);
const definitions = ref<ProcessDefinitionRow[]>([]);
const categoryFilter = ref<string>("ALL");

// 2. Workspace State
const workspaceSubTab = ref<"pending" | "completed" | "my" | "cc">("pending");
const tasksLoading = ref(false);
const pendingTasks = ref<WorkflowTaskRow[]>([]);
const completedTasks = ref<WorkflowTaskRow[]>([]);
const myInstances = ref<ProcessInstanceRow[]>([]);
const ccTasks = ref<WorkflowTaskRow[]>([]);
const searchKeyword = ref("");

// 3. Modals & Drawers
const applyDialogVisible = ref(false);
const selectedDefForApply = ref<ProcessDefinitionRow | null>(null);

const designerDialogVisible = ref(false);
const selectedDefForDesigner = ref<ProcessDefinitionRow | null>(null);

const instanceDrawerVisible = ref(false);
const selectedInstanceId = ref<number | null>(null);

// Category filtering for templates
const filteredDefinitions = computed(() => {
  if (categoryFilter.value === "ALL") {
    return definitions.value;
  }
  return definitions.value.filter((d) => d.category === categoryFilter.value);
});

// Workspace Filtered Lists
const filteredPendingTasks = computed(() => {
  if (!searchKeyword.value.trim()) return pendingTasks.value;
  const kw = searchKeyword.value.toLowerCase();
  return pendingTasks.value.filter(
    (t) =>
      t.instanceNo.toLowerCase().includes(kw) ||
      t.instanceTitle.toLowerCase().includes(kw) ||
      t.initiatorName.toLowerCase().includes(kw)
  );
});

const filteredCompletedTasks = computed(() => {
  if (!searchKeyword.value.trim()) return completedTasks.value;
  const kw = searchKeyword.value.toLowerCase();
  return completedTasks.value.filter(
    (t) =>
      t.instanceNo.toLowerCase().includes(kw) ||
      t.instanceTitle.toLowerCase().includes(kw) ||
      t.initiatorName.toLowerCase().includes(kw)
  );
});

const filteredMyInstances = computed(() => {
  if (!searchKeyword.value.trim()) return myInstances.value;
  const kw = searchKeyword.value.toLowerCase();
  return myInstances.value.filter(
    (i) =>
      i.instanceNo.toLowerCase().includes(kw) ||
      i.title.toLowerCase().includes(kw)
  );
});

const filteredCcTasks = computed(() => {
  if (!searchKeyword.value.trim()) return ccTasks.value;
  const kw = searchKeyword.value.toLowerCase();
  return ccTasks.value.filter(
    (t) =>
      t.instanceNo.toLowerCase().includes(kw) ||
      t.instanceTitle.toLowerCase().includes(kw) ||
      t.initiatorName.toLowerCase().includes(kw)
  );
});

// Subsystem Fault Tolerance & Resilience
const serviceUnavailable = ref(false);
const heartbeatPolling = ref(false);
let heartbeatTimer: any = null;

function handleServiceError(err: any): boolean {
  const code = err?.response?.data?.code || err?.code;
  if (code === "WORKFLOW_SERVICE_UNAVAILABLE" || code === "WORKFLOW_DOWN" || err?.response?.status === 503) {
    serviceUnavailable.value = true;
    startHeartbeatMonitor();
    return true;
  }
  return false;
}

function startHeartbeatMonitor() {
  if (heartbeatTimer) return;
  heartbeatTimer = setInterval(async () => {
    await manualProbeWorkflow(false);
  }, 5000);
}

function stopHeartbeatMonitor() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
}

async function manualProbeWorkflow(notify = true) {
  heartbeatPolling.value = true;
  try {
    const res = await checkWorkflowHealth();
    if (res && res.status === "UP") {
      serviceUnavailable.value = false;
      stopHeartbeatMonitor();
      if (notify) {
        ElMessage.success("工作流独立子系统已恢复在线！");
      }
      refreshAll();
    } else if (notify) {
      ElMessage.warning("工作流独立子系统仍在重启或离线中，请稍候...");
    }
  } catch {
    if (notify) {
      ElMessage.warning("探测失败：工作流服务尚未就绪");
    }
  } finally {
    heartbeatPolling.value = false;
  }
}

// Loaders
async function loadDefinitions() {
  definitionsLoading.value = true;
  try {
    definitions.value = await fetchWorkflowDefinitions();
    if (serviceUnavailable.value) {
      serviceUnavailable.value = false;
      stopHeartbeatMonitor();
    }
  } catch (err: any) {
    if (!handleServiceError(err)) {
      ElMessage.error(err?.message || "加载工作流定义列表失败");
    }
  } finally {
    definitionsLoading.value = false;
  }
}

async function loadWorkspaceData() {
  tasksLoading.value = true;
  try {
    const [pending, completed, my, cc] = await Promise.all([
      fetchMyPendingTasks(),
      fetchMyCompletedTasks(),
      fetchWorkflowInstances({ onlyMine: true }),
      fetchCcTasks()
    ]);
    pendingTasks.value = pending;
    completedTasks.value = completed;
    myInstances.value = my;
    ccTasks.value = cc;
    if (serviceUnavailable.value) {
      serviceUnavailable.value = false;
      stopHeartbeatMonitor();
    }
  } catch (err: any) {
    if (!handleServiceError(err)) {
      ElMessage.error(err?.message || "加载审批任务数据失败");
    }
  } finally {
    tasksLoading.value = false;
  }
}

function refreshAll() {
  loadDefinitions();
  loadWorkspaceData();
}

onMounted(() => {
  refreshAll();
});

onUnmounted(() => {
  stopHeartbeatMonitor();
});

// Fast Action: Open Apply Dialog
function openApplyDialog(def: ProcessDefinitionRow) {
  selectedDefForApply.value = def;
  applyDialogVisible.value = true;
}

// Fast Action: Open Designer Dialog
function openDesignerDialog(def: ProcessDefinitionRow | null) {
  selectedDefForDesigner.value = def;
  designerDialogVisible.value = true;
}

// Fast Action: View Instance Detail Drawer
function viewInstanceDetail(instanceId: number) {
  selectedInstanceId.value = instanceId;
  instanceDrawerVisible.value = true;
}

// Quick Approve / Reject in Pending Table
async function handleQuickApprove(task: WorkflowTaskRow) {
  try {
    const { value: comment } = await ElMessageBox.prompt("请输入审批意见（可选）：", "同意申请", {
      confirmButtonText: "确认同意",
      cancelButtonText: "取消",
      inputValue: "同意，符合办理规范"
    });

    await handleWorkflowTask(task.id, {
      action: "AGREE",
      comment: comment || "同意"
    });
    ElMessage.success("已审批同意");
    loadWorkspaceData();
  } catch {
    // User cancelled
  }
}

async function handleQuickReject(task: WorkflowTaskRow) {
  try {
    const { value: comment } = await ElMessageBox.prompt("请输入驳回原因（必填）：", "驳回申请", {
      confirmButtonText: "确认驳回",
      cancelButtonText: "取消",
      inputPattern: /\S+/,
      inputErrorMessage: "驳回原因不能为空"
    });

    await handleWorkflowTask(task.id, {
      action: "REJECT",
      comment: comment || "驳回"
    });
    ElMessage.warning("已驳回申请");
    loadWorkspaceData();
  } catch {
    // User cancelled
  }
}

// Toggle Definition Status
async function handleToggleStatus(row: ProcessDefinitionRow) {
  const newStatus = row.status === "PUBLISHED" ? "DISABLED" : "PUBLISHED";
  const actionText = newStatus === "PUBLISHED" ? "启用" : "停用";
  try {
    await toggleWorkflowDefinitionStatus(row.id);
    ElMessage.success(`已${actionText}流程【${row.processName}】`);
    loadDefinitions();
  } catch (err: any) {
    ElMessage.error(err?.message || `${actionText}失败`);
  }
}

// Delete Definition
async function handleDeleteDefinition(row: ProcessDefinitionRow) {
  try {
    await ElMessageBox.confirm(`确定要彻底删除流程【${row.processName}】吗？`, "删除确认", {
      type: "warning",
      confirmButtonText: "确认删除",
      cancelButtonText: "取消"
    });
    await deleteWorkflowDefinition(row.id);
    ElMessage.success("已删除该流程定义");
    loadDefinitions();
  } catch {
    // User cancelled
  }
}

// Helper badge colors
function getStatusTagType(status: string) {
  switch (status) {
    case "PENDING":
    case "RUNNING":
      return "warning";
    case "APPROVED":
    case "COMPLETED":
    case "AGREED":
      return "success";
    case "REJECTED":
      return "danger";
    case "CANCELLED":
    case "CANCELED":
      return "info";
    default:
      return "info";
  }
}

function getStatusLabel(status: string) {
  switch (status) {
    case "PENDING":
      return "待处理";
    case "RUNNING":
      return "流转中";
    case "APPROVED":
    case "COMPLETED":
      return "已办结";
    case "AGREED":
      return "同意";
    case "REJECTED":
      return "已驳回";
    case "CANCELLED":
    case "CANCELED":
      return "已撤回";
    case "TRANSFERRED":
      return "已转办";
    case "READ":
      return "已查阅";
    default:
      return status;
  }
}

function getCategoryBadge(category: string) {
  switch (category) {
    case "HR":
      return { label: "人事考勤", color: "#3b82f6" };
    case "FINANCE":
      return { label: "财务报销", color: "#10b981" };
    case "GENERAL":
      return { label: "综合行政", color: "#8b5cf6" };
    case "IT":
      return { label: "IT与权限", color: "#f59e0b" };
    default:
      return { label: "常规业务", color: "#64748b" };
  }
}
</script>

<template>
  <div class="workflow-hub-page">
    <!-- Top Sleek Header Strip -->
    <div class="workflow-header-strip">
      <div class="strip-left">
        <div class="title-cluster">
          <el-icon class="hub-icon"><Tickets /></el-icon>
          <span class="hub-title">工作流与审批中心</span>
        </div>
        <div class="divider"></div>
        <div class="metric-tags">
          <div class="metric-tag" :class="{ 'has-badge': pendingTasks.length > 0 }" @click="activeMainTab = 'workspace'; workspaceSubTab = 'pending'">
            <span class="label">待我审批</span>
            <span class="value">{{ pendingTasks.length }}</span>
          </div>
          <div class="metric-tag" @click="activeMainTab = 'workspace'; workspaceSubTab = 'my'">
            <span class="label">我发起</span>
            <span class="value">{{ myInstances.length }}</span>
          </div>
          <div class="metric-tag" @click="activeMainTab = 'workspace'; workspaceSubTab = 'completed'">
            <span class="label">已处理</span>
            <span class="value">{{ completedTasks.length }}</span>
          </div>
          <div class="metric-tag" @click="activeMainTab = 'definitions'">
            <span class="label">标准模版</span>
            <span class="value">{{ definitions.length }}</span>
          </div>
        </div>
      </div>

      <div class="strip-right">
        <el-button type="primary" size="small" :icon="Plus" @click="openDesignerDialog(null)">
          新建流程模版
        </el-button>
        <el-button size="small" :icon="RefreshRight" @click="refreshAll">刷新</el-button>
      </div>
    </div>

    <!-- 独立子系统故障隔离与自愈状态条 -->
    <transition name="el-fade-in">
      <div v-if="serviceUnavailable" class="workflow-service-alert">
        <el-alert
          type="warning"
          show-icon
          :closable="false"
        >
          <template #title>
            <div class="service-alert-content">
              <div class="service-alert-text">
                <span class="service-alert-title">工作流独立子系统（端口: 8082）正在热重载或维护中</span>
                <span class="service-alert-desc">
                  平台底座已实施优雅故障隔离（Fault Isolation），平台核心功能、IAM、租户与审计 100% 正常运行。系统正在自动重试健康探测...
                </span>
              </div>
              <el-button
                size="small"
                type="warning"
                plain
                :icon="Connection"
                :loading="heartbeatPolling"
                @click="manualProbeWorkflow(true)"
              >
                立即探测恢复
              </el-button>
            </div>
          </template>
        </el-alert>
      </div>
    </transition>

    <!-- Main Navigation Tabs -->
    <div class="hub-tabs-container">
      <el-tabs v-model="activeMainTab" class="modern-hub-tabs">
        <!-- Tab 1: 审批工作台 -->
        <el-tab-pane name="workspace">
          <template #label>
            <span class="tab-label">
              <el-icon><Timer /></el-icon> 审批工作台
              <el-badge v-if="pendingTasks.length > 0" :value="pendingTasks.length" class="tab-badge" />
            </span>
          </template>

          <div class="workspace-wrapper">
            <div class="workspace-toolbar">
              <el-radio-group v-model="workspaceSubTab" size="default">
                <el-radio-button value="pending">
                  待我审批 ({{ pendingTasks.length }})
                </el-radio-button>
                <el-radio-button value="completed">
                  我已审批 ({{ completedTasks.length }})
                </el-radio-button>
                <el-radio-button value="my">
                  我发起的 ({{ myInstances.length }})
                </el-radio-button>
                <el-radio-button value="cc">
                  抄送我的 ({{ ccTasks.length }})
                </el-radio-button>
              </el-radio-group>

              <div class="toolbar-right">
                <el-input
                  v-model="searchKeyword"
                  placeholder="搜索单据号 / 事项 / 发起人"
                  clearable
                  style="width: 260px"
                />
              </div>
            </div>

            <!-- 1. 待我审批表格 -->
            <el-table
              v-if="workspaceSubTab === 'pending'"
              v-loading="tasksLoading"
              :data="filteredPendingTasks"
              stripe
              class="data-table"
            >
              <el-table-column prop="instanceNo" label="单据编号" width="160">
                <template #default="{ row }">
                  <el-link type="primary" :underline="false" @click="viewInstanceDetail(row.instanceId)">
                    {{ row.instanceNo }}
                  </el-link>
                </template>
              </el-table-column>
              <el-table-column prop="instanceTitle" label="申请事项" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="title-cell">
                    <span class="item-title">{{ row.instanceTitle }}</span>
                    <el-tag size="small" :color="getCategoryBadge(row.category).color" effect="dark" class="cat-tag">
                      {{ getCategoryBadge(row.category).label }}
                    </el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="initiatorName" label="申请发起人" width="130" />
              <el-table-column prop="nodeName" label="当前待办环节" width="150">
                <template #default="{ row }">
                  <el-tag size="small" type="warning" effect="plain">{{ row.nodeName }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="到达时间" width="170" />
              <el-table-column label="快捷操作" width="220" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="success" @click="handleQuickApprove(row)">
                    同意
                  </el-button>
                  <el-button size="small" type="danger" plain @click="handleQuickReject(row)">
                    驳回
                  </el-button>
                  <el-button size="small" link type="primary" @click="viewInstanceDetail(row.instanceId)">
                    详情
                  </el-button>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="当前暂无待处理的审批任务，干得漂亮！" />
              </template>
            </el-table>

            <!-- 2. 我已审批表格 -->
            <el-table
              v-else-if="workspaceSubTab === 'completed'"
              v-loading="tasksLoading"
              :data="filteredCompletedTasks"
              stripe
              class="data-table"
            >
              <el-table-column prop="instanceNo" label="单据编号" width="160">
                <template #default="{ row }">
                  <el-link type="primary" :underline="false" @click="viewInstanceDetail(row.instanceId)">
                    {{ row.instanceNo }}
                  </el-link>
                </template>
              </el-table-column>
              <el-table-column prop="instanceTitle" label="申请事项" min-width="220" show-overflow-tooltip />
              <el-table-column prop="initiatorName" label="申请发起人" width="130" />
              <el-table-column prop="nodeName" label="办理环节" width="140" />
              <el-table-column prop="status" label="我的审批结果" width="130">
                <template #default="{ row }">
                  <el-tag size="small" :type="getStatusTagType(row.status)">
                    {{ getStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="comment" label="审批附言" min-width="160" show-overflow-tooltip />
              <el-table-column prop="finishTime" label="办理时间" width="170" />
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" link type="primary" @click="viewInstanceDetail(row.instanceId)">
                    查看
                  </el-button>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="暂无历史审批记录" />
              </template>
            </el-table>

            <!-- 3. 我发起的表格 -->
            <el-table
              v-else-if="workspaceSubTab === 'my'"
              v-loading="tasksLoading"
              :data="filteredMyInstances"
              stripe
              class="data-table"
            >
              <el-table-column prop="instanceNo" label="单据编号" width="160">
                <template #default="{ row }">
                  <el-link type="primary" :underline="false" @click="viewInstanceDetail(row.id)">
                    {{ row.instanceNo }}
                  </el-link>
                </template>
              </el-table-column>
              <el-table-column prop="title" label="申请事项" min-width="220" show-overflow-tooltip />
              <el-table-column prop="processName" label="流程模版" width="160" />
              <el-table-column prop="currentNodeName" label="当前流转环节" width="150" />
              <el-table-column prop="status" label="单据状态" width="130">
                <template #default="{ row }">
                  <el-tag size="small" :type="getStatusTagType(row.status)">
                    {{ getStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="startTime" label="发起时间" width="170" />
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" link type="primary" @click="viewInstanceDetail(row.id)">
                    查看详情
                  </el-button>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="您尚未发起过任何业务审批申请" />
              </template>
            </el-table>

            <!-- 4. 抄送我的表格 -->
            <el-table
              v-else-if="workspaceSubTab === 'cc'"
              v-loading="tasksLoading"
              :data="filteredCcTasks"
              stripe
              class="data-table"
            >
              <el-table-column prop="instanceNo" label="单据编号" width="160">
                <template #default="{ row }">
                  <el-link type="primary" :underline="false" @click="viewInstanceDetail(row.instanceId)">
                    {{ row.instanceNo }}
                  </el-link>
                </template>
              </el-table-column>
              <el-table-column prop="instanceTitle" label="申请事项" min-width="220" show-overflow-tooltip />
              <el-table-column prop="initiatorName" label="申请发起人" width="130" />
              <el-table-column prop="createdAt" label="抄送时间" width="170" />
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" link type="primary" @click="viewInstanceDetail(row.instanceId)">
                    查阅单据
                  </el-button>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="暂无抄送您的单据" />
              </template>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- Tab 2: 发起申请中心 -->
        <el-tab-pane name="apply">
          <template #label>
            <span class="tab-label">
              <el-icon><Tickets /></el-icon> 业务发起中心
            </span>
          </template>

          <div class="apply-center-wrapper">
            <div class="category-filter-bar">
              <span class="filter-label">业务分类筛选：</span>
              <el-radio-group v-model="categoryFilter" size="small">
                <el-radio-button value="ALL">全部模版 ({{ definitions.length }})</el-radio-button>
                <el-radio-button value="HR">人事考勤 (HR)</el-radio-button>
                <el-radio-button value="FINANCE">财务报销 (FINANCE)</el-radio-button>
                <el-radio-button value="GENERAL">综合行政 (GENERAL)</el-radio-button>
                <el-radio-button value="IT">IT与数据权限 (IT)</el-radio-button>
              </el-radio-group>
            </div>

            <div v-loading="definitionsLoading" class="templates-grid">
              <div
                v-for="def in filteredDefinitions"
                :key="def.id"
                class="template-card"
                :class="{ disabled: def.status !== 'PUBLISHED' }"
              >
                <div class="card-top">
                  <div class="template-icon-wrapper" :style="{ background: def.color || getCategoryBadge(def.category).color }">
                    <el-icon v-if="def.icon === 'Calendar'"><Calendar /></el-icon>
                    <el-icon v-else-if="def.icon === 'Money'"><Money /></el-icon>
                    <el-icon v-else-if="def.icon === 'Key'"><Key /></el-icon>
                    <el-icon v-else><Document /></el-icon>
                  </div>
                  <div class="template-meta">
                    <h3 class="template-title">{{ def.processName }}</h3>
                    <div class="template-tags">
                      <el-tag size="small" effect="plain">{{ getCategoryBadge(def.category).label }}</el-tag>
                      <span class="version-tag">v{{ def.version }}.0</span>
                    </div>
                  </div>
                </div>

                <p class="template-desc">{{ def.description || "规范化在线表单流转审批" }}</p>

                <div class="card-bottom">
                  <span class="code-text">{{ def.processCode }}</span>
                  <el-button
                    type="primary"
                    size="small"
                    :disabled="def.status !== 'PUBLISHED'"
                    @click="openApplyDialog(def)"
                  >
                    立即发起申请
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- Tab 3: 流程设计与台账 -->
        <el-tab-pane name="definitions">
          <template #label>
            <span class="tab-label">
              <el-icon><Document /></el-icon> 流程设计与模版台账
            </span>
          </template>

          <div class="definitions-wrapper">
            <div class="def-toolbar">
              <div class="left-tips">
                管理企业全生命周期流程模版，点击【设计流程与表单】可进行无代码节点配置与表单字段设计
              </div>
              <el-button type="primary" :icon="Plus" @click="openDesignerDialog(null)">
                新建流程
              </el-button>
            </div>

            <el-table
              v-loading="definitionsLoading"
              :data="definitions"
              stripe
              class="data-table"
            >
              <el-table-column prop="processCode" label="流程编码" width="160" />
              <el-table-column prop="processName" label="流程名称" min-width="180">
                <template #default="{ row }">
                  <div class="def-title-cell">
                    <strong>{{ row.processName }}</strong>
                    <span class="ver-badge">v{{ row.version }}.0</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="category" label="分类" width="130">
                <template #default="{ row }">
                  <el-tag size="small" :color="getCategoryBadge(row.category).color" effect="dark">
                    {{ getCategoryBadge(row.category).label }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="description" label="功能说明" min-width="220" show-overflow-tooltip />
              <el-table-column prop="status" label="当前状态" width="110">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
                    {{ row.status === "PUBLISHED" ? "已发布启用" : "已停用" }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="updatedAt" label="最后维护时间" width="170" />
              <el-table-column label="设计与维护" width="220" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" plain @click="openDesignerDialog(row)">
                    设计流程
                  </el-button>
                  <el-button
                    size="small"
                    link
                    :type="row.status === 'PUBLISHED' ? 'warning' : 'success'"
                    @click="handleToggleStatus(row)"
                  >
                    {{ row.status === "PUBLISHED" ? "停用" : "启用" }}
                  </el-button>
                  <el-button size="small" link type="danger" @click="handleDeleteDefinition(row)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- Modals -->
    <WorkflowApplyDialog
      v-model:visible="applyDialogVisible"
      :definition="selectedDefForApply"
      @success="() => { activeMainTab = 'workspace'; workspaceSubTab = 'my'; loadWorkspaceData(); }"
    />

    <WorkflowDesignerDialog
      v-model:visible="designerDialogVisible"
      :definition="selectedDefForDesigner"
      @saved="loadDefinitions"
    />

    <WorkflowInstanceDrawer
      v-model:visible="instanceDrawerVisible"
      :instance-id="selectedInstanceId"
      @refresh="loadWorkspaceData"
    />
  </div>
</template>

<style scoped>
.workflow-hub-page {
  padding: 16px 20px;
  background: #f8fafc;
  min-height: calc(100vh - 84px);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Sleek Header Strip */
.workflow-header-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 2px rgb(0 0 0 / 0.03);

  .strip-left {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
  }

  .title-cluster {
    display: flex;
    align-items: center;
    gap: 8px;

    .hub-icon {
      font-size: 18px;
      color: #2563eb;
    }

    .hub-title {
      font-size: 15px;
      font-weight: 700;
      color: #0f172a;
    }
  }

  .divider {
    width: 1px;
    height: 14px;
    background: #e2e8f0;
  }

  .metric-tags {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .metric-tag {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 3px 10px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    cursor: pointer;
    font-size: 12px;
    transition: all 0.15s ease;

    &:hover {
      background: #f1f5f9;
      border-color: #cbd5e1;
    }

    &.has-badge {
      background: #fef2f2;
      border-color: #fecaca;

      .value {
        color: #dc2626;
      }
    }

    .label {
      color: #64748b;
    }

    .value {
      font-weight: 700;
      color: #0f172a;
    }
  }

  .strip-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

/* Tabs & Main Container */
.hub-tabs-container {
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px 0 rgb(0 0 0 / 0.05);
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
}

.tab-badge {
  margin-left: 4px;
}

/* Workspace Toolbar */
.workspace-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 8px;
}

.workspace-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-title {
  font-weight: 600;
  color: #0f172a;
}

.cat-tag {
  border: none;
  font-size: 11px;
}

/* Apply Center Grid */
.apply-center-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 8px;
}

.category-filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-label {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
}

.templates-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.template-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition: all 0.2s ease;
}

.template-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 15px -3px rgb(0 0 0 / 0.06);
  border-color: #3b82f6;
}

.card-top {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 12px;
}

.template-icon-wrapper {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.template-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 4px;
}

.template-tags {
  display: flex;
  align-items: center;
  gap: 6px;
}

.version-tag {
  font-size: 11px;
  color: #94a3b8;
  background: #f1f5f9;
  padding: 1px 6px;
  border-radius: 4px;
}

.template-desc {
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
  margin: 0 0 16px;
  min-height: 38px;
}

.card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #f1f5f9;
  padding-top: 12px;
}

.code-text {
  font-size: 12px;
  color: #94a3b8;
  font-family: monospace;
}

/* Definitions Management */
.definitions-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 8px;
}

.def-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.left-tips {
  font-size: 13px;
  color: #64748b;
}

.def-title-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ver-badge {
  font-size: 11px;
  color: #64748b;
  background: #f1f5f9;
  padding: 1px 6px;
  border-radius: 4px;
}

.data-table {
  width: 100%;
}

.workflow-service-alert {
  margin-top: 16px;
}

.service-alert-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  flex-wrap: wrap;
  gap: 12px;
}

.service-alert-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.service-alert-title {
  font-weight: 600;
  font-size: 14px;
  color: #b45309;
}

.service-alert-desc {
  font-size: 13px;
  color: #78350f;
}
</style>
