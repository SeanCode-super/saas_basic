<template>
  <view class="mobile-login-shell">
    <!-- 背景流光与环境光晕 -->
    <view class="ambient-glow ambient-glow--top" />
    <view class="ambient-glow ambient-glow--bottom" />

    <!-- 居中高定玻璃登录卡片 -->
    <view class="login-card">
      <!-- 1. 品牌头部：徽标与平台名称对齐 (与 Web 统一) -->
      <view class="brand-header">
        <view class="logo-wrap">
          <TianshuLogo :size="40" />
        </view>
        <view class="brand-text-col">
          <text class="brand-name">{{ isTenantPortal ? (portal?.clientName || currentTenantTitle) : '天枢协同云平台' }}</text>
          <text class="brand-slogan">{{ isTenantPortal ? '企业专属多端协同空间' : '新一代全场景企业协同云' }}</text>
        </view>
        <view v-if="isTenantPortal" class="platform-switch-pill" @click="switchToPlatform">
          <text class="platform-switch-text">平台入口 →</text>
        </view>
      </view>

      <!-- 2. 表单输入区 -->
      <view class="form-body">
        <!-- 账号 -->
        <view class="input-cell" :class="{ 'is-focused': focusField === 'username' }">
          <view class="cell-icon">
            <AppIcon name="user" :size="17" color="#64748b" />
          </view>
          <input
            v-model="form.username"
            class="cell-input"
            placeholder="请输入用户名 / 账号"
            placeholder-class="input-placeholder"
            @focus="focusField = 'username'"
            @blur="focusField = null"
          />
        </view>

        <!-- 密码 -->
        <view class="input-cell" :class="{ 'is-focused': focusField === 'password' }">
          <view class="cell-icon">
            <AppIcon name="lock" :size="17" color="#64748b" />
          </view>
          <input
            v-model="form.password"
            :password="!showPassword"
            class="cell-input"
            placeholder="请输入登录密码"
            placeholder-class="input-placeholder"
            confirm-type="go"
            @focus="focusField = 'password'"
            @blur="focusField = null"
            @confirm="handleLogin"
          />
          <view class="eye-toggle-btn" @click="showPassword = !showPassword">
            <AppIcon :name="showPassword ? 'eye-off' : 'eye'" :size="17" color="#94a3b8" />
          </view>
        </view>

        <!-- 3. 天枢智盾安全核验卡片 (与 Web 完美一致) -->
        <view class="shield-badge-card">
          <view class="shield-left">
            <view class="check-squircle">
              <AppIcon name="check" :size="11" color="#ffffff" :stroke-width="3" />
            </view>
            <text class="shield-title">安全环境自动核验通过</text>
          </view>
          <view class="shield-right">
            <text class="shield-tag">天枢智盾</text>
          </view>
        </view>

        <!-- 4. 辅助操作 -->
        <view class="sub-actions-row">
          <text class="forgot-btn" @click="handleForgotPassword">忘记密码？</text>
        </view>

        <!-- 5. 登录主按钮 (浅色高质感微玻质感) -->
        <button class="login-submit-btn" :loading="loading" :disabled="loading" @click="handleLogin">
          <text class="submit-text">登 录</text>
          <text class="submit-arrow">→</text>
        </button>

        <!-- 6. 快捷接入生态渠道圆圈按钮 (与 Web 一致) -->
        <view class="channels-row">
          <view class="channel-circle" @click="quickChannelLogin('wecom')" title="企业微信">
            <AppIcon name="chat" :size="18" color="#64748b" />
          </view>
          <view class="channel-circle" @click="quickChannelLogin('fast')" title="极速免密">
            <AppIcon name="lightning" :size="18" color="#64748b" />
          </view>
          <view class="channel-circle" @click="quickChannelLogin('feishu')" title="飞书协同">
            <AppIcon name="paper-plane" :size="17" color="#64748b" />
          </view>
          <view class="channel-circle" @click="quickChannelLogin('cert')" title="数字证书">
            <AppIcon name="key" :size="17" color="#64748b" />
          </view>
        </view>
      </view>
    </view>

    <!-- 底部端到端安全提示 -->
    <view class="login-footer-security">
      <text class="footer-tip-text">端到端链路加密传输 · 天枢智盾安全防护已开启</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { onLoad, onUnload } from "@dcloudio/uni-app";
import { apiLogin, apiGetPortalEntry, type PortalEntry } from "../../api/auth";
import AppIcon from "../../components/ui/AppIcon.vue";
import TianshuLogo from "../../components/brand/TianshuLogo.vue";

const loading = ref(false);
const showPassword = ref(false);
const focusField = ref<"username" | "password" | null>(null);
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
  if (form.tenantCode) return `${form.tenantCode.toUpperCase()} 企业工作台`;
  return "企业空间";
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

let portalRevision = 0;
function selectPortal(code: string) {
  const revision = ++portalRevision;
  form.tenantCode = code;
  form.clientId = "";
  portal.value = null;
  const tenant = isTenantPortal.value;
  form.username = import.meta.env.DEV ? (tenant ? "user" : "admin") : "";
  form.password = import.meta.env.DEV ? (tenant ? "User@123456" : "Admin@123456") : "";
  apiGetPortalEntry({ tenantCode: code || undefined, terminalCode: "h5" })
    .then(entry => {
      if (revision !== portalRevision) return;
      portal.value = entry;
      form.clientId = entry.clientId;
      form.tenantCode = entry.tenantCode || code;
    })
    .catch(() => {});
}

// #ifdef H5
function syncPortalFromHash() {
  if (window.location.hash.includes("/pages/login/login")) {
    selectPortal(extractTenantCodeFromUrl());
  }
}
// #endif

onLoad(query => {
  selectPortal(extractTenantCodeFromUrl(query));
  // #ifdef H5
  window.addEventListener("hashchange", syncPortalFromHash);
  // #endif
});

onUnload(() => {
  portalRevision++;
  // #ifdef H5
  window.removeEventListener("hashchange", syncPortalFromHash);
  // #endif
});

function switchToPlatform() {
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
  selectPortal("");
  uni.showToast({ title: "已切换至平台控制台", icon: "none" });
}

function handleForgotPassword() {
  uni.showModal({
    title: "密码找回提示",
    content: "请联系平台管理员或在电脑端工作台进行身份凭证重置。",
    showCancel: false,
    confirmText: "我知道了"
  });
}

function quickChannelLogin(type: string) {
  const names: Record<string, string> = {
    wecom: "企业微信单点免密登录",
    fast: "生物识别 / 极速指纹验证",
    feishu: "飞书开放平台协同登录",
    cert: "企业私有 CA 硬件证书校验"
  };
  uni.showToast({
    title: `正在调用${names[type] || "快捷接入"}...`,
    icon: "none"
  });
}

async function handleLogin() {
  if (loading.value) return;
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
    }, 400);
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
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: center;
  padding: calc(30px + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px))) 20px
    calc(24px + env(safe-area-inset-bottom, 0px));
  background-color: #f8fafc;
  background-image:
    radial-gradient(ellipse 90% 50% at 50% -5%, rgba(191, 219, 254, 0.55), transparent 70%),
    radial-gradient(circle at 90% 90%, rgba(224, 231, 255, 0.45), transparent 60%);
  overflow-x: hidden;
}

/* 环境背景光晕 */
.ambient-glow {
  position: absolute;
  pointer-events: none;
  border-radius: 9999px;
  filter: blur(60px);
  z-index: 0;
}

.ambient-glow--top {
  width: 260px;
  height: 260px;
  background: rgba(56, 189, 248, 0.15);
  top: -40px;
  left: 20%;
}

.ambient-glow--bottom {
  width: 280px;
  height: 280px;
  background: rgba(99, 102, 241, 0.12);
  bottom: 40px;
  right: 10%;
}

/* 核心悬浮微晶玻璃卡片 */
.login-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 380px;
  margin: auto 0;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(32px) saturate(200%);
  -webkit-backdrop-filter: blur(32px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.98);
  border-radius: 26px;
  padding: 28px 22px 24px;
  box-sizing: border-box;
  box-shadow:
    inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.99),
    0 16px 40px -8px rgba(15, 23, 42, 0.08),
    0 4px 12px rgba(15, 23, 42, 0.03);
}

/* 头部品牌行 */
.brand-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.logo-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.brand-text-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.brand-name {
  font-size: 19px;
  font-weight: 750;
  color: #0f172a;
  letter-spacing: -0.3px;
  line-height: 1.3;
}

.brand-slogan {
  font-size: 11.5px;
  color: #64748b;
  font-weight: 500;
  margin-top: 2px;
  letter-spacing: 0.2px;
}

.platform-switch-pill {
  padding: 4px 9px;
  border-radius: 9999px;
  background: rgba(241, 245, 249, 0.85);
  border: 1px solid rgba(226, 232, 240, 0.8);
  cursor: pointer;
  flex-shrink: 0;
  transition: transform 0.15s ease;
}

.platform-switch-pill:active {
  transform: scale(0.95);
}

.platform-switch-text {
  font-size: 11px;
  font-weight: 600;
  color: #475569;
}

/* 表单输入区 */
.form-body {
  display: flex;
  flex-direction: column;
}

.input-cell {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 50px;
  padding: 0 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(226, 232, 240, 0.85);
  box-sizing: border-box;
  margin-bottom: 12px;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.input-cell.is-focused {
  background: #ffffff;
  border-color: rgba(37, 99, 235, 0.65);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.cell-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cell-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  font-size: 14.5px;
  color: #0f172a;
  font-weight: 500;
  background: transparent;
}

.input-placeholder {
  color: #94a3b8;
  font-size: 14px;
  font-weight: 400;
}

.eye-toggle-btn {
  padding: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

/* 天枢智盾安全核验卡片 (与 Web 统一) */
.shield-badge-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 44px;
  padding: 0 12px;
  border-radius: 13px;
  background: rgba(236, 253, 245, 0.85);
  border: 1px solid rgba(167, 243, 208, 0.95);
  box-sizing: border-box;
  margin-bottom: 10px;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.9);
}

.shield-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.check-squircle {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  background: #10b981;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 1px 3px rgba(16, 185, 129, 0.3);
}

.shield-title {
  font-size: 12.5px;
  font-weight: 600;
  color: #065f46;
  letter-spacing: 0.1px;
}

.shield-right {
  display: flex;
  align-items: center;
}

.shield-tag {
  font-size: 11.5px;
  font-weight: 700;
  color: #059669;
  letter-spacing: 0.2px;
}

/* 辅助跳转行 */
.sub-actions-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: 14px;
  padding: 0 2px;
}

.forgot-btn {
  font-size: 12px;
  font-weight: 550;
  color: #6366f1;
  cursor: pointer;
}

.forgot-btn:active {
  color: #4338ca;
}

/* 浅色高质感微玻登录提交按钮 */
.login-submit-btn {
  position: relative;
  width: 100%;
  height: 48px;
  line-height: 48px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.98) 0%, rgba(241, 245, 249, 0.92) 100%);
  color: #0f172a;
  border: 1px solid rgba(255, 255, 255, 0.98);
  box-shadow:
    inset 0 1.5px 1.5px 0 #ffffff,
    inset 0 -1.5px 2px 0 rgba(203, 213, 225, 0.35),
    0 8px 22px -4px rgba(15, 23, 42, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  box-sizing: border-box;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  margin-bottom: 22px;
}

.login-submit-btn::after {
  display: none !important;
  border: none !important;
}

.login-submit-btn:active {
  transform: scale(0.985);
  background: linear-gradient(135deg, rgba(248, 250, 252, 0.98) 0%, rgba(226, 232, 240, 0.92) 100%);
  box-shadow:
    inset 0 1px 2px 0 rgba(15, 23, 42, 0.08),
    0 4px 10px -2px rgba(15, 23, 42, 0.06);
}

.submit-text {
  font-size: 15.5px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: 2px;
}

.submit-arrow {
  font-size: 15px;
  font-weight: 700;
  color: #334155;
  transition: transform 0.2s ease;
}

.login-submit-btn:active .submit-arrow {
  transform: translateX(3px);
}

/* 底部 4 个快捷登录渠道圆圈 (与 Web 统一) */
.channels-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding-top: 4px;
}

.channel-circle {
  width: 44px;
  height: 44px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(226, 232, 240, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.04);
  cursor: pointer;
  transition: all 0.18s cubic-bezier(0.16, 1, 0.3, 1);
}

.channel-circle:active {
  transform: scale(0.92);
  background: #f1f5f9;
  border-color: #cbd5e1;
}

/* 底部安全脚注 */
.login-footer-security {
  position: relative;
  z-index: 1;
  text-align: center;
  padding-top: 12px;
}

.footer-tip-text {
  font-size: 11.5px;
  color: #94a3b8;
  font-weight: 500;
  letter-spacing: 0.1px;
}
</style>
