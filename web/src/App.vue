<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { ElNotification } from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import en from "element-plus/es/locale/lang/en";
import { useLocaleStore } from "@/stores/modules/locale";

const localeStore = useLocaleStore();
const currentLocale = computed(() => (localeStore.locale === "en-US" ? en : zhCn));

const isOffline = ref(!navigator.onLine);

function handleOnline() {
  isOffline.value = false;
  ElNotification({
    title: "网络已恢复",
    message: "与天枢云基中枢链路已重连，数据同步保持最新。",
    type: "success",
    duration: 3500
  });
}

function handleOffline() {
  isOffline.value = true;
  ElNotification({
    title: "网络连接中断",
    message: "当前处于离线状态，部分操作可能受限，系统已启用本地缓存保护。",
    type: "warning",
    duration: 0
  });
}

onMounted(() => {
  window.addEventListener("online", handleOnline);
  window.addEventListener("offline", handleOffline);
});

onUnmounted(() => {
  window.removeEventListener("online", handleOnline);
  window.removeEventListener("offline", handleOffline);
});
</script>

<template>
  <el-config-provider :locale="currentLocale">
    <div v-if="isOffline" class="global-offline-banner">
      <span class="offline-dot" />
      <span>网络链路已断开，正在尝试重连天枢云中枢...</span>
    </div>
    <router-view />
  </el-config-provider>
</template>

<style scoped>
.global-offline-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 99999;
  height: 32px;
  background: #ef4444;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
  box-shadow: 0 2px 10px rgba(239, 68, 68, 0.3);
}

.offline-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ffffff;
  animation: blink 1s infinite alternate;
}

@keyframes blink {
  from { opacity: 0.3; }
  to { opacity: 1; }
}
</style>
