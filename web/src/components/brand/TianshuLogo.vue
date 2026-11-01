<script setup lang="ts">
withDefaults(
  defineProps<{
    size?: number;
    showText?: boolean;
    variant?: "cube" | "shield" | "star";
    theme?: "light" | "dark";
  }>(),
  {
    size: 26,
    showText: false,
    variant: "shield",
    theme: "light"
  }
);
</script>

<template>
  <div class="tianshu-brand-emblem" :class="[`theme--${theme}`, { 'with-text': showText }]">
    <!-- 简约真实、稳重可靠的企业科技盾标 / 中枢图标 -->
    <svg
      :width="size"
      :height="size"
      viewBox="0 0 48 48"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      class="brand-vector-mark"
    >
      <rect x="6" y="6" width="36" height="36" rx="10" :fill="theme === 'dark' ? '#1E293B' : '#EFF6FF'" />
      <path
        d="M24 10L36 17V31L24 38L12 31V17L24 10Z"
        :fill="theme === 'dark' ? '#2563EB' : '#0284C7'"
        fill-opacity="0.15"
      />
      <path
        d="M24 14L33 19.5V30.5L24 36L15 30.5V19.5L24 14Z"
        :stroke="theme === 'dark' ? '#60A5FA' : '#0284C7'"
        stroke-width="2.5"
        stroke-linejoin="round"
      />
      <!-- 中心节点 -->
      <path
        d="M24 20L28 24L24 28L20 24L24 20Z"
        :fill="theme === 'dark' ? '#93C5FD' : '#0369A1'"
      />
      <circle cx="24" cy="24" r="2" fill="#FFFFFF" />
    </svg>

    <!-- 纯正中文品牌名称，彻底去除英文与 PaaS 标签 -->
    <div v-if="showText" class="brand-text-block">
      <span class="brand-name-zh">天枢协同云平台</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.tianshu-brand-emblem {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  user-select: none;
}

.brand-vector-mark {
  flex-shrink: 0;
  display: block;
}

.brand-text-block {
  display: flex;
  align-items: center;
}

.brand-name-zh {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.5px;
  line-height: 1;
  color: #0f172a;
}

.theme--dark .brand-name-zh {
  color: #f8fafc;
}
</style>
