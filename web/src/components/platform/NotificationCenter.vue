<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  Bell,
  Check,
  CircleCheck,
  DocumentChecked,
  Promotion,
  Refresh,
  Timer,
  WarningFilled
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { useNotificationStore } from "@/stores/modules/notification";
import { sendTestNotification } from "@/api/modules/notify";
import type { NotifyCategory, NotifyMessage } from "@/api/modules/notify";

const router = useRouter();
const notificationStore = useNotificationStore();
const activeCategory = ref<NotifyCategory>("ALL");
const popoverVisible = ref(false);
const testSending = ref(false);

const filteredMessages = computed(() => {
  if (activeCategory.value === "ALL") {
    return notificationStore.messages;
  }
  return notificationStore.messages.filter((m) => m.category === activeCategory.value);
});

function handleCardClick(item: NotifyMessage) {
  if (item.readStatus === 0) {
    notificationStore.markAsRead(item.id);
  }
  if (item.bizType === "WORKFLOW_TASK") {
    popoverVisible.value = false;
    router.push("/workflow");
  } else if (item.bizType === "SECURITY_ALERT") {
    popoverVisible.value = false;
    router.push("/audit");
  }
}

async function handleSendTest() {
  testSending.value = true;
  try {
    const randomTypes = [
      {
        category: "WORKFLOW",
        title: "【待办审批】紧急采买申请等待审批",
        content: `流程单号【WF-${Date.now().toString().slice(-6)}】发起人【运维部】提交了【紧急服务器扩容审批】，请尽快处理。`,
        bizType: "WORKFLOW_TASK",
        bizId: "1"
      },
      {
        category: "AUDIT",
        title: "【安全审计】检测到跨网段访问尝试",
        content: "安全防线已拦截一次非授权 IP 尝试访问管理接口的操作，规则库已更新。",
        bizType: "SECURITY_ALERT",
        bizId: "SEC-LOG"
      },
      {
        category: "SYSTEM",
        title: "【系统公告】平台双令牌自动刷新验证通过",
        content: "用户无感凭据自动续期链路工作正常，会话持久度持续保持安全最优。",
        bizType: "SYSTEM_ANNOUNCE",
        bizId: "SYS-01"
      }
    ];

    const pick = randomTypes[Math.floor(Math.random() * randomTypes.length)];
    await sendTestNotification(pick);
    ElMessage.success("已通过 WebSocket 推送实时通知");
  } catch (err: any) {
    ElMessage.error(err?.message || "发送测试消息失败");
  } finally {
    testSending.value = false;
  }
}

onMounted(() => {
  notificationStore.loadUnreadCount();
  notificationStore.loadMessages();
  notificationStore.connectWebSocket();
});
</script>

<template>
  <el-popover
    v-model:visible="popoverVisible"
    placement="bottom-end"
    :width="380"
    trigger="click"
    popper-class="notification-center-popover"
  >
    <template #reference>
      <button class="notif-trigger-btn" aria-label="消息通知中心">
        <el-badge
          :value="notificationStore.unreadCount"
          :hidden="notificationStore.unreadCount === 0"
          :max="99"
          class="notif-badge"
        >
          <el-icon class="bell-icon"><Bell /></el-icon>
        </el-badge>
      </button>
    </template>

    <div class="notif-panel">
      <!-- Header -->
      <div class="notif-header">
        <div class="header-left">
          <span class="header-title">通知中心</span>
          <div class="ws-status" :class="{ 'is-live': notificationStore.wsConnected }">
            <span class="pulse-dot" />
            <span class="status-text">{{ notificationStore.wsConnected ? '实时推送' : '连接中' }}</span>
          </div>
        </div>
        <div class="header-right">
          <el-button
            link
            type="primary"
            size="small"
            :disabled="notificationStore.unreadCount === 0"
            @click="notificationStore.markAllRead()"
          >
            全部已读
          </el-button>
        </div>
      </div>

      <!-- Categories Filter Tabs -->
      <div class="notif-tabs">
        <button
          class="tab-btn"
          :class="{ 'is-active': activeCategory === 'ALL' }"
          @click="activeCategory = 'ALL'"
        >
          全部
        </button>
        <button
          class="tab-btn"
          :class="{ 'is-active': activeCategory === 'WORKFLOW' }"
          @click="activeCategory = 'WORKFLOW'"
        >
          待办审批
        </button>
        <button
          class="tab-btn"
          :class="{ 'is-active': activeCategory === 'AUDIT' }"
          @click="activeCategory = 'AUDIT'"
        >
          安全告警
        </button>
        <button
          class="tab-btn"
          :class="{ 'is-active': activeCategory === 'SYSTEM' }"
          @click="activeCategory = 'SYSTEM'"
        >
          系统公告
        </button>
      </div>

      <!-- Message List -->
      <div v-loading="notificationStore.loading" class="notif-list">
        <div
          v-for="item in filteredMessages"
          :key="item.id"
          class="notif-item"
          :class="{ 'is-unread': item.readStatus === 0 }"
          @click="handleCardClick(item)"
        >
          <div class="item-icon-box" :class="`category-${item.category.toLowerCase()}`">
            <el-icon v-if="item.category === 'WORKFLOW'"><Timer /></el-icon>
            <el-icon v-else-if="item.category === 'AUDIT'"><WarningFilled /></el-icon>
            <el-icon v-else><Bell /></el-icon>
          </div>

          <div class="item-content">
            <div class="item-title-row">
              <span class="item-title">{{ item.title }}</span>
              <span v-if="item.readStatus === 0" class="unread-dot" />
            </div>
            <p class="item-desc">{{ item.content }}</p>
            <div class="item-time">
              {{ item.createdAt?.replace('T', ' ').substring(0, 19) }}
            </div>
          </div>
        </div>

        <div v-if="filteredMessages.length === 0" class="notif-empty">
          <el-icon :size="32" class="empty-icon"><CircleCheck /></el-icon>
          <span>暂无未读消息</span>
        </div>
      </div>

      <!-- Footer Actions -->
      <div class="notif-footer">
        <el-button
          size="small"
          plain
          :icon="Promotion"
          :loading="testSending"
          @click="handleSendTest"
        >
          模拟推送测试
        </el-button>
        <el-button
          size="small"
          link
          type="primary"
          @click="notificationStore.loadMessages()"
        >
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </div>
  </el-popover>
</template>

<style scoped>
.notif-trigger-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: all 0.2s ease;
  padding: 0;
}

.notif-trigger-btn:hover {
  background: var(--el-fill-color-light);
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-7);
}

.bell-icon {
  font-size: 17px;
}

.notif-badge :deep(.el-badge__content) {
  font-size: 11px;
  height: 16px;
  line-height: 16px;
  padding: 0 5px;
}

.notif-panel {
  display: flex;
  flex-direction: column;
}

.notif-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.ws-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 12px;
  background: var(--el-fill-color-darker);
  color: var(--el-text-color-secondary);
}

.ws-status.is-live {
  background: rgb(16 185 129 / 0.12);
  color: #10b981;
}

.pulse-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentcolor;
}

.ws-status.is-live .pulse-dot {
  box-shadow: 0 0 6px #10b981;
}

.notif-tabs {
  display: flex;
  gap: 6px;
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.tab-btn {
  background: transparent;
  border: none;
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 6px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn:hover {
  color: var(--el-color-primary);
}

.tab-btn.is-active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.notif-list {
  max-height: 340px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.notif-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 8px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  cursor: pointer;
  transition: background-color 0.2s;
  border-radius: 6px;
}

.notif-item:hover {
  background-color: var(--el-fill-color-light);
}

.notif-item.is-unread {
  background-color: rgb(37 99 235 / 0.03);
}

.item-icon-box {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
  margin-top: 2px;
}

.category-workflow {
  background: rgb(245 158 11 / 0.12);
  color: #f59e0b;
}

.category-audit {
  background: rgb(239 68 68 / 0.12);
  color: #ef4444;
}

.category-system {
  background: rgb(37 99 235 / 0.12);
  color: #2563eb;
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.item-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.unread-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #2563eb;
  flex-shrink: 0;
}

.item-desc {
  font-size: 12px;
  color: var(--el-text-color-regular);
  margin: 4px 0 6px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-time {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.notif-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
  gap: 8px;
}

.empty-icon {
  color: var(--el-color-success);
}

.notif-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
  margin-top: 4px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
