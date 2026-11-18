<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Box,
  Check,
  Delete,
  Edit,
  Files,
  Monitor,
  OfficeBuilding,
  Plus,
  Refresh,
  Star,
  User
} from "@element-plus/icons-vue";
import {
  batchAuthorizeTenantApps,
  createTenantPackage,
  deleteTenantPackage,
  fetchTenantPackages,
  updateTenantPackage
} from "@/api/modules/tenant";
import type {
  TenantPackageRow,
  TenantPackageSavePayload
} from "@/api/modules/tenant";
import { useAuthStore } from "@/stores/modules/auth";
import WorkspacePanel from "@/components/platform/WorkspacePanel.vue";

const authStore = useAuthStore();
const loading = ref(false);
const saving = ref(false);
const authorizingId = ref<number | null>(null);
const packages = ref<TenantPackageRow[]>([]);
const searchKeyword = ref("");
const dialogVisible = ref(false);
const isEditing = ref(false);
const currentEditId = ref<number | null>(null);

const form = reactive<TenantPackageSavePayload>({
  packageCode: "",
  packageName: "",
  packageType: "STANDARD",
  status: "ENABLED",
  maxUserCount: 100,
  maxDeptCount: 20,
  maxStorageMb: 10240,
  maxApiPerMinute: 5000,
  featureFlagsJson: "[]",
  sortNo: 10,
  remark: ""
});

const featureOptions = [
  { label: "统一身份与权限中心 (iam)", value: "iam" },
  { label: "多租户与计量中心 (tenant)", value: "tenant" },
  { label: "系统中心与企业 SSO (system)", value: "system" },
  { label: "进销存 ERP 供应链 (erp)", value: "erp" },
  { label: "客户关系管理 CRM (crm)", value: "crm" },
  { label: "可视化审批工作流 (workflow)", value: "workflow" },
  { label: "分布式文件存储中心 (file)", value: "file" },
  { label: "开放集成与智能中心 (integration)", value: "integration" },
  { label: "分布式任务调度中心 (scheduler)", value: "scheduler" },
  { label: "低代码与代码生成中心 (codegen)", value: "codegen" },
  { label: "合规审计与风控中心 (audit)", value: "audit" },
  { label: "报表设计与套打中心 (report)", value: "report" }
];

const FEATURE_LABEL_MAP: Record<string, string> = {
  iam: "身份权限 (iam)",
  tenant: "租户计量 (tenant)",
  system: "系统中心 (system)",
  organization: "身份权限 (iam)",
  erp: "ERP 中台 (erp)",
  crm: "CRM 中台 (crm)",
  workflow: "工作流 (workflow)",
  ai_center: "开放与 AI (integration)",
  file: "文件中心 (file)",
  integration: "集成与 AI (integration)",
  scheduler: "任务调度 (scheduler)",
  codegen: "代码生成 (codegen)",
  audit: "审计中心 (audit)",
  report: "报表套打 (report)",
  report_print: "报表套打 (report)",
  "saas.iam.basic": "身份权限 (iam)",
  "saas.org.basic": "身份权限 (iam)",
  "saas.workflow.basic": "工作流 (workflow)",
  "saas.file.basic": "文件中心 (file)",
  "saas.scheduler.basic": "任务调度 (scheduler)",
  "saas.audit.basic": "审计中心 (audit)",
  "saas.codegen.basic": "代码生成 (codegen)",
  "saas.integration.basic": "集成中心 (integration)"
};

const LEGACY_APP_CODE_MAP: Record<string, string> = {
  organization: "iam",
  ai_center: "integration",
  report_print: "report",
  "saas.iam.basic": "iam",
  "saas.org.basic": "iam",
  "saas.workflow.basic": "workflow",
  "saas.file.basic": "file",
  "saas.scheduler.basic": "scheduler",
  "saas.audit.basic": "audit",
  "saas.codegen.basic": "codegen",
  "saas.integration.basic": "integration"
};

function resolveErrorMessage(error: unknown, fallback: string): string {
  if (typeof error === "object" && error !== null) {
    const maybe = error as { response?: { data?: { message?: string } }; message?: string };
    return maybe.response?.data?.message || maybe.message || fallback;
  }
  return fallback;
}

const selectedFeatures = computed<string[]>({
  get() {
    try {
      if (!form.featureFlagsJson) return [];
      const parsed = JSON.parse(form.featureFlagsJson);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  },
  set(val: string[]) {
    form.featureFlagsJson = JSON.stringify(val);
  }
});

const filteredPackages = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase();
  if (!kw) return packages.value;
  return packages.value.filter(
    (p) =>
      p.packageName.toLowerCase().includes(kw) ||
      p.packageCode.toLowerCase().includes(kw) ||
      (p.remark && p.remark.toLowerCase().includes(kw))
  );
});

const totalActiveTenants = computed(() =>
  packages.value.reduce((acc, p) => acc + (p.activeTenantCount || 0), 0)
);

const maxUserCeiling = computed(() =>
  Math.max(...packages.value.map((p) => p.maxUserCount || 0), 0)
);

async function loadPackages() {
  loading.value = true;
  try {
    packages.value = await fetchTenantPackages();
  } catch (error: unknown) {
    ElMessage.error(resolveErrorMessage(error, "加载套餐配置失败"));
  } finally {
    loading.value = false;
  }
}

function openCreateDialog() {
  isEditing.value = false;
  currentEditId.value = null;
  form.packageCode = "";
  form.packageName = "";
  form.packageType = "STANDARD";
  form.status = "ENABLED";
  form.maxUserCount = 200;
  form.maxDeptCount = 20;
  form.maxStorageMb = 20480;
  form.maxApiPerMinute = 10000;
  form.featureFlagsJson = JSON.stringify(["iam", "workflow", "file"]);
  form.sortNo = (packages.value.length + 1) * 10;
  form.remark = "";
  dialogVisible.value = true;
}

function openEditDialog(pkg: TenantPackageRow) {
  isEditing.value = true;
  currentEditId.value = pkg.id;
  form.packageCode = pkg.packageCode;
  form.packageName = pkg.packageName;
  form.packageType = pkg.packageType;
  form.status = pkg.status;
  form.maxUserCount = pkg.maxUserCount;
  form.maxDeptCount = pkg.maxDeptCount;
  form.maxStorageMb = pkg.maxStorageMb;
  form.maxApiPerMinute = pkg.maxApiPerMinute;
  const normalizedFlags = resolveCanonicalAppCodes(pkg.featureFlagsJson);
  form.featureFlagsJson = JSON.stringify(normalizedFlags);
  form.sortNo = pkg.sortNo;
  form.remark = pkg.remark || "";
  dialogVisible.value = true;
}

async function handleSavePackage() {
  if (!form.packageCode.trim() || !form.packageName.trim()) {
    ElMessage.warning("请填写完整的套餐编码和套餐名称");
    return;
  }
  saving.value = true;
  try {
    if (isEditing.value && currentEditId.value) {
      await updateTenantPackage(currentEditId.value, form);
      ElMessage.success(`套餐「${form.packageName}」更新成功`);
    } else {
      await createTenantPackage(form);
      ElMessage.success(`套餐「${form.packageName}」创建成功`);
    }
    dialogVisible.value = false;
    await loadPackages();
  } catch (error: unknown) {
    ElMessage.error(resolveErrorMessage(error, "保存套餐失败"));
  } finally {
    saving.value = false;
  }
}

function resolveCanonicalAppCodes(jsonStr?: string): string[] {
  if (!jsonStr) return [];
  const allowed = new Set(featureOptions.map((item) => item.value));
  if (jsonStr.includes("*")) {
    return featureOptions.map((item) => item.value);
  }
  try {
    const raw = JSON.parse(jsonStr);
    if (!Array.isArray(raw)) return [];
    const normalized = raw
      .map((entry) => {
        const key = String(entry).trim();
        return LEGACY_APP_CODE_MAP[key] || key;
      })
      .filter((key) => allowed.has(key));
    return [...new Set(normalized)];
  } catch {
    return [];
  }
}

async function handleSyncPackageApps(pkg: TenantPackageRow) {
  const appCodes = resolveCanonicalAppCodes(pkg.featureFlagsJson);
  if (!appCodes.length) {
    ElMessage.warning(`套餐「${pkg.packageName}」尚未配置任何能力应用编码，请先编辑勾选。`);
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确定将套餐「${pkg.packageName}」包含的 ${appCodes.length} 项应用（${appCodes.join(", ")}）下发授权到当前租户空间吗？`,
      "下发套餐应用授权",
      { type: "info", confirmButtonText: "确认下发", cancelButtonText: "取消" }
    );
    authorizingId.value = pkg.id;
    await batchAuthorizeTenantApps(
      {
        appCodes,
        enabled: true,
        remark: `按订阅套餐「${pkg.packageName} (${pkg.packageCode})」批量下发应用授权`
      },
      authStore.currentUser?.tenantId || undefined
    );
    ElMessage.success(`已为当前租户下发 ${appCodes.length} 项应用授权`);
  } catch (error: unknown) {
    if (error === "cancel" || error === "close") {
      return;
    }
    ElMessage.error(resolveErrorMessage(error, "下发应用授权失败"));
  } finally {
    authorizingId.value = null;
  }
}

async function handleDeletePackage(pkg: TenantPackageRow) {
  if (pkg.activeTenantCount && pkg.activeTenantCount > 0) {
    ElMessage.warning(`该套餐仍有 ${pkg.activeTenantCount} 家活跃租户正在使用，不可删除`);
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确定删除套餐「${pkg.packageName} (${pkg.packageCode})」吗？删除后不可恢复。`,
      "确认删除套餐",
      { type: "warning", confirmButtonText: "确定删除", cancelButtonText: "取消" }
    );
    await deleteTenantPackage(pkg.id);
    ElMessage.success("套餐已成功删除");
    await loadPackages();
  } catch {
    // cancelled
  }
}

function formatStorage(mb: number): string {
  if (mb >= 1024) {
    return `${(mb / 1024).toFixed(0)} GB`;
  }
  return `${mb} MB`;
}

function parseFeatures(jsonStr?: string): string[] {
  try {
    if (!jsonStr) return [];
    if (jsonStr.includes("*")) return ["全部系统应用全量放行 (*)"];
    const list = JSON.parse(jsonStr);
    if (!Array.isArray(list)) return [];
    return list.map((item) => {
      const code = String(item);
      return FEATURE_LABEL_MAP[code] ?? code;
    });
  } catch {
    return [];
  }
}

onMounted(() => {
  loadPackages();
});
</script>

<template>
  <div class="tenant-package-workbench" v-loading="loading">
    <!-- Header Telemetry Strip (40px) -->
    <div class="package-metric-strip">
      <div class="strip-left">
        <div class="metric-item">
          <el-icon class="icon-blue"><Box /></el-icon>
          <span class="label">套餐规格</span>
          <span class="value">{{ packages.length }} 档</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-green"><User /></el-icon>
          <span class="label">席位上限</span>
          <span class="value">{{ maxUserCeiling.toLocaleString() }} 席</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-purple"><OfficeBuilding /></el-icon>
          <span class="label">订阅租户</span>
          <span class="value">{{ totalActiveTenants }} 家</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-amber"><Star /></el-icon>
          <span class="label">配额硬拦截</span>
          <span class="value tag-active">熔断生效</span>
        </div>
      </div>
    </div>

    <!-- Toolbar -->
    <div class="package-toolbar">
      <div class="toolbar-left">
        <el-input
          v-model="searchKeyword"
          placeholder="按套餐名称、编码或说明搜索..."
          clearable
          style="width: 280px"
        />
        <el-button :icon="Refresh" @click="loadPackages">刷新</el-button>
      </div>
      <div class="toolbar-right">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建套餐</el-button>
      </div>
    </div>

    <!-- Package Cards Grid -->
    <div class="package-cards-grid">
      <div
        v-for="pkg in filteredPackages"
        :key="pkg.id"
        class="plan-card"
        :class="{
          'is-supreme': pkg.packageCode === 'SUPREME',
          'is-enterprise': pkg.packageCode === 'ENTERPRISE',
          'is-pro': pkg.packageCode === 'PROFESSIONAL',
          'is-basic': pkg.packageCode === 'BASIC'
        }"
      >
        <div class="plan-card-header">
          <div class="plan-title-box">
            <span class="plan-name">{{ pkg.packageName }}</span>
            <el-tag size="small" effect="plain" class="plan-code-tag">{{ pkg.packageCode }}</el-tag>
          </div>
          <el-tag :type="pkg.status === 'ENABLED' ? 'success' : 'info'" size="small">
            {{ pkg.status === 'ENABLED' ? '正常开放' : '已停用' }}
          </el-tag>
        </div>

        <p class="plan-desc">{{ pkg.remark || "暂无套餐描述说明" }}</p>

        <!-- Quota Matrix Specs -->
        <div class="plan-specs-box">
          <div class="spec-row">
            <span class="spec-label"><el-icon><User /></el-icon> 账号席位</span>
            <strong class="spec-val">{{ pkg.maxUserCount.toLocaleString() }} 席</strong>
          </div>
          <div class="spec-row">
            <span class="spec-label"><el-icon><OfficeBuilding /></el-icon> 部门上限</span>
            <strong class="spec-val">{{ pkg.maxDeptCount.toLocaleString() }} 个</strong>
          </div>
          <div class="spec-row">
            <span class="spec-label"><el-icon><Files /></el-icon> 存储空间</span>
            <strong class="spec-val">{{ formatStorage(pkg.maxStorageMb) }}</strong>
          </div>
          <div class="spec-row">
            <span class="spec-label"><el-icon><Monitor /></el-icon> API每分钟限流</span>
            <strong class="spec-val">{{ pkg.maxApiPerMinute.toLocaleString() }} 次/分</strong>
          </div>
        </div>

        <!-- Feature List -->
        <div class="plan-features-box">
          <div class="features-label">包含能力包：</div>
          <div class="features-tags">
            <el-tag
              v-for="(f, idx) in parseFeatures(pkg.featureFlagsJson)"
              :key="idx"
              size="small"
              class="feature-tag"
            >
              {{ f }}
            </el-tag>
            <span v-if="!parseFeatures(pkg.featureFlagsJson).length" class="no-feature">基础平台能力</span>
          </div>
        </div>

        <!-- Footer Bar -->
        <div class="plan-card-footer">
          <div class="subscriber-badge">
            <el-icon><Check /></el-icon>
            <span>已订购 <strong>{{ pkg.activeTenantCount || 0 }}</strong> 家租户</span>
          </div>
          <div class="plan-actions">
            <el-button
              link
              type="success"
              :icon="Check"
              :loading="authorizingId === pkg.id"
              @click="handleSyncPackageApps(pkg)"
            >
              下发应用授权
            </el-button>
            <el-button link type="primary" :icon="Edit" @click="openEditDialog(pkg)">编辑</el-button>
            <el-button
              link
              type="danger"
              :icon="Delete"
              :disabled="Boolean(pkg.activeTenantCount && pkg.activeTenantCount > 0)"
              @click="handleDeletePackage(pkg)"
            >
              删除
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- Create / Edit Package Workspace Panel -->
    <WorkspacePanel
      v-model="dialogVisible"
      :title="isEditing ? `编辑套餐「${form.packageName}」` : '新建 SaaS 订阅套餐'"
      width="680px"
    >
      <el-form :model="form" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="套餐编码" required>
              <el-input
                v-model="form.packageCode"
                placeholder="如 BASIC, PRO, ENTERPRISE"
                :disabled="isEditing"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="套餐名称" required>
              <el-input v-model="form.packageName" placeholder="如 基础体验版、专业成长版" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="套餐定位类型">
              <el-select v-model="form.packageType" style="width: 100%">
                <el-option label="标准通用型 (STANDARD)" value="STANDARD" />
                <el-option label="中大企业型 (ENTERPRISE)" value="ENTERPRISE" />
                <el-option label="平台旗舰型 (PLATFORM)" value="PLATFORM" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开放状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="正常开放 (ENABLED)" value="ENABLED" />
                <el-option label="暂停订阅 (DISABLED)" value="DISABLED" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">配额硬拦截设定</el-divider>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="最大账号席位 (User Quota)" required>
              <el-input-number v-model="form.maxUserCount" :min="1" :max="1000000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大组织部门 (Dept Quota)" required>
              <el-input-number v-model="form.maxDeptCount" :min="1" :max="100000" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="存储空间容量 (MB)" required>
              <el-input-number v-model="form.maxStorageMb" :min="100" :max="10485760" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="API每分钟请求限流 (Throttle)" required>
              <el-input-number v-model="form.maxApiPerMinute" :min="100" :max="10000000" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">功能权限包范围</el-divider>

        <el-form-item label="勾选允许开通的功能模块">
          <el-checkbox-group v-model="selectedFeatures" class="feature-checkboxes">
            <el-checkbox
              v-for="item in featureOptions"
              :key="item.value"
              :value="item.value"
              :label="item.value"
            >
              {{ item.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-form-item label="排序权重">
          <el-input-number v-model="form.sortNo" :min="0" :max="999" />
        </el-form-item>

        <el-form-item label="套餐说明与权益介绍">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="说明此套餐适用的企业规模、 SLA 服务等级及专享特权..."
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSavePackage">
          确认保存套餐
        </el-button>
      </template>
    </WorkspacePanel>
  </div>
</template>

<style scoped lang="scss">
.tenant-package-workbench {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.package-metric-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background: var(--sb-tier1-bg);
  backdrop-filter: var(--sb-glass-blur-md);
  -webkit-backdrop-filter: var(--sb-glass-blur-md);
  border-radius: var(--sb-radius-glass-md, 14px);
  border: 1px solid var(--sb-border-subtle);
  box-shadow: var(--sb-shadow-xs);

  .strip-left {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
  }

  .metric-item {
    display: flex;
    align-items: center;
    gap: 8px;

    .icon-blue { color: var(--sb-primary-color); }
    .icon-green { color: #10b981; }
    .icon-purple { color: #8b5cf6; }
    .icon-amber { color: #f59e0b; }

    .label {
      font-size: 13px;
      color: var(--sb-text-muted);
    }

    .value {
      font-size: 13px;
      font-weight: 600;
      color: var(--sb-text-primary);

      &.tag-active {
        color: #10b981;
        background: rgba(16, 185, 129, 0.14);
        padding: 2px 8px;
        border-radius: 4px;
        font-size: 12px;
      }
    }
  }

  .divider {
    width: 1px;
    height: 14px;
    background: var(--sb-border-subtle);
  }
}

.package-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--sb-tier1-bg);
  backdrop-filter: var(--sb-glass-blur-md);
  -webkit-backdrop-filter: var(--sb-glass-blur-md);
  padding: 12px 20px;
  border: 1px solid var(--sb-border-subtle);
  border-radius: var(--sb-radius-glass-md, 14px);
  box-shadow: var(--sb-shadow-xs);

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

.package-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;

  .plan-card {
    background: var(--sb-tier1-bg);
    backdrop-filter: var(--sb-glass-blur-md);
    -webkit-backdrop-filter: var(--sb-glass-blur-md);
    border: 1px solid var(--sb-border-subtle);
    border-radius: var(--sb-radius-glass, 16px);
    padding: 22px;
    display: flex;
    flex-direction: column;
    box-shadow: var(--sb-shadow-xs);
    transition: transform 0.2s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.2s ease, border-color 0.2s ease;

    &:hover {
      transform: translateY(-2px);
      background: var(--sb-tier2-bg);
      box-shadow: var(--sb-shadow-sm);
      border-color: var(--sb-primary-color);
    }

    &.is-supreme {
      border-top: 4px solid #7c3aed;
    }

    &.is-enterprise {
      border-top: 4px solid var(--sb-primary-color);
    }

    &.is-pro {
      border-top: 4px solid #10b981;
    }

    &.is-basic {
      border-top: 4px solid var(--sb-text-muted);
    }

    .plan-card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 8px;

      .plan-title-box {
        display: flex;
        align-items: center;
        gap: 8px;

        .plan-name {
          font-size: 17px;
          font-weight: 700;
          color: var(--sb-text-primary);
        }

        .plan-code-tag {
          font-family: var(--sb-font-mono);
          font-size: 11px;
        }
      }
    }

    .plan-desc {
      font-size: 12px;
      color: var(--sb-text-muted);
      line-height: 1.5;
      min-height: 36px;
      margin-bottom: 16px;
    }

    .plan-specs-box {
      background: var(--sb-tier2-bg);
      border: 1px solid var(--sb-border-subtle);
      border-radius: 12px;
      padding: 12px 14px;
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin-bottom: 16px;

      .spec-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-size: 13px;

        .spec-label {
          color: var(--sb-text-muted);
          display: flex;
          align-items: center;
          gap: 6px;
        }

        .spec-val {
          color: var(--sb-text-primary);
          font-weight: 600;
          font-family: var(--sb-font-mono);
        }
      }
    }

    .plan-features-box {
      flex: 1;
      margin-bottom: 16px;

      .features-label {
        font-size: 11px;
        font-weight: 600;
        color: var(--sb-text-muted);
        margin-bottom: 8px;
        text-transform: uppercase;
      }

      .features-tags {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;

        .feature-tag {
          font-size: 11px;
          background: var(--sb-badge-primary-bg);
          color: var(--sb-badge-primary-text);
          border-color: var(--sb-badge-primary-border);
        }

        .no-feature {
          font-size: 12px;
          color: var(--sb-text-muted);
        }
      }
    }

    .plan-card-footer {
      border-top: 1px solid var(--sb-border-subtle);
      padding-top: 14px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;

      .subscriber-badge {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 12px;
        color: var(--sb-text-muted);

        strong {
          color: var(--sb-text-primary);
          font-weight: 700;
        }
      }

      .plan-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
      }
    }
  }
}

.feature-checkboxes {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
