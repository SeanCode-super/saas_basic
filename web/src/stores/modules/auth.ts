import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { fetchCurrentUser, loginByPassword, loginBySso, logoutSession } from "@/api/modules/auth";
import type { AuthCurrentUser, UserWorkspacePreferences } from "@/api/modules/auth";
import { useLocaleStore } from "./locale";
import { useMenuStore } from "./menu";

const TOKEN_KEY = "sb-access-token";
const REFRESH_TOKEN_KEY = "sb-refresh-token";
const CURRENT_USER_KEY = "sb-current-user";

export const useAuthStore = defineStore("auth", () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) ?? "");
  const refreshToken = ref<string>(localStorage.getItem(REFRESH_TOKEN_KEY) ?? "");
  const currentUser = ref<AuthCurrentUser | null>(readCurrentUser());
  const bootstrapped = ref(false);

  const isAuthenticated = computed(() => Boolean(token.value));

  function syncPreferences(prefs?: UserWorkspacePreferences | null, localeOverride?: string | null) {
    const nextLocale = localeOverride || prefs?.locale;
    if (nextLocale === "zh-CN" || nextLocale === "en-US") {
      useLocaleStore().setLocale(nextLocale);
    }
    if (!prefs) {
      return;
    }
    if (prefs.layoutMode) {
      localStorage.setItem("sb_layout_mode", prefs.layoutMode);
    }
    if (prefs.railTheme) {
      localStorage.setItem("sb_rail_theme", prefs.railTheme);
    }
    if (typeof prefs.showBreadcrumbs === "boolean") {
      localStorage.setItem("sb_show_breadcrumbs", String(prefs.showBreadcrumbs));
    }
    if (typeof prefs.sidebarAccordion === "boolean") {
      localStorage.setItem("sb_sidebar_accordion", String(prefs.sidebarAccordion));
    }
    if (typeof prefs.enableWatermark === "boolean") {
      localStorage.setItem("sb_enable_watermark", String(prefs.enableWatermark));
    }
    if (typeof window !== "undefined") {
      window.dispatchEvent(new CustomEvent("sb-preferences-synced", { detail: prefs }));
    }
  }

  function applyCurrentUser(user: AuthCurrentUser) {
    currentUser.value = user;
    persistCurrentUser(user);
    syncPreferences(user.preferences, user.locale);
  }

  function updateUserSnapshot(partial: Partial<AuthCurrentUser>) {
    if (!currentUser.value) return;
    const merged: AuthCurrentUser = {
      ...currentUser.value,
      ...partial
    };
    applyCurrentUser(merged);
  }

  async function refreshCurrentUser() {
    if (!token.value) return null;
    const nextUser = await fetchCurrentUser();
    applyCurrentUser(nextUser);
    return nextUser;
  }

  async function bootstrap() {
    if (bootstrapped.value) {
      return;
    }
    bootstrapped.value = true;
    if (!token.value) {
      return;
    }
    try {
      const nextUser = await fetchCurrentUser();
      applyCurrentUser(nextUser);
    } catch {
      // Retain cached currentUser if network fails
    }
  }

  async function login(payload: {
    tenantCode?: string;
    clientId?: string;
    terminalCode?: string;
    captchaId?: string;
    captchaCode?: string;
    totpCode?: string;
    username: string;
    password: string;
  }) {
    const response = await loginByPassword(payload);
    token.value = response.accessToken;
    refreshToken.value = response.refreshToken ?? "";
    localStorage.setItem(TOKEN_KEY, response.accessToken);
    if (response.refreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken);
    }
    applyCurrentUser(response.currentUser);
    useMenuStore().clear();
  }

  async function loginWithSso(payload: { channel: string; authCode: string; state?: string; tenantCode?: string }) {
    const response = await loginBySso(payload);
    token.value = response.accessToken;
    refreshToken.value = response.refreshToken ?? "";
    localStorage.setItem(TOKEN_KEY, response.accessToken);
    if (response.refreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken);
    }
    applyCurrentUser(response.currentUser);
    useMenuStore().clear();
  }

  async function logout() {
    if (token.value) {
      try {
        await logoutSession();
      } catch {
        // Ignore logout request failures and clear local state anyway.
      }
    }
    token.value = "";
    refreshToken.value = "";
    currentUser.value = null;
    bootstrapped.value = false;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(CURRENT_USER_KEY);
    useMenuStore().clear();
  }

  const isSuperAdmin = computed(() => {
    const user = currentUser.value;
    if (!user) return false;
    return (
      user.userId === 1 ||
      user.username === "admin" ||
      user.userType === "PLATFORM" ||
      Boolean(user.roleIds?.includes(1)) ||
      Boolean(user.permissions?.includes("*") || user.permissions?.includes("*:*:*")) ||
      Boolean(user.buttonPermissions?.includes("*"))
    );
  });

  function hasPermission(permissionCode?: string) {
    if (!permissionCode || isSuperAdmin.value) {
      return true;
    }
    if (currentUser.value?.permissions?.includes("*") || currentUser.value?.permissions?.includes("*:*:*")) {
      return true;
    }
    return currentUser.value?.permissions?.includes(permissionCode) ?? false;
  }

  function hasFeature(featureFlag?: string) {
    if (!featureFlag) {
      return true;
    }
    return currentUser.value?.featureFlags.includes(featureFlag) ?? false;
  }

  function hasButtonPermission(buttonPermissionCode?: string) {
    if (!buttonPermissionCode || isSuperAdmin.value) {
      return true;
    }
    if (currentUser.value?.buttonPermissions?.includes("*")) {
      return true;
    }
    return currentUser.value?.buttonPermissions?.includes(buttonPermissionCode) ?? false;
  }

  return {
    token,
    refreshToken,
    currentUser,
    bootstrapped,
    isAuthenticated,
    isSuperAdmin,
    bootstrap,
    refreshCurrentUser,
    updateUserSnapshot,
    syncPreferences,
    login,
    loginWithSso,
    logout,
    hasPermission,
    hasButtonPermission,
    hasFeature
  };
});

function readCurrentUser(): AuthCurrentUser | null {
  const raw = localStorage.getItem(CURRENT_USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as AuthCurrentUser;
  } catch {
    localStorage.removeItem(CURRENT_USER_KEY);
    return null;
  }
}

function persistCurrentUser(currentUser: AuthCurrentUser | null) {
  if (!currentUser) {
    localStorage.removeItem(CURRENT_USER_KEY);
    return;
  }
  localStorage.setItem(CURRENT_USER_KEY, JSON.stringify(currentUser));
}
