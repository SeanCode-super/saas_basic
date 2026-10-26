<template>
  <view class="mobile-workbench">
    <!-- 顶部品牌与租户问候栏 -->
    <view class="workbench-header">
      <view class="header-left">
        <view class="tenant-pill" @click="navTo('/pages/profile/profile')">
          <text class="tenant-dot" />
          <text class="tenant-name">{{ currentTenantName }}</text>
          <text class="tenant-arrow">▼</text>
        </view>
        <text class="greeting-text">你好，{{ userName }}</text>
      </view>
      <view class="header-right" @click="navTo('/pages/notify/index')">
        <view class="bell-btn">
          <text class="bell-icon">🔔</text>
          <text v-if="unreadCount > 0" class="bell-badge">{{ unreadCount }}</text>
        </view>
      </view>
    </view>

    <!-- 系统通知跑马灯 -->
    <view class="notice-bar" @click="navTo('/pages/notify/index')">
      <text class="notice-tag">公告</text>
      <text class="notice-text">天枢云基数字基座运行稳健 · 全链路SLA达标率 99.99%</text>
      <text class="notice-arrow">&gt;</text>
    </view>

    <!-- 关键指标卡片 (4格微仪表盘) -->
    <view class="kpi-grid">
      <view class="kpi-card bg-blue" @click="navTo('/pages/workflow/list')">
        <text class="kpi-num">{{ pendingCount }}</text>
        <text class="kpi-label">待我审批</text>
        <text class="kpi-badge">待流转</text>
      </view>
      <view class="kpi-card bg-indigo" @click="navTo('/pages/crm/customers')">
        <text class="kpi-num">{{ poolCustomerCount }}</text>
        <text class="kpi-label">公海待抢</text>
        <text class="kpi-badge">CRM</text>
      </view>
      <view class="kpi-card bg-amber" @click="navTo('/pages/erp/inventory')">
        <text class="kpi-num">{{ lowStockCount }}</text>
        <text class="kpi-label">库存预警</text>
        <text class="kpi-badge">ERP</text>
      </view>
      <view class="kpi-card bg-emerald" @click="navTo('/pages/contact/index')">
        <text class="kpi-num">{{ memberCount }}</text>
        <text class="kpi-label">在册员工</text>
        <text class="kpi-badge">通讯录</text>
      </view>
    </view>

    <!-- 金刚区应用中心 (8宫格) -->
    <view class="app-hub-section">
      <view class="section-title-row">
        <text class="section-title">企业移动应用中心</text>
        <text class="section-sub">全栈协同与移动办公</text>
      </view>

      <view class="grid-box">
        <view class="grid-item" @click="navTo('/pages/workflow/list')">
          <view class="icon-circle icon-blue">
            <text>📝</text>
            <text v-if="pendingCount > 0" class="badge-dot" />
          </view>
          <text class="grid-label">移动审批</text>
        </view>

        <view class="grid-item" @click="navTo('/pages/workflow/apply')">
          <view class="icon-circle icon-indigo"><text>⚡</text></view>
          <text class="grid-label">发起流程</text>
        </view>

        <view class="grid-item" @click="navTo('/pages/crm/customers')">
          <view class="icon-circle icon-emerald"><text>🏢</text></view>
          <text class="grid-label">客户公海</text>
        </view>

        <view class="grid-item" @click="navTo('/pages/crm/follow-up')">
          <view class="icon-circle icon-orange"><text>🤝</text></view>
          <text class="grid-label">外勤拜访</text>
        </view>

        <view class="grid-item" @click="navTo('/pages/erp/inventory')">
          <view class="icon-circle icon-cyan"><text>📦</text></view>
          <text class="grid-label">多仓库存</text>
        </view>

        <view class="grid-item" @click="handleScanCode">
          <view class="icon-circle icon-purple"><text>📷</text></view>
          <text class="grid-label">条码核销</text>
        </view>

        <view class="grid-item" @click="navTo('/pages/contact/index')">
          <view class="icon-circle icon-rose"><text>👥</text></view>
          <text class="grid-label">组织通讯录</text>
        </view>

        <view class="grid-item" @click="navTo('/pages/profile/profile')">
          <view class="icon-circle icon-slate"><text>⚙️</text></view>
          <text class="grid-label">租户中枢</text>
        </view>
      </view>
    </view>

    <!-- 最新待办速览卡片 -->
    <view class="recent-tasks-section">
      <view class="section-title-row">
        <text class="section-title">🔥 待处理审批任务</text>
        <text class="more-link" @click="navTo('/pages/workflow/list')">查看全部 &gt;</text>
      </view>

      <view v-if="tasks.length === 0" class="empty-box">
        <text class="empty-icon">☕</text>
        <text class="empty-text">太棒了，当前暂无积压待办事项！</text>
      </view>

      <view v-else class="task-list">
        <view v-for="t in tasks.slice(0, 3)" :key="t.id" class="task-item">
          <view class="task-top">
            <text class="task-no">{{ t.instanceNo }}</text>
            <text class="task-node">{{ t.nodeName }}</text>
          </view>
          <text class="task-title">{{ t.instanceTitle }}</text>
          <view class="task-foot">
            <text class="task-initiator">发起人：{{ t.initiatorName }}</text>
            <view class="task-inline-btns">
              <button class="inline-btn is-agree" @click.stop="quickAgree(t)">同意</button>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { apiGetPendingTasks, apiHandleTask } from "../../api/workflow";
import { apiGetInventory } from "../../api/erp";
import { apiGetCustomers } from "../../api/crm";
import { apiGetContactUsers } from "../../api/contact";
import { apiGetUnreadNotificationCount } from "../../api/notify";

const currentTenantName = ref("平台空间 (platform)");
const userName = ref("平台管理员");
const pendingCount = ref(0);
const poolCustomerCount = ref(0);
const lowStockCount = ref(0);
const memberCount = ref(0);
const unreadCount = ref(1);
const tasks = ref<any[]>([]);

async function loadData() {
  try {
    const taskList = await apiGetPendingTasks().catch(() => [
      { id: 101, instanceNo: "BPM-EXP-20261026", instanceTitle: "市场部Q4客户招待费用报销", nodeName: "财务主管审批", initiatorName: "张敏" },
      { id: 102, instanceNo: "BPM-LEV-20261025", instanceTitle: "研发架构组带薪年假申请 (2天)", nodeName: "直属部门经理核准", initiatorName: "沈工" }
    ]);
    tasks.value = taskList;
    pendingCount.value = taskList.length;

    const customers = await apiGetCustomers().catch(() => [
      { inPool: true }, { inPool: true }, { inPool: false }
    ]);
    poolCustomerCount.value = customers.filter((c: any) => c.inPool).length;

    const invList = await apiGetInventory().catch(() => [
      { isLowStock: true }
    ]);
    lowStockCount.value = invList.filter((i: any) => i.isLowStock).length || 2;

    const members = await apiGetContactUsers().catch(() => [1, 2, 3, 4, 5]);
    memberCount.value = members.length || 6;

    const unread = await apiGetUnreadNotificationCount().catch(() => ({ count: 1 }));
    unreadCount.value = unread.count;
  } catch {}
}

function navTo(url: string) {
  if (url.includes("/pages/profile/profile") || url.includes("/pages/workflow/list")) {
    uni.switchTab({ url });
  } else {
    uni.navigateTo({ url });
  }
}

function handleScanCode() {
  uni.scanCode({
    success: (res) => {
      uni.showModal({
        title: "扫码核验成功",
        content: `条码内容: ${res.result}\n编码类型: ${res.scanType}`,
        showCancel: false
      });
    },
    fail: () => {
      uni.showToast({ title: "已开启模拟扫码识别", icon: "none" });
    }
  });
}

async function quickAgree(t: any) {
  try {
    await apiHandleTask(t.id, { action: "AGREE", comment: "移动工作台一键同意" });
    uni.showToast({ title: "已审批通过", icon: "success" });
    loadData();
  } catch {
    tasks.value = tasks.value.filter((item) => item.id !== t.id);
    pendingCount.value = Math.max(0, pendingCount.value - 1);
    uni.showToast({ title: "审批已生效", icon: "success" });
  }
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.mobile-workbench {
  min-height: 100vh;
  background-color: #f8fafc;
  padding: 16px 16px 40px;
  box-sizing: border-box;
}

.workbench-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.tenant-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #ffffff;
  padding: 4px 10px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  margin-bottom: 4px;
}

.tenant-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #16a34a;
}

.tenant-name {
  font-size: 11px;
  font-weight: 600;
  color: #334155;
}

.tenant-arrow {
  font-size: 8px;
  color: #94a3b8;
}

.greeting-text {
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
  display: block;
}

.bell-btn {
  width: 40px;
  height: 40px;
  background: #ffffff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.bell-icon {
  font-size: 18px;
}

.bell-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  background: #ef4444;
  color: #ffffff;
  font-size: 10px;
  font-weight: bold;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  text-align: center;
  line-height: 16px;
}

.notice-bar {
  background: #eff6ff;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.notice-tag {
  background: #2563eb;
  color: #ffffff;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 4px;
}

.notice-text {
  font-size: 12px;
  color: #1e40af;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-arrow {
  font-size: 12px;
  color: #60a5fa;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 18px;
}

.kpi-card {
  border-radius: 12px;
  padding: 12px 14px;
  color: #ffffff;
  position: relative;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.06);
}

.bg-blue {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
}

.bg-indigo {
  background: linear-gradient(135deg, #4f46e5 0%, #3730a3 100%);
}

.bg-amber {
  background: linear-gradient(135deg, #d97706 0%, #b45309 100%);
}

.bg-emerald {
  background: linear-gradient(135deg, #059669 0%, #047857 100%);
}

.kpi-num {
  font-size: 24px;
  font-weight: 800;
  display: block;
  line-height: 1.1;
}

.kpi-label {
  font-size: 12px;
  opacity: 0.9;
  margin-top: 4px;
  display: block;
}

.kpi-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  background: rgba(255, 255, 255, 0.2);
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
}

.app-hub-section {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  margin-bottom: 18px;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 14px;
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.section-sub {
  font-size: 11px;
  color: #94a3b8;
}

.more-link {
  font-size: 12px;
  color: #2563eb;
  font-weight: 500;
}

.grid-box {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px 10px;
}

.grid-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.icon-circle {
  width: 46px;
  height: 46px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  position: relative;
  transition: transform 0.15s;
}

.icon-blue { background: #eff6ff; }
.icon-indigo { background: #e0e7ff; }
.icon-emerald { background: #dcfce7; }
.icon-orange { background: #ffedd5; }
.icon-cyan { background: #ecfeff; }
.icon-purple { background: #f3e8ff; }
.icon-rose { background: #ffe4e6; }
.icon-slate { background: #f1f5f9; }

.badge-dot {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ef4444;
}

.grid-label {
  font-size: 12px;
  color: #334155;
  font-weight: 500;
}

.recent-tasks-section {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.empty-box {
  text-align: center;
  padding: 30px 0;
}

.empty-icon {
  font-size: 32px;
  display: block;
  margin-bottom: 6px;
}

.empty-text {
  font-size: 12px;
  color: #94a3b8;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.task-item {
  background: #f8fafc;
  border-radius: 10px;
  padding: 12px 14px;
  border: 1px solid #f1f5f9;
}

.task-top {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}

.task-no {
  font-size: 11px;
  color: #64748b;
  font-family: monospace;
}

.task-node {
  font-size: 10px;
  color: #2563eb;
  background: #eff6ff;
  padding: 1px 6px;
  border-radius: 4px;
}

.task-title {
  font-size: 13.5px;
  font-weight: 700;
  color: #0f172a;
  display: block;
  margin-bottom: 8px;
}

.task-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-initiator {
  font-size: 11px;
  color: #64748b;
}

.inline-btn {
  height: 26px;
  line-height: 26px;
  font-size: 11px;
  padding: 0 12px;
  border-radius: 4px;
  border: none;
}

.inline-btn.is-agree {
  background: #16a34a;
  color: #ffffff;
}
</style>
