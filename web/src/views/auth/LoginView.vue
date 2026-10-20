<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { useRoute, useRouter } from "vue-router";
import {
  OfficeBuilding,
  User,
  Lock,
  Key,
  RefreshRight,
  CircleCheckFilled,
  Platform,
  Cpu,
  DataLine
} from "@element-plus/icons-vue";
import { fetchPortalEntry, type PortalEntry } from "@/api/modules/portal";
import { useAuthStore } from "@/stores/modules/auth";
import { rememberPortalContext, resolvePortalContext } from "@/utils/portal-context";
import loginBg from "@/assets/login-bg-light.jpg";

const form = reactive({
  tenantCode: "platform",
  clientId: "",
  terminalCode: "web",
  captchaCode: "",
  username: "admin",
  password: "Admin@123456"
});
const loading = ref(false);
const booting = ref(false);
const portal = ref<PortalEntry | null>(null);
const captchaSeed = ref("");
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

function refreshCaptcha() {
  captchaSeed.value = Math.random().toString(36).slice(2, 6).toUpperCase();
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
    refreshCaptcha();
    rememberPortalContext({ clientId: entry.clientId, terminalCode: entry.terminal.terminalCode });
    if (route.query.clientId !== entry.clientId || route.query.terminalCode !== entry.terminal.terminalCode) {
      await router.replace({
        path: route.path,
        query: {
          ...route.query,
          clientId: entry.clientId,
          terminalCode: entry.terminal.terminalCode
        }
      });
    }
  } catch (error: any) {
    loginError.value = error?.response?.data?.message ?? "门户入口配置加载失败";
    ElMessage.error(loginError.value);
  } finally {
    booting.value = false;
  }
}

function fillDemoAccount() {
  form.tenantCode = "platform";
  form.username = "admin";
  form.password = "Admin@123456";
  if (captchaSeed.value) {
    form.captchaCode = captchaSeed.value;
  }
  ElMessage.success("已自动填入超级管理员默认凭证");
}

function validateForm() {
  if (!form.tenantCode.trim()) {
    loginError.value = "请输入租户编码";
    return false;
  }
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
  return true;
}

async function handleLogin() {
  loginError.value = "";
  if (!validateForm()) {
    ElMessage.error(loginError.value);
    return;
  }
  if (captchaEnabled.value && form.captchaCode.trim().toUpperCase() !== captchaSeed.value) {
    loginError.value = "图形验证码不正确";
    ElMessage.error(loginError.value);
    refreshCaptcha();
    form.captchaCode = "";
    return;
  }
  loading.value = true;
  try {
    await authStore.login({
      tenantCode: form.tenantCode,
      clientId: form.clientId,
      terminalCode: form.terminalCode,
      captchaCode: form.captchaCode,
      username: form.username,
      password: form.password
    });
    const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/dashboard";
    router.push(redirect);
  } catch (error: any) {
    loginError.value = error?.response?.data?.message ?? "登录失败，请检查账号、密码或验证码";
    ElMessage.error(loginError.value);
    if (captchaEnabled.value) {
      refreshCaptcha();
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
    const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/dashboard";
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
        <div class="login-page__logo">
          <img v-if="portal?.logoUrl" :src="portal.logoUrl" alt="" />
          <span v-else>{{ (portal?.portalTitle || "SB").slice(0, 2) }}</span>
        </div>
        <div class="login-page__brand-meta">
          <strong>{{ portal?.portalTitle || "SaaS 基础底座平台" }}</strong>
          <span class="login-page__brand-badge">Enterprise Edition</span>
        </div>
      </div>

      <div class="login-page__header-extras">
        <span class="login-page__status-indicator">
          <span class="status-dot" />
          系统服务正常 · 99.99% SLA
        </span>
        <span v-if="portal" class="login-page__terminal-tag">
          {{ portal.terminal.terminalName }}
        </span>
      </div>
    </header>

    <!-- Main Container: Dual-Pane on Desktop -->
    <main class="login-page__main">
      <div class="login-page__container">
        <!-- Left Hero: Platform Architecture & Values -->
        <section class="login-hero" aria-label="底座特性介绍">
          <div class="login-hero__tag">
            <el-icon><Cpu /></el-icon>
            <span>NEXT-GEN CLOUD FOUNDATION</span>
          </div>

          <h1 class="login-hero__title">
            统一数字化底座<br />
            <span class="login-hero__title--gradient">高隔离企业级多租户引擎</span>
          </h1>

          <p class="login-hero__desc">
            基于微内核架构打造的标准云原生底座，提供全链路租户生命周期隔离、行级数据权限规则与商业化配额治理能力。
          </p>

          <div class="login-hero__features">
            <div class="hero-feature-card">
              <div class="hero-feature-card__icon hero-feature-card__icon--blue">
                <el-icon><Platform /></el-icon>
              </div>
              <div class="hero-feature-card__content">
                <strong>云原生多租户隔离</strong>
                <p>独立 Schema 与共享模式动态路由，支持实时订阅套餐与席位硬配额防穿透</p>
              </div>
            </div>

            <div class="hero-feature-card">
              <div class="hero-feature-card__icon hero-feature-card__icon--emerald">
                <el-icon><CircleCheckFilled /></el-icon>
              </div>
              <div class="hero-feature-card__content">
                <strong>统一安全与行级权限</strong>
                <p>细粒度 RBAC 授权、SQL 级行级数据权限拦截与全链路安全操作审计</p>
              </div>
            </div>

            <div class="hero-feature-card">
              <div class="hero-feature-card__icon hero-feature-card__icon--cyan">
                <el-icon><DataLine /></el-icon>
              </div>
              <div class="hero-feature-card__content">
                <strong>高可用自动化运营</strong>
                <p>集成工作流自动化审批、Quartz 调度管道与多源连接引擎</p>
              </div>
            </div>
          </div>
        </section>

        <!-- Right: Glassmorphism Login Card -->
        <section class="login-panel" aria-label="登录">
          <header class="login-panel__header">
            <div>
              <h2>欢迎登录</h2>
              <p class="login-panel__subtitle">请输入您的租户账号凭证以访问控制台</p>
            </div>
            <span v-if="portal" class="login-panel__client-badge">{{ portal.clientName }}</span>
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

              <el-form-item label="租户编码">
                <el-input
                  v-model="form.tenantCode"
                  placeholder="例如: platform 或租户标识"
                  :prefix-icon="OfficeBuilding"
                />
              </el-form-item>

              <el-form-item label="用户名">
                <el-input
                  v-model="form.username"
                  placeholder="用户名 / 手机号 / 邮箱"
                  autocomplete="username"
                  :prefix-icon="User"
                  @keyup.enter="handleLogin"
                />
              </el-form-item>

              <el-form-item label="密码">
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
                    placeholder="请输入验证码"
                    :prefix-icon="Key"
                    @keyup.enter="handleLogin"
                  />
                  <button
                    type="button"
                    class="login-page__captcha-code"
                    title="点击刷新验证码"
                    @click="refreshCaptcha"
                  >
                    <span>{{ captchaSeed }}</span>
                    <el-icon class="captcha-refresh-icon"><RefreshRight /></el-icon>
                  </button>
                </div>
              </el-form-item>

              <el-button
                type="primary"
                native-type="submit"
                class="login-page__submit"
                :loading="loading"
              >
                {{ loading ? "正在安全验证..." : "登 录" }}
              </el-button>

              <div class="login-page__security-hint">
                <span>🔒 启用 256 位端到端加密传输 · 账号安全策略已防护</span>
                <span class="demo-acc-hint" @click="fillDemoAccount" style="cursor: pointer;" title="点击一键填入演示凭据">
                  演示体验默认超级管理员账号：<strong style="color: #2563eb; text-decoration: underline;">admin / Admin@123456</strong> (点击自动填入)
                </span>
              </div>

              <!-- 企业 SSO 快捷单点登录通道 -->
              <div class="login-sso-section">
                <div class="sso-divider">
                  <span class="sso-divider-line" />
                  <span class="sso-divider-text">企业社交与单点登录 (SSO)</span>
                  <span class="sso-divider-line" />
                </div>

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

  position: relative;
  min-height: 100vh;
  display: grid;
  grid-template-rows: 64px minmax(0, 1fr) 48px;
  background:
    linear-gradient(135deg, rgb(248 250 252 / 0.88) 0%, rgb(241 245 249 / 0.78) 50%, rgb(248 250 252 / 0.92) 100%),
    var(--portal-bg-image) center/cover no-repeat,
    var(--portal-bg-color);
  overflow-x: hidden;
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
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 36px;
  border-bottom: 1px solid rgb(226 232 240 / 0.8);
  background: rgb(255 255 255 / 0.75);
  backdrop-filter: blur(16px);
}

.login-page__brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.login-page__logo {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  overflow: hidden;
  background: linear-gradient(135deg, #0284c7, #2563eb);
  color: #fff;
  font-size: 14px;
  font-weight: 800;
  box-shadow: 0 4px 12px rgb(2 132 199 / 0.3);

  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }
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
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
}

.login-page__container {
  width: 100%;
  max-width: 1200px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 64px;
}

/* Left Hero Section */
.login-hero {
  flex: 1;
  max-width: 600px;

  &__tag {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 6px 14px;
    border-radius: 20px;
    background: #eff6ff;
    border: 1px solid #dbeafe;
    color: #2563eb;
    font-size: 12px;
    font-weight: 700;
    letter-spacing: 1.2px;
    margin-bottom: 20px;
  }

  &__title {
    font-size: 38px;
    font-weight: 800;
    line-height: 1.28;
    margin: 0 0 16px;
    color: #0f172a;
    letter-spacing: -0.5px;

    &--gradient {
      background: linear-gradient(135deg, #0284c7 0%, #2563eb 50%, #4f46e5 100%);
      background-clip: text;
      -webkit-text-fill-color: transparent;
    }
  }

  &__desc {
    font-size: 15px;
    line-height: 1.65;
    color: #475569;
    margin: 0 0 32px;
  }

  &__features {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
}

.hero-feature-card {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px 20px;
  border-radius: 12px;
  background: rgb(255 255 255 / 0.85);
  border: 1px solid rgb(226 232 240 / 0.9);
  backdrop-filter: blur(12px);
  box-shadow: 0 4px 16px rgb(15 23 42 / 0.03);
  transition: all 0.25s ease;

  &:hover {
    border-color: #bae6fd;
    background: #fff;
    transform: translateX(4px);
    box-shadow: 0 8px 24px rgb(2 132 199 / 0.08);
  }

  &__icon {
    width: 38px;
    height: 38px;
    border-radius: 10px;
    display: grid;
    place-items: center;
    font-size: 18px;
    flex-shrink: 0;

    &--blue {
      background: #e0f2fe;
      color: #0284c7;
      border: 1px solid #bae6fd;
    }

    &--emerald {
      background: #dcfce7;
      color: #059669;
      border: 1px solid #a7f3d0;
    }

    &--cyan {
      background: #e0e7ff;
      color: #4f46e5;
      border: 1px solid #c7d2fe;
    }
  }

  &__content {
    strong {
      display: block;
      font-size: 15px;
      color: #0f172a;
      margin-bottom: 4px;
    }

    p {
      margin: 0;
      font-size: 13px;
      color: #64748b;
      line-height: 1.5;
    }
  }
}

/* Right Login Card (Clean Light Luxury Glass) */
.login-panel {
  width: 100%;
  max-width: 440px;
  padding: 36px 36px 32px;
  border: 1px solid rgb(226 232 240 / 0.95);
  border-radius: 16px;
  background: rgb(255 255 255 / 0.88);
  backdrop-filter: blur(24px);
  box-shadow:
    0 20px 40px -15px rgb(15 23 42 / 0.08),
    0 0 0 1px rgb(255 255 255 / 0.8) inset;

  &__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 26px;

    h2 {
      margin: 0 0 6px;
      font-size: 24px;
      font-weight: 700;
      color: #0f172a;
    }
  }

  &__subtitle {
    margin: 0;
    font-size: 13px;
    color: #64748b;
  }

  &__client-badge {
    padding: 3px 10px;
    border-radius: 6px;
    background: #e0f2fe;
    border: 1px solid #bae6fd;
    color: #0284c7;
    font-size: 12px;
    font-weight: 500;
    white-space: nowrap;
  }
}

/* Form Styles */
.login-panel :deep(.el-form-item) {
  margin-bottom: 20px;
}

.login-panel :deep(.el-form-item__label) {
  margin-bottom: 6px;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
}

.login-panel :deep(.el-input__wrapper) {
  min-height: 44px;
  background: #f8fafc;
  box-shadow: 0 0 0 1px #cbd5e1 inset;
  border-radius: 8px;
  transition: all 0.2s ease;

  &:hover {
    box-shadow: 0 0 0 1px #94a3b8 inset;
    background: #fff;
  }

  &.is-focus {
    background: #fff;
    box-shadow: 0 0 0 2px #0284c7 inset, 0 0 10px rgb(2 132 199 / 0.15) !important;
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
  margin-bottom: 20px;
}

/* Captcha */
.login-page__captcha {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 110px;
  gap: 10px;
}

.login-page__captcha-code {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #f1f5f9;
  color: #0284c7;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 16px;
  font-weight: 800;
  letter-spacing: 3px;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;

  &:hover {
    border-color: #0284c7;
    background: #e0f2fe;
    box-shadow: 0 2px 8px rgb(2 132 199 / 0.15);

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
  height: 46px;
  margin-top: 6px;
  border-radius: 8px;
  border: none;
  background: linear-gradient(135deg, #0284c7 0%, #2563eb 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 2px;
  box-shadow: 0 4px 14px rgb(2 132 199 / 0.35);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    background: linear-gradient(135deg, #0369a1 0%, #1d4ed8 100%);
    box-shadow: 0 6px 18px rgb(2 132 199 / 0.45);
    transform: translateY(-1px);
  }

  &:active {
    transform: translateY(1px);
  }
}

/* Security Note */
.login-page__security-hint {
  margin-top: 20px;
  text-align: center;
  font-size: 12px;
  color: #64748b;
  line-height: 1.4;
  display: flex;
  flex-direction: column;
  gap: 4px;

  .demo-acc-hint {
    font-size: 11px;
    color: #0284c7;
    background: #f0f9ff;
    border: 1px solid #bae6fd;
    padding: 3px 8px;
    border-radius: 4px;
    display: inline-block;
    align-self: center;
  }
}

/* SSO Login Section */
.login-sso-section {
  margin-top: 24px;
}

.sso-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;

  .sso-divider-line {
    flex: 1;
    height: 1px;
    background: #e2e8f0;
  }

  .sso-divider-text {
    font-size: 12px;
    color: #94a3b8;
    white-space: nowrap;
  }
}

.sso-buttons {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.sso-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 9px 8px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #334155;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: #cbd5e1;
    background: #f8fafc;
    transform: translateY(-1px);
    box-shadow: 0 2px 6px rgb(0 0 0 / 0.04);
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
    font-size: 15px;
  }

  .sso-label {
    font-size: 12px;
  }
}

/* Footer */
.login-page__footer {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
  border-top: 1px solid rgb(226 232 240 / 0.8);
  background: rgb(255 255 255 / 0.7);
  backdrop-filter: blur(12px);

  .footer-divider {
    color: #cbd5e1;
  }
}

/* Responsive */
@media (max-width: 1024px) {
  .login-page__container {
    flex-direction: column;
    justify-content: center;
    gap: 36px;
  }

  .login-hero {
    max-width: 500px;
    text-align: center;

    &__tag {
      margin: 0 auto 16px;
    }

    &__title {
      font-size: 30px;
    }

    &__desc {
      margin-bottom: 20px;
    }

    &__features {
      display: none;
    }
  }

  .login-panel {
    max-width: 440px;
  }
}

@media (max-width: 640px) {
  .login-page__header {
    padding: 0 16px;
  }

  .login-page__header-extras {
    display: none;
  }

  .login-hero {
    display: none;
  }

  .login-panel {
    padding: 28px 20px;
  }
}
</style>
