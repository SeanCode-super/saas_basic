<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from "vue";
import { sha256 } from "@noble/hashes/sha2.js";
import { Close, RefreshRight } from "@element-plus/icons-vue";
import {
  fetchSmartCaptchaChallenge,
  verifySmartCaptcha,
  type SmartCaptchaChallenge
} from "@/api/modules/auth";

export interface CaptchaTicket {
  captchaId: string;
  passToken: string;
}

const props = withDefaults(
  defineProps<{
    dark?: boolean;
    modeHint?: string;
  }>(),
  {
    dark: false,
    modeHint: "SMART"
  }
);

const emit = defineEmits<{
  verified: [payload: CaptchaTicket];
  reset: [];
}>();

const PUZZLE_VIEW_WIDTH = 312;
const PIECE_STRIP_WIDTH = 60;
const MAX_SLIDE_PX = PUZZLE_VIEW_WIDTH - PIECE_STRIP_WIDTH;
const textEncoder = new TextEncoder();

const status = ref<"idle" | "checking" | "slider" | "verified" | "error">("idle");
const statusText = ref("点击进行智能安全核验");
const verifiedTicket = ref<CaptchaTicket | null>(null);
const sliderOpen = ref(false);
const sliderChallenge = ref<SmartCaptchaChallenge | null>(null);
const stageRef = ref<HTMLElement | null>(null);
const trackRef = ref<HTMLElement | null>(null);

const sliderState = reactive({
  x: 0,
  dragging: false,
  startClientX: 0,
  startTime: 0,
  trailCount: 0,
  shaking: false,
  passed: false,
  verifying: false,
  hint: "向右拖动滑块完成拼图验证"
});

let pendingResolve: ((ticket: CaptchaTicket | null) => void) | null = null;
let dragMaxTrackPx = MAX_SLIDE_PX;

const pieceLeftPercent = computed(() => `${(sliderState.x / PUZZLE_VIEW_WIDTH) * 100}%`);
const thumbLeftPx = computed(() => {
  const ratio = sliderState.x / MAX_SLIDE_PX;
  return `${2 + Math.round(ratio * dragMaxTrackPx)}px`;
});
const trackFillWidthPx = computed(() => {
  const ratio = sliderState.x / MAX_SLIDE_PX;
  return `${Math.round(ratio * dragMaxTrackPx) + 46}px`;
});

function bytesToHex(bytes: Uint8Array): string {
  let hex = "";
  for (let i = 0; i < bytes.length; i++) {
    hex += bytes[i].toString(16).padStart(2, "0");
  }
  return hex;
}

async function solvePowNonce(salt: string, difficulty: number): Promise<string> {
  const prefix = "0".repeat(Math.max(1, difficulty));
  for (let i = 0; i < 400_000; i++) {
    if (i > 0 && i % 4096 === 0) {
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
    const nonce = i.toString(16);
    const hashHex = bytesToHex(sha256(textEncoder.encode(`${salt}:${nonce}`)));
    if (hashHex.startsWith(prefix)) {
      return nonce;
    }
  }
  return "0";
}

function resolvePending(ticket: CaptchaTicket | null) {
  const waiter = pendingResolve;
  pendingResolve = null;
  if (waiter) {
    waiter(ticket);
  }
}

function getAxiosCode(error: unknown): string {
  if (error && typeof error === "object") {
    const code = (error as { response?: { data?: { code?: unknown } } }).response?.data?.code;
    if (typeof code === "string") return code;
  }
  return "";
}

function resetSliderState() {
  sliderState.x = 0;
  sliderState.dragging = false;
  sliderState.startClientX = 0;
  sliderState.startTime = 0;
  sliderState.trailCount = 0;
  sliderState.shaking = false;
  sliderState.passed = false;
  sliderState.verifying = false;
  sliderState.hint = "向右拖动滑块完成拼图验证";
}

async function loadSliderChallenge() {
  resetSliderState();
  status.value = "slider";
  statusText.value = "请完成滑动拼图核验";
  sliderOpen.value = true;
  try {
    const challenge = await fetchSmartCaptchaChallenge("SLIDER");
    sliderChallenge.value = challenge;
    syncTrackMetrics();
  } catch {
    status.value = "error";
    statusText.value = "安全挑战加载失败，点击重试";
    sliderOpen.value = false;
    resolvePending(null);
  }
}

function syncTrackMetrics() {
  const trackEl = trackRef.value;
  if (trackEl && trackEl.clientWidth > 56) {
    dragMaxTrackPx = trackEl.clientWidth - 48;
  } else {
    dragMaxTrackPx = MAX_SLIDE_PX;
  }
}

async function runVerificationFlow(forceSlider = false): Promise<CaptchaTicket | null> {
  if (status.value === "checking") {
    return null;
  }
  const preferSlider = forceSlider || props.modeHint === "SLIDER";
  if (preferSlider) {
    return openAndWaitSlider();
  }

  status.value = "checking";
  statusText.value = "正在进行智能环境核验...";
  try {
    const startMs = Date.now();
    const challenge = await fetchSmartCaptchaChallenge("AUTO");
    if (challenge.challengeType === "SLIDER") {
      sliderChallenge.value = challenge;
      resetSliderState();
      status.value = "slider";
      statusText.value = "请完成滑动拼图核验";
      sliderOpen.value = true;
      syncTrackMetrics();
      return await new Promise<CaptchaTicket | null>((resolve) => {
        pendingResolve = resolve;
      });
    }

    const [nonce] = await Promise.all([
      solvePowNonce(challenge.powSalt || "", challenge.powDifficulty || 3),
      new Promise((resolve) => setTimeout(resolve, 220))
    ]);

    const res = await verifySmartCaptcha({
      captchaId: challenge.captchaId,
      nonce,
      durationMs: Math.max(120, Date.now() - startMs),
      trailCount: 2
    });
    const ticket: CaptchaTicket = {
      captchaId: challenge.captchaId,
      passToken: res.passToken
    };
    verifiedTicket.value = ticket;
    status.value = "verified";
    statusText.value = "智能安全核验已通过";
    emit("verified", ticket);
    return ticket;
  } catch (error: unknown) {
    if (getAxiosCode(error) === "AUTH_CAPTCHA_UPGRADE_SLIDER") {
      return openAndWaitSlider();
    }
    status.value = "error";
    statusText.value = "核验未通过，请点击重试";
    return null;
  }
}

function openAndWaitSlider(): Promise<CaptchaTicket | null> {
  void loadSliderChallenge();
  return new Promise<CaptchaTicket | null>((resolve) => {
    resolvePending(null);
    pendingResolve = resolve;
  });
}

function handleBarClick() {
  if (status.value === "verified" || status.value === "checking") {
    return;
  }
  if (status.value === "slider") {
    sliderOpen.value = true;
    return;
  }
  void runVerificationFlow(false);
}

function handleSliderPointerDown(event: PointerEvent) {
  if (sliderState.verifying || sliderState.passed || !sliderChallenge.value) {
    return;
  }
  syncTrackMetrics();
  sliderState.dragging = true;
  sliderState.startClientX = event.clientX;
  sliderState.startTime = Date.now();
  sliderState.trailCount = 1;
  sliderState.shaking = false;
  window.addEventListener("pointermove", handleSliderPointerMove);
  window.addEventListener("pointerup", handleSliderPointerUp);
  window.addEventListener("pointercancel", handleSliderPointerUp);
}

function handleSliderPointerMove(event: PointerEvent) {
  if (!sliderState.dragging) {
    return;
  }
  const deltaTrack = Math.max(0, Math.min(dragMaxTrackPx, event.clientX - sliderState.startClientX));
  sliderState.x = Math.round((deltaTrack / Math.max(1, dragMaxTrackPx)) * MAX_SLIDE_PX);
  sliderState.trailCount += 1;
}

async function handleSliderPointerUp() {
  window.removeEventListener("pointermove", handleSliderPointerMove);
  window.removeEventListener("pointerup", handleSliderPointerUp);
  window.removeEventListener("pointercancel", handleSliderPointerUp);

  if (!sliderState.dragging || !sliderChallenge.value) {
    return;
  }
  sliderState.dragging = false;
  if (sliderState.x < 8) {
    sliderState.x = 0;
    return;
  }

  sliderState.verifying = true;
  sliderState.hint = "正在校验拼图轨迹...";
  const challengeId = sliderChallenge.value.captchaId;
  const durationMs = Math.max(100, Date.now() - sliderState.startTime);

  try {
    const res = await verifySmartCaptcha({
      captchaId: challengeId,
      slideX: sliderState.x,
      durationMs,
      trailCount: Math.max(2, sliderState.trailCount)
    });
    sliderState.passed = true;
    sliderState.verifying = false;
    sliderState.hint = "安全核验通过";
    const ticket: CaptchaTicket = {
      captchaId: challengeId,
      passToken: res.passToken
    };
    verifiedTicket.value = ticket;
    status.value = "verified";
    statusText.value = "滑动拼图核验已通过";
    emit("verified", ticket);

    setTimeout(() => {
      sliderOpen.value = false;
      resolvePending(ticket);
    }, 240);
  } catch {
    sliderState.verifying = false;
    sliderState.shaking = true;
    sliderState.hint = "拼图未对齐，已刷新图案";
    setTimeout(() => {
      sliderState.shaking = false;
      void loadSliderChallenge();
    }, 460);
  }
}

function closeSliderModal() {
  sliderOpen.value = false;
  if (status.value !== "verified") {
    status.value = "idle";
    statusText.value = "点击完成安全核验";
  }
  resolvePending(null);
}

async function ensureVerified(): Promise<CaptchaTicket | null> {
  if (verifiedTicket.value) {
    return verifiedTicket.value;
  }
  return runVerificationFlow(false);
}

function resetShield(escalateToSlider = false) {
  verifiedTicket.value = null;
  sliderChallenge.value = null;
  sliderOpen.value = false;
  resetSliderState();
  emit("reset");
  if (escalateToSlider) {
    status.value = "idle";
    statusText.value = "请点击完成滑动拼图核验";
  } else {
    status.value = "idle";
    statusText.value = "点击进行智能安全核验";
  }
}

onBeforeUnmount(() => {
  window.removeEventListener("pointermove", handleSliderPointerMove);
  window.removeEventListener("pointerup", handleSliderPointerUp);
  window.removeEventListener("pointercancel", handleSliderPointerUp);
  resolvePending(null);
});

defineExpose({
  ensureVerified,
  resetShield
});
</script>

<template>
  <div class="smart-shield" :class="{ 'is-dark': dark }">
    <!-- Turnstile 风格紧凑智能核验条 -->
    <button
      type="button"
      class="shield-bar"
      :class="`state--${status}`"
      @click="handleBarClick"
    >
      <span class="shield-indicator" aria-hidden="true">
        <span v-if="status === 'checking'" class="shield-spinner" />
        <svg v-else-if="status === 'verified'" viewBox="0 0 20 20" fill="none" class="shield-check">
          <path d="M4.5 10.5L8.5 14.5L15.8 6.5" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <svg v-else viewBox="0 0 20 20" fill="none" class="shield-emblem">
          <path d="M10 2.5L4 5.2V9.8C4 13.7 6.5 16.9 10 18C13.5 16.9 16 13.7 16 9.8V5.2L10 2.5Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" />
          <circle cx="10" cy="10" r="2" fill="currentColor" />
        </svg>
      </span>
      <span class="shield-label">{{ statusText }}</span>
      <span class="shield-brand">天枢智盾</span>
    </button>

    <!-- 高风险触发 / 滑块拼图毛玻璃弹出层 -->
    <div v-if="sliderOpen" class="slider-overlay" role="dialog" aria-label="安全滑动拼图验证">
      <div class="slider-card" :class="{ 'is-shaking': sliderState.shaking }">
        <header class="slider-header">
          <span class="slider-title">拖动滑块完成安全拼图</span>
          <div class="slider-actions">
            <button type="button" class="slider-icon-btn" title="换一张拼图" @click="loadSliderChallenge">
              <el-icon><RefreshRight /></el-icon>
            </button>
            <button type="button" class="slider-icon-btn" title="关闭" @click="closeSliderModal">
              <el-icon><Close /></el-icon>
            </button>
          </div>
        </header>

        <div ref="stageRef" class="puzzle-stage">
          <img
            v-if="sliderChallenge?.backgroundImageDataUrl"
            :src="sliderChallenge.backgroundImageDataUrl"
            alt="安全拼图背景"
            class="puzzle-bg"
            draggable="false"
          />
          <img
            v-if="sliderChallenge?.pieceImageDataUrl"
            :src="sliderChallenge.pieceImageDataUrl"
            alt="拼图滑块"
            class="puzzle-piece"
            :style="{ left: pieceLeftPercent }"
            draggable="false"
          />
          <div v-if="!sliderChallenge?.backgroundImageDataUrl" class="puzzle-loading">正在生成拼图...</div>
          <div v-if="sliderState.passed" class="puzzle-pass-banner">✓ 安全核验通过</div>
        </div>

        <div ref="trackRef" class="slider-track" :class="{ 'is-passed': sliderState.passed }">
          <div class="slider-fill" :style="{ width: trackFillWidthPx }" />
          <span v-if="sliderState.x < 18 && !sliderState.passed" class="track-hint">{{ sliderState.hint }}</span>
          <button
            type="button"
            class="slider-thumb"
            :class="{ 'is-dragging': sliderState.dragging, 'is-passed': sliderState.passed }"
            :style="{ left: thumbLeftPx }"
            aria-label="拖动拼图滑块"
            @pointerdown.prevent="handleSliderPointerDown"
          >
            <svg v-if="sliderState.passed" viewBox="0 0 20 20" fill="none">
              <path d="M5 10.5L8.5 14L15.5 6.5" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <svg v-else viewBox="0 0 20 20" fill="none">
              <path d="M7.5 5.5L12 10L7.5 14.5M12 5.5L16.5 10L12 14.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.smart-shield {
  position: relative;
  width: 100%;
}

.shield-bar {
  width: 100%;
  height: 44px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid rgba(203, 213, 225, 0.85);
  background: rgba(248, 250, 252, 0.92);
  color: #334155;
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: border-color 180ms ease, background 180ms ease, box-shadow 180ms ease;

  &:hover {
    border-color: var(--portal-glow, #4f46e5);
    background: #ffffff;
  }

  &.state--verified {
    border-color: rgba(16, 185, 129, 0.45);
    background: rgba(236, 253, 245, 0.92);
    color: #065f46;

    .shield-indicator {
      background: #10b981;
      border-color: #10b981;
      color: #ffffff;
    }
  }

  &.state--error {
    border-color: rgba(239, 68, 68, 0.45);
    color: #b91c1c;
  }
}

.is-dark .shield-bar {
  border-color: rgba(148, 163, 184, 0.28);
  background: rgba(17, 25, 48, 0.84);
  color: #e2e8f0;

  &:hover {
    border-color: var(--portal-glow, #6366f1);
    background: rgba(22, 33, 62, 0.94);
  }

  &.state--verified {
    border-color: rgba(16, 185, 129, 0.52);
    background: rgba(6, 78, 59, 0.36);
    color: #a7f3d0;
  }
}

.shield-indicator {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.36);
  background: rgba(var(--portal-glow-rgb, 99, 102, 241), 0.12);
  color: var(--portal-glow, #4f46e5);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 180ms ease, color 180ms ease;

  svg {
    width: 15px;
    height: 15px;
  }
}

.shield-spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid rgba(var(--portal-glow-rgb, 99, 102, 241), 0.24);
  border-top-color: var(--portal-glow, #4f46e5);
  animation: shieldSpin 0.7s linear infinite;
}

.shield-label {
  flex: 1;
  text-align: left;
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shield-brand {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: #64748b;
  flex-shrink: 0;
}

.is-dark .shield-brand {
  color: #94a3b8;
}

/* 滑动拼图弹出层 */
.slider-overlay {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: rgba(15, 23, 42, 0.48);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.slider-card {
  width: 344px;
  max-width: 100%;
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 24px 60px -12px rgba(15, 23, 42, 0.35);
  color: #0f172a;
  user-select: none;

  &.is-shaking {
    animation: sliderShake 0.36s ease;
  }
}

.is-dark .slider-card {
  background: rgba(13, 21, 42, 0.95);
  border-color: rgba(148, 163, 184, 0.28);
  color: #f8fafc;
  box-shadow: 0 28px 68px -12px rgba(0, 0, 0, 0.72);
}

.slider-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.slider-title {
  font-size: 13.5px;
  font-weight: 600;
}

.slider-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.slider-icon-btn {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  background: transparent;
  color: inherit;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;

  &:hover {
    border-color: var(--portal-glow, #4f46e5);
    color: var(--portal-glow, #4f46e5);
  }
}

.puzzle-stage {
  position: relative;
  width: 100%;
  aspect-ratio: 312 / 156;
  border-radius: 12px;
  overflow: hidden;
  background: #091128;
  border: 1px solid rgba(148, 163, 184, 0.24);
}

.puzzle-bg {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.puzzle-piece {
  position: absolute;
  top: 0;
  height: 100%;
  width: 19.2307%;
  pointer-events: none;
  filter: drop-shadow(0 4px 10px rgba(0, 0, 0, 0.55));
}

.puzzle-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12.5px;
  color: #cbd5e1;
}

.puzzle-pass-banner {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 28px;
  background: rgba(16, 185, 129, 0.88);
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.slider-track {
  position: relative;
  margin-top: 12px;
  height: 42px;
  border-radius: 9999px;
  border: 1px solid rgba(203, 213, 225, 0.85);
  background: #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;

  &.is-passed {
    border-color: #10b981;
  }
}

.is-dark .slider-track {
  border-color: rgba(148, 163, 184, 0.26);
  background: rgba(15, 23, 42, 0.88);
}

.slider-fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: rgba(var(--portal-glow-rgb, 99, 102, 241), 0.20);
  pointer-events: none;
}

.slider-track.is-passed .slider-fill {
  background: rgba(16, 185, 129, 0.26);
}

.track-hint {
  font-size: 12px;
  color: #64748b;
  pointer-events: none;
}

.is-dark .track-hint {
  color: #94a3b8;
}

.slider-thumb {
  position: absolute;
  top: 2px;
  width: 44px;
  height: 36px;
  border-radius: 9999px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  background: linear-gradient(110deg, var(--portal-glow, #6366f1), var(--portal-secondary, #38bdf8));
  color: #ffffff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  touch-action: none;
  box-shadow: 0 4px 12px rgba(var(--portal-glow-rgb, 99, 102, 241), 0.42);

  svg {
    width: 17px;
    height: 17px;
  }

  &.is-dragging {
    cursor: grabbing;
  }

  &.is-passed {
    background: #10b981;
    box-shadow: 0 4px 12px rgba(16, 185, 129, 0.45);
  }
}

@keyframes shieldSpin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes sliderShake {
  0%,
  100% {
    transform: translateX(0);
  }

  25% {
    transform: translateX(-6px);
  }

  75% {
    transform: translateX(6px);
  }
}
</style>
