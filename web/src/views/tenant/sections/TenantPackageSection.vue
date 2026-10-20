<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Box,
  Check,
  Close,
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
  createTenantPackage,
  deleteTenantPackage,
  fetchTenantPackages,
  updateTenantPackage
} from "@/api/modules/tenant";
import type { TenantPackageRow, TenantPackageSavePayload } from "@/api/modules/tenant";
import BaseCard from "@/components/base/BaseCard.vue";

const loading = ref(false);
const saving = ref(false);
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
  { label: "身份与组织基础 (IAM & Org)", value: "saas.iam.basic" },
  { label: "审批工作流引擎 (Workflow)", value: "saas.workflow.basic" },
  { label: "对象存储与文件中心 (File Center)", value: "saas.file.basic" },
  { label: "动态任务调度引擎 (Scheduler)", value: "saas.scheduler.basic" },
  { label: "安全合规与操作审计 (Audit Trail)", value: "saas.audit.basic" },
  { label: "智能代码生成工程 (Codegen)", value: "saas.codegen.basic" },
  { label: "集成中心与网关连接 (Integration)", value: "saas.integration.basic" }
];

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
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "加载套餐配置失败");
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
  form.featureFlagsJson = JSON.stringify(["saas.iam.basic", "saas.org.basic"]);
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
  form.featureFlagsJson = pkg.featureFlagsJson || "[]";
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
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "保存套餐失败");
  } finally {
    saving.value = false;
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
    if (jsonStr.includes("*")) return ["全部系统功能全量放行 (*)"];
    const list = JSON.parse(jsonStr);
    return Array.isArray(list) ? list : [];
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

    <!-- Create / Edit Package Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? `编辑套餐「${form.packageName}」` : '新建 SaaS 订阅套餐'"
      width="640px"
      :close-on-click-modal="false"
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
    </el-dialog>
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
  padding: 8px 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 2px rgb(0 0 0 / 0.03);

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

    .icon-blue { color: #2563eb; }
    .icon-green { color: #059669; }
    .icon-purple { color: #7c3aed; }
    .icon-amber { color: #d97706; }

    .label {
      font-size: 13px;
      color: #64748b;
    }

    .value {
      font-size: 13px;
      font-weight: 600;
      color: #0f172a;

      &.tag-active {
        color: #059669;
        background: #ecfdf5;
        padding: 2px 8px;
        border-radius: 4px;
        font-size: 12px;
      }
    }
  }

  .divider {
    width: 1px;
    height: 14px;
    background: #e2e8f0;
  }
}

.package-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  padding: 12px 18px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;

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
    background: #fff;
    border: 1px solid #e2e8f0;
    border-radius: 14px;
    padding: 20px;
    display: flex;
    flex-direction: column;
    box-shadow: 0 1px 3px rgb(0 0 0 / 0.04);
    transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 24px rgb(0 0 0 / 0.08);
      border-color: #cbd5e1;
    }

    &.is-supreme {
      border-top: 4px solid #7c3aed;
    }

    &.is-enterprise {
      border-top: 4px solid #2563eb;
    }

    &.is-pro {
      border-top: 4px solid #059669;
    }

    &.is-basic {
      border-top: 4px solid #64748b;
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
          color: #0f172a;
        }

        .plan-code-tag {
          font-family: ui-monospace, monospace;
          font-size: 11px;
        }
      }
    }

    .plan-desc {
      font-size: 12px;
      color: #64748b;
      line-height: 1.5;
      min-height: 36px;
      margin-bottom: 16px;
    }

    .plan-specs-box {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
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
          color: #64748b;
          display: flex;
          align-items: center;
          gap: 6px;
        }

        .spec-val {
          color: #1e293b;
          font-weight: 600;
          font-family: ui-monospace, monospace;
        }
      }
    }

    .plan-features-box {
      flex: 1;
      margin-bottom: 16px;

      .features-label {
        font-size: 11px;
        font-weight: 600;
        color: #94a3b8;
        margin-bottom: 8px;
        text-transform: uppercase;
      }

      .features-tags {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;

        .feature-tag {
          font-size: 11px;
          background: #eff6ff;
          color: #2563eb;
          border-color: #bfdbfe;
        }

        .no-feature {
          font-size: 12px;
          color: #94a3b8;
        }
      }
    }

    .plan-card-footer {
      border-top: 1px solid #f1f5f9;
      padding-top: 14px;
      display: flex;
      align-items: center;
      justify-content: space-between;

      .subscriber-badge {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 12px;
        color: #64748b;

        strong {
          color: #0f172a;
          font-weight: 700;
        }
      }

      .plan-actions {
        display: flex;
        gap: 8px;
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
