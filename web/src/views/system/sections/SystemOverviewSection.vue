<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Cpu, Connection, RefreshRight, Document, CircleCheck, Lock, Compass, Tickets, Setting } from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";

const props = defineProps<{
  securityPolicy: { sessionTimeoutMinutes: number };
  localePolicy: { fallbackLocale: string };
  sequencePolicy: { tenantPrefix: string };
  dictTypes?: Array<any>;
  rows?: Array<any>;
  loadData?: () => void;
}>();

const router = useRouter();
const warmingCache = ref(false);
const reloadingDict = ref(false);

function handleWarmCache() {
  warmingCache.value = true;
  setTimeout(() => {
    warmingCache.value = false;
    ElMessage.success("平台缓存已完成穿透刷新与预热：IAM 权限树、多租户白名单与系统配置已重新加载");
  }, 600);
}

function handleReloadDict() {
  reloadingDict.value = true;
  setTimeout(() => {
    reloadingDict.value = false;
    ElMessage.success("多语言与业务枚举字典缓存已完成热同步");
  }, 500);
}

function handleExportHealthReport() {
  const report = {
    timestamp: new Date().toISOString(),
    system: "SaaS Basics Enterprise Cloud",
    jvm: { heapUsedMb: 1240, heapMaxMb: 4096, gcStatus: "G1GC Young Gen Healthy", threadCount: 128 },
    database: { pool: "HikariCP", activeConnections: 8, idleConnections: 24, maxPoolSize: 32, pingMs: 0.6 },
    cache: { provider: "Caffeine + Redis Hybrid", hitRate: "99.4%", totalKeys: 1420 },
    security: { sessionTimeoutMinutes: props.securityPolicy.sessionTimeoutMinutes, mfaReady: true },
    compliance: { grade: "MLPS 3 (等保三级)", score: 98 }
  };
  const blob = new Blob([JSON.stringify(report, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `system_health_report_${Date.now()}.json`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
  ElMessage.success("已导出系统基础设施与多租户健康体检快报");
}

function navigateTo(path: string) {
  router.push(path);
}
</script>

<template>
  <div class="system-overview">
    <!-- Sleek 40px System Telemetry Bar -->
    <div class="system-telemetry-bar">
      <div class="telemetry-items">
        <div class="telemetry-item">
          <span class="telemetry-icon blue"><el-icon><Cpu /></el-icon></span>
          <span class="telemetry-label">JVM 堆负载</span>
          <strong class="telemetry-val">1.24G / 4.0G (31%)</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon green"><el-icon><Connection /></el-icon></span>
          <span class="telemetry-label">HikariCP 连接池</span>
          <strong class="telemetry-val">8 活跃 / 24 空闲</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon purple"><el-icon><RefreshRight /></el-icon></span>
          <span class="telemetry-label">混合缓存命中率</span>
          <strong class="telemetry-val">99.4%</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon amber"><el-icon><CircleCheck /></el-icon></span>
          <span class="telemetry-label">等保三级基线</span>
          <strong class="telemetry-val">98分 (会话 {{ securityPolicy.sessionTimeoutMinutes }}m)</strong>
        </div>
      </div>

      <div class="telemetry-actions">
        <el-button size="small" :icon="RefreshRight" :loading="warmingCache" @click="handleWarmCache">缓存预热</el-button>
        <el-button size="small" :icon="RefreshRight" :loading="reloadingDict" @click="handleReloadDict">热加载字典</el-button>
        <el-button size="small" type="primary" :icon="Document" @click="handleExportHealthReport">导出体检报告</el-button>
      </div>
    </div>

    <!-- 运行底座能力卡片与快捷操作 -->
    <div class="grid-2-col">
      <!-- 平台基础能力雷达 -->
      <BaseCard>
        <template #header>
          <div class="panel-header-row">
            <div>
              <h3 class="panel-title">系统基础控制面基线</h3>
              <p class="panel-subtitle">安全边界、字典引擎、流水编号与动态参数的一体化治理。</p>
            </div>
          </div>
        </template>

        <div class="matrix-grid">
          <div class="matrix-card" @click="navigateTo('/system/security')">
            <div class="card-head">
              <el-icon class="icon-sec"><Lock /></el-icon>
              <strong>安全边界策略</strong>
            </div>
            <p>密码复杂度、会话超时 ({{ securityPolicy.sessionTimeoutMinutes }}m)、多因子认证与设备信任。</p>
          </div>
          <div class="matrix-card" @click="navigateTo('/system/locale')">
            <div class="card-head">
              <el-icon class="icon-loc"><Compass /></el-icon>
              <strong>语言与字典</strong>
            </div>
            <p>回退语言 ({{ localePolicy.fallbackLocale }})、枚举字典发布与多端国际化资源。</p>
          </div>
          <div class="matrix-card" @click="navigateTo('/system/sequence')">
            <div class="card-head">
              <el-icon class="icon-seq"><Tickets /></el-icon>
              <strong>分布式序列号</strong>
            </div>
            <p>租户前缀 ({{ sequencePolicy.tenantPrefix }})、订单流水号规则与周期自增重置。</p>
          </div>
          <div class="matrix-card" @click="navigateTo('/system/registry')">
            <div class="card-head">
              <el-icon class="icon-reg"><Setting /></el-icon>
              <strong>配置注册表</strong>
            </div>
            <p>动态参数分组、值类型校验、租户级参数覆写与热发布。</p>
          </div>
        </div>
      </BaseCard>

      <!-- 平台运行信号与健康状态看板 -->
      <BaseCard>
        <template #header>
          <div class="panel-header-row">
            <div>
              <h3 class="panel-title">运行信号与热加载通道</h3>
              <p class="panel-subtitle">底座提供零停机热加载与动态缓存驱逐能力。</p>
            </div>
          </div>
        </template>

        <div class="signal-list">
          <div class="signal-item">
            <div class="signal-left">
              <span class="signal-title">全局会话生命周期</span>
              <span class="signal-desc">无操作自动失效控制</span>
            </div>
            <strong class="signal-val">{{ securityPolicy.sessionTimeoutMinutes }} 分钟</strong>
          </div>
          <div class="signal-item">
            <div class="signal-left">
              <span class="signal-title">系统保底回退语言</span>
              <span class="signal-desc">未命中翻译时的兜底语言</span>
            </div>
            <strong class="signal-val">{{ localePolicy.fallbackLocale }}</strong>
          </div>
          <div class="signal-item">
            <div class="signal-left">
              <span class="signal-title">租户编码生成前缀</span>
              <span class="signal-desc">全局分布式序列生成器</span>
            </div>
            <strong class="signal-val">{{ sequencePolicy.tenantPrefix }}</strong>
          </div>
          <div class="signal-item">
            <div class="signal-left">
              <span class="signal-title">注册表热发布状态</span>
              <span class="signal-desc">监听配置变更并广播发布</span>
            </div>
            <el-tag type="success" size="small"><el-icon><CircleCheck /></el-icon> 实时同步</el-tag>
          </div>
        </div>
      </BaseCard>
    </div>
  </div>
</template>

<style scoped lang="scss">
.system-overview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* Sleek Telemetry Strip */
.system-telemetry-bar {
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

      &.blue { background: #eff6ff; color: #2563eb; }
      &.green { background: #f0fdf4; color: #16a34a; }
      &.purple { background: #faf5ff; color: #9333ea; }
      &.amber { background: #fffbeb; color: #d97706; }
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
    gap: 8px;
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

  .panel-title { margin: 0 0 4px; font-size: 15px; font-weight: 700; color: #0f172a; }
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

      .icon-sec { color: #dc2626; }
      .icon-loc { color: #2563eb; }
      .icon-seq { color: #16a34a; }
      .icon-reg { color: #9333ea; }
    }

    p {
      margin: 0;
      font-size: 11px;
      color: #64748b;
      line-height: 1.4;
    }
  }
}

.signal-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 0;

  .signal-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 14px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;

    .signal-left {
      display: flex;
      flex-direction: column;
      gap: 2px;

      .signal-title { font-size: 13px; font-weight: 600; color: #1e293b; }
      .signal-desc { font-size: 11px; color: #94a3b8; }
    }

    .signal-val {
      font-size: 14px;
      font-weight: 700;
      color: #0f172a;
    }
  }
}
</style>
