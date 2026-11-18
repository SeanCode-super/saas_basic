<template>
  <view class="mobile-workflow-page">
    <!-- 顶部导航与发起按钮 -->
    <view class="workflow-header">
      <view class="header-left">
        <text class="page-title">移动审批中心</text>
        <text class="page-desc">在线审批单据、办理流转与历史追溯</text>
      </view>
      <button class="apply-btn" @click="navToApply">
        <text class="apply-plus">+</text>
        <text class="apply-text">发起审批</text>
      </button>
    </view>

    <!-- YouTube 同款 iOS 27 晶透标签横向胶囊栏 -->
    <scroll-view scroll-x class="ios27-chip-scroll" :show-scrollbar="false" style="padding-left: 0; padding-right: 0; margin-bottom: 12px;">
      <view class="ios27-chip-group">
        <view
          class="ios27-chip"
          :class="{ 'is-active': activeTab === 'pending' }"
          @click="activeTab = 'pending'"
        >
          <text>待我审批</text>
          <text v-if="pendingTasks.length > 0" class="ios27-chip-badge">{{ pendingTasks.length }}</text>
        </view>
        <view
          class="ios27-chip"
          :class="{ 'is-active': activeTab === 'my' }"
          @click="activeTab = 'my'"
        >
          <text>我发起的</text>
        </view>
        <view
          class="ios27-chip"
          :class="{ 'is-active': activeTab === 'completed' }"
          @click="activeTab = 'completed'"
        >
          <text>已办结</text>
        </view>
      </view>
    </scroll-view>

    <!-- 骨架屏加载态 -->
    <view v-if="loading" class="state-box">
      <AppSkeleton type="list" :count="3" />
    </view>

    <!-- 优雅空状态 -->
    <view v-else-if="currentList.length === 0" class="state-box">
      <AppEmpty
        type="task"
        :title="activeTab === 'pending' ? '当前暂无待办事项' : '暂无相关审批流单据'"
        :desc="activeTab === 'pending' ? '所有待办审批已全部处理完毕，保持高效！' : '点击右上角即可随时发起新业务审批'"
        action-text="刷新同步"
        @action="loadData(true)"
      />
    </view>

    <!-- 单据列表 -->
    <view v-else class="tasks-container">
      <view v-for="task in currentList" :key="task.id" class="task-card" @click="openTaskDetail(task)">
        <view class="card-top">
          <view class="no-wrap">
            <view class="no-dot" />
            <text class="instance-no">{{ task.instanceNo || 'FLOW-2026102601' }}</text>
          </view>
          <text class="node-badge" :class="[task.status === 'COMPLETED' ? 'is-done' : 'is-pending']">
            {{ task.nodeName || '部门初审' }}
          </text>
        </view>

        <view class="instance-title">{{ task.instanceTitle || task.title || '移动端通用流程审批申请' }}</view>

        <!-- 全新轻盈赛博元数据流：呼吸感充盈，彻底替代内嵌方块 -->
        <view class="card-meta-flow">
          <view class="meta-item">
            <view class="meta-avatar">
              <text class="avatar-letter">{{ (task.initiatorName || '管').slice(0, 1) }}</text>
            </view>
            <text class="meta-label">发起人</text>
            <text class="meta-val">{{ task.initiatorName || '平台管理员' }}</text>
          </view>
          <view class="meta-sep" />
          <view class="meta-item">
            <AppIcon name="clock" :size="12" color="#64748b" />
            <text class="meta-val meta-time">{{ task.createdAt || '刚刚' }}</text>
          </view>
        </view>

        <!-- 批注意见气泡 -->
        <view v-if="task.comment" class="card-comment-bubble">
          <text class="comment-icon">💬</text>
          <text class="comment-text">{{ task.comment }}</text>
        </view>

        <!-- 悬浮指令流光栏：主次非对称黄金比例未来感胶囊 -->
        <view v-if="activeTab === 'pending'" class="card-actions" @click.stop>
          <button class="btn btn-reject" @click="openActionModal(task, 'REJECT')">
            <AppIcon name="close" :size="12" color="#64748b" :stroke-width="2.4" />
            <text class="btn-label">驳回退回</text>
          </button>
          <button class="btn btn-agree" @click="openActionModal(task, 'AGREE')">
            <AppIcon name="check" :size="13" color="#ffffff" :stroke-width="2.6" />
            <text class="btn-label">同意核准</text>
          </button>
        </view>
      </view>
    </view>

    <!-- 1. 审批操作弹窗 (同意/驳回) -->
    <AppModal
      v-model:visible="actionModalVisible"
      :title="actionType === 'AGREE' ? '审批同意核准' : '审批单据驳回'"
      :confirm-text="actionType === 'AGREE' ? '确认同意' : '确认驳回'"
      :confirm-type="actionType === 'AGREE' ? 'success' : 'danger'"
      :loading="actionSubmitting"
      @confirm="submitAction"
    >
      <view class="action-dialog-content">
        <text class="dialog-hint">
          当前操作单据：<text class="font-bold">{{ currentSelectedTask?.instanceNo }}</text>
        </text>

        <!-- 快捷短语胶囊 -->
        <view class="preset-chips">
          <view
            v-for="chip in currentPresetChips"
            :key="chip"
            class="chip-pill"
            :class="{ 'is-selected': actionComment === chip }"
            @click="actionComment = chip"
          >
            <text>{{ chip }}</text>
          </view>
        </view>

        <textarea
          v-model="actionComment"
          class="dialog-textarea"
          :placeholder="actionType === 'AGREE' ? '填写审批同意意见批注 (可选)...' : '请说明驳回原因与修改建议 (必填)...'"
        />
      </view>
    </AppModal>

    <!-- 2. 工单全要素详情抽屉 -->
    <AppModal
      v-model:visible="detailModalVisible"
      title="审批单据流转全景"
      mode="sheet"
      :show-cancel="false"
      confirm-text="已知晓返回"
      @confirm="detailModalVisible = false"
    >
      <view v-if="currentSelectedTask" class="detail-drawer-content">
        <view class="detail-group">
          <text class="group-title">📋 基础信息</text>
          <view class="info-grid">
            <view class="grid-item">
              <text class="k">工单编号</text>
              <text class="v">{{ currentSelectedTask.instanceNo }}</text>
            </view>
            <view class="grid-item">
              <text class="k">流程名称</text>
              <text class="v">{{ currentSelectedTask.processName || '通用业务流' }}</text>
            </view>
            <view class="grid-item">
              <text class="k">发起人员</text>
              <text class="v">{{ currentSelectedTask.initiatorName || 'admin' }}</text>
            </view>
            <view class="grid-item">
              <text class="k">当前环节</text>
              <text class="v">{{ currentSelectedTask.nodeName || '处理中' }}</text>
            </view>
          </view>
        </view>

        <view class="detail-group">
          <text class="group-title">📑 申请主题与内容</text>
          <view class="desc-card">
            <text class="desc-title">{{ currentSelectedTask.instanceTitle || currentSelectedTask.title }}</text>
            <text class="desc-text">{{ currentSelectedTask.comment || '申请人在线提单，各审批节点正按规程流转中。' }}</text>
          </view>
        </view>

        <view class="detail-group">
          <text class="group-title">⏱️ 流转轨迹</text>
          <view class="trace-steps">
            <view class="step-row is-done">
              <view class="step-dot" />
              <view class="step-main">
                <text class="step-title">业务单据发起提交</text>
                <text class="step-time">{{ currentSelectedTask.createdAt }}</text>
              </view>
            </view>
            <view class="step-row" :class="[currentSelectedTask.status === 'COMPLETED' ? 'is-done' : 'is-current']">
              <view class="step-dot" />
              <view class="step-main">
                <text class="step-title">{{ currentSelectedTask.nodeName || '节点初审' }}</text>
                <text class="step-time">办理人：{{ currentSelectedTask.assigneeName || '当前处理人' }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </AppModal>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { onPullDownRefresh } from "@dcloudio/uni-app";
import AppIcon from "../../components/ui/AppIcon.vue";
import AppSkeleton from "../../components/ui/AppSkeleton.vue";
import AppEmpty from "../../components/ui/AppEmpty.vue";
import AppModal from "../../components/ui/AppModal.vue";
import {
  apiGetPendingTasks,
  apiGetCompletedTasks,
  apiGetMyInstances,
  apiHandleTask
} from "../../api/workflow";

const activeTab = ref<"pending" | "my" | "completed">("pending");
const loading = ref(false);
const pendingTasks = ref<any[]>([]);
const myInstances = ref<any[]>([]);
const completedTasks = ref<any[]>([]);

// 操作弹窗状态
const actionModalVisible = ref(false);
const actionType = ref<"AGREE" | "REJECT">("AGREE");
const actionComment = ref("");
const actionSubmitting = ref(false);
const currentSelectedTask = ref<any>(null);

// 详情抽屉状态
const detailModalVisible = ref(false);

const agreeChips = ["情况属实，准予批准", "已电话核对，按规程推进", "资料齐全，核准通过"];
const rejectChips = ["单据材料不全，请补充凭据", "发票金额与报销单不符", "业务不合规，予以驳回"];

const currentPresetChips = computed(() => {
  return actionType.value === "AGREE" ? agreeChips : rejectChips;
});

const currentList = computed(() => {
  if (activeTab.value === "pending") return pendingTasks.value;
  if (activeTab.value === "my") return myInstances.value;
  return completedTasks.value;
});

async function loadData(isRefresh = false) {
  if (!isRefresh && currentList.value.length === 0) {
    loading.value = true;
  }
  try {
    const [pList, mList, cList] = await Promise.all([
      apiGetPendingTasks().catch(() => []),
      apiGetMyInstances().catch(() => []),
      apiGetCompletedTasks().catch(() => [])
    ]);

    const formatTask = (t: any) => ({
      ...t,
      instanceNo: (t.instanceNo || "SP-2026102601").replace("WF", "SP-"),
      initiatorName: t.initiatorName === "admin" ? "系统管理员" : (t.initiatorName || "经办员工"),
      createdAt: t.createdAt ? String(t.createdAt).replace("T", " ").substring(0, 16) : "刚刚"
    });

    pendingTasks.value = Array.isArray(pList) ? pList.map(formatTask) : [];
    myInstances.value = Array.isArray(mList) ? mList.map(formatTask) : [];
    completedTasks.value = Array.isArray(cList) ? cList.map(formatTask) : [];
  } finally {
    loading.value = false;
    uni.stopPullDownRefresh();
  }
}

onPullDownRefresh(() => {
  loadData(true);
});

function navToApply() {
  uni.navigateTo({ url: "/pages/workflow/apply" });
}

function openTaskDetail(task: any) {
  currentSelectedTask.value = task;
  detailModalVisible.value = true;
}

function openActionModal(task: any, type: "AGREE" | "REJECT") {
  currentSelectedTask.value = task;
  actionType.value = type;
  actionComment.value = type === "AGREE" ? "情况属实，准予批准" : "单据材料不全，请补充凭据";
  actionModalVisible.value = true;
}

async function submitAction() {
  if (!currentSelectedTask.value) return;
  if (actionType.value === "REJECT" && !actionComment.value.trim()) {
    uni.showToast({ title: "驳回时请填写原因批注", icon: "none" });
    return;
  }

  actionSubmitting.value = true;
  try {
    await apiHandleTask(currentSelectedTask.value.id, {
      action: actionType.value,
      comment: actionComment.value.trim()
    });
    uni.showToast({
      title: actionType.value === "AGREE" ? "审批核准通过" : "已驳回办结",
      icon: "success"
    });
    actionModalVisible.value = false;
    loadData(true);
  } catch (err: any) {
    uni.showToast({ title: err?.message || "办理操作失败", icon: "none" });
  } finally {
    actionSubmitting.value = false;
  }
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
/* =============================================================================
   iOS 27 Liquid Glass 移动审批待办中心
   ============================================================================= */

.mobile-workflow-page {
  min-height: 100vh;
  background-color: transparent;
  padding: calc(14px + env(safe-area-inset-top)) 14px calc(96px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.workflow-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  padding: 14px 16px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 8px 24px -4px rgba(12, 34, 56, 0.05);
}

.page-title {
  font-size: 19px;
  font-weight: 750;
  color: #0f172a;
  letter-spacing: -0.3px;
}

.page-desc {
  font-size: 11.5px;
  color: #64748b;
  margin-top: 2px;
  display: block;
  font-weight: 500;
}

.apply-btn {
  height: 34px;
  line-height: 34px;
  padding: 0 14px;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #0369a1 100%);
  color: #ffffff;
  border-radius: 9999px;
  font-size: 12.5px;
  font-weight: 650;
  border: 1px solid rgba(255, 255, 255, 0.22);
  box-shadow:
    inset 0 1px 1px 0 rgba(255, 255, 255, 0.40),
    0 4px 14px rgba(15, 23, 42, 0.20);
  display: flex;
  align-items: center;
  gap: 5px;
  transition: all 0.16s ease;
}

.apply-btn:active {
  transform: scale(0.96);
  opacity: 0.92;
}

.apply-plus {
  font-size: 15px;
  font-weight: 700;
  line-height: 1;
}

.apply-text {
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.2px;
}

.workflow-tabs {
  display: flex;
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(24px) saturate(190%);
  -webkit-backdrop-filter: blur(24px) saturate(190%);
  padding: 4px;
  border-radius: 9999px;
  border: 1px solid rgba(255, 255, 255, 0.88);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.95), 0 4px 14px rgba(12, 34, 56, 0.04);
  margin-bottom: 14px;
}

.tab-btn {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: 13px;
  color: #64748b;
  font-weight: 550;
  border-radius: 9999px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.tab-btn.is-active {
  background: #ffffff;
  color: #2563eb;
  font-weight: 700;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
}

.tab-badge {
  background: linear-gradient(135deg, #ef4444, #dc2626);
  color: #ffffff;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 999px;
  line-height: 1.2;
}

.tasks-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-card {
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  padding: 16px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 10px 25px -5px rgba(12, 34, 56, 0.05);
  transition: transform 0.15s ease;
}

.task-card:active {
  transform: scale(0.99);
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.no-wrap {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.no-dot {
  width: 6px;
  height: 6px;
  border-radius: 9999px;
  background: #38bdf8;
  box-shadow: 0 0 8px rgba(56, 189, 248, 0.6);
}

.instance-no {
  font-size: 11.5px;
  color: #64748b;
  font-family: monospace;
  font-weight: 600;
  letter-spacing: -0.2px;
}

.node-badge {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 9999px;
  font-weight: 650;
  letter-spacing: 0.2px;
}

.node-badge.is-pending {
  background: rgba(37, 99, 235, 0.08);
  border: 1px solid rgba(37, 99, 235, 0.22);
  color: #2563eb;
  box-shadow: 0 2px 6px rgba(37, 99, 235, 0.04);
}

.node-badge.is-done {
  background: rgba(16, 185, 129, 0.08);
  border: 1px solid rgba(16, 185, 129, 0.22);
  color: #059669;
}

.instance-title {
  display: block;
  font-size: 15.5px;
  font-weight: 750;
  color: #0f172a;
  line-height: 1.45;
  margin-top: 4px;
  margin-bottom: 14px;
}

/* 全新轻盈赛博元数据流：呼吸感充盈，彻底替代内嵌方块 */
.card-meta-flow {
  display: flex;
  align-items: center;
  gap: 12px;
  background: rgba(248, 250, 252, 0.75);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 12px;
  padding: 8px 12px;
  margin-bottom: 15px;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.meta-avatar {
  width: 20px;
  height: 20px;
  border-radius: 9999px;
  background: linear-gradient(135deg, #0284c7 0%, #2563eb 100%);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-letter {
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
}

.meta-label {
  font-size: 11.5px;
  color: #64748b;
  font-weight: 500;
}

.meta-val {
  font-size: 12px;
  color: #1e293b;
  font-weight: 600;
}

.meta-sep {
  width: 1px;
  height: 14px;
  background: rgba(203, 213, 225, 0.7);
}

.meta-time {
  color: #64748b;
  font-size: 11.5px;
  font-weight: 500;
}

.card-comment-bubble {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  background: rgba(239, 246, 255, 0.7);
  border: 1px solid rgba(191, 219, 254, 0.75);
  border-radius: 10px;
  padding: 6px 10px;
  margin-bottom: 14px;
}

.comment-icon {
  font-size: 11px;
  line-height: 1.3;
}

.comment-text {
  font-size: 12px;
  color: #1d4ed8;
  font-weight: 550;
  line-height: 1.4;
}

/* 悬浮指令流光栏：主次非对称黄金比例未来感胶囊 */
.card-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 14px;
  margin-top: 4px;
  border-top: 1px solid rgba(226, 232, 240, 0.70);
}

.btn {
  height: 40px;
  line-height: 40px;
  border-radius: 9999px;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  box-sizing: border-box;
  transition: all 0.2s cubic-bezier(0.2, 0.8, 0.2, 1);
  cursor: pointer;
}

.btn::after,
.apply-btn::after {
  border: none !important;
  display: none !important;
}

.btn:active {
  transform: scale(0.975);
}

/* 驳回退回：38% 黄金辅助比例，高端雾面半透轻玻 + 悬停微霓虹，告别死板粉色大块 */
.btn-reject {
  flex: 0 0 38%;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.90) 100%);
  color: #475569;
  border: 1px solid rgba(226, 232, 240, 0.95);
  box-shadow:
    inset 0 1px 1px 0 #ffffff,
    0 2px 8px rgba(15, 23, 42, 0.04);
}

.btn-reject:active {
  background: linear-gradient(135deg, rgba(255, 241, 242, 0.95) 0%, rgba(254, 226, 226, 0.90) 100%);
  color: #e11d48;
  border-color: rgba(254, 205, 211, 0.90);
}

/* 同意核准：62% 黄金主导比例，赛博流光量子极光，高对比度立体悬浮 */
.btn-agree {
  flex: 1;
  background: linear-gradient(135deg, #06b6d4 0%, #2563eb 50%, #4f46e5 100%);
  color: #ffffff;
  border: 1px solid rgba(255, 255, 255, 0.45);
  box-shadow:
    inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.65),
    inset 0 -1.5px 2px 0 rgba(15, 23, 42, 0.22),
    0 8px 24px -4px rgba(6, 182, 212, 0.42),
    0 4px 12px -2px rgba(37, 99, 235, 0.35);
}

.btn-agree:active {
  background: linear-gradient(135deg, #0891b2 0%, #1d4ed8 50%, #4338ca 100%);
  box-shadow:
    inset 0 1px 2px 0 rgba(0, 0, 0, 0.25),
    0 4px 12px -2px rgba(37, 99, 235, 0.35);
}

.btn-label {
  font-size: 13.5px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

/* 审批操作弹窗内样式 */
.action-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dialog-hint {
  font-size: 13px;
  color: #475569;
}

.font-bold {
  font-weight: 650;
  color: #0f172a;
}

.preset-chips {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.chip-pill {
  padding: 8px 12px;
  border-radius: 8px;
  background: #f1f5f9;
  font-size: 12px;
  color: #475569;
  border: 1px solid transparent;
  transition: all 0.15s;
}

.chip-pill.is-selected {
  background: rgba(2, 132, 199, 0.1);
  border-color: #0284c7;
  color: #0284c7;
  font-weight: 500;
}

.dialog-textarea {
  width: 100%;
  height: 80px;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  padding: 10px;
  font-size: 13px;
  color: #0f172a;
  box-sizing: border-box;
}

/* 详情抽屉样式 */
.detail-drawer-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.group-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  background: #f8fafc;
  padding: 12px;
  border-radius: 12px;
}

.grid-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.grid-item .k {
  font-size: 11px;
  color: #64748b;
}

.grid-item .v {
  font-size: 12px;
  font-weight: 500;
  color: #0f172a;
}

.desc-card {
  background: #f8fafc;
  padding: 12px;
  border-radius: 12px;
}

.desc-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  display: block;
  margin-bottom: 6px;
}

.desc-text {
  font-size: 12px;
  color: #475569;
  line-height: 1.5;
}

.trace-steps {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-left: 6px;
}

.step-row {
  display: flex;
  gap: 12px;
  position: relative;
}

.step-row:not(:last-child)::after {
  content: "";
  position: absolute;
  top: 14px;
  left: 5px;
  bottom: -10px;
  width: 2px;
  background: #e2e8f0;
}

.step-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #94a3b8;
  margin-top: 3px;
}

.step-row.is-done .step-dot {
  background: #10b981;
}

.step-row.is-current .step-dot {
  background: #0284c7;
  box-shadow: 0 0 0 3px rgba(2, 132, 199, 0.2);
}

.step-main {
  display: flex;
  flex-direction: column;
}

.step-title {
  font-size: 13px;
  font-weight: 500;
  color: #0f172a;
}

.step-time {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}
</style>
