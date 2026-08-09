<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { useRoute, useRouter } from "vue-router";
import BaseCard from "@/components/base/BaseCard.vue";
import { fetchPortalEntry } from "@/api/modules/portal";
import type { PortalEntry } from "@/api/modules/portal";
import { useAuthStore } from "@/stores/modules/auth";

const form = reactive({
  tenantCode: "platform",
  clientId: "",
  terminalCode: "web",
  captchaCode: "",
  username: "",
  password: ""
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
  "--portal-bg-color": portal.value?.backgroundColor || "#0f2740",
  "--portal-bg-image": portal.value?.backgroundImageUrl ? `url(${portal.value.backgroundImageUrl})` : "none",
  "--portal-accent-start": resolveThemePalette(portal.value?.themeCode).start,
  "--portal-accent-end": resolveThemePalette(portal.value?.themeCode).end,
  "--portal-accent-soft": resolveThemePalette(portal.value?.themeCode).soft
}));

const captchaEnabled = computed(() => portal.value?.captcha.mode === "IMAGE");
const sliderReserved = computed(() => portal.value?.captcha.sliderReserved ?? false);
const channelSummary = computed(() => {
  if (!portal.value) {
    return [];
  }
  const channels = [];
  if (portal.value.loginPolicy.allowPasswordLogin) channels.push("密码登录");
  if (portal.value.loginPolicy.allowSmsLogin) channels.push("短信登录");
  if (portal.value.loginPolicy.allowEmailLogin) channels.push("邮箱登录");
  if (portal.value.loginPolicy.allowSocialLogin) channels.push("社交登录");
  return channels;
});

function resolveThemePalette(themeCode?: string) {
  switch ((themeCode || "").toLowerCase()) {
    case "emerald":
      return {
        start: "#0f9d72",
        end: "#7be0b3",
        soft: "rgba(15, 157, 114, 0.08)"
      };
    case "amber":
      return {
        start: "#d48614",
        end: "#ffcb71",
        soft: "rgba(212, 134, 20, 0.1)"
      };
    case "graphite":
      return {
        start: "#2c3f55",
        end: "#8597ad",
        soft: "rgba(44, 63, 85, 0.1)"
      };
    default:
      return {
        start: "#2e7dff",
        end: "#8caeff",
        soft: "rgba(46, 125, 255, 0.1)"
      };
  }
}

function refreshCaptcha() {
  captchaSeed.value = Math.random().toString(36).slice(2, 6).toUpperCase();
}

async function bootstrapPortal() {
  booting.value = true;
  try {
    const clientId = typeof route.query.clientId === "string" ? route.query.clientId : undefined;
    const terminalCode = typeof route.query.terminalCode === "string" ? route.query.terminalCode : "web";
    const entry = await fetchPortalEntry({ clientId, terminalCode });
    portal.value = entry;
    form.clientId = entry.clientId;
    form.terminalCode = entry.terminal.terminalCode;
    form.tenantCode = entry.tenantCode;
    document.title = `${entry.portalTitle} | 登录`;
    loginError.value = "";
    refreshCaptcha();
  } catch (error: any) {
    loginError.value = error?.response?.data?.message ?? "门户入口配置加载失败";
    ElMessage.error(loginError.value);
  } finally {
    booting.value = false;
  }
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

onMounted(() => {
  void bootstrapPortal();
});
</script>

<template>
  <div class="login-page" :style="portalThemeStyle">
    <div class="login-page__hero">
      <p class="login-page__eyebrow">{{ portal?.clientName || "门户入口" }}</p>
      <h1>{{ portal?.welcomeTitle || "统一门户入口" }}</h1>
      <p class="login-page__copy">{{ portal?.welcomeText || "客户端、终端、登录策略和密码策略统一在入口层编排。" }}</p>
      <div class="login-page__signals" v-if="portal">
        <span>{{ portal.terminal.terminalName }}</span>
        <span>租户：{{ portal.tenantCode }}</span>
        <span>会话：{{ portal.loginPolicy.sessionTimeoutMinutes }} 分钟</span>
        <span>密码：{{ portal.passwordPolicy.minLength }}-{{ portal.passwordPolicy.maxLength }} 位</span>
      </div>
    </div>

    <BaseCard class="login-page__panel">
      <template #header>
        <div class="login-panel__header">
          <div class="login-panel__brand">
            <div class="login-panel__logo">
              <img v-if="portal?.logoUrl" :src="portal.logoUrl" alt="portal logo" />
              <span v-else>{{ (portal?.portalTitle || "SB").slice(0, 2) }}</span>
            </div>
            <div>
              <h2>{{ portal?.portalTitle || "登录系统" }}</h2>
              <p>入口配置、终端类型、登录策略和密码策略都由服务端动态下发。</p>
            </div>
          </div>
          <el-tag v-if="portal" type="info" round>{{ portal.clientId }}</el-tag>
        </div>
      </template>

      <el-skeleton :loading="booting" animated>
        <template #template>
          <el-skeleton-item variant="rect" style="height: 360px; border-radius: 24px" />
        </template>
        <el-form label-position="top" @submit.prevent="handleLogin">
          <div class="login-panel__badges" v-if="portal">
            <el-tag v-for="item in channelSummary" :key="item" round>{{ item }}</el-tag>
            <el-tag v-if="portal.loginPolicy.forceMfa" type="warning" round>强制 MFA</el-tag>
            <el-tag type="success" round>策略：{{ portal.loginPolicy.policyName }}</el-tag>
          </div>

          <el-alert
            v-if="loginError"
            class="login-page__error"
            type="error"
            :closable="false"
            :title="loginError"
          />

        <el-form-item label="租户编码">
          <el-input v-model="form.tenantCode" placeholder="请输入租户编码" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item v-if="captchaEnabled" label="图形验证码">
          <div class="login-page__captcha">
            <el-input v-model="form.captchaCode" placeholder="请输入图形验证码" @keyup.enter="handleLogin" />
            <button type="button" class="login-page__captcha-code" @click="refreshCaptcha">{{ captchaSeed }}</button>
          </div>
        </el-form-item>
        <div v-if="sliderReserved" class="login-page__reserved">
          <strong>滑块验证</strong>
          <p>当前终端保留滑块校验接入位，第一版仍以图形验证码为准。</p>
        </div>
        <el-alert
          type="info"
          :closable="false"
          title="使用真实门户账号登录"
          description="账号、密码、权限和菜单都从服务端真实数据链路加载；如登录失败，请检查门户、租户、用户与策略配置。"
        />
        <el-button type="primary" class="login-page__submit" :loading="loading" @click="handleLogin">
          进入控制台
        </el-button>
        <p v-if="portal?.filingInfo" class="login-page__filing">{{ portal.filingInfo }}</p>
        </el-form>
      </el-skeleton>
    </BaseCard>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  --portal-bg-color: #0f2740;
  --portal-bg-image: none;
  --portal-accent-start: #2e7dff;
  --portal-accent-end: #8caeff;
  --portal-accent-soft: rgb(46 125 255 / 0.1);

  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.2fr minmax(360px, 480px);
  background:
    radial-gradient(circle at top left, rgb(94 194 255 / 0.22), transparent 30%),
    var(--portal-bg-image) center/cover no-repeat,
    linear-gradient(135deg, color-mix(in srgb, var(--portal-bg-color) 92%, black) 0%, var(--portal-bg-color) 50%, #edf3f8 50%, #edf3f8 100%);
}

.login-page__hero {
  padding: 72px;
  color: #eff7ff;
  display: flex;
  flex-direction: column;
  justify-content: center;

  h1 {
    margin: 0;
    font-size: clamp(42px, 6vw, 68px);
    line-height: 0.95;
    max-width: 720px;
  }
}

.login-page__eyebrow {
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #8df2d7;
}

.login-page__copy {
  max-width: 560px;
  margin-top: 20px;
  color: rgb(239 247 255 / 0.82);
  font-size: 18px;
  line-height: 1.7;
}

.login-page__signals {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;

  span {
    padding: 8px 12px;
    border-radius: 999px;
    background: rgb(255 255 255 / 0.08);
    color: rgb(239 247 255 / 0.9);
    font-size: 12px;
  }
}

.login-page__panel {
  margin: 32px;
  align-self: center;
}

.login-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.login-panel__brand {
  display: flex;
  gap: 14px;

  h2 {
    margin: 0;
  }

  p {
    margin: 6px 0 0;
    color: var(--sb-text-secondary);
  }
}

.login-panel__logo {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 16px;
  overflow: hidden;
  background: linear-gradient(135deg, var(--portal-accent-start), var(--portal-accent-end));
  color: white;
  font-weight: 700;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  span {
    display: grid;
    place-items: center;
    width: 100%;
    height: 100%;
  }
}

.login-panel__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.login-page__error {
  margin-bottom: 14px;
}

.login-page__captcha {
  display: grid;
  grid-template-columns: 1fr 116px;
  gap: 12px;
  width: 100%;
}

.login-page__captcha-code {
  border: 1px solid var(--sb-border-color);
  border-radius: 14px;
  background: linear-gradient(135deg, var(--portal-accent-soft), rgb(255 255 255 / 0.96));
  font-weight: 700;
  letter-spacing: 0.18em;
  cursor: pointer;
}

.login-page__reserved {
  padding: 12px 14px;
  margin-bottom: 14px;
  border-radius: 16px;
  background: var(--portal-accent-soft);
  color: var(--sb-text-secondary);

  strong,
  p {
    margin: 0;
  }

  p {
    margin-top: 6px;
    font-size: 12px;
    line-height: 1.6;
  }
}

.login-page__submit {
  width: 100%;
  margin-top: 8px;
}

.login-page__filing {
  margin: 14px 0 0;
  color: var(--sb-text-secondary);
  text-align: center;
  font-size: 12px;
}

@media (max-width: 1080px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-page__hero {
    padding: 32px 24px 12px;
  }

  .login-page__panel {
    margin: 16px;
  }
}
</style>
