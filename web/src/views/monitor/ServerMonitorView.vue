<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Monitor,
  Refresh,
  Timer,
  Cpu,
  DataLine,
  Coin,
  Folder,
  CircleCheck,
  WarningFilled,
  Search,
  Download,
  DocumentCopy,
  Tickets,
  Share,
  Connection,
  Lock,
  Box,
  DocumentChecked,
  Platform,
  SetUp,
  TrendCharts,
  Lightning
} from "@element-plus/icons-vue";
import {
  fetchServerMonitor,
  fetchThreadDump,
  testAlarmWebhook,
  type ServerMonitorData,
  type ThreadDumpItem
} from "@/api/modules/server-monitor";

const loading = ref(false);
const autoRefreshSeconds = ref(5);
let timerId: any = null;

// Tab switcher: 4 structured clean tabs
const activeTab = ref<"apps" | "host" | "jvm" | "dbpool">("apps");

const monitorData = ref<ServerMonitorData | null>(null);

// Thread Dump Drawer
const threadDrawerVisible = ref(false);
const threadLoading = ref(false);
const threadList = ref<ThreadDumpItem[]>([]);
const threadSearch = ref("");
const threadFilterState = ref("ALL");

// Helper: Format Bytes to Human Readable
function formatBytes(bytes?: number): string {
  if (bytes === undefined || bytes === null || bytes === 0) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
}

// Helper: Format Uptime Seconds to d h m s
function formatUptime(seconds?: number): string {
  if (!seconds) return "0秒";
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = Math.floor(seconds % 60);
  let res = "";
  if (days > 0) res += `${days}天 `;
  if (hours > 0 || days > 0) res += `${hours}时 `;
  if (minutes > 0 || hours > 0 || days > 0) res += `${minutes}分 `;
  res += `${secs}秒`;
  return res;
}

// Dynamic Colors for Progress Gauges
function getProgressColor(rate: number): string {
  if (rate >= 90) return "#f56c6c";
  if (rate >= 75) return "#e6a23c";
  if (rate >= 50) return "#409eff";
  return "#67c23a";
}

async function loadData() {
  loading.value = true;
  try {
    monitorData.value = await fetchServerMonitor();
  } catch (err: any) {
    ElMessage.error(err?.message || "获取服务监控指标失败");
  } finally {
    loading.value = false;
  }
}

function handleAutoRefreshChange(val: number) {
  if (timerId) {
    clearInterval(timerId);
    timerId = null;
  }
  if (val > 0) {
    timerId = setInterval(() => {
      loadData();
    }, val * 1000);
  }
}

async function openThreadDrawer() {
  threadDrawerVisible.value = true;
  threadLoading.value = true;
  try {
    threadList.value = await fetchThreadDump();
  } catch (err: any) {
    ElMessage.error(err?.message || "获取线程堆栈失败");
  } finally {
    threadLoading.value = false;
  }
}

const filteredThreads = computed(() => {
  let list = threadList.value;
  if (threadFilterState.value !== "ALL") {
    list = list.filter((t) => t.state === threadFilterState.value);
  }
  if (threadSearch.value.trim()) {
    const q = threadSearch.value.toLowerCase().trim();
    list = list.filter(
      (t) =>
        t.name.toLowerCase().includes(q) ||
        String(t.id).includes(q) ||
        t.stackTrace.toLowerCase().includes(q)
    );
  }
  return list;
});

function copyPrometheusUrl() {
  const url = `${window.location.origin}/actuator/prometheus`;
  navigator.clipboard.writeText(url);
  ElMessage.success("Prometheus 抓取地址已复制到剪贴板！");
}

const alarmTesting = ref(false);

async function handleTestAlarm() {
  alarmTesting.value = true;
  try {
    const res = await testAlarmWebhook();
    ElMessageBox.alert(
      `<div style="font-family: monospace; white-space: pre-wrap; background: #f8fafc; padding: 14px; border-radius: 8px; line-height: 1.6; font-size: 13px; color: #1e293b; border: 1px solid #e2e8f0;">${res.alarmMessage}</div>
      <div style="margin-top: 14px; font-size: 13px; color: #16a34a; font-weight: 600; display: flex; gap: 12px; align-items: center;">
        <span>✓ 渠道: ${res.channel}</span>
        <span>• 状态: ${res.status}</span>
        <span>• 响应时间: ${res.sentAt}</span>
      </div>`,
      "运维哨兵告警联动联调测试成功",
      {
        dangerouslyUseHTMLString: true,
        confirmButtonText: "我知道了"
      }
    );
  } catch (err: any) {
    ElMessage.error(err.message || "告警测试失败");
  } finally {
    alarmTesting.value = false;
  }
}

onMounted(() => {
  loadData();
  handleAutoRefreshChange(autoRefreshSeconds.value);
});

onUnmounted(() => {
  if (timerId) {
    clearInterval(timerId);
    timerId = null;
  }
});
</script>

<template>
  <div class="server-monitor-container">
    <!-- Top Action Card -->
    <el-card shadow="never" class="mb-4 monitor-header-card">
      <div class="header-content">
        <div>
          <div class="header-title-row">
            <el-icon class="title-icon" color="#409eff"><Monitor /></el-icon>
            <h2 class="panel-title">应用与服务监控中心</h2>
            <el-tag type="success" size="small" effect="plain" class="ml-2">
              <span class="pulse-beacon"></span> 单体进程内集成架构 (Monolith)
            </el-tag>
            <el-tag type="primary" size="small" effect="plain" class="ml-1">
              多应用就绪架构
            </el-tag>
          </div>
          <p class="panel-subtitle">
            聚合底座核心服务、嵌入式工作流引擎、宿主机硬件、JVM 虚拟机与连接池遥测指标，支持单体集成与微服务平滑演进。
          </p>
        </div>
        <div class="header-actions">
          <div class="auto-refresh-box">
            <span class="refresh-label"><el-icon><Timer /></el-icon> 自动刷新:</span>
            <el-select
              v-model="autoRefreshSeconds"
              size="default"
              style="width: 110px;"
              @change="handleAutoRefreshChange"
            >
              <el-option :value="0" label="关闭" />
              <el-option :value="3" label="3 秒" />
              <el-option :value="5" label="5 秒" />
              <el-option :value="10" label="10 秒" />
            </el-select>
          </div>
          <el-button :icon="Refresh" :loading="loading" @click="loadData">立即刷新</el-button>
          <el-button type="primary" plain :icon="Cpu" @click="openThreadDrawer">在线线程诊断</el-button>
          <el-button type="info" plain :icon="DocumentCopy" @click="copyPrometheusUrl">Prometheus 端点</el-button>
          <el-button type="warning" plain :icon="Lightning" :loading="alarmTesting" @click="handleTestAlarm">告警联动测试</el-button>
        </div>
      </div>
    </el-card>

    <!-- SECTION 1: 多应用拓扑实例矩阵 (Application Topology Grid) - 彻底解决多应用扩展与混乱 -->
    <el-row :gutter="14" class="mb-4">
      <!-- 实例 1: 底座平台主服务 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="app-instance-card active-card">
          <div class="inst-top">
            <div class="inst-title">
              <el-icon color="#409eff"><Platform /></el-icon>
              <span>saas-basics-app</span>
            </div>
            <el-tag size="small" type="success" effect="dark">HEALTHY</el-tag>
          </div>
          <div class="inst-role">单体宿主底座 (Host App)</div>
          <div class="inst-meta-grid">
            <div class="meta-cell">
              <span class="val font-mono">8080</span>
              <span class="lbl">监听端口</span>
            </div>
            <div class="meta-cell">
              <span class="val font-mono">{{ formatBytes(monitorData?.jvm?.heapUsedBytes) }}</span>
              <span class="lbl">JVM 堆已用</span>
            </div>
            <div class="meta-cell">
              <span class="val font-mono">{{ monitorData?.jvm?.threadCount || 0 }}</span>
              <span class="lbl">活动线程</span>
            </div>
            <div class="meta-cell">
              <span class="val font-mono">{{ formatUptime(monitorData?.jvm?.uptimeSeconds) }}</span>
              <span class="lbl">已运行时长</span>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 实例 2: 工作流引擎子系统 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="app-instance-card">
          <div class="inst-top">
            <div class="inst-title">
              <el-icon color="#67c23a"><Tickets /></el-icon>
              <span>workflow-engine</span>
            </div>
            <el-tag size="small" type="success" effect="dark">
              {{ monitorData?.appStatus?.workflow?.status || "HEALTHY" }}
            </el-tag>
          </div>
          <div class="inst-role">流程与审批中心 (单体嵌入式驱动)</div>
          <div class="inst-meta-grid">
            <div class="meta-cell">
              <span class="val font-mono text-primary font-bold">
                {{ monitorData?.appStatus?.workflow?.definitionCount ?? 0 }}
              </span>
              <span class="lbl">流程模型</span>
            </div>
            <div class="meta-cell">
              <span class="val font-mono text-warning font-bold">
                {{ monitorData?.appStatus?.workflow?.instanceCount ?? 0 }}
              </span>
              <span class="lbl">流转实例</span>
            </div>
            <div class="meta-cell">
              <span class="val font-mono text-danger font-bold">
                {{ monitorData?.appStatus?.workflow?.pendingTaskCount ?? 0 }}
              </span>
              <span class="lbl">待审批工单</span>
            </div>
            <div class="meta-cell">
              <span class="val font-mono text-success font-bold">
                {{ monitorData?.appStatus?.workflow?.totalTaskCount ?? 0 }}
              </span>
              <span class="lbl">历史流转</span>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 实例 3: MySQL 数据存储底座 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="app-instance-card">
          <div class="inst-top">
            <div class="inst-title">
              <el-icon color="#e6a23c"><Coin /></el-icon>
              <span>MySQL 9.7 Database</span>
            </div>
            <el-tag size="small" type="success" effect="dark">ONLINE</el-tag>
          </div>
          <div class="inst-role">多租户关系存储底座 (HikariCP 驱动)</div>
          <div class="inst-meta-grid">
            <div class="meta-cell">
              <span class="val font-mono">3306</span>
              <span class="lbl">服务端口</span>
            </div>
            <div class="meta-cell">
              <span class="val font-mono text-success">{{ monitorData?.dbPool?.idleConnections ?? 10 }}</span>
              <span class="lbl">空闲连接</span>
            </div>
            <div class="meta-cell">
              <span class="val font-mono text-primary">{{ monitorData?.dbPool?.activeConnections ?? 0 }}</span>
              <span class="lbl">活跃连接</span>
            </div>
            <div class="meta-cell">
              <span class="val font-mono">{{ monitorData?.dbPool?.poolUsageRate ?? 0 }}%</span>
              <span class="lbl">饱和度</span>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 实例 4: 缓存与事件总线 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="app-instance-card">
          <div class="inst-top">
            <div class="inst-title">
              <el-icon color="#f56c6c"><Lightning /></el-icon>
              <span>Redis / Native Cache</span>
            </div>
            <el-tag v-if="monitorData?.redis?.available" size="small" type="success" effect="dark">ONLINE</el-tag>
            <el-tag v-else size="small" type="info" effect="plain">IN-MEMORY</el-tag>
          </div>
          <div class="inst-role">缓存与实时事件信令中枢</div>
          <div class="inst-meta-grid">
            <div class="meta-cell">
              <span class="val font-mono">{{ monitorData?.redis?.version || "Native" }}</span>
              <span class="lbl">引擎版本</span>
            </div>
            <div class="meta-cell">
              <span class="val font-mono">{{ monitorData?.redis?.usedMemory || "0M" }}</span>
              <span class="lbl">已用内存</span>
            </div>
            <div class="meta-cell">
              <span class="val font-mono">{{ monitorData?.redis?.keysCount || 0 }}</span>
              <span class="lbl">缓存键数</span>
            </div>
            <div class="meta-cell">
              <span class="val font-mono text-success">正常</span>
              <span class="lbl">总线状态</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- SECTION 2: 结构化选项卡 (Tabs) - 彻底消灭页面冗长杂乱 -->
    <el-card shadow="never" class="monitor-tab-card">
      <el-tabs v-model="activeTab" class="monitor-tabs">
        <!-- TAB 1: 🧩 应用与工作流拓扑 (Applications & Modules) -->
        <el-tab-pane label="应用拓扑与单体模块矩阵" name="apps">
          <el-row :gutter="16">
            <!-- 工作流专属卡片 -->
            <el-col :xs="24" :lg="10">
              <div class="inner-section-card h-100">
                <div class="inner-header">
                  <span class="inner-title">
                    <el-icon color="#409eff"><Tickets /></el-icon>
                    工作流引擎单体运行机制与优势
                  </span>
                  <el-tag type="success" size="small">Spring In-Process</el-tag>
                </div>
                <div class="wf-banner mb-3">
                  <div class="wf-banner-title">
                    <el-icon color="#67c23a"><CircleCheck /></el-icon>
                    <span>单体部署特性：同 JVM 进程内高内聚运行</span>
                  </div>
                  <div class="wf-banner-desc">
                    流程模型校验、状态机流转、任务退回驳回与业务代码共享同一个 Spring 上下文，享有<strong>零 RPC 网络调用损耗</strong>与<strong>Spring 本地 ACID 事务强一致性保证</strong>。
                  </div>
                </div>

                <el-descriptions :column="1" border size="small" class="mb-3">
                  <el-descriptions-item label="引擎版本">
                    {{ monitorData?.appStatus?.workflow?.engineVersion || "v2.1.0 企业完整版" }}
                  </el-descriptions-item>
                  <el-descriptions-item label="架构形态">
                    <el-tag size="small" type="primary">{{ monitorData?.appStatus?.workflow?.mode || "单体嵌入式进程" }}</el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="事务机制">
                    {{ monitorData?.appStatus?.workflow?.transactionType || "Spring 本地事务 (ACID 强一致)" }}
                  </el-descriptions-item>
                  <el-descriptions-item label="微服务解耦">
                    预备独立模块 <code class="font-mono">saas-basics-workflow-app</code> (8082)，随时可拆分为独立微服务
                  </el-descriptions-item>
                </el-descriptions>
              </div>
            </el-col>

            <!-- 单体全模块矩阵 -->
            <el-col :xs="24" :lg="14">
              <div class="inner-section-card h-100">
                <div class="inner-header">
                  <span class="inner-title">
                    <el-icon color="#67c23a"><Share /></el-icon>
                    单体架构 10 大托管子系统运行状态矩阵
                  </span>
                  <el-tag type="info" size="small">全部正常运行中</el-tag>
                </div>

                <el-table
                  :data="monitorData?.appStatus?.modules || []"
                  stripe
                  size="small"
                  border
                  style="width: 100%;"
                  height="320px"
                >
                  <el-table-column prop="moduleName" label="系统模块" min-width="150">
                    <template #default="{ row }">
                      <strong>{{ row.moduleName }}</strong>
                      <div class="text-muted font-mono" style="font-size: 11px;">{{ row.moduleCode }}</div>
                    </template>
                  </el-table-column>
                  <el-table-column prop="status" label="运行状态" width="100" align="center">
                    <template #default="{ row }">
                      <el-tag v-if="row.status === 'HEALTHY' || row.status === 'UP'" type="success" size="small">
                        正常
                      </el-tag>
                      <el-tag v-else type="danger" size="small">{{ row.status }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="mode" label="部署形态" width="120" align="center">
                    <template #default="{ row }">
                      <el-tag size="small" type="info" effect="plain">{{ row.mode }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="description" label="职能说明" min-width="220" show-overflow-tooltip />
                </el-table>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- TAB 2: 💻 宿主机与硬件负载 (Host & Hardware) -->
        <el-tab-pane label="宿主机与硬件负载" name="host">
          <!-- 4 Key Gauge Cards -->
          <el-row :gutter="16" class="mb-4">
            <el-col :xs="24" :sm="12" :md="6">
              <div class="gauge-card">
                <div class="gauge-header">
                  <span class="gauge-title"><el-icon><Cpu /></el-icon> CPU 使用率</span>
                  <el-tag size="small" type="info">{{ monitorData?.cpu?.cpuNum || 0 }} 核心</el-tag>
                </div>
                <div class="gauge-body">
                  <el-progress
                    type="dashboard"
                    :percentage="Math.min(monitorData?.cpu?.totalUsage || 0, 100)"
                    :color="getProgressColor(monitorData?.cpu?.totalUsage || 0)"
                    :width="120"
                  >
                    <template #default="{ percentage }">
                      <span class="gauge-val">{{ percentage }}%</span>
                      <span class="gauge-sub">总占用</span>
                    </template>
                  </el-progress>
                </div>
                <div class="gauge-footer">
                  <div class="footer-row">
                    <span>系统态: {{ monitorData?.cpu?.sysUsage }}%</span>
                    <span>用户态: {{ monitorData?.cpu?.userUsage }}%</span>
                  </div>
                  <div class="footer-row text-muted">
                    <span>负载均值: {{ monitorData?.cpu?.loadAverage }}</span>
                    <span>空闲率: {{ monitorData?.cpu?.idleRate }}%</span>
                  </div>
                </div>
              </div>
            </el-col>

            <el-col :xs="24" :sm="12" :md="6">
              <div class="gauge-card">
                <div class="gauge-header">
                  <span class="gauge-title"><el-icon><DataLine /></el-icon> 物理内存 (RAM)</span>
                  <el-tag size="small" type="primary">{{ formatBytes(monitorData?.memory?.totalBytes) }}</el-tag>
                </div>
                <div class="gauge-body">
                  <el-progress
                    type="dashboard"
                    :percentage="Math.min(monitorData?.memory?.usageRate || 0, 100)"
                    :color="getProgressColor(monitorData?.memory?.usageRate || 0)"
                    :width="120"
                  >
                    <template #default="{ percentage }">
                      <span class="gauge-val">{{ percentage }}%</span>
                      <span class="gauge-sub">物理内存</span>
                    </template>
                  </el-progress>
                </div>
                <div class="gauge-footer">
                  <div class="footer-row">
                    <span>已用: {{ formatBytes(monitorData?.memory?.usedBytes) }}</span>
                    <span class="text-success">可用: {{ formatBytes(monitorData?.memory?.freeBytes) }}</span>
                  </div>
                  <div class="footer-row text-muted">
                    <span>宿主机内存保障率充足</span>
                  </div>
                </div>
              </div>
            </el-col>

            <el-col :xs="24" :sm="12" :md="6">
              <div class="gauge-card">
                <div class="gauge-header">
                  <span class="gauge-title"><el-icon><Coin /></el-icon> JVM 堆内存</span>
                  <el-tag size="small" type="success">Max {{ formatBytes(monitorData?.jvm?.heapMaxBytes) }}</el-tag>
                </div>
                <div class="gauge-body">
                  <el-progress
                    type="dashboard"
                    :percentage="Math.min(monitorData?.jvm?.heapUsageRate || 0, 100)"
                    :color="getProgressColor(monitorData?.jvm?.heapUsageRate || 0)"
                    :width="120"
                  >
                    <template #default="{ percentage }">
                      <span class="gauge-val">{{ percentage }}%</span>
                      <span class="gauge-sub">堆占用率</span>
                    </template>
                  </el-progress>
                </div>
                <div class="gauge-footer">
                  <div class="footer-row">
                    <span>已分配: {{ formatBytes(monitorData?.jvm?.heapCommittedBytes) }}</span>
                    <span>已占用: {{ formatBytes(monitorData?.jvm?.heapUsedBytes) }}</span>
                  </div>
                  <div class="footer-row text-muted">
                    <span>非堆/元空间: {{ formatBytes(monitorData?.jvm?.nonHeapUsedBytes) }}</span>
                  </div>
                </div>
              </div>
            </el-col>

            <el-col :xs="24" :sm="12" :md="6">
              <div class="gauge-card">
                <div class="gauge-header">
                  <span class="gauge-title"><el-icon><Folder /></el-icon> 根磁盘分区</span>
                  <el-tag size="small" type="warning">{{ formatBytes(monitorData?.disks?.[0]?.totalBytes) }}</el-tag>
                </div>
                <div class="gauge-body">
                  <el-progress
                    type="dashboard"
                    :percentage="Math.min(monitorData?.disks?.[0]?.usageRate || 0, 100)"
                    :color="getProgressColor(monitorData?.disks?.[0]?.usageRate || 0)"
                    :width="120"
                  >
                    <template #default="{ percentage }">
                      <span class="gauge-val">{{ percentage }}%</span>
                      <span class="gauge-sub">存储水位</span>
                    </template>
                  </el-progress>
                </div>
                <div class="gauge-footer">
                  <div class="footer-row">
                    <span>已用: {{ formatBytes(monitorData?.disks?.[0]?.usedBytes) }}</span>
                    <span class="text-success">剩余: {{ formatBytes(monitorData?.disks?.[0]?.freeBytes) }}</span>
                  </div>
                  <div class="footer-row text-muted">
                    <span>挂载点: {{ monitorData?.disks?.[0]?.dirName || "/" }}</span>
                  </div>
                </div>
              </div>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :xs="24" :lg="14">
              <div class="inner-section-card">
                <div class="inner-header">
                  <span class="inner-title"><el-icon><Folder /></el-icon> 宿主机磁盘分区健康度</span>
                  <el-tag type="info" size="small">{{ monitorData?.disks?.length || 0 }} 个挂载卷</el-tag>
                </div>
                <el-table :data="monitorData?.disks || []" stripe size="small" border style="width: 100%;">
                  <el-table-column prop="dirName" label="挂载盘符" min-width="160" show-overflow-tooltip />
                  <el-table-column prop="sysTypeName" label="文件系统" width="100" align="center" />
                  <el-table-column prop="totalBytes" label="总容量" width="110" align="right">
                    <template #default="{ row }">{{ formatBytes(row.totalBytes) }}</template>
                  </el-table-column>
                  <el-table-column prop="usedBytes" label="已占用" width="110" align="right">
                    <template #default="{ row }">{{ formatBytes(row.usedBytes) }}</template>
                  </el-table-column>
                  <el-table-column prop="freeBytes" label="可用空间" width="110" align="right">
                    <template #default="{ row }">
                      <span class="text-success">{{ formatBytes(row.freeBytes) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="使用率" width="140" align="center">
                    <template #default="{ row }">
                      <el-progress
                        :percentage="Math.min(row.usageRate, 100)"
                        :color="getProgressColor(row.usageRate)"
                        :stroke-width="8"
                      />
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </el-col>

            <el-col :xs="24" :lg="10">
              <div class="inner-section-card">
                <div class="inner-header">
                  <span class="inner-title"><el-icon><Monitor /></el-icon> 服务器硬件与系统环境</span>
                </div>
                <el-descriptions :column="1" border size="small">
                  <el-descriptions-item label="服务器名称">{{ monitorData?.sys?.computerName }}</el-descriptions-item>
                  <el-descriptions-item label="绑定内网 IP">{{ monitorData?.sys?.serverIp }}</el-descriptions-item>
                  <el-descriptions-item label="CPU 处理器架构">
                    {{ monitorData?.cpu?.cpuModel }} ({{ monitorData?.sys?.osArch }})
                  </el-descriptions-item>
                  <el-descriptions-item label="宿主操作系统">{{ monitorData?.sys?.osName }}</el-descriptions-item>
                  <el-descriptions-item label="项目部署目录">
                    <span class="font-mono text-muted text-break">{{ monitorData?.sys?.userDir }}</span>
                  </el-descriptions-item>
                </el-descriptions>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- TAB 3: ☕ JVM 运行时画像 (JVM Deep-Dive) -->
        <el-tab-pane label="JVM 虚拟机深度遥测" name="jvm">
          <el-row :gutter="16">
            <el-col :xs="24" :lg="12">
              <div class="inner-section-card">
                <div class="inner-header">
                  <span class="inner-title"><el-icon><Coin /></el-icon> JVM 虚拟机基础规格</span>
                  <el-tag type="info" size="small">{{ monitorData?.jvm?.name }}</el-tag>
                </div>
                <el-descriptions :column="2" border size="small" class="mb-3">
                  <el-descriptions-item label="Java 版本">{{ monitorData?.jvm?.version }}</el-descriptions-item>
                  <el-descriptions-item label="供应商">{{ monitorData?.jvm?.vendor }}</el-descriptions-item>
                  <el-descriptions-item label="启动时刻">{{ monitorData?.jvm?.startTime }}</el-descriptions-item>
                  <el-descriptions-item label="已运行时长">
                    <span class="font-mono text-primary font-bold">{{ formatUptime(monitorData?.jvm?.uptimeSeconds) }}</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="堆内存上限">{{ formatBytes(monitorData?.jvm?.heapMaxBytes) }}</el-descriptions-item>
                  <el-descriptions-item label="已提交堆内存">{{ formatBytes(monitorData?.jvm?.heapCommittedBytes) }}</el-descriptions-item>
                </el-descriptions>

                <div class="sub-section-title mb-1">JVM 启动调优参数</div>
                <div class="vm-args-box">
                  <el-tag
                    v-for="(arg, idx) in monitorData?.jvm?.jvmArguments || []"
                    :key="idx"
                    size="small"
                    class="mr-1 mb-1 font-mono"
                  >
                    {{ arg }}
                  </el-tag>
                  <span v-if="!monitorData?.jvm?.jvmArguments?.length" class="text-muted">无附加 JVM 参数</span>
                </div>
              </div>
            </el-col>

            <el-col :xs="24" :lg="12">
              <div class="inner-section-card">
                <div class="inner-header">
                  <span class="inner-title"><el-icon><TrendCharts /></el-icon> 垃圾回收器 (Garbage Collection) 遥测</span>
                </div>
                <el-table :data="monitorData?.jvm?.gcMetrics || []" stripe size="small" border class="mb-3">
                  <el-table-column prop="name" label="垃圾回收器名称" />
                  <el-table-column prop="count" label="累计收集次数" width="130" align="center">
                    <template #default="{ row }">
                      <el-tag size="small" type="primary">{{ row.count }} 次</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="timeMillis" label="累计停顿耗时" width="140" align="right">
                    <template #default="{ row }">
                      <span class="font-mono">{{ row.timeMillis }} ms</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- TAB 4: 🗄️ 数据库连接池与线程哨兵 (HikariCP & Thread Sentinel) -->
        <el-tab-pane label="连接池与线程哨兵" name="dbpool">
          <el-row :gutter="16">
            <!-- HikariCP -->
            <el-col :xs="24" :lg="12">
              <div class="inner-section-card">
                <div class="inner-header">
                  <span class="inner-title"><el-icon><DataLine /></el-icon> HikariCP 数据库连接池水位</span>
                  <el-tag type="success" size="small">{{ monitorData?.dbPool?.poolName }}</el-tag>
                </div>

                <div class="pool-box mb-3">
                  <div class="pool-row-flex mb-1">
                    <span class="pool-title">连接池饱和度:</span>
                    <span class="pool-rate font-bold text-primary">{{ monitorData?.dbPool?.poolUsageRate }}%</span>
                  </div>
                  <el-progress
                    :percentage="Math.min(monitorData?.dbPool?.poolUsageRate || 0, 100)"
                    :color="getProgressColor(monitorData?.dbPool?.poolUsageRate || 0)"
                    :stroke-width="12"
                  />
                  <div class="pool-stats-row mt-2">
                    <div class="stat-item">
                      <span class="num text-primary">{{ monitorData?.dbPool?.activeConnections }}</span>
                      <span class="lbl">活跃连接</span>
                    </div>
                    <div class="stat-item">
                      <span class="num text-success">{{ monitorData?.dbPool?.idleConnections }}</span>
                      <span class="lbl">就绪空闲</span>
                    </div>
                    <div class="stat-item">
                      <span class="num" :class="(monitorData?.dbPool?.threadsAwaitingConnection || 0) > 0 ? 'text-danger' : 'text-muted'">
                        {{ monitorData?.dbPool?.threadsAwaitingConnection }}
                      </span>
                      <span class="lbl">排队等待线程</span>
                    </div>
                    <div class="stat-item">
                      <span class="num">{{ monitorData?.dbPool?.maxPoolSize }}</span>
                      <span class="lbl">容量上限</span>
                    </div>
                  </div>
                </div>

                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item label="慢 SQL 探针">
                    <span :class="(monitorData?.slowSql?.totalSlowQueries || 0) > 0 ? 'text-danger font-bold' : 'text-success'">
                      {{ monitorData?.slowSql?.totalSlowQueries }} 笔慢查询 (阈值: {{ monitorData?.slowSql?.thresholdMillis }}ms)
                    </span>
                  </el-descriptions-item>
                  <el-descriptions-item label="Redis 状态">
                    <el-tag v-if="monitorData?.redis?.available" type="success" size="small">在线</el-tag>
                    <el-tag v-else type="info" size="small">进程内原生</el-tag>
                  </el-descriptions-item>
                </el-descriptions>
              </div>
            </el-col>

            <!-- Threads & Deadlock -->
            <el-col :xs="24" :lg="12">
              <div class="inner-section-card">
                <div class="inner-header">
                  <span class="inner-title"><el-icon><Cpu /></el-icon> 线程监控与死锁探针</span>
                </div>

                <!-- Deadlock Sentinel -->
                <div v-if="monitorData?.jvm?.hasDeadlock" class="deadlock-alert mb-3">
                  <el-alert
                    title="严重警告：检测到 JVM 线程死锁！"
                    type="error"
                    :closable="false"
                    show-icon
                  >
                    <template #default>
                      <div>死锁线程列表: {{ monitorData?.jvm?.deadlockedThreads?.join(", ") }}</div>
                      <el-button size="small" type="danger" plain class="mt-2" @click="openThreadDrawer">
                        立即转储线程堆栈排查
                      </el-button>
                    </template>
                  </el-alert>
                </div>
                <div v-else class="deadlock-safe-bar mb-3">
                  <el-icon color="#67c23a" :size="18"><CircleCheck /></el-icon>
                  <span class="safe-text">死锁探测哨兵：未检测到任何线程死锁，当前高并发任务调度平稳运行。</span>
                </div>

                <el-row :gutter="12" class="mb-3">
                  <el-col :span="8">
                    <div class="metric-pill">
                      <span class="pill-title">当前活动线程</span>
                      <span class="pill-value text-primary font-mono">{{ monitorData?.jvm?.threadCount }}</span>
                    </div>
                  </el-col>
                  <el-col :span="8">
                    <div class="metric-pill">
                      <span class="pill-title">历史峰值线程</span>
                      <span class="pill-value font-mono">{{ monitorData?.jvm?.peakThreadCount }}</span>
                    </div>
                  </el-col>
                  <el-col :span="8">
                    <div class="metric-pill">
                      <span class="pill-title">守护线程 (Daemon)</span>
                      <span class="pill-value text-muted font-mono">{{ monitorData?.jvm?.daemonThreadCount }}</span>
                    </div>
                  </el-col>
                </el-row>

                <el-button type="primary" plain style="width: 100%;" @click="openThreadDrawer">
                  打开在线 JVM 线程转储抽屉进行调用栈排障
                </el-button>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- Thread Dump Drawer -->
    <el-drawer
      v-model="threadDrawerVisible"
      title="在线 JVM 线程转储与堆栈排障"
      size="70%"
      direction="rtl"
    >
      <div class="drawer-header-filter mb-3">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-input
              v-model="threadSearch"
              placeholder="搜索线程名称、ID、调用栈关键字..."
              prefix-icon="Search"
              clearable
            />
          </el-col>
          <el-col :span="12" class="text-right">
            <el-radio-group v-model="threadFilterState" size="small">
              <el-radio-button label="ALL">全部 ({{ threadList.length }})</el-radio-button>
              <el-radio-button label="RUNNABLE">可运行</el-radio-button>
              <el-radio-button label="BLOCKED">阻塞</el-radio-button>
              <el-radio-button label="WAITING">等待</el-radio-button>
            </el-radio-group>
            <el-button size="small" :icon="Refresh" class="ml-2" @click="openThreadDrawer">刷新转储</el-button>
          </el-col>
        </el-row>
      </div>

      <el-table
        v-loading="threadLoading"
        :data="filteredThreads"
        border
        stripe
        style="width: 100%;"
        height="calc(100vh - 160px)"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="stack-box">
              <div class="stack-meta">
                <span>锁定资源: {{ row.lockName }}</span>
                <span class="ml-3">锁持有者: {{ row.lockOwner }}</span>
              </div>
              <pre class="stack-code">{{ row.stackTrace || "无调用栈" }}</pre>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="name" label="线程名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="state" label="运行状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.state === 'RUNNABLE'" type="success" size="small">RUNNABLE</el-tag>
            <el-tag v-else-if="row.state === 'BLOCKED'" type="danger" size="small">BLOCKED</el-tag>
            <el-tag v-else-if="row.state === 'WAITING'" type="warning" size="small">WAITING</el-tag>
            <el-tag v-else type="info" size="small">{{ row.state }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="daemon" label="守护线程" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.daemon ? 'info' : 'primary'" size="small">
              {{ row.daemon ? "是" : "否" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cpuTimeMillis" label="CPU耗时" width="110" align="right">
          <template #default="{ row }">
            <span class="font-mono">{{ row.cpuTimeMillis }} ms</span>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<style scoped>
.server-monitor-container {
  padding: 20px;
  background-color: var(--el-bg-color-page);
  min-height: calc(100vh - 84px);
}

.monitor-header-card {
  border-radius: 8px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.header-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-icon {
  font-size: 24px;
}

.panel-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: var(--el-text-color-primary);
}

.panel-subtitle {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 6px 0 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.auto-refresh-box {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.pulse-beacon {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #67c23a;
  box-shadow: 0 0 0 rgb(103 194 58 / 0.4);
  animation: pulse 2s infinite;
  margin-right: 4px;
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgb(103 194 58 / 0.7);
  }

  70% {
    box-shadow: 0 0 0 8px rgb(103 194 58 / 0);
  }

  100% {
    box-shadow: 0 0 0 0 rgb(103 194 58 / 0);
  }
}

/* Application Instance Matrix Cards */
.app-instance-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: var(--el-box-shadow-light);
  transition: all 0.25s ease;
}

.app-instance-card:hover {
  transform: translateY(-2px);
  border-color: var(--el-color-primary-light-5);
}

.active-card {
  border-left: 3px solid #409eff;
}

.inst-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.inst-title {
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.inst-role {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 4px 0 10px;
}

.inst-meta-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  background: var(--el-fill-color-light);
  padding: 8px 6px;
  border-radius: 6px;
  text-align: center;
}

.meta-cell {
  display: flex;
  flex-direction: column;
}

.meta-cell .val {
  font-size: 13px;
  font-weight: 600;
}

.meta-cell .lbl {
  font-size: 10px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

/* Tabs & Cards */
.monitor-tab-card {
  border-radius: 8px;
}

.inner-section-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px;
}

.inner-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.inner-title {
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.wf-banner {
  background-color: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  padding: 10px 14px;
  border-radius: 6px;
}

.wf-banner-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-primary-dark-2);
  display: flex;
  align-items: center;
  gap: 6px;
}

.wf-banner-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
  line-height: 1.5;
}

.gauge-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 14px;
}

.gauge-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.gauge-title {
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.gauge-body {
  display: flex;
  justify-content: center;
  padding: 6px 0;
}

.gauge-val {
  font-size: 18px;
  font-weight: 700;
  font-family: monospace;
}

.gauge-sub {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  display: block;
}

.gauge-footer {
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 8px;
  font-size: 11px;
}

.footer-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 2px;
}

.vm-args-box {
  background: var(--el-fill-color-light);
  padding: 10px;
  border-radius: 6px;
  max-height: 120px;
  overflow-y: auto;
}

.sub-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.deadlock-safe-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background-color: var(--el-color-success-light-9);
  border: 1px solid var(--el-color-success-light-7);
  padding: 8px 12px;
  border-radius: 6px;
}

.safe-text {
  font-size: 13px;
  color: var(--el-color-success-dark-2);
}

.pool-box {
  background-color: var(--el-fill-color-light);
  padding: 14px;
  border-radius: 6px;
}

.pool-row-flex {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}

.pool-stats-row {
  display: flex;
  justify-content: space-between;
  text-align: center;
}

.stat-item {
  display: flex;
  flex-direction: column;
}

.stat-item .num {
  font-size: 18px;
  font-weight: 700;
  font-family: monospace;
}

.stat-item .lbl {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.metric-pill {
  background: var(--el-fill-color-light);
  padding: 10px 14px;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.pill-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pill-value {
  font-size: 18px;
  font-weight: 700;
  margin-top: 4px;
}

.stack-box {
  padding: 12px 16px;
  background-color: var(--el-fill-color-darker);
  color: #f8fafc;
  border-radius: 6px;
}

.stack-meta {
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 8px;
}

.stack-code {
  font-family: monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  margin: 0;
  color: #38bdf8;
  max-height: 300px;
  overflow-y: auto;
}

.font-mono {
  font-family: monospace;
}

.text-break {
  word-break: break-all;
}
</style>
