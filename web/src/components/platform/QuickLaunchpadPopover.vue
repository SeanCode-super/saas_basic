<script setup lang="ts">
import { computed, ref } from "vue";
import {
  Bell,
  DataBoard,
  Document,
  Download,
  Grid,
  Monitor,
  Moon,
  Operation,
  Setting,
  Sunny
} from "@element-plus/icons-vue";
import { translate, type AppLocale } from "@/locales/messages";
import { useLocaleStore } from "@/stores/modules/locale";
import { useThemeStore, type ThemeMode } from "@/stores/modules/theme";

type LayoutMode = "mix" | "side" | "top";

defineProps<{
  layoutMode: LayoutMode;
}>();

const emit = defineEmits<{
  (e: "open-data-screen"): void;
  (e: "open-download-center"): void;
  (e: "open-server-monitor"): void;
  (e: "open-webhook-center"): void;
  (e: "open-api-docs"): void;
  (e: "open-settings"): void;
  (e: "change-layout", mode: LayoutMode): void;
  (e: "change-locale", locale: AppLocale): void;
}>();

const popoverVisible = ref(false);
const localeStore = useLocaleStore();
const themeStore = useThemeStore();

const t = (key: string) => translate(localeStore.locale, key);

const quickApps = computed(() => [
  { key: "screen", label: t("launchpad.dataScreen"), icon: DataBoard, tone: "blue", event: "open-data-screen" as const },
  { key: "download", label: t("launchpad.downloadCenter"), icon: Download, tone: "emerald", event: "open-download-center" as const },
  { key: "monitor", label: t("launchpad.serverMonitor"), icon: Monitor, tone: "indigo", event: "open-server-monitor" as const },
  { key: "webhook", label: t("launchpad.webhookCenter"), icon: Bell, tone: "amber", event: "open-webhook-center" as const },
  { key: "apidoc", label: t("launchpad.apiDocs"), icon: Document, tone: "cyan", event: "open-api-docs" as const },
  { key: "prefs", label: t("launchpad.preferences"), icon: Setting, tone: "slate", event: "open-settings" as const }
]);

function triggerAction(
  event:
    | "open-data-screen"
    | "open-download-center"
    | "open-server-monitor"
    | "open-webhook-center"
    | "open-api-docs"
    | "open-settings"
) {
  popoverVisible.value = false;
  switch (event) {
    case "open-data-screen":
      emit("open-data-screen");
      break;
    case "open-download-center":
      emit("open-download-center");
      break;
    case "open-server-monitor":
      emit("open-server-monitor");
      break;
    case "open-webhook-center":
      emit("open-webhook-center");
      break;
    case "open-api-docs":
      emit("open-api-docs");
      break;
    case "open-settings":
      emit("open-settings");
      break;
  }
}

function selectTheme(mode: ThemeMode) {
  themeStore.setMode(mode);
}

function selectLocale(locale: AppLocale) {
  emit("change-locale", locale);
}

function selectLayout(mode: LayoutMode) {
  emit("change-layout", mode);
}
</script>

<template>
  <el-popover
    v-model:visible="popoverVisible"
    placement="bottom-end"
    :width="352"
    trigger="click"
    popper-class="launchpad-popover-panel"
  >
    <template #reference>
      <button
        type="button"
        class="launchpad-trigger-btn"
        :title="t('launchpad.hub')"
        :aria-label="t('launchpad.hub')"
      >
        <el-icon class="launchpad-trigger-icon"><Grid /></el-icon>
        <span class="launchpad-trigger-label">{{ t("launchpad.hub") }}</span>
      </button>
    </template>

    <div class="launchpad-body">
      <div class="launchpad-bento-grid">
        <button
          v-for="item in quickApps"
          :key="item.key"
          type="button"
          class="launchpad-tile"
          @click="triggerAction(item.event)"
        >
          <span class="tile-icon" :class="`tone-${item.tone}`">
            <el-icon><component :is="item.icon" /></el-icon>
          </span>
          <span class="tile-label">{{ item.label }}</span>
        </button>
      </div>

      <div class="launchpad-divider" />

      <div class="launchpad-controls">
        <div class="control-row">
          <span class="control-label">
            <el-icon><component :is="themeStore.isDark ? Moon : Sunny" /></el-icon>
            {{ t("launchpad.theme") }}
          </span>
          <div class="segmented-group">
            <button
              type="button"
              class="seg-btn"
              :class="{ 'is-active': themeStore.mode === 'light' }"
              @click="selectTheme('light')"
            >
              {{ t("launchpad.themeLight") }}
            </button>
            <button
              type="button"
              class="seg-btn"
              :class="{ 'is-active': themeStore.mode === 'dark' }"
              @click="selectTheme('dark')"
            >
              {{ t("launchpad.themeDark") }}
            </button>
            <button
              type="button"
              class="seg-btn"
              :class="{ 'is-active': themeStore.mode === 'system' }"
              @click="selectTheme('system')"
            >
              {{ t("launchpad.themeSystem") }}
            </button>
          </div>
        </div>

        <div class="control-row">
          <span class="control-label">
            <el-icon><Operation /></el-icon>
            {{ t("launchpad.locale") }}
          </span>
          <div class="segmented-group">
            <button
              type="button"
              class="seg-btn"
              :class="{ 'is-active': localeStore.locale === 'zh-CN' }"
              @click="selectLocale('zh-CN')"
            >
              简体中文
            </button>
            <button
              type="button"
              class="seg-btn"
              :class="{ 'is-active': localeStore.locale === 'en-US' }"
              @click="selectLocale('en-US')"
            >
              English
            </button>
          </div>
        </div>

        <div class="control-row">
          <span class="control-label">
            <el-icon><Grid /></el-icon>
            {{ t("launchpad.layout") }}
          </span>
          <div class="segmented-group">
            <button
              type="button"
              class="seg-btn"
              :class="{ 'is-active': layoutMode === 'top' }"
              @click="selectLayout('top')"
            >
              {{ t("launchpad.layoutTop") }}
            </button>
            <button
              type="button"
              class="seg-btn"
              :class="{ 'is-active': layoutMode === 'mix' }"
              @click="selectLayout('mix')"
            >
              {{ t("launchpad.layoutMix") }}
            </button>
            <button
              type="button"
              class="seg-btn"
              :class="{ 'is-active': layoutMode === 'side' }"
              @click="selectLayout('side')"
            >
              {{ t("launchpad.layoutSide") }}
            </button>
          </div>
        </div>

        <div class="control-row">
          <span class="control-label">
            <el-icon><Operation /></el-icon>
            {{ t("launchpad.density") }}
          </span>
          <div class="segmented-group">
            <button
              type="button"
              class="seg-btn"
              :class="{ 'is-active': themeStore.tableDensity === 'comfortable' }"
              @click="themeStore.setTableDensity('comfortable')"
            >
              {{ t("launchpad.densityComfortable") }}
            </button>
            <button
              type="button"
              class="seg-btn"
              :class="{ 'is-active': themeStore.tableDensity === 'compact' }"
              @click="themeStore.setTableDensity('compact')"
            >
              {{ t("launchpad.densityCompact") }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </el-popover>
</template>

<style scoped lang="scss">
.launchpad-trigger-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 11px;
  border-radius: 9px;
  border: 1px solid var(--sb-border-color, #cbd5e1);
  background: var(--sb-surface-color, rgba(255, 255, 255, 0.92));
  color: var(--sb-text-primary, #0f172a);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  transition: all 140ms ease;

  &:hover {
    border-color: var(--sb-primary-color, #2563eb);
    color: var(--sb-primary-color, #2563eb);
    background: var(--sb-primary-weak, rgba(37, 99, 235, 0.08));
  }
}

.launchpad-trigger-icon {
  font-size: 14px;
}

.launchpad-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.launchpad-bento-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.launchpad-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 6px;
  border-radius: 12px;
  border: 1px solid var(--sb-border-subtle, #e2e8f0);
  background: var(--sb-surface-soft, rgba(248, 250, 252, 0.9));
  color: var(--sb-text-secondary, #1e293b);
  cursor: pointer;
  transition: transform 140ms ease, border-color 140ms ease, background 140ms ease;

  &:hover {
    transform: translateY(-1px);
    border-color: var(--sb-primary-border, rgba(37, 99, 235, 0.35));
    background: var(--sb-primary-weak, rgba(37, 99, 235, 0.08));
  }
}

.tile-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;

  &.tone-blue { background: rgba(37, 99, 235, 0.12); color: #2563eb; }
  &.tone-emerald { background: rgba(5, 150, 105, 0.12); color: #059669; }
  &.tone-indigo { background: rgba(79, 70, 229, 0.12); color: #4f46e5; }
  &.tone-amber { background: rgba(217, 119, 6, 0.12); color: #d97706; }
  &.tone-cyan { background: rgba(2, 132, 199, 0.12); color: #0284c7; }
  &.tone-slate { background: rgba(71, 85, 105, 0.12); color: #475569; }
}

.tile-label {
  font-size: 11.5px;
  font-weight: 600;
  white-space: nowrap;
}

.launchpad-divider {
  height: 1px;
  background: var(--sb-border-subtle, #e2e8f0);
}

.launchpad-controls {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.control-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.control-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--sb-text-tertiary, #334155);
  white-space: nowrap;
}

.segmented-group {
  display: inline-flex;
  padding: 2px;
  border-radius: 8px;
  background: var(--sb-surface-contrast, #f1f5f9);
  border: 1px solid var(--sb-border-subtle, #e2e8f0);
}

.seg-btn {
  padding: 4px 9px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--sb-text-muted, #475569);
  font-size: 11.5px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 140ms ease;

  &.is-active {
    background: var(--sb-primary-color, #2563eb);
    color: #ffffff;
    font-weight: 600;
    box-shadow: 0 1px 3px rgba(37, 99, 235, 0.25);
  }
}
</style>
