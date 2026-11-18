import { computed, ref } from "vue";
import { defineStore } from "pinia";

export type ThemeMode = "light" | "dark" | "system";
export type TableDensity = "comfortable" | "compact";
export type ThemePreset = "obsidian" | "emerald" | "titanium" | "cobalt";
export type SurfaceStyle = "matte" | "glass";

const THEME_STORAGE_KEY = "sb-theme-mode";
const DENSITY_STORAGE_KEY = "sb-table-density";
const PRESET_STORAGE_KEY = "sb-theme-preset";
const SURFACE_STORAGE_KEY = "sb-surface-style";

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

function readInitialPreset(): ThemePreset {
  if (typeof window === "undefined") {
    return "obsidian";
  }
  const stored = localStorage.getItem(PRESET_STORAGE_KEY);
  if (stored === "obsidian" || stored === "emerald" || stored === "titanium" || stored === "cobalt") {
    return stored;
  }
  return "obsidian";
}

function readInitialSurface(): SurfaceStyle {
  if (typeof window === "undefined") {
    return "matte";
  }
  const stored = localStorage.getItem(SURFACE_STORAGE_KEY);
  return stored === "glass" ? "glass" : "matte";
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
  const preset = ref<ThemePreset>(readInitialPreset());
  const surface = ref<SurfaceStyle>(readInitialSurface());
  const systemDark = ref<boolean>(prefersSystemDark());
  let initialized = false;

  const isDark = computed(() => (mode.value === "system" ? systemDark.value : mode.value === "dark"));

  function applyDomDensity() {
    if (typeof document === "undefined") {
      return;
    }
    document.documentElement.dataset.density = tableDensity.value;
    document.documentElement.dataset.preset = preset.value;
    document.documentElement.dataset.surface = surface.value;
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

  function setPreset(next: ThemePreset) {
    preset.value = next;
    if (typeof localStorage !== "undefined") {
      localStorage.setItem(PRESET_STORAGE_KEY, next);
    }
    applyDomDensity();
  }

  function setSurface(next: SurfaceStyle) {
    surface.value = next;
    if (typeof localStorage !== "undefined") {
      localStorage.setItem(SURFACE_STORAGE_KEY, next);
    }
    applyDomDensity();
  }

  initThemeListener();

  return {
    mode,
    tableDensity,
    preset,
    surface,
    isDark,
    setMode,
    setTableDensity,
    setPreset,
    setSurface,
    applyDomTheme
  };
});
