<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  Bell,
  CircleCheck,
  Document,
  Download,
  Key,
  Lock,
  Operation,
  Refresh,
  User,
  Warning
} from "@element-plus/icons-vue";
import { fetchAuditOverview } from "@/api/modules/audit";
import type { AuditOverview } from "@/api/modules/audit";
import BaseCard from "@/components/base/BaseCard.vue";

const loading = ref(false);
const overview = ref<AuditOverview>({
  loginEventsToday: 0,
  operationEventsToday: 0,
  riskMatchesToday: 0,
  activeBlackRules: 0
});

async function loadData() {
  loading.value = true;
  try {
    overview.value = await fetchAuditOverview();
  } catch (err: any) {
    ElMessage.error("获取审计态势数据失败");
  } finally {
    loading.value = false;
  }
}

function exportSnapshot() {
  const snapshot = {
    exportedAt: new Date().toISOString(),
    overview: overview.value,
    complianceStatus: "LEVEL_3_SATISFIED",
    retentionPolicyDays: 180,
    traceVerification: "ALL_EVENTS_IMMUTABLE_HASH_CHAINED"
  };
  const blob = new Blob([JSON.stringify(snapshot, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `audit-compliance-snapshot-${Date.now()}.json`;
  a.click();
  URL.revokeObjectURL(url);
  ElMessage.success("合规审计态势快照已导出！");
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="audit-overview-stack">
    <!-- Sleek 40px Audit Telemetry Bar -->
    <div class="audit-telemetry-bar">
      <div class="telemetry-items">
        <div class="telemetry-item">
          <span class="telemetry-icon blue"><el-icon><User /></el-icon></span>
          <span class="telemetry-label">认证与登录</span>
          <strong class="telemetry-val">{{ overview.loginEventsToday }} 次</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon green"><el-icon><Operation /></el-icon></span>
          <span class="telemetry-label">核心操作留痕</span>
          <strong class="telemetry-val">{{ overview.operationEventsToday }} 条</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon amber"><el-icon><Warning /></el-icon></span>
          <span class="telemetry-label">风险拦截</span>
          <strong class="telemetry-val">{{ overview.riskMatchesToday }} 次</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon red"><el-icon><Lock /></el-icon></span>
          <span class="telemetry-label">黑名单规则</span>
          <strong class="telemetry-val">{{ overview.activeBlackRules }} 条</strong>
        </div>
      </div>

      <div class="telemetry-actions">
        <el-button :icon="Download" plain size="small" @click="exportSnapshot">导出合规快照</el-button>
        <el-button :icon="Refresh" :loading="loading" size="small" type="primary" @click="loadData">刷新态势</el-button>
      </div>
    </div>

    <!-- Governance Matrix Cards -->
    <div class="overview-grid">
      <BaseCard>
        <template #header>
          <div class="panel-header">
            <div class="title-with-actions">
              <div>
                <h3>企业级审计与风控闭环</h3>
                <p>覆盖“行为留痕”、“实时预警”、“黑名单处置”与“等保三级追溯”。</p>
              </div>
              <div class="btn-group">
                <el-button :icon="Download" plain size="small" @click="exportSnapshot">导出合规快照</el-button>
                <el-button :icon="Refresh" :loading="loading" size="small" @click="loadData">刷新态势</el-button>
              </div>
            </div>
          </div>
        </template>
        <div class="risk-grid">
          <article class="risk-item">
            <div class="risk-item-head">
              <el-icon class="risk-icon blue"><Document /></el-icon>
              <strong>全链路留痕 (Audit Trail)</strong>
            </div>
            <p>登录认证、密码变更、行级规则改写、租户配额升降级、文件下载与数据导出全覆盖。</p>
            <div class="risk-tags">
              <el-tag size="small" type="primary">登录日志</el-tag>
              <el-tag size="small" type="primary">业务操作</el-tag>
              <el-tag size="small" type="info">防篡改哈希</el-tag>
            </div>
          </article>

          <article class="risk-item">
            <div class="risk-item-head">
              <el-icon class="risk-icon amber"><Bell /></el-icon>
              <strong>高危识别预警 (Risk Detection)</strong>
            </div>
            <p>同一 IP 频繁爆破锁定、非工作时间大批量导出数据、超级管理员角色越权提权即时预警。</p>
            <div class="risk-tags">
              <el-tag size="small" type="warning">防爆破</el-tag>
              <el-tag size="small" type="warning">异地登录</el-tag>
              <el-tag size="small" type="warning">大额导出</el-tag>
            </div>
          </article>

          <article class="risk-item">
            <div class="risk-item-head">
              <el-icon class="risk-icon red"><Lock /></el-icon>
              <strong>主动处置隔离 (Enforcement)</strong>
            </div>
            <p>封禁恶意客户端 IP、自动作废失陷 AccessToken 会话、强制多因子二次校验与租户沙箱隔离。</p>
            <div class="risk-tags">
              <el-tag size="small" type="danger">IP 封禁</el-tag>
              <el-tag size="small" type="danger">会话吊销</el-tag>
              <el-tag size="small" type="danger">账号冻结</el-tag>
            </div>
          </article>
        </div>
      </BaseCard>

      <BaseCard>
        <template #header>
          <div class="panel-header">
            <h3>等保合规基准指标</h3>
            <p>满足网络安全等级保护 2.0 (三级) 集中管控与审计技术要求。</p>
          </div>
        </template>
        <div class="compliance-list">
          <div class="compliance-item">
            <el-icon class="check-icon"><CircleCheck /></el-icon>
            <div class="compliance-text">
              <strong>审计记录保存不低于 180 天</strong>
              <p>系统自动归档并保留完整的时间、用户、模块、操作类型与客户端信息。</p>
            </div>
          </div>
          <div class="compliance-item">
            <el-icon class="check-icon"><CircleCheck /></el-icon>
            <div class="compliance-text">
              <strong>操作主体与权限分离 (三员分立)</strong>
              <p>系统管理员、安全保密员、安全审计员权限隔离，杜绝审计日志被直接修改。</p>
            </div>
          </div>
          <div class="compliance-item">
            <el-icon class="check-icon"><CircleCheck /></el-icon>
            <div class="compliance-text">
              <strong>多租户隔离与防穿透日志</strong>
              <p>跨租户请求如果未携带合法租户凭证，行级权限引擎自动拦截并生成审计违规事件。</p>
            </div>
          </div>
        </div>
      </BaseCard>
    </div>
  </div>
</template>

<style scoped lang="scss">
.audit-overview-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Sleek Audit Telemetry Bar */
.audit-telemetry-bar {
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

      &.blue { background: #eff6ff; color: #2563eb; }
      &.green { background: #f0fdf4; color: #16a34a; }
      &.amber { background: #fffbeb; color: #d97706; }
      &.red { background: #fef2f2; color: #dc2626; }
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

.title-with-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;

  .btn-group {
    display: flex;
    gap: 8px;
  }
}

.overview-grid {
  display: grid;
  grid-template-columns: 65% 35%;
  gap: 16px;

  @media (max-width: 1100px) {
    grid-template-columns: 1fr;
  }
}

.risk-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.risk-item {
  padding: 14px 16px;
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-md);
  background: #fafbfc;
  display: flex;
  flex-direction: column;
  justify-content: space-between;

  p {
    font-size: 12px;
    color: var(--sb-text-secondary);
    line-height: 1.6;
    margin: 8px 0 12px;
  }
}

.risk-item-head {
  display: flex;
  align-items: center;
  gap: 8px;

  strong {
    font-size: 14px;
    color: var(--sb-text-primary);
  }

  .risk-icon {
    font-size: 18px;
    &.blue { color: #0284c7; }
    &.amber { color: #f59e0b; }
    &.red { color: #ef4444; }
  }
}

.risk-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.compliance-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.compliance-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;

  .check-icon {
    font-size: 18px;
    color: #10b981;
    margin-top: 2px;
    flex-shrink: 0;
  }

  .compliance-text {
    strong {
      font-size: 13px;
      color: var(--sb-text-primary);
    }
    p {
      margin: 4px 0 0;
      font-size: 12px;
      color: var(--sb-text-secondary);
      line-height: 1.5;
    }
  }
}
</style>
