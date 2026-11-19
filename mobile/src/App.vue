<script setup lang="ts">
import { onLaunch, onShow, onHide } from "@dcloudio/uni-app";

onLaunch(() => {
  console.log("Antigravity Mobile Workspace App Launch - iOS 27 Liquid Glass Enabled");
});

onShow(() => {
  console.log("App Show");
});

onHide(() => {
  console.log("App Hide");
});
</script>

<style>
/* =============================================================================
   iOS 27 Liquid Glass 全局跨端移动设计系统
   ============================================================================= */

:root {
  --ios-glass-bg: rgba(255, 255, 255, 0.88);
  --ios-glass-bg-subtle: rgba(255, 255, 255, 0.72);
  --ios-glass-bg-elevated: rgba(255, 255, 255, 0.98);
  --ios-glass-border: rgba(255, 255, 255, 0.95);
  --ios-glass-border-subtle: rgba(226, 232, 240, 0.80);
  --ios-glass-shadow: inset 0 1px 1px 0 rgba(255, 255, 255, 0.98), 0 8px 24px -4px rgba(12, 34, 56, 0.04), 0 2px 6px -1px rgba(12, 34, 56, 0.02);
  --ios-glass-shadow-sm: inset 0 1px 1px 0 rgba(255, 255, 255, 0.95), 0 4px 12px rgba(12, 34, 56, 0.03);
  --ios-text-primary: #0f172a;
  --ios-text-secondary: #475569;
  --ios-text-tertiary: #94a3b8;
  --ios-primary: #2563eb;
  --ios-primary-dark: #1d4ed8;
  --ios-radius-lg: 18px;
  --ios-radius-md: 14px;
  --ios-radius-sm: 10px;
  --ios-radius-full: 9999px;
}

page {
  min-height: 100vh;
  background-color: #f1f5f9;
  background-image:
    radial-gradient(circle at 14% 10%, rgba(99, 102, 241, 0.12) 0%, transparent 48%),
    radial-gradient(circle at 86% 14%, rgba(14, 165, 233, 0.10) 0%, transparent 46%),
    radial-gradient(circle at 50% 88%, rgba(168, 85, 247, 0.07) 0%, transparent 52%),
    linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  background-size: 100% 100%;
  background-position: center top;
  background-attachment: fixed;
  background-repeat: no-repeat;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Helvetica Neue", "PingFang SC", "Segoe UI", Roboto, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #0f172a;
  box-sizing: border-box;
  -webkit-overflow-scrolling: touch;
  scroll-behavior: smooth;
  overscroll-behavior-y: contain;
}

*, *::before, *::after {
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}

/* 页面切换平滑微动效，硬件加速淡入，杜绝顿挫与卡顿 (绝不在 uni-page 施加 transform，避免劫持 fixed 容器导致悬浮按钮随页面滚动) */
uni-page {
  animation: iosPageFadeIn 0.18s cubic-bezier(0.16, 1, 0.3, 1);
  will-change: opacity;
}

@keyframes iosPageFadeIn {
  from {
    opacity: 0.88;
  }
  to {
    opacity: 1;
  }
}

/* 全局可交互元素极致丝滑触感即时反馈 (Snappy Haptic Response) */
button,
uni-button,
.btn,
.ios-nav-back-pill,
.ios27-chip,
.kpi-card,
.task-card,
.grid-item,
.mode-pill,
.seg-item,
.tab-btn {
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
  cursor: pointer;
  transition: transform 0.14s cubic-bezier(0.2, 0.8, 0.2, 1), opacity 0.14s ease, box-shadow 0.14s ease;
}

button:active,
uni-button:active,
.btn:active,
.ios-nav-back-pill:active,
.ios27-chip:active,
.kpi-card:active,
.task-card:active,
.grid-item:active,
.mode-pill:active,
.seg-item:active,
.tab-btn:active {
  transform: scale(0.965) !important;
  opacity: 0.88 !important;
  transition: transform 0.06s ease, opacity 0.06s ease !important;
}

/* #ifdef H5 */
@media screen and (min-width: 640px) {
  body {
    display: flex;
    justify-content: center;
    background: #070d1e !important;
  }
  uni-app, #app {
    max-width: 430px;
    width: 100%;
    margin: 0 auto;
    min-height: 100vh;
    box-shadow: 0 0 60px rgba(0, 0, 0, 0.50);
    position: relative;
    overflow-x: hidden;
  }
}
/* #endif */

/* 移除默认顶部导航条，实现全屏沉浸式无界体验 */
uni-page-head {
  display: none !important;
  height: 0 !important;
}

uni-page-head .uni-page-head {
  display: none !important;
}

/* 消除 uni-app 默认原生 button::after 灰色方框描边，释放现代高定材质质感 */
button::after,
uni-button::after {
  border: none !important;
  display: none !important;
}

/* =============================================================================
   iOS 27 沉浸式二级页面顶栏导航规范
   ============================================================================= */

.ios-nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 44px;
  margin-bottom: 12px;
  box-sizing: border-box;
}

.ios-nav-back-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  padding: 0 12px 0 8px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(20px) saturate(190%);
  -webkit-backdrop-filter: blur(20px) saturate(190%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.95), 0 2px 6px rgba(15, 23, 42, 0.04);
  color: #0f172a;
  cursor: pointer;
  transition: all 0.16s ease;
}

.ios-nav-back-pill:active {
  transform: scale(0.94);
  background: rgba(255, 255, 255, 0.9);
}

.ios-nav-back-text {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  letter-spacing: -0.01em;
}

.ios-nav-title {
  font-size: 15.5px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.2px;
}

.ios-nav-placeholder {
  width: 58px;
}

/* 移动端 H5 底部 TabBar：iOS 27 悬浮晶透胶囊 (YouTube 最新设计语言) */
uni-tabbar {
  position: fixed !important;
  left: 0 !important;
  right: 0 !important;
  bottom: 0 !important;
  width: 100% !important;
  height: 0 !important;
  z-index: 9999 !important;
  display: block !important;
  background: transparent !important;
  backdrop-filter: none !important;
  -webkit-backdrop-filter: none !important;
  border: none !important;
  box-shadow: none !important;
  pointer-events: none !important;
}

uni-tabbar .uni-placeholder {
  display: none !important;
  height: 0 !important;
}

uni-tabbar .uni-tabbar-border {
  display: none !important;
}

uni-tabbar .uni-tabbar {
  position: fixed !important;
  left: 14px !important;
  right: 14px !important;
  bottom: calc(14px + env(safe-area-inset-bottom)) !important;
  width: auto !important;
  max-width: 420px !important;
  margin: 0 auto !important;
  height: 62px !important;
  display: flex !important;
  align-items: center !important;
  justify-content: space-around !important;
  padding: 0 6px !important;
  box-sizing: border-box !important;
  border-radius: 9999px !important;
  background: rgba(255, 255, 255, 0.80) !important;
  backdrop-filter: blur(36px) saturate(220%) contrast(102%) !important;
  -webkit-backdrop-filter: blur(36px) saturate(220%) contrast(102%) !important;
  border: 1px solid rgba(255, 255, 255, 0.98) !important;
  box-shadow:
    0 18px 40px -8px rgba(15, 23, 42, 0.18),
    0 6px 16px rgba(15, 23, 42, 0.06),
    inset 0 1.5px 2px 0 rgba(255, 255, 255, 1) !important;
  z-index: 9999 !important;
  pointer-events: auto !important;
  overflow: hidden !important;
}

uni-tabbar .uni-tabbar__item {
  flex: 1 !important;
  height: 50px !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  border-radius: 9999px !important;
  padding: 0 !important;
  margin: 0 2px !important;
  transition: transform 0.12s ease, background 0.15s ease !important;
  cursor: pointer !important;
  -webkit-tap-highlight-color: transparent !important;
  touch-action: manipulation !important;
}

uni-tabbar .uni-tabbar__bd {
  height: 100% !important;
  display: flex !important;
  flex-direction: column !important;
  align-items: center !important;
  justify-content: center !important;
  padding: 0 !important;
  gap: 2px !important;
}

uni-tabbar .uni-tabbar__icon {
  width: 22px !important;
  height: 22px !important;
  margin: 0 !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

uni-tabbar .uni-tabbar__icon img {
  width: 22px !important;
  height: 22px !important;
  object-fit: contain !important;
  transition: transform 0.22s cubic-bezier(0.34, 1.56, 0.64, 1) !important;
}

uni-tabbar .uni-tabbar__label {
  font-size: 10px !important;
  font-weight: 550 !important;
  margin: 0 !important;
  line-height: 1.1 !important;
  letter-spacing: -0.01em !important;
  transition: color 0.18s ease, font-weight 0.18s ease !important;
}

/* 激活项高光药丸反馈 (YouTube 风格柔和激活底衬) */
uni-tabbar .uni-tabbar__item:has(img[src*="-active"]) {
  background: rgba(37, 99, 235, 0.09) !important;
  box-shadow: inset 0 1px 1.5px rgba(37, 99, 235, 0.15) !important;
}

uni-tabbar .uni-tabbar__item:has(img[src*="-active"]) .uni-tabbar__icon img {
  transform: scale(1.12) !important;
}

uni-tabbar .uni-tabbar__item:has(img[src*="-active"]) .uni-tabbar__label {
  color: #1d4ed8 !important;
  font-weight: 700 !important;
}

/* 触摸下压弹性动效 */
uni-tabbar .uni-tabbar__item:active {
  transform: scale(0.92) !important;
  transition: transform 0.06s ease !important;
}

/* 登录页、模态弹窗以及所有二级页面（带 .ios-nav-bar）一律彻底屏蔽底部 TabBar，杜绝遮挡操作按钮 */
body:has(.mobile-login-page) uni-tabbar,
body:has(.mobile-login-shell) uni-tabbar,
body:has(.login-card) uni-tabbar,
body:has(.login-box) uni-tabbar,
body:has(.modal-overlay) uni-tabbar,
body:has(.ios-nav-bar) uni-tabbar,
body:has(.mobile-edit-page) uni-tabbar,
body:has(.mobile-password-page) uni-tabbar,
body:has(.mobile-sessions-page) uni-tabbar,
body:has(.follow-up-page) uni-tabbar,
body:has(.workflow-apply-page) uni-tabbar,
body:has(.ai-page) uni-tabbar,
body:has(.notify-page) uni-tabbar,
body:has(.inventory-page) uni-tabbar,
body:has(uni-page[data-page*="profile/edit"]) uni-tabbar,
body:has(uni-page[data-page*="profile/password"]) uni-tabbar,
body:has(uni-page[data-page*="profile/sessions"]) uni-tabbar,
body:has(uni-page[data-page*="crm/follow-up"]) uni-tabbar,
body:has(uni-page[data-page*="workflow/apply"]) uni-tabbar,
body:has(uni-page[data-page*="erp/inventory"]) uni-tabbar,
body:has(uni-page[data-page*="notify/index"]) uni-tabbar,
body:has(uni-page[data-page*="ai/index"]) uni-tabbar {
  display: none !important;
  visibility: hidden !important;
  pointer-events: none !important;
  height: 0 !important;
  overflow: hidden !important;
}

/* =============================================================================
   iOS 27 晶透标签横向胶囊栏规范 (YouTube 同款 Chip / Tag Filter Bar)
   ============================================================================= */

.ios27-chip-scroll {
  width: 100%;
  white-space: nowrap;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  padding: 6px 14px 10px;
  box-sizing: border-box;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.ios27-chip-scroll::-webkit-scrollbar {
  display: none !important;
  width: 0 !important;
  height: 0 !important;
}

.ios27-chip-group {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.ios27-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 33px;
  padding: 0 15px;
  border-radius: 9999px;
  font-size: 13px;
  font-weight: 550;
  letter-spacing: -0.01em;
  white-space: nowrap;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(24px) saturate(190%);
  -webkit-backdrop-filter: blur(24px) saturate(190%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  color: #334155;
  box-shadow:
    inset 0 1px 1px rgba(255, 255, 255, 0.95),
    0 2px 6px rgba(15, 23, 42, 0.04);
  cursor: pointer;
  /* 杜绝初次渲染全属性过渡引起的背景色及阴影跳动，仅保留触摸即时弹性 */
  transition: transform 0.08s ease, opacity 0.08s ease;
}

.ios27-chip:active {
  transform: scale(0.94);
}

/* 激活态：深黑曜石高对比度胶囊 (YouTube 同款) */
.ios27-chip.is-active {
  background: #0f172a !important;
  border-color: #0f172a !important;
  color: #ffffff !important;
  font-weight: 650 !important;
  box-shadow:
    0 4px 14px rgba(15, 23, 42, 0.22),
    inset 0 1px 0 rgba(255, 255, 255, 0.2) !important;
}

/* 蓝色激活变体 */
.ios27-chip.is-active-blue {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%) !important;
  border-color: #1d4ed8 !important;
  color: #ffffff !important;
  font-weight: 650 !important;
  box-shadow:
    0 4px 14px rgba(37, 99, 235, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.25) !important;
}

.ios27-chip-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 9999px;
  background: rgba(148, 163, 184, 0.2);
  color: inherit;
  font-weight: 600;
}

.ios27-chip.is-active .ios27-chip-badge {
  background: rgba(255, 255, 255, 0.25);
  color: #ffffff;
}

.ios27-chip.is-active-blue .ios27-chip-badge {
  background: rgba(255, 255, 255, 0.25);
  color: #ffffff;
}

/* 滚动条轻量化 */
::-webkit-scrollbar {
  width: 4px;
  height: 4px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.3);
  border-radius: 4px;
}

/* 常用交互与卡片类 */
.glass-surface {
  background: var(--ios-glass-bg);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid var(--ios-glass-border);
  box-shadow: var(--ios-glass-shadow);
  border-radius: var(--ios-radius-lg);
}
</style>
