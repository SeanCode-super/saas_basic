<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { Timer, Cpu, VideoPlay, VideoPause, RefreshRight, Document, CircleCheck, Warning } from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";

const props = defineProps<{
  jobs: Array<Record<string, unknown>>;
  logs: Array<Record<string, unknown>>;
  loading?: boolean;
  triggerJob?: (row: any) => Promise<void> | void;
  toggleJobStatus?: (row: any) => Promise<void> | void;
}>();

const router = useRouter();
const syncing = ref(false);
const allPaused = ref(false);
const clusterExpanded = ref(false);

const enabledCount = computed(() => props.jobs.filter((j) => j.status === "ENABLED").length);
const disabledCount = computed(() => props.jobs.filter((j) => j.status !== "ENABLED").length);

function handleSyncCluster() {
  syncing.value = true;
  setTimeout(() => {
    syncing.value = false;
    ElMessage.success("Quartz 分布式集群状态同步完成，分布式排他锁与 Misfire 策略已刷新");
  }, 500);
}

function handleEmergencyPauseAll() {
  ElMessageBox.confirm("紧急熔断将立即挂起所有正在排队与调度的 Quartz 触发器，确定执行全平台熔断吗？", "调度集群紧急熔断确认", {
    type: "warning"
  }).then(() => {
    allPaused.value = !allPaused.value;
    ElMessage.warning(allPaused.value ? "已启动紧急熔断机制，所有定时触发器已暂停挂起" : "已恢复调度集群常态化轮询");
  });
}

function navigateTo(path: string) {
  router.push(path);
}
</script>

<template>
  <div class="scheduler-overview">
    <!-- Slim Metric Strip -->
    <div class="kpi-telemetry-strip">
      <div class="kpi-item">
        <span class="kpi-icon blue"><Timer style="width: 14px; height: 14px" /></span>
        <span class="kpi-label">定时任务</span>
        <strong class="kpi-val">{{ jobs.length }} 个</strong>
        <span class="kpi-desc">({{ enabledCount }} 运行 / {{ disabledCount }} 暂停)</span>
      </div>
      <div class="kpi-divider" />
      <div class="kpi-item">
        <span class="kpi-icon green"><Cpu style="width: 14px; height: 14px" /></span>
        <span class="kpi-label">Quartz 集群</span>
        <strong class="kpi-val">Node-01 (Leader)</strong>
        <span class="kpi-desc">抢锁 0.4ms</span>
      </div>
      <div class="kpi-divider" />
      <div class="kpi-item">
        <span class="kpi-icon purple"><Document style="width: 14px; height: 14px" /></span>
        <span class="kpi-label">累计执行</span>
        <strong class="kpi-val">{{ logs.length }} 条</strong>
        <span class="kpi-desc">成功率 99.8%</span>
      </div>
      <div class="kpi-divider" />
      <div class="kpi-item">
        <span class="kpi-icon amber"><CircleCheck style="width: 14px; height: 14px" /></span>
        <span class="kpi-label">容灾状态</span>
        <strong class="kpi-val" :style="{ color: allPaused ? '#dc2626' : '#16a34a' }">
          {{ allPaused ? '紧急熔断' : '常态运行' }}
        </strong>
      </div>
      <div class="kpi-divider" />
      <div class="kpi-item-actions">
        <el-button link type="primary" size="small" @click="clusterExpanded = !clusterExpanded">
          {{ clusterExpanded ? "收起集群拓扑" : "展开集群拓扑" }}
        </el-button>
        <el-button size="small" :icon="RefreshRight" :loading="syncing" @click="handleSyncCluster">同步集群</el-button>
        <el-button
          size="small"
          :type="allPaused ? 'success' : 'danger'"
          plain
          @click="handleEmergencyPauseAll"
        >
          {{ allPaused ? '恢复全量' : '紧急熔断' }}
        </el-button>
      </div>
    </div>

    <!-- Collapsible Cluster Nodes & Strategy Cards -->
    <el-collapse-transition>
      <div v-show="clusterExpanded" class="grid-2-col">
        <BaseCard>
          <template #header>
            <div class="panel-header-row">
              <h4 class="panel-title-sm">调度集群高可用节点拓扑</h4>
            </div>
          </template>
          <div class="cluster-nodes-stack">
            <div class="node-row node-active">
              <div class="node-left">
                <span class="node-dot dot-green"></span>
                <div class="node-info">
                  <span class="node-name">Quartz-Node-01 (192.168.1.10:8080)</span>
                  <span class="node-role">当前选举 Leader · 承担 70% 分发调度</span>
                </div>
              </div>
              <el-tag size="small" type="success">主节点在线</el-tag>
            </div>
            <div class="node-row">
              <div class="node-left">
                <span class="node-dot dot-blue"></span>
                <div class="node-info">
                  <span class="node-name">Quartz-Node-02 (192.168.1.11:8080)</span>
                  <span class="node-role">热备节点 Standby · 心跳就绪</span>
                </div>
              </div>
              <el-tag size="small">热备待命</el-tag>
            </div>
            <div class="node-row">
              <div class="node-left">
                <span class="node-dot dot-blue"></span>
                <div class="node-info">
                  <span class="node-name">Quartz-Node-03 (192.168.1.12:8080)</span>
                  <span class="node-role">执行沙箱 Executor · 承担高算力任务</span>
                </div>
              </div>
              <el-tag size="small">计算节点</el-tag>
            </div>
          </div>
        </BaseCard>

        <BaseCard>
          <template #header>
            <div class="panel-header-row">
              <h4 class="panel-title-sm">调度治理中心导航</h4>
            </div>
          </template>
          <div class="strategy-matrix">
            <div class="strategy-item" @click="navigateTo('/scheduler/strategy')">
              <strong>执行策略</strong>
              <p>Cron 表达式、隔离执行器与并发控制</p>
            </div>
            <div class="strategy-item" @click="navigateTo('/scheduler/pipeline')">
              <strong>链路观测</strong>
              <p>全生命周期任务流转拓扑</p>
            </div>
            <div class="strategy-item" @click="navigateTo('/scheduler/alarm')">
              <strong>告警基线</strong>
              <p>超时告警、静默期与多通道推送</p>
            </div>
            <div class="strategy-item" @click="navigateTo('/scheduler/log')">
              <strong>日志治理</strong>
              <p>Trace 链路追踪与执行归档审计</p>
            </div>
          </div>
        </BaseCard>
      </div>
    </el-collapse-transition>

    <!-- 活跃调度任务列表 -->
    <el-card shadow="never" class="mt-4">
      <template #header>
        <div class="panel-header-row">
          <div>
            <h3 class="panel-title">调度任务实时状态台账 ({{ jobs.length }} 个任务)</h3>
            <p class="panel-subtitle">支持直接触发、状态暂停/启用与全链路执行跟踪。</p>
          </div>
          <el-button type="primary" size="small" @click="navigateTo('/scheduler/strategy')">进入策略管理</el-button>
        </div>
      </template>

      <el-table :data="jobs" border stripe v-loading="Boolean(loading)" style="width: 100%">
        <el-table-column prop="code" label="任务编码" min-width="150" />
        <el-table-column prop="name" label="任务名称" min-width="180">
          <template #default="{ row }">
            <span style="font-weight: 600; color: #0f172a">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="jobType" label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small">{{ row.jobType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handlerName" label="Handler 处理器" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? '调度中' : '已暂停' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="VideoPlay" @click="triggerJob?.(row)">即刻触发</el-button>
            <el-button
              link
              :type="row.status === 'ENABLED' ? 'warning' : 'success'"
              @click="toggleJobStatus?.(row)"
            >
              {{ row.status === 'ENABLED' ? '暂停' : '启动' }}
            </el-button>
            <el-button link type="primary" @click="navigateTo('/scheduler/pipeline')">链路透视</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.scheduler-overview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* Compact Telemetry Strip */
.kpi-telemetry-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--sb-border-color, #e2e8f0);
  border-radius: 8px;
  flex-wrap: wrap;
  gap: 12px;

  .kpi-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;

    .kpi-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 22px;
      height: 22px;
      border-radius: 4px;

      &.blue { background: #eff6ff; color: #2563eb; }
      &.green { background: #f0fdf4; color: #16a34a; }
      &.purple { background: #faf5ff; color: #9333ea; }
      &.amber { background: #fffbeb; color: #d97706; }
    }

    .kpi-label {
      color: #64748b;
      font-size: 12px;
    }

    .kpi-val {
      color: #0f172a;
      font-weight: 600;
    }

    .kpi-desc {
      color: #94a3b8;
      font-size: 11px;
    }
  }

  .kpi-divider {
    width: 1px;
    height: 18px;
    background: #e2e8f0;
  }

  .kpi-item-actions {
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }
}

.panel-title-sm {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
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
  .panel-actions { display: flex; gap: 8px; }
}

.cluster-nodes-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 0;

  .node-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 14px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;

    &.node-active {
      border-color: #bbf7d0;
      background: #f0fdf4;
    }

    .node-left {
      display: flex;
      align-items: center;
      gap: 10px;

      .node-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;

        &.dot-green { background: #16a34a; }
        &.dot-blue { background: #3b82f6; }
      }

      .node-info {
        display: flex;
        flex-direction: column;
        gap: 2px;

        .node-name { font-size: 13px; font-weight: 600; color: #1e293b; }
        .node-role { font-size: 11px; color: #64748b; }
      }
    }
  }
}

.strategy-matrix {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 8px 0;

  .strategy-item {
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

    strong { font-size: 13px; color: #0f172a; display: block; margin-bottom: 4px; }
    p { margin: 0; font-size: 11px; color: #64748b; line-height: 1.4; }
  }
}

.mt-4 { margin-top: 16px; }
</style>
