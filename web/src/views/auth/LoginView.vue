<script setup lang="ts">
import { computed, onMounted, reactive, ref, useId } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Lock, Monitor, Moon, Sunny, User } from "@element-plus/icons-vue";
import { fetchPortalEntry, type PortalEntry } from "@/api/modules/portal";
import {
  acceptTenantInvite,
  confirmPasswordReset,
  fetchTenantInvitePreview,
  sendPasswordResetCode,
  type TenantInviteResponse
} from "@/api/modules/auth";
import { useAuthStore } from "@/stores/modules/auth";
import { useThemeStore, type ThemeMode, type ThemePreset } from "@/stores/modules/theme";
import {
  extractSubdomainTenant,
  rememberPortalContext,
  resolvePortalContext
} from "@/utils/portal-context";
import TianshuLogo from "@/components/brand/TianshuLogo.vue";
import SmartShieldCaptcha, { type CaptchaTicket } from "@/components/auth/SmartShieldCaptcha.vue";

const form = reactive({
  tenantCode: "",
  clientId: "",
  terminalCode: "web",
  totpCode: "",
  username: "admin",
  password: "Admin@123456"
});
const loading = ref(false);
const booting = ref(true);
const portal = ref<PortalEntry | null>(null);
const mfaRequired = ref(false);
const loginError = ref("");
const shieldRef = ref<InstanceType<typeof SmartShieldCaptcha> | null>(null);
const captchaTicket = ref<CaptchaTicket | null>(null);

// 自助找回密码 (OTP) 状态
const resetDialogVisible = ref(false);
const resetSendingOtp = ref(false);
const resetSubmitting = ref(false);
const resetSandboxHint = ref("");
const resetForm = reactive({
  username: "",
  tenantCode: "",
  contact: "",
  otpCode: "",
  newPassword: ""
});

// 租户成员邀请链接入驻 (?inviteToken=...)
const inviteDialogVisible = ref(false);
const inviteSubmitting = ref(false);
const invitePreview = ref<TenantInviteResponse | null>(null);
const inviteTokenValue = ref("");
const inviteForm = reactive({
  username: "",
  nickname: "",
  email: "",
  mobile: "",
  password: ""
});

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const themeStore = useThemeStore();
const svgUid = useId();

interface PresetPalette {
  value: ThemePreset;
  label: string;
  color: string;
  rgb: string;
  btnMid: string;
  secondary: string;
  secondaryRgb: string;
}

const presetOptions: PresetPalette[] = [
  {
    value: "obsidian",
    label: "曜石极光",
    color: "#6366f1",
    rgb: "99, 102, 241",
    btnMid: "#4338ca",
    secondary: "#0ea5e9",
    secondaryRgb: "14, 165, 233"
  },
  {
    value: "emerald",
    label: "松石森境",
    color: "#14b8a6",
    rgb: "20, 184, 166",
    btnMid: "#0f766e",
    secondary: "#0284c7",
    secondaryRgb: "2, 132, 199"
  },
  {
    value: "titanium",
    label: "钛金琥珀",
    color: "#f59e0b",
    rgb: "245, 158, 11",
    btnMid: "#d97706",
    secondary: "#ea580c",
    secondaryRgb: "234, 88, 12"
  },
  {
    value: "cobalt",
    label: "极光青蓝",
    color: "#3b82f6",
    rgb: "59, 130, 246",
    btnMid: "#1d4ed8",
    secondary: "#06b6d4",
    secondaryRgb: "6, 182, 212"
  }
];

const modeOptions: Array<{ value: ThemeMode; label: string; icon: typeof Sunny }> = [
  { value: "light", label: "亮色", icon: Sunny },
  { value: "dark", label: "暗色", icon: Moon },
  { value: "system", label: "跟随系统", icon: Monitor }
];

const captchaEnabled = computed(() => Boolean(portal.value && portal.value.captcha.mode !== "NONE"));

const activePreset = computed(
  () => presetOptions.find((item) => item.value === themeStore.preset) ?? presetOptions[0]
);

const universeStyle = computed(() => ({
  "--portal-glow": activePreset.value.color,
  "--portal-glow-rgb": activePreset.value.rgb,
  "--portal-btn-mid": activePreset.value.btnMid,
  "--portal-secondary": activePreset.value.secondary,
  "--portal-secondary-rgb": activePreset.value.secondaryRgb
}));

// 双色系动态星轨 SVG 调色板：亮色版高对比度蓝靛晶钻，暗色版深空冰青极光
const astroPalette = computed(() =>
  themeStore.isDark
    ? {
        nebulaCenter: "#38BDF8",
        nebulaMid: "#6366F1",
        nebulaEdge: "#030614",
        coreAuraCenter: "#E0F2FE",
        coreAuraMid: "#38BDF8",
        ringOuter: "rgba(148, 163, 184, 0.24)",
        ringMiddle: "rgba(129, 140, 248, 0.28)",
        ringInner: "rgba(56, 189, 248, 0.24)",
        ringInnerDash: "rgba(165, 180, 252, 0.30)",
        orbitStart: "#818CF8",
        orbitMid: "#38BDF8",
        orbitEnd: "#6366F1",
        pulsePrimary: "#7DD3FC",
        pulseSecondary: "#A5B4FC",
        starTop: "#FFFFFF",
        starMid: "#BAE6FD",
        starBottom: "#6366F1",
        starHighlightOpacity: "0.92",
        starCorePivot: "#060A17",
        starCenterDot: "#FFFFFF",
        resonanceStroke: "#38BDF8",
        sat1: "#38BDF8",
        sat2: "#C7D2FE",
        sat3: "#6EE7B7",
        sat4: "#7DD3FC",
        sat5: "#67E8F9"
      }
    : {
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
      }
);

const displayPortalTitle = computed(() => {
  const title = portal.value?.portalTitle?.trim();
  if (
    !title ||
    form.tenantCode === "platform" ||
    title === "SaaS 基础底座控制台" ||
    title.includes("Tianshu PaaS")
  ) {
    return "天枢协同云平台";
  }
  return title;
});

const customLogoUrl = computed(() => {
  const url = portal.value?.logoUrl?.trim();
  if (!url || url === "/logo.svg" || url === "/favicon.svg") {
    return "";
  }
  return url;
});

function getAxiosErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === "object") {
    const respMsg = (error as { response?: { data?: { message?: unknown } } }).response?.data?.message;
    if (typeof respMsg === "string" && respMsg) return respMsg;
    const msg = (error as { message?: unknown }).message;
    if (typeof msg === "string" && msg) return msg;
  }
  return fallback;
}

function getAxiosErrorCode(error: unknown): string | undefined {
  if (error && typeof error === "object") {
    const code = (error as { response?: { data?: { code?: unknown } } }).response?.data?.code;
    return typeof code === "string" ? code : undefined;
  }
  return undefined;
}

function onShieldVerified(ticket: CaptchaTicket) {
  captchaTicket.value = ticket;
}

function onShieldReset() {
  captchaTicket.value = null;
}

async function bootstrapPortal() {
  booting.value = true;
  try {
    const context = resolvePortalContext(route.query);
    const entry = await fetchPortalEntry(context);
    portal.value = entry;
    form.clientId = entry.clientId;
    form.terminalCode = entry.terminal.terminalCode;
    form.tenantCode = entry.tenantCode;
    document.title = `${displayPortalTitle.value} | 登录`;
    loginError.value = "";
    rememberPortalContext({
      clientId: entry.clientId,
      terminalCode: entry.terminal.terminalCode,
      tenantCode: entry.tenantCode
    });
    const subdomain = extractSubdomainTenant();
    const currentQuery: Record<string, string | string[] | null | undefined> = {
      ...(route.query as Record<string, string | string[] | null | undefined>)
    };
    let needsUpdate = false;
    if (!subdomain && entry.tenantCode && currentQuery.tenantCode !== entry.tenantCode) {
      currentQuery.tenantCode = entry.tenantCode;
      delete currentQuery.tenant;
      needsUpdate = true;
    }
    if (currentQuery.clientId !== entry.clientId) {
      currentQuery.clientId = entry.clientId;
      needsUpdate = true;
    }
    if (currentQuery.terminalCode !== entry.terminal.terminalCode) {
      currentQuery.terminalCode = entry.terminal.terminalCode;
      needsUpdate = true;
    }
    if (needsUpdate) {
      await router.replace({
        path: route.path,
        query: currentQuery as Record<string, string>
      });
    }
  } catch (error: unknown) {
    loginError.value = getAxiosErrorMessage(error, "门户入口配置加载失败");
    ElMessage.error(loginError.value);
  } finally {
    booting.value = false;
  }
}

function validateForm() {
  if (!form.username.trim()) {
    loginError.value = "请输入用户名";
    return false;
  }
  if (!form.password.trim()) {
    loginError.value = "请输入密码";
    return false;
  }
  if (mfaRequired.value && !form.totpCode.trim()) {
    loginError.value = "请输入 6 位 MFA 动态验证码或恢复码";
    return false;
  }
  return true;
}

async function handleLogin() {
  loginError.value = "";
  if (!validateForm()) {
    ElMessage.error(loginError.value);
    return;
  }

  let activeTicket = captchaTicket.value;
  if (captchaEnabled.value && !activeTicket) {
    activeTicket = (await shieldRef.value?.ensureVerified()) ?? null;
    if (!activeTicket) {
      return;
    }
    captchaTicket.value = activeTicket;
  }

  loading.value = true;
  try {
    await authStore.login({
      tenantCode: form.tenantCode.trim() || undefined,
      clientId: form.clientId,
      terminalCode: form.terminalCode,
      captchaId: captchaEnabled.value && activeTicket ? activeTicket.captchaId : undefined,
      captchaCode: captchaEnabled.value && activeTicket ? activeTicket.passToken : undefined,
      totpCode: mfaRequired.value ? form.totpCode.trim() : undefined,
      username: form.username.trim(),
      password: form.password.trim()
    });
    const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/";
    void router.push(redirect);
  } catch (error: unknown) {
    const errCode = getAxiosErrorCode(error);
    const errMsg = getAxiosErrorMessage(error, "登录失败，请检查账号或密码");
    if (errCode === "AUTH_MFA_REQUIRED") {
      mfaRequired.value = true;
      loginError.value = errMsg;
      ElMessage.warning(errMsg);
    } else {
      loginError.value = errMsg;
      ElMessage.error(errMsg);
    }
    captchaTicket.value = null;
    if (captchaEnabled.value) {
      shieldRef.value?.resetShield(true);
    }
  } finally {
    loading.value = false;
  }
}

async function handleSsoLogin(channel: string) {
  loading.value = true;
  loginError.value = "";
  try {
    const channelName =
      channel === "WECOM"
        ? "企业微信"
        : channel === "DINGTALK"
          ? "钉钉"
          : channel === "FEISHU"
            ? "飞书"
            : "SAML/OIDC 身份联合";
    ElMessage.info(`正在通过【${channelName}】进行企业 SSO 单点登录...`);
    await authStore.loginWithSso({
      channel,
      authCode: `sso_${channel.toLowerCase()}_mock_${Date.now()}`,
      tenantCode: form.tenantCode.trim() || "platform"
    });
    ElMessage.success(`${channelName} SSO 鉴权成功，正在进入工作台...`);
    const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/";
    void router.push(redirect);
  } catch (err: unknown) {
    loginError.value = getAxiosErrorMessage(err, "SSO 登录失败");
    ElMessage.error(loginError.value);
  } finally {
    loading.value = false;
  }
}

async function handleSendResetOtp() {
  if (!resetForm.username.trim() || !resetForm.contact.trim()) {
    ElMessage.warning("请先填写用户名和绑定邮箱或手机号");
    return;
  }
  resetSendingOtp.value = true;
  resetSandboxHint.value = "";
  try {
    const res = await sendPasswordResetCode({
      username: resetForm.username.trim(),
      tenantCode: resetForm.tenantCode.trim() || form.tenantCode.trim() || undefined,
      contact: resetForm.contact.trim()
    });
    if (res.sandboxOtpCode) {
      resetForm.otpCode = res.sandboxOtpCode;
      resetSandboxHint.value = `沙箱模式已自动回填验证码: ${res.sandboxOtpCode}`;
    }
    ElMessage.success(`验证码已通过 ${res.deliveryChannel} 发送，${res.expireSeconds} 秒内有效`);
  } catch (err: unknown) {
    ElMessage.error(getAxiosErrorMessage(err, "发送验证码失败"));
  } finally {
    resetSendingOtp.value = false;
  }
}

async function handleConfirmResetPassword() {
  if (!resetForm.username.trim() || !resetForm.otpCode.trim() || !resetForm.newPassword.trim()) {
    ElMessage.warning("请填写完整用户名、验证码与新密码");
    return;
  }
  resetSubmitting.value = true;
  try {
    await confirmPasswordReset({
      username: resetForm.username.trim(),
      tenantCode: resetForm.tenantCode.trim() || form.tenantCode.trim() || undefined,
      otpCode: resetForm.otpCode.trim(),
      newPassword: resetForm.newPassword
    });
    ElMessage.success("密码已重置成功，请使用新密码登录");
    form.username = resetForm.username.trim();
    form.password = "";
    resetDialogVisible.value = false;
  } catch (err: unknown) {
    ElMessage.error(getAxiosErrorMessage(err, "重置密码失败"));
  } finally {
    resetSubmitting.value = false;
  }
}

function openResetDialog() {
  resetForm.username = form.username.trim();
  resetForm.tenantCode = form.tenantCode.trim();
  resetForm.otpCode = "";
  resetForm.newPassword = "";
  resetSandboxHint.value = "";
  resetDialogVisible.value = true;
}

async function checkInviteToken() {
  const rawToken = typeof route.query.inviteToken === "string" ? route.query.inviteToken.trim() : "";
  if (!rawToken) return;
  try {
    const preview = await fetchTenantInvitePreview(rawToken);
    inviteTokenValue.value = rawToken;
    invitePreview.value = preview;
    inviteForm.email = preview.invitedEmail || "";
    inviteDialogVisible.value = true;
  } catch (err: unknown) {
    ElMessage.warning(getAxiosErrorMessage(err, "邀请链接已失效或已过期"));
  }
}

async function handleAcceptInvite() {
  if (!inviteForm.username.trim() || !inviteForm.nickname.trim() || !inviteForm.password) {
    ElMessage.warning("请填写完整的账号名、显示姓名与登录密码");
    return;
  }
  inviteSubmitting.value = true;
  try {
    const accepted = await acceptTenantInvite(inviteTokenValue.value, {
      username: inviteForm.username.trim(),
      nickname: inviteForm.nickname.trim(),
      password: inviteForm.password,
      email: inviteForm.email.trim() || undefined,
      mobile: inviteForm.mobile.trim() || undefined
    });
    ElMessage.success(`已成功加入「${accepted.tenantName}」，请立即登录`);
    form.tenantCode = accepted.tenantCode;
    form.username = inviteForm.username.trim();
    form.password = "";
    inviteDialogVisible.value = false;
  } catch (err: unknown) {
    ElMessage.error(getAxiosErrorMessage(err, "邀请入驻提交失败"));
  } finally {
    inviteSubmitting.value = false;
  }
}

onMounted(() => {
  void bootstrapPortal();
  void checkInviteToken();
});
</script>

<template>
  <div class="login-universe" :class="{ 'is-dark': themeStore.isDark }" :style="universeStyle">
    <!-- 全屏空间背景（支持亮色晨曦星轨版与暗夜深空版） -->
    <div class="universe-aurora" aria-hidden="true">
      <div class="aurora-orb orb-primary" />
      <div class="aurora-orb orb-secondary" />
      <div class="aurora-orb orb-core" />
      <div class="universe-grid" />
      <div class="universe-stars" />
    </div>

    <!-- 右上角悬浮外观控制条：亮色 / 暗色 / 跟随系统 + 四套光效光谱点 -->
    <div class="floating-controls">
      <div class="mode-switcher" role="group" aria-label="外观模式切换">
        <button
          v-for="m in modeOptions"
          :key="m.value"
          type="button"
          class="mode-pill-btn"
          :class="{ 'is-active': themeStore.mode === m.value }"
          :title="`切换为${m.label}模式`"
          @click="themeStore.setMode(m.value)"
        >
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.label }}</span>
        </button>
      </div>

      <span class="controls-divider" aria-hidden="true" />

      <div class="spectrum-group" title="切换光效主题">
        <button
          v-for="item in presetOptions"
          :key="item.value"
          type="button"
          class="spectrum-dot-btn"
          :class="{ 'is-active': themeStore.preset === item.value }"
          :title="item.label"
          :aria-label="`切换主题为${item.label}`"
          @click="themeStore.setPreset(item.value)"
        >
          <span class="dot-core" :style="{ backgroundColor: item.color }" />
        </button>
      </div>
    </div>

    <!-- 左右分域空间舞台：左侧纯净大型星轨动效 · 右侧极简琉璃登录舱 -->
    <main class="universe-layout">
      <!-- 左侧：放大全屏动态星轨（无文字、无边框） -->
      <section class="cosmic-astrolabe-zone" aria-hidden="true">
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
      </section>

      <!-- 右侧：极简自适应琉璃登录舱（亮色晶透白 / 暗色曜石黑） -->
      <section class="auth-prism-card" aria-label="账号登录">
        <div class="prism-top-beam" aria-hidden="true" />

        <header class="card-brand">
          <img v-if="customLogoUrl" :src="customLogoUrl" alt="品牌标识" class="custom-portal-logo" />
          <TianshuLogo v-else :size="42" :theme="themeStore.isDark ? 'dark' : 'light'" animated />
          <span class="card-brand__name">{{ displayPortalTitle }}</span>
        </header>

        <el-skeleton :loading="booting" animated>
          <template #template>
            <el-skeleton-item variant="rect" style="height: 290px; border-radius: 14px" />
          </template>

          <el-form class="prism-form" @submit.prevent="handleLogin">
            <el-alert
              v-if="loginError"
              class="auth-error-alert"
              type="error"
              :closable="false"
              :title="loginError"
            />

            <el-form-item>
              <el-input
                v-model="form.username"
                size="large"
                placeholder="用户名 / 手机号 / 邮箱"
                autocomplete="username"
                :prefix-icon="User"
                @keyup.enter="handleLogin"
              />
            </el-form-item>

            <el-form-item>
              <el-input
                v-model="form.password"
                size="large"
                type="password"
                show-password
                placeholder="登录密码"
                autocomplete="current-password"
                :prefix-icon="Lock"
                @keyup.enter="handleLogin"
              />
            </el-form-item>

            <!-- 天枢智盾：低风险一键无感核验 / 高风险自动弹滑动拼图 -->
            <el-form-item v-if="captchaEnabled">
              <SmartShieldCaptcha
                ref="shieldRef"
                :dark="themeStore.isDark"
                :mode-hint="portal?.captcha.mode || 'SMART'"
                @verified="onShieldVerified"
                @reset="onShieldReset"
              />
            </el-form-item>

            <el-form-item v-if="mfaRequired">
              <el-input
                v-model="form.totpCode"
                size="large"
                placeholder="6 位 MFA 动态码 / 恢复码"
                maxlength="16"
                :prefix-icon="Lock"
                @keyup.enter="handleLogin"
              />
            </el-form-item>

            <div class="auth-meta-row">
              <button type="button" class="meta-link-btn" @click="openResetDialog">忘记密码？</button>
            </div>

            <button type="submit" class="cyber-submit-btn" :disabled="loading">
              <span class="btn-sheen" aria-hidden="true" />
              <span class="btn-label">{{ loading ? "验证中..." : "登 录" }}</span>
              <svg viewBox="0 0 20 20" fill="none" class="btn-arrow" aria-hidden="true">
                <path
                  d="M4.5 10H15.5M15.5 10L11 5.5M15.5 10L11 14.5"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </button>
          </el-form>

          <div class="sso-icon-row">
            <div class="sso-hairline" aria-hidden="true" />
            <div class="sso-icons">
              <button type="button" class="sso-icon-btn" title="企业微信" aria-label="企业微信登录" @click="handleSsoLogin('WECOM')">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
                  <path d="M4 11c0-3.9 3.6-7 8-7s8 3.1 8 7-3.6 7-8 7c-1.2 0-2.4-.2-3.4-.7L4 19l1-3.3C4.4 14.3 4 12.7 4 11z" />
                </svg>
              </button>
              <button type="button" class="sso-icon-btn" title="钉钉" aria-label="钉钉登录" @click="handleSsoLogin('DINGTALK')">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
                  <path d="M13 3L5 14h6l-1 7 9-11h-6l0-7z" />
                </svg>
              </button>
              <button type="button" class="sso-icon-btn" title="飞书" aria-label="飞书登录" @click="handleSsoLogin('FEISHU')">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
                  <path d="M4 17L19 5l-4 14-4.5-4.5L4 17z" />
                </svg>
              </button>
              <button type="button" class="sso-icon-btn" title="OIDC / SAML" aria-label="OIDC / SAML 登录" @click="handleSsoLogin('SAML_OIDC')">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
                  <circle cx="8" cy="12" r="3" />
                  <path d="M11 12h9M17 12v3M20 12v2" />
                </svg>
              </button>
            </div>
          </div>
        </el-skeleton>
      </section>
    </main>

    <!-- 自助找回密码对话框 -->
    <el-dialog v-model="resetDialogVisible" title="自助找回并重置密码" width="440px" append-to-body>
      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="resetForm.username" placeholder="请输入待重置的账号用户名" />
        </el-form-item>
        <el-form-item label="绑定邮箱或手机号">
          <div class="reset-contact-row">
            <el-input v-model="resetForm.contact" placeholder="输入绑定邮箱或手机号" />
            <el-button :loading="resetSendingOtp" @click="handleSendResetOtp">发送验证码</el-button>
          </div>
        </el-form-item>
        <el-alert v-if="resetSandboxHint" type="info" :closable="false" :title="resetSandboxHint" style="margin-bottom: 12px" />
        <el-form-item label="6 位数字验证码">
          <el-input v-model="resetForm.otpCode" maxlength="6" placeholder="请输入 6 位 OTP 验证码" />
        </el-form-item>
        <el-form-item label="设置新密码">
          <el-input v-model="resetForm.newPassword" type="password" show-password placeholder="至少 8 位且符合安全基线" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetSubmitting" @click="handleConfirmResetPassword">确认重置密码</el-button>
      </template>
    </el-dialog>

    <!-- 租户成员邀请链接入驻对话框 -->
    <el-dialog v-model="inviteDialogVisible" title="受邀加入企业租户" width="460px" append-to-body>
      <el-alert
        v-if="invitePreview"
        type="success"
        :closable="false"
        :title="`邀请空间：${invitePreview.tenantName} (${invitePreview.tenantCode})`"
        style="margin-bottom: 14px"
      />
      <el-form label-position="top">
        <el-form-item label="登录账号">
          <el-input v-model="inviteForm.username" placeholder="设置英文/数字登录账号名" />
        </el-form-item>
        <el-form-item label="显示姓名">
          <el-input v-model="inviteForm.nickname" placeholder="请输入您的真实姓名或昵称" />
        </el-form-item>
        <el-form-item label="联系邮箱 (可选)">
          <el-input v-model="inviteForm.email" placeholder="name@company.com" />
        </el-form-item>
        <el-form-item label="设置初始密码">
          <el-input v-model="inviteForm.password" type="password" show-password placeholder="请输入登录密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inviteDialogVisible = false">稍后注册</el-button>
        <el-button type="primary" :loading="inviteSubmitting" @click="handleAcceptInvite">完成激活入驻</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.login-universe {
  --portal-glow: #6366f1;
  --portal-glow-rgb: 99, 102, 241;
  --portal-btn-mid: #4338ca;
  --portal-secondary: #0ea5e9;
  --portal-secondary-rgb: 14, 165, 233;

  /* 默认亮色晨曦星轨版：清透浅色表单控件令牌 */
  --sb-control-bg: rgba(248, 250, 252, 0.94);
  --sb-control-bg-hover: #ffffff;
  --sb-border-color: rgba(148, 163, 184, 0.36);
  --sb-border-strong: rgba(var(--portal-glow-rgb), 0.72);
  --sb-primary-color: var(--portal-glow);
  --sb-text-primary: #0f172a;
  --sb-text-secondary: #0f172a;
  --sb-text-muted: #64748b;
  --el-input-text-color: #0f172a;
  --el-text-color-regular: #0f172a;
  --el-text-color-placeholder: #64748b;

  position: relative;
  min-height: 100vh;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px clamp(20px, 5vw, 72px);
  overflow-x: hidden;
  background: radial-gradient(circle at 28% 45%, #dbeafe 0%, #eef2ff 50%, #f8fafc 100%);
  color: #0f172a;
  transition: background 120ms ease, color 120ms ease;

  &.is-dark {
    --sb-control-bg: rgba(17, 25, 48, 0.88);
    --sb-control-bg-hover: rgba(22, 33, 62, 0.95);
    --sb-border-color: rgba(148, 163, 184, 0.34);
    --sb-text-primary: #ffffff;
    --sb-text-secondary: #ffffff;
    --sb-text-muted: #cbd5e1;
    --el-input-text-color: #ffffff;
    --el-text-color-regular: #ffffff;
    --el-text-color-placeholder: rgba(148, 163, 184, 0.78);

    background: radial-gradient(circle at 28% 45%, #0a1535 0%, #060b1e 48%, #030614 100%);
    color: #f8fafc;
  }
}

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
  filter: blur(96px);
  transition: background 280ms ease;
}

.orb-primary {
  top: -140px;
  left: 6%;
  width: 680px;
  height: 680px;
  background: radial-gradient(circle, rgba(var(--portal-glow-rgb), 0.24) 0%, transparent 70%);
}

.orb-secondary {
  right: -100px;
  bottom: -160px;
  width: 620px;
  height: 620px;
  background: radial-gradient(circle, rgba(var(--portal-secondary-rgb), 0.20) 0%, transparent 70%);
}

.orb-core {
  top: 50%;
  left: 34%;
  width: 540px;
  height: 540px;
  transform: translate(-50%, -50%);
  background: radial-gradient(circle, rgba(var(--portal-secondary-rgb), 0.14) 0%, transparent 72%);
}

.is-dark {
  .orb-primary {
    background: radial-gradient(circle, rgba(var(--portal-glow-rgb), 0.34) 0%, transparent 70%);
  }

  .orb-secondary {
    background: radial-gradient(circle, rgba(var(--portal-secondary-rgb), 0.24) 0%, transparent 70%);
  }

  .orb-core {
    background: radial-gradient(circle, rgba(var(--portal-secondary-rgb), 0.16) 0%, transparent 72%);
  }
}

.universe-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, rgba(79, 70, 229, 0.065) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(79, 70, 229, 0.065) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse 88% 82% at 38% 50%, #000 30%, transparent 100%);
}

.is-dark .universe-grid {
  background-image:
    linear-gradient(to right, rgba(148, 163, 184, 0.06) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(148, 163, 184, 0.06) 1px, transparent 1px);
}

.universe-stars {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(1.5px 1.5px at 12% 18%, rgba(79, 70, 229, 0.42), transparent),
    radial-gradient(1.5px 1.5px at 26% 74%, rgba(2, 132, 199, 0.45), transparent),
    radial-gradient(1.5px 1.5px at 44% 12%, rgba(99, 102, 241, 0.36), transparent),
    radial-gradient(1.5px 1.5px at 62% 84%, rgba(2, 132, 199, 0.34), transparent),
    radial-gradient(1.5px 1.5px at 84% 24%, rgba(79, 70, 229, 0.36), transparent);
}

.is-dark .universe-stars {
  background-image:
    radial-gradient(1.5px 1.5px at 12% 18%, rgba(255, 255, 255, 0.45), transparent),
    radial-gradient(1.5px 1.5px at 26% 74%, rgba(56, 189, 248, 0.48), transparent),
    radial-gradient(1px 1px at 44% 12%, rgba(165, 180, 252, 0.42), transparent),
    radial-gradient(1.5px 1.5px at 62% 84%, rgba(255, 255, 255, 0.36), transparent),
    radial-gradient(1.5px 1.5px at 84% 24%, rgba(56, 189, 248, 0.40), transparent),
    radial-gradient(1px 1px at 90% 68%, rgba(165, 180, 252, 0.35), transparent);
}

/* 右上角外观切换栏：亮色 / 暗色 / 跟随系统 + 光谱点 */
.floating-controls {
  position: fixed;
  top: 20px;
  right: 24px;
  z-index: 10;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 4px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.80);
  border: 1px solid rgba(203, 213, 225, 0.78);
  backdrop-filter: blur(16px);
  box-shadow: 0 8px 24px -8px rgba(15, 23, 42, 0.12);
}

.is-dark .floating-controls {
  background: rgba(15, 23, 42, 0.72);
  border-color: rgba(148, 163, 184, 0.24);
  box-shadow: 0 10px 26px -8px rgba(0, 0, 0, 0.45);
}

.mode-switcher {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.mode-pill-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 9px;
  border: 0;
  border-radius: 9999px;
  background: transparent;
  color: #475569;
  font-size: 11.5px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: background 150ms ease, color 150ms ease, box-shadow 150ms ease;

  &:hover {
    color: #0f172a;
  }

  &.is-active {
    background: #ffffff;
    color: var(--portal-glow);
    font-weight: 600;
    box-shadow: 0 1px 4px rgba(15, 23, 42, 0.12);
  }
}

.is-dark .mode-pill-btn {
  color: #94a3b8;

  &:hover {
    color: #f8fafc;
  }

  &.is-active {
    background: rgba(var(--portal-glow-rgb), 0.28);
    color: #ffffff;
    box-shadow: inset 0 0 0 1px rgba(var(--portal-glow-rgb), 0.45);
  }
}

.controls-divider {
  width: 1px;
  height: 14px;
  background: rgba(148, 163, 184, 0.35);
}

.spectrum-group {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.spectrum-dot-btn {
  width: 18px;
  height: 18px;
  padding: 0;
  border-radius: 50%;
  border: 1.5px solid transparent;
  background: transparent;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 160ms ease, transform 160ms ease;

  &:hover {
    transform: scale(1.1);
  }

  &.is-active {
    border-color: var(--portal-glow);
  }
}

.is-dark .spectrum-dot-btn.is-active {
  border-color: #ffffff;
}

.dot-core {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: block;
}

/* 左右分域舞台 */
.universe-layout {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 1240px;
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) 396px;
  gap: clamp(32px, 5vw, 88px);
  align-items: center;
}

/* 左侧大型全息星轨动效 */
.cosmic-astrolabe-zone {
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  user-select: none;
}

.grand-astrolabe {
  display: block;
  width: min(100%, 640px);
  height: auto;
  overflow: visible;
}

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
  animation: astroRotate 12s linear infinite;
}

.astro-pulse-track {
  animation: astroTrackFlow 4.8s linear infinite;
}

.astro-pulse-track-rev {
  animation: astroTrackFlowRev 5.6s linear infinite;
}

.astro-core-star {
  transform-origin: 320px 320px;
  animation: astroStarFloat 5.2s ease-in-out infinite;
}

.astro-resonance-ring {
  transform-origin: 320px 320px;
  animation: astroResonance 3.8s ease-out infinite;
}

.astro-satellites {
  animation: astroSatellitePulse 3.6s ease-in-out infinite;
}

/* 右侧极简自适应琉璃登录舱 */
.auth-prism-card {
  position: relative;
  width: 100%;
  padding: 36px 32px 28px;
  border-radius: 26px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(28px) saturate(190%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.96),
    0 28px 68px -18px rgba(30, 41, 59, 0.16);
  overflow: hidden;
  transition: background 120ms ease, border-color 120ms ease, box-shadow 120ms ease;
}

.is-dark .auth-prism-card {
  background: rgba(10, 16, 34, 0.78);
  border-color: rgba(148, 163, 184, 0.22);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.16),
    0 30px 72px -18px rgba(0, 0, 0, 0.68);
}

.prism-top-beam {
  position: absolute;
  top: 0;
  left: 32px;
  right: 32px;
  height: 2px;
  border-radius: 9999px;
  background: linear-gradient(90deg, transparent, var(--portal-glow), var(--portal-secondary), transparent);
  opacity: 0.92;
}

.card-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 26px;
}

.custom-portal-logo {
  width: 42px;
  height: 42px;
  border-radius: 11px;
  object-fit: cover;
  flex-shrink: 0;
}

.card-brand__name {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.03em;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.is-dark .card-brand__name {
  color: #f8fafc;
}

.login-universe .prism-form {
  :deep(.el-form-item) {
    margin-bottom: 15px;
  }

  :deep(.el-input__wrapper) {
    height: 46px;
    padding: 0 14px;
    border-radius: 12px !important;
    background: rgba(248, 250, 252, 0.95) !important;
    box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.36) inset !important;
    transition: box-shadow 180ms ease, background 180ms ease;

    &:hover {
      background: #ffffff !important;
      box-shadow: 0 0 0 1px rgba(var(--portal-glow-rgb), 0.56) inset !important;
    }

    &.is-focus,
    &:focus-within {
      background: #ffffff !important;
      box-shadow:
        0 0 0 1.5px var(--portal-glow) inset,
        0 0 22px -6px rgba(var(--portal-glow-rgb), 0.38) !important;
    }
  }

  :deep(.el-input__inner) {
    color: #0f172a !important;
    font-size: 14px;
    font-weight: 500;
    caret-color: var(--portal-glow);

    &::placeholder {
      color: #64748b !important;
      font-weight: 400;
    }

    &:-webkit-autofill {
      -webkit-text-fill-color: #0f172a !important;
      -webkit-box-shadow: 0 0 0 1000px #f8fafc inset !important;
      transition: background-color 5000s ease-in-out 0s;
    }
  }

  :deep(.el-input__prefix),
  :deep(.el-input__suffix),
  :deep(.el-input__icon) {
    color: #64748b !important;
  }
}

.login-universe.is-dark .prism-form {
  :deep(.el-input__wrapper) {
    background: rgba(17, 25, 48, 0.88) !important;
    box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.34) inset !important;

    &:hover {
      background: rgba(22, 33, 62, 0.95) !important;
      box-shadow: 0 0 0 1px rgba(var(--portal-glow-rgb), 0.68) inset !important;
    }

    &.is-focus,
    &:focus-within {
      background: rgba(24, 36, 68, 0.98) !important;
      box-shadow:
        0 0 0 1.5px var(--portal-glow) inset,
        0 0 22px -5px rgba(var(--portal-glow-rgb), 0.58) !important;
    }
  }

  :deep(.el-input__inner) {
    color: #ffffff !important;
    caret-color: var(--portal-secondary);

    &::placeholder {
      color: rgba(148, 163, 184, 0.78) !important;
    }

    &:-webkit-autofill {
      -webkit-text-fill-color: #ffffff !important;
      -webkit-box-shadow: 0 0 0 1000px #111930 inset !important;
    }
  }

  :deep(.el-input__prefix),
  :deep(.el-input__suffix),
  :deep(.el-input__icon) {
    color: rgba(203, 213, 225, 0.85) !important;
  }
}

.auth-error-alert {
  margin-bottom: 14px;
  border-radius: 10px;
}

.auth-meta-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin: -4px 2px 14px;
}

.meta-link-btn {
  padding: 0;
  border: 0;
  background: transparent;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--portal-glow);
  cursor: pointer;
  transition: color 150ms ease;

  &:hover {
    opacity: 0.82;
  }
}

.is-dark .meta-link-btn {
  color: #7dd3fc;

  &:hover {
    color: #ffffff;
    opacity: 1;
  }
}

/* 主登录行动按钮 */
.cyber-submit-btn {
  position: relative;
  width: 100%;
  height: 48px;
  padding: 0 18px;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 12px;
  background: linear-gradient(110deg, var(--portal-glow) 0%, var(--portal-btn-mid) 52%, var(--portal-secondary) 100%);
  background-size: 160% 100%;
  color: #ffffff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.26);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  overflow: hidden;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.34),
    0 14px 30px -8px rgba(var(--portal-glow-rgb), 0.52);
  transition: transform 180ms ease, box-shadow 180ms ease, background-position 260ms ease, opacity 180ms ease;

  &:hover:not(:disabled) {
    background-position: 80% 0;
    transform: translateY(-1px);
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.42),
      0 18px 36px -8px rgba(var(--portal-glow-rgb), 0.65);
  }

  &:hover:not(:disabled) .btn-arrow {
    transform: translateX(3px);
  }

  &:hover:not(:disabled) .btn-sheen {
    transform: translateX(120%);
  }

  &:disabled {
    opacity: 0.68;
    cursor: not-allowed;
  }
}

.btn-sheen {
  position: absolute;
  inset: 0;
  background: linear-gradient(110deg, transparent 20%, rgba(255, 255, 255, 0.28) 48%, transparent 72%);
  transform: translateX(-110%);
  transition: transform 520ms cubic-bezier(0.22, 1, 0.36, 1);
  pointer-events: none;
}

.btn-arrow {
  width: 17px;
  height: 17px;
  transition: transform 180ms ease;
}

/* 极简纯图标 SSO 行 */
.sso-icon-row {
  margin-top: 22px;
}

.sso-hairline {
  height: 1px;
  width: 100%;
  background: linear-gradient(90deg, transparent, rgba(148, 163, 184, 0.32), transparent);
  margin-bottom: 16px;
}

.sso-icons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
}

.sso-icon-btn {
  width: 40px;
  height: 40px;
  padding: 0;
  border-radius: 50%;
  border: 1px solid rgba(148, 163, 184, 0.32);
  background: rgba(248, 250, 252, 0.88);
  color: #334155;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 160ms ease, background 160ms ease, color 160ms ease, transform 160ms ease;

  svg {
    width: 17px;
    height: 17px;
  }

  &:hover {
    border-color: var(--portal-glow);
    background: rgba(var(--portal-glow-rgb), 0.12);
    color: var(--portal-glow);
    transform: translateY(-1px);
  }
}

.is-dark .sso-icon-btn {
  border-color: rgba(148, 163, 184, 0.32);
  background: rgba(15, 23, 42, 0.62);
  color: rgba(226, 232, 240, 0.92);

  &:hover {
    border-color: var(--portal-secondary);
    background: rgba(var(--portal-glow-rgb), 0.22);
    color: #ffffff;
  }
}

.reset-contact-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

@keyframes astroRotate {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

@keyframes astroRotateReverse {
  from {
    transform: rotate(360deg);
  }

  to {
    transform: rotate(0deg);
  }
}

@keyframes astroTrackFlow {
  from {
    stroke-dashoffset: 0;
  }

  to {
    stroke-dashoffset: -508;
  }
}

@keyframes astroTrackFlowRev {
  from {
    stroke-dashoffset: 0;
  }

  to {
    stroke-dashoffset: 476;
  }
}

@keyframes astroStarFloat {
  0%,
  100% {
    transform: scale(1);
  }

  50% {
    transform: scale(1.045);
  }
}

@keyframes astroResonance {
  0% {
    opacity: 0.55;
    transform: scale(0.92);
  }

  100% {
    opacity: 0;
    transform: scale(1.65);
  }
}

@keyframes astroSatellitePulse {
  0%,
  100% {
    opacity: 0.72;
  }

  50% {
    opacity: 1;
  }
}

@media (max-width: 960px) {
  .universe-layout {
    grid-template-columns: minmax(0, 1fr);
    max-width: 410px;
  }

  .cosmic-astrolabe-zone {
    display: none;
  }
}

@media (max-width: 640px) {
  .login-universe {
    padding: 68px 16px 24px;
  }

  .floating-controls {
    top: 14px;
    right: 12px;
    gap: 6px;
    padding: 3px 6px 3px 3px;
  }

  .mode-pill-btn {
    padding: 0 7px;
    font-size: 11px;
  }

  .auth-prism-card {
    padding: 28px 20px 24px;
    border-radius: 22px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .astro-ring-outer,
  .astro-ring-middle,
  .astro-radar-sweep,
  .astro-pulse-track,
  .astro-pulse-track-rev,
  .astro-core-star,
  .astro-resonance-ring,
  .astro-satellites {
    animation: none;
  }
}
</style>
