<template>
  <view class="mobile-login-shell">
    <!-- Ambient Chromatic Glow -->
    <view class="glow-orb glow-orb--cyan" />
    <view class="glow-orb glow-orb--purple" />

    <view class="login-card">
      <!-- 品牌标识与门户环境胶囊 -->
      <view class="brand-row">
        <view class="brand-capsule">
          <view class="capsule-logo-badge">
            <AppIcon name="logo" :size="16" color="#2563eb" :stroke-width="1.8" />
          </view>
          <text class="capsule-brand-name">天枢云基</text>
          <text class="capsule-sep">|</text>
          <text class="capsule-portal-name">{{ portal?.clientName || (isTenantPortal ? currentTenantTitle : "平台统一门户") }}</text>
        </view>

        <view v-if="isTenantPortal" class="platform-entry-btn" @click="switchToPlatform">
          <text class="platform-entry-text">平台管理入口 →</text>
        </view>
      </view>

      <view class="title-box">
        <text class="login-title">欢迎登录</text>
        <text class="login-desc">{{ isTenantPortal ? '请输入您的企业租户凭据以进入工作空间' : '请输入您的企业租户凭证以进入控制台' }}</text>
      </view>

      <view class="form-container">
        <view class="form-item">
          <text class="item-label">账号凭证</text>
          <view class="input-wrap">
            <AppIcon name="user" :size="16" color="#94a3b8" />
            <input
              v-model="form.username"
              class="item-input"
              placeholder="用户名 / 手机号 / 企业邮箱"
            />
          </view>
        </view>

        <view class="form-item">
          <text class="item-label">访问密码</text>
          <view class="input-wrap">
            <AppIcon name="shield" :size="16" color="#94a3b8" />
            <input
              v-model="form.password"
              type="password"
              class="item-input"
              placeholder="请输入登录密码"
            />
          </view>
        </view>

        <button class="login-btn" :loading="loading" @click="handleLogin">
          登 录
        </button>
      </view>

      <!-- 快速填入凭证芯片 -->
      <view class="quick-chips">
        <template v-if="!isTenantPortal">
          <view class="chip-item chip-item--admin" @click="fillAccount('admin')">
            <text class="chip-title">超管: admin / Admin@123456</text>
          </view>
          <view class="chip-item chip-item--user" @click="fillAccount('user')">
            <text class="chip-title">普通用户: user / User@123456</text>
          </view>
        </template>
        <template v-else>
          <view class="chip-item chip-item--user" @click="fillAccount('user')">
            <text class="chip-title">租户员工: user / User@123456</text>
          </view>
          <view class="chip-item chip-item--admin" @click="fillAccount('admin')">
            <text class="chip-title">租户超管: admin / Admin@123456</text>
          </view>
        </template>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { onLoad, onShow } from "@dcloudio/uni-app";
import { apiLogin, apiGetPortalEntry, type PortalEntry } from "../../api/auth";
import AppIcon from "../../components/ui/AppIcon.vue";

const loading = ref(false);
const portal = ref<PortalEntry | null>(null);

const form = reactive({
  tenantCode: "",
  clientId: "",
  username: "admin",
  password: "Admin@123456"
});

const isTenantPortal = computed(() => {
  return !!form.tenantCode && form.tenantCode.toLowerCase() !== "platform";
});

const currentTenantTitle = computed(() => {
  if (portal.value?.clientName) return portal.value.clientName;
  if (form.tenantCode) return `${form.tenantCode.toUpperCase()} 企业空间`;
  return "企业工作台";
});

function extractTenantCodeFromUrl(query?: any): string {
  if (query?.tenantCode) return String(query.tenantCode).trim();
  if (query?.tenant) return String(query.tenant).trim();
  // #ifdef H5
  if (typeof window !== "undefined") {
    if (window.location.search) {
      const sp = new URLSearchParams(window.location.search);
      const c = sp.get("tenantCode") || sp.get("tenant");
      if (c) return c.trim();
    }
    if (window.location.hash.includes("?")) {
      const hashQuery = window.location.hash.split("?")[1];
      const hp = new URLSearchParams(hashQuery);
      const c = hp.get("tenantCode") || hp.get("tenant");
      if (c) return c.trim();
    }
  }
  // #endif
  return "";
}

async function loadPortal(tenantCode?: string) {
  try {
    const entry = await apiGetPortalEntry({
      tenantCode: tenantCode || undefined
    });
    portal.value = entry;
    form.clientId = entry.clientId;
    if (entry.tenantCode) {
      form.tenantCode = entry.tenantCode;
    }
  } catch (e) {
    // 降级容灾
  }
}

onLoad((query: any) => {
  const code = extractTenantCodeFromUrl(query);
  if (code) {
    form.tenantCode = code;
    form.username = "user";
    form.password = "User@123456";
  } else {
    form.tenantCode = "";
    form.username = "admin";
    form.password = "Admin@123456";
  }
  loadPortal(form.tenantCode);
});

onShow(() => {
  const code = extractTenantCodeFromUrl();
  if (code && code !== form.tenantCode) {
    form.tenantCode = code;
    form.username = "user";
    form.password = "User@123456";
    loadPortal(code);
  }
});

// #ifdef H5
if (typeof window !== "undefined") {
  window.addEventListener("hashchange", () => {
    const code = extractTenantCodeFromUrl();
    if (code !== form.tenantCode) {
      form.tenantCode = code;
      if (code) {
        form.username = "user";
        form.password = "User@123456";
      } else {
        form.username = "admin";
        form.password = "Admin@123456";
      }
      loadPortal(code);
    }
  });
}
// #endif

onMounted(() => {
  if (!portal.value) {
    const code = extractTenantCodeFromUrl();
    if (code) {
      form.tenantCode = code;
    }
    loadPortal(form.tenantCode);
  }
});

function switchToPlatform() {
  form.tenantCode = "";
  form.username = "admin";
  form.password = "Admin@123456";
  // #ifdef H5
  if (typeof window !== "undefined") {
    const url = new URL(window.location.href);
    url.searchParams.delete("tenantCode");
    url.searchParams.delete("tenant");
    if (url.hash.includes("?")) {
      url.hash = url.hash.split("?")[0];
    }
    window.history.replaceState({}, "", url.toString());
  }
  // #endif
  loadPortal("");
  uni.showToast({ title: "已切换至平台统一门户", icon: "none" });
}

function fillAccount(type: "admin" | "user") {
  if (type === "user") {
    form.username = "user";
    form.password = "User@123456";
  } else {
    form.username = "admin";
    form.password = "Admin@123456";
  }
}

async function handleLogin() {
  if (!form.username || !form.password) {
    uni.showToast({ title: "请输入用户名与密码", icon: "none" });
    return;
  }
  loading.value = true;
  try {
    const payload = {
      username: form.username.trim(),
      password: form.password.trim(),
      clientId: form.clientId || undefined,
      ...(form.tenantCode.trim() ? { tenantCode: form.tenantCode.trim() } : {})
    };
    const res = await apiLogin(payload);
    uni.setStorageSync("sb_access_token", res.accessToken);
    uni.setStorageSync("sb_refresh_token", res.refreshToken);
    if (res.currentUser?.tenantId) {
      uni.setStorageSync("sb_tenant_id", String(res.currentUser.tenantId));
    }
    if (res.currentUser?.tenantCode) {
      uni.setStorageSync("sb_tenant_code", res.currentUser.tenantCode);
    }
    if (res.currentUser) {
      uni.setStorageSync("sb_current_user", JSON.stringify(res.currentUser));
    }
    uni.showToast({ title: "登录成功", icon: "success" });
    setTimeout(() => {
      uni.switchTab({ url: "/pages/index/index" });
    }, 450);
  } catch (err: any) {
    uni.showToast({ title: err?.message || "登录失败，请检查账号密码", icon: "none" });
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.mobile-login-shell {
  position: relative;
  min-height: 100vh;
  background-image:
    radial-gradient(ellipse 90% 60% at 50% -10%, rgba(191, 219, 254, 0.45), transparent 75%),
    url("/static/ios27-liquid-glass-bg.jpg");
  background-size: cover;
  background-position: center top;
  background-attachment: fixed;
  background-repeat: no-repeat;
  padding: 40px 18px 32px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  box-sizing: border-box;
  overflow: hidden;
}

.glow-orb {
  position: absolute;
  border-radius: 9999px;
  filter: blur(50px);
  pointer-events: none;
  z-index: 0;
}

.glow-orb--cyan {
  width: 240px;
  height: 240px;
  background: rgba(56, 189, 248, 0.25);
  top: 40px;
  left: -40px;
}

.glow-orb--purple {
  width: 260px;
  height: 260px;
  background: rgba(168, 85, 247, 0.20);
  bottom: 80px;
  right: -50px;
}

.login-card {
  position: relative;
  z-index: 1;
  background: rgba(255, 255, 255, 0.68);
  border: 1px solid rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(36px) saturate(210%);
  -webkit-backdrop-filter: blur(36px) saturate(210%);
  box-shadow:
    inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98),
    0 20px 45px -10px rgba(12, 34, 56, 0.08);
  border-radius: 24px;
  padding: 24px 20px;
}

/* 顶部品牌胶囊与切换按钮 */
.brand-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.brand-capsule {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.95);
  padding: 4px 10px 4px 6px;
  border-radius: 9999px;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.98), 0 2px 8px rgba(15, 23, 42, 0.04);
}

.capsule-logo-badge {
  width: 24px;
  height: 24px;
  border-radius: 7px;
  background: rgba(37, 99, 235, 0.10);
  display: flex;
  align-items: center;
  justify-content: center;
}

.capsule-brand-name {
  font-size: 13px;
  font-weight: 750;
  color: #0f172a;
  letter-spacing: -0.2px;
}

.capsule-sep {
  font-size: 11px;
  color: #cbd5e1;
}

.capsule-portal-name {
  font-size: 12px;
  font-weight: 600;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.08);
  padding: 2px 7px;
  border-radius: 6px;
}

.platform-entry-btn {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.90);
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.04);
  cursor: pointer;
  transition: transform 0.15s ease;
}

.platform-entry-btn:active {
  transform: scale(0.95);
}

.platform-entry-text {
  font-size: 11px;
  font-weight: 600;
  color: #475569;
}

.title-box {
  text-align: center;
  margin-bottom: 22px;
}

.login-title {
  font-size: 22px;
  font-weight: 800;
  color: #0f172a;
  display: block;
  letter-spacing: -0.3px;
}

.login-desc {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
  margin-top: 5px;
  display: block;
}

.form-container {
  margin-bottom: 18px;
}

.form-item {
  margin-bottom: 16px;
}

.item-label {
  display: block;
  font-size: 12.5px;
  color: #334155;
  margin-bottom: 6px;
  font-weight: 600;
}

.input-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.60);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 14px;
  padding: 0 14px;
  height: 48px;
  box-sizing: border-box;
  box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.04);
  transition: all 0.2s ease;
}

.input-wrap:focus-within {
  background: rgba(255, 255, 255, 0.90);
  border-color: rgba(37, 99, 235, 0.6);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.item-input {
  flex: 1;
  color: #0f172a;
  font-size: 14px;
  height: 100%;
  background: transparent;
}

.login-btn {
  margin-top: 8px;
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #ffffff;
  border-radius: 12px;
  font-size: 15.5px;
  font-weight: 600;
  height: 48px;
  line-height: 48px;
  border: 1px solid rgba(255, 255, 255, 0.4);
  box-shadow:
    inset 0 1px 1.5px rgba(255, 255, 255, 0.4),
    0 8px 20px rgba(37, 99, 235, 0.28);
  transition: transform 0.15s ease;
}

.login-btn:active {
  transform: scale(0.98);
}

.quick-chips {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 6px;
}

.chip-item {
  padding: 8px 14px;
  border-radius: 12px;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.90);
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.95), 0 2px 8px rgba(15, 23, 42, 0.04);
  text-align: center;
  transition: transform 0.15s ease;
}

.chip-item:active {
  transform: scale(0.98);
}

.chip-item--admin {
  border-color: rgba(37, 99, 235, 0.30);
  background: rgba(239, 246, 255, 0.75);
}

.chip-item--user {
  border-color: rgba(16, 185, 129, 0.30);
  background: rgba(236, 253, 245, 0.75);
}

.chip-title {
  font-size: 12px;
  font-weight: 550;
  color: #1e293b;
}
</style>
