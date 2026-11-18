import { computed, ref } from "vue";
import { defineStore } from "pinia";

export type ThemeMode = "light" | "dark" | "system";

const THEME_STORAGE_KEY = "sb-theme-mode";

function readInitialThemeMode(): ThemeMode {
  if (typeof window === "undefined") {
    return "light";
  }
  const stored = localStorage.getItem(THEME_STORAGE_KEY);
  if (stored === "light" || stored === "dark" || stored === "system") {
    return stored;
  }
  return "light";
}

function prefersSystemDark(): boolean {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return false;
  }
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

export const useThemeStore = defineStore("theme", () => {
  const mode = ref<ThemeMode>(readInitialThemeMode());
  const systemDark = ref<boolean>(prefersSystemDark());
  let initialized = false;

  const isDark = computed(() => (mode.value === "system" ? systemDark.value : mode.value === "dark"));

  function applyDomTheme() {
    if (typeof document === "undefined") {
      return;
    }
    const dark = isDark.value;
    document.documentElement.classList.toggle("dark", dark);
    document.documentElement.style.colorScheme = dark ? "dark" : "light";
  }

  function initThemeListener() {
    if (initialized || typeof window === "undefined" || typeof window.matchMedia !== "function") {
      applyDomTheme();
      return;
    }
    initialized = true;
    const mq = window.matchMedia("(prefers-color-scheme: dark)");
    systemDark.value = mq.matches;
    mq.addEventListener?.("change", (event: MediaQueryListEvent) => {
      systemDark.value = event.matches;
      applyDomTheme();
    });
    applyDomTheme();
  }

  function setMode(next: ThemeMode) {
    mode.value = next;
    if (typeof localStorage !== "undefined") {
      localStorage.setItem(THEME_STORAGE_KEY, next);
    }
    applyDomTheme();
  }

  initThemeListener();

  return {
    mode,
    isDark,
    setMode,
    applyDomTheme
  };
});
