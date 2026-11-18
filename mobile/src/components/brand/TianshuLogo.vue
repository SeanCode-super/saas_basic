<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    size?: number;
    showText?: boolean;
    animated?: boolean;
  }>(),
  {
    size: 24,
    showText: false,
    animated: false
  }
);

// 随机生成短 id 避免多实例渐变 id 冲突
const uid = computed(() => Math.random().toString(36).slice(2, 8));
</script>

<template>
  <view
    class="tianshu-brand-emblem"
    :class="{ 'with-text': showText, 'is-animated': animated }"
  >
    <!-- 天枢星轨棱镜：星轨轨道与切面四芒星核 -->
    <svg
      :width="size"
      :height="size"
      viewBox="0 0 48 48"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      class="brand-vector-mark"
    >
      <defs>
        <linearGradient :id="`ts-bg-${uid}`" x1="4" y1="4" x2="44" y2="44" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="#060918" />
          <stop offset="55%" stop-color="#131B3A" />
          <stop offset="100%" stop-color="#1E1B4B" />
        </linearGradient>

        <radialGradient :id="`ts-glow-${uid}`" cx="32%" cy="24%" r="72%" gradientUnits="objectBoundingBox">
          <stop offset="0%" stop-color="#38BDF8" stop-opacity="0.34" />
          <stop offset="48%" stop-color="#6366F1" stop-opacity="0.18" />
          <stop offset="100%" stop-color="#060918" stop-opacity="0" />
        </radialGradient>

        <linearGradient :id="`ts-rim-${uid}`" x1="4" y1="4" x2="44" y2="44" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.38" />
          <stop offset="50%" stop-color="#38BDF8" stop-opacity="0.32" />
          <stop offset="100%" stop-color="#6366F1" stop-opacity="0.18" />
        </linearGradient>

        <linearGradient :id="`ts-orbit-${uid}`" x1="8" y1="34" x2="40" y2="14" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="#818CF8" stop-opacity="0.12" />
          <stop offset="45%" stop-color="#818CF8" stop-opacity="0.88" />
          <stop offset="100%" stop-color="#38BDF8" stop-opacity="1" />
        </linearGradient>

        <linearGradient :id="`ts-star-a-${uid}`" x1="12" y1="10" x2="36" y2="38" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="#E0F2FE" />
          <stop offset="46%" stop-color="#60A5FA" />
          <stop offset="100%" stop-color="#6366F1" />
        </linearGradient>

        <linearGradient :id="`ts-star-b-${uid}`" x1="9" y1="9" x2="27" y2="27" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="#FFFFFF" />
          <stop offset="52%" stop-color="#BAE6FD" />
          <stop offset="100%" stop-color="#38BDF8" />
        </linearGradient>
      </defs>

      <!-- 深空高定底盘与微光内倒角 -->
      <rect x="3" y="3" width="42" height="42" rx="11.5" :fill="`url(#ts-bg-${uid})`" />
      <rect x="3" y="3" width="42" height="42" rx="11.5" :fill="`url(#ts-glow-${uid})`" />
      <rect x="3.5" y="3.5" width="41" height="41" rx="11" :stroke="`url(#ts-rim-${uid})`" stroke-width="1" />

      <!-- 次级交叉微轨 -->
      <ellipse
        cx="24"
        cy="24"
        rx="13.5"
        ry="4.8"
        transform="rotate(56 24 24)"
        :stroke="`url(#ts-orbit-${uid})`"
        stroke-width="1"
        stroke-opacity="0.34"
      />

      <!-- 主倾斜星轨 -->
      <ellipse
        cx="24"
        cy="24"
        rx="16.2"
        ry="6.8"
        transform="rotate(-34 24 24)"
        :stroke="`url(#ts-orbit-${uid})`"
        stroke-width="1.75"
      />

      <!-- 3D 切面天枢四芒星核 -->
      <path
        d="M24 8.5L27.5 20.5L39.5 24L27.5 27.5L24 39.5L20.5 27.5L8.5 24L20.5 20.5Z"
        :fill="`url(#ts-star-a-${uid})`"
      />
      <path
        d="M24 8.5L27.5 20.5L24 24L8.5 24L20.5 20.5Z"
        :fill="`url(#ts-star-b-${uid})`"
        fill-opacity="0.92"
      />

      <!-- 星核菱形锚点与中心奇点 -->
      <path d="M24 18.2L29.8 24L24 29.8L18.2 24Z" fill="#0F172A" fill-opacity="0.55" />
      <circle cx="24" cy="24" r="3.8" fill="#38BDF8" fill-opacity="0.26" class="emblem-core-halo" />
      <circle cx="24" cy="24" r="2.3" fill="#FFFFFF" />

      <!-- 卫星跃迁节点 -->
      <g class="emblem-satellite">
        <circle cx="35.2" cy="16.4" r="3.4" fill="#38BDF8" fill-opacity="0.24" />
        <circle cx="35.2" cy="16.4" r="1.9" fill="#38BDF8" />
      </g>
    </svg>

    <view v-if="showText" class="brand-text-block">
      <text class="brand-name-zh">天枢协同云平台</text>
    </view>
  </view>
</template>

<style scoped>
.tianshu-brand-emblem {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  line-height: 1;
  user-select: none;
}

.brand-vector-mark {
  flex-shrink: 0;
  display: block;
  border-radius: 24%;
  box-shadow: 0 4px 12px -2px rgba(6, 9, 24, 0.28);
}

.brand-text-block {
  display: flex;
  flex-direction: column;
}

.brand-name-zh {
  font-size: 14px;
  font-weight: 750;
  letter-spacing: -0.2px;
  color: #0f172a;
}
</style>
