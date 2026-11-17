<template>
  <view class="empty-state">
    <view class="empty-icon-wrap">
      <AppIcon :name="resolvedIcon" :size="48" :color="resolvedColor" />
    </view>
    <text class="empty-title">{{ title || defaultTitle }}</text>
    <text class="empty-desc">{{ desc || defaultDesc }}</text>
    <button v-if="actionText" class="empty-btn" @click="$emit('action')">
      {{ actionText }}
    </button>
  </view>
</template>

<script setup lang="ts">
import { computed } from "vue";
import AppIcon from "./AppIcon.vue";

const props = withDefaults(
  defineProps<{
    type?: "data" | "task" | "search" | "network";
    title?: string;
    desc?: string;
    actionText?: string;
  }>(),
  {
    type: "data",
    title: "",
    desc: "",
    actionText: ""
  }
);

defineEmits<{
  (e: "action"): void;
}>();

const resolvedIcon = computed(() => {
  switch (props.type) {
    case "task":
      return "clipboard";
    case "search":
      return "search";
    case "network":
      return "database";
    default:
      return "box";
  }
});

const resolvedColor = computed(() => {
  switch (props.type) {
    case "task":
      return "#3b82f6";
    case "network":
      return "#ef4444";
    default:
      return "#94a3b8";
  }
});

const defaultTitle = computed(() => {
  switch (props.type) {
    case "task":
      return "暂无待办审批任务";
    case "search":
      return "未搜索到匹配项";
    case "network":
      return "网络服务连接异常";
    default:
      return "暂无相关数据记录";
  }
});

const defaultDesc = computed(() => {
  switch (props.type) {
    case "task":
      return "当前没有流转中的待审单据，保持高效轻松！";
    case "search":
      return "请尝试调整搜索关键词或重置筛选条件";
    case "network":
      return "请检查网络连接或稍后点击重试";
    default:
      return "企业空间中暂无更多业务条目";
  }
});
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.empty-icon-wrap {
  width: 80px;
  height: 80px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(24px) saturate(200%);
  -webkit-backdrop-filter: blur(24px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 8px 24px -4px rgba(12, 34, 56, 0.05);
}

.empty-title {
  font-size: 15.5px;
  font-weight: 750;
  color: #0f172a;
  letter-spacing: -0.2px;
  margin-bottom: 6px;
}

.empty-desc {
  font-size: 12.5px;
  color: #64748b;
  line-height: 1.5;
  max-width: 280px;
  font-weight: 500;
}

.empty-btn {
  margin-top: 18px;
  height: 38px;
  line-height: 38px;
  padding: 0 22px;
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #ffffff;
  border-radius: 9999px;
  font-size: 13px;
  font-weight: 650;
  border: 1px solid rgba(255, 255, 255, 0.35);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.4), 0 4px 14px rgba(37, 99, 235, 0.28);
  transition: transform 0.15s ease;
}

.empty-btn:active {
  transform: scale(0.96);
}
</style>
