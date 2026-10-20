<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Monitor,
  Refresh,
  Delete,
  Search,
  Timer,
  WarningFilled,
  Cpu,
  DataAnalysis,
  CircleCheckFilled,
  DocumentCopy,
  Setting,
  Clock
} from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";
import {
  fetchRedisInfo,
  fetchRedisKeys,
  deleteRedisKey,
  reloadRedisCache,
  fetchSlowSqlReport,
  updateSlowSqlThreshold,
  clearSlowSql,
  fetchJvmInfo
} from "@/api/modules/system";
import type {
  RedisInfoData,
  RedisKeyItem,
  SlowSqlRecord,
  SlowSqlReportData,
  JvmInfoData
} from "@/api/modules/system";

const activeSubTab = ref<"redis" | "slowSql" | "jvm">("redis");
const loading = ref(false);
const autoRefreshSeconds = ref(0);
let timerId: any = null;

// Redis state
const redisInfo = ref<RedisInfoData | null>(null);
const redisKeys = ref<RedisKeyItem[]>([]);
const redisKeyPattern = ref("*");
const redisSearching = ref(false);

// Slow SQL state
const slowSqlReport = ref<SlowSqlReportData | null>(null);
const thresholdInput = ref(500);
const updatingThreshold = ref(false);
const selectedSqlRecord = ref<SlowSqlRecord | null>(null);
const sqlDrawerVisible = ref(false);

// JVM state
const jvmInfo = ref<JvmInfoData | null>(null);

async function loadAllData() {
  loading.value = true;
  try {
    if (activeSubTab.value === "redis") {
      await Promise.all([loadRedisInfo(), loadRedisKeys()]);
    } else if (activeSubTab.value === "slowSql") {
      await loadSlowSqlReport();
    } else if (activeSubTab.value === "jvm") {
      await loadJvmInfo();
    }
  } finally {
    loading.value = false;
  }
}

async function loadRedisInfo() {
  try {
    redisInfo.value = await fetchRedisInfo();
  } catch (err: any) {
    ElMessage.error(err.message || "获取 Redis 监控信息失败");
  }
}

async function loadRedisKeys() {
  redisSearching.value = true;
  try {
    redisKeys.value = await fetchRedisKeys(redisKeyPattern.value || "*");
  } catch (err: any) {
    ElMessage.error(err.message || "查询 Redis 键列表失败");
  } finally {
    redisSearching.value = false;
  }
}

async function handleDeleteKey(key: string) {
  try {
    await ElMessageBox.confirm(`确定要从缓存中强制驱逐/删除键【${key}】吗？`, "确认删除缓存键", {
      confirmButtonText: "确定删除",
      cancelButtonText: "取消",
      type: "warning"
    });
    await deleteRedisKey(key);
    ElMessage.success(`缓存键【${key}】已删除`);
    await loadRedisKeys();
    await loadRedisInfo();
  } catch (err: any) {
    if (err !== "cancel") {
      ElMessage.error(err.message || "删除缓存键失败");
    }
  }
}

const reloadingCache = ref(false);

async function handleReloadCache() {
  try {
    await ElMessageBox.confirm(
      "该操作将立即清空 Redis 中缓存的组织树、菜单路由与数据权限规约，并强制下一次业务访问直穿数据库完成最新数据重载。\n\n适用于：DBA 直接在数据库通过 SQL 增加了菜单或部门后需要立即同步生效的场景。\n\n确定执行吗？",
      "重新加载系统 Redis 缓存",
      {
        confirmButtonText: "立即重载",
        cancelButtonText: "取消",
        type: "warning"
      }
    );
    reloadingCache.value = true;
    const res = await reloadRedisCache();
    ElMessage.success(res?.message || `系统缓存已成功重新加载！共失效同步 ${res?.clearedKeys ?? 0} 个缓存键`);
    await loadRedisKeys();
    await loadRedisInfo();
  } catch (err: any) {
    if (err !== "cancel") {
      ElMessage.error(err.message || "重载系统缓存失败");
    }
  } finally {
    reloadingCache.value = false;
  }
}

async function loadSlowSqlReport() {
  try {
    const report = await fetchSlowSqlReport();
    slowSqlReport.value = report;
    if (report && !updatingThreshold.value) {
      thresholdInput.value = report.thresholdMillis;
    }
  } catch (err: any) {
    ElMessage.error(err.message || "获取慢 SQL 诊断报告失败");
  }
}

async function handleUpdateThreshold() {
  if (thresholdInput.value < 10) {
    ElMessage.warning("慢 SQL 阈值不能低于 10ms");
    return;
  }
  updatingThreshold.value = true;
  try {
    const nextVal = await updateSlowSqlThreshold(thresholdInput.value);
    ElMessage.success(`慢 SQL 拦截阈值已生效：${nextVal} ms`);
    await loadSlowSqlReport();
  } catch (err: any) {
    ElMessage.error(err.message || "更新慢 SQL 阈值失败");
  } finally {
    updatingThreshold.value = false;
  }
}

async function handleClearSlowSql() {
  try {
    await ElMessageBox.confirm("确定要清空内存中所有慢 SQL 诊断采样记录吗？", "清空诊断记录", {
      confirmButtonText: "确定清空",
      cancelButtonText: "取消",
      type: "warning"
    });
    await clearSlowSql();
    ElMessage.success("慢 SQL 采样记录已清空");
    await loadSlowSqlReport();
  } catch (err: any) {
    if (err !== "cancel") {
      ElMessage.error(err.message || "清空慢 SQL 失败");
    }
  }
}

function openSqlDrawer(row: SlowSqlRecord) {
  selectedSqlRecord.value = row;
  sqlDrawerVisible.value = true;
}

function copySqlText(sql: string) {
  navigator.clipboard.writeText(sql);
  ElMessage.success("SQL 语句已复制到剪贴板");
}

async function loadJvmInfo() {
  try {
    jvmInfo.value = await fetchJvmInfo();
  } catch (err: any) {
    ElMessage.error(err.message || "获取 JVM 运行时指标失败");
  }
}

function formatBytes(bytes: number) {
  if (!bytes || bytes <= 0) return "0 MB";
  const mb = bytes / (1024 * 1024);
  if (mb < 1024) return `${mb.toFixed(1)} MB`;
  return `${(mb / 1024).toFixed(2)} GB`;
}

function formatUptime(seconds: number) {
  if (!seconds) return "0秒";
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;
  let str = "";
  if (days > 0) str += `${days}天 `;
  if (hours > 0 || days > 0) str += `${hours}小时 `;
  if (minutes > 0 || hours > 0) str += `${minutes}分 `;
  str += `${secs}秒`;
  return str;
}

function switchTab(tab: "redis" | "slowSql" | "jvm") {
  activeSubTab.value = tab;
  loadAllData();
}

function handleAutoRefreshChange(val: number) {
  if (timerId) {
    clearInterval(timerId);
    timerId = null;
  }
  if (val > 0) {
    timerId = setInterval(() => {
      loadAllData();
    }, val * 1000);
    ElMessage.info(`已开启自动刷新：每 ${val} 秒更新一次`);
  } else {
    ElMessage.info("已关闭自动刷新");
  }
}

onMounted(() => {
  loadAllData();
});

onUnmounted(() => {
  if (timerId) {
    clearInterval(timerId);
  }
});
</script>

<template>
  <div class="infra-dashboard">
    <!-- 顶栏导航与刷新控制 -->
    <div class="infra-toolbar">
      <div class="infra-subtabs">
        <button
          type="button"
          class="infra-subtab-btn"
          :class="{ active: activeSubTab === 'redis' }"
          @click="switchTab('redis')"
        >
          <el-icon><DataAnalysis /></el-icon>
          <span>Redis 缓存大屏</span>
        </button>
        <button
          type="button"
          class="infra-subtab-btn"
          :class="{ active: activeSubTab === 'slowSql' }"
          @click="switchTab('slowSql')"
        >
          <el-icon><Timer /></el-icon>
          <span>慢 SQL 智能诊断</span>
          <span v-if="slowSqlReport?.totalSlowQueries" class="badge-count">{{ slowSqlReport.totalSlowQueries }}</span>
        </button>
        <button
          type="button"
          class="infra-subtab-btn"
          :class="{ active: activeSubTab === 'jvm' }"
          @click="switchTab('jvm')"
        >
          <el-icon><Cpu /></el-icon>
          <span>JVM 运行时健康</span>
        </button>
      </div>

      <div class="infra-actions">
        <span class="refresh-label">轮询刷新:</span>
        <el-select
          v-model="autoRefreshSeconds"
          size="small"
          style="width: 110px"
          @change="handleAutoRefreshChange"
        >
          <el-option :value="0" label="手动刷新" />
          <el-option :value="5" label="每 5 秒" />
          <el-option :value="15" label="每 15 秒" />
          <el-option :value="30" label="每 30 秒" />
        </el-select>
        <el-button
          type="primary"
          size="small"
          plain
          :icon="Refresh"
          :loading="loading"
          @click="loadAllData"
        >
          立即刷新
        </el-button>
      </div>
    </div>

    <!-- TAB 1: Redis 缓存监控 -->
    <div v-if="activeSubTab === 'redis'" class="infra-content-stack">
      <!-- Redis 关键指标遥测栏 -->
      <div class="infra-telemetry-strip">
        <div class="telemetry-items">
          <div class="telemetry-item">
            <span class="telemetry-icon blue"><el-icon><DataAnalysis /></el-icon></span>
            <span class="telemetry-label">服务版本</span>
            <strong class="telemetry-val">v{{ redisInfo?.redisVersion || "-" }} ({{ redisInfo?.mode || "standalone" }})</strong>
          </div>
          <div class="telemetry-divider" />
          <div class="telemetry-item">
            <span class="telemetry-icon green"><el-icon><Clock /></el-icon></span>
            <span class="telemetry-label">运行健康时间</span>
            <strong class="telemetry-val">{{ formatUptime(redisInfo?.uptimeInSeconds || 0) }} (客户端: {{ redisInfo?.connectedClients || 0 }})</strong>
          </div>
          <div class="telemetry-divider" />
          <div class="telemetry-item">
            <span class="telemetry-icon purple"><el-icon><Cpu /></el-icon></span>
            <span class="telemetry-label">内存分配</span>
            <strong class="telemetry-val">{{ redisInfo?.usedMemoryHuman || "0B" }} (峰值 {{ redisInfo?.usedMemoryPeakHuman || "0B" }})</strong>
          </div>
          <div class="telemetry-divider" />
          <div class="telemetry-item">
            <span class="telemetry-icon orange"><el-icon><CircleCheckFilled /></el-icon></span>
            <span class="telemetry-label">缓存命中率</span>
            <strong class="telemetry-val">{{ (redisInfo?.hitRate ?? 100).toFixed(1) }}% ({{ redisInfo?.instantaneousOpsPerSec || 0 }} ops)</strong>
          </div>
        </div>
      </div>

      <!-- Redis 键空间管理 -->
      <BaseCard title="缓存键空间实时检索 (Key Explorer)" class="mt-4">
        <template #extra>
          <div class="redis-search-bar">
            <el-input
              v-model="redisKeyPattern"
              placeholder="键匹配通配符，如 *、saas:*、dict:*"
              clearable
              size="small"
              style="width: 320px"
              @keyup.enter="loadRedisKeys"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button
              type="primary"
              size="small"
              :loading="redisSearching"
              @click="loadRedisKeys"
            >
              检索键
            </el-button>
            <el-button
              type="warning"
              size="small"
              plain
              :icon="Refresh"
              :loading="reloadingCache"
              @click="handleReloadCache"
            >
              强制重载系统缓存 (Sync DB)
            </el-button>
          </div>
        </template>

        <el-table
          :data="redisKeys"
          v-loading="redisSearching"
          stripe
          border
          style="width: 100%"
          max-height="520"
        >
          <el-table-column type="index" label="#" width="60" align="center" />
          <el-table-column prop="key" label="键名 (Cache Key)" min-width="320">
            <template #default="{ row }">
              <span class="code-font highlight-key">{{ row.key }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="type" label="数据结构" width="120" align="center">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" type="info">{{ row.type?.toUpperCase() || "STRING" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="ttlSeconds" label="生存时间 (TTL)" width="160" align="center">
            <template #default="{ row }">
              <span v-if="row.ttlSeconds === -1" class="text-secondary">永久有效 (无过期)</span>
              <span v-else-if="row.ttlSeconds === -2" class="text-danger">已过期</span>
              <span v-else class="text-primary font-medium">{{ row.ttlSeconds }} 秒</span>
            </template>
          </el-table-column>
          <el-table-column prop="size" label="内存占用评估" width="130" align="center">
            <template #default="{ row }">
              <span>{{ row.size || "-" }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                type="danger"
                link
                size="small"
                :icon="Delete"
                @click="handleDeleteKey(row.key)"
              >
                删除键
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </BaseCard>
    </div>

    <!-- TAB 2: 慢 SQL 智能诊断 -->
    <div v-if="activeSubTab === 'slowSql'" class="infra-content-stack">
      <!-- 慢 SQL 统计与阈值配置卡片 -->
      <BaseCard>
        <div class="slow-sql-config-bar">
          <div class="config-stat-group">
            <div class="stat-pill">
              <span class="label">慢 SQL 捕获总量:</span>
              <span class="value text-warning">{{ slowSqlReport?.totalSlowQueries || 0 }} 次</span>
            </div>
            <div class="stat-pill">
              <span class="label">拦截阈值:</span>
              <span class="value text-primary">{{ slowSqlReport?.thresholdMillis || 500 }} ms</span>
            </div>
          </div>

          <div class="config-action-group">
            <span class="label-inline">动态调节拦截阈值:</span>
            <el-input-number
              v-model="thresholdInput"
              :min="10"
              :max="10000"
              :step="50"
              size="small"
              style="width: 140px"
            />
            <span class="unit-text">毫秒</span>
            <el-button
              type="primary"
              size="small"
              :loading="updatingThreshold"
              @click="handleUpdateThreshold"
            >
              更新阈值
            </el-button>
            <el-button
              type="danger"
              plain
              size="small"
              :icon="Delete"
              @click="handleClearSlowSql"
            >
              清空诊断记录
            </el-button>
          </div>
        </div>
      </BaseCard>

      <!-- TOP 耗时最长 SQL 诊断 -->
      <BaseCard title="🔥 耗时最长 TOP 慢 SQL (按耗时降序)" class="mt-4">
        <el-table
          :data="slowSqlReport?.topSlowestQueries || []"
          stripe
          border
          style="width: 100%"
        >
          <el-table-column type="index" label="#" width="60" align="center" />
          <el-table-column prop="costMillis" label="执行耗时" width="130" align="center">
            <template #default="{ row }">
              <el-tag :type="row.costMillis > 1000 ? 'danger' : 'warning'" effect="dark">
                {{ row.costMillis }} ms
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sql" label="SQL 执行语句" min-width="400">
            <template #default="{ row }">
              <div class="sql-preview-text code-font">{{ row.sql }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="executedAt" label="执行发生时间" width="180" align="center" />
          <el-table-column prop="threadName" label="执行线程" width="160" />
          <el-table-column label="详情" width="100" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="openSqlDrawer(row)">
                查看诊断
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </BaseCard>

      <!-- 最近捕获的慢 SQL -->
      <BaseCard title="⏱️ 最近捕获的慢 SQL (时间倒序)" class="mt-4">
        <el-table
          :data="slowSqlReport?.recentSlowQueries || []"
          stripe
          border
          style="width: 100%"
        >
          <el-table-column type="index" label="#" width="60" align="center" />
          <el-table-column prop="costMillis" label="执行耗时" width="130" align="center">
            <template #default="{ row }">
              <el-tag :type="row.costMillis > 1000 ? 'danger' : 'warning'" effect="plain">
                {{ row.costMillis }} ms
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sql" label="SQL 执行语句" min-width="400">
            <template #default="{ row }">
              <div class="sql-preview-text code-font">{{ row.sql }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="executedAt" label="执行发生时间" width="180" align="center" />
          <el-table-column prop="threadName" label="执行线程" width="160" />
          <el-table-column label="详情" width="100" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="openSqlDrawer(row)">
                查看诊断
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </BaseCard>
    </div>

    <!-- TAB 3: JVM 运行时监控 -->
    <div v-if="activeSubTab === 'jvm'" class="infra-content-stack">
      <!-- JVM 运行时遥测栏 -->
      <div class="infra-telemetry-strip">
        <div class="telemetry-items">
          <div class="telemetry-item">
            <span class="telemetry-icon blue"><el-icon><Cpu /></el-icon></span>
            <span class="telemetry-label">JVM 架构</span>
            <strong class="telemetry-val">Java {{ jvmInfo?.javaVersion || "17" }} ({{ jvmInfo?.javaVendor || "Adoptium" }})</strong>
          </div>
          <div class="telemetry-divider" />
          <div class="telemetry-item">
            <span class="telemetry-icon green"><el-icon><Clock /></el-icon></span>
            <span class="telemetry-label">运行时间</span>
            <strong class="telemetry-val">{{ formatUptime(jvmInfo?.uptimeSeconds || 0) }} ({{ jvmInfo?.availableProcessors || 8 }} 核)</strong>
          </div>
          <div class="telemetry-divider" />
          <div class="telemetry-item">
            <span class="telemetry-icon purple"><el-icon><Monitor /></el-icon></span>
            <span class="telemetry-label">堆内存使用率</span>
            <strong class="telemetry-val">{{ (jvmInfo?.heapUsedPercent ?? 0).toFixed(1) }}% ({{ formatBytes(jvmInfo?.heapUsedBytes || 0) }} / {{ formatBytes(jvmInfo?.heapMaxBytes || 0) }})</strong>
          </div>
          <div class="telemetry-divider" />
          <div class="telemetry-item">
            <span class="telemetry-icon orange"><el-icon><Setting /></el-icon></span>
            <span class="telemetry-label">活跃线程</span>
            <strong class="telemetry-val">{{ jvmInfo?.activeThreads || 0 }} 个 (峰值 {{ jvmInfo?.peakThreads || 0 }})</strong>
          </div>
        </div>
      </div>

      <!-- 内存使用进度图 -->
      <BaseCard title="JVM 堆与非堆内存分布" class="mt-4">
        <div class="memory-progress-section">
          <div class="memory-bar-item">
            <div class="memory-bar-header">
              <span class="memory-name">堆内存 (Heap Memory)</span>
              <span class="memory-detail">
                已用 {{ formatBytes(jvmInfo?.heapUsedBytes || 0) }} /
                已提交 {{ formatBytes(jvmInfo?.heapCommittedBytes || 0) }} /
                最大 {{ formatBytes(jvmInfo?.heapMaxBytes || 0) }}
              </span>
            </div>
            <el-progress
              :percentage="Math.min(100, Math.round(jvmInfo?.heapUsedPercent || 0))"
              :status="((jvmInfo?.heapUsedPercent || 0) > 85) ? 'exception' : 'warning'"
              :stroke-width="18"
              striped
              striped-flow
            />
          </div>

          <div class="memory-bar-item mt-4">
            <div class="memory-bar-header">
              <span class="memory-name">非堆内存 (Metaspace & CodeCache)</span>
              <span class="memory-detail">已用 {{ formatBytes(jvmInfo?.nonHeapUsedBytes || 0) }}</span>
            </div>
            <el-progress
              :percentage="45"
              color="#8b5cf6"
              :stroke-width="18"
            />
          </div>
        </div>
      </BaseCard>

      <!-- GC 收集器统计 -->
      <BaseCard title="垃圾回收器 (Garbage Collector) 运行台账" class="mt-4">
        <el-table
          :data="jvmInfo?.gcMetrics || []"
          stripe
          border
          style="width: 100%"
        >
          <el-table-column type="index" label="#" width="60" align="center" />
          <el-table-column prop="name" label="收集器名称" min-width="220">
            <template #default="{ row }">
              <span class="code-font font-medium">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="count" label="累计回收次数 (GC Count)" width="220" align="center">
            <template #default="{ row }">
              <el-tag type="info" effect="plain">{{ row.count }} 次</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="timeMillis" label="累计停顿耗时 (GC Pause Time)" width="220" align="center">
            <template #default="{ row }">
              <span class="text-primary font-medium">{{ row.timeMillis }} ms</span>
            </template>
          </el-table-column>
          <el-table-column label="平均单次耗时" min-width="180" align="center">
            <template #default="{ row }">
              <span>{{ row.count > 0 ? (row.timeMillis / row.count).toFixed(2) : 0 }} ms / 次</span>
            </template>
          </el-table-column>
        </el-table>
      </BaseCard>
    </div>

    <!-- SQL 诊断详情抽屉 -->
    <el-drawer
      v-model="sqlDrawerVisible"
      title="慢 SQL 深度采样诊断"
      size="650px"
      direction="rtl"
    >
      <div v-if="selectedSqlRecord" class="sql-drawer-content">
        <div class="drawer-header-banner">
          <div class="banner-metric">
            <span class="label">执行耗时</span>
            <span class="value text-danger">{{ selectedSqlRecord.costMillis }} ms</span>
          </div>
          <div class="banner-metric">
            <span class="label">捕获时间</span>
            <span class="value">{{ selectedSqlRecord.executedAt }}</span>
          </div>
          <div class="banner-metric">
            <span class="label">执行线程</span>
            <span class="value">{{ selectedSqlRecord.threadName }}</span>
          </div>
        </div>

        <div class="drawer-section mt-4">
          <div class="section-title-bar">
            <strong>完整 SQL 语句</strong>
            <el-button
              type="primary"
              link
              size="small"
              :icon="DocumentCopy"
              @click="copySqlText(selectedSqlRecord.sql)"
            >
              复制语句
            </el-button>
          </div>
          <pre class="sql-code-box"><code>{{ selectedSqlRecord.sql }}</code></pre>
        </div>

        <div v-if="selectedSqlRecord.stackTrace" class="drawer-section mt-4">
          <div class="section-title-bar">
            <strong>执行调用栈 (Call Stack)</strong>
          </div>
          <pre class="stack-trace-box"><code>{{ selectedSqlRecord.stackTrace }}</code></pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.infra-dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.infra-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--el-bg-color);
  padding: 12px 16px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 1px 3px rgb(0 0 0 / 0.05);
}

.infra-subtabs {
  display: flex;
  gap: 8px;
}

.infra-subtab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 6px;
  border: 1px solid transparent;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.infra-subtab-btn:hover {
  background: var(--el-fill-color);
  color: var(--el-color-primary);
}

.infra-subtab-btn.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
  font-weight: 600;
}

.badge-count {
  background: var(--el-color-warning);
  color: #fff;
  border-radius: 10px;
  padding: 1px 6px;
  font-size: 11px;
  font-weight: 700;
}

.infra-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.refresh-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

/* Sleek Infra Telemetry Strip */
.infra-telemetry-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--sb-border-color, #e2e8f0);
  border-radius: 8px;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;

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

      &.blue { background: #eff6ff; color: #2563eb; }
      &.green { background: #f0fdf4; color: #16a34a; }
      &.purple { background: #faf5ff; color: #9333ea; }
      &.orange { background: #fffbeb; color: #d97706; }
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
}

.redis-search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.code-font {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.highlight-key {
  color: var(--el-color-primary);
  font-weight: 600;
}

.slow-sql-config-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.config-stat-group {
  display: flex;
  gap: 16px;
}

.stat-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--el-fill-color-light);
  padding: 6px 14px;
  border-radius: 6px;
}

.stat-pill .label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stat-pill .value {
  font-size: 15px;
  font-weight: 700;
}

.config-action-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.label-inline {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.unit-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.sql-preview-text {
  max-height: 48px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  font-size: 12px;
  background: var(--el-fill-color-lighter);
  padding: 4px 8px;
  border-radius: 4px;
  color: var(--el-text-color-primary);
}

.memory-progress-section {
  padding: 12px 0;
}

.memory-bar-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.memory-bar-header {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}

.memory-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.memory-detail {
  color: var(--el-text-color-secondary);
}

.sql-drawer-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drawer-header-banner {
  display: flex;
  justify-content: space-around;
  background: var(--el-fill-color-light);
  padding: 16px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
}

.banner-metric {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.banner-metric .label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.banner-metric .value {
  font-size: 16px;
  font-weight: 700;
}

.section-title-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.sql-code-box,
.stack-trace-box {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: 14px;
  border-radius: 8px;
  overflow-x: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
}

.mt-4 {
  margin-top: 16px;
}

.text-danger {
  color: var(--el-color-danger);
}

.text-warning {
  color: var(--el-color-warning);
}

.text-primary {
  color: var(--el-color-primary);
}

.text-secondary {
  color: var(--el-text-color-secondary);
}

.font-medium {
  font-weight: 500;
}
</style>
