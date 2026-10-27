<template>
  <view class="mobile-login-shell">
    <view class="brand-box">
      <view class="brand-logo">
        <AppIcon name="logo" :size="52" color="#38bdf8" :stroke-width="1.8" />
      </view>
      <text class="brand-title">天枢云基</text>
      <text class="brand-desc">Enterprise PaaS · 跨端数字中枢移动工作台</text>
    </view>

    <view class="login-card">
      <view class="form-item">
        <text class="item-label">企业租户编码</text>
        <input v-model="form.tenantCode" class="item-input" placeholder="例如: platform" />
      </view>
      <view class="form-item">
        <text class="item-label">员工用户名 / 手机号</text>
        <input v-model="form.username" class="item-input" placeholder="输入账号 (如 user 或 admin)" />
      </view>
      <view class="form-item">
        <text class="item-label">登录访问密码</text>
        <input v-model="form.password" type="password" class="item-input" placeholder="输入访问密码" />
      </view>

      <button class="login-btn" :loading="loading" @click="handleLogin">
        安全登录
      </button>
    </view>

    <view class="login-footer">
      <view class="quick-chips">
        <view class="chip-item chip-item--admin" @click="fillAccount('admin')">
          <text class="chip-title">超管凭据: admin</text>
        </view>
        <view class="chip-item chip-item--user" @click="fillAccount('user')">
          <text class="chip-title">普通员工: user</text>
        </view>
      </view>
      <text class="footer-tip">端到端链路加密传输 · 账号安全策略已防护</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { apiLogin } from "../../api/auth";
import AppIcon from "../../components/ui/AppIcon.vue";

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
  background:
    radial-gradient(circle at 10% 15%, rgba(56, 189, 248, 0.18), transparent 50%),
    radial-gradient(circle at 90% 85%, rgba(99, 102, 241, 0.18), transparent 50%),
    #070a13;
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
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 16px;
}

.brand-title {
  display: block;
  font-size: 26px;
  font-weight: 800;
  color: #ffffff;
  letter-spacing: 1.5px;
}

.brand-desc {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-top: 6px;
  letter-spacing: 0.2px;
}

.login-card {
  background: rgba(30, 41, 59, 0.65);
  border: 1px solid rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(28px) saturate(190%);
  -webkit-backdrop-filter: blur(28px) saturate(190%);
  box-shadow: inset 0 1px 1px 0 rgba(255, 255, 255, 0.25), 0 24px 48px -12px rgba(0, 0, 0, 0.5);
  border-radius: 20px;
  padding: 26px 20px;
}

.form-item {
  margin-bottom: 16px;
}

.item-label {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 6px;
  font-weight: 500;
}

.item-input {
  background: rgba(15, 23, 42, 0.65);
  border: 1px solid rgba(255, 255, 255, 0.1);
  height: 46px;
  border-radius: 10px;
  padding: 0 14px;
  color: #ffffff;
  font-size: 14px;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.2);
}

.login-btn {
  margin-top: 24px;
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #ffffff;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  height: 46px;
  line-height: 46px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.3), 0 8px 20px rgba(37, 99, 235, 0.35);
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
