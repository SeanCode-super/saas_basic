<script setup lang="ts">
withDefaults(
  defineProps<{
    size?: number;
    showText?: boolean;
    showBadge?: boolean;
    badgeText?: string;
  }>(),
  {
    size: 28,
    showText: false,
    showBadge: false,
    badgeText: "PaaS"
  }
);
</script>

<template>
  <div class="tianshu-logo-wrapper" :class="{ 'with-text': showText }">
    <!-- Tianshu Geometric Pivot Star & Cloud Base SVG -->
    <svg
      :width="size"
      :height="size"
      viewBox="0 0 48 48"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      class="tianshu-mark-svg"
    >
      <defs>
        <!-- Gradients -->
        <linearGradient id="ts-grad-top-comp" x1="12" y1="4" x2="36" y2="24" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="#38bdf8" />
          <stop offset="100%" stop-color="#2563eb" />
        </linearGradient>
        <linearGradient id="ts-grad-left-comp" x1="6" y1="12" x2="24" y2="42" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="#0ea5e9" />
          <stop offset="100%" stop-color="#1d4ed8" />
        </linearGradient>
        <linearGradient id="ts-grad-right-comp" x1="42" y1="12" x2="24" y2="42" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="#1d4ed8" />
          <stop offset="100%" stop-color="#0f172a" />
        </linearGradient>
        <!-- Star Pivot Glow Filter -->
        <filter id="ts-glow-comp" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="1.2" result="blur" />
          <feComposite in="SourceGraphic" in2="blur" operator="over" />
        </filter>
      </defs>

      <!-- Isometric Cloud Foundation Layers -->
      <path d="M24 4L42 14.5L24 25L6 14.5L24 4Z" fill="url(#ts-grad-top-comp)" />
      <path d="M6 14.5L24 25V44L6 33.5V14.5Z" fill="url(#ts-grad-left-comp)" />
      <path d="M42 14.5L24 25V44L42 33.5V14.5Z" fill="url(#ts-grad-right-comp)" />

      <!-- High-tech Grid Lattice -->
      <path d="M24 9L36 16L24 23L12 16L24 9Z" fill="#ffffff" fill-opacity="0.28" />
      <path d="M12 18L24 25V37L12 30V18Z" fill="#000000" fill-opacity="0.18" />
      <path d="M36 18L24 25V37L36 30V18Z" fill="#ffffff" fill-opacity="0.16" />

      <!-- Central Tianshu Star Pivot (北斗天枢星核) -->
      <circle cx="24" cy="24" r="4.2" fill="#ffffff" filter="url(#ts-glow-comp)" />
      <circle cx="24" cy="24" r="2.2" fill="#00e5ff" />
      
      <!-- 4-Axis Ray Radiance -->
      <path
        d="M24 12L25.8 22.2L36 24L25.8 25.8L24 36L22.2 25.8L12 24L22.2 22.2L24 12Z"
        fill="#ffffff"
        filter="url(#ts-glow-comp)"
        opacity="0.95"
      />
      <!-- Top Anchor Spark -->
      <circle cx="24" cy="4" r="1.5" fill="#7dd3fc" />
    </svg>

    <!-- Optional Brand Typography -->
    <div v-if="showText" class="tianshu-text-block">
      <div class="tianshu-title-line">
        <span class="brand-chinese">天枢云基</span>
        <span class="brand-english">Tianshu PaaS</span>
      </div>
      <span v-if="showBadge" class="brand-badge-pill">{{ badgeText }}</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.tianshu-logo-wrapper {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  user-select: none;
}

.tianshu-mark-svg {
  display: block;
  flex-shrink: 0;
  transition: transform 0.25s ease;
  filter: drop-shadow(0 2px 4px rgb(37 99 235 / 0.18));

  &:hover {
    transform: scale(1.05) rotate(2deg);
  }
}

.tianshu-text-block {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.tianshu-title-line {
  display: flex;
  flex-direction: column;
  line-height: 1.15;

  .brand-chinese {
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;
    letter-spacing: 0.02em;
    white-space: nowrap;
  }

  .brand-english {
    font-size: 9px;
    font-weight: 600;
    color: #64748b;
    letter-spacing: 0.08em;
    font-family: ui-monospace, "SF Mono", monospace;
    text-transform: uppercase;
  }
}

.brand-badge-pill {
  padding: 1px 6px;
  border-radius: 9999px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #2563eb;
  font-family: ui-monospace, monospace;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.2;
}
</style>
