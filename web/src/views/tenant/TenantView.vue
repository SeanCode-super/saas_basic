<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import { createTenant, fetchTenantPackages, fetchTenantPage } from "@/api/modules/tenant";
import type { TenantPackageRow, TenantRow } from "@/api/modules/tenant";
import BaseCard from "@/components/base/BaseCard.vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import BaseRefreshButton from "@/components/base/BaseRefreshButton.vue";
import ModuleSectionNav from "@/components/platform/ModuleSectionNav.vue";
import RegistryImportDialog from "@/components/platform/RegistryImportDialog.vue";
import ModuleWorkbench from "@/components/platform/ModuleWorkbench.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
import { useDict } from "@/hooks/useDict";
import { useModuleSection } from "@/hooks/useModuleSection";
import { formatJsonManifest, parseJsonManifest } from "@/utils/manifest-import";
import TenantIsolationSection from "./sections/TenantIsolationSection.vue";
import TenantLedgerSection from "./sections/TenantLedgerSection.vue";
import TenantOnboardingSection from "./sections/TenantOnboardingSection.vue";
import TenantOverviewSection from "./sections/TenantOverviewSection.vue";
import TenantPackageSection from "./sections/TenantPackageSection.vue";
import TenantAppStoreSection from "./sections/TenantAppStoreSection.vue";

const props = withDefaults(
  defineProps<{
    section?: string;
  }>(),
  {
    section: ""
  }
);

const tenantStatusOptions = useDict("tenant_status");
const loading = ref(false);
const submitting = ref(false);
const importing = ref(false);
const createVisible = ref(false);
const importVisible = ref(false);
const importContent = ref("");
const rows = ref<TenantRow[]>([]);
const availablePackages = ref<TenantPackageRow[]>([]);
const query = reactive({
  keyword: "",
  status: ""
});
const form = reactive({
  tenantCode: "",
  tenantName: "",
  packageId: 1,
  status: "ACTIVE",
  isolationMode: "SHARED_SCHEMA",
  expireAt: "",
  contactName: "",
  contactMobile: "",
  remark: ""
});
const runtimePolicy = reactive({
  autoOpenConsole: true,
  trialDays: 30,
  defaultLocale: "zh-CN",
  allowSelfService: false,
  tenantApprovalMode: "MANUAL"
});
const packagePolicy = reactive({
  defaultPackage: "SUPREME",
  storageQuotaGb: 100,
  seatQuota: 500,
  apiThrottle: 200000,
  featureReleaseMode: "GRADUAL"
});
const isolationPolicy = reactive({
  platformIsolation: "PLATFORM",
  businessIsolation: "SHARED_SCHEMA",
  financeIsolation: "DEDICATED_DATABASE",
  allowUpgradeIsolation: true,
  enforceReadWriteSplit: false
});

const packageLabelMap: Record<string, string> = {
  SUPREME: "至尊版",
  ENTERPRISE: "企业版",
  PROFESSIONAL: "专业版",
  BASIC: "基础版",
  "至尊版": "至尊版"
};

const isolationModeLabelMap: Record<string, string> = {
  PLATFORM: "平台模式",
  SHARED_SCHEMA: "共享 Schema",
  ISOLATED_SCHEMA: "独立 Schema",
  DEDICATED_DATABASE: "独立数据库"
};

const sectionItems = [
  { key: "overview", label: "总览", description: "查看租户底座的治理蓝图、运行信号和租户画像。" },
  { key: "apps", label: "租户应用授权", description: "顶级应用权限管控，授予或回收租户应用权限；未获授权的应用在菜单管理、角色分配及导航中完全隐形。" },
  { key: "onboarding", label: "开通治理", description: "统一注册、审批、试用期与控制台开通基线。" },
  { key: "package", label: "套餐配额", description: "管理默认套餐、席位、存储和流量配额模型。" },
  { key: "isolation", label: "隔离升级", description: "规划共享、独库与高隔离租户的升级路径。" },
  { key: "ledger", label: "租户台账", description: "查看并管理租户清单、状态和隔离模式。" }
];

const { activeSection, updateSection } = useModuleSection(sectionItems, {
  mode: "path",
  basePath: "/tenant",
  fallback: "overview"
});

const currentSection = computed(() => props.section || activeSection.value);
const sectionLocked = computed(() => Boolean(props.section));
const isOverviewSection = computed(() => currentSection.value === "overview");
const activeSectionMeta = computed(
  () => sectionItems.find((item) => item.key === currentSection.value) ?? sectionItems[0]
);

const summary = computed(() => {
  const total = rows.value.length;
  const enabled = rows.value.filter((item) => item.status === "ENABLED").length;
  const trial = rows.value.filter((item) => item.status === "TRIAL").length;
  const dedicated = rows.value.filter((item) => item.isolationMode === "DEDICATED_DATABASE").length;
  return [
    { label: "租户总数", value: total, note: "已纳入底座治理" },
    { label: "启用租户", value: enabled, note: "当前运行中的租户" },
    { label: "试用租户", value: trial, note: "尚在试用期内" },
    { label: "独库租户", value: dedicated, note: "高隔离模式租户" }
  ];
});

const tenantSpotlight = computed(() => [
  {
    label: "开通模式",
    value: runtimePolicy.tenantApprovalMode === "MANUAL" ? "人工审批" : "自动开通"
  },
  {
    label: "试用窗口",
    value: `${runtimePolicy.trialDays} 天`
  },
  {
    label: "默认套餐",
    value: packageLabelMap[packagePolicy.defaultPackage] ?? packagePolicy.defaultPackage
  },
  {
    label: "最高隔离",
    value: isolationModeLabelMap[isolationPolicy.financeIsolation] ?? isolationPolicy.financeIsolation
  }
]);

const operatingBlueprint = [
  { title: "开通治理", description: "决定谁能注册、谁需要审批、试用期多久，以及是否自动开通控制台。" },
  { title: "套餐配额", description: "定义默认套餐、席位、存储、API 限流和功能发布方式。" },
  { title: "隔离升级", description: "平台、业务、财务三类数据按不同隔离级别治理，并保留升级路径。" },
  { title: "租户台账", description: "统一看租户编码、状态、套餐和隔离模式，不把台账当成唯一入口。" }
];

const columns: ProTableColumn[] = [
  { prop: "tenantCode", label: "租户编码", minWidth: 120 },
  { prop: "tenantName", label: "租户名称", minWidth: 180 },
  {
    prop: "packageName",
    label: "订阅套餐",
    minWidth: 120,
    slot: "package"
  },
  {
    prop: "expireAt",
    label: "履约到期 / 倒计时",
    minWidth: 170,
    slot: "expireAt"
  },
  {
    prop: "isolationMode",
    label: "隔离模式",
    minWidth: 130,
    formatter: (row) => isolationModeLabelMap[String(row.isolationMode ?? "")] ?? String(row.isolationMode ?? "--")
  },
  { prop: "status", label: "生命周期状态", minWidth: 120, slot: "status" },
  { prop: "actions", label: "操作", minWidth: 180, fixed: "right", slot: "actions" }
];

const schema = computed(() => [
  { field: "keyword", label: "关键词", component: "input" as const, placeholder: "请输入租户编码或租户名称" },
  { field: "status", label: "状态", component: "select" as const, placeholder: "请选择状态", options: tenantStatusOptions.value }
]);

const filteredRows = computed(() =>
  rows.value.filter((item) => {
    const keyword = query.keyword.trim().toLowerCase();
    const matchesKeyword = !keyword || item.tenantCode.toLowerCase().includes(keyword) || item.tenantName.toLowerCase().includes(keyword);
    const matchesStatus = !query.status || item.status === query.status;
    return matchesKeyword && matchesStatus;
  })
);

const sectionComponentMap = {
  overview: TenantOverviewSection,
  apps: TenantAppStoreSection,
  onboarding: TenantOnboardingSection,
  package: TenantPackageSection,
  isolation: TenantIsolationSection,
  ledger: TenantLedgerSection
} as const;

const tenantImportSample = formatJsonManifest([
  {
    tenantCode: "northwind",
    tenantName: "北风工业",
    packageId: 1,
    status: "TRIAL",
    isolationMode: "SHARED_SCHEMA",
    remark: "示例租户"
  }
]);

const currentSectionComponent = computed(
  () => sectionComponentMap[currentSection.value as keyof typeof sectionComponentMap] ?? TenantOverviewSection
);

async function loadData() {
  loading.value = true;
  try {
    const [tenants, pkgs] = await Promise.all([
      fetchTenantPage(),
      fetchTenantPackages().catch(() => [])
    ]);
    rows.value = tenants;
    availablePackages.value = pkgs;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  query.keyword = "";
  query.status = "";
}

function openCreateDialog() {
  form.tenantCode = "";
  form.tenantName = "";
  form.packageId = availablePackages.value[0]?.id || 1;
  form.status = "ACTIVE";
  form.isolationMode = "SHARED_SCHEMA";
  const d = new Date();
  d.setDate(d.getDate() + 365);
  form.expireAt = d.toISOString().substring(0, 10);
  form.contactName = "";
  form.contactMobile = "";
  form.remark = "";
  createVisible.value = true;
}

function openImportDialog() {
  importContent.value = "";
  importVisible.value = true;
}

async function handleCreate() {
  if (!form.tenantCode?.trim()) {
    ElMessage.warning("请输入租户编码");
    return;
  }
  if (!form.tenantName?.trim()) {
    ElMessage.warning("请输入企业/租户全称");
    return;
  }
  submitting.value = true;
  try {
    await createTenant({
      tenantCode: form.tenantCode.trim(),
      tenantName: form.tenantName.trim(),
      packageId: form.packageId,
      status: form.status,
      isolationMode: form.isolationMode,
      expireAt: form.expireAt ? `${form.expireAt} 23:59:59` : undefined,
      contactName: form.contactName?.trim() || undefined,
      contactMobile: form.contactMobile?.trim() || undefined,
      remark: form.remark
    });
    createVisible.value = false;
    ElMessage.success("租户已创建，初始配额基线已就绪！");
    await loadData();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "创建租户失败");
  } finally {
    submitting.value = false;
  }
}

async function handleImport() {
  importing.value = true;
  try {
    const manifest = parseJsonManifest<Array<Record<string, unknown>>[number]>(importContent.value);
    if (!manifest.length) {
      throw new Error("导入清单不能为空数组");
    }

    let success = 0;
    const errors: string[] = [];

    for (const [index, item] of manifest.entries()) {
      try {
        await createTenant({
          tenantCode: String(item.tenantCode ?? ""),
          tenantName: String(item.tenantName ?? ""),
          packageId: Number(item.packageId ?? 1),
          status: String(item.status ?? "TRIAL"),
          isolationMode: String(item.isolationMode ?? "SHARED_SCHEMA"),
          remark: item.remark ? String(item.remark) : undefined
        });
        success += 1;
      } catch (error: any) {
        errors.push(`第 ${index + 1} 条：${error?.response?.data?.message ?? error?.message ?? "导入失败"}`);
      }
    }

    importVisible.value = false;
    await loadData();
    ElMessage.success(`租户清单导入完成，成功 ${success} 条${errors.length ? `，失败 ${errors.length} 条` : ""}`);
    if (errors.length) {
      ElMessage.warning(errors.slice(0, 3).join("；"));
    }
  } catch (error: any) {
    ElMessage.error(error?.message ?? "解析导入清单失败");
  } finally {
    importing.value = false;
  }
}

function savePolicy(message: string) {
  ElMessage.success(message);
}

onMounted(loadData);
</script>

<template>
  <ControlSurface
    eyebrow="租户中心"
    :title="activeSectionMeta.label"
    :description="activeSectionMeta.description"
  >
    <template #actions>
      <BaseRefreshButton :loading="loading" @click="loadData" />
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建租户空间</el-button>
    </template>

    <div class="module-section-stack">
      <ModuleSectionNav
        v-if="!sectionLocked"
        :model-value="currentSection"
        :items="sectionItems"
        @update:model-value="updateSection"
      />

      <component
        :is="currentSectionComponent"
        :runtime-policy="runtimePolicy"
        :package-policy="packagePolicy"
        :isolation-policy="isolationPolicy"
        :isolation-mode-label-map="isolationModeLabelMap"
        :query="query"
        :schema="schema"
        :columns="columns"
        :rows="filteredRows"
        :loading="loading"
        :reset-query="resetQuery"
        :load-data="loadData"
        :open-create-dialog="openCreateDialog"
        :open-import-dialog="openImportDialog"
        :save-policy="savePolicy"
        @navigate-section="updateSection"
      />
    </div>
  </ControlSurface>

  <el-dialog v-model="createVisible" title="新建租户空间" width="580px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="租户编码" required>
              <el-input v-model="form.tenantCode" placeholder="英文小写标识，如 demo_corp" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="企业/租户全称" required>
              <el-input v-model="form.tenantName" placeholder="如 某某科技股份有限公司" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="订阅套餐档位" required>
          <el-select v-model="form.packageId" placeholder="选择预设套餐计划" style="width: 100%">
            <el-option
              v-for="p in availablePackages"
              :key="p.id"
              :label="`${p.packageName} (${p.packageCode}) · 席位上限: ${p.maxUserCount}席 · 存储: ${(p.maxStorageMb/1024).toFixed(0)}GB`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="初始生命周期状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="正常履约 (ACTIVE)" value="ACTIVE" />
                <el-option label="试用体验 (TRIAL)" value="TRIAL" />
                <el-option label="预先停用 (DISABLED)" value="DISABLED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物理/逻辑隔离模式">
              <el-select v-model="form.isolationMode" style="width: 100%">
                <el-option label="共享 Schema (行级隔离)" value="SHARED_SCHEMA" />
                <el-option label="独立 Schema (表级隔离)" value="ISOLATED_SCHEMA" />
                <el-option label="独立数据库 (物理隔离)" value="DEDICATED_DATABASE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="合同/试用到期时间">
          <el-date-picker
            v-model="form.expireAt"
            type="date"
            placeholder="默认开通 1 年，可自定义设定..."
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="主联系人姓名">
              <el-input v-model="form.contactName" placeholder="选填，企业对接人姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人手机号码">
              <el-input v-model="form.contactMobile" placeholder="选填，手机号码" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注说明">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="商务背景、客户主体或开通批注..." />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">确认创建租户</el-button>
      </template>
  </el-dialog>

  <RegistryImportDialog
    v-model:visible="importVisible"
    v-model:content="importContent"
    title="导入租户台账"
    description="使用 JSON 数组导入租户基础档案。系统会按清单逐条创建，并返回成功/失败结果。"
    :sample="tenantImportSample"
    :loading="importing"
    @confirm="handleImport"
  />
</template>
