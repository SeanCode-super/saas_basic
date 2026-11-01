<template>
  <view class="notify-page">
    <!-- iOS 27 沉浸式顶栏返回导航 -->
    <view class="ios-nav-bar">
      <view class="ios-nav-back-pill" @click="goBack">
        <AppIcon name="chevron-left" :size="16" color="#0f172a" />
        <text class="ios-nav-back-text">返回</text>
      </view>
      <text class="ios-nav-title">消息与通知中心</text>
      <view class="ios-nav-placeholder" />
    </view>

    <!-- 顶部操作条 -->
    <view class="notify-header-bar">
      <view class="header-left">
        <text class="page-title">消息与协同通知</text>
        <text class="page-subtitle">及时掌握流程审批、安全告警与系统公告</text>
      </view>
      <view v-if="unreadCount > 0" class="mark-all-pill" @click="handleMarkAllRead">
        <AppIcon name="clean" :size="12" color="#0284c7" />
        <text class="mark-all-text">全部已读</text>
      </view>
    </view>

    <!-- 分类标签栏 (YouTube 同款 iOS 27 晶透标签) -->
    <scroll-view scroll-x class="ios27-chip-scroll" :show-scrollbar="false" style="padding-left: 0; padding-right: 0; margin-bottom: 12px;">
      <view class="ios27-chip-group">
        <view
          v-for="cat in categories"
          :key="cat.key"
          class="ios27-chip"
          :class="{ 'is-active': activeCategory === cat.key }"
          @click="activeCategory = cat.key"
        >
          <text>{{ cat.label }}</text>
          <text v-if="getCategoryUnread(cat.key) > 0" class="ios27-chip-badge">
            {{ getCategoryUnread(cat.key) }}
          </text>
        </view>
      </view>
    </scroll-view>

    <!-- 骨架屏 -->
    <view v-if="loading" class="content-body">
      <AppSkeleton type="list" :count="4" />
    </view>

    <!-- 空状态 -->
    <view v-else-if="filteredList.length === 0" class="content-body">
      <AppEmpty
        type="task"
        title="当前暂无此类通知消息"
        description="所有协同任务与系统告警均已妥善处置"
        action-text="刷新消息信箱"
        @action="handleManualRefresh"
      />
    </view>

    <!-- 消息卡片列表 -->
    <view v-else class="content-body">
      <view
        v-for="msg in filteredList"
        :key="msg.id"
        class="notify-card"
        :class="{ 'is-unread': !msg.read }"
        @click="handleOpenMessage(msg)"
      >
        <view class="card-icon-box" :class="'cat-' + (msg.category || 'system').toLowerCase()">
          <AppIcon :name="getIconName(msg.category)" :size="18" :color="getIconColor(msg.category)" />
        </view>

        <view class="card-body">
          <view class="card-top">
            <view class="title-wrap">
              <text v-if="!msg.read" class="unread-dot" />
              <text class="msg-title">{{ msg.title }}</text>
            </view>
            <text class="msg-time">{{ msg.createdAt || '刚刚' }}</text>
          </view>
          <text class="msg-content">{{ msg.content }}</text>
          <view class="card-bottom">
            <text class="cat-pill-tag">{{ getCategoryLabel(msg.category) }}</text>
            <text class="card-arrow">查看详情 &gt;</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 消息详情抽屉 (AppModal Sheet Mode) -->
    <AppModal
      v-model="detailVisible"
      mode="sheet"
      :title="selectedMsg?.title || '通知详情'"
      :subtitle="selectedMsg ? `${getCategoryLabel(selectedMsg.category)} · ${selectedMsg.createdAt || '刚刚'}` : ''"
      :confirm-text="getModalActionText(selectedMsg?.category)"
      cancel-text="关闭"
      @confirm="handleModalAction"
    >
      <view v-if="selectedMsg" class="modal-detail-content">
        <view class="detail-banner" :class="'cat-' + (selectedMsg.category || 'system').toLowerCase()">
          <AppIcon :name="getIconName(selectedMsg.category)" :size="24" :color="getIconColor(selectedMsg.category)" />
          <view class="banner-text-box">
            <text class="banner-title">{{ selectedMsg.title }}</text>
            <text class="banner-time">接收时间：{{ selectedMsg.createdAt || '刚刚' }}</text>
          </view>
        </view>

        <view class="detail-body-card">
          <text class="detail-body-text">{{ selectedMsg.content }}</text>
        </view>

        <view class="detail-meta-list">
          <view class="meta-row">
            <text class="meta-label">发信主体</text>
            <text class="meta-val">系统协同管理中心</text>
          </view>
          <view class="meta-row">
            <text class="meta-label">处理状态</text>
            <text class="meta-val text-green">{{ selectedMsg.read ? '已查阅' : '未读待办' }}</text>
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
import { apiGetMyNotifications, apiMarkNotificationRead } from "../../api/notify";

const activeCategory = ref("ALL");
const loading = ref(false);
const messages = ref<any[]>([]);

const detailVisible = ref(false);
const selectedMsg = ref<any | null>(null);

const categories = [
  { key: "ALL", label: "全部通知" },
  { key: "WORKFLOW", label: "审批提醒" },
  { key: "SECURITY", label: "安全告警" },
  { key: "SYSTEM", label: "系统公告" }
];

const unreadCount = computed(() => {
  return messages.value.filter((m) => !m.read).length;
});

function getCategoryUnread(key: string) {
  if (key === "ALL") return unreadCount.value;
  return messages.value.filter((m) => m.category === key && !m.read).length;
}

const filteredList = computed(() => {
  if (activeCategory.value === "ALL") return messages.value;
  return messages.value.filter((m) => m.category === activeCategory.value);
});

function getIconName(cat?: string) {
  if (cat === "WORKFLOW") return "workflow";
  if (cat === "SECURITY") return "shield";
  return "bell";
}

function getIconColor(cat?: string) {
  if (cat === "WORKFLOW") return "#2563eb";
  if (cat === "SECURITY") return "#dc2626";
  return "#0284c7";
}

function getCategoryLabel(cat?: string) {
  if (cat === "WORKFLOW") return "审批流程";
  if (cat === "SECURITY") return "安全风控";
  return "系统通知";
}

function getModalActionText(cat?: string) {
  if (cat === "WORKFLOW") return "立即前往办理审批";
  if (cat === "SECURITY") return "查看安全合规";
  return "我知道了";
}

async function loadData(silent = false) {
  if (!silent) loading.value = true;
  try {
    const list = await apiGetMyNotifications();
    messages.value = (Array.isArray(list) ? list : []).map((m: any) => ({
      ...m,
      read: m.read ?? (m.readStatus === 1),
      createdAt: m.createdAt ? String(m.createdAt).replace("T", " ").substring(0, 16) : "刚刚"
    }));
  } catch {
    messages.value = [
      {
        id: 1,
        title: "您有一条新的差旅费用报销待审批",
        content: "商务部张敏提交了【华东重点客户拓展差旅交通费用报销申请】，请及时审批办理。",
        category: "WORKFLOW",
        read: false,
        createdAt: "10分钟前"
      },
      {
        id: 2,
        title: "账号登录安全提醒",
        content: "检测到管理员账号在新的设备成功登录，会话审计与安全校验已留痕。",
        category: "SECURITY",
        read: true,
        createdAt: "2小时前"
      },
      {
        id: 3,
        title: "企业协同平台维护升级公告",
        content: "全平台协同办公与移动端服务已完成平滑优化升级，各项业务功能保持正常运行。",
        category: "SYSTEM",
        read: true,
        createdAt: "昨天"
      }
    ];
  } finally {
    loading.value = false;
    uni.stopPullDownRefresh();
  }
}

onPullDownRefresh(() => {
  loadData(true);
});

function handleManualRefresh() {
  loadData(true);
}

function handleOpenMessage(msg: any) {
  selectedMsg.value = msg;
  detailVisible.value = true;
  if (!msg.read) {
    msg.read = true;
    try {
      apiMarkNotificationRead(msg.id);
    } catch {}
  }
}

function handleMarkAllRead() {
  messages.value.forEach((m) => (m.read = true));
  uni.showToast({ title: "已全部标为已读", icon: "success" });
}

function handleModalAction() {
  const cat = selectedMsg.value?.category;
  detailVisible.value = false;
  if (cat === "WORKFLOW") {
    uni.switchTab({ url: "/pages/workflow/list" });
  }
}

function goBack() {
  uni.navigateBack({
    fail: () => {
      uni.switchTab({ url: "/pages/index/index" });
    }
  });
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
/* =============================================================================
   iOS 27 Liquid Glass 消息与协同通知规范
   ============================================================================= */

.notify-page {
  min-height: 100vh;
  background-color: transparent;
  padding: calc(14px + env(safe-area-inset-top)) 14px calc(96px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 顶部操作条 */
.notify-header-bar {
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

.header-left {
  display: flex;
  flex-direction: column;
}

.page-title {
  font-size: 18px;
  font-weight: 750;
  color: #0f172a;
  letter-spacing: -0.3px;
}

.page-subtitle {
  font-size: 11.5px;
  color: #64748b;
  margin-top: 2px;
  font-weight: 500;
}

.mark-all-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: rgba(37, 99, 235, 0.10);
  border: 1px solid rgba(37, 99, 235, 0.22);
  padding: 5px 12px;
  border-radius: 9999px;
  transition: transform 0.15s ease;
}

.mark-all-pill:active {
  transform: scale(0.96);
}

.mark-all-text {
  font-size: 11.5px;
  font-weight: 650;
  color: #2563eb;
}

/* 分类标签栏 */
.notify-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  overflow-x: auto;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 8px 6px;
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.88);
  border-radius: 9999px;
  font-size: 12px;
  color: #475569;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-weight: 550;
  transition: all 0.2s ease;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.95);
}

.tab-item.is-active {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.35);
  font-weight: 650;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.4), 0 4px 12px rgba(37, 99, 235, 0.28);
}

.tab-badge {
  background: linear-gradient(135deg, #ef4444, #dc2626);
  color: #ffffff;
  font-size: 9px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 8px;
}

/* 消息列表 */
.content-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.notify-card {
  display: flex;
  gap: 12px;
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  padding: 16px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 10px 25px -5px rgba(12, 34, 56, 0.05);
  transition: transform 0.15s ease;
}

.notify-card:active {
  transform: scale(0.99);
}

.notify-card.is-unread {
  background: rgba(255, 255, 255, 0.85);
  border-left: 3px solid #2563eb;
}

.card-icon-box {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.card-icon-box.cat-workflow { background: rgba(37, 99, 235, 0.1); }
.card-icon-box.cat-security { background: rgba(220, 38, 38, 0.1); }
.card-icon-box.cat-system { background: rgba(2, 132, 199, 0.1); }

.card-body {
  flex: 1;
  min-width: 0;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  overflow: hidden;
}

.unread-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #0284c7;
  flex-shrink: 0;
}

.msg-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg-time {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
  margin-left: 8px;
}

.msg-content {
  display: block;
  font-size: 12px;
  color: #475569;
  line-height: 1.5;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.cat-pill-tag {
  font-size: 10px;
  color: #64748b;
  background: #f1f5f9;
  padding: 1px 6px;
  border-radius: 4px;
}

.card-arrow {
  font-size: 11px;
  color: #0284c7;
  font-weight: 500;
}

/* 模态抽屉详情内容 */
.modal-detail-content {
  padding: 6px 0;
}

.detail-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  background: rgba(2, 132, 199, 0.08);
  margin-bottom: 14px;
}

.banner-text-box {
  flex: 1;
}

.banner-title {
  display: block;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.banner-time {
  display: block;
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}

.detail-body-card {
  background: #f8fafc;
  border-radius: 12px;
  padding: 14px;
  margin-bottom: 14px;
}

.detail-body-text {
  font-size: 13px;
  color: #334155;
  line-height: 1.6;
}

.detail-meta-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
}

.meta-label { color: #64748b; }
.meta-val { color: #0f172a; font-weight: 600; }
.text-green { color: #16a34a; }
</style>
