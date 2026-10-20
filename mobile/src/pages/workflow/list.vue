<template>
  <view class="mobile-workflow-page">
    <view class="page-header">
      <text class="page-title">待办任务审批</text>
      <text class="page-desc">支持在线查看工单并一键办理同意或驳回</text>
    </view>

    <view v-if="loading" class="loading-state">
      <text>加载审批任务中...</text>
    </view>

    <view v-else-if="tasks.length === 0" class="empty-state">
      <text class="empty-icon">☕</text>
      <text class="empty-text">当前暂无待您审批的任务</text>
    </view>

    <view v-else class="tasks-container">
      <view v-for="task in tasks" :key="task.id" class="task-card">
        <view class="card-top">
          <text class="instance-no">{{ task.instanceNo }}</text>
          <text class="node-badge">{{ task.nodeName }}</text>
        </view>

        <text class="instance-title">{{ task.instanceTitle }}</text>

        <view class="card-details">
          <text class="detail-line">发起人员：{{ task.initiatorName }}</text>
          <text class="detail-line">发起时间：{{ task.createdAt }}</text>
        </view>

        <!-- 快捷操作按钮组 -->
        <view class="card-actions">
          <button class="btn btn-reject" @click="handleReject(task)">驳回申请</button>
          <button class="btn btn-agree" @click="handleAgree(task)">同意审批</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { apiGetPendingTasks, apiHandleTask } from "../../api/workflow";

const loading = ref(false);
const tasks = ref<any[]>([]);

async function loadTasks() {
  loading.value = true;
  try {
    tasks.value = await apiGetPendingTasks();
  } catch {
  } finally {
    loading.value = false;
  }
}

async function handleAgree(task: any) {
  uni.showModal({
    title: "审批确认",
    content: `确认【同意】单据 ${task.instanceNo} 吗？`,
    success: async (res) => {
      if (res.confirm) {
        try {
          await apiHandleTask(task.id, {
            action: "AGREE",
            comment: "移动端快捷审批同意通过"
          });
          uni.showToast({ title: "已同意", icon: "success" });
          loadTasks();
        } catch {}
      }
    }
  });
}

async function handleReject(task: any) {
  uni.showModal({
    title: "驳回确认",
    content: `确认【驳回】单据 ${task.instanceNo} 吗？`,
    success: async (res) => {
      if (res.confirm) {
        try {
          await apiHandleTask(task.id, {
            action: "REJECT",
            comment: "移动端审批驳回退回"
          });
          uni.showToast({ title: "已驳回", icon: "none" });
          loadTasks();
        } catch {}
      }
    }
  });
}

onMounted(() => {
  loadTasks();
});
</script>

<style scoped>
.mobile-workflow-page {
  min-height: 100vh;
  background-color: #f8fafc;
  padding: 16px;
  box-sizing: border-box;
}

.page-header {
  margin-bottom: 16px;
}

.page-title {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}

.page-desc {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

.tasks-container {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.task-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  border: 1px solid #e2e8f0;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.instance-no {
  font-family: monospace;
  font-size: 12px;
  color: #64748b;
}

.node-badge {
  background: #eff6ff;
  color: #2563eb;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
}

.instance-title {
  display: block;
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 10px;
}

.card-details {
  background: #f8fafc;
  padding: 8px 10px;
  border-radius: 6px;
  margin-bottom: 14px;
}

.detail-line {
  display: block;
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
}

.card-actions {
  display: flex;
  gap: 12px;
}

.btn {
  flex: 1;
  height: 38px;
  line-height: 38px;
  font-size: 13px;
  border-radius: 8px;
  border: none;
}

.btn-reject {
  background: #fee2e2;
  color: #ef4444;
}

.btn-agree {
  background: #2563eb;
  color: #ffffff;
}

.empty-state {
  padding: 60px 0;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  display: block;
  margin-bottom: 12px;
}

.empty-text {
  font-size: 14px;
  color: #94a3b8;
}
</style>
