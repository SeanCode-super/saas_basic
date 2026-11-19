<template>
  <view class="mobile-sessions-page">
    <!-- iOS 27 沉浸式顶栏返回导航 -->
    <view class="ios-nav-bar">
      <view class="ios-nav-back-pill" @click="goBack">
        <AppIcon name="chevron-left" :size="16" color="#0f172a" />
        <text class="ios-nav-back-text">返回</text>
      </view>
      <text class="ios-nav-title">在线设备与安全</text>
      <view class="ios-nav-placeholder" />
    </view>

    <!-- 安全审计说明卡片 -->
    <view class="security-banner">
      <view class="banner-icon-box">
        <AppIcon name="shield" :size="20" color="#059669" />
      </view>
      <view class="banner-texts">
        <text class="banner-title">多终端活跃会话管控</text>
        <text class="banner-desc">实时监控当前账号在各终端的活跃登录状态。如发现异常或非本人操作设备，可立即强制下线。</text>
      </view>
    </view>

    <!-- 会话列表容器 -->
    <view class="sessions-container">
      <view class="list-head-row">
        <text class="list-head-title">活跃设备列表 ({{ sessions.length }})</text>
        <text class="list-refresh-text" @click="loadSessions">刷新状态</text>
      </view>

      <view v-if="loading" class="loading-box">
        <text class="loading-text">正在同步在线终端状态...</text>
      </view>

      <view v-else-if="sessions.length === 0" class="empty-box">
        <text class="empty-text">暂无其他活跃会话记录</text>
      </view>

      <view v-else class="session-cards">
        <view
          v-for="item in sessions"
          :key="item.sessionNo"
          class="session-card"
          :class="{ 'is-current': item.isCurrent }"
        >
          <view class="card-icon-col">
            <view class="device-icon-box" :class="[item.isCurrent ? 'bg-emerald' : 'bg-blue']">
              <AppIcon :name="getDeviceIcon(item.userAgent)" :size="18" :color="item.isCurrent ? '#059669' : '#2563eb'" />
            </view>
          </view>

          <view class="card-info-col">
            <view class="info-top-row">
              <text class="device-name">{{ formatDeviceName(item.userAgent) }}</text>
              <text v-if="item.isCurrent" class="current-badge">本机当前</text>
            </view>
            <text class="info-meta">登录 IP：{{ item.loginIp || '127.0.0.1' }}</text>
            <text class="info-meta">最近活跃：{{ formatTime(item.lastAccessAt || item.loginTime) }}</text>
          </view>

          <view v-if="!item.isCurrent" class="card-action-col">
            <button class="kick-pill-btn" @click="kickOne(item)">
              下线
            </button>
          </view>
        </view>
      </view>
    </view>

    <!-- 底部常驻一键下线其他设备按钮 -->
    <view v-if="otherSessionsCount > 0" class="bottom-bar">
      <button class="kick-all-btn" :loading="kickingAll" @click="kickAllOthers">
        一键踢出其他所有设备 ({{ otherSessionsCount }})
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import AppIcon from "../../components/ui/AppIcon.vue";
import {
  apiGetMySessions,
  apiRevokeMySession,
  apiRevokeOtherMySessions,
  type MobileOnlineSession
} from "../../api/profile";

const loading = ref(false);
const kickingAll = ref(false);
const sessions = ref<MobileOnlineSession[]>([]);

const otherSessionsCount = computed(() => {
  return sessions.value.filter((s) => !s.isCurrent).length;
});

function goBack() {
  uni.navigateBack({ delta: 1 });
}

function getDeviceIcon(ua?: string) {
  const u = (ua || "").toLowerCase();
  if (u.includes("iphone") || u.includes("android") || u.includes("mobile")) {
    return "phone";
  }
  return "monitor";
}

function formatDeviceName(ua?: string) {
  if (!ua) return "标准网络浏览器终端";
  const u = ua.toLowerCase();
  if (u.includes("iphone")) return "Apple iPhone (iOS Safari)";
  if (u.includes("android")) return "Android 移动终端浏览器";
  if (u.includes("macintosh") || u.includes("mac os")) return "Apple Mac 工作站 (Desktop)";
  if (u.includes("windows")) return "Windows PC 工作站 (Desktop)";
  return "企业云协同桌面终端";
}

function formatTime(t?: string) {
  if (!t) return "刚刚在线活跃";
  return t.slice(0, 16).replace("T", " ");
}

async function loadSessions() {
  loading.value = true;
  try {
    const list = await apiGetMySessions();
    sessions.value = list || [];
  } catch (err: any) {
    uni.showToast({ title: err?.message || "拉取设备列表失败", icon: "none" });
  } finally {
    loading.value = false;
  }
}

async function kickOne(item: MobileOnlineSession) {
  uni.showModal({
    title: "确认下线该设备",
    content: `确定强制将【${formatDeviceName(item.userAgent)}】退出登录吗？`,
    success: async (res) => {
      if (res.confirm) {
        try {
          await apiRevokeMySession(item.sessionNo);
          uni.showToast({ title: "该设备已成功下线", icon: "success" });
          void loadSessions();
        } catch (err: any) {
          uni.showToast({ title: err?.message || "下线设备失败", icon: "none" });
        }
      }
    }
  });
}

async function kickAllOthers() {
  uni.showModal({
    title: "一键下线其他设备",
    content: "确定强制下线除当前设备外的所有其他活跃登录会话吗？",
    success: async (res) => {
      if (res.confirm) {
        kickingAll.value = true;
        try {
          await apiRevokeOtherMySessions();
          uni.showToast({ title: "已下线所有其他设备", icon: "success" });
          void loadSessions();
        } catch (err: any) {
          uni.showToast({ title: err?.message || "操作失败", icon: "none" });
        } finally {
          kickingAll.value = false;
        }
      }
    }
  });
}

onMounted(() => {
  uni.hideTabBar({ animation: false });
  void loadSessions();
});

onShow(() => {
  uni.hideTabBar({ animation: false });
});
</script>

<style scoped>
.mobile-sessions-page {
  min-height: 100vh;
  padding: calc(14px + env(safe-area-inset-top)) 14px calc(100px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 顶部安全横幅 */
.security-banner {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
  box-shadow: inset 0 1px 1.5px rgba(255, 255, 255, 1), 0 8px 24px -4px rgba(12, 34, 56, 0.04);
}

.banner-icon-box {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: rgba(5, 150, 105, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.banner-texts {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.banner-title {
  font-size: 15px;
  font-weight: 750;
  color: #0f172a;
}

.banner-desc {
  font-size: 12px;
  color: #64748b;
  line-height: 1.4;
}

/* 列表区 */
.sessions-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.list-head-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 4px;
}

.list-head-title {
  font-size: 13.5px;
  font-weight: 700;
  color: #334155;
}

.list-refresh-text {
  font-size: 12px;
  color: #2563eb;
  font-weight: 600;
  cursor: pointer;
}

.loading-box,
.empty-box {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 18px;
  padding: 30px;
  text-align: center;
}

.loading-text,
.empty-text {
  font-size: 13px;
  color: #94a3b8;
}

.session-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.session-card {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 18px;
  padding: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: inset 0 1px 1.5px rgba(255, 255, 255, 1), 0 8px 24px -4px rgba(12, 34, 56, 0.04);
}

.session-card.is-current {
  border-color: rgba(16, 185, 129, 0.4);
  background: rgba(255, 255, 255, 0.92);
}

.card-icon-col {
  flex-shrink: 0;
}

.device-icon-box {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.bg-emerald {
  background: rgba(16, 185, 129, 0.12);
}

.bg-blue {
  background: rgba(37, 99, 235, 0.1);
}

.card-info-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.info-top-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}

.device-name {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.current-badge {
  font-size: 11px;
  font-weight: 650;
  color: #059669;
  background: rgba(16, 185, 129, 0.12);
  border: 1px solid rgba(16, 185, 129, 0.28);
  padding: 2px 7px;
  border-radius: 9999px;
  white-space: nowrap;
  flex-shrink: 0;
}

.info-meta {
  font-size: 11.5px;
  color: #64748b;
  line-height: 1.35;
}

.card-action-col {
  flex-shrink: 0;
}

.kick-pill-btn {
  background: rgba(254, 242, 242, 0.85);
  color: #dc2626;
  border: 1px solid rgba(239, 68, 68, 0.25);
  font-size: 12px;
  font-weight: 650;
  padding: 4px 12px;
  border-radius: 9999px;
  cursor: pointer;
  transition: transform 0.15s ease;
}

.kick-pill-btn:active {
  transform: scale(0.95);
}

/* 底部常驻按钮容器 */
.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px 18px calc(14px + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border-top: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: 0 -4px 16px rgba(12, 34, 56, 0.05);
  z-index: 99;
}

@media screen and (min-width: 640px) {
  .bottom-bar {
    max-width: 430px;
    left: 50% !important;
    transform: translateX(-50%);
  }
}

.kick-all-btn {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  color: #ffffff;
  font-size: 14.5px;
  font-weight: 650;
  height: 48px;
  line-height: 48px;
  border-radius: 9999px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.4), 0 6px 18px rgba(239, 68, 68, 0.35);
  cursor: pointer;
  transition: transform 0.15s ease;
}

.kick-all-btn:active {
  transform: scale(0.98);
}
</style>
