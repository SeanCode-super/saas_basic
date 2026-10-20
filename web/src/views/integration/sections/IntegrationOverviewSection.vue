<script setup lang="ts">
import { useRouter } from "vue-router";
import { Connection, Position, CircleCheck, Timer, Setting, Document, RefreshRight } from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";

defineProps<{
  connectionPolicy: {
    defaultTimeoutMs: number;
    rotationCycleDays: number;
  };
  webhookPolicy: {
    callbackTimeoutMs: number;
  };
  rows?: Array<any>;
}>();

const router = useRouter();

function navigateTo(path: string) {
  router.push(path);
}
</script>

<template>
  <div class="integration-overview">
    <!-- 顶部核心遥测指标栏 (Sleek 40px Bar) -->
    <div class="integration-telemetry-bar">
      <div class="telemetry-left">
        <div class="metric-item">
          <el-icon class="icon-blue"><Connection /></el-icon>
          <span class="label">数据源在线</span>
          <span class="value">{{ rows ? rows.length : 4 }} 个节点</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-green"><Position /></el-icon>
          <span class="label">Webhook 成功率</span>
          <span class="value">99.82%</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-purple"><Timer /></el-icon>
          <span class="label">网关响应耗时</span>
          <span class="value">14.2 ms</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-amber"><CircleCheck /></el-icon>
          <span class="label">熔断器保护</span>
          <span class="value tag-closed">CLOSED 常态</span>
        </div>
      </div>
    </div>

    <!-- 集成能力矩阵与快捷导航 -->
    <div class="grid-2-col mt-4">
      <BaseCard>
        <template #header>
          <div class="panel-header-row">
            <div>
              <h3 class="panel-title">集成底座控制面基线</h3>
              <p class="panel-subtitle">不仅保障网络连通，更对协议适配、加密凭证、死信补偿全流程留痕。</p>
            </div>
          </div>
        </template>

        <div class="matrix-grid">
          <div class="matrix-card" @click="navigateTo('/integration/connection')">
            <div class="card-head">
              <el-icon class="icon-blue"><Connection /></el-icon>
              <strong>连接治理中心</strong>
            </div>
            <p>连接池最大空闲、超时中断、凭证安全脱敏与批量健康连通性探针。</p>
          </div>

          <div class="matrix-card" @click="navigateTo('/integration/callback')">
            <div class="card-head">
              <el-icon class="icon-green"><Position /></el-icon>
              <strong>回调与死信策略</strong>
            </div>
            <p>Webhook 签名鉴权、去重防重放、死信补偿队列与一键重试投递。</p>
          </div>

          <div class="matrix-card" @click="navigateTo('/integration/health')">
            <div class="card-head">
              <el-icon class="icon-amber"><CircleCheck /></el-icon>
              <strong>全链路健康体检</strong>
            </div>
            <p>多端点连通性测试、网络 RTT 延迟探测与熔断器状态全景看板。</p>
          </div>

          <div class="matrix-card" @click="navigateTo('/integration/registry')">
            <div class="card-head">
              <el-icon class="icon-purple"><Setting /></el-icon>
              <strong>集成接入台账</strong>
            </div>
            <p>业务数据源、外部第三方系统注册表与批量清单安全导入。</p>
          </div>
        </div>
      </BaseCard>

      <BaseCard>
        <template #header>
          <div class="panel-header-row">
            <div>
              <h3 class="panel-title">协议支持与网关拓扑</h3>
              <p class="panel-subtitle">支持异构系统间多协议互通与数据交换底座。</p>
            </div>
          </div>
        </template>

        <div class="protocol-stack">
          <div class="protocol-row">
            <div class="proto-left">
              <el-tag size="small">REST / HTTPS</el-tag>
              <span class="proto-name">OpenAPI 网关与动态代理</span>
            </div>
            <span class="proto-stat">TLS 1.3 · 互信证书</span>
          </div>
          <div class="protocol-row">
            <div class="proto-left">
              <el-tag size="small" type="success">MySQL / JDBC</el-tag>
              <span class="proto-name">多租户动态分库与只读副本</span>
            </div>
            <span class="proto-stat">HikariCP · 连通正常</span>
          </div>
          <div class="protocol-row">
            <div class="proto-left">
              <el-tag size="small" type="warning">Webhook</el-tag>
              <span class="proto-name">异步事件驱动消息广播通道</span>
            </div>
            <span class="proto-stat">HMAC-SHA256 验签</span>
          </div>
          <div class="protocol-row">
            <div class="proto-left">
              <el-tag size="small" type="info">AMQP / MQTT</el-tag>
              <span class="proto-name">物联网设备接入与高频数据管道</span>
            </div>
            <span class="proto-stat">QoS 1 · 幂等去重</span>
          </div>
        </div>
      </BaseCard>
    </div>
  </div>
</template>

<style scoped lang="scss">
.integration-overview {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.integration-telemetry-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 2px rgb(0 0 0 / 0.03);

  .telemetry-left {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
  }

  .metric-item {
    display: flex;
    align-items: center;
    gap: 8px;

    .icon-blue { color: #2563eb; }
    .icon-green { color: #16a34a; }
    .icon-purple { color: #9333ea; }
    .icon-amber { color: #d97706; }

    .label {
      font-size: 13px;
      color: #64748b;
    }

    .value {
      font-size: 13px;
      font-weight: 600;
      color: #0f172a;

      &.tag-closed {
        color: #16a34a;
        background: #f0fdf4;
        padding: 2px 8px;
        border-radius: 4px;
        font-size: 12px;
      }
    }
  }

  .divider {
    width: 1px;
    height: 14px;
    background: #e2e8f0;
  }
}

.grid-2-col {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.panel-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  .panel-title { margin: 0 0 4px; font-size: 16px; font-weight: 700; color: #0f172a; }
  .panel-subtitle { margin: 0; font-size: 12px; color: #64748b; }
}

.matrix-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 8px 0;

  .matrix-card {
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    padding: 14px;
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover {
      border-color: #3b82f6;
      background: #eff6ff;
    }

    .card-head {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 6px;

      strong { font-size: 13px; color: #0f172a; }
      .icon-blue { color: #2563eb; }
      .icon-green { color: #16a34a; }
      .icon-amber { color: #d97706; }
      .icon-purple { color: #9333ea; }
    }

    p {
      margin: 0;
      font-size: 11px;
      color: #64748b;
      line-height: 1.4;
    }
  }
}

.protocol-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 0;

  .protocol-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 14px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;

    .proto-left {
      display: flex;
      align-items: center;
      gap: 10px;

      .proto-name { font-size: 13px; font-weight: 600; color: #1e293b; }
    }

    .proto-stat {
      font-size: 12px;
      color: #64748b;
    }
  }
}

.mt-4 { margin-top: 16px; }
</style>
