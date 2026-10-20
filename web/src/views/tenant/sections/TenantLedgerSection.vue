<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  ArrowDown,
  Calendar,
  Check,
  CircleCheck,
  Close,
  DataAnalysis,
  Edit,
  Files,
  Monitor,
  OfficeBuilding,
  Operation,
  Plus,
  Refresh,
  Switch,
  Timer,
  User,
  Warning
} from "@element-plus/icons-vue";
import {
  fetchTenantPackages,
  fetchTenantQuotas,
  updateTenant,
  updateTenantPlan,
  updateTenantStatus
} from "@/api/modules/tenant";
import type {
  TenantPackageRow,
  TenantQuotaOverview,
  TenantRow
} from "@/api/modules/tenant";
import ProTable, { type ProTableColumn } from "@/components/pro/ProTable.vue";
import SchemaForm, { type SchemaField } from "@/components/pro/SchemaForm.vue";
import { useTenantStore } from "@/stores/modules/tenant";

const props = defineProps<{
  query: Record<string, string>;
  schema: SchemaField[];
  columns: ProTableColumn[];
  rows: TenantRow[];
  loading: boolean;
  resetQuery: () => void;
  loadData: () => Promise<void> | void;
  openCreateDialog: () => void;
  openImportDialog: () => void;
}>();

const queryModel = computed({
  get: () => props.query,
  set: (value) => Object.assign(props.query, value)
});

// Plan & Expiration Dialog
const planDialogVisible = ref(false);
const planSubmitting = ref(false);
const activeTenant = ref<TenantRow | null>(null);
const packages = ref<TenantPackageRow[]>([]);
const planForm = reactive({
  packageId: 1,
  expireAt: "",
  remark: ""
});

// Edit Tenant Basic Info Dialog
const editDialogVisible = ref(false);
const editSubmitting = ref(false);
const editTenantId = ref<number | null>(null);
const editForm = reactive({
  tenantCode: "",
  tenantName: "",
  packageId: 1,
  status: "ACTIVE",
  isolationMode: "SHARED_SCHEMA",
  expireAt: "",
  contactName: "",
  contactMobile: "",
  contactEmail: "",
  remark: ""
});

function openEditTenantDialog(row: TenantRow) {
  editTenantId.value = row.id;
  editForm.tenantCode = row.tenantCode;
  editForm.tenantName = row.tenantName;
  editForm.packageId = row.packageId || 1;
  editForm.status = row.status || "ACTIVE";
  editForm.isolationMode = row.isolationMode || "SHARED_SCHEMA";
  editForm.expireAt = row.expireAt ? row.expireAt.substring(0, 10) : "";
  editForm.contactName = row.contactName || "";
  editForm.contactMobile = row.contactMobile || "";
  editForm.contactEmail = row.contactEmail || "";
  editForm.remark = row.remark || "";
  editDialogVisible.value = true;
}

async function handleConfirmEdit() {
  if (!editForm.tenantName.trim()) {
    ElMessage.warning("企业/租户全称不得为空白");
    return;
  }
  if (!editTenantId.value) return;
  editSubmitting.value = true;
  try {
    await updateTenant(editTenantId.value, {
      tenantCode: editForm.tenantCode.trim(),
      tenantName: editForm.tenantName.trim(),
      packageId: editForm.packageId,
      status: editForm.status,
      isolationMode: editForm.isolationMode,
      expireAt: editForm.expireAt ? `${editForm.expireAt} 23:59:59` : undefined,
      contactName: editForm.contactName.trim() || undefined,
      contactMobile: editForm.contactMobile.trim() || undefined,
      contactEmail: editForm.contactEmail.trim() || undefined,
      remark: editForm.remark.trim() || undefined
    });
    ElMessage.success(`租户「${editForm.tenantName}」基础档案更新成功！`);
    editDialogVisible.value = false;
    await props.loadData();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "更新租户基础档案失败");
  } finally {
    editSubmitting.value = false;
  }
}

function handleMoreCommand(cmd: string, row: TenantRow) {
  if (cmd === "plan") {
    openPlanRenewDialog(row);
  } else if (cmd === "quota") {
    openQuotaDrawer(row);
  } else if (cmd === "toggleStatus") {
    handleToggleStatus(row);
  }
}

// Quota Drawer
const quotaDrawerVisible = ref(false);
const quotaLoading = ref(false);
const quotaData = ref<TenantQuotaOverview | null>(null);

async function loadPackages() {
  try {
    packages.value = await fetchTenantPackages();
  } catch {
    // ignore
  }
}

function openPlanRenewDialog(row: TenantRow) {
  activeTenant.value = row;
  planForm.packageId = row.packageId || 1;
  planForm.expireAt = row.expireAt ? row.expireAt.substring(0, 10) : "";
  planForm.remark = "";
  planDialogVisible.value = true;
}

function applyQuickExpire(days: number) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  planForm.expireAt = d.toISOString().substring(0, 10);
}

async function handleConfirmPlanRenew() {
  if (!activeTenant.value) return;
  planSubmitting.value = true;
  try {
    await updateTenantPlan(activeTenant.value.id, {
      packageId: planForm.packageId,
      expireAt: planForm.expireAt ? `${planForm.expireAt} 23:59:59` : undefined,
      remark: planForm.remark
    });
    ElMessage.success(`租户「${activeTenant.value.tenantName}」套餐变配与续期已生效！`);
    planDialogVisible.value = false;
    await props.loadData();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "更新租户套餐失败");
  } finally {
    planSubmitting.value = false;
  }
}

async function openQuotaDrawer(row: TenantRow) {
  activeTenant.value = row;
  quotaDrawerVisible.value = true;
  quotaLoading.value = true;
  try {
    quotaData.value = await fetchTenantQuotas(row.id);
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "加载租户配额画像失败");
  } finally {
    quotaLoading.value = false;
  }
}

const tenantStore = useTenantStore();

function handleSwitchTenant(row: TenantRow) {
  tenantStore.setCurrentTenant({
    id: row.id,
    code: row.tenantCode,
    name: row.tenantName,
    plan: row.packageName || "ENTERPRISE",
    isolationMode: row.isolationMode
  });
  ElMessage.success(`已切换当前工作空间至「${row.tenantName} (${row.tenantCode})」！`);
}

async function handleToggleStatus(row: TenantRow) {
  const nextStatus = row.status === "DISABLED" ? "ACTIVE" : "DISABLED";
  try {
    if (nextStatus === "DISABLED") {
      const { value } = await ElMessageBox.prompt(
        `请输入停用/冻结租户「${row.tenantName}」的处置原因与审批工单号：`,
        "合规处置确认",
        {
          confirmButtonText: "确认执行停用",
          cancelButtonText: "取消",
          inputPlaceholder: "如: 合同到期 / 欠费催缴未付 / 安全违规处置",
          inputValidator: (val) => Boolean(val && val.trim()) || "请务必填写停用处置依据"
        }
      );
      await updateTenantStatus(row.id, nextStatus);
      ElMessage.success(`租户已停用，处置依据「${value}」已记入安全审计留痕！`);
    } else {
      await ElMessageBox.confirm(
        `确定启用恢复租户「${row.tenantName} (${row.tenantCode})」吗？`,
        "确认恢复租户",
        { type: "success", confirmButtonText: "确认恢复", cancelButtonText: "取消" }
      );
      await updateTenantStatus(row.id, nextStatus);
      ElMessage.success(`租户已启用恢复正常服务！`);
    }
    await props.loadData();
  } catch {
    // cancelled
  }
}

function getPackageTagType(name?: string): "success" | "warning" | "danger" | "info" {
  if (!name) return "info";
  if (name.includes("旗舰") || name.includes("SUPREME")) return "danger";
  if (name.includes("企业") || name.includes("ENTERPRISE")) return "success";
  if (name.includes("专业") || name.includes("PRO")) return "warning";
  return "info";
}

onMounted(() => {
  loadPackages();
});
</script>

<template>
  <section class="module-registry-shell">
    <div class="module-registry-shell__header">
      <div>
        <span class="module-registry-shell__eyebrow">ENTERPRISE SAAS TENANT CONTROL</span>
        <h3>租户全生命周期台账</h3>
        <p>集中掌控全平台多租户订购套餐、账号席位配额水位、履约到期倒计时与隔离模式。</p>
      </div>
    </div>

    <SchemaForm v-model="queryModel" :schema="schema">
      <template #actions>
        <el-button @click="resetQuery">重置</el-button>
        <el-button type="primary" @click="loadData">查询</el-button>
      </template>
    </SchemaForm>

    <ProTable
      title="租户全量台账"
      subtitle="实时透视租户订阅套餐、到期倒计时预警、行级隔离防护与实时席位使用率。"
      :loading="loading"
      :columns="columns"
      :data="rows"
      selectable
      empty-title="当前没有租户台账"
      empty-description="可以先新建租户，或通过导入清单一次性导入租户基础档案。"
    >
      <template #toolbar>
        <el-button
          v-button-permission="{ code: 'tenant:import', fallback: 'tenant:write' }"
          @click="openImportDialog"
        >
          批量导入
        </el-button>
        <el-button
          v-button-permission="{ code: 'tenant:create', fallback: 'tenant:write' }"
          type="primary"
          @click="openCreateDialog"
        >
          新建租户
        </el-button>
      </template>

      <!-- Custom Column Slots -->
      <template #package="{ row }">
        <el-tag :type="getPackageTagType(row.packageName)" effect="plain" class="package-pill">
          {{ row.packageName || "未分配" }}
        </el-tag>
      </template>

      <template #expireAt="{ row }">
        <div class="expire-cell">
          <span class="expire-date">{{ row.expireAt ? row.expireAt.substring(0, 10) : "永久有效" }}</span>
          <template v-if="row.daysRemaining != null">
            <el-tag v-if="row.daysRemaining < 0" size="small" type="danger" effect="dark" class="days-badge">
              逾期 {{ Math.abs(row.daysRemaining) }} 天
            </el-tag>
            <el-tag v-else-if="row.daysRemaining <= 7" size="small" type="warning" effect="dark" class="days-badge">
              剩 {{ row.daysRemaining }} 天到期
            </el-tag>
            <span v-else class="days-remaining-normal">
              剩 {{ row.daysRemaining }} 天
            </span>
          </template>
        </div>
      </template>

      <template #status="{ row }">
        <div class="lifecycle-status-cell">
          <DictTag
            :value="row.status"
            :options="[
              { label: '正常履约', value: 'ACTIVE', tagType: 'success' },
              { label: '试用体验', value: 'TRIAL', tagType: 'primary' },
              { label: '临期预警', value: 'EXPIRING', tagType: 'warning' },
              { label: '到期只读', value: 'EXPIRED', tagType: 'danger' },
              { label: '欠费冻结', value: 'SUSPENDED', tagType: 'danger' },
              { label: '已停用', value: 'DISABLED', tagType: 'info' }
            ]"
            round
          />
        </div>
      </template>

      <template #actions="{ row }">
        <div class="table-action-links">
          <el-button link type="primary" :icon="Switch" @click="handleSwitchTenant(row)">
            切入
          </el-button>
          <el-button link type="primary" :icon="Edit" @click="openEditTenantDialog(row)">
            编辑
          </el-button>
          <el-dropdown trigger="click" @command="(cmd: string) => handleMoreCommand(cmd, row)">
            <el-button link type="primary" class="more-action-btn">
              更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="plan" :icon="Operation">变配与续期</el-dropdown-item>
                <el-dropdown-item command="quota" :icon="DataAnalysis">配额与画像</el-dropdown-item>
                <el-dropdown-item
                  divided
                  command="toggleStatus"
                  :icon="row.status === 'DISABLED' ? CircleCheck : Warning"
                  :class="row.status === 'DISABLED' ? 'menu-item-success' : 'menu-item-danger'"
                >
                  {{ row.status === 'DISABLED' ? '恢复启用' : '合规停用' }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </template>
    </ProTable>

    <!-- Edit Tenant Basic Info Dialog -->
    <el-dialog
      v-model="editDialogVisible"
      :title="`编辑租户空间档案 —「${editForm.tenantName}」`"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form :model="editForm" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="租户编码">
              <el-input :model-value="editForm.tenantCode" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="企业/租户全称" required>
              <el-input v-model="editForm.tenantName" placeholder="如 某某科技股份有限公司" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="生命周期状态">
              <el-select v-model="editForm.status" style="width: 100%">
                <el-option label="正常履约 (ACTIVE)" value="ACTIVE" />
                <el-option label="试用体验 (TRIAL)" value="TRIAL" />
                <el-option label="已停用 (DISABLED)" value="DISABLED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="隔离模式">
              <el-select v-model="editForm.isolationMode" style="width: 100%">
                <el-option label="共享 Schema (行级隔离)" value="SHARED_SCHEMA" />
                <el-option label="独立 Schema (表级隔离)" value="ISOLATED_SCHEMA" />
                <el-option label="独立数据库 (物理隔离)" value="DEDICATED_DATABASE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="主联系人姓名">
              <el-input v-model="editForm.contactName" placeholder="企业对接人姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人手机号码">
              <el-input v-model="editForm.contactMobile" placeholder="联系手机号码" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="联系人电子邮箱">
          <el-input v-model="editForm.contactEmail" placeholder="企业通知邮箱" />
        </el-form-item>

        <el-form-item label="备注说明">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" placeholder="商务签约背景或补充说明..." />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleConfirmEdit">
          保存租户档案
        </el-button>
      </template>
    </el-dialog>

    <!-- Plan & Expiration Dialog -->
    <el-dialog
      v-model="planDialogVisible"
      :title="`租户变配与履约续期 —「${activeTenant?.tenantName}」`"
      width="540px"
      :close-on-click-modal="false"
    >
      <el-form :model="planForm" label-position="top">
        <el-form-item label="当前租户">
          <el-input :model-value="`${activeTenant?.tenantName} (${activeTenant?.tenantCode})`" disabled />
        </el-form-item>

        <el-form-item label="调整订阅套餐" required>
          <el-select v-model="planForm.packageId" style="width: 100%">
            <el-option
              v-for="p in packages"
              :key="p.id"
              :label="`${p.packageName} (${p.packageCode}) · 席位: ${p.maxUserCount}席 · 存储: ${(p.maxStorageMb/1024).toFixed(0)}GB`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="到期时间设定">
          <el-date-picker
            v-model="planForm.expireAt"
            type="date"
            placeholder="选择到期日期..."
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
          <div class="quick-expire-bar">
            <span>快捷延期：</span>
            <el-button size="small" text type="primary" @click="applyQuickExpire(30)">+1个月</el-button>
            <el-button size="small" text type="primary" @click="applyQuickExpire(90)">+3个月</el-button>
            <el-button size="small" text type="primary" @click="applyQuickExpire(365)">+1年</el-button>
            <el-button size="small" text type="info" @click="planForm.expireAt = ''">永久有效</el-button>
          </div>
        </el-form-item>

        <el-form-item label="变配与延期备注">
          <el-input
            v-model="planForm.remark"
            type="textarea"
            :rows="2"
            placeholder="记录合同编号、客户付费依据或续期审批单号..."
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="planDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="planSubmitting" @click="handleConfirmPlanRenew">
          确认生效变配
        </el-button>
      </template>
    </el-dialog>

    <!-- Quota Overview Drawer -->
    <el-drawer
      v-model="quotaDrawerVisible"
      title="租户资源配额健康度画像"
      size="560px"
      :destroy-on-close="true"
    >
      <div v-loading="quotaLoading" class="quota-drawer-content">
        <!-- Tenant Identity Header -->
        <div class="tenant-quota-header">
          <div class="quota-tenant-title">
            <span class="tenant-name">{{ quotaData?.tenantName }}</span>
            <el-tag size="small" effect="plain" class="code-tag">{{ quotaData?.tenantCode }}</el-tag>
          </div>
          <div class="quota-tenant-sub">
            <span>当前套餐：<strong class="pkg-name">{{ quotaData?.packageName }}</strong></span>
            <span>到期时间：{{ quotaData?.expireAt }}</span>
          </div>
        </div>

        <!-- Quota Progress Bars -->
        <div class="quota-list-section">
          <div class="section-title">核心维度配额实时水位与熔断阈值</div>
          <div class="quota-card-list">
            <div v-for="q in quotaData?.quotas" :key="q.quotaCode" class="quota-card">
              <div class="quota-card-header">
                <span class="quota-name">
                  <el-icon v-if="q.quotaCode === 'USER_COUNT'"><User /></el-icon>
                  <el-icon v-else-if="q.quotaCode === 'DEPT_COUNT'"><OfficeBuilding /></el-icon>
                  <el-icon v-else-if="q.quotaCode === 'STORAGE_MB'"><Files /></el-icon>
                  <el-icon v-else><Monitor /></el-icon>
                  {{ q.quotaName }}
                </span>
                <span class="quota-values">
                  <strong class="used-val">{{ q.usedValue.toLocaleString() }}</strong> / {{ q.quotaValue.toLocaleString() }}
                  <span class="unit-span">{{ q.quotaCode === 'STORAGE_MB' ? 'MB' : (q.quotaCode === 'API_PER_MINUTE' ? '次/分' : '') }}</span>
                </span>
              </div>

              <div class="progress-bar-wrapper">
                <el-progress
                  :percentage="q.usagePercent"
                  :status="q.status === 'DANGER' ? 'exception' : (q.status === 'WARNING' ? 'warning' : 'success')"
                  :stroke-width="10"
                />
              </div>

              <div class="quota-card-hint">
                <span v-if="q.status === 'DANGER'" class="danger-hint">
                  <el-icon><Warning /></el-icon> 已触发硬拦截熔断水位，无法继续创建该资源！
                </span>
                <span v-else-if="q.status === 'WARNING'" class="warn-hint">
                  配额使用已超 80%，建议跟进客户升级套餐。
                </span>
                <span v-else class="normal-hint">
                  配额充足，运行平稳。
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped lang="scss">
.package-pill {
  font-weight: 500;
  font-size: 12px;
}

.expire-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;

  .expire-date {
    font-size: 13px;
    color: #1e293b;
    font-family: ui-monospace, monospace;
  }

  .days-badge {
    align-self: flex-start;
  }

  .days-remaining-normal {
    font-size: 11px;
    color: #059669;
  }
}

.lifecycle-status-cell {
  .status-chip {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-weight: 500;

    .chip-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;

      &.is-green { background: #10b981; }
      &.is-blue { background: #3b82f6; }
      &.is-amber { background: #f59e0b; }
      &.is-red { background: #ef4444; }
    }
  }
}

.table-action-links {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.more-action-btn {
  display: inline-flex;
  align-items: center;
  font-size: 13px;
  padding: 0 4px;
}

.menu-item-success {
  color: #10b981 !important;
}

.menu-item-danger {
  color: #ef4444 !important;
}

.quick-expire-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
}

.quota-drawer-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.tenant-quota-header {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 16px;

  .quota-tenant-title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;

    .tenant-name {
      font-size: 17px;
      font-weight: 700;
      color: #0f172a;
    }

    .code-tag {
      font-family: ui-monospace, monospace;
    }
  }

  .quota-tenant-sub {
    display: flex;
    align-items: center;
    gap: 16px;
    font-size: 13px;
    color: #64748b;

    .pkg-name {
      color: #2563eb;
    }
  }
}

.quota-list-section {
  .section-title {
    font-size: 13px;
    font-weight: 600;
    color: #334155;
    margin-bottom: 12px;
  }

  .quota-card-list {
    display: flex;
    flex-direction: column;
    gap: 14px;

    .quota-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      padding: 14px 16px;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);

      .quota-card-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 10px;

        .quota-name {
          font-size: 14px;
          font-weight: 600;
          color: #1e293b;
          display: flex;
          align-items: center;
          gap: 6px;
        }

        .quota-values {
          font-size: 13px;
          color: #64748b;
          font-family: ui-monospace, monospace;

          .used-val {
            color: #0f172a;
            font-size: 15px;
          }

          .unit-span {
            font-size: 11px;
            color: #94a3b8;
          }
        }
      }

      .progress-bar-wrapper {
        margin-bottom: 8px;
      }

      .quota-card-hint {
        font-size: 12px;

        .danger-hint {
          color: #ef4444;
          font-weight: 500;
          display: flex;
          align-items: center;
          gap: 4px;
        }

        .warn-hint {
          color: #d97706;
        }

        .normal-hint {
          color: #10b981;
        }
      }
    }
  }
}
</style>
