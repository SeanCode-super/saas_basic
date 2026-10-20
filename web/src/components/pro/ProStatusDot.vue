<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    status?: "success" | "warning" | "danger" | "info" | "neutral";
    text?: string;
    pulse?: boolean;
    size?: "sm" | "md";
  }>(),
  {
    status: "neutral",
    text: "",
    pulse: false,
    size: "md"
  }
);

const dotClasses = computed(() => [
  "pro-status-dot__indicator",
  `pro-status-dot__indicator--${props.status}`,
  `pro-status-dot__indicator--${props.size}`,
  { "is-pulsing": props.pulse }
]);
</script>

<template>
  <span class="pro-status-dot">
    <span :class="dotClasses" />
    <span v-if="text || $slots.default" class="pro-status-dot__text">
      <slot>{{ text }}</slot>
    </span>
  </span>
</template>

<style scoped lang="scss">
.pro-status-dot {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  line-height: 1;
}

.pro-status-dot__indicator {
  position: relative;
  display: inline-block;
  flex: 0 0 auto;
  border-radius: 50%;

  &--sm {
    width: 6px;
    height: 6px;
  }

  &--md {
    width: 8px;
    height: 8px;
  }

  &--success {
    background-color: var(--sb-success-color);
    box-shadow: 0 0 0 2px rgb(5 150 105 / 0.2);
  }

  &--warning {
    background-color: var(--sb-warning-color);
    box-shadow: 0 0 0 2px rgb(217 119 6 / 0.2);
  }

  &--danger {
    background-color: var(--sb-danger-color);
    box-shadow: 0 0 0 2px rgb(220 38 38 / 0.2);
  }

  &--info {
    background-color: var(--sb-primary-color);
    box-shadow: 0 0 0 2px rgb(37 99 235 / 0.2);
  }

  &--neutral {
    background-color: var(--sb-text-tertiary);
    box-shadow: 0 0 0 2px rgb(148 163 184 / 0.2);
  }

  &.is-pulsing::after {
    position: absolute;
    top: -2px;
    left: -2px;
    width: calc(100% + 4px);
    height: calc(100% + 4px);
    border-radius: 50%;
    background-color: inherit;
    content: "";
    opacity: 0.6;
    animation: pro-dot-ping 2s cubic-bezier(0, 0, 0.2, 1) infinite;
  }
}

.pro-status-dot__text {
  color: var(--sb-text-primary);
  font-size: 13px;
  font-weight: 500;
}

@keyframes pro-dot-ping {
  75%,
  100% {
    opacity: 0;
    transform: scale(2.2);
  }
}
</style>
