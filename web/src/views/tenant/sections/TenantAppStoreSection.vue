<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  ArrowRight,
  Briefcase,
  Check,
  CircleCheck,
  Clock,
  Close,
  Coin,
  Connection,
  Cpu,
  DataLine,
  Document,
  Files,
  Grid,
  Key,
  Lock,
  MagicStick,
  Menu,
  OfficeBuilding,
  PriceTag,
  Printer,
  Refresh,
  Search,
  Setting,
  SetUp,
  Share,
  Shop,
  Switch,
  Timer,
  TrendCharts,
  UserFilled,
  Warning
} from "@element-plus/icons-vue";
import {
  fetchTenantAppCatalog,
  subscribeTenantApp,
  updateTenantAppStatus,
  toggleTenantAppAuthorization,
  batchAuthorizeTenantApps,
  resetTenantPackageBaseline,
  fetchTenantPage
} from "@/api/modules/tenant";
import type {
  TenantAppCatalogItem,
  TenantAppSubscribePayload,
  TenantRow
} from "@/api/modules/tenant";
import BaseCard from "@/components/base/BaseCard.vue";
import { useTenantStore } from "@/stores/modules/tenant";

const route = useRoute();
const router = useRouter();
const tenantStore = useTenantStore();

const props = defineProps<{
  tenantId?: number;
  tenants?: TenantRow[];
  runtimePolicy?: any;
  packagePolicy?: any;
  isolationPolicy?: any;
  isolationModeLabelMap?: Record<string, string>;
  query?: any;
  schema?: any;
  columns?: any;
  rows?: any;
  resetQuery?: any;
  openCreateDialog?: any;
  openImportDialog?: any;
  savePolicy?: any;
}>();

const catalogLoading = ref(false);
const catalog = ref<TenantAppCatalogItem[]>([]);
const tenants = ref<TenantRow[]>(props.tenants || []);
const selectedTenantId = ref<number | undefined>(
  props.tenantId || (route.query.tenantId ? Number(route.query.tenantId) : undefined)
);

// View Mode: Table matrix (default, high efficiency) vs Refined compact cards
const viewMode = ref<"table" | "card">(
  (localStorage.getItem("sb_app_store_view_mode") as "table" | "card") || "table"
);

function setViewMode(mode: "table" | "card") {
  viewMode.value = mode;
  localStorage.setItem("sb_app_store_view_mode", mode);
}

const searchKeyword = ref("");
const selectedCategory = ref("ALL");
const selectedStatus = ref("ALL");

// Subscribe dialog state
const subscribeDialogVisible = ref(false);
const subscribing = ref(false);
const currentApp = ref<TenantAppCatalogItem | null>(null);
const subscribeForm = reactive<TenantAppSubscribePayload>({
  appCode: "",
  trialDays: 14,
  remark: "控制台自助开通试用"
});

const isolationLabelMap: Record<string, string> = {
  PLATFORM: "平台模式",
  SHARED_SCHEMA: "共享 Schema (行级隔离)",
  ISOLATED_SCHEMA: "独立 Schema (表级隔离)",
  DEDICATED_DATABASE: "独立数据库 (物理隔离)",
  ...(props.isolationModeLabelMap || {})
};

const currentTenantMeta = computed(() => {
  return tenants.value.find((t) => t.id === selectedTenantId.value);
});

const categoryOptions = [
  { label: "全部应用", value: "ALL" },
  { label: "核心底座", value: "SYSTEM" },
  { label: "协同引擎", value: "COLLABORATION" },
  { label: "业务中台", value: "BUSINESS" },
  { label: "基础工具", value: "INFRA" }
];

const categoryLabelMap: Record<string, string> = {
  SYSTEM: "核心底座",
  CORE: "核心底座",
  COLLABORATION: "协同引擎",
  BUSINESS: "业务中台",
  INFRA: "基础工具",
  SECURITY: "安全风控",
  INTEGRATION: "开放集成",
  DEV: "开发运维"
};

const appRouteMap: Record<string, string> = {
  dashboard: "/dashboard/workbench",
  tenant: "/tenant/overview",
  iam: "/iam/users",
  workflow: "/workflow/definitions",
  crm: "/crm/leads",
  erp: "/erp/products",
  file: "/file/upload",
  scheduler: "/scheduler/jobs",
  codegen: "/codegen",
  audit: "/audit/logs",
  integration: "/integration/gateways",
  system: "/system/dicts",
  report: "/integration/openapp"
};

const iconComponentMap: Record<string, any> = {
  Connection,
  Briefcase,
  Shop,
  Document,
  Clock,
  Files,
  DataLine,
  Lock,
  OfficeBuilding,
  Key,
  Cpu,
  Coin,
  SetUp,
  UserFilled,
  Share,
  TrendCharts,
  Timer,
  Setting,
  MagicStick,
  Printer
};

const filteredCatalog = computed(() => {
  return catalog.value.filter((item) => {
    const kw = searchKeyword.value.trim().toLowerCase();
    const matchKw =
      !kw ||
      item.appName.toLowerCase().includes(kw) ||
      item.appCode.toLowerCase().includes(kw) ||
      item.description.toLowerCase().includes(kw);

    const matchCategory =
      selectedCategory.value === "ALL" ||
      item.category === selectedCategory.value ||
      (selectedCategory.value === "SYSTEM" && item.category === "CORE");

    const matchStatus =
      selectedStatus.value === "ALL" || item.status === selectedStatus.value;

    return matchKw && matchCategory && matchStatus;
  });
});

const stats = computed(() => {
  const total = catalog.value.length;
  const enabled = catalog.value.filter((a) => a.status === "ENABLED").length;
  const disabled = catalog.value.filter((a) => a.status === "DISABLED").length;
  return { total, enabled, disabled };
});

async function fetchCatalog() {
  catalogLoading.value = true;
  try {
    if (tenants.value.length === 0) {
      if (props.tenants && props.tenants.length > 0) {
        tenants.value = props.tenants;
      } else {
        const tenantData = await fetchTenantPage().catch(() => []);
        tenants.value = Array.isArray(tenantData) ? tenantData : ((tenantData as any)?.records || []);
      }
    }

    if (!selectedTenantId.value && tenants.value.length > 0) {
      const currentId = tenantStore.currentTenant?.id;
      const matched = tenants.value.find((t) => t.id === currentId);
      selectedTenantId.value = matched ? matched.id : tenants.value[0].id;
      router.replace({
        query: { ...route.query, tenantId: String(selectedTenantId.value) }
      });
    }

    const catalogData = await fetchTenantAppCatalog(selectedTenantId.value);
    catalog.value = catalogData;
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "获取租户应用授权目录失败");
  } finally {
    catalogLoading.value = false;
  }
}

function handleTenantChange(targetId?: number) {
  if (targetId) {
    router.replace({
      query: { ...route.query, tenantId: String(targetId) }
    });
  }
  fetchCatalog();
}

function getAppIcon(iconName: string) {
  return iconComponentMap[iconName] || Cpu;
}

async function handleToggleAppAuthorization(app: TenantAppCatalogItem, targetEnabled: boolean) {
  const tenantName = currentTenantMeta.value?.tenantName || "当前租户";
  const actionText = targetEnabled ? "授权开通" : "停用回收";
  const impactText = targetEnabled
    ? `确认将【${app.appName}】授权给企业「${tenantName}」使用？授权后该企业将即时获得对应菜单与接口权限。`
    : `确认回收企业「${tenantName}」的【${app.appName}】权限？停用后该应用在企业端将彻底隐藏屏蔽。`;

  try {
    await ElMessageBox.confirm(
      impactText,
      `企业应用${actionText}`,
      {
        confirmButtonText: `确认${actionText}`,
        cancelButtonText: "取消",
        type: targetEnabled ? "success" : "warning"
      }
    );

    catalogLoading.value = true;
    await toggleTenantAppAuthorization(
      {
        appCode: app.appCode,
        enabled: targetEnabled,
        remark: `管理员${actionText}应用权限`
      },
      selectedTenantId.value
    );

    ElMessage.success(`应用【${app.appName}】已成功为「${tenantName}」${targetEnabled ? "授权开启" : "回收停用"}！`);
    await fetchCatalog();
  } catch (e: any) {
    if (e !== "cancel") {
      ElMessage.error(e?.response?.data?.message ?? `${actionText}失败`);
    }
  } finally {
    catalogLoading.value = false;
  }
}

async function handleBatchAuthorize(targetEnabled: boolean) {
  if (!selectedTenantId.value) return;
  const actionText = targetEnabled ? "全量授权" : "全部回收";
  const tenantName = currentTenantMeta.value?.tenantName || "当前租户";
  try {
    await ElMessageBox.confirm(
      `确定为企业「${tenantName}」执行【${actionText}】吗？${
        targetEnabled ? "授权后该企业将获得全部可用业务应用。" : "回收后除底座核心外，其余应用将对该租户彻底屏蔽。"
      }`,
      `确认${actionText}`,
      {
        confirmButtonText: `确认${actionText}`,
        cancelButtonText: "取消",
        type: targetEnabled ? "success" : "warning"
      }
    );
    catalogLoading.value = true;
    const allCodes = catalog.value.map((c) => c.appCode);
    await batchAuthorizeTenantApps(
      {
        appCodes: allCodes,
        enabled: targetEnabled,
        remark: `管理员批量${actionText}`
      },
      selectedTenantId.value
    );
    ElMessage.success(`企业「${tenantName}」已完成全部应用${actionText}！`);
    await fetchCatalog();
  } catch (e: any) {
    if (e !== "cancel") {
      ElMessage.error(e?.response?.data?.message ?? `${actionText}失败`);
    }
  } finally {
    catalogLoading.value = false;
  }
}

async function handleAlignWithPackage() {
  if (!selectedTenantId.value) return;
  const tenantName = currentTenantMeta.value?.tenantName || "当前租户";
  const pkgName = currentTenantMeta.value?.packageName || "默认套餐";
  try {
    await ElMessageBox.confirm(
      `确定将企业「${tenantName}」的应用权限重置并对齐其套餐【${pkgName}】基线吗？\n将清除所有单独设定的覆盖，严格恢复为套餐预设应用。`,
      "对齐套餐基线确认",
      {
        confirmButtonText: "确认对齐",
        cancelButtonText: "取消",
        type: "info"
      }
    );
    catalogLoading.value = true;
    await resetTenantPackageBaseline(selectedTenantId.value);
    ElMessage.success(`企业「${tenantName}」已成功对齐【${pkgName}】基线！`);
    await fetchCatalog();
  } catch (e: any) {
    if (e !== "cancel") {
      ElMessage.error(e?.response?.data?.message ?? "重置基线失败");
    }
  } finally {
    catalogLoading.value = false;
  }
}

function navigateToApp(app: TenantAppCatalogItem) {
  const path = appRouteMap[app.appCode];
  if (path) {
    router.push(path);
  } else {
    ElMessage.info(`正在前往应用：${app.appName}`);
  }
}

watch(
  () => props.tenantId,
  (newId) => {
    if (newId && newId !== selectedTenantId.value) {
      selectedTenantId.value = newId;
      fetchCatalog();
    }
  }
);

watch(
  () => route.query.tenantId,
  (newQueryId) => {
    if (newQueryId) {
      const num = Number(newQueryId);
      if (num && num !== selectedTenantId.value) {
        selectedTenantId.value = num;
        fetchCatalog();
      }
    }
  }
);

watch(
  () => props.tenants,
  (newTenants) => {
    if (newTenants && newTenants.length > 0) {
      tenants.value = newTenants;
    }
  },
  { immediate: true }
);

onMounted(() => {
  fetchCatalog();
});
</script>

<template>
  <div class="tenant-app-store-section">
    <!-- Unified Enterprise Entitlement Command Surface -->
    <div class="command-surface">
      <!-- Top Row: Target Tenant Selector + Meta Badges + Actions -->
      <div class="command-top-row">
        <div class="tenant-focus-cluster">
          <div class="tenant-select-box">
            <span class="tenant-select-label">
              <el-icon class="mr-1 text-primary"><OfficeBuilding /></el-icon>
              目标企业 / 租户
            </span>
            <el-select
              v-model="selectedTenantId"
              placeholder="选择企业租户..."
              filterable
              class="tenant-select"
              @change="handleTenantChange"
            >
              <el-option
                v-for="t in tenants"
                :key="t.id"
                :label="`${t.tenantName} (${t.tenantCode})`"
                :value="t.id"
              >
                <div class="tenant-option-row">
                  <span class="option-name">{{ t.tenantName }}</span>
                  <span class="option-code">{{ t.tenantCode }}</span>
                  <el-tag size="small" type="info" effect="plain">{{ t.packageName || '默认套餐' }}</el-tag>
                </div>
              </el-option>
            </el-select>
          </div>

          <div v-if="currentTenantMeta" class="tenant-meta-strip">
            <span class="meta-tag package-pill">
              {{ currentTenantMeta.packageName || '默认套餐' }}
            </span>
            <span class="meta-tag isolation-pill">
              {{ isolationLabelMap[currentTenantMeta.isolationMode] || currentTenantMeta.isolationMode }}
            </span>
            <span
              class="status-chip"
              :class="{ 'is-active': currentTenantMeta.status === 'ACTIVE' }"
            >
              <span class="status-dot" />
              {{ currentTenantMeta.status === 'ACTIVE' ? '正常履约' : currentTenantMeta.status }}
            </span>
          </div>
        </div>

        <div class="command-actions-cluster">
          <el-button-group>
            <el-button type="primary" size="default" :icon="CircleCheck" @click="handleBatchAuthorize(true)">
              全量授权
            </el-button>
            <el-button size="default" :icon="MagicStick" @click="handleAlignWithPackage">
              对齐套餐基线
            </el-button>
            <el-button size="default" :icon="Close" @click="handleBatchAuthorize(false)">
              全部回收
            </el-button>
          </el-button-group>
          <el-button :icon="Refresh" circle size="default" :loading="catalogLoading" @click="fetchCatalog" />
        </div>
      </div>

      <!-- Bottom Row: Filter Tabs + Counter + View Switcher + Search -->
      <div class="command-filter-row">
        <el-radio-group v-model="selectedCategory" size="small" class="category-radio-group">
          <el-radio-button
            v-for="opt in categoryOptions"
            :key="opt.value"
            :label="opt.value"
          >
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>

        <div class="filter-right-cluster">
          <!-- Compact Metric Chip -->
          <div class="metric-chip">
            <span class="chip-label">已授权应用</span>
            <span class="chip-val"><strong>{{ stats.enabled }}</strong> / {{ stats.total }}</span>
          </div>

          <!-- View Mode Switcher -->
          <div class="view-mode-tabs">
            <button
              type="button"
              class="view-mode-btn"
              :class="{ 'is-active': viewMode === 'table' }"
              title="切换为矩阵清单表格"
              @click="setViewMode('table')"
            >
              <el-icon><Menu /></el-icon>
              <span>清单矩阵</span>
            </button>
            <button
              type="button"
              class="view-mode-btn"
              :class="{ 'is-active': viewMode === 'card' }"
              title="切换为精简卡片视图"
              @click="setViewMode('card')"
            >
              <el-icon><Grid /></el-icon>
              <span>精简卡片</span>
            </button>
          </div>

          <el-select v-model="selectedStatus" placeholder="状态" size="small" style="width: 110px">
            <el-option label="全部状态" value="ALL" />
            <el-option label="已授权" value="ENABLED" />
            <el-option label="未授权" value="DISABLED" />
          </el-select>

          <el-input
            v-model="searchKeyword"
            placeholder="搜索应用..."
            :prefix-icon="Search"
            clearable
            size="small"
            style="width: 180px"
          />
        </div>
      </div>
    </div>

    <!-- VIEW A: Enterprise Matrix Table (Default, Clean & Rigorous) -->
    <div v-if="viewMode === 'table'" v-loading="catalogLoading" class="matrix-table-container">
      <el-table
        :data="filteredCatalog"
        row-key="appCode"
        size="default"
        class="clean-matrix-table"
      >
        <!-- Col 1: App Identity -->
        <el-table-column label="应用服务" min-width="240">
          <template #default="{ row }">
            <div class="table-app-cell">
              <div class="app-icon-badge" :class="`icon-${row.category.toLowerCase()}`">
                <el-icon :size="18">
                  <component :is="getAppIcon(row.icon)" />
                </el-icon>
              </div>
              <div class="app-info-block">
                <span class="app-title-text">{{ row.appName }}</span>
                <span class="app-category-badge">{{ categoryLabelMap[row.category] || row.category }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <!-- Col 2: Core Capability / Position -->
        <el-table-column label="核心功能定位" min-width="320">
          <template #default="{ row }">
            <span class="app-summary-desc">{{ row.description }}</span>
          </template>
        </el-table-column>

        <!-- Col 3: Package Baseline Status -->
        <el-table-column label="套餐预设" width="130" align="center">
          <template #default="{ row }">
            <span v-if="row.isIncludedInPlan" class="package-included-pill">
              <el-icon class="mr-1 text-success"><Check /></el-icon> 套餐标配
            </span>
            <span v-else class="package-addon-pill">
              增购 / 特许
            </span>
          </template>
        </el-table-column>

        <!-- Col 4: Authorization Switch (Instant Trigger) -->
        <el-table-column label="企业授权状态" width="160" align="center">
          <template #default="{ row }">
            <div class="switch-cell-wrapper">
              <el-switch
                :model-value="row.status === 'ENABLED'"
                active-text="已授权"
                inactive-text="未授权"
                inline-prompt
                :loading="catalogLoading"
                @change="(val: any) => handleToggleAppAuthorization(row, Boolean(val))"
              />
            </div>
          </template>
        </el-table-column>

        <!-- Col 5: Direct Launch / Action -->
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'ENABLED'"
              type="primary"
              link
              size="small"
              @click="navigateToApp(row)"
            >
              进入 <el-icon class="ml-1"><ArrowRight /></el-icon>
            </el-button>
            <span v-else class="text-disabled-cell">--</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- VIEW B: Refined Compact Tech Cards (Minimalist & Clean) -->
    <div v-else v-loading="catalogLoading" class="compact-card-grid">
      <div
        v-for="app in filteredCatalog"
        :key="app.appCode"
        class="compact-app-card"
        :class="{ 'is-authorized': app.status === 'ENABLED' }"
      >
        <div class="card-head">
          <div class="card-icon-box" :class="`icon-${app.category.toLowerCase()}`">
            <el-icon :size="18">
              <component :is="getAppIcon(app.icon)" />
            </el-icon>
          </div>
          <div class="card-title-box">
            <div class="card-name-line">
              <span class="card-app-title">{{ app.appName }}</span>
              <span class="card-cat-chip">{{ categoryLabelMap[app.category] || app.category }}</span>
            </div>
            <span class="card-desc-line">{{ app.description }}</span>
          </div>
        </div>

        <div class="card-foot">
          <div class="card-foot-left">
            <span v-if="app.isIncludedInPlan" class="package-included-pill">
              <el-icon class="mr-1 text-success"><Check /></el-icon> 标配
            </span>
            <span v-else class="package-addon-pill">
              特许
            </span>
          </div>

          <div class="card-foot-right">
            <el-switch
              :model-value="app.status === 'ENABLED'"
              active-text="已授权"
              inactive-text="未授权"
              inline-prompt
              :loading="catalogLoading"
              @change="(val: any) => handleToggleAppAuthorization(app, Boolean(val))"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.tenant-app-store-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Unified Command Surface */
.command-surface {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px 18px;
  box-shadow: 0 1px 3px 0 rgb(15 23 42 / 0.05);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.command-top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.tenant-focus-cluster {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.tenant-select-box {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tenant-select-label {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  white-space: nowrap;
  display: flex;
  align-items: center;
}

.tenant-select {
  width: 280px;
}

.tenant-option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 8px;

  .option-name {
    font-weight: 600;
    color: #0f172a;
  }

  .option-code {
    font-size: 11px;
    color: #64748b;
    font-family: ui-monospace, monospace;
  }
}

.tenant-meta-strip {
  display: flex;
  align-items: center;
  gap: 8px;

  .meta-tag {
    font-size: 12px;
    padding: 2px 8px;
    border-radius: 6px;
    font-weight: 500;
  }

  .package-pill {
    background: #ecfdf5;
    color: #059669;
    border: 1px solid #a7f3d0;
  }

  .isolation-pill {
    background: #f8fafc;
    color: #475569;
    border: 1px solid #e2e8f0;
  }

  .status-chip {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 12px;
    color: #64748b;

    .status-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: #94a3b8;
    }

    &.is-active {
      color: #059669;
      .status-dot {
        background: #10b981;
      }
    }
  }
}

.command-actions-cluster {
  display: flex;
  align-items: center;
  gap: 10px;
}

.command-filter-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding-top: 10px;
  border-top: 1px solid #f1f5f9;
}

.category-radio-group {
  :deep(.el-radio-button__inner) {
    font-size: 12px;
    padding: 6px 14px;
  }
}

.filter-right-cluster {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.metric-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;

  .chip-label {
    color: #64748b;
  }

  .chip-val {
    color: #0f172a;
    strong {
      color: #2563eb;
    }
  }
}

/* View Mode Switcher Button Group */
.view-mode-tabs {
  display: inline-flex;
  background: #f1f5f9;
  border-radius: 6px;
  padding: 2px;
  gap: 2px;

  .view-mode-btn {
    border: none;
    background: transparent;
    padding: 3px 8px;
    border-radius: 4px;
    font-size: 12px;
    color: #64748b;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    transition: all 0.15s ease;

    &:hover {
      color: #0f172a;
    }

    &.is-active {
      background: #ffffff;
      color: #0f172a;
      font-weight: 600;
      box-shadow: 0 1px 2px rgb(0 0 0 / 0.05);
    }
  }
}

/* VIEW A: Matrix Table */
.matrix-table-container {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 1px 3px 0 rgb(15 23 42 / 0.05);
}

.clean-matrix-table {
  width: 100%;

  :deep(.el-table__header-wrapper th) {
    background: #f8fafc;
    color: #475569;
    font-size: 13px;
    font-weight: 600;
    height: 44px;
  }

  :deep(.el-table__row) {
    height: 54px;
    &:hover {
      background: #f8fafc !important;
    }
  }
}

.table-app-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.app-icon-badge {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.icon-system,
.icon-core {
  background: #eff6ff;
  color: #2563eb;
}

.icon-collaboration {
  background: #fef3c7;
  color: #d97706;
}

.icon-business {
  background: #ecfdf5;
  color: #059669;
}

.icon-infra,
.icon-dev {
  background: #f1f5f9;
  color: #475569;
}

.icon-security {
  background: #fee2e2;
  color: #dc2626;
}

.icon-integration {
  background: #fef3c7;
  color: #d97706;
}

.app-info-block {
  display: flex;
  align-items: center;
  gap: 6px;
}

.app-title-text {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.app-category-badge {
  font-size: 11px;
  color: #64748b;
  background: #f1f5f9;
  border-radius: 4px;
  padding: 1px 5px;
}

.app-summary-desc {
  font-size: 13px;
  color: #475569;
  line-height: 1.4;
}

.package-included-pill {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
  color: #059669;
}

.package-addon-pill {
  font-size: 12px;
  color: #94a3b8;
}

.switch-cell-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
}

.text-disabled-cell {
  color: #cbd5e1;
}

/* VIEW B: Compact Tech Cards */
.compact-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.compact-app-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition: all 0.15s ease;
  min-height: 110px;

  &:hover {
    border-color: #cbd5e1;
    box-shadow: 0 4px 12px rgb(15 23 42 / 0.06);
  }

  &.is-authorized {
    border-left: 3px solid #10b981;
  }
}

.card-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.card-icon-box {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.card-title-box {
  flex: 1;
  min-width: 0;
}

.card-name-line {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 2px;
}

.card-app-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.card-cat-chip {
  font-size: 10px;
  color: #64748b;
  background: #f1f5f9;
  border-radius: 4px;
  padding: 1px 4px;
}

.card-desc-line {
  font-size: 12px;
  color: #64748b;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
}

.card-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid #f1f5f9;
}

.mr-1 {
  margin-right: 4px;
}

.ml-1 {
  margin-left: 4px;
}

.text-primary {
  color: #2563eb;
}

.text-success {
  color: #10b981;
}
</style>
