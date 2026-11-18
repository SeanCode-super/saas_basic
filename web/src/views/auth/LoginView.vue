<script setup lang="ts">
import { computed, onMounted, reactive, ref, useId } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  Key,
  Lock,
  Moon,
  RefreshRight,
  Sunny,
  User
} from "@element-plus/icons-vue";
import { fetchPortalEntry, type PortalEntry } from "@/api/modules/portal";
import {
  acceptTenantInvite,
  confirmPasswordReset,
  fetchAuthCaptcha,
  fetchTenantInvitePreview,
  sendPasswordResetCode,
  type TenantInviteResponse
} from "@/api/modules/auth";
import { useAuthStore } from "@/stores/modules/auth";
import { useThemeStore, type ThemePreset } from "@/stores/modules/theme";
import {
  extractSubdomainTenant,
  rememberPortalContext,
  resolvePortalContext
} from "@/utils/portal-context";
import TianshuLogo from "@/components/brand/TianshuLogo.vue";

const form = reactive({
  tenantCode: "",
  clientId: "",
  terminalCode: "web",
  captchaCode: "",
  totpCode: "",
  username: "admin",
  password: "Admin@123456"
});
const loading = ref(false);
const booting = ref(false);
const portal = ref<PortalEntry | null>(null);
const captchaId = ref("");
const captchaImageUrl = ref("");
const mfaRequired = ref(false);
const loginError = ref("");

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
  secondary: string;
  secondaryRgb: string;
}

const presetOptions: PresetPalette[] = [
  {
    value: "obsidian",
    label: "曜石极光",
    color: "#6366f1",
    rgb: "99, 102, 241",
    secondary: "#38bdf8",
    secondaryRgb: "56, 189, 248"
  },
  {
    value: "emerald",
    label: "松石森境",
    color: "#14b8a6",
    rgb: "20, 184, 166",
    secondary: "#38bdf8",
    secondaryRgb: "56, 189, 248"
  },
  {
    value: "titanium",
    label: "钛金琥珀",
    color: "#f59e0b",
    rgb: "245, 158, 11",
    secondary: "#818cf8",
    secondaryRgb: "129, 140, 248"
  },
  {
    value: "cobalt",
    label: "极光青蓝",
    color: "#3b82f6",
    rgb: "59, 130, 246",
    secondary: "#22d3ee",
    secondaryRgb: "34, 211, 238"
  }
];

const captchaEnabled = computed(() => portal.value?.captcha.mode === "IMAGE");

const activePreset = computed(
  () => presetOptions.find((item) => item.value === themeStore.preset) ?? presetOptions[0]
);

const universeStyle = computed(() => ({
  "--portal-glow": activePreset.value.color,
  "--portal-glow-rgb": activePreset.value.rgb,
  "--portal-secondary": activePreset.value.secondary,
  "--portal-secondary-rgb": activePreset.value.secondaryRgb
}));

const displayPortalTitle = computed(() => {
  const title = portal.value?.portalTitle?.trim();
  if (!title || title === "SaaS 基础底座控制台") {
    return "天枢协同云平台";
  }
  return title;
});

const displayClientName = computed(() => {
  const name = portal.value?.clientName?.trim();
  if (!name || name === "平台统一门户") {
    return "天枢主控空间";
  }
  return name;
});

const displayFilingInfo = computed(() => {
  const info = portal.value?.filingInfo?.trim();
  if (!info || info.startsWith("备案信息预留位")) {
    return "TLS 1.3 · X25519 + AES-256-GCM 零信任加密链路";
  }
  return info;
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

async function refreshCaptcha() {
  try {
    const challenge = await fetchAuthCaptcha();
    captchaId.value = challenge.captchaId;
    captchaImageUrl.value = challenge.imageDataUrl;
  } catch {
    captchaId.value = "";
    captchaImageUrl.value = "";
  }
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
    document.title = `${displayPortalTitle.value} | 安全接入`;
    loginError.value = "";
    await refreshCaptcha();
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

function fillDemoAccount(type: "admin" | "user" = "admin") {
  form.tenantCode =
    portal.value?.tenantCode ||
    (route.query.tenantCode as string) ||
    (route.query.tenant as string) ||
    "platform";
  if (type === "user") {
    form.username = "user";
    form.password = "User@123456";
    form.captchaCode = "";
    ElMessage.success(`已填入企业成员凭证 (user · ${displayClientName.value})`);
  } else {
    form.username = "admin";
    form.password = "Admin@123456";
    form.captchaCode = "";
    ElMessage.success(`已填入平台管理员凭证 (admin · ${displayClientName.value})`);
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
  if (captchaEnabled.value && !form.captchaCode.trim()) {
    loginError.value = "请输入图形验证码";
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
  loading.value = true;
  try {
    await authStore.login({
      tenantCode: form.tenantCode.trim() || undefined,
      clientId: form.clientId,
      terminalCode: form.terminalCode,
      captchaId: captchaEnabled.value && captchaId.value ? captchaId.value : undefined,
      captchaCode: form.captchaCode.trim() || undefined,
      totpCode: mfaRequired.value ? form.totpCode.trim() : undefined,
      username: form.username.trim(),
      password: form.password.trim()
    });
    const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/";
    void router.push(redirect);
  } catch (error: unknown) {
    const errCode = getAxiosErrorCode(error);
    const errMsg = getAxiosErrorMessage(error, "登录失败，请检查账号、密码或验证码");
    if (errCode === "AUTH_MFA_REQUIRED") {
      mfaRequired.value = true;
      loginError.value = errMsg;
      ElMessage.warning(errMsg);
    } else {
      loginError.value = errMsg;
      ElMessage.error(errMsg);
    }
    if (captchaEnabled.value) {
      await refreshCaptcha();
      form.captchaCode = "";
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

function toggleDarkMode() {
  themeStore.setMode(themeStore.isDark ? "light" : "dark");
}

onMounted(() => {
  void bootstrapPortal();
  void checkInviteToken();
});
</script>

<template>
  <div class="login-universe" :class="{ 'is-dark': themeStore.isDark }" :style="universeStyle">
    <!-- 空间感多维极光与透视坐标系背景 -->
    <div class="universe-aurora" aria-hidden="true">
      <div class="aurora-orb orb-primary" />
      <div class="aurora-orb orb-secondary" />
      <div class="aurora-orb orb-core" />
      <div class="universe-perspective-grid" />
      <div class="universe-starfield" />
    </div>

    <!-- 顶部悬浮空间导航栏 -->
    <header class="universe-topbar">
      <div class="topbar-brand">
        <img v-if="customLogoUrl" :src="customLogoUrl" alt="品牌标识" class="custom-portal-logo" />
        <TianshuLogo v-else :size="38" :theme="themeStore.isDark ? 'dark' : 'light'" animated />
        <div class="topbar-brand__meta">
          <strong>{{ displayPortalTitle }}</strong>
          <span>CELESTIAL CLOUD OS</span>
        </div>
      </div>

      <div class="topbar-controls">
        <span class="gateway-pill">
          <span class="gateway-pulse" />
          <span class="gateway-text">量子密文网关在线 · {{ portal?.terminal.terminalName || "Web 控制面" }}</span>
        </span>

        <div class="preset-capsule" title="切换空间主题光谱">
          <button
            v-for="item in presetOptions"
            :key="item.value"
            type="button"
            class="preset-dot-btn"
            :class="{ 'is-active': themeStore.preset === item.value }"
            :title="item.label"
            :aria-label="`切换主题为${item.label}`"
            @click="themeStore.setPreset(item.value)"
          >
            <span class="dot-core" :style="{ backgroundColor: item.color }" />
          </button>
        </div>

        <button
          type="button"
          class="mode-toggle-btn"
          :title="themeStore.isDark ? '切换为晨曦琉璃画布' : '切换为深空星轨画布'"
          :aria-label="themeStore.isDark ? '切换为浅色模式' : '切换为深色模式'"
          @click="toggleDarkMode"
        >
          <el-icon><component :is="themeStore.isDark ? Sunny : Moon" /></el-icon>
        </button>
      </div>
    </header>

    <!-- 中央非对称悬浮空间双舱 -->
    <main class="universe-stage">
      <!-- 左侧：天枢星轨全息舱 -->
      <section class="holo-monolith" aria-label="天枢平台能力架构概览">
        <div class="monolith-specular-line" aria-hidden="true" />
        <div class="monolith-ambient-mesh" aria-hidden="true" />

        <div class="monolith-header">
          <span class="monolith-eyebrow">
            <span class="eyebrow-pulse" />
            TIANSHU QUANTUM ARCHITECTURE
          </span>
          <span class="monolith-matrix-code">AST · X25519 · AI-NATIVE</span>
        </div>

        <!-- 矢量全息星轨图谱 -->
        <div class="astrolabe-stage" aria-hidden="true">
          <svg viewBox="0 0 460 240" fill="none" xmlns="http://www.w3.org/2000/svg" class="holo-astrolabe">
            <defs>
              <radialGradient :id="`astro-core-glow-${svgUid}`" cx="50%" cy="50%" r="50%">
                <stop offset="0%" stop-color="#38BDF8" stop-opacity="0.48" />
                <stop offset="45%" stop-color="#6366F1" stop-opacity="0.22" />
                <stop offset="100%" stop-color="#060A17" stop-opacity="0" />
              </radialGradient>
              <linearGradient :id="`astro-orbit-a-${svgUid}`" x1="90" y1="168" x2="370" y2="72" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stop-color="#818CF8" stop-opacity="0.16" />
                <stop offset="50%" stop-color="#38BDF8" stop-opacity="0.95" />
                <stop offset="100%" stop-color="#818CF8" stop-opacity="0.22" />
              </linearGradient>
              <linearGradient :id="`astro-orbit-b-${svgUid}`" x1="100" y1="68" x2="360" y2="172" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stop-color="#38BDF8" stop-opacity="0.12" />
                <stop offset="50%" stop-color="#A5B4FC" stop-opacity="0.72" />
                <stop offset="100%" stop-color="#38BDF8" stop-opacity="0.18" />
              </linearGradient>
              <linearGradient :id="`astro-star-${svgUid}`" x1="196" y1="86" x2="264" y2="154" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stop-color="#FFFFFF" />
                <stop offset="48%" stop-color="#BAE6FD" />
                <stop offset="100%" stop-color="#6366F1" />
              </linearGradient>
              <linearGradient :id="`astro-radar-${svgUid}`" x1="230" y1="120" x2="318" y2="46" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stop-color="#38BDF8" stop-opacity="0.28" />
                <stop offset="100%" stop-color="#38BDF8" stop-opacity="0" />
              </linearGradient>
            </defs>

            <!-- 中心引力场微光 -->
            <circle cx="230" cy="120" r="102" :fill="`url(#astro-core-glow-${svgUid})`" />

            <!-- 外层刻度光环 -->
            <circle
              cx="230"
              cy="120"
              r="94"
              stroke="rgba(148, 163, 184, 0.24)"
              stroke-width="1"
              stroke-dasharray="3 7"
              class="astro-ring-outer"
            />
            <circle cx="230" cy="120" r="68" stroke="rgba(129, 140, 248, 0.26)" stroke-width="1" />
            <circle cx="230" cy="120" r="42" stroke="rgba(56, 189, 248, 0.28)" stroke-width="1" stroke-dasharray="2 4" />

            <!-- 雷达扫描扇区 -->
            <path d="M230 120 L230 26 A94 94 0 0 1 312 73 Z" :fill="`url(#astro-radar-${svgUid})`" class="astro-radar-sweep" />

            <!-- HUD 引线 -->
            <path d="M164 78 L112 46 H28" stroke="rgba(148, 163, 184, 0.40)" stroke-width="1" />
            <path d="M296 78 L348 46 H432" stroke="rgba(148, 163, 184, 0.40)" stroke-width="1" />
            <path d="M164 162 L112 194 H28" stroke="rgba(148, 163, 184, 0.40)" stroke-width="1" />
            <path d="M296 162 L348 194 H432" stroke="rgba(148, 163, 184, 0.40)" stroke-width="1" />

            <!-- 倾斜量子主轨道 -->
            <ellipse
              cx="230"
              cy="120"
              rx="132"
              ry="40"
              transform="rotate(-22 230 120)"
              :stroke="`url(#astro-orbit-a-${svgUid})`"
              stroke-width="1.6"
            />
            <ellipse
              cx="230"
              cy="120"
              rx="132"
              ry="40"
              transform="rotate(-22 230 120)"
              stroke="#7DD3FC"
              stroke-width="1.8"
              stroke-dasharray="24 220"
              class="astro-pulse-track"
            />

            <!-- 交叉辅轨道 -->
            <ellipse
              cx="230"
              cy="120"
              rx="124"
              ry="36"
              transform="rotate(24 230 120)"
              :stroke="`url(#astro-orbit-b-${svgUid})`"
              stroke-width="1.15"
            />

            <!-- 中心四芒星棱镜 -->
            <path
              d="M230 82L239.5 110.5L268 120L239.5 129.5L230 158L220.5 129.5L192 120L220.5 110.5Z"
              :fill="`url(#astro-star-${svgUid})`"
            />
            <path
              d="M230 82L239.5 110.5L230 120L192 120L220.5 110.5Z"
              fill="#FFFFFF"
              fill-opacity="0.88"
            />
            <path d="M230 107L243 120L230 133L217 120Z" fill="#060A17" fill-opacity="0.58" />
            <circle cx="230" cy="120" r="4.5" fill="#FFFFFF" />

            <!-- 四象限节点锚 -->
            <circle cx="164" cy="78" r="3.2" fill="#38BDF8" />
            <circle cx="296" cy="78" r="3.2" fill="#818CF8" />
            <circle cx="164" cy="162" r="3.2" fill="#34D399" />
            <circle cx="296" cy="162" r="3.2" fill="#38BDF8" />
          </svg>

          <div class="hud-node hud-node--tl">
            <strong>多租户隔离域</strong>
            <span>5D AST</span>
          </div>
          <div class="hud-node hud-node--tr">
            <strong>AI 智算中枢</strong>
            <span>Agent · RAG</span>
          </div>
          <div class="hud-node hud-node--bl">
            <strong>零信任密文网</strong>
            <span>X25519 · GCM</span>
          </div>
          <div class="hud-node hud-node--br">
            <strong>全链路协同流</strong>
            <span>OIDC · SCIM</span>
          </div>
        </div>

        <!-- 主叙事文案 -->
        <div class="monolith-copy">
          <h1>
            全域协同 · 智算驱动的<br />
            <span class="hero-gradient-text">新一代企业云原生数字中枢</span>
          </h1>
          <p>
            基于五轴行级数据隔离、全链路密文握手与标准组织权限图谱，为集团与多业态 SaaS 业务提供高可信智能底座。
          </p>
        </div>

        <!-- 三维遥测指标条 -->
        <div class="telemetry-strip">
          <div class="telemetry-cell">
            <span class="telemetry-label">DATA ISOLATION</span>
            <strong>五轴行级隔离</strong>
            <span class="telemetry-desc">租户·部门·本人实时重写</span>
          </div>
          <div class="telemetry-cell">
            <span class="telemetry-label">ZERO TRUST CRYPTO</span>
            <strong>传输密文防御</strong>
            <span class="telemetry-desc">国密兼容与防重放校验</span>
          </div>
          <div class="telemetry-cell">
            <span class="telemetry-label">AI NATIVE ENGINE</span>
            <strong>智算路由协同</strong>
            <span class="telemetry-desc">工作流与模型安全编排</span>
          </div>
        </div>
      </section>

      <!-- 右侧：晶体棱镜认证舱 -->
      <section class="auth-prism-card" aria-label="安全认证表单">
        <div class="prism-top-beam" aria-hidden="true" />

        <header class="prism-header">
          <div class="workspace-badge">
            <span class="workspace-dot" />
            <span class="workspace-name">{{ displayClientName }}</span>
            <code v-if="form.tenantCode" class="workspace-code">{{ form.tenantCode }}</code>
          </div>
          <h2>欢迎接入天枢控制台</h2>
          <p>验证企业身份凭证或通过下方联邦身份通道接入。</p>
        </header>

        <el-skeleton :loading="booting" animated>
          <template #template>
            <el-skeleton-item variant="rect" style="height: 340px; border-radius: 14px" />
          </template>

          <el-form label-position="top" class="prism-form" @submit.prevent="handleLogin">
            <el-alert
              v-if="loginError"
              class="auth-error-alert"
              type="error"
              :closable="false"
              :title="loginError"
            />

            <el-form-item label="账号凭证">
              <el-input
                v-model="form.username"
                size="large"
                placeholder="用户名 / 手机号 / 企业邮箱"
                autocomplete="username"
                :prefix-icon="User"
                @keyup.enter="handleLogin"
              />
            </el-form-item>

            <el-form-item label="访问密码">
              <template #label>
                <div class="password-label-row">
                  <span>访问密码</span>
                  <el-button link type="primary" size="small" class="reset-link-btn" @click="openResetDialog">
                    忘记密码？
                  </el-button>
                </div>
              </template>
              <el-input
                v-model="form.password"
                size="large"
                type="password"
                show-password
                placeholder="请输入访问密码"
                autocomplete="current-password"
                :prefix-icon="Lock"
                @keyup.enter="handleLogin"
              />
            </el-form-item>

            <el-form-item v-if="captchaEnabled" label="安全验证码">
              <div class="captcha-row">
                <el-input
                  v-model="form.captchaCode"
                  size="large"
                  placeholder="请输入图形验证码"
                  :prefix-icon="Key"
                  @keyup.enter="handleLogin"
                />
                <button
                  type="button"
                  class="captcha-box"
                  title="点击刷新验证码"
                  @click="refreshCaptcha"
                >
                  <span class="captcha-img-wrap">
                    <img
                      v-if="captchaImageUrl"
                      :src="captchaImageUrl"
                      alt="安全验证码"
                      class="captcha-img"
                    />
                    <span v-else class="captcha-fallback">刷新</span>
                  </span>
                  <span class="captcha-refresh-pill" aria-hidden="true">
                    <el-icon><RefreshRight /></el-icon>
                  </span>
                </button>
              </div>
            </el-form-item>

            <el-form-item v-if="mfaRequired" label="MFA 动态口令 / 恢复码">
              <el-input
                v-model="form.totpCode"
                size="large"
                placeholder="请输入 6 位数字或备用恢复码"
                maxlength="16"
                :prefix-icon="Lock"
                @keyup.enter="handleLogin"
              />
            </el-form-item>

            <button type="submit" class="cyber-submit-btn" :disabled="loading">
              <span class="btn-sheen" aria-hidden="true" />
              <span class="btn-label">{{ loading ? "正在建立安全握手..." : "进入天枢控制台" }}</span>
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

          <!-- 紧凑型快捷体验凭证条 -->
          <div class="quick-fill-bar">
            <span class="quick-fill-label">快速体验填入</span>
            <div class="quick-fill-pills">
              <button type="button" class="quick-pill" @click="fillDemoAccount('admin')">
                <span class="pill-dot is-admin" />
                <span>平台管理员</span>
              </button>
              <button type="button" class="quick-pill" @click="fillDemoAccount('user')">
                <span class="pill-dot is-user" />
                <span>企业成员</span>
              </button>
            </div>
          </div>

          <!-- 企业 SSO 联邦接入通道 -->
          <div class="sso-dock">
            <div class="sso-divider">
              <span>或通过企业身份联邦接入</span>
            </div>
            <div class="sso-grid">
              <button type="button" class="sso-tile" @click="handleSsoLogin('WECOM')">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
                  <path d="M4 11c0-3.9 3.6-7 8-7s8 3.1 8 7-3.6 7-8 7c-1.2 0-2.4-.2-3.4-.7L4 19l1-3.3C4.4 14.3 4 12.7 4 11z" />
                </svg>
                <span>企业微信</span>
              </button>
              <button type="button" class="sso-tile" @click="handleSsoLogin('DINGTALK')">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
                  <path d="M13 3L5 14h6l-1 7 9-11h-6l0-7z" />
                </svg>
                <span>钉钉</span>
              </button>
              <button type="button" class="sso-tile" @click="handleSsoLogin('FEISHU')">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
                  <path d="M4 17L19 5l-4 14-4.5-4.5L4 17z" />
                </svg>
                <span>飞书</span>
              </button>
              <button type="button" class="sso-tile" @click="handleSsoLogin('SAML_OIDC')">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
                  <circle cx="8" cy="12" r="3" />
                  <path d="M11 12h9M17 12v3M20 12v2" />
                </svg>
                <span>OIDC / SAML</span>
              </button>
            </div>
          </div>
        </el-skeleton>
      </section>
    </main>

    <!-- 底部空间签名栏 -->
    <footer class="universe-footer">
      <span>{{ displayFilingInfo }}</span>
      <span class="footer-sep" aria-hidden="true">·</span>
      <span>Tianshu Collaborative Cloud Platform</span>
    </footer>

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
  --portal-secondary: #38bdf8;
  --portal-secondary-rgb: 56, 189, 248;

  position: relative;
  min-height: 100vh;
  width: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 20px 28px;
  overflow-x: hidden;
  background: linear-gradient(150deg, #eef2ff 0%, #f8fafc 46%, #edf5ff 100%);
  color: #0f172a;
  transition: background 260ms ease, color 260ms ease;

  &.is-dark {
    background: radial-gradient(circle at 50% 0%, #0b1430 0%, #050814 55%, #03050d 100%);
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
  filter: blur(88px);
  transition: background 260ms ease;
}

.orb-primary {
  top: -160px;
  left: -100px;
  width: 620px;
  height: 620px;
  background: radial-gradient(circle, rgba(var(--portal-glow-rgb), 0.24) 0%, transparent 70%);
}

.orb-secondary {
  right: -120px;
  bottom: -140px;
  width: 560px;
  height: 560px;
  background: radial-gradient(circle, rgba(var(--portal-secondary-rgb), 0.20) 0%, transparent 70%);
}

.orb-core {
  top: 28%;
  left: 42%;
  width: 460px;
  height: 460px;
  background: radial-gradient(circle, rgba(var(--portal-glow-rgb), 0.12) 0%, transparent 72%);
}

.is-dark {
  .orb-primary {
    background: radial-gradient(circle, rgba(var(--portal-glow-rgb), 0.34) 0%, transparent 70%);
  }

  .orb-secondary {
    background: radial-gradient(circle, rgba(var(--portal-secondary-rgb), 0.26) 0%, transparent 70%);
  }

  .orb-core {
    background: radial-gradient(circle, rgba(var(--portal-glow-rgb), 0.18) 0%, transparent 72%);
  }
}

.universe-perspective-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, rgba(15, 23, 42, 0.045) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(15, 23, 42, 0.045) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(ellipse 85% 75% at 50% 45%, #000 35%, transparent 100%);
}

.is-dark .universe-perspective-grid {
  background-image:
    linear-gradient(to right, rgba(148, 163, 184, 0.07) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(148, 163, 184, 0.07) 1px, transparent 1px);
}

.universe-starfield {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(1.5px 1.5px at 14% 22%, rgba(99, 102, 241, 0.38), transparent),
    radial-gradient(1.5px 1.5px at 84% 18%, rgba(56, 189, 248, 0.42), transparent),
    radial-gradient(1px 1px at 66% 78%, rgba(99, 102, 241, 0.32), transparent),
    radial-gradient(1.5px 1.5px at 26% 84%, rgba(56, 189, 248, 0.36), transparent);
}

/* 顶部空间导航条 */
.universe-topbar {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.topbar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.custom-portal-logo {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  object-fit: cover;
}

.topbar-brand__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  strong {
    font-size: 16px;
    font-weight: 700;
    letter-spacing: -0.01em;
    color: #0f172a;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  span {
    font-family: var(--sb-font-mono);
    font-size: 10px;
    font-weight: 600;
    letter-spacing: 0.14em;
    color: #64748b;
  }
}

.is-dark .topbar-brand__meta {
  strong {
    color: #f8fafc;
  }

  span {
    color: #94a3b8;
  }
}

.topbar-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.gateway-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(203, 213, 225, 0.72);
  backdrop-filter: blur(14px);
  font-size: 12px;
  font-weight: 500;
  color: #334155;
}

.is-dark .gateway-pill {
  background: rgba(15, 23, 42, 0.68);
  border-color: rgba(148, 163, 184, 0.22);
  color: #cbd5e1;
}

.gateway-pulse {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 10px rgba(16, 185, 129, 0.78);
  flex-shrink: 0;
}

.preset-capsule {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 8px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.74);
  border: 1px solid rgba(203, 213, 225, 0.74);
  backdrop-filter: blur(14px);
}

.is-dark .preset-capsule {
  background: rgba(15, 23, 42, 0.68);
  border-color: rgba(148, 163, 184, 0.22);
}

.preset-dot-btn {
  width: 20px;
  height: 20px;
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
    transform: scale(1.08);
  }

  &.is-active {
    border-color: var(--portal-glow);
  }
}

.dot-core {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  display: block;
}

.mode-toggle-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid rgba(203, 213, 225, 0.74);
  background: rgba(255, 255, 255, 0.74);
  backdrop-filter: blur(14px);
  color: #334155;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 160ms ease, background 160ms ease, transform 160ms ease;

  &:hover {
    border-color: var(--portal-glow);
    transform: translateY(-1px);
  }
}

.is-dark .mode-toggle-btn {
  background: rgba(15, 23, 42, 0.68);
  border-color: rgba(148, 163, 184, 0.22);
  color: #e2e8f0;
}

/* 中央双舱舞台 */
.universe-stage {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 1180px;
  margin: 18px auto;
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(380px, 444px);
  gap: 26px;
  align-items: stretch;
}

/* 左侧：深空全息星轨舱（浅色与深色下均维持高反差全息视觉核心） */
.holo-monolith {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 28px 30px;
  border-radius: 28px;
  background: linear-gradient(148deg, #060a17 0%, #0c1429 52%, #121c3a 100%);
  border: 1px solid rgba(255, 255, 255, 0.14);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.18),
    0 28px 60px -18px rgba(6, 10, 23, 0.36);
  color: #f8fafc;
  overflow: hidden;
}

.monolith-specular-line {
  position: absolute;
  top: 0;
  left: 32px;
  right: 32px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(var(--portal-secondary-rgb), 0.72), rgba(var(--portal-glow-rgb), 0.72), transparent);
  pointer-events: none;
}

.monolith-ambient-mesh {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 22% 18%, rgba(var(--portal-glow-rgb), 0.24), transparent 52%),
    radial-gradient(circle at 82% 80%, rgba(var(--portal-secondary-rgb), 0.18), transparent 50%);
  pointer-events: none;
}

.monolith-header {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.monolith-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.14);
  font-family: var(--sb-font-mono);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: #e2e8f0;
}

.eyebrow-pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--portal-secondary);
  box-shadow: 0 0 10px var(--portal-secondary);
}

.monolith-matrix-code {
  font-family: var(--sb-font-mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.08em;
  color: rgba(148, 163, 184, 0.85);
}

/* 全息星轨舞台 */
.astrolabe-stage {
  position: relative;
  z-index: 2;
  margin: 14px 0;
  padding: 6px 0;
}

.holo-astrolabe {
  display: block;
  width: 100%;
  height: auto;
  max-height: 235px;
}

.astro-ring-outer {
  transform-origin: 230px 120px;
  animation: astroRotate 54s linear infinite;
}

.astro-radar-sweep {
  transform-origin: 230px 120px;
  animation: astroRotate 11s linear infinite;
}

.astro-pulse-track {
  animation: astroTrackFlow 4.2s linear infinite;
}

.hud-node {
  position: absolute;
  padding: 5px 10px;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.72);
  border: 1px solid rgba(148, 163, 184, 0.24);
  backdrop-filter: blur(8px);
  line-height: 1.25;

  strong {
    display: block;
    font-size: 11.5px;
    font-weight: 600;
    color: #f8fafc;
  }

  span {
    display: block;
    font-family: var(--sb-font-mono);
    font-size: 9.5px;
    color: #93c5fd;
  }

  &--tl {
    top: 24px;
    left: 2px;
  }

  &--tr {
    top: 24px;
    right: 2px;
    text-align: right;
  }

  &--bl {
    bottom: 24px;
    left: 2px;
  }

  &--br {
    bottom: 24px;
    right: 2px;
    text-align: right;
  }
}

.monolith-copy {
  position: relative;
  z-index: 2;

  h1 {
    margin: 0;
    font-size: 27px;
    line-height: 1.28;
    font-weight: 700;
    letter-spacing: -0.02em;
    color: #f8fafc;
  }

  p {
    margin: 10px 0 0;
    max-width: 520px;
    font-size: 13.5px;
    line-height: 1.65;
    color: #94a3b8;
  }
}

.hero-gradient-text {
  background: linear-gradient(95deg, #f8fafc 12%, #bae6fd 52%, var(--portal-secondary) 96%);
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
}

.telemetry-strip {
  position: relative;
  z-index: 2;
  margin-top: 20px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.telemetry-cell {
  padding: 12px 13px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.045);
  border: 1px solid rgba(255, 255, 255, 0.09);

  strong {
    display: block;
    margin-top: 4px;
    font-size: 13px;
    font-weight: 600;
    color: #f8fafc;
  }
}

.telemetry-label {
  display: block;
  font-family: var(--sb-font-mono);
  font-size: 9.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--portal-secondary);
}

.telemetry-desc {
  display: block;
  margin-top: 3px;
  font-size: 11.5px;
  line-height: 1.4;
  color: #94a3b8;
}

/* 右侧：晶体棱镜认证舱 */
.auth-prism-card {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 32px 30px 26px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(28px) saturate(190%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.95),
    0 24px 56px -14px rgba(15, 23, 42, 0.14);
  overflow: hidden;
}

.is-dark .auth-prism-card {
  background: rgba(11, 17, 34, 0.82);
  border-color: rgba(148, 163, 184, 0.22);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.14),
    0 28px 64px -16px rgba(0, 0, 0, 0.62);
}

.prism-top-beam {
  position: absolute;
  top: 0;
  left: 28px;
  right: 28px;
  height: 2px;
  border-radius: 9999px;
  background: linear-gradient(90deg, transparent, var(--portal-glow), var(--portal-secondary), transparent);
  opacity: 0.9;
}

.prism-header {
  margin-bottom: 20px;

  h2 {
    margin: 14px 0 0;
    font-size: 24px;
    font-weight: 700;
    letter-spacing: -0.02em;
    color: var(--sb-text-primary);
  }

  p {
    margin: 6px 0 0;
    font-size: 13px;
    color: var(--sb-text-muted);
  }
}

.workspace-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 11px;
  border-radius: 9999px;
  background: rgba(var(--portal-glow-rgb), 0.10);
  border: 1px solid rgba(var(--portal-glow-rgb), 0.26);
  font-size: 12px;
  font-weight: 600;
  color: var(--portal-glow);
}

.is-dark .workspace-badge {
  background: rgba(var(--portal-glow-rgb), 0.20);
  border-color: rgba(var(--portal-glow-rgb), 0.36);
  color: #e0e7ff;
}

.workspace-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--portal-glow);
  box-shadow: 0 0 8px rgba(var(--portal-glow-rgb), 0.72);
}

.workspace-code {
  padding: 1px 6px;
  border-radius: 9999px;
  background: rgba(var(--portal-glow-rgb), 0.14);
  font-family: var(--sb-font-mono);
  font-size: 11px;
  font-weight: 600;
}

.prism-form {
  :deep(.el-form-item) {
    margin-bottom: 16px;
  }

  :deep(.el-form-item__label) {
    font-size: 12.5px;
    font-weight: 600;
    color: var(--sb-text-secondary);
    padding-bottom: 6px;
  }

  :deep(.el-input__wrapper) {
    height: 46px;
    padding: 0 14px;
    border-radius: 12px;
    background: rgba(248, 250, 252, 0.85);
    box-shadow: 0 0 0 1px rgba(203, 213, 225, 0.75) inset;
    transition: box-shadow 180ms ease, background 180ms ease;

    &:hover {
      box-shadow: 0 0 0 1px rgba(var(--portal-glow-rgb), 0.55) inset;
    }

    &.is-focus {
      background: #ffffff;
      box-shadow:
        0 0 0 1.5px var(--portal-glow) inset,
        0 0 22px -6px rgba(var(--portal-glow-rgb), 0.45);
    }
  }
}

.is-dark .prism-form {
  :deep(.el-input__wrapper) {
    background: rgba(15, 23, 42, 0.72);
    box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.24) inset;

    &.is-focus {
      background: rgba(15, 23, 42, 0.96);
      box-shadow:
        0 0 0 1.5px var(--portal-glow) inset,
        0 0 22px -6px rgba(var(--portal-glow-rgb), 0.55);
    }
  }
}

.auth-error-alert {
  margin-bottom: 14px;
  border-radius: 10px;
}

.password-label-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.reset-link-btn {
  font-size: 12px;
  font-weight: 500;
}

.captcha-row {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 144px;
  gap: 10px;
}

.captcha-box {
  position: relative;
  height: 46px;
  border-radius: 12px;
  border: 1px solid rgba(203, 213, 225, 0.75);
  background: rgba(255, 255, 255, 0.96);
  padding: 0;
  overflow: hidden;
  cursor: pointer;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 26px;
  align-items: stretch;
  transition: border-color 160ms ease;

  &:hover {
    border-color: var(--portal-glow);
  }
}

.is-dark .captcha-box {
  border-color: rgba(148, 163, 184, 0.28);
  background: rgba(248, 250, 252, 0.94);
}

.captcha-img-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.captcha-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.captcha-fallback {
  font-size: 12px;
  color: #475569;
}

.captcha-refresh-pill {
  border-left: 1px solid rgba(203, 213, 225, 0.7);
  background: rgba(241, 245, 249, 0.92);
  color: #475569;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
}

/* 未来感主行动 CTA */
.cyber-submit-btn {
  position: relative;
  width: 100%;
  height: 48px;
  margin-top: 6px;
  padding: 0 18px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 12px;
  background: linear-gradient(110deg, var(--portal-glow) 0%, #4338ca 52%, var(--portal-secondary) 100%);
  background-size: 160% 100%;
  color: #ffffff;
  font-size: 14.5px;
  font-weight: 600;
  letter-spacing: 0.02em;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  overflow: hidden;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.34),
    0 12px 28px -8px rgba(var(--portal-glow-rgb), 0.56);
  transition: transform 180ms ease, box-shadow 180ms ease, background-position 260ms ease, opacity 180ms ease;

  &:hover:not(:disabled) {
    background-position: 80% 0;
    transform: translateY(-1px);
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.42),
      0 16px 34px -8px rgba(var(--portal-glow-rgb), 0.68);
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

/* 紧凑体验凭证条 */
.quick-fill-bar {
  margin-top: 18px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(var(--portal-glow-rgb), 0.05);
  border: 1px solid rgba(var(--portal-glow-rgb), 0.14);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.is-dark .quick-fill-bar {
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(255, 255, 255, 0.10);
}

.quick-fill-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--sb-text-muted);
  white-space: nowrap;
}

.quick-fill-pills {
  display: flex;
  align-items: center;
  gap: 6px;
}

.quick-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 11px;
  border-radius: 9999px;
  border: 1px solid rgba(var(--portal-glow-rgb), 0.22);
  background: rgba(255, 255, 255, 0.78);
  color: var(--sb-text-primary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: border-color 150ms ease, background 150ms ease, transform 150ms ease;

  &:hover {
    border-color: var(--portal-glow);
    background: rgba(var(--portal-glow-rgb), 0.12);
    transform: translateY(-1px);
  }
}

.is-dark .quick-pill {
  background: rgba(15, 23, 42, 0.72);
  border-color: rgba(148, 163, 184, 0.24);
  color: #f8fafc;
}

.pill-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;

  &.is-admin {
    background: var(--portal-glow);
  }

  &.is-user {
    background: #10b981;
  }
}

/* 企业 SSO 联邦通道 */
.sso-dock {
  margin-top: 20px;
}

.sso-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;

  &::before,
  &::after {
    content: "";
    flex: 1;
    height: 1px;
    background: rgba(148, 163, 184, 0.28);
  }

  span {
    font-size: 11.5px;
    color: var(--sb-text-muted);
    white-space: nowrap;
  }
}

.sso-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.sso-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 6px;
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.26);
  background: rgba(255, 255, 255, 0.58);
  color: var(--sb-text-secondary);
  font-size: 11.5px;
  font-weight: 500;
  cursor: pointer;
  transition: border-color 160ms ease, background 160ms ease, transform 160ms ease, box-shadow 160ms ease;

  svg {
    width: 17px;
    height: 17px;
    color: var(--portal-glow);
  }

  &:hover {
    border-color: rgba(var(--portal-glow-rgb), 0.56);
    background: rgba(var(--portal-glow-rgb), 0.10);
    color: var(--sb-text-primary);
    transform: translateY(-1px);
    box-shadow: 0 8px 18px -8px rgba(var(--portal-glow-rgb), 0.35);
  }
}

.is-dark .sso-tile {
  background: rgba(15, 23, 42, 0.56);
  border-color: rgba(148, 163, 184, 0.18);
  color: #cbd5e1;

  &:hover {
    color: #ffffff;
  }
}

/* 底部信息条 */
.universe-footer {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 12px;
  color: #64748b;
  flex-wrap: wrap;
  text-align: center;
}

.is-dark .universe-footer {
  color: #94a3b8;
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

@keyframes astroTrackFlow {
  from {
    stroke-dashoffset: 0;
  }

  to {
    stroke-dashoffset: -244;
  }
}

@media (max-width: 1080px) {
  .universe-stage {
    grid-template-columns: minmax(0, 1fr);
    max-width: 480px;
  }

  .holo-monolith {
    display: none;
  }

  .gateway-pill {
    display: none;
  }
}

@media (max-width: 640px) {
  .login-universe {
    padding: 16px;
  }

  .topbar-controls {
    flex-wrap: nowrap;
    flex-shrink: 0;
    gap: 6px;
  }

  .topbar-brand__meta strong {
    font-size: 14px;
  }

  .auth-prism-card {
    padding: 24px 20px 22px;
    border-radius: 22px;
  }

  .quick-fill-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .sso-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (prefers-reduced-motion: reduce) {
  .astro-ring-outer,
  .astro-radar-sweep,
  .astro-pulse-track {
    animation: none;
  }
}
</style>
