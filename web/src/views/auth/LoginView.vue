<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { useRoute, useRouter } from "vue-router";
import {
  User,
  Lock,
  Key,
  RefreshRight
} from "@element-plus/icons-vue";
import { fetchPortalEntry, type PortalEntry } from "@/api/modules/portal";
import { fetchAuthCaptcha } from "@/api/modules/auth";
import { useAuthStore } from "@/stores/modules/auth";
import {
  rememberPortalContext,
  resolvePortalContext,
  extractSubdomainTenant
} from "@/utils/portal-context";
import loginBg from "@/assets/ios27-liquid-glass-bg.jpg";
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

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const portalThemeStyle = computed(() => ({
  "--portal-bg-color": portal.value?.backgroundColor || "#f8fafc",
  "--portal-bg-image": portal.value?.backgroundImageUrl
    ? `url(${portal.value.backgroundImageUrl})`
    : `url(${loginBg})`,
  "--portal-accent": resolveThemeColor(portal.value?.themeCode)
}));

const captchaEnabled = computed(() => portal.value?.captcha.mode === "IMAGE");

function resolveThemeColor(themeCode?: string) {
  switch ((themeCode || "").toLowerCase()) {
    case "emerald":
      return "#10b981";
    case "amber":
      return "#f59e0b";
    case "graphite":
      return "#475569";
    default:
      return "#0284c7";
  }
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
    const currentQuery: Record<string, any> = { ...route.query };
    let needsUpdate = false;
    // 只有在非二级子域名模式下，才把 tenantCode 同步至 query 参数
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
        query: currentQuery
      });
    }
  } catch (error: any) {
    loginError.value = error?.response?.data?.message ?? "门户入口配置加载失败";
    ElMessage.error(loginError.value);
  } finally {
    booting.value = false;
  }
}

function fillDemoAccount(type: "admin" | "user" = "admin") {
  form.tenantCode = portal.value?.tenantCode || (route.query.tenantCode as string) || (route.query.tenant as string) || "platform";
  if (type === "user") {
    form.username = "user";
    form.password = "User@123456";
    form.captchaCode = "";
    ElMessage.success(`已填入员工凭证 (user，专属企业: ${portal.value?.clientName || form.tenantCode})`);
  } else {
    form.username = "admin";
    form.password = "Admin@123456";
    form.captchaCode = "";
    ElMessage.success(`已填入管理员凭据 (admin，专属企业: ${portal.value?.clientName || form.tenantCode})`);
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
    router.push(redirect);
  } catch (error: any) {
    const errCode = error?.response?.data?.code;
    const errMsg = error?.response?.data?.message ?? "登录失败，请检查账号、密码或验证码";
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
    const channelName = channel === "WECOM" ? "企业微信" : channel === "DINGTALK" ? "钉钉" : channel === "FEISHU" ? "飞书" : "SAML/OIDC 身份联合";
    ElMessage.info(`正在通过【${channelName}】进行企业 SSO 单点登录...`);
    await authStore.loginWithSso({
      channel,
      authCode: `sso_${channel.toLowerCase()}_mock_${Date.now()}`,
      tenantCode: form.tenantCode.trim() || "platform"
    });
    ElMessage.success(`${channelName} SSO 鉴权成功，正在进入工作台...`);
    const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/";
    router.push(redirect);
  } catch (err: any) {
    loginError.value = err?.response?.data?.message || err?.message || "SSO 登录失败";
    ElMessage.error(loginError.value);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void bootstrapPortal();
});
</script>

<template>
  <div class="login-page" :style="portalThemeStyle">
    <!-- Ambient ambient glow decorations -->
    <div class="login-glow login-glow--top" aria-hidden="true" />
    <div class="login-glow login-glow--bottom" aria-hidden="true" />

    <!-- Top Navigation Header -->
    <header class="login-page__header">
      <div class="login-page__brand">
        <TianshuLogo :size="32" />
        <div class="login-page__brand-meta">
          <strong>{{ portal?.portalTitle || "天枢企业协同管理平台" }}</strong>
          <span class="login-page__brand-badge">企业标准版</span>
        </div>
      </div>

      <div class="login-page__header-extras">
        <span class="login-page__status-indicator">
          <span class="status-dot" />
          系统运行正常 · 数据安全受控
        </span>
        <span v-if="portal" class="login-page__terminal-tag">
          {{ portal.terminal.terminalName }}
        </span>
      </div>
    </header>

    <!-- Main Container: Centered Flagship Card -->
    <main class="login-page__main">
      <div class="login-page__container">
        <!-- Glassmorphism Login Card -->
        <section class="login-panel" aria-label="登录">
          <header class="login-panel__header">
            <div class="login-brand-capsule-wrap">
              <div class="login-brand-capsule">
                <div class="capsule-logo-badge">
                  <TianshuLogo :size="18" />
                </div>
                <span class="capsule-brand-name">天枢云基</span>
                <span class="capsule-sep" />
                <span class="capsule-portal-name">{{ portal?.clientName || "平台统一门户" }}</span>
              </div>
            </div>

            <h2 class="login-panel__title">欢迎登录</h2>
          </header>

          <el-skeleton :loading="booting" animated>
            <template #template>
              <el-skeleton-item variant="rect" style="height: 320px; border-radius: 8px" />
            </template>

            <el-form label-position="top" @submit.prevent="handleLogin">
              <el-alert
                v-if="loginError"
                class="login-page__error"
                type="error"
                :closable="false"
                :title="loginError"
              />

              <el-form-item label="账号凭证">
                <el-input
                  v-model="form.username"
                  placeholder="用户名 / 手机号 / 企业邮箱"
                  autocomplete="username"
                  :prefix-icon="User"
                  @keyup.enter="handleLogin"
                />
              </el-form-item>

              <el-form-item label="访问密码">
                <el-input
                  v-model="form.password"
                  type="password"
                  show-password
                  placeholder="请输入登录密码"
                  autocomplete="current-password"
                  :prefix-icon="Lock"
                  @keyup.enter="handleLogin"
                />
              </el-form-item>

              <el-form-item v-if="captchaEnabled" label="安全验证码">
                <div class="login-page__captcha">
                  <el-input
                    v-model="form.captchaCode"
                    placeholder="请输入图形验证码"
                    :prefix-icon="Key"
                    @keyup.enter="handleLogin"
                  />
                  <button
                    type="button"
                    class="login-page__captcha-code"
                    title="点击刷新验证码"
                    @click="refreshCaptcha"
                  >
                    <img
                      v-if="captchaImageUrl"
                      :src="captchaImageUrl"
                      alt="安全验证码"
                      class="captcha-svg-img"
                    />
                    <span v-else class="captcha-loading-text">刷新</span>
                    <el-icon class="captcha-refresh-icon"><RefreshRight /></el-icon>
                  </button>
                </div>
              </el-form-item>

              <el-form-item v-if="mfaRequired" label="MFA 动态口令 / 恢复码">
                <el-input
                  v-model="form.totpCode"
                  placeholder="请输入 Authenticator 6 位数字或备用恢复码"
                  maxlength="16"
                  :prefix-icon="Lock"
                  @keyup.enter="handleLogin"
                />
              </el-form-item>

              <el-button
                type="primary"
                native-type="submit"
                class="login-page__submit"
                :loading="loading"
              >
                {{ loading ? "正在安全验证..." : "登 录" }}
              </el-button>

              <div class="demo-acc-wrapper" style="margin-top: 14px; display: flex; gap: 8px; justify-content: center; flex-wrap: wrap;">
                <el-tag
                  effect="plain"
                  type="primary"
                  style="cursor: pointer;"
                  title="点击填入超管凭证"
                  @click="fillDemoAccount('admin')"
                >
                  👑 超管: admin / Admin@123456
                </el-tag>
                <el-tag
                  effect="plain"
                  type="success"
                  style="cursor: pointer;"
                  title="点击填入测试凭证"
                  @click="fillDemoAccount('user')"
                >
                  👤 普通用户: user / User@123456
                </el-tag>
              </div>

              <!-- 企业 SSO 快捷单点登录通道 -->
              <div class="login-sso-section">
                <div class="sso-divider-line" />

                <div class="sso-buttons">
                  <button
                    type="button"
                    class="sso-btn sso-btn--wecom"
                    title="企业微信扫码登录"
                    @click="handleSsoLogin('WECOM')"
                  >
                    <span class="sso-icon">💬</span>
                    <span class="sso-label">企业微信</span>
                  </button>

                  <button
                    type="button"
                    class="sso-btn sso-btn--dingtalk"
                    title="钉钉快捷登录"
                    @click="handleSsoLogin('DINGTALK')"
                  >
                    <span class="sso-icon">⚡</span>
                    <span class="sso-label">钉钉</span>
                  </button>

                  <button
                    type="button"
                    class="sso-btn sso-btn--feishu"
                    title="飞书统一授权"
                    @click="handleSsoLogin('FEISHU')"
                  >
                    <span class="sso-icon">🕊️</span>
                    <span class="sso-label">飞书</span>
                  </button>

                  <button
                    type="button"
                    class="sso-btn sso-btn--oidc"
                    title="企业身份联邦 (SAML 2.0 / OIDC)"
                    @click="handleSsoLogin('SAML_OIDC')"
                  >
                    <span class="sso-icon">🔑</span>
                    <span class="sso-label">SAML/OIDC</span>
                  </button>
                </div>
              </div>
            </el-form>
          </el-skeleton>
        </section>
      </div>
    </main>

    <!-- Bottom Footer -->
    <footer class="login-page__footer">
      <span v-if="portal?.filingInfo">{{ portal.filingInfo }}</span>
      <span v-else>SaaS Basics Enterprise Platform</span>
      <span class="footer-divider">·</span>
      <span>© 2026 SaaS Basics Platform. All rights reserved.</span>
    </footer>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  --portal-bg-color: #f8fafc;
  --portal-bg-image: none;
  --portal-accent: #0284c7;

  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100vw;
  height: 100vh;
  height: 100dvh;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background:
    radial-gradient(ellipse 80% 50% at 50% -10%, rgba(191, 219, 254, 0.40), transparent 75%),
    var(--portal-bg-image) center center / cover no-repeat fixed,
    var(--portal-bg-color);
  overflow: hidden;
  color: #0f172a;
}

/* Atmospheric ambient glows */
.login-glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(120px);
  z-index: 0;
  opacity: 0.55;

  &--top {
    top: -100px;
    left: 8%;
    width: 520px;
    height: 520px;
    background: radial-gradient(circle, rgb(14 165 233 / 0.22) 0%, transparent 70%);
  }

  &--bottom {
    bottom: -120px;
    right: 5%;
    width: 600px;
    height: 600px;
    background: radial-gradient(circle, rgb(99 102 241 / 0.18) 0%, transparent 70%);
  }
}

/* Header */
.login-page__header {
  height: 56px;
  flex-shrink: 0;
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 32px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.90);
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  box-shadow: 0 2px 16px rgba(15, 23, 42, 0.04);
}

.login-page__brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.login-page__brand-meta {
  display: flex;
  align-items: center;
  gap: 10px;

  strong {
    font-size: 16px;
    font-weight: 700;
    color: #0f172a;
    letter-spacing: 0.3px;
  }
}

.login-page__brand-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 12px;
  background: #e0f2fe;
  border: 1px solid #bae6fd;
  color: #0284c7;
  font-weight: 600;
}

.login-page__header-extras {
  display: flex;
  align-items: center;
  gap: 18px;
}

.login-page__status-indicator {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #475569;
  background: rgb(241 245 249 / 0.85);
  padding: 4px 12px;
  border-radius: 20px;
  border: 1px solid #e2e8f0;

  .status-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #10b981;
    box-shadow: 0 0 6px #10b981;
    animation: pulse-dot 2s infinite ease-in-out;
  }
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.85); }
}

.login-page__terminal-tag {
  padding: 3px 10px;
  border-radius: 6px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  color: #475569;
  font-size: 12px;
  font-weight: 500;
}

/* Main Container */
.login-page__main {
  flex: 1;
  min-height: 0;
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 24px;
  overflow-y: auto;
}

.login-page__container {
  width: 100%;
  max-width: 440px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin: auto;
}

/* Flagship Login Card (VisionOS / iOS 27 Liquid Titanium Glass) */
.login-panel {
  width: 100%;
  max-width: 440px;
  padding: 30px 32px 24px;
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.74);
  backdrop-filter: blur(48px) saturate(220%) contrast(102%);
  -webkit-backdrop-filter: blur(48px) saturate(220%) contrast(102%);
  box-shadow:
    inset 0 2px 3px 0 rgba(255, 255, 255, 1),
    inset 0 -1px 2px 0 rgba(255, 255, 255, 0.5),
    0 24px 50px -12px rgba(15, 23, 42, 0.12),
    0 4px 16px -2px rgba(15, 23, 42, 0.04);

  &__header {
    margin-bottom: 20px;
    text-align: center;
  }

  &__title {
    margin: 8px 0 0;
    font-size: 23px;
    font-weight: 750;
    color: #0f172a;
    letter-spacing: -0.02em;
  }
}

.login-brand-capsule-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.login-brand-capsule {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px 4px 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.05), inset 0 1px 0 rgba(255, 255, 255, 1);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);

  .capsule-logo-badge {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: linear-gradient(135deg, rgba(37, 99, 235, 0.12), rgba(59, 130, 246, 0.04));
    flex-shrink: 0;
  }

  .capsule-brand-name {
    font-size: 13.5px;
    font-weight: 700;
    color: #0f172a;
    letter-spacing: -0.01em;
  }

  .capsule-sep {
    width: 1px;
    height: 12px;
    background: rgba(148, 163, 184, 0.45);
    flex-shrink: 0;
  }

  .capsule-portal-name {
    font-size: 12.5px;
    font-weight: 600;
    color: #2563eb;
    letter-spacing: -0.01em;
  }
}

/* Form Styles */
.login-panel :deep(.el-form-item) {
  margin-bottom: 14px;
}

.login-panel :deep(.el-form-item__label) {
  margin-bottom: 4px;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.3;
}

.login-panel :deep(.el-input__wrapper) {
  min-height: 42px;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: 0 0 0 1px rgba(203, 213, 225, 0.8) inset, inset 0 1px 2px rgba(15, 23, 42, 0.03);
  border-radius: 12px;
  backdrop-filter: blur(12px);
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);

  &:hover {
    box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.9) inset;
    background: rgba(255, 255, 255, 0.95);
  }

  &.is-focus {
    background: #ffffff;
    box-shadow: 0 0 0 2px #2563eb inset, 0 0 12px rgba(37, 99, 235, 0.2) !important;
  }
}

.login-panel :deep(.el-input__inner) {
  color: #0f172a;
  font-size: 14px;

  &::placeholder {
    color: #94a3b8;
  }
}

.login-panel :deep(.el-input__prefix-inner) {
  color: #64748b;
  font-size: 16px;
}

.login-page__error {
  margin-bottom: 14px;
}

/* Captcha */
.login-page__captcha {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 128px;
  gap: 10px;
}

.login-page__captcha-code {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 2px;
  overflow: hidden;
  border: 1px solid rgba(203, 213, 225, 0.9);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.92);
  color: #2563eb;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;

  .captcha-svg-img {
    width: 102px;
    height: 36px;
    display: block;
    border-radius: 8px;
  }

  .captcha-loading-text {
    font-size: 12px;
    letter-spacing: 0;
  }

  &:hover {
    border-color: #2563eb;
    background: #eff6ff;
    box-shadow: 0 2px 10px rgba(37, 99, 235, 0.18);

    .captcha-refresh-icon {
      transform: rotate(180deg);
    }
  }

  .captcha-refresh-icon {
    font-size: 13px;
    color: #64748b;
    transition: transform 0.3s ease;
  }
}

/* Submit Button */
.login-page__submit {
  width: 100%;
  height: 44px;
  margin-top: 4px;
  border-radius: 12px;
  border: none;
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 2px;
  box-shadow: 0 6px 18px rgba(37, 99, 235, 0.32), inset 0 1px 1px rgba(255, 255, 255, 0.35);
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);

  &:hover {
    background: linear-gradient(135deg, #1d4ed8 0%, #1e40af 100%);
    box-shadow: 0 8px 22px rgba(37, 99, 235, 0.42), inset 0 1px 1px rgba(255, 255, 255, 0.5);
    transform: translateY(-1px);
  }

  &:active {
    transform: translateY(1px);
  }
}

/* SSO Login Section */
.login-sso-section {
  margin-top: 14px;
}

.sso-divider-line {
  height: 1px;
  background: rgba(226, 232, 240, 0.85);
  margin-bottom: 12px;
}

.sso-buttons {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.sso-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 8px 4px;
  border-radius: 10px;
  border: 1px solid rgba(226, 232, 240, 0.85);
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(12px);
  color: #334155;
  font-size: 12px;
  font-weight: 550;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);

  &:hover {
    border-color: rgba(148, 163, 184, 0.6);
    background: #fff;
    transform: translateY(-1px);
    box-shadow: 0 4px 14px rgba(15, 23, 42, 0.08);
  }

  &--wecom:hover {
    border-color: #22c55e;
    color: #15803d;
  }

  &--dingtalk:hover {
    border-color: #3b82f6;
    color: #1d4ed8;
  }

  &--feishu:hover {
    border-color: #06b6d4;
    color: #0e7490;
  }

  .sso-icon {
    font-size: 14px;
  }

  .sso-label {
    font-size: 11px;
    white-space: nowrap;
  }
}

/* Footer */
.login-page__footer {
  height: 38px;
  flex-shrink: 0;
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.85);
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(24px) saturate(200%);
  -webkit-backdrop-filter: blur(24px) saturate(200%);
  box-sizing: border-box;

  .footer-divider {
    color: #cbd5e1;
  }
}

/* Responsive */
@media (max-width: 640px) {
  .login-page__header {
    padding: 0 16px;
  }

  .login-page__header-extras {
    display: none;
  }

  .login-panel {
    padding: 24px 20px 20px;
    border-radius: 18px;
  }
}
</style>
