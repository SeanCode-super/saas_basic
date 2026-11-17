<template>
  <view class="app-icon" :style="{ width: size + 'px', height: size + 'px' }">
    <svg
      :width="size"
      :height="size"
      viewBox="0 0 24 24"
      fill="none"
      :stroke="color || 'currentColor'"
      :stroke-width="strokeWidth"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="svg-icon"
    >
      <!-- 品牌 Logo 晶透核 -->
      <g v-if="name === 'logo'">
        <polygon points="12 2 2 8.5 12 15 22 8.5 12 2" stroke="currentColor" fill="none" />
        <polyline points="2 15.5 12 22 22 15.5" />
        <polyline points="2 12 12 18.5 22 12" />
      </g>

      <!-- 审批 / 剪贴板 -->
      <g v-else-if="name === 'workflow' || name === 'clipboard'">
        <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" />
        <rect x="8" y="2" width="8" height="4" rx="1" ry="1" />
        <path d="m9 14 2 2 4-4" />
      </g>

      <!-- 发起流程 / 闪电 -->
      <g v-else-if="name === 'flash' || name === 'send'">
        <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
      </g>

      <!-- 客户 / 企业楼宇 -->
      <g v-else-if="name === 'building' || name === 'crm'">
        <rect x="4" y="2" width="16" height="20" rx="2" ry="2" />
        <path d="M9 22v-4h6v4" />
        <path d="M8 6h.01M16 6h.01M8 10h.01M16 10h.01M8 14h.01M16 14h.01" />
      </g>

      <!-- 外勤拜访 / 握手合作 -->
      <g v-else-if="name === 'visit' || name === 'handshake'">
        <path d="m11 17 2 2a1 1 0 0 0 1.4 0l6.6-6.6a2 2 0 0 0 0-2.8l-1.6-1.6a2 2 0 0 0-2.8 0L14 10.6" />
        <path d="m13 7.4-2.6-2.6a2 2 0 0 0-2.8 0L6 6.4a2 2 0 0 0 0 2.8L8.6 11.8" />
        <path d="m3 21 4-4" />
        <path d="m14 13.5-3.5 3.5" />
      </g>

      <!-- 多仓库存 / 盒子 -->
      <g v-else-if="name === 'box' || name === 'inventory'">
        <path d="M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z" />
        <path d="m3.3 7 8.7 5 8.7-5" />
        <path d="M12 22V12" />
      </g>

      <!-- 扫码核销 / 二维码扫描 -->
      <g v-else-if="name === 'scan' || name === 'camera'">
        <path d="M3 7V5a2 2 0 0 1 2-2h2" />
        <path d="M17 3h2a2 2 0 0 1 2 2v2" />
        <path d="M21 17v2a2 2 0 0 1-2 2h-2" />
        <path d="M7 21H5a2 2 0 0 1-2-2v-2" />
        <line x1="7" y1="12" x2="17" y2="12" />
      </g>

      <!-- 组织通讯录 / 群组 -->
      <g v-else-if="name === 'contacts' || name === 'users'">
        <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
        <circle cx="9" cy="7" r="4" />
        <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
        <path d="M16 3.13a4 4 0 0 1 0 7.75" />
      </g>

      <!-- 租户中枢 / 齿轮设置 -->
      <g v-else-if="name === 'settings' || name === 'gear'">
        <circle cx="12" cy="12" r="3" />
        <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
      </g>

      <!-- 消息通知 / 铃铛 -->
      <g v-else-if="name === 'bell' || name === 'notify'">
        <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
        <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
      </g>

      <!-- 租户空间 / 地球 -->
      <g v-else-if="name === 'globe' || name === 'tenant'">
        <circle cx="12" cy="12" r="10" />
        <line x1="2" y1="12" x2="22" y2="12" />
        <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
      </g>

      <!-- 清理缓存 / 扫帚 -->
      <g v-else-if="name === 'broom' || name === 'clean'">
        <path d="m14 12 4-4a2.83 2.83 0 0 0-4-4l-4 4" />
        <path d="m7 15 5-5" />
        <path d="M3 21l3-1 2-5-4-4-1 3-1 3 1 4z" />
      </g>

      <!-- 安全基座 / 盾牌 -->
      <g v-else-if="name === 'shield' || name === 'security'">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      </g>

      <!-- 引擎架构 / CPU -->
      <g v-else-if="name === 'cpu' || name === 'engine'">
        <rect x="4" y="4" width="16" height="16" rx="2" />
        <rect x="9" y="9" width="6" height="6" />
        <path d="M9 1v3M15 1v3M9 20v3M15 20v3M20 9h3M20 14h3M1 9h3M1 14h3" />
      </g>

      <!-- 电话 / 拨打 -->
      <g v-else-if="name === 'phone' || name === 'call'">
        <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
      </g>

      <!-- 邮件 -->
      <g v-else-if="name === 'mail' || name === 'email'">
        <rect width="20" height="16" x="2" y="4" rx="2" />
        <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
      </g>

      <!-- 箭头向右 -->
      <g v-else-if="name === 'chevron-right' || name === 'arrow-right'">
        <path d="m9 18 6-6-6-6" />
      </g>

      <!-- 箭头向左 / 返回 -->
      <g v-else-if="name === 'chevron-left' || name === 'arrow-left' || name === 'back'">
        <path d="m15 18-6-6 6-6" />
      </g>

      <!-- 默认通用图标 -->
      <g v-else>
        <circle cx="12" cy="12" r="10" />
        <line x1="12" y1="8" x2="12" y2="12" />
        <line x1="12" y1="16" x2="12.01" y2="16" />
      </g>
    </svg>
  </view>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    name: string;
    size?: number;
    color?: string;
    strokeWidth?: number;
  }>(),
  {
    size: 20,
    color: "",
    strokeWidth: 1.75
  }
);
</script>

<style scoped>
.app-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.svg-icon {
  display: block;
}
</style>
