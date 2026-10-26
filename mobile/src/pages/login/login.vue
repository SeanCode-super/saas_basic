<template>
  <view class="mobile-login-shell">
    <view class="brand-box">
      <view class="brand-logo"><text>💠</text></view>
      <text class="brand-title">天枢云基</text>
      <text class="brand-desc">Enterprise PaaS · 跨端多平台协同移动工作台</text>
    </view>

    <view class="login-card">
      <view class="form-item">
        <text class="item-label">租户编码</text>
        <input v-model="form.tenantCode" class="item-input" placeholder="默认 platform" />
      </view>
      <view class="form-item">
        <text class="item-label">登录账号</text>
        <input v-model="form.username" class="item-input" placeholder="输入账号 (如 admin)" />
      </view>
      <view class="form-item">
        <text class="item-label">登录密码</text>
        <input v-model="form.password" type="password" class="item-input" placeholder="输入密码" />
      </view>

      <button class="login-btn" :loading="loading" @click="handleLogin">
        立即登录
      </button>
    </view>

    <view class="login-footer">
      <view class="quick-chips">
        <view class="chip-item chip-item--admin" @click="fillAccount('admin')">
          <text class="chip-title">👑 超管凭证: admin</text>
        </view>
        <view class="chip-item chip-item--user" @click="fillAccount('user')">
          <text class="chip-title">👤 普通用户: user</text>
        </view>
      </view>
      <text class="footer-tip">密码统一为: Admin@123456 / User@123456</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { apiLogin } from "../../api/auth";

const loading = ref(false);
const form = reactive({
  tenantCode: "platform",
  username: "admin",
  password: "Admin@123456"
});

function fillAccount(type: "admin" | "user") {
  form.tenantCode = "platform";
  if (type === "user") {
    form.username = "user";
    form.password = "User@123456";
    uni.showToast({ title: "已填入普通用户 user", icon: "none" });
  } else {
    form.username = "admin";
    form.password = "Admin@123456";
    uni.showToast({ title: "已填入超管 admin", icon: "none" });
  }
}

async function handleLogin() {
  if (!form.username || !form.password) {
    uni.showToast({ title: "请填写完整账号与密码", icon: "none" });
    return;
  }
  loading.value = true;
  try {
    const res = await apiLogin(form);
    uni.setStorageSync("sb_access_token", res.accessToken);
    uni.setStorageSync("sb_refresh_token", res.refreshToken);
    uni.setStorageSync("sb_tenant_id", "1");
    if (res.currentUser) {
      uni.setStorageSync("sb_current_user", JSON.stringify(res.currentUser));
    }
    uni.showToast({ title: "登录成功", icon: "success" });
    setTimeout(() => {
      uni.switchTab({ url: "/pages/index/index" });
    }, 500);
  } catch (err: any) {
    uni.showToast({ title: err?.message || "登录失败", icon: "none" });
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.mobile-login-shell {
  min-height: 100vh;
  background: linear-gradient(180deg, #0f172a 0%, #1e293b 100%);
  padding: 40px 24px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  box-sizing: border-box;
}

.brand-box {
  text-align: center;
  margin-bottom: 36px;
}

.brand-logo {
  font-size: 48px;
  margin-bottom: 12px;
}

.brand-title {
  display: block;
  font-size: 26px;
  font-weight: 800;
  color: #ffffff;
  letter-spacing: 1px;
}

.brand-desc {
  display: block;
  font-size: 13px;
  color: #94a3b8;
  margin-top: 4px;
}

.login-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 24px;
}

.form-item {
  margin-bottom: 16px;
}

.item-label {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 6px;
}

.item-input {
  background: rgba(255, 255, 255, 0.08);
  height: 44px;
  border-radius: 8px;
  padding: 0 14px;
  color: #ffffff;
  font-size: 14px;
}

.login-btn {
  margin-top: 24px;
  background: #0284c7;
  color: #ffffff;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 600;
  height: 44px;
  line-height: 44px;
  border: none;
}

.login-footer {
  margin-top: 24px;
  text-align: center;
}

.quick-chips {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-bottom: 12px;
}

.chip-item {
  padding: 6px 12px;
  border-radius: 20px;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: rgba(255, 255, 255, 0.05);
}

.chip-item--admin {
  border-color: rgba(56, 189, 248, 0.4);
  background: rgba(56, 189, 248, 0.1);
}

.chip-item--user {
  border-color: rgba(52, 211, 153, 0.4);
  background: rgba(52, 211, 153, 0.1);
}

.chip-title {
  font-size: 11px;
  color: #e2e8f0;
}

.footer-tip {
  font-size: 11px;
  color: #64748b;
}
</style>
