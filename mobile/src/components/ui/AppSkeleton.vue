<template>
  <view class="skeleton-wrapper" :class="['type-' + type]">
    <!-- 统计卡片骨架 -->
    <view v-if="type === 'stats'" class="skeleton-stats-grid">
      <view v-for="i in 4" :key="i" class="skeleton-box skeleton-stat-cell shimmer" />
    </view>

    <!-- 横幅骨架 -->
    <view v-else-if="type === 'banner'" class="skeleton-box skeleton-banner shimmer" />

    <!-- 列表骨架 -->
    <view v-else class="skeleton-list">
      <view v-for="i in count" :key="i" class="skeleton-card shimmer">
        <view class="skeleton-row top-row">
          <view class="skeleton-circle" />
          <view class="skeleton-lines">
            <view class="skeleton-line w-40" />
            <view class="skeleton-line w-70" />
          </view>
        </view>
        <view class="skeleton-line w-100 mt-12" />
        <view class="skeleton-line w-60 mt-8" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    type?: "list" | "card" | "banner" | "stats";
    count?: number;
  }>(),
  {
    type: "list",
    count: 3
  }
);
</script>

<style scoped>
.skeleton-wrapper {
  width: 100%;
}

.shimmer {
  background: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0.45) 0%,
    rgba(255, 255, 255, 0.85) 50%,
    rgba(255, 255, 255, 0.45) 100%
  );
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.85);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.95);
  background-size: 200% 100%;
  animation: shimmerAnim 1.6s ease-in-out infinite;
}

@keyframes shimmerAnim {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

.skeleton-stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.skeleton-stat-cell {
  height: 72px;
  border-radius: 14px;
}

.skeleton-banner {
  height: 120px;
  border-radius: 16px;
  margin-bottom: 14px;
}

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skeleton-card {
  padding: 16px;
  border-radius: 16px;
  border: 1px solid rgba(226, 232, 240, 0.6);
  min-height: 100px;
}

.skeleton-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.skeleton-circle {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(203, 213, 225, 0.5);
}

.skeleton-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.skeleton-line {
  height: 12px;
  border-radius: 6px;
  background: rgba(203, 213, 225, 0.5);
}

.w-40 { width: 40%; }
.w-60 { width: 60%; }
.w-70 { width: 70%; }
.w-100 { width: 100%; }
.mt-12 { margin-top: 12px; }
.mt-8 { margin-top: 8px; }
</style>
