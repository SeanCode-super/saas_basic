<template>
  <view v-if="isOpen" class="modal-overlay" @click.self="handleMaskClick">
    <view class="modal-container" :class="[mode === 'sheet' ? 'is-sheet' : 'is-dialog']">
      <!-- 头部 -->
      <view class="modal-header">
        <view class="title-group">
          <text class="modal-title">{{ title }}</text>
          <text v-if="subtitle" class="modal-subtitle">{{ subtitle }}</text>
        </view>
        <view class="modal-close" @click="handleClose">
          <text class="close-icon">✕</text>
        </view>
      </view>

      <!-- 内容插槽 -->
      <view class="modal-body">
        <slot />
      </view>

      <!-- 底部操作区 -->
      <view v-if="showFooter" class="modal-footer">
        <button v-if="showCancel" class="btn btn-cancel" @click="handleCancel">
          {{ cancelText }}
        </button>
        <button
          class="btn btn-confirm"
          :class="[confirmType ? 'btn-' + confirmType : '']"
          :loading="loading || confirmLoading"
          @click="handleConfirm"
        >
          {{ confirmText }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    modelValue?: boolean;
    visible?: boolean;
    title?: string;
    subtitle?: string;
    mode?: "dialog" | "sheet";
    confirmText?: string;
    cancelText?: string;
    confirmType?: "primary" | "danger" | "success";
    showCancel?: boolean;
    showFooter?: boolean;
    loading?: boolean;
    confirmLoading?: boolean;
    closeOnMask?: boolean;
  }>(),
  {
    modelValue: undefined,
    visible: undefined,
    title: "提示",
    subtitle: "",
    mode: "dialog",
    confirmText: "确定",
    cancelText: "取消",
    confirmType: "primary",
    showCancel: true,
    showFooter: true,
    loading: false,
    confirmLoading: false,
    closeOnMask: true
  }
);

const emit = defineEmits<{
  (e: "update:modelValue", val: boolean): void;
  (e: "update:visible", val: boolean): void;
  (e: "confirm"): void;
  (e: "cancel"): void;
  (e: "close"): void;
}>();

const isOpen = computed(() => {
  if (props.modelValue !== undefined) return props.modelValue;
  if (props.visible !== undefined) return props.visible;
  return false;
});

function handleClose() {
  emit("update:modelValue", false);
  emit("update:visible", false);
  emit("close");
}

function handleMaskClick() {
  if (props.closeOnMask) {
    handleClose();
  }
}

function handleCancel() {
  emit("cancel");
  handleClose();
}

function handleConfirm() {
  emit("confirm");
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: fadeIn 0.2s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.modal-container {
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(36px) saturate(210%);
  -webkit-backdrop-filter: blur(36px) saturate(210%);
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 25px 50px -12px rgba(12, 34, 56, 0.18);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 居中对话框 */
.modal-container.is-dialog {
  width: 86vw;
  max-width: 380px;
  border-radius: 22px;
  animation: popIn 0.22s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes popIn {
  from {
    transform: scale(0.92);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}

/* 底部抽屉 (Bottom Sheet) */
.modal-container.is-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100vw;
  max-height: 82vh;
  border-radius: 26px 26px 0 0;
  padding-bottom: calc(14px + env(safe-area-inset-bottom));
  animation: slideUp 0.26s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes slideUp {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

.modal-header {
  padding: 18px 20px 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(226, 232, 240, 0.7);
}

.title-group {
  display: flex;
  flex-direction: column;
}

.modal-title {
  font-size: 16.5px;
  font-weight: 750;
  color: #0f172a;
  letter-spacing: -0.2px;
}

.modal-subtitle {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
  font-weight: 500;
}

.modal-close {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: rgba(148, 163, 184, 0.16);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s ease;
}

.modal-close:active {
  transform: scale(0.92);
}

.close-icon {
  font-size: 13px;
  color: #64748b;
  line-height: 1;
}

.modal-body {
  padding: 16px 20px;
  overflow-y: auto;
  max-height: 60vh;
  box-sizing: border-box;
}

.modal-footer {
  padding: 12px 20px 16px;
  display: flex;
  gap: 12px;
  border-top: 1px solid rgba(226, 232, 240, 0.7);
}

.btn {
  flex: 1;
  height: 44px;
  line-height: 44px;
  border-radius: 9999px;
  font-size: 14px;
  font-weight: 650;
  border: none;
  transition: transform 0.15s ease;
}

.btn:active {
  transform: scale(0.97);
}

.btn-cancel {
  background: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(203, 213, 225, 0.85);
  color: #475569;
}

.btn-confirm {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #ffffff;
  border: 1px solid rgba(255, 255, 255, 0.35);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.4), 0 4px 14px rgba(37, 99, 235, 0.28);
}

.btn-confirm.btn-success {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.4), 0 4px 14px rgba(16, 185, 129, 0.28);
}

.btn-confirm.btn-danger {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.4), 0 4px 14px rgba(239, 68, 68, 0.28);
}
</style>
