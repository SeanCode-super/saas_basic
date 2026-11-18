<script setup lang="ts">
import { useId } from "vue";

withDefaults(
  defineProps<{
    size?: number;
    showText?: boolean;
    variant?: "cube" | "shield" | "star";
    theme?: "light" | "dark";
    animated?: boolean;
  }>(),
  {
    size: 26,
    showText: false,
    variant: "shield",
    theme: "light",
    animated: false
  }
);

const uid = useId();
</script>

<template>
  <div
    class="tianshu-brand-emblem"
    :class="[`theme--${theme}`, `variant--${variant}`, { 'with-text': showText, 'is-animated': animated }]"
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
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  user-select: none;

  &:hover .brand-vector-mark {
    transform: scale(1.04);
  }
}

.brand-vector-mark {
  flex-shrink: 0;
  display: block;
  border-radius: 24%;
  transition: transform 220ms cubic-bezier(0.22, 1, 0.36, 1);
  box-shadow: 0 4px 12px -2px rgba(6, 9, 24, 0.28);
}

.is-animated {
  .emblem-core-halo {
    transform-origin: 24px 24px;
    animation: tsCorePulse 3.2s ease-in-out infinite;
  }

  .emblem-satellite {
    transform-origin: 35.2px 16.4px;
    animation: tsNodePulse 2.8s ease-in-out infinite;
  }
}

.brand-text-block {
  display: flex;
  align-items: center;
  min-width: 0;
  overflow: hidden;
}

.brand-name-zh {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
  line-height: 1;
  white-space: nowrap;
  color: #0f172a;
}

.theme--dark .brand-name-zh {
  color: #f8fafc;
}

@keyframes tsCorePulse {
  0%,
  100% {
    opacity: 0.28;
    transform: scale(0.95);
  }

  50% {
    opacity: 0.65;
    transform: scale(1.18);
  }
}

@keyframes tsNodePulse {
  0%,
  100% {
    opacity: 0.82;
    transform: scale(0.92);
  }

  50% {
    opacity: 1;
    transform: scale(1.14);
  }
}

@media (prefers-reduced-motion: reduce) {
  .is-animated .emblem-core-halo,
  .is-animated .emblem-satellite {
    animation: none;
  }
}
</style>
