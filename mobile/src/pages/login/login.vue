<template>
  <view class="mobile-login-shell">
    <!-- 1. PC 同款宏观宇宙微光背景 (环境光晕、宇宙网格、微星矩阵) -->
    <view class="universe-aurora" aria-hidden="true">
      <view class="aurora-orb orb-primary" />
      <view class="aurora-orb orb-secondary" />
      <view class="aurora-orb orb-core" />
      <view class="aurora-orb orb-bottom" />
      <view class="universe-grid" />
      <view class="universe-stars" />
    </view>

    <!-- 2. PC 同款天体仪动态星轨 (按移动端视口缩放适配并呈现纵深景深) -->
    <view class="cosmic-astrolabe-zone" aria-hidden="true">
      <svg viewBox="0 0 640 640" fill="none" xmlns="http://www.w3.org/2000/svg" class="grand-astrolabe">
        <defs>
          <radialGradient :id="`cosmos-nebula-${svgUid}`" cx="50%" cy="50%" r="50%">
            <stop offset="0%" :stop-color="astroPalette.nebulaCenter" stop-opacity="0.34" />
            <stop offset="44%" :stop-color="astroPalette.nebulaMid" stop-opacity="0.20" />
            <stop offset="100%" :stop-color="astroPalette.nebulaEdge" stop-opacity="0" />
          </radialGradient>
          <radialGradient :id="`cosmos-core-aura-${svgUid}`" cx="50%" cy="50%" r="50%">
            <stop offset="0%" :stop-color="astroPalette.coreAuraCenter" stop-opacity="0.56" />
            <stop offset="46%" :stop-color="astroPalette.coreAuraMid" stop-opacity="0.24" />
            <stop offset="100%" :stop-color="astroPalette.nebulaEdge" stop-opacity="0" />
          </radialGradient>
          <linearGradient :id="`orbit-grad-a-${svgUid}`" x1="56" y1="420" x2="584" y2="220" gradientUnits="userSpaceOnUse">
            <stop offset="0%" :stop-color="astroPalette.orbitStart" stop-opacity="0.22" />
            <stop offset="50%" :stop-color="astroPalette.orbitMid" stop-opacity="0.96" />
            <stop offset="100%" :stop-color="astroPalette.orbitEnd" stop-opacity="0.26" />
          </linearGradient>
          <linearGradient :id="`orbit-grad-b-${svgUid}`" x1="88" y1="200" x2="552" y2="440" gradientUnits="userSpaceOnUse">
            <stop offset="0%" :stop-color="astroPalette.orbitMid" stop-opacity="0.18" />
            <stop offset="50%" :stop-color="astroPalette.orbitStart" stop-opacity="0.82" />
            <stop offset="100%" :stop-color="astroPalette.orbitMid" stop-opacity="0.22" />
          </linearGradient>
          <linearGradient :id="`orbit-grad-c-${svgUid}`" x1="200" y1="110" x2="440" y2="530" gradientUnits="userSpaceOnUse">
            <stop offset="0%" :stop-color="astroPalette.orbitMid" stop-opacity="0.12" />
            <stop offset="50%" :stop-color="astroPalette.orbitEnd" stop-opacity="0.50" />
            <stop offset="100%" :stop-color="astroPalette.orbitMid" stop-opacity="0.14" />
          </linearGradient>
          <linearGradient :id="`star-prism-${svgUid}`" x1="240" y1="236" x2="400" y2="404" gradientUnits="userSpaceOnUse">
            <stop offset="0%" :stop-color="astroPalette.starTop" />
            <stop offset="48%" :stop-color="astroPalette.starMid" />
            <stop offset="100%" :stop-color="astroPalette.starBottom" />
          </linearGradient>
          <linearGradient :id="`radar-cone-${svgUid}`" x1="320" y1="320" x2="520" y2="112" gradientUnits="userSpaceOnUse">
            <stop offset="0%" :stop-color="astroPalette.orbitMid" stop-opacity="0.22" />
            <stop offset="65%" :stop-color="astroPalette.orbitEnd" stop-opacity="0.06" />
            <stop offset="100%" :stop-color="astroPalette.orbitMid" stop-opacity="0" />
          </linearGradient>
        </defs>

        <circle cx="320" cy="320" r="280" :fill="`url(#cosmos-nebula-${svgUid})`" />
        <circle cx="320" cy="320" r="170" :fill="`url(#cosmos-core-aura-${svgUid})`" />

        <circle
          cx="320"
          cy="320"
          r="276"
          :stroke="astroPalette.ringOuter"
          stroke-width="1.2"
          stroke-dasharray="2 10"
          class="astro-ring-outer"
        />
        <circle
          cx="320"
          cy="320"
          r="228"
          :stroke="astroPalette.ringMiddle"
          stroke-width="1.15"
          stroke-dasharray="18 10 2 10"
          class="astro-ring-middle"
        />
        <circle cx="320" cy="320" r="164" :stroke="astroPalette.ringInner" stroke-width="1" />
        <circle cx="320" cy="320" r="104" :stroke="astroPalette.ringInnerDash" stroke-width="1" stroke-dasharray="3 6" />

        <path d="M320 320 L320 44 A276 276 0 0 1 528 138 Z" :fill="`url(#radar-cone-${svgUid})`" class="astro-radar-sweep" />

        <ellipse
          cx="320"
          cy="320"
          rx="210"
          ry="62"
          transform="rotate(-68 320 320)"
          :stroke="`url(#orbit-grad-c-${svgUid})`"
          stroke-width="1.35"
        />

        <ellipse
          cx="320"
          cy="320"
          rx="252"
          ry="78"
          transform="rotate(28 320 320)"
          :stroke="`url(#orbit-grad-b-${svgUid})`"
          stroke-width="1.6"
        />
        <ellipse
          cx="320"
          cy="320"
          rx="252"
          ry="78"
          transform="rotate(28 320 320)"
          :stroke="astroPalette.pulseSecondary"
          stroke-width="2.2"
          stroke-dasharray="36 440"
          class="astro-pulse-track-rev"
        />

        <ellipse
          cx="320"
          cy="320"
          rx="268"
          ry="86"
          transform="rotate(-24 320 320)"
          :stroke="`url(#orbit-grad-a-${svgUid})`"
          stroke-width="2"
        />
        <ellipse
          cx="320"
          cy="320"
          rx="268"
          ry="86"
          transform="rotate(-24 320 320)"
          :stroke="astroPalette.pulsePrimary"
          stroke-width="2.6"
          stroke-dasharray="48 460"
          class="astro-pulse-track"
        />

        <circle
          cx="320"
          cy="320"
          r="56"
          :stroke="astroPalette.resonanceStroke"
          stroke-width="1.5"
          stroke-opacity="0.48"
          class="astro-resonance-ring"
        />

        <g class="astro-core-star">
          <path
            d="M320 232L341 299L408 320L341 341L320 408L299 341L232 320L299 299Z"
            :fill="`url(#star-prism-${svgUid})`"
          />
          <path
            d="M320 232L341 299L320 320L232 320L299 299Z"
            fill="#FFFFFF"
            :fill-opacity="astroPalette.starHighlightOpacity"
          />
          <path d="M320 292L348 320L320 348L292 320Z" :fill="astroPalette.starCorePivot" fill-opacity="0.72" />
          <circle cx="320" cy="320" r="12" :fill="astroPalette.orbitMid" fill-opacity="0.34" />
          <circle cx="320" cy="320" r="6" :fill="astroPalette.starCenterDot" />
        </g>

        <g class="astro-satellites">
          <circle cx="142" cy="238" r="9" :fill="astroPalette.sat1" fill-opacity="0.22" />
          <circle cx="142" cy="238" r="4.2" :fill="astroPalette.sat1" />
          <circle cx="498" cy="242" r="10" :fill="astroPalette.sat2" fill-opacity="0.24" />
          <circle cx="498" cy="242" r="4.5" :fill="astroPalette.sat2" />
          <circle cx="204" cy="424" r="8.5" :fill="astroPalette.sat3" fill-opacity="0.24" />
          <circle cx="204" cy="424" r="3.8" :fill="astroPalette.sat3" />
          <circle cx="462" cy="398" r="9" :fill="astroPalette.sat4" fill-opacity="0.22" />
          <circle cx="462" cy="398" r="4" :fill="astroPalette.sat4" />
          <circle cx="358" cy="146" r="7.5" :fill="astroPalette.sat5" fill-opacity="0.24" />
          <circle cx="358" cy="146" r="3.4" :fill="astroPalette.sat5" />
        </g>
      </svg>
    </view>

    <view class="login-card">
      <view class="brand-header">
        <view class="logo-wrap">
          <TianshuLogo :size="38" />
        </view>
        <text class="brand-name">{{ isTenantPortal ? currentTenantTitle : '天枢协同云平台' }}</text>
        <view v-if="isTenantPortal" class="platform-switch-pill" @click="switchToPlatform">
          <text class="platform-switch-text">平台入口 →</text>
        </view>
      </view>

      <view class="form-body">
        <!-- 账号输入 -->
        <view class="input-cell" :class="{ 'is-focused': focusField === 'username' }">
          <view class="cell-icon">
            <AppIcon name="user" :size="17" color="#64748b" />
          </view>
          <input
            v-model="form.username"
            class="cell-input"
            aria-label="账号"
            placeholder="账号"
            placeholder-class="input-placeholder"
            @focus="focusField = 'username'"
            @blur="focusField = null"
          />
        </view>

        <!-- 密码输入 -->
        <view class="input-cell" :class="{ 'is-focused': focusField === 'password' }">
          <view class="cell-icon">
            <AppIcon name="lock" :size="17" color="#64748b" />
          </view>
          <input
            v-model="form.password"
            :password="!showPassword"
            class="cell-input"
            aria-label="密码"
            placeholder="密码"
            placeholder-class="input-placeholder"
            confirm-type="go"
            @focus="focusField = 'password'"
            @blur="focusField = null"
            @confirm="handleLogin"
          />
          <button class="eye-toggle-btn" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">
            <AppIcon :name="showPassword ? 'eye-off' : 'eye'" :size="17" color="#52647e" />
          </button>
        </view>

        <view v-if="mfaRequired" class="input-cell">
          <input v-model="totpCode" class="uni-input" placeholder="动态验证码或恢复码" aria-label="动态验证码或恢复码" @confirm="handleLogin" />
        </view>
        <!-- 辅助操作 -->
        <view class="sub-actions-row">
          <button class="forgot-btn" @click="handleForgotPassword">忘记密码？</button>
        </view>

        <!-- 浅色珠光轻玻登录按钮 (与 PC 端统一质感) -->
        <button class="login-submit-btn" :loading="loading" :disabled="loading" @click="handleLogin">
          <text class="submit-text">登录</text>
          <text class="submit-arrow">→</text>
        </button>
      </view>
    </view>

  </view>
</template>

<script setup lang="ts">
import { clearMobileSession } from "../../utils/warm-cache";
import { computed, reactive, ref, watch } from "vue";
import { onLoad, onUnload } from "@dcloudio/uni-app";
import { apiLogin, apiGetPortalEntry, type PortalEntry } from "../../api/auth";
import AppIcon from "../../components/ui/AppIcon.vue";
import TianshuLogo from "../../components/brand/TianshuLogo.vue";

const loading = ref(false);
const showPassword = ref(false);
const mfaRequired = ref(false);
const totpCode = ref("");
const focusField = ref<"username" | "password" | null>(null);
const portal = ref<PortalEntry | null>(null);
const svgUid = "ts-mobile";

// 完美继承 PC 端明亮模式下的天体轨调色盘
const astroPalette = {
  nebulaCenter: "#38BDF8",
  nebulaMid: "#6366F1",
  nebulaEdge: "#EEF2FF",
  coreAuraCenter: "#FFFFFF",
  coreAuraMid: "#60A5FA",
  ringOuter: "rgba(79, 70, 229, 0.24)",
  ringMiddle: "rgba(37, 99, 235, 0.28)",
  ringInner: "rgba(2, 132, 199, 0.26)",
  ringInnerDash: "rgba(79, 70, 229, 0.30)",
  orbitStart: "#4F46E5",
  orbitMid: "#0284C7",
  orbitEnd: "#2563EB",
  pulsePrimary: "#0284C7",
  pulseSecondary: "#4F46E5",
  starTop: "#E0F2FE",
  starMid: "#3B82F6",
  starBottom: "#3730A3",
  starHighlightOpacity: "0.96",
  starCorePivot: "#0F172A",
  starCenterDot: "#FFFFFF",
  resonanceStroke: "#2563EB",
  sat1: "#0284C7",
  sat2: "#4F46E5",
  sat3: "#059669",
  sat4: "#2563EB",
  sat5: "#0891B2"
};

const form = reactive({
  tenantCode: "",
  clientId: "",
  username: "admin",
  password: "Admin@123456"
});

watch(() => [form.tenantCode, form.username], () => {
  mfaRequired.value = false;
  totpCode.value = "";
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

async function handleLogin() {
  if (loading.value) return;
  if (!form.username || !form.password) {
    uni.showToast({ title: "请输入用户名与密码", icon: "none" });
    return;
  }
  if (mfaRequired.value && !totpCode.value.trim()) {
    uni.showToast({ title: "请输入动态验证码或恢复码", icon: "none" });
    return;
  }
  loading.value = true;
  try {
    const payload = {
      username: form.username.trim(),
      password: form.password,
      totpCode: mfaRequired.value ? totpCode.value.trim() : undefined,
      clientId: form.clientId || undefined,
      terminalCode: "h5",
      ...(form.tenantCode.trim() ? { tenantCode: form.tenantCode.trim() } : {})
    };
    const res = await apiLogin(payload);
    clearMobileSession();
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
      uni.reLaunch({ url: "/pages/index/index" });
    }, 400);
  } catch (err: any) {
    if (err?.code === "AUTH_MFA_REQUIRED" || err?.code === "AUTH_MFA_INVALID") mfaRequired.value = true;
    uni.showToast({ title: err?.message || "登录失败，请检查账号密码", icon: "none" });
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.mobile-login-shell {
  position: relative;
  --orbit-size: min(84vw, 320px, max(64px, calc(100vh - 322px)));
  min-height: 100vh;
  min-height: 100dvh;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: center;
  padding: calc(16px + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px))) 20px
    calc(20px + env(safe-area-inset-bottom, 0px));
  background-color: #f8fafc;
  background: radial-gradient(circle at 50% 25%, #dbeafe 0%, #eef2ff 50%, #f8fafc 100%);
  overflow-x: hidden;
}

/* =============================================================================
   1. PC 端同款宇宙微光背景 (适配移动端垂直流)
   ============================================================================= */
.universe-aurora {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  z-index: 0;
}

.aurora-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
}

.orb-primary {
  top: -80px;
  left: 50%;
  transform: translateX(-50%);
  width: 380px;
  height: 380px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.22) 0%, transparent 70%);
}

.orb-secondary {
  right: -80px;
  bottom: 120px;
  width: 340px;
  height: 340px;
  background: radial-gradient(circle, rgba(14, 165, 233, 0.20) 0%, transparent 70%);
}

.orb-core {
  top: 30%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(56, 189, 248, 0.16) 0%, transparent 72%);
}

.orb-bottom {
  bottom: -40px;
  left: 10%;
  width: 360px;
  height: 280px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.15) 0%, transparent 70%);
}

.universe-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, rgba(79, 70, 229, 0.055) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(79, 70, 229, 0.055) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(ellipse 90% 80% at 50% 40%, #000 30%, transparent 100%);
  -webkit-mask-image: radial-gradient(ellipse 90% 80% at 50% 40%, #000 30%, transparent 100%);
}

.universe-stars {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(1.5px 1.5px at 15% 14%, rgba(79, 70, 229, 0.40), transparent),
    radial-gradient(1.5px 1.5px at 82% 18%, rgba(2, 132, 199, 0.42), transparent),
    radial-gradient(1.2px 1.2px at 28% 42%, rgba(99, 102, 241, 0.35), transparent),
    radial-gradient(1.5px 1.5px at 76% 78%, rgba(2, 132, 199, 0.32), transparent),
    radial-gradient(1.2px 1.2px at 18% 86%, rgba(79, 70, 229, 0.35), transparent);
}

/* =============================================================================
   2. PC 端同款全息天体仪 (尺寸适配移动端 + 精致空间层次)
   ============================================================================= */
.cosmic-astrolabe-zone {
  position: absolute;
  top: calc(24px + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px)));
  left: 50%;
  transform: translateX(-50%);
  width: var(--orbit-size);
  height: var(--orbit-size);
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  user-select: none;
  z-index: 1;
  opacity: 0.90;
}

.grand-astrolabe {
  width: 100%;
  height: 100%;
  overflow: visible;
}

/* 动效系统：与 PC 端完全一致的流畅动态 */
.astro-ring-outer {
  transform-origin: 320px 320px;
  animation: astroRotate 70s linear infinite;
}

.astro-ring-middle {
  transform-origin: 320px 320px;
  animation: astroRotateReverse 48s linear infinite;
}

.astro-radar-sweep {
  transform-origin: 320px 320px;
  animation: astroRotate 14s linear infinite;
}

.astro-pulse-track {
  animation: astroTrackFlow 8s linear infinite;
}

.astro-pulse-track-rev {
  animation: astroTrackFlowRev 7s linear infinite;
}

.astro-core-star {
  transform-origin: 320px 320px;
  animation: astroStarFloat 4s ease-in-out infinite;
}

.astro-resonance-ring {
  transform-origin: 320px 320px;
  animation: astroResonance 3.2s cubic-bezier(0.2, 0.8, 0.4, 1) infinite;
}

@keyframes astroRotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes astroRotateReverse {
  from { transform: rotate(360deg); }
  to { transform: rotate(0deg); }
}

@keyframes astroTrackFlow {
  from { stroke-dashoffset: 0; }
  to { stroke-dashoffset: -508; }
}

@keyframes astroTrackFlowRev {
  from { stroke-dashoffset: 0; }
  to { stroke-dashoffset: 476; }
}

@keyframes astroStarFloat {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}

@keyframes astroResonance {
  0% { opacity: 0.55; transform: scale(0.92); }
  100% { opacity: 0; transform: scale(1.65); }
}

.login-card {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 380px;
  margin-top: calc(var(--orbit-size) * 0.95 + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px)) + 24px);
  margin-bottom: auto;
  padding: 26px 20px 12px;
  box-sizing: border-box;
}
.login-card::before {
  content: "";
  position: absolute;
  z-index: -1;
  inset: -16px -10px;
  pointer-events: none;
  background: radial-gradient(ellipse at 50% 46%, rgba(241, 246, 255, .80), rgba(241, 246, 255, .46) 52%, transparent 76%);
}

/* 品牌头部 */
.brand-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 22px;
}

.logo-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.brand-name {
  flex: 1;
  min-width: 0;
  font-size: 19px;
  font-weight: 650;
  line-height: 1.4;
  color: #172440;
  overflow-wrap: anywhere;
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
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 50px;
  padding: 0 4px;
  box-sizing: border-box;
  border-bottom: 1px solid rgba(70, 96, 143, .34);
  transition: border-color .16s ease;
}

.input-cell.is-focused {
  border-color: #4857a8;
  box-shadow: 0 1px #4857a8;
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
  height: 48px;
  font-size: 16px;
  color: #0f172a;
  font-weight: 500;
  background: transparent;
}

.input-placeholder {
  color: #596982;
  font-size: 15px;
  font-weight: 400;
}

.eye-toggle-btn, .forgot-btn {
  margin: 0;
  padding: 0;
  min-height: 44px;
  line-height: 44px;
  border: 0;
  background: transparent;
}
.eye-toggle-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  margin-right: -8px;
}
.sub-actions-row {
  display: flex;
  justify-content: flex-end;
  margin: 2px 0 4px;
}
.forgot-btn {
  padding: 0 4px;
  font-size: 13px;
  color: #475877;
}
.forgot-btn:active { color: #373e84; }

.login-submit-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  min-height: 48px;
  box-sizing: border-box;
  border: 1px solid rgba(114, 138, 186, .38);
  border-radius: 12px;
  background: linear-gradient(120deg, rgba(255, 255, 255, .80), rgba(224, 235, 253, .78));
  color: #182842;
  box-shadow: inset 0 1px rgba(255, 255, 255, .75), 0 3px 9px rgba(67, 90, 138, .07);
}
.login-submit-btn:active { background: #dce7fa; }
.login-submit-btn[disabled] { color: #52627c; }
.eye-toggle-btn:focus-visible, .forgot-btn:focus-visible, .login-submit-btn:focus-visible {
  outline: 2px solid #4857a8;
  outline-offset: 2px;
}
.submit-text {
  font-size: 15.5px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: .05em;
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

@media (max-height: 500px) {
  .login-card { padding-top: 8px; }
  .brand-header { margin-bottom: 10px; }
}
</style>
