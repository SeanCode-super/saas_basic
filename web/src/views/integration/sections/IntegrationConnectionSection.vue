<script setup lang="ts">
import {
  ref
} from "vue";
import {

  Connection,
  Cpu,

  Setting,
  Key
} from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";

import IntegrationHealthSection from "./IntegrationHealthSection.vue";
import type { DatasourceRow } from "@/api/modules/integration";

defineProps<{
  rows: DatasourceRow[];
  connectionPolicy: {
    defaultTimeoutMs: number;
    rotationCycleDays: number;
    encryptSecrets: boolean;
    testBeforeEnable: boolean;
    allowPublicNetwork: boolean;
  };
  savePolicy: (message: string) => void;
}>();

const policyExpanded = ref(false);
</script>

<template>
  <div class="integration-connection-stack">
    <!-- Sleek 40px Connection Telemetry Strip -->
    <div class="connection-telemetry-bar">
      <div class="telemetry-items">
        <div class="telemetry-item">
          <span class="telemetry-icon blue"><el-icon><Connection /></el-icon></span>
          <span class="telemetry-label">默认超时</span>
          <strong class="telemetry-val">{{ connectionPolicy.defaultTimeoutMs }}ms</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon purple"><el-icon><Key /></el-icon></span>
          <span class="telemetry-label">凭证加密</span>
          <strong class="telemetry-val">{{ connectionPolicy.encryptSecrets ? 'AES-256 已强制' : '未开启' }}</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon amber"><el-icon><Cpu /></el-icon></span>
          <span class="telemetry-label">轮换周期</span>
          <strong class="telemetry-val">{{ connectionPolicy.rotationCycleDays }} 天</strong>
        </div>
      </div>

      <div class="telemetry-actions">
        <el-button link type="primary" size="small" @click="policyExpanded = !policyExpanded">
          <el-icon><Setting /></el-icon>
          <span>{{ policyExpanded ? '收起治理基线' : '配置治理基线' }}</span>
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
                <h4 style="margin: 0; font-size: 14px; font-weight: 600; color: #0f172a">连接治理基线参数</h4>
                <p style="margin: 2px 0 0; font-size: 12px; color: #64748b">所有接入数据源与下游服务需满足超时控制、加密存储与凭证轮换基准。</p>
              </div>
              <el-button type="primary" size="small" @click="savePolicy('连接治理策略已保存')">保存连接策略</el-button>
            </div>
          </template>

          <el-form label-position="top">
            <div class="dialog-grid">
              <el-form-item label="默认超时（毫秒）">
                <el-input-number v-model="connectionPolicy.defaultTimeoutMs" :min="1000" :max="60000" style="width: 100%" />
              </el-form-item>
              <el-form-item label="凭证轮换周期（天）">
                <el-input-number v-model="connectionPolicy.rotationCycleDays" :min="1" :max="365" style="width: 100%" />
              </el-form-item>
            </div>
            <div class="policy-toggle-stack">
              <el-switch v-model="connectionPolicy.encryptSecrets" active-text="强制加密保存连接凭证与密匙" />
              <el-switch v-model="connectionPolicy.testBeforeEnable" active-text="数据源启用前强制探针连通" />
              <el-switch v-model="connectionPolicy.allowPublicNetwork" active-text="允许跨公网 VPC 连接数据源" />
            </div>
          </el-form>
        </BaseCard>
      </div>
    </el-collapse-transition>

    <IntegrationHealthSection :rows="rows" />
  </div>
</template>

<style scoped lang="scss">
.integration-connection-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* Sleek Telemetry Strip */
.connection-telemetry-bar {
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
      &.purple { background: #faf5ff; color: #9333ea; }
      &.amber { background: #fffbeb; color: #d97706; }
    }

    .telemetry-label {
      color: #475569;
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
    gap: 10px;
  }
}

.policy-collapse-box {
  margin-bottom: 4px;
}

.probe-table-card {
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

.latency-badge {
  font-family: ui-monospace, SFMono-Regular, monospace;
  font-size: 12px;
  font-weight: 600;
  color: #10b981;
}
</style>
