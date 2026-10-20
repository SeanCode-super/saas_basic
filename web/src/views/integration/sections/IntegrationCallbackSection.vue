<script setup lang="ts">
import { ref } from "vue";
import { ElMessage } from "element-plus";
import { CircleCheck, Refresh, Setting, VideoPlay, Warning, Bell, Key } from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";

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

interface WebhookEvent {
  id: string;
  eventType: string;
  targetUrl: string;
  deliveredAt: string;
  status: "SUCCESS" | "FAILED" | "DEAD_LETTER";
  retryCount: number;
  httpStatus: number;
}

const policyExpanded = ref(false);

const webhookEvents = ref<WebhookEvent[]>([
  {
    id: "EVT-20261017-001",
    eventType: "tenant.created",
    targetUrl: "https://api.erp.example.com/hooks/tenant",
    deliveredAt: "2026-10-17 14:30:12",
    status: "SUCCESS",
    retryCount: 0,
    httpStatus: 200
  },
  {
    id: "EVT-20261017-002",
    eventType: "iam.user.disabled",
    targetUrl: "https://security.example.com/alerts",
    deliveredAt: "2026-10-17 12:15:00",
    status: "SUCCESS",
    retryCount: 1,
    httpStatus: 200
  },
  {
    id: "EVT-20261017-003",
    eventType: "workflow.instance.approved",
    targetUrl: "https://finance.example.com/oa/callback",
    deliveredAt: "2026-10-17 09:20:44",
    status: "DEAD_LETTER",
    retryCount: 3,
    httpStatus: 504
  }
]);

function handleReplay(row: WebhookEvent) {
  row.status = "SUCCESS";
  row.httpStatus = 200;
  row.deliveredAt = new Date().toISOString().replace("T", " ").substring(0, 19);
  ElMessage.success(`Webhook 事件「${row.id}」已成功补偿重投递！对方服务器已返回 HTTP 200 OK。`);
}
</script>

<template>
  <div class="callback-stack">
    <!-- Sleek 40px Webhook Telemetry Strip -->
    <div class="callback-telemetry-bar">
      <div class="telemetry-items">
        <div class="telemetry-item">
          <span class="telemetry-icon blue"><el-icon><Bell /></el-icon></span>
          <span class="telemetry-label">事件队列</span>
          <strong class="telemetry-val">{{ webhookEvents.length }} 条</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon green"><el-icon><CircleCheck /></el-icon></span>
          <span class="telemetry-label">投递成功率</span>
          <strong class="telemetry-val">67%</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon rose"><el-icon><Warning /></el-icon></span>
          <span class="telemetry-label">死信待补偿</span>
          <strong class="telemetry-val" style="color: #e11d48">1 件</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon purple"><el-icon><Key /></el-icon></span>
          <span class="telemetry-label">签名 & 去重</span>
          <strong class="telemetry-val">HMAC-SHA256 · 24h 幂等</strong>
        </div>
      </div>

      <div class="telemetry-actions">
        <el-button link type="primary" size="small" @click="policyExpanded = !policyExpanded">
          <el-icon><Setting /></el-icon>
          <span>{{ policyExpanded ? '收起投递策略' : '配置投递策略' }}</span>
        </el-button>
      </div>
    </div>

    <!-- Collapsible Policy Configuration Card -->
    <el-collapse-transition>
      <div v-show="policyExpanded" class="policy-collapse-box">
        <BaseCard tone="soft">
          <template #header>
            <div class="panel-header-flex">
              <div>
                <h4 style="margin: 0; font-size: 14px; font-weight: 600; color: #0f172a">Webhook 回调投递策略与重试基准</h4>
                <p style="margin: 2px 0 0; font-size: 12px; color: #64748b">所有对外 Webhook 投递实行统一的请求签名、指数退避重试、去重和死信队列补偿机制。</p>
              </div>
              <el-button type="primary" size="small" @click="savePolicy('回调投递策略已保存')">保存回调策略</el-button>
            </div>
          </template>

          <el-form label-position="top">
            <div class="dialog-grid">
              <el-form-item label="回调超时（毫秒）">
                <el-input-number v-model="webhookPolicy.callbackTimeoutMs" :min="100" :max="30000" style="width: 100%" />
              </el-form-item>
              <el-form-item label="最大自动重试次数">
                <el-input-number v-model="webhookPolicy.retryCount" :min="0" :max="20" style="width: 100%" />
              </el-form-item>
            </div>
            <div class="policy-toggle-stack">
              <el-switch v-model="webhookPolicy.signRequest" active-text="启用 HMAC-SHA256 请求签名 (Header: X-SaaS-Signature)" />
              <el-switch v-model="webhookPolicy.deduplicateEvent" active-text="启用 24 小时幂等事件去重" />
              <el-switch v-model="webhookPolicy.deadLetterQueue" active-text="重试衰竭后自动存入死信队列 (DLQ)" />
            </div>
          </el-form>
        </BaseCard>
      </div>
    </el-collapse-transition>

    <!-- Main Full-Width Webhook Queue Table Card -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="panel-header-flex">
          <div>
            <h3 style="margin: 0; font-size: 15px; font-weight: 700; color: #0f172a">实时事件投递与死信补偿队列</h3>
            <p style="margin: 2px 0 0; font-size: 12px; color: #64748b">监控对外下发 Webhook 事件状态，支持对投递失败或死信事件发起一键重投补偿。</p>
          </div>
          <el-tag size="small" type="danger" effect="plain">死信 1 件待补偿</el-tag>
        </div>
      </template>

      <el-table :data="webhookEvents" stripe style="width: 100%">
        <el-table-column prop="eventType" label="事件类型" width="200">
          <template #default="{ row }">
            <el-tag size="small" type="primary">{{ row.eventType }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="targetUrl" label="目标接收端点" min-width="260">
          <template #default="{ row }">
            <code class="mono-url">{{ row.targetUrl }}</code>
          </template>
        </el-table-column>

        <el-table-column prop="deliveredAt" label="投递时间" width="180">
          <template #default="{ row }">
            <span style="font-size: 12px; color: #475569">{{ row.deliveredAt }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'SUCCESS'" size="small" type="success">投递成功</el-tag>
            <el-tag v-else-if="row.status === 'DEAD_LETTER'" size="small" type="danger" effect="dark">进入死信</el-tag>
            <el-tag v-else size="small" type="warning">重试中</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="httpStatus" label="响应码" width="100" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.httpStatus === 200 ? '#10b981' : '#ef4444', fontWeight: 'bold' }">
              {{ row.httpStatus }}
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
  </div>
</template>

<style scoped lang="scss">
.callback-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* Sleek Telemetry Strip */
.callback-telemetry-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--el-bg-color, #ffffff);
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
      color: #64748b;
      font-size: 12px;
    }

    .telemetry-val {
      color: #0f172a;
      font-weight: 600;
    }
  }

  .telemetry-divider {
    width: 1px;
    height: 18px;
    background: #e2e8f0;
  }

  .telemetry-actions {
    display: inline-flex;
    align-items: center;
  }
}

.policy-collapse-box {
  margin-bottom: 4px;
}

.table-card {
  border-radius: 8px;
  border: 1px solid #e2e8f0;
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
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.mono-url {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: #334155;
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
}
</style>
