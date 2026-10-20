<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import {
  Calendar,
  Check,
  CircleCheck,
  Cpu,
  DataAnalysis,
  Document,
  Download,
  Files,
  Monitor,
  OfficeBuilding,
  Odometer,
  Operation,
  Plus,
  Refresh,
  Search,
  Timer,
  User,
  View,
  Warning
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { createTenant } from "@/api/modules/tenant";
import type { TenantRow } from "@/api/modules/tenant";
import BaseCard from "@/components/base/BaseCard.vue";

const props = defineProps<{
  rows: TenantRow[];
  loading?: boolean;
  isolationModeLabelMap: Record<string, string>;
  runtimePolicy: {
    trialDays: number;
    defaultLocale: string;
    tenantApprovalMode: string;
    autoOpenConsole: boolean;
  };
  packagePolicy: {
    seatQuota: number;
    storageQuotaGb: number;
  };
  openCreateDialog: () => void;
  loadData: () => Promise<void> | void;
}>();

// Metrics calculations
const totalTenants = computed(() => props.rows.length);
const activeTenants = computed(() => props.rows.filter((t) => t.status === "ACTIVE" || t.status === "ENABLED").length);
const trialTenants = computed(() => props.rows.filter((t) => t.status === "TRIAL").length);
const dedicatedTenants = computed(() => props.rows.filter((t) => t.isolationMode === "DEDICATED_DATABASE").length);
const expiringTenants = computed(() => props.rows.filter((t) => (t.daysRemaining != null && t.daysRemaining <= 15) || t.status === "EXPIRING").length);

// Selected tenant for quick inspection
const inspectDrawerVisible = ref(false);
const inspectingTenant = ref<TenantRow | null>(null);

function openInspect(row: TenantRow) {
  inspectingTenant.value = row;
  inspectDrawerVisible.value = true;
}

// Onboarding Wizard Modal
const wizardVisible = ref(false);
const wizardStep = ref(0);
const wizardForm = reactive({
  tenantCode: "",
  tenantName: "",
  packageTier: "ENTERPRISE",
  isolationMode: "SHARED_SCHEMA",
  adminUsername: "admin",
  adminMobile: "13800000000"
});

function openWizard() {
  wizardStep.value = 0;
  wizardForm.tenantCode = "";
  wizardForm.tenantName = "";
  wizardForm.packageTier = "ENTERPRISE";
  wizardForm.isolationMode = "SHARED_SCHEMA";
  wizardForm.adminUsername = "admin";
  wizardForm.adminMobile = "13800000000";
  wizardVisible.value = true;
}

const wizardSubmitting = ref(false);

async function handleWizardNext() {
  if (wizardStep.value === 0 && (!wizardForm.tenantCode.trim() || !wizardForm.tenantName.trim())) {
    ElMessage.warning("请填写完整的租户编码与租户全称");
    return;
  }
  if (wizardStep.value < 3) {
    wizardStep.value++;
  } else {
    wizardSubmitting.value = true;
    try {
      await createTenant({
        tenantCode: wizardForm.tenantCode.trim(),
        tenantName: wizardForm.tenantName.trim(),
        packageId: wizardForm.packageTier === "SUPREME" ? 1 : 2,
        status: "ACTIVE",
        isolationMode: wizardForm.isolationMode,
        contactName: wizardForm.adminUsername,
        contactMobile: wizardForm.adminMobile,
        remark: `向导式交付开通 (所属行业: 综合制造与数字科技)`
      });
      wizardVisible.value = false;
      ElMessage.success(`租户「${wizardForm.tenantName}」向导式交付创建成功，租户沙箱与环境已就绪！`);
      await props.loadData();
    } catch (err: any) {
      ElMessage.error(err?.response?.data?.message || err?.message || "向导式交付创建租户失败");
    } finally {
      wizardSubmitting.value = false;
    }
  }
}

function exportTelemetry() {
  const data = {
    exportedAt: new Date().toISOString(),
    summary: {
      total: totalTenants.value,
      active: activeTenants.value,
      trial: trialTenants.value,
      dedicated: dedicatedTenants.value,
      expiring: expiringTenants.value
    },
    tenants: props.rows
  };
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `tenant-telemetry-radar-${Date.now()}.json`;
  a.click();
  URL.revokeObjectURL(url);
  ElMessage.success("租户治理态势巡检快报已导出！");
}
</script>

<template>
  <div class="tenant-overview-stack">
    <!-- Top Sleek Telemetry Bar (40px) -->
    <div class="tenant-telemetry-bar">
      <div class="telemetry-left">
        <div class="metric-item">
          <el-icon class="icon-blue"><Odometer /></el-icon>
          <span class="label">纳入底座</span>
          <span class="value">{{ totalTenants }} 家</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-emerald"><CircleCheck /></el-icon>
          <span class="label">正常运行</span>
          <span class="value">{{ activeTenants }} 家 (SLA 100%)</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-indigo"><Timer /></el-icon>
          <span class="label">体验试用</span>
          <span class="value">{{ trialTenants }} 家</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-purple"><Cpu /></el-icon>
          <span class="label">独库隔离</span>
          <span class="value">{{ dedicatedTenants }} 家</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-amber"><Warning /></el-icon>
          <span class="label">临期预警</span>
          <span class="value" :class="{ 'has-warning': expiringTenants > 0 }">{{ expiringTenants }} 家</span>
        </div>
      </div>
    </div>

    <!-- Main Overview Row: Left Tenant Telemetry Table, Right Blueprint -->
    <div class="overview-dual-grid">
      <!-- Tenant Health Radar Table -->
      <BaseCard>
        <template #header>
          <div class="panel-header-flex">
            <div>
              <h3>租户空间健康度与资源水位巡检矩阵</h3>
              <p>实时掌控租户运行健康度评分、套餐到期倒计时、隔离防护状态与席位负载。</p>
            </div>
            <div class="header-actions">
              <el-button size="small" :icon="Download" plain @click="exportTelemetry">导出巡检快报</el-button>
              <el-button size="small" type="primary" :icon="Plus" @click="openWizard">向导式开通</el-button>
            </div>
          </div>
        </template>

        <el-table :data="rows" stripe style="width: 100%">
          <el-table-column prop="tenantCode" label="租户编码" width="140">
            <template #default="{ row }">
              <span class="mono-code">{{ row.tenantCode }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="tenantName" label="租户全称" min-width="160" />
          <el-table-column prop="packageName" label="订阅套餐" width="120">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" type="primary">{{ row.packageName || '标准套餐' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="isolationMode" label="隔离模式" width="140">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row.isolationMode === 'DEDICATED_DATABASE' ? 'danger' : row.isolationMode === 'ISOLATED_SCHEMA' ? 'warning' : 'info'"
              >
                {{ isolationModeLabelMap[row.isolationMode] || row.isolationMode || '共享模式' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="履约倒计时" width="150">
            <template #default="{ row }">
              <span v-if="row.daysRemaining != null && row.daysRemaining < 0" style="color: #ef4444; font-weight: 600;">
                逾期 {{ Math.abs(row.daysRemaining) }} 天
              </span>
              <span v-else-if="row.daysRemaining != null && row.daysRemaining <= 15" style="color: #f59e0b; font-weight: 600;">
                剩 {{ row.daysRemaining }} 天到期
              </span>
              <span v-else-if="row.daysRemaining != null" style="color: #10b981;">
                剩 {{ row.daysRemaining }} 天
              </span>
              <span v-else style="color: #64748b;">永久有效</span>
            </template>
          </el-table-column>
          <el-table-column label="健康评分" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" type="success" effect="dark">100 分</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" @click="openInspect(row)">画像</el-button>
            </template>
          </el-table-column>
        </el-table>
      </BaseCard>

      <!-- Right Column: Blueprint & Policies -->
      <div class="side-blueprint-stack">
        <BaseCard>
          <template #header>
            <div class="panel-header">
              <h3>租户全生命周期治理基线</h3>
              <p>平台在租户管理上的自动化执行策略。</p>
            </div>
          </template>
          <div class="baseline-items">
            <div class="baseline-item">
              <span class="baseline-title">开通审批模式</span>
              <strong>{{ runtimePolicy.tenantApprovalMode === 'MANUAL' ? '人工双人复核' : '自动放行开通' }}</strong>
            </div>
            <div class="baseline-item">
              <span class="baseline-title">标准试用窗口</span>
              <strong>{{ runtimePolicy.trialDays }} 天倒计时</strong>
            </div>
            <div class="baseline-item">
              <span class="baseline-title">默认席位配额</span>
              <strong>{{ packagePolicy.seatQuota }} 席位</strong>
            </div>
            <div class="baseline-item">
              <span class="baseline-title">默认存储配额</span>
              <strong>{{ packagePolicy.storageQuotaGb }} GB</strong>
            </div>
            <div class="baseline-item">
              <span class="baseline-title">控制台自动部署</span>
              <el-tag size="small" type="success">已开启</el-tag>
            </div>
          </div>
        </BaseCard>

        <BaseCard>
          <template #header>
            <div class="panel-header">
              <h3>隔离等级递进说明</h3>
              <p>可针对租户业务发展动态平滑演进。</p>
            </div>
          </template>
          <div class="level-steps">
            <div class="step-card">
              <div class="step-dot blue" />
              <div class="step-content">
                <strong>共享 Schema (行级隔离)</strong>
                <p>基于 tenant_id 与 AST 动态拦截，成本最低，标准化交付。</p>
              </div>
            </div>
            <div class="step-card">
              <div class="step-dot amber" />
              <div class="step-content">
                <strong>独立 Schema (表级隔离)</strong>
                <p>独立数据库 Schema，杜绝同表干扰，支持定制化表结构。</p>
              </div>
            </div>
            <div class="step-card">
              <div class="step-dot purple" />
              <div class="step-content">
                <strong>独立物理数据库 (最高隔离)</strong>
                <p>物理独立 RDS 实例，独享连接池与 CPU/IOPS，满足金融级合规。</p>
              </div>
            </div>
          </div>
        </BaseCard>
      </div>
    </div>

    <!-- Tenant 360 Inspection Drawer -->
    <el-drawer v-model="inspectDrawerVisible" title="租户 360° 运营画像透视" size="520px">
      <div v-if="inspectingTenant" class="drawer-content">
        <div class="tenant-banner">
          <div class="tenant-banner-main">
            <h3>{{ inspectingTenant.tenantName }}</h3>
            <span class="mono-code">{{ inspectingTenant.tenantCode }}</span>
          </div>
          <el-tag size="small" type="success" effect="dark">{{ inspectingTenant.status }}</el-tag>
        </div>

        <el-descriptions :column="1" border style="margin-top: 16px;">
          <el-descriptions-item label="租户 ID">{{ inspectingTenant.id }}</el-descriptions-item>
          <el-descriptions-item label="订阅套餐">{{ inspectingTenant.packageName || '基础版' }}</el-descriptions-item>
          <el-descriptions-item label="隔离模式">{{ isolationModeLabelMap[inspectingTenant.isolationMode] || inspectingTenant.isolationMode }}</el-descriptions-item>
          <el-descriptions-item label="履约到期日">{{ inspectingTenant.expireAt || '永久有效' }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ inspectingTenant.contactName || '未指定' }} ({{ inspectingTenant.contactMobile || '-' }})</el-descriptions-item>
          <el-descriptions-item label="联系邮箱">{{ inspectingTenant.contactEmail || '未留存' }}</el-descriptions-item>
          <el-descriptions-item label="备注说明">{{ inspectingTenant.remark || '无' }}</el-descriptions-item>
        </el-descriptions>

        <div class="tenant-quota-meters" style="margin-top: 20px;">
          <h4>实时配额使用情况</h4>
          <div class="meter-box">
            <div class="meter-head">
              <span>账号席位消耗</span>
              <strong>1 / 100 席</strong>
            </div>
            <el-progress :percentage="1" :stroke-width="6" color="#0284c7" />
          </div>
          <div class="meter-box" style="margin-top: 10px;">
            <div class="meter-head">
              <span>文件存储空间</span>
              <strong>0.12 / 50 GB</strong>
            </div>
            <el-progress :percentage="1" :stroke-width="6" color="#10b981" />
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- Onboarding Wizard Dialog -->
    <el-dialog v-model="wizardVisible" title="租户极速开通交付向导" width="580px">
      <el-steps :active="wizardStep" finish-status="success" simple style="margin-bottom: 20px;">
        <el-step title="企业标识" />
        <el-step title="套餐规格" />
        <el-step title="隔离模式" />
        <el-step title="初始交付" />
      </el-steps>

      <div v-if="wizardStep === 0" class="wizard-step-pane">
        <el-form label-position="top">
          <el-form-item label="租户编码 (全局唯一标识)" required>
            <el-input v-model="wizardForm.tenantCode" placeholder="如: alibaba_cloud" />
          </el-form-item>
          <el-form-item label="企业全称 / 组织名称" required>
            <el-input v-model="wizardForm.tenantName" placeholder="如: 阿里巴巴云计算有限公司" />
          </el-form-item>
        </el-form>
      </div>

      <div v-else-if="wizardStep === 1" class="wizard-step-pane">
        <el-form label-position="top">
          <el-form-item label="选择订阅套餐规格">
            <el-radio-group v-model="wizardForm.packageTier" style="display: flex; flex-direction: column; gap: 10px;">
              <el-radio label="BASIC" border style="width: 100%">基础版 · 100 席位 · 10 GB 存储 · 基础能力</el-radio>
              <el-radio label="ENTERPRISE" border style="width: 100%">企业版 · 500 席位 · 100 GB 存储 · 工作流与调度</el-radio>
              <el-radio label="SUPREME" border style="width: 100%">至尊旗舰版 · 无限席位 · 1 TB 存储 · 全功能开放</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </div>

      <div v-else-if="wizardStep === 2" class="wizard-step-pane">
        <el-form label-position="top">
          <el-form-item label="数据隔离部署模式">
            <el-radio-group v-model="wizardForm.isolationMode" style="display: flex; flex-direction: column; gap: 10px;">
              <el-radio label="SHARED_SCHEMA" border style="width: 100%">共享 Schema · 行级隔离 (推荐标准企业)</el-radio>
              <el-radio label="ISOLATED_SCHEMA" border style="width: 100%">独立 Schema · 表级隔离 (中大型企业)</el-radio>
              <el-radio label="DEDICATED_DATABASE" border style="width: 100%">独立物理数据库 (金融监管/独享资源)</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </div>

      <div v-else class="wizard-step-pane">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="租户编码">{{ wizardForm.tenantCode }}</el-descriptions-item>
          <el-descriptions-item label="租户名称">{{ wizardForm.tenantName }}</el-descriptions-item>
          <el-descriptions-item label="订购规格">{{ wizardForm.packageTier }}</el-descriptions-item>
          <el-descriptions-item label="数据隔离">{{ wizardForm.isolationMode }}</el-descriptions-item>
        </el-descriptions>
        <p style="margin-top: 12px; color: #10b981; font-size: 13px;">✓ 点击确认后，系统将自动化完成底层权限范围配置并下发基础账号。</p>
      </div>

      <template #footer>
        <el-button v-if="wizardStep > 0" @click="wizardStep--">上一步</el-button>
        <el-button @click="wizardVisible = false">取消</el-button>
        <el-button type="primary" :loading="wizardSubmitting" @click="handleWizardNext">
          {{ wizardStep === 3 ? "立即交付开通" : "下一步" }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.tenant-overview-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tenant-telemetry-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #ffffff;
  border-radius: 8px;
  border: 1px solid var(--sb-border-color);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);

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

    .icon-blue { color: #0284c7; }
    .icon-emerald { color: #10b981; }
    .icon-indigo { color: #6366f1; }
    .icon-purple { color: #a855f7; }
    .icon-amber { color: #f59e0b; }

    .label {
      font-size: 13px;
      color: var(--sb-text-secondary);
    }

    .value {
      font-size: 13px;
      font-weight: 600;
      color: var(--sb-text-primary);

      &.has-warning {
        color: #d97706;
        background: #fef3c7;
        padding: 2px 8px;
        border-radius: 4px;
      }
    }
  }

  .divider {
    width: 1px;
    height: 14px;
    background: var(--sb-border-color);
  }
}

.overview-dual-grid {
  display: grid;
  grid-template-columns: 68% 32%;
  gap: 16px;

  @media (max-width: 1100px) {
    grid-template-columns: 1fr;
  }
}

.panel-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;

  .header-actions {
    display: flex;
    gap: 8px;
  }
}

.side-blueprint-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.baseline-items {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.baseline-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  border-radius: 4px;
  background: #ffffff;
  border: 1px solid var(--sb-border-color);
  box-shadow: var(--sb-shadow-xs);
  font-size: 13px;

  .baseline-title {
    color: var(--sb-text-secondary);
  }

  strong {
    color: var(--sb-text-primary);
  }
}

.level-steps {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.step-card {
  display: flex;
  gap: 10px;
  align-items: flex-start;

  .step-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    margin-top: 6px;
    flex-shrink: 0;

    &.blue { background: #0284c7; }
    &.amber { background: #f59e0b; }
    &.purple { background: #a855f7; }
  }

  .step-content {
    strong {
      font-size: 13px;
      color: var(--sb-text-primary);
    }
    p {
      margin: 2px 0 0;
      font-size: 12px;
      color: var(--sb-text-secondary);
      line-height: 1.4;
    }
  }
}

.mono-code {
  font-family: ui-monospace, SFMono-Regular, monospace;
  font-size: 12px;
  color: var(--sb-text-secondary);
}

.tenant-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;

  h3 {
    margin: 0;
    font-size: 16px;
  }
}

.tenant-quota-meters {
  h4 {
    margin: 0 0 10px;
    font-size: 14px;
  }

  .meter-box {
    padding: 10px 12px;
    background: #f8fafc;
    border-radius: 6px;
    border: 1px solid #e2e8f0;

    .meter-head {
      display: flex;
      justify-content: space-between;
      margin-bottom: 6px;
      font-size: 12px;
      color: #64748b;

      strong {
        color: #0f172a;
      }
    }
  }
}
</style>
