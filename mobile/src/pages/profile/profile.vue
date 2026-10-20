<template>
  <view class="mobile-profile-page">
    <view class="profile-card">
      <view class="avatar-lg"><text>管</text></view>
      <text class="user-name">平台管理员 (admin)</text>
      <text class="role-badge">SUPER_ADMIN · 超级管理</text>
    </view>

    <view class="setting-list">
      <view class="setting-item">
        <text class="setting-title">当前归属租户</text>
        <text class="setting-val">平台默认总租户 (platform)</text>
      </view>
      <view class="setting-item">
        <text class="setting-title">当前环境</text>
        <text class="setting-val">云原生本地调试 (DEV)</text>
      </view>
      <view class="setting-item">
        <text class="setting-title">移动端引擎</text>
        <text class="setting-val">Vue 3 + uni-app (跨端编译)</text>
      </view>
    </view>

    <view class="logout-box">
      <button class="logout-btn" @click="handleLogout">安全退出登录</button>
    </view>
  </view>
</template>

<script setup lang="ts">
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
</script>

<style scoped>
.mobile-profile-page {
  min-height: 100vh;
  background-color: #f1f5f9;
  padding: 20px 16px;
  box-sizing: border-box;
}

.profile-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  margin-bottom: 20px;
}

.avatar-lg {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: #0284c7;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  font-weight: 700;
  margin-bottom: 12px;
}

.user-name {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.role-badge {
  font-size: 11px;
  color: #0284c7;
  background: #e0f2fe;
  padding: 2px 8px;
  border-radius: 4px;
  margin-top: 6px;
}

.setting-list {
  background: #ffffff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.setting-item {
  display: flex;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #f1f5f9;
  font-size: 13px;
}

.setting-title {
  color: #334155;
}

.setting-val {
  color: #64748b;
}

.logout-box {
  margin-top: 30px;
}

.logout-btn {
  background: #fee2e2;
  color: #ef4444;
  font-size: 14px;
  font-weight: 600;
  height: 44px;
  line-height: 44px;
  border-radius: 8px;
  border: none;
}
</style>
