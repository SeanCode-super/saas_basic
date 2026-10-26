<script setup lang="ts">
withDefaults(
  defineProps<{
    size?: number;
    showText?: boolean;
    showBadge?: boolean;
    badgeText?: string;
    variant?: "star" | "monolith" | "orbit";
  }>(),
  {
    size: 26,
    showText: false,
    showBadge: false,
    badgeText: "PaaS",
    variant: "star"
  }
);
</script>

<template>
  <div class="tianshu-brand-emblem" :class="{ 'with-text': showText }">
    <!-- VARIANT 1: 天枢极星中枢 (The Polaris Pivot - Snowflake / Linear 纯几何矢量风格) -->
    <svg
      v-if="variant === 'star'"
      :width="size"
      :height="size"
      viewBox="0 0 48 48"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      class="brand-vector-mark"
    >
      <!-- 4 Engineered Chevron Wings (Four Pillars converging on Pivot) -->
      <!-- Top Wing -->
      <path d="M24 4L34 14H27.5L24 10.5L20.5 14H14L24 4Z" fill="#1D4ED8" />
      <!-- Bottom Wing -->
      <path d="M24 44L14 34H20.5L24 37.5L27.5 34H34L24 44Z" fill="#1D4ED8" />
      <!-- Left Wing -->
      <path d="M4 24L14 14V20.5L10.5 24L14 27.5V34L4 24Z" fill="#2563EB" />
      <!-- Right Wing -->
      <path d="M44 24L34 34V27.5L37.5 24L34 20.5V14L44 24Z" fill="#2563EB" />

      <!-- Center Precision Pivot Diamond (Perfect 4-Axis Symmetry) -->
      <path d="M24 17L31 24L24 31L17 24L24 17Z" fill="#0F172A" />
      <!-- Core Anchor Point -->
      <circle cx="24" cy="24" r="2.5" fill="#FFFFFF" />
    </svg>

    <!-- VARIANT 2: 天枢基石 T (The Monolith Foundation - HashiCorp / Docker 工业级等轴测) -->
    <svg
      v-else-if="variant === 'monolith'"
      :width="size"
      :height="size"
      viewBox="0 0 48 48"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      class="brand-vector-mark"
    >
      <!-- Horizontal Crossbar (Top Face) -->
      <path d="M24 5L42 14L34 18.5L14 8.5L24 5Z" fill="#3B82F6" />
      <!-- Horizontal Crossbar (Left Face) -->
      <path d="M6 18.5L24 9.5L24 18.5L6 27.5V18.5Z" fill="#2563EB" />
      <!-- Horizontal Crossbar (Right Face) -->
      <path d="M42 14L24 23V32L42 23V14Z" fill="#1D4ED8" />
      <!-- Pillar (Left Face) -->
      <path d="M15 23L24 27.5V43L15 38.5V23Z" fill="#1E40AF" />
      <!-- Pillar (Right Face) -->
      <path d="M33 23L24 27.5V43L33 38.5V23Z" fill="#0F172A" />
    </svg>

    <!-- VARIANT 3: 天枢星轨 (The Celestial Orbit - Linear / Cosmos 极简圆弧) -->
    <svg
      v-else
      :width="size"
      :height="size"
      viewBox="0 0 48 48"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      class="brand-vector-mark"
    >
      <!-- Outer Precision Structural Ring -->
      <circle cx="24" cy="24" r="18" stroke="#0F172A" stroke-width="3.5" />
      <!-- 4 Convergence Axes -->
      <path d="M24 6V18M24 30V42M6 24H18M30 24H42" stroke="#2563EB" stroke-width="3.5" stroke-linecap="round" />
      <!-- Center Anchor -->
      <circle cx="24" cy="24" r="5" fill="#2563EB" />
      <circle cx="24" cy="24" r="2" fill="#FFFFFF" />
    </svg>

    <!-- Typography Block -->
    <div v-if="showText" class="brand-text-block">
      <div class="brand-name-group">
        <span class="brand-name-zh">天枢云基</span>
        <span class="brand-name-en">TIANSHU PAAS</span>
      </div>
      <span v-if="showBadge" class="brand-badge-pill">{{ badgeText }}</span>
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
  display: block;
  flex-shrink: 0;
  transition: transform 0.2s cubic-bezier(0.16, 1, 0.3, 1);

  &:hover {
    transform: scale(1.06);
  }
}

.brand-text-block {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.brand-name-group {
  display: flex;
  flex-direction: column;
  line-height: 1.15;

  .brand-name-zh {
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;
    letter-spacing: -0.01em;
    white-space: nowrap;
  }

  .brand-name-en {
    font-size: 8.5px;
    font-weight: 700;
    color: #64748b;
    letter-spacing: 0.1em;
    font-family: ui-monospace, "SF Mono", "Roboto Mono", monospace;
    text-transform: uppercase;
  }
}

.brand-badge-pill {
  padding: 1px 6px;
  border-radius: 4px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  color: #475569;
  font-family: ui-monospace, monospace;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.2;
}
</style>
