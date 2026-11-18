<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { ArrowLeft, Close } from "@element-plus/icons-vue";
import { routeLocationKey } from "vue-router";

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    title?: string;
    subtitle?: string;
    width?: string | number;
    size?: "default" | "wide" | "full";
    destroyOnClose?: boolean;
    closeOnClickModal?: boolean;
    appendToBody?: boolean;
    backText?: string;
  }>(),
  {
    title: "",
    subtitle: "",
    width: undefined,
    size: "default",
    destroyOnClose: false,
    closeOnClickModal: false,
    appendToBody: false,
    backText: "返回列表"
  }
);

const emit = defineEmits<{
  (event: "update:modelValue", value: boolean): void;
  (event: "close"): void;
  (event: "closed"): void;
  (event: "open"): void;
}>();

const isBrowser = typeof window !== "undefined" && typeof document !== "undefined";
const route = inject(routeLocationKey, null);
const stageAvailable = ref(false);

function refreshStageTarget() {
  if (!isBrowser) {
    stageAvailable.value = false;
    return;
  }
  stageAvailable.value = Boolean(document.getElementById("sb-workspace-stage"));
}

const teleportTarget = computed(() => {
  if (props.appendToBody && !stageAvailable.value) {
    return "body";
  }
  return stageAvailable.value ? "#sb-workspace-stage" : "body";
});

const surfaceStyle = computed(() => {
  if (props.size === "full") {
    return { maxWidth: "100%", height: "100%" };
  }
  if (props.size === "wide") {
    return { maxWidth: "1180px" };
  }
  if (props.width !== undefined && props.width !== null && props.width !== "") {
    if (typeof props.width === "number") {
      const targetMax = props.width >= 720 ? Math.max(920, props.width + 180) : 920;
      return { maxWidth: `${targetMax}px` };
    }
    const numeric = Number.parseInt(String(props.width), 10);
    if (Number.isFinite(numeric)) {
      const targetMax = numeric >= 720 ? Math.max(920, numeric + 180) : 920;
      return { maxWidth: `${targetMax}px` };
    }
    if (String(props.width).includes("100%") || String(props.width).includes("96")) {
      return { maxWidth: "100%" };
    }
  }
  return { maxWidth: "960px" };
});

function closePanel() {
  if (!props.modelValue) return;
  emit("update:modelValue", false);
  emit("close");
}

function handleAfterLeave() {
  emit("closed");
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === "Escape" && props.modelValue) {
    closePanel();
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    refreshStageTarget();
    if (visible) {
      emit("open");
    }
  },
  { immediate: true }
);

watch(
  () => route?.fullPath,
  () => {
    if (props.modelValue) {
      closePanel();
    }
  }
);

onMounted(() => {
  refreshStageTarget();
  if (typeof window !== "undefined") {
    window.addEventListener("keydown", handleKeydown);
  }
});

onBeforeUnmount(() => {
  if (typeof window !== "undefined") {
    window.removeEventListener("keydown", handleKeydown);
  }
});
</script>

<template>
  <Teleport :to="teleportTarget" :disabled="!isBrowser">
    <Transition name="workspace-panel-fade" @after-leave="handleAfterLeave">
      <section
        v-if="modelValue || !destroyOnClose"
        v-show="modelValue"
        class="workspace-panel-overlay"
        :class="{ 'is-fixed-fallback': isBrowser && !stageAvailable, 'is-full': size === 'full' }"
        role="dialog"
        aria-modal="false"
        :aria-label="title || '工作区编辑面板'"
      >
        <div class="workspace-panel__surface" :style="surfaceStyle">
          <header class="workspace-panel__header">
            <div class="workspace-panel__header-left">
              <button type="button" class="workspace-panel__back-btn" @click="closePanel">
                <ArrowLeft class="workspace-panel__icon" />
                <span>{{ backText }}</span>
              </button>
              <span class="workspace-panel__divider" aria-hidden="true" />
              <div class="workspace-panel__title-wrap">
                <slot name="header">
                  <h2 class="workspace-panel__title">{{ title }}</h2>
                </slot>
                <p v-if="subtitle" class="workspace-panel__subtitle">{{ subtitle }}</p>
              </div>
            </div>

            <div class="workspace-panel__header-right">
              <slot name="header-extra" />
              <button
                type="button"
                class="workspace-panel__close-btn"
                title="关闭编辑面板 (Esc)"
                aria-label="关闭编辑面板"
                @click="closePanel"
              >
                <Close class="workspace-panel__icon" />
              </button>
            </div>
          </header>

          <div class="workspace-panel__body">
            <slot />
          </div>

          <footer v-if="$slots.footer" class="workspace-panel__footer">
            <slot name="footer" />
          </footer>
        </div>
      </section>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss">
.workspace-panel-overlay {
  position: absolute;
  inset: 0;
  z-index: 45;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 16px 14px;
  overflow: hidden;
  background: var(--sb-app-canvas, #f4f4f5);
  backdrop-filter: var(--sb-glass-blur, blur(26px) saturate(180%));
  -webkit-backdrop-filter: var(--sb-glass-blur, blur(26px) saturate(180%));
}

.workspace-panel-overlay.is-fixed-fallback {
  position: fixed;
  padding: 24px;
  z-index: 1990;
  background: rgba(15, 23, 42, 0.45);
}

.workspace-panel-overlay.is-full {
  padding: 8px 10px 10px;
}

.workspace-panel__surface {
  width: 100%;
  height: 100%;
  max-height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: var(--sb-radius-glass, 16px);
  border: 1px solid var(--sb-border-subtle, #e4e4e7);
  background: var(--sb-tier3-bg, #ffffff);
  box-shadow: var(--sb-glass-shadow-lg, 0 18px 42px -12px rgba(15, 23, 42, 0.14));
}

.workspace-panel__header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--sb-border-subtle, #e4e4e7);
  background: var(--sb-tier2-header-bg, #fafafa);
}

.workspace-panel__header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1 1 auto;
}

.workspace-panel__back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--sb-border-color, #d4d4d8);
  background: var(--sb-control-bg, #ffffff);
  color: var(--sb-text-primary, #0f172a);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.16s ease;

  &:hover {
    border-color: var(--sb-primary-color, #4f46e5);
    color: var(--sb-primary-color, #4f46e5);
    background: var(--sb-primary-weak, rgba(79, 70, 229, 0.08));
  }
}

.workspace-panel__icon {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}

.workspace-panel__divider {
  width: 1px;
  height: 18px;
  background: var(--sb-border-color, #d4d4d8);
  flex-shrink: 0;
}

.workspace-panel__title-wrap {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.workspace-panel__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--sb-text-primary, #0f172a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-panel__subtitle {
  margin: 0;
  font-size: 12px;
  color: var(--sb-text-muted, #475569);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-panel__header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.workspace-panel__close-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid var(--sb-border-subtle, #e4e4e7);
  background: var(--sb-control-bg, #ffffff);
  color: var(--sb-text-muted, #475569);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.16s ease;

  &:hover {
    color: var(--sb-danger-color, #dc2626);
    border-color: var(--sb-danger-border, rgba(220, 38, 38, 0.3));
    background: var(--sb-danger-weak, rgba(220, 38, 38, 0.08));
  }
}

.workspace-panel__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 20px 24px 28px;
}

.workspace-panel-overlay.is-full .workspace-panel__body {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
}

.workspace-panel__footer {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 22px;
  border-top: 1px solid var(--sb-border-subtle, #e4e4e7);
  background: var(--sb-tier2-header-bg, #fafafa);
}

.workspace-panel-fade-enter-active,
.workspace-panel-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.workspace-panel-fade-enter-from,
.workspace-panel-fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
