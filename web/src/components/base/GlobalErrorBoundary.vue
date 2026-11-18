<script setup lang="ts">
import { onErrorCaptured, ref } from "vue";
import { useRouter } from "vue-router";
import { WarningFilled, RefreshRight, HomeFilled } from "@element-plus/icons-vue";

const router = useRouter();
const capturedMessage = ref<string | null>(null);

onErrorCaptured((err: unknown) => {
  const msg = err instanceof Error ? err.message : String(err || "未知视图渲染异常");
  console.error("[GlobalErrorBoundary]", err);
  capturedMessage.value = msg;
  return false;
});

function retryView() {
  capturedMessage.value = null;
}

function goHome() {
  capturedMessage.value = null;
  void router.push("/dashboard");
}
</script>

<template>
  <div v-if="capturedMessage" class="error-boundary-shell">
    <div class="error-boundary-card">
      <div class="error-boundary-icon">
        <el-icon><WarningFilled /></el-icon>
      </div>
      <h2>页面组件渲染异常</h2>
      <p class="error-boundary-desc">{{ capturedMessage }}</p>
      <div class="error-boundary-actions">
        <el-button type="primary" :icon="RefreshRight" @click="retryView">重试加载</el-button>
        <el-button :icon="HomeFilled" @click="goHome">返回工作台</el-button>
      </div>
    </div>
  </div>
  <slot v-else />
</template>

<style scoped lang="scss">
.error-boundary-shell {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.error-boundary-card {
  max-width: 480px;
  width: 100%;
  padding: 28px 32px;
  border-radius: 20px;
  background: var(--sb-glass-bg, rgba(255, 255, 255, 0.92));
  border: 1px solid var(--sb-border-subtle, #e2e8f0);
  box-shadow: 0 20px 44px -12px rgba(15, 23, 42, 0.14);
  text-align: center;
}

.error-boundary-icon {
  width: 48px;
  height: 48px;
  margin: 0 auto 14px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(239, 68, 68, 0.12);
  color: #dc2626;
  font-size: 24px;
}

.error-boundary-desc {
  margin: 8px 0 20px;
  font-size: 13px;
  color: var(--sb-text-muted, #64748b);
  word-break: break-word;
}

.error-boundary-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
}
</style>
