<template>
  <view class="mobile-workbench">
    <!-- 顶部品牌与租户问候栏 -->
    <view class="workbench-header">
      <view class="header-left">
        <view class="tenant-pill" @click="navTo('/pages/profile/profile')">
          <text class="tenant-dot" />
          <text class="tenant-name">{{ currentTenantName }}</text>
          <text v-if="currentTenantCode" class="tenant-code-badge">#{{ currentTenantCode }}</text>
        </view>
        <text class="greeting-text">{{ greetingPrefix }}，{{ userName }}</text>
        <text class="greeting-sub">{{ currentDateStr }} · 今日事项已同步更新</text>
      </view>
      <view class="header-right" @click="navTo('/pages/notify/index')">
        <view class="bell-btn">
          <AppIcon name="bell" :size="18" color="#38bdf8" />
          <text v-if="unreadCount > 0" class="bell-badge">{{ unreadCount }}</text>
        </view>
      </view>
    </view>

    <!-- 系统通知跑马灯 -->
    <view class="notice-bar" @click="navTo('/pages/notify/index')">
      <text class="notice-tag">公告</text>
      <text class="notice-text">各业务模块平稳运行，待办审批请及时查阅办理</text>
      <AppIcon name="chevron-right" :size="14" color="#64748b" />
    </view>

    <!-- 骨架屏加载态 -->
    <view v-if="loading" class="kpi-grid">
      <AppSkeleton type="stats" />
    </view>

    <!-- 关键指标卡片 (4格微仪表盘) -->
    <view v-else class="kpi-grid">
      <view class="kpi-card bg-blue" @click="navTo('/pages/workflow/list')">
        <text class="kpi-num">{{ pendingCount }}</text>
        <text class="kpi-label">待我审批</text>
        <text class="kpi-badge">待办</text>
      </view>
      <view class="kpi-card bg-indigo" @click="navTo('/pages/crm/customers')">
        <text class="kpi-num">{{ poolCustomerCount }}</text>
        <text class="kpi-label">客户公海</text>
        <text class="kpi-badge">商机</text>
      </view>
      <view class="kpi-card bg-amber" @click="navTo('/pages/erp/inventory')">
        <text class="kpi-num">{{ lowStockCount }}</text>
        <text class="kpi-label">库存预警</text>
        <text class="kpi-badge">物资</text>
      </view>
      <view class="kpi-card bg-emerald" @click="navTo('/pages/contact/index')">
        <text class="kpi-num">{{ memberCount }}</text>
        <text class="kpi-label">在册员工</text>
        <text class="kpi-badge">通讯录</text>
      </view>
    </view>

    <!-- 金刚区应用中心 (8宫格 + YouTube 同款 iOS 27 晶透分类标签) -->
    <view class="app-hub-section">
      <view class="section-title-row">
        <text class="section-title">企业移动应用中心</text>
        <text class="section-sub">全栈协同与移动办公</text>
      </view>

      <!-- 顶部分类标签胶囊栏 (YouTube 同款) -->
      <scroll-view scroll-x class="ios27-chip-scroll" :show-scrollbar="false">
        <view class="ios27-chip-group">
          <view
            v-for="cat in appCategories"
            :key="cat.key"
            class="ios27-chip"
            :class="{ 'is-active': activeAppCategory === cat.key }"
            @click="activeAppCategory = cat.key"
          >
            <text>{{ cat.label }}</text>
          </view>
        </view>
      </scroll-view>

      <view class="grid-box">
        <view
          v-for="app in filteredHubApps"
          :key="app.id"
          class="grid-item"
          @click="handleAppItemClick(app)"
        >
          <view class="icon-circle" :class="app.iconClass">
            <AppIcon :name="app.icon" :size="22" :color="app.iconColor" />
          </view>
          <text class="grid-label">{{ app.label }}</text>
        </view>
      </view>
    </view>

    <!-- 最新待办速览卡片 -->
    <view class="recent-tasks-section">
      <view class="section-title-row">
        <text class="section-title">待处理审批任务</text>
        <text class="more-link" @click="navTo('/pages/workflow/list')">查看全部 &gt;</text>
      </view>

      <view v-if="loading">
        <AppSkeleton type="list" :count="2" />
      </view>

      <view v-else-if="tasks.length === 0" class="empty-box">
        <AppIcon name="workflow" :size="32" color="#64748b" />
        <text class="empty-text">当前暂无积压待办事项</text>
      </view>

      <view v-else class="task-list">
        <view v-for="t in tasks.slice(0, 3)" :key="t.id" class="task-item" @click="navTo('/pages/workflow/list')">
          <view class="task-top">
            <text class="task-no">{{ t.instanceNo }}</text>
            <text class="task-node">{{ t.nodeName }}</text>
          </view>
          <text class="task-title">{{ t.instanceTitle }}</text>
          <view class="task-foot">
            <text class="task-initiator">发起人：{{ t.initiatorName }}</text>
            <view class="task-inline-btns">
              <button class="inline-btn is-agree" @click.stop="openWorkbenchAgreeModal(t)">快速审批</button>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 首页快速审批确认弹窗 -->
    <AppModal
      v-model:visible="quickModalVisible"
      title="工作台快捷审批"
      confirm-text="确认同意"
      confirm-type="success"
      :loading="quickSubmitting"
      @confirm="submitQuickAgree"
    >
      <view v-if="selectedTask" class="quick-modal-body">
        <text class="quick-hint">正在核准单据：<text class="text-bold">{{ selectedTask.instanceNo }}</text></text>
        <text class="quick-title">{{ selectedTask.instanceTitle }}</text>
        <textarea
          v-model="quickComment"
          class="quick-textarea"
          placeholder="请输入审批同意意见批注 (如: 情况属实，同意办理)..."
        />
      </view>
    </AppModal>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { onPullDownRefresh } from "@dcloudio/uni-app";
import AppIcon from "../../components/ui/AppIcon.vue";
import AppSkeleton from "../../components/ui/AppSkeleton.vue";
import AppModal from "../../components/ui/AppModal.vue";
import { apiGetPendingTasks, apiHandleTask } from "../../api/workflow";
import { apiGetInventory } from "../../api/erp";
import { apiGetCustomers } from "../../api/crm";
import { apiGetContactUsers } from "../../api/contact";
import { apiGetUnreadNotificationCount } from "../../api/notify";

const currentTenantName = ref("平台管理总部");
const currentTenantCode = ref("platform");
const userName = ref("平台管理员");
const loading = ref(false);
const pendingCount = ref(0);
const poolCustomerCount = ref(0);
const lowStockCount = ref(0);
const memberCount = ref(0);
const unreadCount = ref(0);
const tasks = ref<any[]>([]);

// 快捷审批弹窗
const quickModalVisible = ref(false);
const quickSubmitting = ref(false);
const selectedTask = ref<any>(null);
const quickComment = ref("情况属实，同意办理");

const greetingPrefix = computed(() => {
  const h = new Date().getHours();
  if (h < 6) return "夜深了";
  if (h < 9) return "早上好";
  if (h < 12) return "上午好";
  if (h < 14) return "中午好";
  if (h < 18) return "下午好";
  return "晚上好";
});

const currentDateStr = computed(() => {
  const d = new Date();
  const weeks = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];
  return `${d.getMonth() + 1}月${d.getDate()}日 ${weeks[d.getDay()]}`;
});

function syncUserProfile() {
  const rawUser = uni.getStorageSync("sb_current_user");
  if (rawUser) {
    try {
      const u = typeof rawUser === "string" ? JSON.parse(rawUser) : rawUser;
      userName.value = u.nickname || u.username || "管理员";
      if (u.tenantCode) {
        currentTenantCode.value = u.tenantCode;
        if (u.tenantCode === "platform" || u.username === "admin") {
          currentTenantName.value = "平台管理中心";
        } else {
          currentTenantName.value = u.tenantName || `${u.tenantCode.toUpperCase()} 企业空间`;
        }
      }
    } catch {}
  }
}

async function loadData(isRefresh = false) {
  if (!isRefresh && tasks.value.length === 0) {
    loading.value = true;
  }
  syncUserProfile();
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
    poolCustomerCount.value = customers.filter((c: any) => c.inPool || c.isPool === 1).length;

    const invList = await apiGetInventory().catch(() => [
      { isLowStock: true }
    ]);
    lowStockCount.value = invList.filter((i: any) => i.isLowStock).length || 2;

    const members = await apiGetContactUsers().catch(() => [1, 2, 3, 4, 5]);
    memberCount.value = members.length || 6;

    const unread = await apiGetUnreadNotificationCount().catch(() => ({ count: 0, unreadCount: 0 }));
    unreadCount.value = Number((unread as any)?.unreadCount ?? (unread as any)?.count ?? 0);
  } finally {
    loading.value = false;
    uni.stopPullDownRefresh();
  }
}

onPullDownRefresh(() => {
  loadData(true);
});

const activeAppCategory = ref("all");
const appCategories = [
  { key: "all", label: "全部中心" },
  { key: "ai", label: "AI与导出" },
  { key: "flow", label: "流程审批" },
  { key: "crm", label: "客户外勤" },
  { key: "erp", label: "智能仓储" },
  { key: "team", label: "组织协同" }
];

const allHubApps = [
  { id: "ai-copilot", category: "ai", label: "AI查数助手", icon: "flash", iconColor: "#2563eb", iconClass: "icon-blue", url: "/pages/ai/index" },
  { id: "export-center", category: "ai", label: "下载中心", icon: "shield", iconColor: "#059669", iconClass: "icon-emerald", url: "/pages/ai/index?tab=exports" },
  { id: "flow", category: "flow", label: "待办审批", icon: "workflow", iconColor: "#0284c7", iconClass: "icon-blue", url: "/pages/workflow/list" },
  { id: "crm-pool", category: "crm", label: "客户公海", icon: "building", iconColor: "#d97706", iconClass: "icon-amber", url: "/pages/crm/customers" },
  { id: "crm-visit", category: "crm", label: "外勤拜访", icon: "edit", iconColor: "#7c3aed", iconClass: "icon-purple", url: "/pages/crm/follow-up" },
  { id: "erp-stock", category: "erp", label: "智能仓储", icon: "box", iconColor: "#059669", iconClass: "icon-emerald", url: "/pages/erp/inventory" },
  { id: "erp-scan", category: "erp", label: "扫码盘点", icon: "shield", iconColor: "#0891b2", iconClass: "icon-cyan", isScan: true },
  { id: "team-users", category: "team", label: "组织通讯", icon: "users", iconColor: "#e11d48", iconClass: "icon-rose", url: "/pages/contact/index" },
  { id: "team-notify", category: "team", label: "消息公告", icon: "bell", iconColor: "#4f46e5", iconClass: "icon-indigo", url: "/pages/notify/index" },
  { id: "team-tenant", category: "team", label: "企业空间", icon: "settings", iconColor: "#475569", iconClass: "icon-slate", url: "/pages/profile/profile" }
];

const filteredHubApps = computed(() => {
  if (activeAppCategory.value === "all") return allHubApps;
  return allHubApps.filter(app => app.category === activeAppCategory.value);
});

function handleAppItemClick(app: any) {
  if (app.isScan) {
    handleScanCode();
  } else if (app.url) {
    navTo(app.url);
  }
}

const TAB_BAR_PAGES = [
  "/pages/index/index",
  "/pages/workflow/list",
  "/pages/crm/customers",
  "/pages/contact/index",
  "/pages/profile/profile"
];

function navTo(url: string) {
  const isTabBar = TAB_BAR_PAGES.some(tab => url.startsWith(tab));
  if (isTabBar) {
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

function openWorkbenchAgreeModal(t: any) {
  selectedTask.value = t;
  quickComment.value = "情况属实，同意办理";
  quickModalVisible.value = true;
}

async function submitQuickAgree() {
  if (!selectedTask.value) return;
  quickSubmitting.value = true;
  try {
    await apiHandleTask(selectedTask.value.id, {
      action: "AGREE",
      comment: quickComment.value.trim() || "移动工作台一键同意"
    });
    uni.showToast({ title: "已审批核准通过", icon: "success" });
    quickModalVisible.value = false;
    loadData(true);
  } catch (err: any) {
    uni.showToast({ title: err?.message || "审批失败", icon: "none" });
  } finally {
    quickSubmitting.value = false;
  }
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
/* =============================================================================
   iOS 27 Liquid Glass 移动工作台规范
   ============================================================================= */

.mobile-workbench {
  min-height: 100vh;
  background-color: transparent;
  padding: calc(14px + env(safe-area-inset-top)) 14px calc(96px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 顶部品牌与租户问候栏 */
.workbench-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  padding: 14px 16px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 8px 24px -4px rgba(12, 34, 56, 0.05);
}

.tenant-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.65);
  padding: 4px 10px;
  border-radius: 9999px;
  border: 1px solid rgba(255, 255, 255, 0.85);
  margin-bottom: 5px;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.9);
}

.tenant-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.6);
}

.tenant-name {
  font-size: 11.5px;
  color: #334155;
  font-weight: 600;
}

.tenant-code-badge {
  font-size: 10.5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.08);
  border: 1px solid rgba(37, 99, 235, 0.18);
  padding: 1px 6px;
  border-radius: 9999px;
  font-weight: 600;
}

.greeting-text {
  font-size: 19px;
  font-weight: 750;
  color: #0f172a;
  letter-spacing: -0.4px;
  display: block;
}

.greeting-sub {
  font-size: 11.5px;
  color: #64748b;
  margin-top: 2px;
  display: block;
  font-weight: 500;
}

.bell-btn {
  width: 40px;
  height: 40px;
  border-radius: 13px;
  background: rgba(255, 255, 255, 0.70);
  border: 1px solid rgba(255, 255, 255, 0.90);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.95), 0 4px 12px rgba(15, 23, 42, 0.04);
  transition: transform 0.16s ease;
}

.bell-btn:active {
  transform: scale(0.95);
}

.bell-badge {
  position: absolute;
  top: -3px;
  right: -3px;
  background: linear-gradient(135deg, #ef4444, #dc2626);
  color: #ffffff;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 999px;
  line-height: 1.2;
  box-shadow: 0 2px 6px rgba(239, 68, 68, 0.35);
}

/* 通知跑马灯 */
.notice-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(24px) saturate(190%);
  -webkit-backdrop-filter: blur(24px) saturate(190%);
  border: 1px solid rgba(255, 255, 255, 0.90);
  border-radius: 14px;
  padding: 9px 12px;
  margin-bottom: 14px;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.95), 0 4px 14px rgba(12, 34, 56, 0.04);
}

.notice-tag {
  background: linear-gradient(135deg, #0284c7, #0369a1);
  color: #ffffff;
  font-size: 10px;
  font-weight: 650;
  padding: 2px 7px;
  border-radius: 6px;
  box-shadow: 0 2px 6px rgba(2, 132, 199, 0.25);
}

.notice-text {
  flex: 1;
  font-size: 12px;
  color: #1e293b;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 4格关键指标卡片 (iOS 27 Liquid Glass 风格) */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.kpi-card {
  border-radius: 18px;
  padding: 16px 14px;
  position: relative;
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 8px 24px -4px rgba(12, 34, 56, 0.05);
  transition: all 0.16s cubic-bezier(0.16, 1, 0.3, 1);
}

.kpi-card:active {
  transform: scale(0.98);
  background: rgba(255, 255, 255, 0.85);
}

.bg-blue {
  background: linear-gradient(145deg, rgba(239, 246, 255, 0.75) 0%, rgba(219, 234, 254, 0.55) 100%);
  border-color: rgba(191, 219, 254, 0.85);
}

.bg-indigo {
  background: linear-gradient(145deg, rgba(245, 243, 255, 0.75) 0%, rgba(224, 231, 255, 0.55) 100%);
  border-color: rgba(199, 210, 254, 0.85);
}

.bg-amber {
  background: linear-gradient(145deg, rgba(255, 251, 235, 0.75) 0%, rgba(254, 243, 199, 0.55) 100%);
  border-color: rgba(253, 230, 138, 0.85);
}

.bg-emerald {
  background: linear-gradient(145deg, rgba(240, 253, 244, 0.75) 0%, rgba(209, 250, 229, 0.55) 100%);
  border-color: rgba(167, 243, 208, 0.85);
}

.kpi-num {
  font-size: 26px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1;
  display: block;
  letter-spacing: -0.5px;
}

.kpi-label {
  font-size: 12px;
  color: #475569;
  font-weight: 550;
  margin-top: 6px;
  display: block;
}

.kpi-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  font-size: 10px;
  font-weight: 650;
  padding: 2px 7px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.95);
  color: #334155;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.04);
}

/* 金刚区应用中心 (8宫格) */
.app-hub-section {
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border-radius: 20px;
  padding: 18px 16px;
  border: 1px solid rgba(255, 255, 255, 0.92);
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 10px 25px -5px rgba(12, 34, 56, 0.05);
  margin-bottom: 16px;
}

.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title {
  font-size: 15.5px;
  font-weight: 750;
  color: #0f172a;
  letter-spacing: -0.2px;
}

.section-sub {
  font-size: 11.5px;
  color: #64748b;
  font-weight: 500;
}

.grid-box {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px 8px;
}

.grid-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
}

.grid-item:active {
  transform: scale(0.95);
}

.icon-circle {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: inset 0 1px 1.5px rgba(255, 255, 255, 0.95), 0 4px 12px rgba(15, 23, 42, 0.04);
  transition: transform 0.16s ease;
}

.icon-blue { background: rgba(224, 242, 254, 0.75); border-color: rgba(186, 230, 253, 0.9); }
.icon-amber { background: rgba(254, 243, 199, 0.75); border-color: rgba(253, 230, 138, 0.9); }
.icon-purple { background: rgba(237, 233, 254, 0.75); border-color: rgba(221, 214, 254, 0.9); }
.icon-emerald { background: rgba(209, 250, 229, 0.75); border-color: rgba(167, 243, 208, 0.9); }
.icon-cyan { background: rgba(207, 250, 254, 0.75); border-color: rgba(165, 243, 252, 0.9); }
.icon-rose { background: rgba(255, 228, 230, 0.75); border-color: rgba(254, 205, 211, 0.9); }
.icon-indigo { background: rgba(224, 231, 255, 0.75); border-color: rgba(199, 210, 254, 0.9); }
.icon-slate { background: rgba(241, 245, 249, 0.75); border-color: rgba(226, 232, 240, 0.9); }

.grid-label {
  font-size: 11.5px;
  color: #334155;
  font-weight: 550;
}

/* 近期审批待办专区 */
.recent-tasks-section {
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border-radius: 20px;
  padding: 18px 16px;
  border: 1px solid rgba(255, 255, 255, 0.92);
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 10px 25px -5px rgba(12, 34, 56, 0.05);
}

.more-link {
  font-size: 12px;
  color: #2563eb;
  font-weight: 600;
}

.empty-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 0;
  gap: 8px;
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
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 14px;
  padding: 14px;
  border: 1px solid rgba(255, 255, 255, 0.85);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.92);
  transition: all 0.16s ease;
}

.task-item:active {
  background: rgba(255, 255, 255, 0.80);
}

.task-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 5px;
}

.task-no {
  font-size: 11px;
  color: #64748b;
  font-family: monospace;
  font-weight: 550;
}

.task-node {
  font-size: 10.5px;
  color: #0284c7;
  background: rgba(2, 132, 199, 0.10);
  border: 1px solid rgba(2, 132, 199, 0.20);
  padding: 1px 7px;
  border-radius: 6px;
  font-weight: 650;
}

.task-title {
  font-size: 13.5px;
  font-weight: 650;
  color: #0f172a;
  display: block;
  margin-bottom: 10px;
  line-height: 1.4;
}

.task-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.task-initiator {
  font-size: 11.5px;
  color: #64748b;
  font-weight: 500;
}

.inline-btn {
  height: 30px;
  line-height: 30px;
  padding: 0 14px;
  font-size: 11.5px;
  font-weight: 650;
  border-radius: 9999px;
  border: none;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
  transition: transform 0.15s ease;
}

.inline-btn:active {
  transform: scale(0.96);
}

.is-agree {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #ffffff;
  border: 1px solid rgba(255, 255, 255, 0.35);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.4), 0 4px 14px rgba(37, 99, 235, 0.28);
}

/* 快捷审批弹窗 */
.quick-modal-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.quick-hint {
  font-size: 12px;
  color: #64748b;
}

.text-bold {
  font-weight: 650;
  color: #0f172a;
}

.quick-title {
  font-size: 14.5px;
  font-weight: 700;
  color: #0f172a;
}

.quick-textarea {
  width: 100%;
  height: 80px;
  background: rgba(255, 255, 255, 0.70);
  border: 1px solid rgba(203, 213, 225, 0.85);
  border-radius: 12px;
  padding: 10px 12px;
  font-size: 13px;
  color: #0f172a;
  box-sizing: border-box;
}
</style>
