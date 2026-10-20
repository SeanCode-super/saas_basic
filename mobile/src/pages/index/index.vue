<template>
  <view class="mobile-workbench">
    <!-- 顶部品牌与租户问候栏 -->
    <view class="workbench-header">
      <view class="header-tenant">
        <text class="tenant-tag">多租户平台</text>
        <text class="greeting-text">你好，{{ userName }}</text>
      </view>
      <view class="header-avatar">
        <text class="avatar-text">{{ userInitial }}</text>
      </view>
    </view>

    <!-- 关键指标卡片 -->
    <view class="kpi-card-row">
      <view class="kpi-card bg-blue">
        <text class="kpi-num">{{ pendingCount }}</text>
        <text class="kpi-label">待我审批</text>
      </view>
      <view class="kpi-card bg-indigo">
        <text class="kpi-num">{{ lowStockCount }}</text>
        <text class="kpi-label">库存预警</text>
      </view>
      <view class="kpi-card bg-emerald">
        <text class="kpi-num">99.9%</text>
        <text class="kpi-label">系统可用性</text>
      </view>
    </view>

    <!-- 金刚区快捷导航 -->
    <view class="grid-section">
      <view class="section-title"><text>常用数字化工具</text></view>
      <view class="grid-box">
        <view class="grid-item" @click="navTo('/pages/workflow/list')">
          <view class="icon-circle icon-orange"><text>📝</text></view>
          <text class="grid-label">移动审批</text>
        </view>
        <view class="grid-item" @click="navTo('/pages/erp/inventory')">
          <view class="icon-circle icon-blue"><text>📦</text></view>
          <text class="grid-label">多仓库存</text>
        </view>
        <view class="grid-item" @click="scanCode">
          <view class="icon-circle icon-purple"><text>📷</text></view>
          <text class="grid-label">扫码核销</text>
        </view>
        <view class="grid-item" @click="navTo('/pages/profile/profile')">
          <view class="icon-circle icon-green"><text>👤</text></view>
          <text class="grid-label">租户切换</text>
        </view>
      </view>
    </view>

    <!-- 最新待办速览 -->
    <view class="recent-tasks-section">
      <view class="section-title-row">
        <text class="section-title">今日最新审批待办</text>
        <text class="more-link" @click="navTo('/pages/workflow/list')">查看全部 &gt;</text>
      </view>

      <view v-if="tasks.length === 0" class="empty-box">
        <text class="empty-text">🎉 太棒了，当前暂无待办事项！</text>
      </view>

      <view v-else class="task-list">
        <view v-for="t in tasks.slice(0, 3)" :key="t.id" class="task-item">
          <view class="task-meta">
            <text class="task-no">{{ t.instanceNo }}</text>
            <text class="task-node">{{ t.nodeName }}</text>
          </view>
          <text class="task-title">{{ t.instanceTitle }}</text>
          <view class="task-footer">
            <text class="task-initiator">发起人：{{ t.initiatorName }}</text>
            <text class="task-time">{{ t.createdAt?.substring(11, 16) }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { apiGetPendingTasks } from "../../api/workflow";
import { apiGetInventory } from "../../api/erp";

const userName = ref("平台管理员");
const userInitial = ref("管");
const pendingCount = ref(0);
const lowStockCount = ref(0);
const tasks = ref<any[]>([]);

async function loadData() {
  try {
    const taskList = await apiGetPendingTasks().catch(() => []);
    tasks.value = taskList;
    pendingCount.value = taskList.length;

    const invList = await apiGetInventory().catch(() => []);
    lowStockCount.value = invList.filter((i: any) => i.isLowStock).length;
  } catch {}
}

function navTo(url: string) {
  uni.switchTab({ url, fail: () => uni.navigateTo({ url }) });
}

function scanCode() {
  uni.scanCode({
    success: (res) => {
      uni.showModal({
        title: "条码扫描识别成功",
        content: `扫码结果：${res.result}`,
        showCancel: false
      });
    },
    fail: () => {
      uni.showToast({ title: "扫码取消或未开启相机权限", icon: "none" });
    }
  });
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.mobile-workbench {
  min-height: 100vh;
  background-color: #f1f5f9;
  padding: 16px;
  box-sizing: border-box;
}

.workbench-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-top: 8px;
}

.tenant-tag {
  display: inline-block;
  font-size: 11px;
  color: #0284c7;
  background-color: #e0f2fe;
  padding: 2px 8px;
  border-radius: 9999px;
  margin-bottom: 4px;
}

.greeting-text {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}

.header-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0284c7 0%, #38bdf8 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-text {
  color: #ffffff;
  font-size: 18px;
  font-weight: 600;
}

.kpi-card-row {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.kpi-card {
  flex: 1;
  padding: 14px 12px;
  border-radius: 12px;
  color: #ffffff;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.08);
}

.bg-blue { background: linear-gradient(135deg, #2563eb 0%, #3b82f6 100%); }
.bg-indigo { background: linear-gradient(135deg, #4f46e5 0%, #6366f1 100%); }
.bg-emerald { background: linear-gradient(135deg, #059669 0%, #10b981 100%); }

.kpi-num {
  display: block;
  font-size: 24px;
  font-weight: 800;
  margin-bottom: 2px;
}

.kpi-label {
  font-size: 11px;
  opacity: 0.9;
}

.grid-section {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px;
  margin-bottom: 20px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 14px;
}

.grid-box {
  display: flex;
  justify-content: space-around;
}

.grid-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.icon-circle {
  width: 50px;
  height: 50px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}

.icon-orange { background: #ffedd5; }
.icon-blue { background: #dbeafe; }
.icon-purple { background: #f3e8ff; }
.icon-green { background: #dcfce7; }

.grid-label {
  font-size: 12px;
  color: #475569;
}

.recent-tasks-section {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.more-link {
  font-size: 12px;
  color: #0284c7;
}

.empty-box {
  padding: 24px 0;
  text-align: center;
}

.empty-text {
  font-size: 13px;
  color: #94a3b8;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.task-item {
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.task-meta {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
}

.task-no {
  font-size: 11px;
  font-family: monospace;
  color: #64748b;
}

.task-node {
  font-size: 11px;
  color: #0284c7;
  background: #e0f2fe;
  padding: 1px 6px;
  border-radius: 4px;
}

.task-title {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 6px;
}

.task-footer {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #94a3b8;
}
</style>
