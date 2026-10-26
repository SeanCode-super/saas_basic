<template>
  <view class="notify-page">
    <view class="notify-tabs">
      <view
        v-for="cat in categories"
        :key="cat.key"
        class="tab-item"
        :class="{ 'is-active': activeCategory === cat.key }"
        @click="activeCategory = cat.key"
      >
        <text>{{ cat.label }}</text>
      </view>
    </view>

    <view v-if="loading" class="state-view">
      <text class="state-text">加载消息通知中...</text>
    </view>

    <view v-else-if="filteredList.length === 0" class="state-view">
      <text class="state-icon">🔕</text>
      <text class="state-text">暂无此类未读消息</text>
    </view>

    <view v-else class="notify-list">
      <view
        v-for="msg in filteredList"
        :key="msg.id"
        class="notify-card"
        :class="{ 'is-unread': !msg.read }"
        @click="handleClickMsg(msg)"
      >
        <view class="card-icon-box" :class="'cat-' + msg.category?.toLowerCase()">
          <text>{{ getIcon(msg.category) }}</text>
        </view>

        <view class="card-body">
          <view class="card-top">
            <text class="msg-title">{{ msg.title }}</text>
            <text class="msg-time">{{ msg.createdAt || '刚刚' }}</text>
          </view>
          <text class="msg-content">{{ msg.content }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { apiGetMyNotifications, apiMarkNotificationRead } from "../../api/notify";

const activeCategory = ref("ALL");
const loading = ref(false);
const messages = ref<any[]>([]);

const categories = [
  { key: "ALL", label: "全部通知" },
  { key: "WORKFLOW", label: "审批提醒" },
  { key: "SECURITY", label: "安全告警" },
  { key: "SYSTEM", label: "系统公告" }
];

const filteredList = computed(() => {
  if (activeCategory.value === "ALL") return messages.value;
  return messages.value.filter((m) => m.category === activeCategory.value);
});

function getIcon(cat?: string) {
  if (cat === "WORKFLOW") return "📝";
  if (cat === "SECURITY") return "🛡️";
  return "📢";
}

async function loadData() {
  loading.value = true;
  try {
    const list = await apiGetMyNotifications();
    messages.value = Array.isArray(list) ? list : [];
  } catch {
    messages.value = [
      { id: 1, title: "您有一条新的费用报销待审批", content: "市场部张敏提交了【Q4大客户拓展招待费用报销】，请及时审批办理。", category: "WORKFLOW", read: false, createdAt: "10分钟前" },
      { id: 2, title: "异地登录安全提醒", content: "检测到管理员账号在新的 IP (192.168.1.100) 成功登录，行级审计已留痕。", category: "SECURITY", read: true, createdAt: "2小时前" },
      { id: 3, title: "天枢云基 v2.4 升级公告", content: "全平台多租户微服务基座已无缝升级至最新高可用版本，SLA保障率99.99%。", category: "SYSTEM", read: true, createdAt: "昨天" }
    ];
  } finally {
    loading.value = false;
  }
}

async function handleClickMsg(msg: any) {
  if (!msg.read) {
    msg.read = true;
    try {
      await apiMarkNotificationRead(msg.id);
    } catch {}
  }
  if (msg.category === "WORKFLOW") {
    uni.navigateTo({ url: "/pages/workflow/list" });
  } else {
    uni.showToast({ title: "已标为已读", icon: "none" });
  }
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.notify-page {
  min-height: 100vh;
  background-color: #f8fafc;
  padding: 12px 14px 40px;
  box-sizing: border-box;
}

.notify-tabs {
  display: flex;
  background: #ffffff;
  border-radius: 10px;
  padding: 4px;
  margin-bottom: 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: 12px;
  color: #64748b;
  border-radius: 8px;
}

.tab-item.is-active {
  background: #2563eb;
  color: #ffffff;
  font-weight: 600;
}

.state-view {
  text-align: center;
  padding: 60px 0;
}

.state-icon {
  font-size: 40px;
  display: block;
  margin-bottom: 8px;
}

.state-text {
  font-size: 13px;
  color: #94a3b8;
}

.notify-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.notify-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 14px;
  display: flex;
  gap: 12px;
  border: 1px solid #f1f5f9;
  position: relative;
}

.notify-card.is-unread::after {
  content: "";
  position: absolute;
  top: 12px;
  right: 12px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ef4444;
}

.card-icon-box {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.card-icon-box.cat-workflow {
  background: #eff6ff;
}

.card-icon-box.cat-security {
  background: #fef2f2;
}

.card-icon-box.cat-system {
  background: #f0fdf4;
}

.card-body {
  flex: 1;
  min-width: 0;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.msg-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.msg-time {
  font-size: 11px;
  color: #94a3b8;
}

.msg-content {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
  display: block;
}
</style>
