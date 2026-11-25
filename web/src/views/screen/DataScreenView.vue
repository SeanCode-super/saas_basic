<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ArrowLeft, FullScreen } from "@element-plus/icons-vue";
import {
  fetchTenantBills,
  fetchTenantMeteringSummary,
  fetchTenantPage,
  fetchTenantQuotas,
  type TenantBillRow,
  type TenantMeteringSummary,
  type TenantRow
} from "@/api/modules/tenant";
import {
  fetchAuditOverview,
  fetchLoginLogs,
  fetchOperationLogs,
  type AuditOverview
} from "@/api/modules/audit";
import { listCrmContracts, type CrmContractRow } from "@/api/modules/crm";
import { fetchWorkflowInstances, type ProcessInstanceRow } from "@/api/modules/workflow";
import {
  fetchAiModels,
  fetchAiRuns,
  fetchAiTenantQuota,
  type AiModelConfigRow,
  type AiRunRow,
  type AiTenantQuotaRow
} from "@/api/modules/ai";
import {
  fetchDatasources,
  fetchMessageLogs,
  fetchPayOrders,
  type DatasourceRow
} from "@/api/modules/integration";
import { fetchJvmInfo, fetchRedisInfo, type JvmInfoData, type RedisInfoData } from "@/api/modules/system";

const router = useRouter();
const currentTime = ref("");
let clockTimer: ReturnType<typeof setInterval> | null = null;
let dataTimer: ReturnType<typeof setInterval> | null = null;
const isFullscreen = ref(false);

const tenants = ref<TenantRow[]>([]);
const tenantRanks = ref<Array<{ id: number; name: string; percent: number }>>([]);
const metering = ref<TenantMeteringSummary | null>(null);
const bills = ref<TenantBillRow[]>([]);
const contracts = ref<CrmContractRow[]>([]);
const auditOverview = ref<AuditOverview | null>(null);
const workflows = ref<ProcessInstanceRow[]>([]);
const aiModels = ref<AiModelConfigRow[]>([]);
const aiRuns = ref<AiRunRow[]>([]);
const aiQuota = ref<AiTenantQuotaRow | null>(null);
const datasources = ref<DatasourceRow[]>([]);
const messageLogCount = ref(0);
const payOrderCount = ref(0);
const jvmInfo = ref<JvmInfoData | null>(null);
const redisInfo = ref<RedisInfoData | null>(null);
const recentLogs = ref<Array<{ time: string; tenant: string; action: string; status: string }>>([]);

function updateTime() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const date = String(now.getDate()).padStart(2, "0");
  const hours = String(now.getHours()).padStart(2, "0");
  const minutes = String(now.getMinutes()).padStart(2, "0");
  const seconds = String(now.getSeconds()).padStart(2, "0");
  currentTime.value = `${year}-${month}-${date} ${hours}:${minutes}:${seconds}`;
}

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    void document.documentElement.requestFullscreen();
    isFullscreen.value = true;
  } else if (document.exitFullscreen) {
    void document.exitFullscreen();
    isFullscreen.value = false;
  }
}

function goBack() {
  void router.push("/dashboard");
}

function formatTimeShort(ts?: string) {
  if (!ts) return "--:--:--";
  const match = ts.match(/\d{2}:\d{2}:\d{2}/);
  return match ? match[0] : ts.slice(0, 8);
}

const snapshotTime = ref("");
const snapshotError = ref("");
let refreshing = false;

async function loadScreenData() {
  if (refreshing) return;
  refreshing = true;
  try {
    await refreshSnapshot();
    snapshotTime.value = new Date().toLocaleTimeString();
    snapshotError.value = "";
  } catch {
    snapshotError.value = "部分数据源读取失败";
  } finally {
    refreshing = false;
  }
}

async function refreshSnapshot() {
  const [
    nextTenants,
    nextMetering,
    nextBills,
    nextContracts,
    nextAudit,
    nextOpLogs,
    nextLoginLogs,
    nextWorkflows,
    nextAiModels,
    nextAiRuns,
    nextAiQuota,
    nextDatasources,
    nextMessages,
    nextPayOrders,
    nextJvm,
    nextRedis
  ] = await Promise.all([
    fetchTenantPage(),
    fetchTenantMeteringSummary(),
    fetchTenantBills(),
    listCrmContracts().then((r) => r.data ?? []),
    fetchAuditOverview(),
    fetchOperationLogs(),
    fetchLoginLogs(),
    fetchWorkflowInstances(),
    fetchAiModels(),
    fetchAiRuns(),
    fetchAiTenantQuota(),
    fetchDatasources(),
    fetchMessageLogs(),
    fetchPayOrders(),
    fetchJvmInfo(),
    fetchRedisInfo()
  ]);

  // Fetch real quota saturation for top 5 tenants
  const quotaOverviews = await Promise.all(
    nextTenants.slice(0, 5).map((t) => fetchTenantQuotas(t.id))
  );
  tenants.value = nextTenants;
  metering.value = nextMetering;
  bills.value = nextBills;
  contracts.value = nextContracts;
  auditOverview.value = nextAudit;
  workflows.value = nextWorkflows;
  aiModels.value = nextAiModels;
  aiRuns.value = nextAiRuns;
  aiQuota.value = nextAiQuota;
  datasources.value = nextDatasources;
  messageLogCount.value = nextMessages.length;
  payOrderCount.value = nextPayOrders.length;
  jvmInfo.value = nextJvm;
  redisInfo.value = nextRedis;

  tenantRanks.value = nextTenants
    .slice(0, 5)
    .map((t, idx) => {
      const q = quotaOverviews[idx];
      const peak = q && q.quotas.length > 0
        ? Math.max(...q.quotas.map((item) => Math.round(item.usagePercent ?? 0)))
        : 0;
      return {
        id: t.id,
        name: t.tenantName,
        percent: Math.min(100, Math.max(0, peak))
      };
    })
    .sort((a, b) => b.percent - a.percent);

  const tenantNameMap = new Map(nextTenants.map((t) => [t.id, t.tenantName]));
  const combinedLogs = [
    ...nextOpLogs.map((l) => ({
      sortKey: l.occurredAt || "",
      time: formatTimeShort(l.occurredAt),
      tenant: tenantNameMap.get(l.tenantId) ?? l.operatorName ?? `租户#${l.tenantId}`,
      action: `${l.bizModule} · ${l.operationType}`,
      status: l.success ? "SUCCESS" : "FAILED"
    })),
    ...nextLoginLogs.map((l) => ({
      sortKey: l.occurredAt || "",
      time: formatTimeShort(l.occurredAt),
      tenant: tenantNameMap.get(l.tenantId) ?? l.username,
      action: `登录认证 · ${l.loginType}`,
      status: l.success ? "SUCCESS" : "DENIED"
    }))
  ]
    .sort((a, b) => b.sortKey.localeCompare(a.sortKey))
    .slice(0, 12)
    .map(({ time, tenant, action, status }) => ({ time, tenant, action, status }));

  recentLogs.value = combinedLogs;
}

const stats = computed(() => {
  const contractTotal = contracts.value.reduce((acc, item) => acc + Number(item.totalPrice || 0), 0);
  const billTotal = bills.value.reduce((acc, item) => acc + Number(item.amount || 0), 0);
  const gmvValue = contractTotal > 0 ? contractTotal : billTotal;
  const activeCount = tenants.value.filter((t) => t.status === "ACTIVE" || t.status === "TRIAL").length;
  const events =
    (metering.value?.totalApiCalls ?? 0) ||
    ((auditOverview.value?.operationEventsToday ?? 0) + (auditOverview.value?.loginEventsToday ?? 0));

  return {
    totalGmv: `¥ ${gmvValue.toLocaleString("en-US", { maximumFractionDigits: 0 })}`,
    activeTenants: activeCount,
    throughputToday: events.toLocaleString(),
    workflowCount: workflows.value.length
  };
});

const aiDistributions = computed(() => {
  const colors = ["#00e5ff", "#3b82f6", "#10b981", "#f59e0b"];
  if (aiRuns.value.length > 0) {
    const counts = new Map<string, number>();
    for (const run of aiRuns.value) {
      const key = run.modelId || "unknown";
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
    const total = aiRuns.value.length;
    return Array.from(counts.entries())
      .sort((a, b) => b[1] - a[1])
      .slice(0, 4)
      .map(([name, count], idx) => ({
        name,
        percent: Math.round((count / total) * 100),
        color: colors[idx % colors.length]
      }));
  }
  const enabledModels = aiModels.value.filter((m) => m.status === "ENABLED").slice(0, 4);
  if (enabledModels.length === 0) {
    return [];
  }
  const share = Math.round(100 / enabledModels.length);
  return enabledModels.map((m, idx) => ({
    name: m.modelName || m.modelId,
    percent: idx === 0 ? 100 - share * (enabledModels.length - 1) : share,
    color: colors[idx % colors.length]
  }));
});

onMounted(() => {
  updateTime();
  clockTimer = setInterval(updateTime, 1000);
  void loadScreenData();
  dataTimer = setInterval(() => {
    void loadScreenData();
  }, 30000);
});

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer);
  if (dataTimer) clearInterval(dataTimer);
});
</script>

<template>
  <div class="data-screen-shell">
    <!-- 科技动态星轨与网格底纹 -->
    <div class="screen-bg-grid" />
    <div class="screen-glow-spot" />

    <!-- 顶部科技感 Header -->
    <header class="screen-header">
      <div class="header-left">
        <el-button class="screen-back-btn" :icon="ArrowLeft" plain size="small" @click="goBack">
          返回控制台
        </el-button>
        <span class="header-clock">{{ currentTime }}</span>
      </div>

      <div class="header-center">
        <div class="header-title-glow">
          <span class="title-sub">ANTIGRAVITY INTELLIGENT PLATFORM</span>
          <h1 class="title-main">企业级多租户数字化实时指挥中心</h1>
        </div>
      </div>

      <div class="header-right">
        <el-button class="screen-back-btn" :icon="FullScreen" plain size="small" @click="toggleFullscreen">
          {{ isFullscreen ? "退出全屏" : "全屏沉浸" }}
        </el-button>
        <span class="status-badge">
          <span v-if="snapshotTime && !snapshotError" class="status-dot-pulse" />
          {{ snapshotError || (snapshotTime ? '数据更新时间 ' + snapshotTime : '正在读取') }}
        </span>
      </div>
    </header>

    <!-- 主展示面板三栏布局 -->
    <p v-if="snapshotError && snapshotTime" role="alert">当前展示 {{ snapshotTime }} 的数据</p>
    <main v-if="snapshotTime" class="screen-body">
      <!-- 左栏：多租户分布 + 运行时与网关指标 -->
      <section class="screen-column col-left">
        <div class="screen-card">
          <div class="card-header">
            <span class="card-title-icon">✦</span>
            <h3>实时多租户配额饱和 TOP5</h3>
          </div>
          <div class="card-content">
            <div v-for="(item, idx) in tenantRanks" :key="item.id" class="tenant-rank-item">
              <span class="rank-badge" :class="idx < 3 ? `rank-${idx + 1}` : ''">{{ String(idx + 1).padStart(2, "0") }}</span>
              <span class="rank-name">{{ item.name }}</span>
              <div class="rank-bar-bg"><div class="rank-bar-fill" :style="{ width: `${item.percent}%` }" /></div>
              <span class="rank-val">{{ item.percent }}%</span>
            </div>
            <div v-if="tenantRanks.length === 0" class="screen-log-row">
              <span class="log-action">暂无租户配额数据</span>
            </div>
          </div>
        </div>

        <div class="screen-card mt-3">
          <div class="card-header">
            <span class="card-title-icon">✦</span>
            <h3>基础设施与集成网关实测指标</h3>
          </div>
          <div class="card-content">
            <div class="qps-grid">
              <div class="qps-cell">
                <span class="qps-label">消息网关投递</span>
                <span class="qps-num">{{ messageLogCount }} <small>条</small></span>
              </div>
              <div class="qps-cell">
                <span class="qps-label">支付交易流水</span>
                <span class="qps-num">{{ payOrderCount }} <small>笔</small></span>
              </div>
              <div class="qps-cell">
                <span class="qps-label">Redis 实时 ops/s</span>
                <span class="qps-num">{{ redisInfo?.available ? redisInfo.instantaneousOpsPerSec : "--" }} <small>命中 {{ redisInfo?.hitRate ?? 0 }}%</small></span>
              </div>
              <div class="qps-cell">
                <span class="qps-label">JVM 堆内存 / 线程</span>
                <span class="qps-num">{{ jvmInfo ? jvmInfo.heapUsedPercent.toFixed(1) : "--" }}% <small>{{ jvmInfo?.activeThreads ?? 0 }} 线程</small></span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 中栏：核心数字 KPI + 运行态枢纽拓扑 -->
      <section class="screen-column col-center">
        <!-- 核心统计大卡 -->
        <div class="kpi-banner">
          <div class="kpi-item">
            <span class="kpi-label">合同与账单累计规模</span>
            <span class="kpi-value text-gold">{{ stats.totalGmv }}</span>
          </div>
          <div class="kpi-divider" />
          <div class="kpi-item">
            <span class="kpi-label">当前活跃多租户</span>
            <span class="kpi-value text-cyan">{{ stats.activeTenants }} <small>家</small></span>
          </div>
          <div class="kpi-divider" />
          <div class="kpi-item">
            <span class="kpi-label">平台计量与审计总量</span>
            <span class="kpi-value text-blue">{{ stats.throughputToday }}</span>
          </div>
          <div class="kpi-divider" />
          <div class="kpi-item">
            <span class="kpi-label">工作流实例总数</span>
            <span class="kpi-value text-emerald">{{ stats.workflowCount }} <small>单</small></span>
          </div>
        </div>

        <!-- 真实平台核心节点运行监控示意图 -->
        <div class="screen-map-container">
          <div class="map-radar-circle circle-1" />
          <div class="map-radar-circle circle-2" />
          <div class="map-radar-circle circle-3" />

          <div class="map-node node-shanghai">
            <span class="node-dot" />
            <span class="node-label">
              数据源枢纽 · {{ datasources.length }} 个 ({{ datasources.filter((d) => d.testStatus === "PASSED").length }} 连通)
            </span>
          </div>
          <div class="map-node node-beijing">
            <span class="node-dot" />
            <span class="node-label">
              JVM 核心 · 堆 {{ jvmInfo ? jvmInfo.heapUsedPercent.toFixed(1) : "0.0" }}% ({{ jvmInfo?.availableProcessors ?? "-" }} 核)
            </span>
          </div>
          <div class="map-node node-shenzhen">
            <span class="node-dot" />
            <span class="node-label">
              Redis 缓存 · {{ redisInfo?.available ? `${redisInfo.totalKeys} Keys` : "单机退化" }}
            </span>
          </div>
          <div class="map-node node-chengdu">
            <span class="node-dot" />
            <span class="node-label">
              AI 网关 · {{ aiModels.filter((m) => m.status === "ENABLED").length }} 模型 ({{ (aiQuota?.usedTokensMonth ?? 0).toLocaleString() }} Tok)
            </span>
          </div>

          <div class="map-status-overlay">
            <span>
              今日审计事件 {{ (auditOverview?.operationEventsToday ?? 0) + (auditOverview?.loginEventsToday ?? 0) }} 次 ·
              当前存储占用 {{ metering?.storageMb ?? 0 }} MB ·
              JVM 连续运行 {{ Math.floor((jvmInfo?.uptimeSeconds ?? 0) / 60) }} 分钟
            </span>
          </div>
        </div>
      </section>

      <!-- 右栏：实时动态流水与 AI 模型负载 -->
      <section class="screen-column col-right">
        <div class="screen-card">
          <div class="card-header">
            <span class="card-title-icon">✦</span>
            <h3>AI 大模型调用分布 ({{ aiRuns.length > 0 ? "实测执行" : "已启用模型" }})</h3>
          </div>
          <div class="card-content">
            <div class="ai-model-dist">
              <div v-for="item in aiDistributions" :key="item.name" class="ai-dist-row">
                <span class="ai-name">{{ item.name }}</span>
                <el-progress :percentage="item.percent" :color="item.color" :stroke-width="10" />
              </div>
              <div v-if="aiDistributions.length === 0" class="screen-log-row">
                <span class="log-action">暂未配置启用中的 AI 模型</span>
              </div>
            </div>
          </div>
        </div>

        <div class="screen-card mt-3 flex-1">
          <div class="card-header">
            <span class="card-title-icon">✦</span>
            <h3>系统关键业务执行流水</h3>
          </div>
          <div class="card-content scroll-logs">
            <div v-for="(log, idx) in recentLogs" :key="idx" class="screen-log-row">
              <span class="log-time">{{ log.time }}</span>
              <span class="log-tenant">{{ log.tenant }}</span>
              <span class="log-action">{{ log.action }}</span>
              <span class="log-status">{{ log.status }}</span>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.data-screen-shell {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100vh;
  background-color: #030a16;
  color: #e2e8f0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  z-index: 9999;
}

.screen-bg-grid {
  position: absolute;
  inset: 0;
  background-image: 
    linear-gradient(rgb(0 229 255 / 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgb(0 229 255 / 0.04) 1px, transparent 1px);
  background-size: 40px 40px;
  pointer-events: none;
}

.screen-glow-spot {
  position: absolute;
  top: -100px;
  left: 50%;
  transform: translateX(-50%);
  width: 800px;
  height: 350px;
  background: radial-gradient(circle, rgb(0 229 255 / 0.15) 0%, rgb(3 10 22 / 0) 70%);
  pointer-events: none;
}

.screen-header {
  position: relative;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: linear-gradient(180deg, rgb(13 27 49 / 0.8) 0%, rgb(3 10 22 / 0.4) 100%);
  border-bottom: 1px solid rgb(0 229 255 / 0.2);
  z-index: 10;
}

.header-left, .header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 320px;
}

.header-right {
  justify-content: flex-end;
}

.screen-back-btn {
  background: rgb(0 229 255 / 0.1) !important;
  border-color: rgb(0 229 255 / 0.4) !important;
  color: #00e5ff !important;
}

.header-clock {
  font-family: monospace;
  font-size: 14px;
  color: #94a3b8;
  letter-spacing: 1px;
}

.header-center {
  text-align: center;
}

.title-sub {
  display: block;
  font-size: 11px;
  color: #00e5ff;
  letter-spacing: 3px;
  opacity: 0.8;
  margin-bottom: 2px;
}

.title-main {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 2px;
  background: linear-gradient(180deg, #fff 0%, #00e5ff 100%);
  background-clip: text;
  -webkit-text-fill-color: transparent;
  margin: 0;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #10b981;
  background: rgb(16 185 129 / 0.1);
  padding: 4px 10px;
  border-radius: 9999px;
  border: 1px solid rgb(16 185 129 / 0.3);
}

.status-dot-pulse {
  width: 8px;
  height: 8px;
  background: #10b981;
  border-radius: 50%;
  box-shadow: 0 0 8px #10b981;
}

.screen-body {
  flex: 1;
  display: grid;
  grid-template-columns: 360px 1fr 380px;
  gap: 20px;
  padding: 20px;
  z-index: 10;
  overflow: hidden;
}

.screen-column {
  display: flex;
  flex-direction: column;
}

.screen-card {
  background: rgb(13 27 49 / 0.6);
  border: 1px solid rgb(0 229 255 / 0.2);
  border-radius: 12px;
  padding: 16px;
  backdrop-filter: blur(10px);
  box-shadow: inset 0 0 20px rgb(0 229 255 / 0.05);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid rgb(255 255 255 / 0.08);
  padding-bottom: 10px;
  margin-bottom: 14px;
}

.card-title-icon {
  color: #00e5ff;
}

.card-header h3 {
  font-size: 15px;
  font-weight: 600;
  color: #f1f5f9;
  margin: 0;
}

.tenant-rank-item {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  font-size: 13px;
}

.rank-badge {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  background: rgb(255 255 255 / 0.1);
  font-weight: bold;
  font-size: 11px;
}

.rank-1 { background: #eab308; color: #000; }
.rank-2 { background: #94a3b8; color: #000; }
.rank-3 { background: #d97706; color: #fff; }

.rank-name {
  width: 130px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #cbd5e1;
}

.rank-bar-bg {
  flex: 1;
  height: 6px;
  background: rgb(255 255 255 / 0.1);
  border-radius: 3px;
  overflow: hidden;
}

.rank-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #0284c7 0%, #00e5ff 100%);
  border-radius: 3px;
}

.rank-val {
  font-family: monospace;
  color: #00e5ff;
  font-size: 12px;
  width: 32px;
  text-align: right;
}

.qps-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.qps-cell {
  background: rgb(255 255 255 / 0.03);
  padding: 12px;
  border-radius: 8px;
  border: 1px solid rgb(255 255 255 / 0.05);
}

.qps-label {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.qps-num {
  font-size: 18px;
  font-weight: bold;
  color: #00e5ff;
  font-family: monospace;
}

.qps-num small {
  font-size: 11px;
  color: #64748b;
  font-weight: normal;
}

.kpi-banner {
  display: flex;
  align-items: center;
  justify-content: space-around;
  background: rgb(13 27 49 / 0.7);
  border: 1px solid rgb(0 229 255 / 0.25);
  border-radius: 12px;
  padding: 18px 24px;
  margin-bottom: 20px;
}

.kpi-item {
  text-align: center;
}

.kpi-label {
  display: block;
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 6px;
}

.kpi-value {
  font-size: 26px;
  font-weight: 800;
  font-family: monospace;
}

.kpi-value small {
  font-size: 14px;
  font-weight: normal;
}

.text-gold { color: #facc15; text-shadow: 0 0 12px rgb(250 204 21 / 0.4); }
.text-cyan { color: #00e5ff; text-shadow: 0 0 12px rgb(0 229 255 / 0.4); }
.text-blue { color: #60a5fa; }
.text-emerald { color: #34d399; }

.kpi-divider {
  width: 1px;
  height: 36px;
  background: rgb(255 255 255 / 0.1);
}

.screen-map-container {
  flex: 1;
  position: relative;
  background: radial-gradient(circle at center, rgb(14 165 233 / 0.1) 0%, rgb(3 10 22 / 0) 70%);
  border: 1px solid rgb(0 229 255 / 0.15);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.map-radar-circle {
  position: absolute;
  border-radius: 50%;
  border: 1px dashed rgb(0 229 255 / 0.2);
  pointer-events: none;
}

.circle-1 { width: 220px; height: 220px; }
.circle-2 { width: 420px; height: 420px; }
.circle-3 { width: 620px; height: 620px; }

.map-node {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgb(3 10 22 / 0.7);
  padding: 6px 12px;
  border-radius: 20px;
  border: 1px solid rgb(0 229 255 / 0.4);
  font-size: 12px;
  color: #f8fafc;
}

.node-dot {
  width: 8px;
  height: 8px;
  background: #00e5ff;
  border-radius: 50%;
  box-shadow: 0 0 10px #00e5ff;
}

.node-shanghai { top: 48%; left: 58%; }
.node-beijing { top: 32%; left: 52%; }
.node-shenzhen { top: 62%; left: 54%; }
.node-chengdu { top: 54%; left: 38%; }

.map-status-overlay {
  position: absolute;
  bottom: 16px;
  font-size: 12px;
  color: #64748b;
  letter-spacing: 1px;
}

.ai-dist-row {
  margin-bottom: 14px;
}

.ai-name {
  display: block;
  font-size: 12px;
  color: #cbd5e1;
  margin-bottom: 6px;
}

.scroll-logs {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.screen-log-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  padding: 8px 10px;
  background: rgb(255 255 255 / 0.02);
  border-radius: 6px;
  border-left: 3px solid #00e5ff;
}

.log-time {
  color: #64748b;
  font-family: monospace;
}

.log-tenant {
  color: #93c5fd;
  font-weight: 500;
}

.log-action {
  flex: 1;
  color: #e2e8f0;
}

.log-status {
  color: #10b981;
  font-size: 10px;
  background: rgb(16 185 129 / 0.15);
  padding: 2px 6px;
  border-radius: 4px;
}

.mt-3 {
  margin-top: 16px;
}

.flex-1 {
  flex: 1;
}
</style>
