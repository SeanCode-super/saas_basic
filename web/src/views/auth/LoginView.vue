<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
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

const presetOptions: Array<{ value: ThemePreset; label: string; color: string }> = [
  { value: "obsidian", label: "曜石极光", color: "#4f46e5" },
  { value: "emerald", label: "松石森境", color: "#0f766e" },
  { value: "titanium", label: "钛金琥珀", color: "#d97706" },
  { value: "cobalt", label: "极光青蓝", color: "#2563eb" }
];

const captchaEnabled = computed(() => portal.value?.captcha.mode === "IMAGE");

const activePresetColor = computed(
  () => presetOptions.find((item) => item.value === themeStore.preset)?.color ?? "#4f46e5"
);

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
    document.title = `${entry.portalTitle} | 登录`;
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
    ElMessage.success(`已填入员工凭证 (user，专属企业: ${portal.value?.clientName || form.tenantCode})`);
  } else {
    form.username = "admin";
    form.password = "Admin@123456";
    form.captchaCode = "";
    ElMessage.success(`已填入管理员凭证 (admin，专属企业: ${portal.value?.clientName || form.tenantCode})`);
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
  <div class="login-split" :style="{ '--portal-glow': activePresetColor }">
    <!-- 左侧 46%：深邃曜石建筑品牌舱 -->
    <aside class="login-split__brand">
      <div class="brand-grid-bg" aria-hidden="true" />
      <div class="brand-aura" aria-hidden="true" />

      <div class="brand-top">
        <div class="brand-identity">
          <TianshuLogo :size="30" />
          <div class="brand-identity__text">
            <strong>{{ portal?.portalTitle || "天枢云基 Tianshu PaaS" }}</strong>
            <span>Multi-Tenant Enterprise Cloud</span>
          </div>
        </div>

        <div class="brand-theme-controls">
          <div class="preset-dots" title="切换工作台预设主题">
            <button
              v-for="item in presetOptions"
              :key="item.value"
              type="button"
              class="preset-dot-btn"
              :class="{ 'is-active': themeStore.preset === item.value }"
              :title="item.label"
              @click="themeStore.setPreset(item.value)"
            >
              <span class="dot-core" :style="{ backgroundColor: item.color }" />
            </button>
          </div>
          <button
            type="button"
            class="mode-toggle-btn"
            :title="themeStore.isDark ? '切换为浅色画布' : '切换为暗夜画布'"
            @click="toggleDarkMode"
          >
            <el-icon><component :is="themeStore.isDark ? Sunny : Moon" /></el-icon>
          </button>
        </div>
      </div>

      <div class="brand-hero">
        <span class="hero-eyebrow">ENTERPRISE CONTROL PLANE · V2026</span>
        <h1>统一治理的多租户<br />与 AI Native 数字底座</h1>
        <p>
          基于五轴行级数据隔离、全链路报文加密与标准化组织权限图谱，为集团与多业态 SaaS 业务提供零信任企业级运行环境。
        </p>

        <div class="trust-pillars">
          <div class="trust-card">
            <span class="trust-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <path d="M12 2L4 6v6c0 5.5 3.4 9.7 8 11 4.6-1.3 8-5.5 8-11V6l-8-4z" />
                <path d="M9 12l2 2 4-4" />
              </svg>
            </span>
            <div>
              <strong>5D 行级隔离 · JSqlParser AST</strong>
              <span>支持租户、法人、部门树、岗位与 ABAC 属性表达式实时编译改写。</span>
            </div>
          </div>

          <div class="trust-card">
            <span class="trust-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <rect x="4" y="10" width="16" height="10" rx="2" />
                <path d="M8 10V7a4 4 0 118 0v3" />
              </svg>
            </span>
            <div>
              <strong>X25519 + AES-256-GCM 传输防御</strong>
              <span>国密兼容非对称密钥协商与防重放校验，高敏凭证毫秒级全量密文流转。</span>
            </div>
          </div>

          <div class="trust-card">
            <span class="trust-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <circle cx="8" cy="8" r="3" />
                <circle cx="17" cy="17" r="3" />
                <path d="M10.5 10.5L14.5 14.5M17 7h-4M7 17h4" />
              </svg>
            </span>
            <div>
              <strong>OIDC PKCE + SCIM 2.0 联邦治理</strong>
              <span>原生打通企业微信、钉钉、飞书与企业标准 IdP 单点登录与组织自动同步。</span>
            </div>
          </div>
        </div>
      </div>

      <footer class="brand-footer">
        <span class="status-badge">
          <span class="status-pulse" />
          网关在线 · {{ portal?.terminal.terminalName || "Web 控制面" }}
        </span>
        <span class="copyright">
          {{ portal?.filingInfo || "© 2026 Tianshu PaaS Foundation" }}
        </span>
      </footer>
    </aside>

    <!-- 右侧 54%：纯净高留白认证舱 -->
    <main class="login-split__stage">
      <div class="auth-card-wrap">
        <header class="auth-header">
          <div class="auth-portal-chip">
            <span class="chip-dot" />
            <span>{{ portal?.clientName || "平台统一控制门户" }}</span>
            <code v-if="form.tenantCode">{{ form.tenantCode }}</code>
          </div>
          <h2>登录天枢工作台</h2>
          <p>请使用企业账号密码或下方联邦身份渠道安全接入。</p>
        </header>

        <el-skeleton :loading="booting" animated>
          <template #template>
            <el-skeleton-item variant="rect" style="height: 320px; border-radius: 10px" />
          </template>

          <el-form label-position="top" @submit.prevent="handleLogin">
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
                  <el-button link type="primary" size="small" @click="openResetDialog">
                    忘记密码？自助重置
                  </el-button>
                </div>
              </template>
              <el-input
                v-model="form.password"
                size="large"
                type="password"
                show-password
                placeholder="请输入登录密码"
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
                  <img
                    v-if="captchaImageUrl"
                    :src="captchaImageUrl"
                    alt="安全验证码"
                    class="captcha-img"
                  />
                  <span v-else class="captcha-fallback">刷新</span>
                  <el-icon class="captcha-refresh"><RefreshRight /></el-icon>
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

            <el-button
              type="primary"
              size="large"
              native-type="submit"
              class="auth-submit-btn"
              :loading="loading"
            >
              {{ loading ? "正在安全校验..." : "进入控制台" }}
            </el-button>
          </el-form>

          <!-- 快速演示体验账户 -->
          <div class="demo-section">
            <span class="section-caption">快速体验账号（点击自动回填）</span>
            <div class="demo-grid">
              <button type="button" class="demo-tile" @click="fillDemoAccount('admin')">
                <span class="demo-badge is-admin">ADMIN</span>
                <span class="demo-meta">
                  <strong>平台超级管理员</strong>
                  <code>admin / Admin@123456</code>
                </span>
              </button>
              <button type="button" class="demo-tile" @click="fillDemoAccount('user')">
                <span class="demo-badge is-user">USER</span>
                <span class="demo-meta">
                  <strong>企业普通成员</strong>
                  <code>user / User@123456</code>
                </span>
              </button>
            </div>
          </div>

          <!-- 企业 SSO 联邦通道 (纯矢量 SVG 图标) -->
          <div class="sso-section">
            <div class="sso-divider">
              <span>或通过企业身份联邦快捷接入</span>
            </div>
            <div class="sso-grid">
              <button type="button" class="sso-tile" @click="handleSsoLogin('WECOM')">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                  <path d="M4 11c0-3.9 3.6-7 8-7s8 3.1 8 7-3.6 7-8 7c-1.2 0-2.4-.2-3.4-.7L4 19l1-3.3C4.4 14.3 4 12.7 4 11z" />
                </svg>
                <span>企业微信</span>
              </button>
              <button type="button" class="sso-tile" @click="handleSsoLogin('DINGTALK')">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                  <path d="M13 3L5 14h6l-1 7 9-11h-6l0-7z" />
                </svg>
                <span>钉钉工作台</span>
              </button>
              <button type="button" class="sso-tile" @click="handleSsoLogin('FEISHU')">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                  <path d="M4 17L19 5l-4 14-4.5-4.5L4 17z" />
                </svg>
                <span>飞书认证</span>
              </button>
              <button type="button" class="sso-tile" @click="handleSsoLogin('SAML_OIDC')">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                  <circle cx="8" cy="12" r="3" />
                  <path d="M11 12h9M17 12v3M20 12v2" />
                </svg>
                <span>OIDC / SAML</span>
              </button>
            </div>
          </div>
        </el-skeleton>
      </div>
    </main>

    <!-- 自助找回密码对话框 -->
    <el-dialog v-model="resetDialogVisible" title="自助找回并重置密码" width="440px" append-to-body>
      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="resetForm.username" placeholder="请输入待重置的账号用户名" />
        </el-form-item>
        <el-form-item label="绑定邮箱或手机号">
          <div style="display: flex; gap: 8px; width: 100%">
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
.login-split {
  --portal-glow: #4f46e5;

  min-height: 100vh;
  width: 100%;
  display: grid;
  grid-template-columns: minmax(420px, 46%) minmax(0, 1fr);
  background: var(--sb-stage-bg, #ffffff);
  color: var(--sb-text-primary);
}

.login-split__brand {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 28px 38px;
  background: #0c0c10;
  color: #fafafa;
  overflow: hidden;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-grid-bg {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, rgba(255, 255, 255, 0.035) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(255, 255, 255, 0.035) 1px, transparent 1px);
  background-size: 36px 36px;
  mask-image: radial-gradient(ellipse 80% 70% at 30% 35%, #000 30%, transparent 100%);
  pointer-events: none;
}

.brand-aura {
  position: absolute;
  top: -120px;
  left: -80px;
  width: 520px;
  height: 520px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--portal-glow) 0%, transparent 68%);
  opacity: 0.24;
  filter: blur(70px);
  pointer-events: none;
  transition: background 240ms ease;
}

.brand-top {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.brand-identity {
  display: flex;
  align-items: center;
  gap: 11px;
}

.brand-identity__text {
  display: flex;
  flex-direction: column;
  gap: 1px;

  strong {
    font-size: 15px;
    font-weight: 700;
    color: #fafafa;
    letter-spacing: -0.01em;
  }

  span {
    font-size: 11px;
    color: #a1a1aa;
  }
}

.brand-theme-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.preset-dots {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 6px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.10);
}

.preset-dot-btn {
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

  &.is-active {
    border-color: #ffffff;
  }
}

.dot-core {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: block;
}

.mode-toggle-btn {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.06);
  color: #e4e4e7;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;

  &:hover {
    background: rgba(255, 255, 255, 0.14);
  }
}

.brand-hero {
  position: relative;
  z-index: 2;
  margin: auto 0;
  padding: 24px 0;

  h1 {
    margin: 12px 0 0;
    font-size: 32px;
    line-height: 1.2;
    font-weight: 750;
    letter-spacing: -0.025em;
    color: #fafafa;
  }

  > p {
    margin: 12px 0 0;
    max-width: 460px;
    font-size: 14px;
    line-height: 1.65;
    color: #a1a1aa;
  }
}

.hero-eyebrow {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #d4d4d8;
  font-family: var(--sb-font-mono);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.trust-pillars {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 28px;
}

.trust-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 13px 15px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.09);

  strong {
    display: block;
    font-size: 13px;
    font-weight: 650;
    color: #fafafa;
  }

  span {
    display: block;
    margin-top: 3px;
    font-size: 12px;
    line-height: 1.45;
    color: #a1a1aa;
  }
}

.trust-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.08);
  color: #a5b4fc;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  svg {
    width: 17px;
    height: 17px;
  }
}

.brand-footer {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 12px;
  color: #71717a;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #d4d4d8;
}

.status-pulse {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.65);
}

.login-split__stage {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: var(--sb-stage-bg, #ffffff);
  overflow-y: auto;
}

.auth-card-wrap {
  width: 100%;
  max-width: 420px;
}

.auth-header {
  margin-bottom: 24px;

  h2 {
    margin: 12px 0 0;
    font-size: 24px;
    font-weight: 750;
    letter-spacing: -0.02em;
    color: var(--sb-text-primary);
  }

  p {
    margin: 6px 0 0;
    font-size: 13.5px;
    color: var(--sb-text-muted);
  }
}

.auth-portal-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 4px 10px;
  border-radius: 9999px;
  background: var(--sb-badge-primary-bg);
  border: 1px solid var(--sb-badge-primary-border);
  color: var(--sb-badge-primary-text);
  font-size: 12px;
  font-weight: 600;

  code {
    font-family: var(--sb-font-mono);
    font-size: 11px;
    opacity: 0.85;
  }
}

.chip-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--sb-primary-color);
}

.auth-error-alert {
  margin-bottom: 16px;
}

.password-label-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.captcha-row {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 128px;
  gap: 10px;
}

.captcha-box {
  position: relative;
  height: 40px;
  border-radius: 8px;
  border: 1px solid var(--sb-border-color);
  background: var(--sb-tier1-bg);
  padding: 0;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.captcha-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.captcha-refresh {
  position: absolute;
  right: 4px;
  bottom: 4px;
  font-size: 12px;
  color: var(--sb-text-muted);
}

.auth-submit-btn {
  width: 100%;
  height: 42px;
  margin-top: 6px;
  font-size: 14.5px;
  font-weight: 600;
}

.demo-section {
  margin-top: 22px;
}

.section-caption {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--sb-text-muted);
}

.demo-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.demo-tile {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 11px;
  border-radius: 10px;
  border: 1px solid var(--sb-border-subtle);
  background: var(--sb-tier1-bg);
  text-align: left;
  cursor: pointer;
  transition: border-color 140ms ease, background 140ms ease;

  &:hover {
    border-color: var(--sb-primary-border);
    background: var(--sb-badge-primary-bg);
  }
}

.demo-badge {
  padding: 3px 6px;
  border-radius: 5px;
  font-family: var(--sb-font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
  flex-shrink: 0;

  &.is-admin {
    background: var(--sb-badge-primary-bg);
    color: var(--sb-badge-primary-text);
  }

  &.is-user {
    background: var(--sb-success-weak);
    color: var(--sb-success-color);
  }
}

.demo-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;

  strong {
    font-size: 12.5px;
    color: var(--sb-text-primary);
  }

  code {
    font-family: var(--sb-font-mono);
    font-size: 11px;
    color: var(--sb-text-muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.sso-section {
  margin-top: 24px;
}

.sso-divider {
  position: relative;
  text-align: center;
  margin-bottom: 14px;

  &::before {
    content: "";
    position: absolute;
    left: 0;
    right: 0;
    top: 50%;
    height: 1px;
    background: var(--sb-border-subtle);
  }

  span {
    position: relative;
    padding: 0 12px;
    background: var(--sb-stage-bg, #ffffff);
    font-size: 12px;
    color: var(--sb-text-muted);
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
  border-radius: 10px;
  border: 1px solid var(--sb-border-subtle);
  background: var(--sb-control-bg);
  color: var(--sb-text-secondary);
  font-size: 11.5px;
  font-weight: 500;
  cursor: pointer;
  transition: border-color 140ms ease, color 140ms ease, background 140ms ease;

  svg {
    width: 17px;
    height: 17px;
    color: var(--sb-primary-color);
  }

  &:hover {
    border-color: var(--sb-primary-border);
    background: var(--sb-badge-primary-bg);
    color: var(--sb-text-primary);
  }
}

@media (max-width: 960px) {
  .login-split {
    grid-template-columns: minmax(0, 1fr);
  }

  .login-split__brand {
    display: none;
  }

  .login-split__stage {
    padding: 24px 18px;
  }

  .demo-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .sso-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
