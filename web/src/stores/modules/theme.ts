import { computed, ref } from "vue";
import { defineStore } from "pinia";

export type ThemeMode = "light" | "dark" | "system";
export type TableDensity = "comfortable" | "compact";

const THEME_STORAGE_KEY = "sb-theme-mode";
const DENSITY_STORAGE_KEY = "sb-table-density";

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

function readInitialTableDensity(): TableDensity {
  if (typeof window === "undefined") {
    return "comfortable";
  }
  const stored = localStorage.getItem(DENSITY_STORAGE_KEY);
  return stored === "compact" ? "compact" : "comfortable";
}

function prefersSystemDark(): boolean {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return false;
  }
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

export const useThemeStore = defineStore("theme", () => {
  const mode = ref<ThemeMode>(readInitialThemeMode());
  const tableDensity = ref<TableDensity>(readInitialTableDensity());
  const systemDark = ref<boolean>(prefersSystemDark());
  let initialized = false;

  const isDark = computed(() => (mode.value === "system" ? systemDark.value : mode.value === "dark"));

  function applyDomDensity() {
    if (typeof document === "undefined") {
      return;
    }
    document.documentElement.dataset.density = tableDensity.value;
  }

  function applyDomTheme() {
    if (typeof document === "undefined") {
      return;
    }
    const dark = isDark.value;
    document.documentElement.classList.toggle("dark", dark);
    document.documentElement.style.colorScheme = dark ? "dark" : "light";
    applyDomDensity();
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

  function setTableDensity(next: TableDensity) {
    tableDensity.value = next;
    if (typeof localStorage !== "undefined") {
      localStorage.setItem(DENSITY_STORAGE_KEY, next);
    }
    applyDomDensity();
  }

  initThemeListener();

  return {
    mode,
    tableDensity,
    isDark,
    setMode,
    setTableDensity,
    applyDomTheme
  };
});
