<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
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
  OfficeBuilding,
  PriceTag,
  Refresh,
  Search,
  SetUp,
  Shop,
  Switch,
  Timer,
  Warning
} from "@element-plus/icons-vue";
import {
  fetchTenantAppCatalog,
  subscribeTenantApp,
  updateTenantAppStatus,
  fetchTenantPage
} from "@/api/modules/tenant";
import type {
  TenantAppCatalogItem,
  TenantAppSubscribePayload,
  TenantRow
} from "@/api/modules/tenant";
import BaseCard from "@/components/base/BaseCard.vue";

const router = useRouter();

const props = defineProps<{
  tenantId?: number;
  runtimePolicy?: any;
  packagePolicy?: any;
  isolationPolicy?: any;
  isolationModeLabelMap?: any;
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
const tenants = ref<TenantRow[]>([]);
const selectedTenantId = ref<number | undefined>(props.tenantId);

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

const categoryOptions = [
  { label: "全部应用", value: "ALL" },
  { label: "核心底座", value: "SYSTEM" },
  { label: "协同引擎", value: "COLLABORATION" },
  { label: "业务中台", value: "BUSINESS" },
  { label: "基础工具", value: "INFRA" }
];

const categoryLabelMap: Record<string, string> = {
  SYSTEM: "核心底座",
  COLLABORATION: "协同引擎",
  BUSINESS: "业务中台",
  INFRA: "基础工具"
};

const categoryTagTypeMap: Record<string, "primary" | "success" | "warning" | "info"> = {
  SYSTEM: "primary",
  COLLABORATION: "warning",
  BUSINESS: "success",
  INFRA: "info"
};

const tierLabelMap: Record<string, string> = {
  BASIC: "基础版",
  PROFESSIONAL: "专业版",
  ENTERPRISE: "企业版",
  SUPREME: "至尊版"
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
  system: "/system/dicts"
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
  SetUp
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
      selectedCategory.value === "ALL" || item.category === selectedCategory.value;

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
    const [catalogData, tenantData] = await Promise.all([
      fetchTenantAppCatalog(selectedTenantId.value),
      tenants.value.length === 0 ? fetchTenantPage().catch(() => []) : Promise.resolve(tenants.value)
    ]);
    catalog.value = catalogData;
    if (tenantData.length > 0) {
      tenants.value = tenantData;
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "获取应用目录失败");
  } finally {
    catalogLoading.value = false;
  }
}

function handleTenantChange() {
  fetchCatalog();
}

function getAppIcon(iconName: string) {
  return iconComponentMap[iconName] || Cpu;
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

async function handleQuickTrial(app: TenantAppCatalogItem) {
  try {
    await ElMessageBox.confirm(
      `确定为当前租户立即开通【${app.appName}】的 14 天免费全功能体验吗？`,
      "一键开通体验",
      {
        confirmButtonText: "立即体验",
        cancelButtonText: "取消",
        type: "success"
      }
    );
    catalogLoading.value = true;
    await subscribeTenantApp(
      {
        appCode: app.appCode,
        trialDays: 14,
        remark: "PLG 自助一键开通 14 天试用"
      },
      selectedTenantId.value
    );
    ElMessage.success(`【${app.appName}】体验已就绪，可直接进入！`);
    await fetchCatalog();
  } catch (e: any) {
    if (e !== "cancel") {
      ElMessage.error(e?.response?.data?.message ?? "试用开通失败");
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

onMounted(() => {
  fetchCatalog();
});
</script>

<template>
  <div class="tenant-app-store-section">
    <!-- Header Control Bar -->
    <BaseCard class="control-card">
      <div class="header-wrapper">
        <div class="header-left">
          <div class="title-row">
            <span class="main-title">应用矩阵与商业化订阅中心</span>
            <el-tag type="primary" effect="plain" round class="badge-tag">
              双层授权体系 · PLG 体验流
            </el-tag>
          </div>
          <p class="subtitle">
            严格隔离套餐商业化开通（平台层）与角色菜单分配（租户层），未订阅应用物理切断直接 API 调用并隐藏界面入口。
          </p>
        </div>

        <div class="header-right">
          <div v-if="tenants.length > 0" class="tenant-picker">
            <span class="picker-label">查看目标租户:</span>
            <el-select
              v-model="selectedTenantId"
              placeholder="当前工作空间租户"
              clearable
              filterable
              class="tenant-select"
              @change="handleTenantChange"
            >
              <el-option
                v-for="t in tenants"
                :key="t.id"
                :label="`${t.tenantName} (${t.tenantCode}) · [${t.packageName || '默认'}]`"
                :value="t.id"
              />
            </el-select>
          </div>
          <el-button :icon="Refresh" circle :loading="catalogLoading" @click="fetchCatalog" />
        </div>
      </div>

      <!-- Stat Badges Row -->
      <div class="stats-row">
        <div class="stat-item total">
          <span class="stat-num">{{ stats.total }}</span>
          <span class="stat-label">矩阵应用数</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-item enabled">
          <span class="stat-num">{{ stats.enabled }}</span>
          <span class="stat-label">套餐/永久订阅</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-item trial">
          <span class="stat-num">{{ stats.trial }}</span>
          <span class="stat-label">免费试用中</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-item disabled">
          <span class="stat-num">{{ stats.disabled }}</span>
          <span class="stat-label">未开通应用</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-item expired">
          <span class="stat-num">{{ stats.expired }}</span>
          <span class="stat-label">已过期</span>
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
          <el-select v-model="selectedStatus" placeholder="状态筛选" style="width: 130px">
            <el-option label="全部状态" value="ALL" />
            <el-option label="已开通 (有效)" value="ENABLED" />
            <el-option label="免费试用中" value="TRIAL" />
            <el-option label="未订阅" value="DISABLED" />
            <el-option label="已过期" value="EXPIRED" />
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
              <el-icon class="mr-1"><CircleCheck /></el-icon> 已开通
            </el-tag>
            <el-tag
              v-else-if="app.status === 'TRIAL'"
              type="warning"
              effect="dark"
              round
              size="small"
            >
              <el-icon class="mr-1"><Timer /></el-icon> 试用中
            </el-tag>
            <el-tag
              v-else-if="app.status === 'EXPIRED'"
              type="danger"
              effect="plain"
              round
              size="small"
            >
              <el-icon class="mr-1"><Warning /></el-icon> 已到期
            </el-tag>
            <el-tag
              v-else
              type="info"
              effect="plain"
              round
              size="small"
            >
              未订阅
            </el-tag>
          </div>
        </div>

        <!-- Description -->
        <p class="app-desc">{{ app.description }}</p>

        <!-- Entitlement Context Pills -->
        <div class="entitlement-meta">
          <div class="meta-row">
            <span class="meta-label">准入套餐档位:</span>
            <el-tag size="small" type="primary" effect="light">
              {{ tierLabelMap[app.requiredTier] || app.requiredTier }} 及以上
            </el-tag>
          </div>

          <div class="meta-row">
            <span class="meta-label">授权源属性:</span>
            <span v-if="app.isIncludedInPlan" class="plan-included-text">
              <el-icon><Check /></el-icon> 租户基础套餐内包含
            </span>
            <span v-else-if="app.isTrial" class="trial-included-text">
              <el-icon><Clock /></el-icon> 独立试用包 (剩余 {{ app.daysRemaining ?? 0 }} 天)
            </span>
            <span v-else class="addon-text">
              需独立订阅或升级套餐
            </span>
          </div>

          <!-- Trial Countdown Bar -->
          <div v-if="app.isTrial && app.daysRemaining !== null" class="trial-bar-wrapper">
            <div class="trial-bar-label">
              <span>试用有效期履约进度</span>
              <span>剩余 {{ app.daysRemaining }} 天</span>
            </div>
            <el-progress
              :percentage="Math.min(100, Math.max(5, Math.round(((app.daysRemaining ?? 0) / 14) * 100)))"
              :status="(app.daysRemaining ?? 0) < 3 ? 'exception' : 'warning'"
              :stroke-width="6"
              :show-text="false"
            />
          </div>
        </div>

        <!-- Card Footer Actions -->
        <div class="card-footer">
          <template v-if="app.status === 'ENABLED'">
            <el-button
              type="primary"
              class="action-btn"
              @click="navigateToApp(app)"
            >
              进入应用 <el-icon class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
          </template>

          <template v-else-if="app.status === 'TRIAL'">
            <el-button
              type="warning"
              class="action-btn"
              @click="navigateToApp(app)"
            >
              进入试用 <el-icon class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
            <el-button
              type="primary"
              plain
              size="default"
              @click="openSubscribeDialog(app, 365)"
            >
              正式开通
            </el-button>
          </template>

          <template v-else>
            <el-button
              type="success"
              class="action-btn"
              @click="handleQuickTrial(app)"
            >
              14 天免费体验
            </el-button>
            <el-button
              type="primary"
              plain
              @click="openSubscribeDialog(app)"
            >
              开通订阅
            </el-button>
          </template>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <el-empty
      v-if="!catalogLoading && filteredCatalog.length === 0"
      description="没有匹配的应用服务"
      :image-size="120"
    />

    <!-- Subscribe / Trial Dialog -->
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

.control-card {
  padding: 8px 12px;
}

.header-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.header-left {
  max-width: 680px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.main-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.tenant-picker {
  display: flex;
  align-items: center;
  gap: 8px;
}

.picker-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.tenant-select {
  width: 280px;
}

.stats-row {
  display: flex;
  align-items: center;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 12px 20px;
  margin: 16px 0;
  gap: 24px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}

.stat-num {
  font-size: 20px;
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
  margin-top: 8px;
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
  border-top: 3px solid var(--el-color-success);
}

.app-card.status-trial {
  border-top: 3px solid var(--el-color-warning);
}

.app-card.status-expired {
  border-top: 3px solid var(--el-color-danger);
}

.app-card.status-disabled {
  border-top: 3px solid var(--el-border-color);
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

.icon-system {
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

.icon-infra {
  background: var(--el-color-info-light-9);
  color: var(--el-color-info);
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

.trial-included-text {
  color: var(--el-color-warning);
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
}

.addon-text {
  color: var(--el-text-color-placeholder);
}

.trial-bar-wrapper {
  margin-top: 4px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.trial-bar-label {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.card-footer {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 10px;
}

.action-btn {
  flex: 1;
}

.mr-1 {
  margin-right: 4px;
}

.mb-4 {
  margin-bottom: 16px;
}
</style>
