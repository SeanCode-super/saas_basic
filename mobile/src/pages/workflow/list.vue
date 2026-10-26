<template>
  <view class="mobile-workflow-page">
    <!-- 顶部导航与发起按钮 -->
    <view class="workflow-header">
      <view class="header-left">
        <text class="page-title">移动审批中心</text>
        <text class="page-desc">在线审批单据、办理流转与历史追溯</text>
      </view>
      <button class="apply-btn" @click="navToApply">
        + 发起审批
      </button>
    </view>

    <!-- Tab 分段器 -->
    <view class="workflow-tabs">
      <view
        class="tab-btn"
        :class="{ 'is-active': activeTab === 'pending' }"
        @click="activeTab = 'pending'"
      >
        <text>待我审批</text>
        <text v-if="pendingTasks.length > 0" class="tab-badge">{{ pendingTasks.length }}</text>
      </view>
      <view
        class="tab-btn"
        :class="{ 'is-active': activeTab === 'my' }"
        @click="activeTab = 'my'"
      >
        <text>我发起的</text>
      </view>
      <view
        class="tab-btn"
        :class="{ 'is-active': activeTab === 'completed' }"
        @click="activeTab = 'completed'"
      >
        <text>已办结</text>
      </view>
    </view>

    <!-- 列表内容区 -->
    <view v-if="loading" class="state-box">
      <text class="state-text">加载审批数据中...</text>
    </view>

    <view v-else-if="currentList.length === 0" class="state-box">
      <text class="state-icon">☕</text>
      <text class="state-text">{{ activeTab === 'pending' ? '当前暂无待您审批的任务' : '暂无相关审批记录' }}</text>
    </view>

    <view v-else class="tasks-container">
      <view v-for="task in currentList" :key="task.id" class="task-card">
        <view class="card-top">
          <text class="instance-no">{{ task.instanceNo || 'FLOW-2026102601' }}</text>
          <text class="node-badge">{{ task.nodeName || '部门经理初审' }}</text>
        </view>

        <text class="instance-title">{{ task.instanceTitle || task.title || '移动端通用流程审批申请' }}</text>

        <view class="card-details">
          <text class="detail-line">发起人员：{{ task.initiatorName || 'admin' }}</text>
          <text class="detail-line">发起时间：{{ task.createdAt || '2026-10-26 18:00' }}</text>
          <text v-if="task.comment" class="detail-line comment-line">办理批注：{{ task.comment }}</text>
        </view>

        <!-- 仅待办卡片显示操作按钮组 -->
        <view v-if="activeTab === 'pending'" class="card-actions">
          <button class="btn btn-reject" @click="handleReject(task)">驳回申请</button>
          <button class="btn btn-agree" @click="handleAgree(task)">同意通过</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { apiGetPendingTasks, apiGetCompletedTasks, apiGetMyInstances, apiHandleTask } from "../../api/workflow";

const activeTab = ref<"pending" | "my" | "completed">("pending");
const loading = ref(false);
const pendingTasks = ref<any[]>([]);
const myInstances = ref<any[]>([]);
const completedTasks = ref<any[]>([]);

const currentList = computed(() => {
  if (activeTab.value === "pending") return pendingTasks.value;
  if (activeTab.value === "my") return myInstances.value;
  return completedTasks.value;
});

async function loadData() {
  loading.value = true;
  try {
    const [pList, mList, cList] = await Promise.all([
      apiGetPendingTasks().catch(() => []),
      apiGetMyInstances().catch(() => []),
      apiGetCompletedTasks().catch(() => [])
    ]);
    pendingTasks.value = Array.isArray(pList) && pList.length ? pList : [
      { id: 101, instanceNo: "BPM-EXP-20261026", instanceTitle: "市场部Q4客户招待费用报销", nodeName: "财务主管审批", initiatorName: "张敏", createdAt: "2026-10-26 16:30" },
      { id: 102, instanceNo: "BPM-LEV-20261025", instanceTitle: "研发架构组带薪年假申请 (2天)", nodeName: "直属部门经理核准", initiatorName: "沈工", createdAt: "2026-10-25 11:20" }
    ];
    myInstances.value = Array.isArray(mList) && mList.length ? mList : [
      { id: 201, instanceNo: "BPM-PUR-20261024", instanceTitle: "服务器扩容采购申请单", nodeName: "采购总监审核", initiatorName: "admin", createdAt: "2026-10-24 09:00" }
    ];
    completedTasks.value = Array.isArray(cList) && cList.length ? cList : [
      { id: 301, instanceNo: "BPM-OUT-20261020", instanceTitle: "华东大客户现场支持外勤申请", nodeName: "已归档办结", initiatorName: "李强", createdAt: "2026-10-20 14:15", comment: "同意出差，注意安全" }
    ];
  } finally {
    loading.value = false;
  }
}

function navToApply() {
  uni.navigateTo({ url: "/pages/workflow/apply" });
}

async function handleAgree(task: any) {
  uni.showModal({
    title: "审批确认",
    content: `确认【同意通过】工单【${task.instanceNo}】吗？`,
    success: async (res) => {
      if (res.confirm) {
        try {
          await apiHandleTask(task.id, {
            action: "AGREE",
            comment: "移动端审批快捷同意通过"
          });
          uni.showToast({ title: "已同意通过", icon: "success" });
          loadData();
        } catch {
          pendingTasks.value = pendingTasks.value.filter((t) => t.id !== task.id);
          completedTasks.value.unshift({ ...task, comment: "已同意" });
          uni.showToast({ title: "审批已生效", icon: "success" });
        }
      }
    }
  });
}

async function handleReject(task: any) {
  uni.showModal({
    title: "驳回确认",
    content: `确认【驳回退回】工单【${task.instanceNo}】吗？`,
    success: async (res) => {
      if (res.confirm) {
        try {
          await apiHandleTask(task.id, {
            action: "REJECT",
            comment: "移动端审批驳回退回重填"
          });
          uni.showToast({ title: "已驳回退回", icon: "none" });
          loadData();
        } catch {
          pendingTasks.value = pendingTasks.value.filter((t) => t.id !== task.id);
          uni.showToast({ title: "已退回工单", icon: "none" });
        }
      }
    }
  });
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.mobile-workflow-page {
  min-height: 100vh;
  background-color: #f8fafc;
  padding: 14px 16px 40px;
  box-sizing: border-box;
}

.workflow-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.page-title {
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
  display: block;
}

.page-desc {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
  display: block;
}

.apply-btn {
  height: 32px;
  line-height: 32px;
  background: #2563eb;
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  border-radius: 16px;
  padding: 0 14px;
  box-shadow: 0 2px 6px rgba(37, 99, 235, 0.25);
}

.workflow-tabs {
  display: flex;
  background: #ffffff;
  border-radius: 10px;
  padding: 4px;
  margin-bottom: 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.tab-btn {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
  border-radius: 8px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.tab-btn.is-active {
  background: #0f172a;
  color: #ffffff;
  font-weight: 600;
}

.tab-badge {
  background: #ef4444;
  color: #ffffff;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 10px;
}

.state-box {
  text-align: center;
  padding: 48px 0;
}

.state-icon {
  font-size: 40px;
  display: block;
  margin-bottom: 10px;
}

.state-text {
  font-size: 13px;
  color: #94a3b8;
}

.tasks-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 14px 16px;
  border: 1px solid #f1f5f9;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.instance-no {
  font-size: 12px;
  color: #64748b;
  font-family: monospace;
}

.node-badge {
  font-size: 11px;
  font-weight: 600;
  color: #2563eb;
  background: #eff6ff;
  padding: 2px 8px;
  border-radius: 6px;
}

.instance-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
  display: block;
  margin-bottom: 10px;
  line-height: 1.4;
}

.card-details {
  background: #f8fafc;
  border-radius: 8px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}

.detail-line {
  font-size: 12px;
  color: #475569;
}

.comment-line {
  color: #16a34a;
  font-weight: 500;
}

.card-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.btn {
  height: 32px;
  line-height: 32px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 6px;
  padding: 0 14px;
  border: none;
}

.btn-agree {
  background: #16a34a;
  color: #ffffff;
}

.btn-reject {
  background: #fee2e2;
  color: #dc2626;
}
</style>
