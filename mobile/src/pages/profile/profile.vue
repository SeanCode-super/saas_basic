<template>
  <view class="mobile-profile-page">
    <!-- 用户个人名片卡 -->
    <view class="profile-card">
      <view class="avatar-lg"><text>{{ userInitial }}</text></view>
      <text class="user-name">{{ userName }}</text>
      <text class="user-account">账号：{{ userAccount }}</text>
      <view class="badge-row">
        <text class="role-badge">超级管理员 · 平台管控</text>
        <text class="tenant-badge">{{ currentTenantName }}</text>
      </view>
    </view>

    <!-- 租户空间管理卡片 -->
    <view class="section-card">
      <view class="section-card-head">
        <text class="section-card-title">🏢 多租户空间中枢</text>
        <text class="section-card-action" @click="handleSwitchTenant">切换租户 &gt;</text>
      </view>
      <view class="tenant-display-box" @click="handleSwitchTenant">
        <view class="tenant-icon">🌐</view>
        <view class="tenant-info">
          <text class="tenant-title">当前会话空间</text>
          <text class="tenant-val">{{ currentTenantName }} (tenant_id: {{ currentTenantId }})</text>
        </view>
        <text class="switch-hint">点击更换</text>
      </view>
    </view>

    <!-- 常用功能列表 -->
    <view class="setting-list">
      <view class="setting-item" @click="navTo('/pages/workflow/list')">
        <view class="item-left">
          <text class="item-icon">📝</text>
          <text class="item-title">我的审批代办</text>
        </view>
        <text class="item-arrow">&gt;</text>
      </view>

      <view class="setting-item" @click="navTo('/pages/crm/customers')">
        <view class="item-left">
          <text class="item-icon">💼</text>
          <text class="item-title">我的私海客户与公海</text>
        </view>
        <text class="item-arrow">&gt;</text>
      </view>

      <view class="setting-item" @click="navTo('/pages/notify/index')">
        <view class="item-left">
          <text class="item-icon">🔔</text>
          <text class="item-title">消息与系统公告</text>
        </view>
        <text class="item-arrow">&gt;</text>
      </view>

      <view class="setting-item" @click="clearCache">
        <view class="item-left">
          <text class="item-icon">🧹</text>
          <text class="item-title">清理本地离线缓存</text>
        </view>
        <text class="item-val">2.4 MB</text>
      </view>

      <view class="setting-item">
        <view class="item-left">
          <text class="item-icon">⚡</text>
          <text class="item-title">跨端移动引擎架构</text>
        </view>
        <text class="item-val">Vue 3 + uni-app (跨端输出)</text>
      </view>

      <view class="setting-item">
        <view class="item-left">
          <text class="item-icon">🛡️</text>
          <text class="item-title">基座核心版本</text>
        </view>
        <text class="item-val">天枢云基 v2.4 (Enterprise)</text>
      </view>
    </view>

    <!-- 安全退出 -->
    <view class="logout-box">
      <button class="logout-btn" @click="handleLogout">安全退出登录</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { apiGetTenants } from "../../api/tenant";

const userName = ref("平台管理员");
const userAccount = ref("admin");
const userInitial = ref("管");
const currentTenantName = ref("平台默认空间 (platform)");
const currentTenantId = ref("1");

const tenantOptions = ref([
  { id: "1", name: "平台默认空间 (platform)", code: "platform" },
  { id: "2", name: "未来科技租户 (tech_corp)", code: "tech_corp" },
  { id: "3", name: "数智零售专区 (retail_hub)", code: "retail_hub" }
]);

async function loadTenants() {
  try {
    const res = await apiGetTenants();
    const list = Array.isArray(res) ? res : (res?.records || res?.list || []);
    if (Array.isArray(list) && list.length > 0) {
      tenantOptions.value = list.map((t: any) => ({
        id: String(t.id),
        name: `${t.name} (${t.code})`,
        code: t.code
      }));
    }
  } catch {}
}

function handleSwitchTenant() {
  const names = tenantOptions.value.map((t) => t.name);
  uni.showActionSheet({
    itemList: names,
    success: (res) => {
      const selected = tenantOptions.value[res.tapIndex];
      if (selected) {
        currentTenantName.value = selected.name;
        currentTenantId.value = selected.id;
        uni.setStorageSync("sb_tenant_id", selected.id);
        uni.showToast({ title: `已切换至 ${selected.code}`, icon: "success" });
      }
    }
  });
}

function navTo(url: string) {
  if (url.includes("/pages/workflow/list")) {
    uni.switchTab({ url });
  } else {
    uni.navigateTo({ url });
  }
}

function clearCache() {
  uni.showModal({
    title: "清理提示",
    content: "确定清理本地离线缓存吗？（登录凭据将被安全保留）",
    success: (res) => {
      if (res.confirm) {
        uni.showToast({ title: "本地缓存已清理完毕", icon: "success" });
      }
    }
  });
}

function handleLogout() {
  uni.showModal({
    title: "退出提示",
    content: "确定安全退出当前移动会话吗？",
    success: (res) => {
      if (res.confirm) {
        uni.removeStorageSync("sb_access_token");
        uni.removeStorageSync("sb_refresh_token");
        uni.reLaunch({ url: "/pages/login/login" });
      }
    }
  });
}

onMounted(() => {
  loadTenants();
});
</script>

<style scoped>
.mobile-profile-page {
  min-height: 100vh;
  background-color: #f8fafc;
  padding: 16px 14px 40px;
  box-sizing: border-box;
}

.profile-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 22px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid #f1f5f9;
  margin-bottom: 14px;
}

.avatar-lg {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0284c7 0%, #2563eb 100%);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 10px;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.25);
}

.user-name {
  font-size: 17px;
  font-weight: 800;
  color: #0f172a;
}

.user-account {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}

.badge-row {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.role-badge {
  font-size: 10.5px;
  font-weight: 600;
  color: #2563eb;
  background: #eff6ff;
  padding: 2px 8px;
  border-radius: 6px;
}

.tenant-badge {
  font-size: 10.5px;
  font-weight: 600;
  color: #059669;
  background: #ecfdf5;
  padding: 2px 8px;
  border-radius: 6px;
}

.section-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 14px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  margin-bottom: 14px;
}

.section-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.section-card-title {
  font-size: 13.5px;
  font-weight: 700;
  color: #0f172a;
}

.section-card-action {
  font-size: 12px;
  color: #2563eb;
  font-weight: 500;
}

.tenant-display-box {
  background: #f8fafc;
  border-radius: 8px;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid #e2e8f0;
}

.tenant-icon {
  font-size: 20px;
}

.tenant-info {
  flex: 1;
}

.tenant-title {
  font-size: 11px;
  color: #64748b;
  display: block;
}

.tenant-val {
  font-size: 12.5px;
  font-weight: 600;
  color: #1e293b;
  display: block;
}

.switch-hint {
  font-size: 11px;
  color: #2563eb;
  background: #eff6ff;
  padding: 2px 6px;
  border-radius: 4px;
}

.setting-list {
  background: #ffffff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid #f1f5f9;
  margin-bottom: 20px;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #f1f5f9;
}

.setting-item:last-child {
  border-bottom: none;
}

.item-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.item-icon {
  font-size: 16px;
}

.item-title {
  font-size: 13.5px;
  color: #1e293b;
  font-weight: 500;
}

.item-val {
  font-size: 12px;
  color: #94a3b8;
}

.item-arrow {
  font-size: 14px;
  color: #cbd5e1;
}

.logout-box {
  padding: 0 6px;
}

.logout-btn {
  background: #fee2e2;
  color: #dc2626;
  font-size: 14px;
  font-weight: 700;
  border-radius: 10px;
  height: 44px;
  line-height: 44px;
  border: none;
}
</style>
