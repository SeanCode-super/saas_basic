<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  Bell,
  CircleCheck,
  Delete,
  Key,
  Plus,
  Promotion,
  Refresh,
  Setting,
  Warning
} from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";
import WorkspacePanel from "@/components/platform/WorkspacePanel.vue";
import {
  createWebhookSubscription,
  deleteWebhookSubscription,
  fetchWebhookDeliveries,
  fetchWebhookSubscriptions,
  replayWebhookDelivery,
  sendTestWebhook,
  type WebhookDeliveryRow,
  type WebhookSubscriptionRow
} from "@/api/modules/integration";

defineProps<{
  webhookPolicy: {
    callbackTimeoutMs: number;
    retryCount: number;
    signRequest: boolean;
    deduplicateEvent: boolean;
    deadLetterQueue: boolean;
  };
  savePolicy: (message: string) => void;
}>();

const policyExpanded = ref(false);
const loading = ref(false);
const createDialogVisible = ref(false);
const creating = ref(false);
const latestRawSecret = ref("");

const subscriptions = ref<WebhookSubscriptionRow[]>([]);
const webhookEvents = ref<WebhookDeliveryRow[]>([]);

const subForm = reactive({
  subscriptionName: "",
  targetUrl: "",
  eventTypes: "crm.customer.created,tenant.created",
  timeoutMs: 5000,
  maxRetries: 3
});

const successRatePercent = computed(() => {
  if (webhookEvents.value.length === 0) return 100;
  const succeeded = webhookEvents.value.filter((e) => e.status === "SUCCESS").length;
  return Math.round((succeeded / webhookEvents.value.length) * 100);
});

const failedOrDeadLetterCount = computed(() => {
  return webhookEvents.value.filter((e) => e.status !== "SUCCESS").length;
});

async function loadWebhookCenter() {
  loading.value = true;
  try {
    const [subs, deliveries] = await Promise.all([
      fetchWebhookSubscriptions().catch(() => []),
      fetchWebhookDeliveries().catch(() => [])
    ]);
    subscriptions.value = subs;
    webhookEvents.value = deliveries;
  } finally {
    loading.value = false;
  }
}

async function handleCreateSubscription() {
  if (!subForm.subscriptionName.trim() || !subForm.targetUrl.trim()) {
    ElMessage.warning("请填写完整的订阅名称与目标接收 URL");
    return;
  }
  creating.value = true;
  latestRawSecret.value = "";
  try {
    const created = await createWebhookSubscription({
      subscriptionName: subForm.subscriptionName.trim(),
      targetUrl: subForm.targetUrl.trim(),
      eventTypes: subForm.eventTypes.trim() || "*",
      timeoutMs: subForm.timeoutMs,
      maxRetries: subForm.maxRetries
    });
    latestRawSecret.value = created.rawSecretOnce || "";
    ElMessage.success(`CloudEvents 订阅「${created.subscriptionName}」已创建！`);
    subForm.subscriptionName = "";
    subForm.targetUrl = "";
    createDialogVisible.value = false;
    await loadWebhookCenter();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "创建订阅失败");
  } finally {
    creating.value = false;
  }
}

async function handleSendTest(sub: WebhookSubscriptionRow) {
  try {
    const delivery = await sendTestWebhook(sub.id);
    if (delivery.status === "SUCCESS") {
      ElMessage.success(`CloudEvents 1.0 测试事件投递成功 (HTTP ${delivery.httpStatus}, ${delivery.durationMs}ms)`);
    } else {
      ElMessage.warning(`测试投递返回 HTTP ${delivery.httpStatus} (${delivery.responseExcerpt || "待补偿"})`);
    }
    await loadWebhookCenter();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "测试投递失败");
  }
}

async function handleDeleteSub(sub: WebhookSubscriptionRow) {
  try {
    await deleteWebhookSubscription(sub.id);
    ElMessage.success(`已删除订阅「${sub.subscriptionName}」`);
    await loadWebhookCenter();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "删除订阅失败");
  }
}

async function handleReplay(row: WebhookDeliveryRow) {
  try {
    const updated = await replayWebhookDelivery(row.id);
    const idx = webhookEvents.value.findIndex((item) => item.id === row.id);
    if (idx >= 0) {
      webhookEvents.value[idx] = updated;
    }
    if (updated.status === "SUCCESS") {
      ElMessage.success(`Webhook 事件「${updated.eventId}」已补偿重投递成功 (HTTP ${updated.httpStatus})！`);
    } else {
      ElMessage.warning(`重投递返回 HTTP ${updated.httpStatus}，当前状态：${updated.status}`);
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "重投补偿失败");
  }
}

onMounted(() => {
  void loadWebhookCenter();
});
</script>

<template>
  <div class="callback-stack">
    <!-- Sleek Webhook Telemetry Strip -->
    <div class="callback-telemetry-bar">
      <div class="telemetry-items">
        <div class="telemetry-item">
          <span class="telemetry-icon blue"><el-icon><Bell /></el-icon></span>
          <span class="telemetry-label">活跃订阅 / 流水</span>
          <strong class="telemetry-val">{{ subscriptions.length }} / {{ webhookEvents.length }} 条</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon green"><el-icon><CircleCheck /></el-icon></span>
          <span class="telemetry-label">投递成功率</span>
          <strong class="telemetry-val">{{ successRatePercent }}%</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon rose"><el-icon><Warning /></el-icon></span>
          <span class="telemetry-label">待补偿 / 死信</span>
          <strong class="telemetry-val" style="color: #e11d48">{{ failedOrDeadLetterCount }} 件</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon purple"><el-icon><Key /></el-icon></span>
          <span class="telemetry-label">协议 & 签名</span>
          <strong class="telemetry-val">CloudEvents 1.0 · HMAC-SHA256</strong>
        </div>
      </div>

      <div class="telemetry-actions">
        <el-button type="primary" size="small" :icon="Plus" @click="createDialogVisible = true">
          新建订阅
        </el-button>
        <el-button link size="small" :icon="Refresh" :loading="loading" @click="loadWebhookCenter">
          刷新
        </el-button>
        <el-button link type="primary" size="small" @click="policyExpanded = !policyExpanded">
          <el-icon><Setting /></el-icon>
          <span>{{ policyExpanded ? '收起投递策略' : '配置投递策略' }}</span>
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="latestRawSecret"
      type="warning"
      show-icon
      closable
      :title="`请立即保存新创建订阅的一次性签名密钥 (关闭后仅展示脱敏串): ${latestRawSecret}`"
      @close="latestRawSecret = ''"
    />

    <!-- Collapsible Policy Configuration Card -->
    <el-collapse-transition>
      <div v-show="policyExpanded" class="policy-collapse-box">
        <BaseCard tone="soft">
          <template #header>
            <div class="panel-header-flex">
              <div>
                <h4 style="margin: 0; font-size: 14px; font-weight: 600; color: #0f172a">Webhook 回调投递策略与重试基准</h4>
                <p style="margin: 2px 0 0; font-size: 12px; color: #64748b">所有对外 Webhook 投递实行 CloudEvents 1.0 信封格式、SSRF 出站防护与 X-Webhook-Signature-256 验签。</p>
              </div>
              <el-button type="primary" size="small" @click="savePolicy('回调投递策略已保存')">保存回调策略</el-button>
            </div>
          </template>

          <el-form label-position="top">
            <div class="dialog-grid">
              <el-form-item label="回调超时（毫秒）">
                <el-input-number v-model="webhookPolicy.callbackTimeoutMs" :min="500" :max="30000" style="width: 100%" />
              </el-form-item>
              <el-form-item label="最大自动重试次数">
                <el-input-number v-model="webhookPolicy.retryCount" :min="0" :max="20" style="width: 100%" />
              </el-form-item>
            </div>
            <div class="policy-toggle-stack">
              <el-switch v-model="webhookPolicy.signRequest" active-text="启用 HMAC-SHA256 请求签名 (X-Webhook-Signature-256)" />
              <el-switch v-model="webhookPolicy.deduplicateEvent" active-text="启用 24 小时 CloudEvents ID 幂等去重" />
              <el-switch v-model="webhookPolicy.deadLetterQueue" active-text="重试衰竭后自动存入死信队列 (DLQ)" />
            </div>
          </el-form>
        </BaseCard>
      </div>
    </el-collapse-transition>

    <!-- Subscriptions Table -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="panel-header-flex">
          <div>
            <h3 style="margin: 0; font-size: 15px; font-weight: 700; color: #0f172a">CloudEvents 1.0 出站订阅配置</h3>
            <p style="margin: 2px 0 0; font-size: 12px; color: #64748b">管理租户级 Webhook 接收端点与签名密钥，支持一键发起实时 Ping 测试投递。</p>
          </div>
          <el-tag size="small" type="info">{{ subscriptions.length }} 个活跃订阅</el-tag>
        </div>
      </template>

      <el-table :data="subscriptions" stripe style="width: 100%" empty-text="暂无 Webhook 订阅，点击右上角「新建订阅」快速接入">
        <el-table-column prop="subscriptionName" label="订阅名称" min-width="150" />
        <el-table-column prop="targetUrl" label="目标端点 URL" min-width="240">
          <template #default="{ row }">
            <code class="mono-url">{{ row.targetUrl }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="eventTypes" label="订阅事件" min-width="170">
          <template #default="{ row }">
            <el-tag size="small" type="primary">{{ row.eventTypes }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="maskedSecret" label="签名密钥" width="140">
          <template #default="{ row }">
            <code class="mono-url">{{ row.maskedSecret }}</code>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="Promotion" @click="handleSendTest(row)">
              测试事件
            </el-button>
            <el-button link type="danger" size="small" :icon="Delete" @click="handleDeleteSub(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Main Full-Width Webhook Queue Table Card -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="panel-header-flex">
          <div>
            <h3 style="margin: 0; font-size: 15px; font-weight: 700; color: #0f172a">实时事件投递与死信补偿队列</h3>
            <p style="margin: 2px 0 0; font-size: 12px; color: #64748b">监控对外下发 CloudEvents 1.0 投递流水，支持对失败或死信事件发起一键重投补偿。</p>
          </div>
          <el-tag size="small" :type="failedOrDeadLetterCount > 0 ? 'danger' : 'success'" effect="plain">
            待补偿 {{ failedOrDeadLetterCount }} 件
          </el-tag>
        </div>
      </template>

      <el-table :data="webhookEvents" stripe style="width: 100%" empty-text="暂无投递流水，可通过上方订阅「测试事件」触发 CloudEvents 1.0 实时投递">
        <el-table-column prop="eventType" label="事件类型" width="190">
          <template #default="{ row }">
            <el-tag size="small" type="primary">{{ row.eventType }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="targetUrl" label="目标接收端点" min-width="240">
          <template #default="{ row }">
            <code class="mono-url">{{ row.targetUrl }}</code>
          </template>
        </el-table-column>

        <el-table-column prop="deliveredAt" label="最近投递时间" width="180">
          <template #default="{ row }">
            <span style="font-size: 12px; color: #475569">{{ row.deliveredAt || row.createdAt }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'SUCCESS'" size="small" type="success">投递成功</el-tag>
            <el-tag v-else-if="row.status === 'DEAD_LETTER'" size="small" type="danger" effect="dark">进入死信</el-tag>
            <el-tag v-else size="small" type="warning">待补偿</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="httpStatus" label="响应码 / 耗时" width="130" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.httpStatus >= 200 && row.httpStatus < 300 ? '#10b981' : '#ef4444', fontWeight: 'bold' }">
              {{ row.httpStatus }} ({{ row.durationMs }}ms)
            </span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="Refresh" @click="handleReplay(row)">
              重投补偿
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Create Subscription WorkspacePanel -->
    <WorkspacePanel v-model="createDialogVisible" title="新建 CloudEvents 1.0 Webhook 订阅" width="540px">
      <el-form label-position="top">
        <el-form-item label="订阅名称">
          <el-input v-model="subForm.subscriptionName" placeholder="例如：ERP 客户同步事件订阅" />
        </el-form-item>
        <el-form-item label="接收端点 URL (支持 HTTPS 与本地测试端点)">
          <el-input v-model="subForm.targetUrl" placeholder="https://api.example.com/webhooks/tianshu" />
        </el-form-item>
        <el-form-item label="订阅事件类型 (逗号分隔)">
          <el-input v-model="subForm.eventTypes" placeholder="crm.customer.created,tenant.created" />
        </el-form-item>
        <div class="dialog-grid">
          <el-form-item label="请求超时 (ms)">
            <el-input-number v-model="subForm.timeoutMs" :min="500" :max="30000" style="width: 100%" />
          </el-form-item>
          <el-form-item label="最大重试次数">
            <el-input-number v-model="subForm.maxRetries" :min="0" :max="10" style="width: 100%" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreateSubscription">保存订阅</el-button>
      </template>
    </WorkspacePanel>
  </div>
</template>

<style scoped lang="scss">
.callback-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.callback-telemetry-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--sb-border-color, #e2e8f0);
  border-radius: 8px;
  flex-wrap: wrap;
  gap: 12px;

  .telemetry-items {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
  }

  .telemetry-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;

    .telemetry-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 24px;
      height: 24px;
      border-radius: 4px;
      font-size: 13px;

      &.green { background: #f0fdf4; color: #16a34a; }
      &.blue { background: #eff6ff; color: #2563eb; }
      &.rose { background: #fff1f2; color: #e11d48; }
      &.purple { background: #faf5ff; color: #9333ea; }
    }

    .telemetry-label {
      color: var(--sb-text-muted, #475569);
      font-size: 12px;
    }

    .telemetry-val {
      color: var(--sb-text-primary, #0f172a);
      font-weight: 600;
    }
  }

  .telemetry-divider {
    width: 1px;
    height: 18px;
    background: var(--sb-border-subtle, #e2e8f0);
  }

  .telemetry-actions {
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }
}

.policy-collapse-box {
  margin-bottom: 4px;
}

.table-card {
  border-radius: 8px;
  border: 1px solid var(--sb-border-subtle, #e2e8f0);
}

.panel-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.dialog-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.policy-toggle-stack {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 10px 14px;
  background: var(--sb-surface-soft, #f8fafc);
  border-radius: 6px;
  border: 1px solid var(--sb-border-subtle, #e2e8f0);
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.mono-url {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: var(--sb-text-secondary, #334155);
  background: var(--sb-surface-contrast, #f1f5f9);
  padding: 2px 6px;
  border-radius: 4px;
}
</style>
