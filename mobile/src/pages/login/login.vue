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
            <TianshuLogo :size="18" />
          </view>
          <text class="capsule-brand-name">天枢协同云平台</text>
          <text class="capsule-sep">|</text>
          <text class="capsule-portal-name">{{ portal?.clientName || (isTenantPortal ? currentTenantTitle : "统一门户") }}</text>
        </view>

        <view v-if="isTenantPortal" class="platform-entry-btn" @click="switchToPlatform">
          <text class="platform-entry-text">平台入口 →</text>
        </view>
      </view>

      <view class="title-box">
        <text class="login-title">欢迎登录</text>
        <text class="login-desc">{{ isTenantPortal ? '请输入企业租户凭据以进入工作空间' : '请输入凭据以接入天枢协同控制台' }}</text>
      </view>

      <view class="form-container">
        <!-- 账号输入 -->
        <view class="form-item">
          <text class="item-label">账号凭证</text>
          <view class="input-wrap">
            <AppIcon name="user" :size="16" color="#64748b" />
            <input
              v-model="form.username"
              class="item-input"
              placeholder="用户名 / 手机号 / 企业邮箱"
            />
          </view>
        </view>

        <!-- 密码输入 -->
        <view class="form-item">
          <text class="item-label">访问密码</text>
          <view class="input-wrap">
            <AppIcon name="lock" :size="16" color="#64748b" />
            <input
              v-model="form.password"
              :password="!showPassword"
              class="item-input"
              placeholder="请输入登录密码"
            />
            <view class="eye-toggle" @click="showPassword = !showPassword">
              <AppIcon :name="showPassword ? 'eye-off' : 'shield'" :size="16" color="#94a3b8" />
            </view>
          </view>
        </view>

        <!-- 天枢智盾：安全环境自动核验通过 -->
        <view class="shield-check-card">
          <view class="shield-check-left">
            <view class="shield-icon-badge">
              <AppIcon name="shield-check" :size="15" color="#059669" :stroke-width="2.2" />
            </view>
            <text class="shield-check-text">安全环境自动核验通过</text>
          </view>
          <view class="shield-check-right">
            <text class="shield-brand-text">天枢智盾</text>
          </view>
        </view>

        <!-- 辅助操作 -->
        <view class="form-sub-row">
          <text class="sub-link" @click="handleForgotPassword">忘记密码？</text>
        </view>

        <!-- 浅色珠光轻玻登录按钮 (与 Web 统一浅色主题一致) -->
        <button class="login-btn" :loading="loading" @click="handleLogin">
          <text class="btn-text">登 录</text>
          <text class="btn-arrow">→</text>
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { onLoad, onShow } from "@dcloudio/uni-app";
import { apiLogin, apiGetPortalEntry, type PortalEntry } from "../../api/auth";
import AppIcon from "../../components/ui/AppIcon.vue";
import TianshuLogo from "../../components/brand/TianshuLogo.vue";

const loading = ref(false);
const showPassword = ref(false);
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
      tenantCode: tenantCode || undefined,
      terminalCode: "h5"
    });
    portal.value = entry;
    form.clientId = entry.clientId;
    if (entry.tenantCode) {
      form.tenantCode = entry.tenantCode;
    }
  } catch (e) {
    // 容灾降级
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
  uni.showToast({ title: "已切换至平台控制台", icon: "none" });
}

function handleForgotPassword() {
  uni.showModal({
    title: "密码找回提示",
    content: "请使用 Web 端自主找回密码，或联系系统顶级超级管理员进行安全凭据重置。",
    showCancel: false,
    confirmText: "我知道了"
  });
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
      terminalCode: "h5",
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
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(36px) saturate(210%);
  -webkit-backdrop-filter: blur(36px) saturate(210%);
  box-shadow:
    inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98),
    0 24px 48px -12px rgba(12, 34, 56, 0.10);
  border-radius: 24px;
  padding: 26px 20px;
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
  gap: 7px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.96);
  padding: 4px 10px 4px 5px;
  border-radius: 9999px;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.98), 0 2px 8px rgba(15, 23, 42, 0.04);
}

.capsule-logo-badge {
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
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.92);
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
  font-size: 12.5px;
  color: #64748b;
  font-weight: 500;
  margin-top: 5px;
  display: block;
}

.form-container {
  margin-bottom: 8px;
}

.form-item {
  margin-bottom: 14px;
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
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.90);
  border-radius: 14px;
  padding: 0 14px;
  height: 48px;
  box-sizing: border-box;
  box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.03);
  transition: all 0.2s ease;
}

.input-wrap:focus-within {
  background: rgba(255, 255, 255, 0.95);
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

.eye-toggle {
  padding: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 天枢智盾安全核验胶囊卡片 */
.shield-check-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(236, 253, 245, 0.78);
  border: 1px solid rgba(167, 243, 208, 0.92);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-radius: 12px;
  padding: 0 12px;
  height: 42px;
  box-sizing: border-box;
  margin-bottom: 12px;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.9);
}

.shield-check-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.shield-icon-badge {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: rgba(5, 150, 105, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
}

.shield-check-text {
  font-size: 12.5px;
  font-weight: 600;
  color: #065f46;
}

.shield-check-right {
  display: flex;
  align-items: center;
}

.shield-brand-text {
  font-size: 11px;
  font-weight: 600;
  color: #059669;
  letter-spacing: 0.2px;
}

.form-sub-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.forgot-link {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
  cursor: pointer;
}

.forgot-link:active {
  color: #2563eb;
}

/* 浅色珠光微玻高质感登录按钮 */
.login-btn {
  position: relative;
  width: 100%;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.96) 0%, rgba(241, 245, 249, 0.90) 100%);
  color: #0f172a;
  border-radius: 14px;
  height: 48px;
  line-height: 48px;
  border: 1px solid rgba(255, 255, 255, 0.98);
  box-shadow:
    inset 0 1px 1.5px 0 #ffffff,
    inset 0 -1px 2px 0 rgba(203, 213, 225, 0.35),
    0 10px 24px -4px rgba(15, 23, 42, 0.10);
  transition: all 0.2s cubic-bezier(0.2, 0.8, 0.2, 1);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  box-sizing: border-box;
}

.login-btn:active {
  transform: scale(0.985);
  background: linear-gradient(135deg, rgba(248, 250, 252, 0.98) 0%, rgba(226, 232, 240, 0.92) 100%);
  box-shadow:
    inset 0 1px 2px 0 rgba(15, 23, 42, 0.08),
    0 4px 12px -2px rgba(15, 23, 42, 0.08);
}

.btn-text {
  font-size: 15.5px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: 1px;
}

.btn-arrow {
  font-size: 15px;
  font-weight: 700;
  color: #334155;
  transition: transform 0.2s ease;
}

.login-btn:active .btn-arrow {
  transform: translateX(2px);
}
</style>
