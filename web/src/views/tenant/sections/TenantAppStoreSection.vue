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
  Key,
  Lock,
  MagicStick,
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
  SECURITY: "安全合规",
  INTEGRATION: "开放集成",
  DEV: "开发运维"
};

const categoryTagTypeMap: Record<string, "primary" | "success" | "warning" | "info" | "danger"> = {
  SYSTEM: "primary",
  CORE: "primary",
  COLLABORATION: "warning",
  BUSINESS: "success",
  INFRA: "info",
  SECURITY: "danger",
  INTEGRATION: "warning",
  DEV: "info"
};

const tierLabelMap: Record<string, string> = {
  BASIC: "基础版",
  PROFESSIONAL: "专业版",
  ENTERPRISE: "企业版",
  SUPREME: "至尊版",
  PLATFORM: "平台旗舰版"
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
  const trial = catalog.value.filter((a) => a.status === "TRIAL").length;
  const disabled = catalog.value.filter((a) => a.status === "DISABLED").length;
  const expired = catalog.value.filter((a) => a.status === "EXPIRED").length;
  return { total, enabled, trial, disabled, expired };
});

async function fetchCatalog() {
  catalogLoading.value = true;
  try {
    // 1. Ensure tenants list is ready
    if (tenants.value.length === 0) {
      if (props.tenants && props.tenants.length > 0) {
        tenants.value = props.tenants;
      } else {
        const tenantData = await fetchTenantPage().catch(() => []);
        tenants.value = Array.isArray(tenantData) ? tenantData : ((tenantData as any)?.records || []);
      }
    }

    // 2. Fallback to active workspace tenant or first tenant if not set
    if (!selectedTenantId.value && tenants.value.length > 0) {
      const currentId = tenantStore.currentTenant?.id;
      const matched = tenants.value.find((t) => t.id === currentId);
      selectedTenantId.value = matched ? matched.id : tenants.value[0].id;
      router.replace({
        query: { ...route.query, tenantId: String(selectedTenantId.value) }
      });
    }

    // 3. Load catalog for target tenant
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
    ? `授权后，企业【${tenantName}】将立即获得【${app.appName}】的所有默认菜单和功能权限，并在该企业的【菜单管理】中呈现。`
    : `停用后，企业【${tenantName}】在【菜单管理】、【角色分配】及【侧边栏导航】中将彻底隐藏【${app.appName}】的所有菜单与按钮！`;

  try {
    await ElMessageBox.confirm(
      `${impactText}\n\n确定要为企业【${tenantName}】${actionText}【${app.appName}】的应用权限吗？`,
      `确认${actionText}应用权限`,
      {
        confirmButtonText: `确定${actionText}`,
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

    ElMessage.success(`应用【${app.appName}】已成功为企业【${tenantName}】${targetEnabled ? "授权开启" : "回收停用"}！`);
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
      `确定为企业【${tenantName}】执行${actionText}操作吗？\n${
        targetEnabled
          ? "授权后该企业将获得平台全部可用业务应用的菜单与接口使用权。"
          : "回收后除底座核心服务外，其余应用将对该租户在菜单和导航中彻底物理屏蔽。"
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
    ElMessage.success(`企业【${tenantName}】已成功完成全部应用${actionText}！`);
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
      `确定将企业【${tenantName}】的应用权限重置并对齐其订阅套餐【${pkgName}】基线吗？\n该操作将清除针对该租户的所有个性化增删覆盖，严格还原为套餐预设应用矩阵。`,
      "对齐套餐基线确认",
      {
        confirmButtonText: "确认对齐",
        cancelButtonText: "取消",
        type: "info"
      }
    );
    catalogLoading.value = true;
    await resetTenantPackageBaseline(selectedTenantId.value);
    ElMessage.success(`企业【${tenantName}】已成功重置并对齐【${pkgName}】基线！`);
    await fetchCatalog();
  } catch (e: any) {
    if (e !== "cancel") {
      ElMessage.error(e?.response?.data?.message ?? "重置基线失败");
    }
  } finally {
    catalogLoading.value = false;
  }
}

function openSubscribeDialog(app: TenantAppCatalogItem, defaultDays = 14) {
  currentApp.value = app;
  subscribeForm.appCode = app.appCode;
  subscribeForm.trialDays = defaultDays;
  subscribeForm.remark = `控制台申请 ${app.appName} 试用`;
  subscribeDialogVisible.value = true;
}

async function handleSubscribe() {
  if (!subscribeForm.appCode) return;
  subscribing.value = true;
  try {
    await subscribeTenantApp(subscribeForm, selectedTenantId.value);
    ElMessage.success(`应用【${currentApp.value?.appName}】开通成功，权限即刻生效！`);
    subscribeDialogVisible.value = false;
    await fetchCatalog();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "开通应用失败");
  } finally {
    subscribing.value = false;
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
    <!-- Hero Target Tenant Authorization Banner -->
    <BaseCard class="target-tenant-hero-card">
      <div class="hero-top-bar">
        <div class="hero-title-group">
          <div class="title-row">
            <span class="hero-main-title">租户应用授权中心</span>
            <el-tag type="danger" effect="dark" round class="hero-badge">
              顶级应用权限管控
            </el-tag>
          </div>
          <p class="hero-subtitle">
            平台管理员专属控制台：精准决定各企业租户可拥有的应用矩阵与功能范围。未获授权的应用对该租户在菜单管理、角色分配及系统导航中彻底隐形。
          </p>
        </div>

        <div class="hero-actions-group">
          <el-button-group>
            <el-button type="success" :icon="CircleCheck" @click="handleBatchAuthorize(true)">
              一键全量授权
            </el-button>
            <el-button type="primary" plain :icon="MagicStick" @click="handleAlignWithPackage">
              对齐套餐基线
            </el-button>
            <el-button type="warning" plain :icon="Close" @click="handleBatchAuthorize(false)">
              一键全部回收
            </el-button>
          </el-button-group>
          <el-button :icon="Refresh" circle :loading="catalogLoading" @click="fetchCatalog" />
        </div>
      </div>

      <!-- Prominent Enterprise/Tenant Selector -->
      <div class="enterprise-selector-panel">
        <div class="selector-field">
          <span class="selector-title">
            <el-icon class="mr-1 text-primary"><OfficeBuilding /></el-icon>
            当前正在授权配置的目标企业 / 租户：
          </span>
          <el-select
            v-model="selectedTenantId"
            placeholder="请选择要授权的企业租户..."
            filterable
            class="tenant-primary-select"
            @change="handleTenantChange"
          >
            <el-option
              v-for="t in tenants"
              :key="t.id"
              :label="`${t.tenantName} (${t.tenantCode}) · [${t.packageName || '默认套餐'}]`"
              :value="t.id"
            >
              <div class="tenant-option-row">
                <span class="option-name">{{ t.tenantName }}</span>
                <span class="option-code">编码: {{ t.tenantCode }}</span>
                <el-tag size="small" type="success" effect="plain">{{ t.packageName || '默认套餐' }}</el-tag>
                <el-tag size="small" type="info" effect="light">{{ isolationLabelMap[t.isolationMode] || t.isolationMode }}</el-tag>
              </div>
            </el-option>
          </el-select>
        </div>

        <!-- Active Tenant Meta Tags -->
        <div v-if="currentTenantMeta" class="active-tenant-meta-pills">
          <el-tag type="info" effect="plain" class="meta-pill">
            企业编码：<strong>{{ currentTenantMeta.tenantCode }}</strong>
          </el-tag>
          <el-tag type="success" effect="dark" class="meta-pill">
            订购套餐：<strong>{{ currentTenantMeta.packageName || '默认套餐' }}</strong>
          </el-tag>
          <el-tag type="warning" effect="plain" class="meta-pill">
            隔离模式：<strong>{{ isolationLabelMap[currentTenantMeta.isolationMode] || currentTenantMeta.isolationMode }}</strong>
          </el-tag>
          <el-tag :type="currentTenantMeta.status === 'ACTIVE' ? 'success' : 'info'" effect="plain" class="meta-pill">
            生命周期：<strong>{{ currentTenantMeta.status === 'ACTIVE' ? '正常履约' : currentTenantMeta.status }}</strong>
          </el-tag>
          <el-tag v-if="currentTenantMeta.daysRemaining != null" type="primary" effect="plain" class="meta-pill">
            服务有效期：<strong>{{ currentTenantMeta.daysRemaining }} 天</strong>
          </el-tag>
        </div>
      </div>

      <!-- Enterprise Isolation Callout Notice -->
      <div class="isolation-notice-strip">
        <el-icon :size="16" class="notice-icon"><Warning /></el-icon>
        <span class="notice-text">
          <strong>企业独立应用授权隔离机制：</strong>
          您当前正在对企业<strong>【{{ currentTenantMeta?.tenantName || '当前选中租户' }}】</strong>配置应用授权。下方每个应用的<strong>【开关】</strong>拨动后<strong>即刻针对该企业生效</strong>。未获授权的应用将在该企业的【菜单管理】、【角色授权】及【系统侧边栏】中<strong>彻底隐藏</strong>，杜绝越权。
        </span>
      </div>

      <!-- Stat Badges Row -->
      <div class="stats-row">
        <div class="stat-item total">
          <span class="stat-num">{{ stats.total }}</span>
          <span class="stat-label">矩阵应用总数</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-item enabled">
          <span class="stat-num">{{ stats.enabled }}</span>
          <span class="stat-label">已为该企业授权</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-item disabled">
          <span class="stat-num">{{ stats.disabled }}</span>
          <span class="stat-label">未授权 / 已隔离回收</span>
        </div>
      </div>

      <!-- Filter Controls -->
      <div class="filter-row">
        <el-radio-group v-model="selectedCategory" size="default" class="category-radio">
          <el-radio-button
            v-for="opt in categoryOptions"
            :key="opt.value"
            :label="opt.value"
          >
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>

        <div class="filter-inputs">
          <el-select v-model="selectedStatus" placeholder="权限状态筛选" style="width: 150px">
            <el-option label="全部状态" value="ALL" />
            <el-option label="已授权 (生效)" value="ENABLED" />
            <el-option label="未授权 (停用)" value="DISABLED" />
          </el-select>

          <el-input
            v-model="searchKeyword"
            placeholder="搜索应用名称 / App Code..."
            :prefix-icon="Search"
            clearable
            style="width: 260px"
          />
        </div>
      </div>
    </BaseCard>

    <!-- App Cards Grid -->
    <div v-loading="catalogLoading" class="app-grid">
      <div
        v-for="app in filteredCatalog"
        :key="app.appCode"
        class="app-card"
        :class="[`status-${app.status.toLowerCase()}`, { 'in-plan': app.isIncludedInPlan }]"
      >
        <!-- Card Top Bar: Icon, Name, Category & Status -->
        <div class="card-header">
          <div class="app-icon-wrapper" :class="`icon-${app.category.toLowerCase()}`">
            <el-icon :size="24">
              <component :is="getAppIcon(app.icon)" />
            </el-icon>
          </div>

          <div class="app-title-box">
            <div class="app-name-row">
              <span class="app-name">{{ app.appName }}</span>
              <el-tag
                :type="categoryTagTypeMap[app.category] || 'info'"
                size="small"
                effect="plain"
                class="category-tag"
              >
                {{ categoryLabelMap[app.category] || app.category }}
              </el-tag>
            </div>
            <span class="app-code-badge">app_code: {{ app.appCode }}</span>
          </div>

          <div class="status-indicator">
            <el-tag
              v-if="app.status === 'ENABLED'"
              type="success"
              effect="dark"
              round
              size="small"
            >
              <el-icon class="mr-1"><CircleCheck /></el-icon> 已授权使用
            </el-tag>
            <el-tag
              v-else
              type="info"
              effect="plain"
              round
              size="small"
            >
              未授权 / 已停用
            </el-tag>
          </div>
        </div>

        <!-- Description -->
        <p class="app-desc">{{ app.description }}</p>

        <!-- Entitlement Context Pills -->
        <div class="entitlement-meta">
          <div class="meta-row">
            <span class="meta-label">套餐归属状态:</span>
            <span v-if="app.isIncludedInPlan" class="plan-included-text">
              <el-icon><Check /></el-icon> 属于套餐预设权益
            </span>
            <span v-else class="addon-text">
              <el-icon><Close /></el-icon> 套餐外独立增购 / 特许
            </span>
          </div>

          <div class="meta-row">
            <span class="meta-label">租户端生效表现:</span>
            <span v-if="app.status === 'ENABLED'" class="plan-included-text">
              <el-icon><Check /></el-icon> 菜单管理可见 · 角色可用
            </span>
            <span v-else class="addon-text">
              <el-icon><Close /></el-icon> 菜单完全隐形 · 访问物理拦截
            </span>
          </div>
        </div>

        <!-- Card Footer Actions: DIRECT AUTHORIZATION SWITCH -->
        <div class="card-footer">
          <div class="auth-toggle-group">
            <span class="auth-toggle-label">授权状态:</span>
            <el-switch
              :model-value="app.status === 'ENABLED'"
              active-text="已授权"
              inactive-text="未授权"
              inline-prompt
              :loading="catalogLoading"
              @change="(val: any) => handleToggleAppAuthorization(app, Boolean(val))"
            />
          </div>

          <div class="card-action-links">
            <el-button
              v-if="app.status === 'ENABLED'"
              type="primary"
              link
              size="small"
              @click="navigateToApp(app)"
            >
              进入应用 <el-icon class="ml-1"><ArrowRight /></el-icon>
            </el-button>
            <el-button
              v-else
              type="primary"
              size="small"
              plain
              @click="handleToggleAppAuthorization(app, true)"
            >
              直接授权
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- Self-service Subscribe / Trial Modal -->
    <el-dialog
      v-model="subscribeDialogVisible"
      :title="`开通/升级应用【${currentApp?.appName}】`"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mb-4"
          title="商业化授权生效说明"
          description="开通后系统将立即刷新租户 Redis 特权缓存。对应菜单将对租户开放，租户管理员可进一步在 IAM 中授权给下属角色。"
        />

        <el-form-item label="开通应用编码">
          <el-input :model-value="subscribeForm.appCode" disabled />
        </el-form-item>

        <el-form-item label="订阅 / 试用期限">
          <el-radio-group v-model="subscribeForm.trialDays">
            <el-radio :label="7">7 天体验</el-radio>
            <el-radio :label="14">14 天标准试用</el-radio>
            <el-radio :label="30">30 天全功能测试</el-radio>
            <el-radio :label="365">1 年商业授权</el-radio>
            <el-radio :label="0">永久授权 (不过期)</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="开通备注 / 商务单号">
          <el-input
            v-model="subscribeForm.remark"
            type="textarea"
            :rows="3"
            placeholder="填写商务合同单号、开通原因或支持人员姓名..."
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="subscribeDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="subscribing"
          @click="handleSubscribe"
        >
          确认开通生效
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tenant-app-store-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.target-tenant-hero-card {
  padding: 18px 24px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
}

.hero-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.hero-title-group {
  max-width: 680px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hero-main-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.hero-badge {
  font-size: 12px;
}

.hero-subtitle {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.hero-actions-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.enterprise-selector-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 16px 0;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 10px;
  border: 1px solid var(--el-color-primary-light-8);
}

.selector-field {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.selector-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  display: flex;
  align-items: center;
}

.tenant-primary-select {
  width: 420px;
}

.tenant-option-row {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: space-between;
  width: 100%;
}

.option-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.option-code {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-family: monospace;
}

.active-tenant-meta-pills {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.meta-pill {
  font-size: 12px;
}

.isolation-notice-strip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  background: var(--el-color-primary-light-9);
  border-left: 4px solid var(--el-color-primary);
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 16px;
}

.notice-icon {
  color: var(--el-color-primary);
  margin-top: 2px;
  flex-shrink: 0;
}

.notice-text {
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-color-primary-dark-2);
}

.stats-row {
  display: flex;
  align-items: center;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
  padding: 12px 20px;
  margin-bottom: 16px;
  gap: 24px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}

.stat-num {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.stat-item.total .stat-num {
  color: var(--el-color-primary);
}

.stat-item.enabled .stat-num {
  color: var(--el-color-success);
}

.stat-item.trial .stat-num {
  color: var(--el-color-warning);
}

.stat-item.disabled .stat-num {
  color: var(--el-text-color-placeholder);
}

.stat-item.expired .stat-num {
  color: var(--el-color-danger);
}

.stat-divider {
  width: 1px;
  height: 28px;
  background: var(--el-border-color-lighter);
}

.filter-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.filter-inputs {
  display: flex;
  gap: 12px;
  align-items: center;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 20px;
}

.app-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  transition: all 0.25s ease;
  position: relative;
  overflow: hidden;
}

.app-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgb(0 0 0 / 0.08);
  border-color: var(--el-color-primary-light-5);
}

.app-card.status-enabled {
  border-top: 4px solid var(--el-color-success);
}

.app-card.status-trial {
  border-top: 4px solid var(--el-color-warning);
}

.app-card.status-expired {
  border-top: 4px solid var(--el-color-danger);
}

.app-card.status-disabled {
  border-top: 4px solid var(--el-border-color);
}

.card-header {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.app-icon-wrapper {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.icon-system,
.icon-core {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.icon-collaboration {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
}

.icon-business {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.icon-infra,
.icon-dev {
  background: var(--el-color-info-light-9);
  color: var(--el-color-info);
}

.icon-security {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.icon-integration {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
}

.app-title-box {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.app-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.app-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.category-tag {
  font-size: 11px;
}

.app-code-badge {
  font-size: 11px;
  font-family: monospace;
  color: var(--el-text-color-secondary);
}

.app-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  margin: 14px 0;
  min-height: 42px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.entitlement-meta {
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
  font-size: 12px;
}

.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.meta-label {
  color: var(--el-text-color-secondary);
}

.plan-included-text {
  color: var(--el-color-success);
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
}

.addon-text {
  color: var(--el-text-color-placeholder);
  display: flex;
  align-items: center;
  gap: 4px;
}

.card-footer {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.auth-toggle-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.auth-toggle-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.card-action-links {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mr-1 {
  margin-right: 4px;
}

.ml-1 {
  margin-left: 4px;
}

.mb-4 {
  margin-bottom: 16px;
}

.text-primary {
  color: var(--el-color-primary);
}
</style>
