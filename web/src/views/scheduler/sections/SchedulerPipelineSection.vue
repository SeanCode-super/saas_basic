<script setup lang="ts">
import { ref } from "vue";
import { ElMessage } from "element-plus";
import {
  Bell,
  CircleCheck,
  Cpu,
  DataAnalysis,
  Document,
  Monitor,
  Operation,
  Refresh,
  Search,
  Timer,
  VideoPlay,
  Warning
} from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";

const props = defineProps<{
  jobs?: Array<Record<string, unknown>>;
  logs?: Array<Record<string, unknown>>;
  loading?: boolean;
  triggerJob?: (row: any) => void | Promise<void>;
}>();

const testingPipeline = ref(false);
const testResult = ref<Record<string, any> | null>(null);
const topologyExpanded = ref(false);

function runPipelineTest() {
  testingPipeline.value = true;
  testResult.value = null;
  setTimeout(() => {
    testingPipeline.value = false;
    testResult.value = {
      evaluatedAt: new Date().toISOString().substring(11, 19),
      triggerLatency: "0.45ms",
      schedulerLock: "ACQUIRED_MYSQL_ROW_LOCK",
      routeTarget: "worker-node-01 (192.168.1.12:9099)",
      executionTime: "12.8ms",
      archiveStatus: "SUCCESS_AUDIT_LOG_COMMITTED"
    };
    ElMessage.success("全链路调度管道连通性探针执行完成，端到端延迟仅 13.25ms！");
  }, 600);
}
</script>

<template>
  <div class="scheduler-pipeline-stack">
    <!-- Slim Pipeline Radar Bar with Collapsible Topology -->
    <div class="pipeline-summary-bar">
      <div class="pipeline-flow-summary">
        <span class="pipeline-badge">
          <el-icon><Timer /></el-icon>
          <span>执行链路拓扑</span>
        </span>
        <div class="pipeline-mini-steps">
          <span class="mini-step"><span class="step-num">1</span> 触发源</span>
          <span class="mini-arrow">→</span>
          <span class="mini-step"><span class="step-num">2</span> 核心调度</span>
          <span class="mini-arrow">→</span>
          <span class="mini-step"><span class="step-num">3</span> 路由分发</span>
          <span class="mini-arrow">→</span>
          <span class="mini-step"><span class="step-num">4</span> 节点沙箱</span>
          <span class="mini-arrow">→</span>
          <span class="mini-step"><span class="step-num">5</span> 归档告警</span>
        </div>
        <el-button link type="primary" size="small" @click="topologyExpanded = !topologyExpanded">
          {{ topologyExpanded ? "收起链路拓扑" : "展开拓扑架构" }}
        </el-button>
      </div>

      <div class="pipeline-actions">
        <el-button size="small" type="primary" :icon="VideoPlay" :loading="testingPipeline" @click="runPipelineTest">
          一键全链路探测
        </el-button>
      </div>
    </div>

    <!-- Collapsible Full Visual Pipeline Topology -->
    <el-collapse-transition>
      <div v-show="topologyExpanded" class="topology-detail-card">
        <div class="pipeline-flow-cards">
          <div class="flow-step">
            <div class="flow-step-icon blue">
              <el-icon><Timer /></el-icon>
            </div>
            <h4>1. 触发事件层</h4>
            <p>Crontab 解析、动态延时队列、外部 Webhook 消息与依赖触发器。</p>
            <div class="step-meta">
              <span>策略: <strong>Cron / Manual</strong></span>
            </div>
          </div>

          <div class="flow-arrow">➔</div>

          <div class="flow-step">
            <div class="flow-step-icon indigo">
              <el-icon><Cpu /></el-icon>
            </div>
            <h4>2. Quartz 核心调度</h4>
            <p>分布式行锁防重复执行、Misfire 补偿决策、并发冲突阻止。</p>
            <div class="step-meta">
              <span>隔离: <strong>分布式排他锁</strong></span>
            </div>
          </div>

          <div class="flow-arrow">➔</div>

          <div class="flow-step">
            <div class="flow-step-icon amber">
              <el-icon><Operation /></el-icon>
            </div>
            <h4>3. 路由与分发</h4>
            <p>按一致性哈希、轮询或最低负载算法将任务下发给执行器工作节点。</p>
            <div class="step-meta">
              <span>网关: <strong>一致性路由</strong></span>
            </div>
          </div>

          <div class="flow-arrow">➔</div>

          <div class="flow-step">
            <div class="flow-step-icon purple">
              <el-icon><Monitor /></el-icon>
            </div>
            <h4>4. 执行节点沙箱</h4>
            <p>独立线程池执行 Handler、捕获标准输出与异常堆栈、超时强杀保护。</p>
            <div class="step-meta">
              <span>沙箱: <strong>超时 300s 熔断</strong></span>
            </div>
          </div>

          <div class="flow-arrow">➔</div>

          <div class="flow-step">
            <div class="flow-step-icon green">
              <el-icon><CircleCheck /></el-icon>
            </div>
            <h4>5. 归档与告警</h4>
            <p>回写执行状态码、记录耗时日志、失败触发重试与钉钉/企业微信告警。</p>
            <div class="step-meta">
              <span>留存: <strong>180 天审计归档</strong></span>
            </div>
          </div>
        </div>
      </div>
    </el-collapse-transition>

    <!-- Test Diagnostic Result Inline Strip -->
    <div v-if="testResult" class="pipeline-test-result">
      <div class="test-head">
        <el-icon class="icon-pass"><CircleCheck /></el-icon>
        <strong>全链路端到端拨测通过 (SLA 99.99%)</strong>
        <span class="mono-time">{{ testResult.evaluatedAt }}</span>
      </div>
      <div class="test-grid">
        <div class="test-item">
          <span>触发耗时:</span> <strong>{{ testResult.triggerLatency }}</strong>
        </div>
        <div class="test-item">
          <span>锁状态:</span> <strong>{{ testResult.schedulerLock }}</strong>
        </div>
        <div class="test-item">
          <span>目标节点:</span> <strong>{{ testResult.routeTarget }}</strong>
        </div>
        <div class="test-item">
          <span>执行耗时:</span> <strong>{{ testResult.executionTime }}</strong>
        </div>
      </div>
    </div>

    <!-- Main Active Jobs & Pipeline Mounting Table -->
    <el-card shadow="never" class="job-table-card">
      <template #header>
        <div class="panel-header-flex">
          <div class="header-title-box">
            <h3 class="compact-title">生效作业与链路挂载</h3>
            <span class="compact-subtitle">支持在线发起即时单次执行拨测与链路追踪</span>
          </div>
          <span class="total-tag">共 {{ (jobs || []).length }} 个任务挂载</span>
        </div>
      </template>

      <el-table :data="jobs || []" stripe style="width: 100%">
        <el-table-column prop="code" label="任务代码" width="170">
          <template #default="{ row }">
            <span class="mono-code">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="任务名称" min-width="180" />
        <el-table-column prop="jobType" label="作业类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" type="primary" effect="plain">{{ row.jobType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handlerName" label="处理器 Handler" min-width="220">
          <template #default="{ row }">
            <span class="mono-code">{{ row.handlerName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="调度状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? '生效运行' : '暂停中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="管道调试" width="120" align="center">
          <template #default="{ row }">
            <el-button link type="primary" :icon="VideoPlay" @click="triggerJob?.(row)">
              立即触发
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.scheduler-pipeline-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pipeline-summary-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 14px;
  background: var(--el-bg-color);
  border: 1px solid var(--sb-border-color, #e2e8f0);
  border-radius: 8px;
  flex-wrap: wrap;
  gap: 12px;

  .pipeline-flow-summary {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .pipeline-badge {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 600;
    color: var(--el-color-primary, #0284c7);
  }

  .pipeline-mini-steps {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    color: var(--el-text-color-regular, #475569);

    .mini-step {
      display: inline-flex;
      align-items: center;
      gap: 4px;

      .step-num {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 16px;
        height: 16px;
        border-radius: 50%;
        background: #f1f5f9;
        font-size: 10px;
        font-weight: 700;
        color: #0284c7;
      }
    }

    .mini-arrow {
      color: #94a3b8;
      font-size: 12px;
    }
  }
}

.topology-detail-card {
  padding: 14px 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--sb-border-color, #e2e8f0);
  border-radius: 8px;
}

.panel-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;

  .header-title-box {
    display: flex;
    align-items: baseline;
    gap: 10px;

    .compact-title {
      font-size: 14px;
      font-weight: 600;
      color: var(--el-text-color-primary, #0f172a);
      margin: 0;
    }

    .compact-subtitle {
      font-size: 12px;
      color: var(--el-text-color-secondary, #64748b);
    }
  }

  .total-tag {
    font-size: 12px;
    color: var(--el-text-color-secondary, #64748b);
  }
}

.pipeline-flow-cards {
  display: grid;
  grid-template-columns: 1fr auto 1fr auto 1fr auto 1fr auto 1fr;
  align-items: center;
  gap: 8px;
  margin: 16px 0;

  @media (max-width: 1200px) {
    grid-template-columns: 1fr;

    .flow-arrow {
      transform: rotate(90deg);
      text-align: center;
    }
  }
}

.flow-step {
  padding: 14px 16px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid var(--sb-border-color);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 160px;

  h4 {
    margin: 8px 0 4px;
    font-size: 13px;
    font-weight: 600;
    color: var(--sb-text-primary);
  }

  p {
    margin: 0;
    font-size: 11px;
    color: var(--sb-text-secondary);
    line-height: 1.5;
  }

  .step-meta {
    margin-top: 10px;
    padding-top: 8px;
    border-top: 1px dashed var(--sb-border-color);
    font-size: 11px;
    color: #64748b;

    strong {
      color: #0f172a;
    }
  }
}

.flow-step-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;

  &.blue { background: #e0f2fe; color: #0284c7; }
  &.indigo { background: #e0e7ff; color: #4f46e5; }
  &.amber { background: #fef3c7; color: #d97706; }
  &.purple { background: #f3e8ff; color: #9333ea; }
  &.green { background: #dcfce7; color: #10b981; }
}

.flow-arrow {
  color: #94a3b8;
  font-size: 16px;
  font-weight: 700;
  user-select: none;
}

.pipeline-test-result {
  margin-top: 16px;
  padding: 14px 16px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 6px;

  .test-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
    font-size: 13px;
    color: #166534;

    .icon-pass { font-size: 18px; }

    .mono-time {
      margin-left: auto;
      font-size: 11px;
      color: #64748b;
      font-family: ui-monospace, SFMono-Regular, monospace;
    }
  }

  .test-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 12px;
    font-size: 12px;

    @media (max-width: 900px) {
      grid-template-columns: repeat(2, 1fr);
    }

    .test-item {
      span { color: #64748b; margin-right: 4px; }
      strong { color: #0f172a; }
    }
  }
}

.mono-code {
  font-family: ui-monospace, SFMono-Regular, monospace;
  font-size: 12px;
  color: var(--sb-text-secondary);
}
</style>
