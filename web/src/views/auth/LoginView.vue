<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { useRoute, useRouter } from "vue-router";
import { fetchPortalEntry, type PortalEntry } from "@/api/modules/portal";
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
  "--portal-bg-color": portal.value?.backgroundColor || "#20262d",
  "--portal-bg-image": portal.value?.backgroundImageUrl ? `url(${portal.value.backgroundImageUrl})` : "none",
  "--portal-accent": resolveThemeColor(portal.value?.themeCode)
}));

const captchaEnabled = computed(() => portal.value?.captcha.mode === "IMAGE");

function resolveThemeColor(themeCode?: string) {
  switch ((themeCode || "").toLowerCase()) {
    case "emerald":
      return "#16805f";
    case "amber":
      return "#ad690f";
    case "graphite":
      return "#3a4a5a";
    default:
      return "#2f6fd5";
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
    <header class="login-page__header">
      <div class="login-page__brand">
        <div class="login-page__logo">
          <img v-if="portal?.logoUrl" :src="portal.logoUrl" alt="" />
          <span v-else>{{ (portal?.portalTitle || "SB").slice(0, 2) }}</span>
        </div>
        <strong>{{ portal?.portalTitle || "SaaS Basic" }}</strong>
      </div>
      <span v-if="portal">{{ portal.terminal.terminalName }}</span>
    </header>

    <main class="login-page__main">
      <section class="login-panel" aria-label="登录">
        <header class="login-panel__header">
          <h1>登录</h1>
          <span v-if="portal">{{ portal.clientName }}</span>
        </header>

        <el-skeleton :loading="booting" animated>
          <template #template>
            <el-skeleton-item variant="rect" style="height: 320px; border-radius: 6px" />
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
              <el-input v-model="form.tenantCode" placeholder="租户编码" />
            </el-form-item>
            <el-form-item label="用户名">
              <el-input v-model="form.username" placeholder="用户名" autocomplete="username" @keyup.enter="handleLogin" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                placeholder="密码"
                autocomplete="current-password"
                @keyup.enter="handleLogin"
              />
            </el-form-item>
            <el-form-item v-if="captchaEnabled" label="验证码">
              <div class="login-page__captcha">
                <el-input v-model="form.captchaCode" placeholder="验证码" @keyup.enter="handleLogin" />
                <button type="button" class="login-page__captcha-code" title="刷新验证码" @click="refreshCaptcha">
                  {{ captchaSeed }}
                </button>
              </div>
            </el-form-item>

            <el-button type="primary" native-type="submit" class="login-page__submit" :loading="loading">
              登录
            </el-button>
          </el-form>
        </el-skeleton>
      </section>
    </main>

    <footer v-if="portal?.filingInfo" class="login-page__footer">{{ portal.filingInfo }}</footer>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  --portal-bg-color: #20262d;
  --portal-bg-image: none;
  --portal-accent: #2f6fd5;

  min-height: 100vh;
  display: grid;
  grid-template-rows: 60px minmax(0, 1fr) 42px;
  background:
    linear-gradient(rgb(245 247 250 / 0.93), rgb(245 247 250 / 0.93)),
    var(--portal-bg-image) center/cover no-repeat,
    #f5f7fa;
}

.login-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 28px;
  border-bottom: 1px solid #d9dfe7;
  background: #fff;

  > span {
    color: var(--sb-text-secondary);
    font-size: 12px;
  }
}

.login-page__brand {
  display: flex;
  align-items: center;
  gap: 10px;

  strong {
    font-size: 16px;
  }
}

.login-page__logo {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 5px;
  overflow: hidden;
  background: var(--portal-accent);
  color: #fff;
  font-size: 12px;
  font-weight: 700;

  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }
}

.login-page__main {
  display: grid;
  place-items: center;
  padding: 32px 20px;
}

.login-panel {
  width: min(400px, 100%);
  padding: 28px 30px 30px;
  border: 1px solid #d7dee7;
  border-radius: 7px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(31 42 55 / 0.08);
}

.login-panel__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 24px;

  h1 {
    margin: 0;
    font-size: 21px;
  }

  span {
    max-width: 200px;
    color: var(--sb-text-tertiary);
    overflow: hidden;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.login-panel :deep(.el-form-item) {
  margin-bottom: 18px;
}

.login-panel :deep(.el-form-item__label) {
  margin-bottom: 5px;
  color: var(--sb-text-secondary);
  font-size: 13px;
  line-height: 1.4;
}

.login-panel :deep(.el-input__wrapper) {
  min-height: 40px;
}

.login-page__error {
  margin-bottom: 16px;
}

.login-page__captcha {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 96px;
  gap: 8px;
}

.login-page__captcha-code {
  border: 1px solid #cfd7e1;
  border-radius: 5px;
  background: #f7f8fa;
  color: var(--sb-text-primary);
  font-weight: 700;
  cursor: pointer;
}

.login-page__submit {
  width: 100%;
  min-height: 40px;
  margin-top: 4px;

  --el-button-bg-color: var(--portal-accent);
  --el-button-border-color: var(--portal-accent);
}

.login-page__footer {
  display: grid;
  place-items: center;
  color: var(--sb-text-tertiary);
  font-size: 12px;
}

@media (max-width: 600px) {
  .login-page__header {
    padding: 0 16px;
  }

  .login-panel {
    padding: 24px 20px;
  }
}
</style>
