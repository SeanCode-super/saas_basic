<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Check,
  Close,
  Plus
} from "@element-plus/icons-vue";
import { createTenant } from "@/api/modules/tenant";
import { fetchSystemConfigs, upsertSystemConfigByKey } from "@/api/modules/system";
import { useAuthStore } from "@/stores/modules/auth";

const props = defineProps<{
  runtimePolicy: {
    defaultLocale: string;
    trialDays: number;
    tenantApprovalMode: string;
    autoOpenConsole: boolean;
    allowSelfService: boolean;
  };
  savePolicy: (message: string) => void;
  loadData?: () => Promise<void> | void;
}>();

interface OnboardingApplication {
  id: number;
  tenantName: string;
  tenantCode: string;
  contactName: string;
  contactMobile: string;
  packageTier: string;
  appliedAt: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  auditReason?: string;
}

const ONBOARDING_GROUP = "TENANT_GOVERNANCE";
const APPLICATIONS_KEY = "tenant.onboarding.applications";
const BASELINE_KEY = "tenant.onboarding.baseline";

const authStore = useAuthStore();
const persisting = ref(false);
const createDialogVisible = ref(false);
const createForm = reactive({
  tenantName: "",
  tenantCode: "",
  contactName: "",
  contactMobile: "",
  packageTier: "企业版 (500席位)"
});

const DEFAULT_APPLICATIONS: OnboardingApplication[] = [
  {
    id: 101,
    tenantName: "深圳前海智能科技有限公司",
    tenantCode: "qianhai_ai",
    contactName: "赵志鹏",
    contactMobile: "13912345678",
    packageTier: "企业版 (500席位)",
    appliedAt: "2026-10-17 14:32:00",
    status: "PENDING"
  },
  {
    id: 102,
    tenantName: "北京华泰金融信息服务有限责任公司",
    tenantCode: "huatai_fin",
    contactName: "李思睿",
    contactMobile: "13888889999",
    packageTier: "至尊旗舰版 (独库)",
    appliedAt: "2026-10-17 11:15:20",
    status: "PENDING"
  }
];

const applications = ref<OnboardingApplication[]>([...DEFAULT_APPLICATIONS]);

const localPolicy = reactive({
  defaultLocale: props.runtimePolicy.defaultLocale,
  trialDays: props.runtimePolicy.trialDays,
  tenantApprovalMode: props.runtimePolicy.tenantApprovalMode,
  autoOpenConsole: props.runtimePolicy.autoOpenConsole,
  allowSelfService: props.runtimePolicy.allowSelfService
});

const welcomeTemplate = reactive({
  emailSubject: "【SaaS Basics】您的企业租户空间已开通，初始管理员账号已就绪",
  smsTemplate: "【SaaS平台】尊敬的企业管理员，您的租户空间已交付，初始账号为 admin，请及时登入控制台配置组织架构。",
  webhookEndpoint: "https://api.enterprise.com/webhooks/tenant_provisioned"
});

const showPolicySettings = ref(false);

watch(
  () => props.runtimePolicy,
  (next) => {
    Object.assign(localPolicy, next);
  },
  { deep: true }
);

function resolveErrorMessage(err: unknown, fallback: string): string {
  if (typeof err === "object" && err !== null) {
    const maybe = err as { response?: { data?: { message?: string } }; message?: string };
    return maybe.response?.data?.message || maybe.message || fallback;
  }
  return fallback;
}

function getOperatorTenantId(): number {
  return authStore.currentUser?.tenantId || 1;
}

async function persistApplications() {
  await upsertSystemConfigByKey({
    tenantId: getOperatorTenantId(),
    group: ONBOARDING_GROUP,
    key: APPLICATIONS_KEY,
    name: "租户入驻开通审批工单队列",
    value: JSON.stringify(applications.value),
    valueType: "JSON",
    status: "ENABLED",
    remark: "平台租户开通审批流转持久化队列"
  });
}

async function persistBaselineConfig(successMessage: string) {
  persisting.value = true;
  try {
    await upsertSystemConfigByKey({
      tenantId: getOperatorTenantId(),
      group: ONBOARDING_GROUP,
      key: BASELINE_KEY,
      name: "租户开通基线与交付通知模版",
      value: JSON.stringify({
        runtimePolicy: { ...localPolicy },
        welcomeTemplate: { ...welcomeTemplate }
      }),
      valueType: "JSON",
      status: "ENABLED",
      remark: "SaaS 租户试用天数、审批模式及欢迎信配置"
    });
    props.savePolicy(successMessage);
  } catch (err: unknown) {
    ElMessage.error(resolveErrorMessage(err, "保存开通基线配置失败"));
  } finally {
    persisting.value = false;
  }
}

async function loadPersistedOnboardingState() {
  try {
    const configs = await fetchSystemConfigs();
    const appRow = configs.find((item) => item.group === ONBOARDING_GROUP && item.key === APPLICATIONS_KEY);
    if (appRow?.value) {
      const parsed = JSON.parse(appRow.value) as OnboardingApplication[];
      if (Array.isArray(parsed)) {
        applications.value = parsed;
      }
    }
    const baselineRow = configs.find((item) => item.group === ONBOARDING_GROUP && item.key === BASELINE_KEY);
    if (baselineRow?.value) {
      const parsed = JSON.parse(baselineRow.value) as {
        runtimePolicy?: Partial<typeof localPolicy>;
        welcomeTemplate?: Partial<typeof welcomeTemplate>;
      };
      if (parsed.runtimePolicy) {
        Object.assign(localPolicy, parsed.runtimePolicy);
      }
      if (parsed.welcomeTemplate) {
        Object.assign(welcomeTemplate, parsed.welcomeTemplate);
      }
    }
  } catch {
    // 首次未初始化时沿用默认工单队列与模版
  }
}

function openCreateApplication() {
  createForm.tenantName = "";
  createForm.tenantCode = "";
  createForm.contactName = "";
  createForm.contactMobile = "";
  createForm.packageTier = "企业版 (500席位)";
  createDialogVisible.value = true;
}

async function handleCreateApplication() {
  if (!createForm.tenantName.trim() || !createForm.tenantCode.trim()) {
    ElMessage.warning("请填写申请企业名称与拟用编码");
    return;
  }
  const now = new Date();
  const stamp = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")} ${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}:${String(now.getSeconds()).padStart(2, "0")}`;
  applications.value.unshift({
    id: Date.now(),
    tenantName: createForm.tenantName.trim(),
    tenantCode: createForm.tenantCode.trim().toLowerCase(),
    contactName: createForm.contactName.trim() || "企业管理员",
    contactMobile: createForm.contactMobile.trim() || "-",
    packageTier: createForm.packageTier,
    appliedAt: stamp,
    status: "PENDING"
  });
  try {
    await persistApplications();
    createDialogVisible.value = false;
    ElMessage.success("入驻工单已登记并持久化至待审批队列");
  } catch (err: unknown) {
    ElMessage.error(resolveErrorMessage(err, "入驻工单持久化失败"));
  }
}

async function handleApprove(app: OnboardingApplication) {
  try {
    await ElMessageBox.confirm(
      `确定审批通过租户申请「${app.tenantName} (${app.tenantCode})」吗？通过后将自动初始化租户沙箱并发送交付通知。`,
      "审批通过开通",
      { type: "success", confirmButtonText: "确认开通", cancelButtonText: "取消" }
    );
    try {
      await createTenant({
        tenantCode: app.tenantCode,
        tenantName: app.tenantName,
        packageId: 1,
        status: "ACTIVE",
        isolationMode: app.packageTier.includes("独库") ? "DEDICATED_DATABASE" : "SHARED_SCHEMA",
        contactName: app.contactName,
        contactMobile: app.contactMobile,
        remark: `开通申请审批通过交付 (申请时间: ${app.appliedAt})`
      });
      app.status = "APPROVED";
      await persistApplications();
      ElMessage.success(`租户「${app.tenantName}」审批通过，租户空间与管理员凭证已自动化完成交付！`);
      if (props.loadData) {
        await props.loadData();
      }
    } catch (err: unknown) {
      const msg = resolveErrorMessage(err, "审批开通交付失败");
      if (msg.includes("已存在")) {
        app.status = "APPROVED";
        await persistApplications();
        ElMessage.success(`租户「${app.tenantName}」空间已存在，审批状态已持久化变更为已批准！`);
      } else {
        ElMessage.error(msg);
      }
    }
  } catch {
    // cancelled
  }
}

async function handleReject(app: OnboardingApplication) {
  try {
    const { value } = await ElMessageBox.prompt(
      `请输入驳回申请「${app.tenantName}」的理由：`,
      "驳回开通申请",
      { confirmButtonText: "确认驳回", cancelButtonText: "取消", inputPlaceholder: "如: 资质信息不全 / 企业名称与税号不符" }
    );
    app.status = "REJECTED";
    app.auditReason = value || "管理员手动驳回";
    await persistApplications();
    ElMessage.warning(`申请已驳回并记录审校原因。`);
  } catch {
    // cancelled
  }
}

onMounted(() => {
  void loadPersistedOnboardingState();
});
</script>

<template>
  <div class="onboarding-container">
    <!-- Top Primary Section: Pending Approval Applications Queue -->
    <el-card shadow="never">
      <template #header>
        <div class="panel-header-flex">
          <div>
            <h3>待审批租户开通工单队列</h3>
            <p>门户或渠道提交的租户入驻申请在此进行双人合规复核与自动沙箱初始化。</p>
          </div>
          <div class="header-right-actions">
            <el-tag size="small" type="warning" effect="dark">
              待处理 {{ applications.filter(a => a.status === 'PENDING').length }} 单
            </el-tag>
            <el-button size="small" type="primary" :icon="Plus" @click="openCreateApplication">
              登记入驻申请
            </el-button>
            <el-button link type="primary" size="small" @click="showPolicySettings = !showPolicySettings">
              {{ showPolicySettings ? '收起治理基线与通知设置' : '配置开通基线与通知模板' }}
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="applications" stripe style="width: 100%">
        <el-table-column prop="appliedAt" label="申请时间" width="170" />
        <el-table-column prop="tenantName" label="申请企业名称" min-width="180" />
        <el-table-column prop="tenantCode" label="拟用编码" width="130">
          <template #default="{ row }">
            <span class="mono-code">{{ row.tenantCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="contactName" label="申请联系人" width="150">
          <template #default="{ row }">
            <span>{{ row.contactName }} ({{ row.contactMobile }})</span>
          </template>
        </el-table-column>
        <el-table-column prop="packageTier" label="申请套餐" width="160" />
        <el-table-column prop="status" label="审批状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'PENDING'" size="small" type="warning">待合规审批</el-tag>
            <el-tag v-else-if="row.status === 'APPROVED'" size="small" type="success">审批通过</el-tag>
            <el-tag v-else size="small" type="danger">已驳回</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button link type="primary" :icon="Check" @click="handleApprove(row)">通过</el-button>
              <el-button link type="danger" :icon="Close" @click="handleReject(row)">驳回</el-button>
            </template>
            <span v-else class="archived-label">
              {{ row.auditReason ? `已归档 (${row.auditReason})` : "已归档" }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Secondary Collapsible Section: Baseline Policy & Templates -->
    <el-collapse-transition>
      <div v-show="showPolicySettings" class="dual-columns mt-3">
        <!-- Left Column: Baseline Policy Form -->
        <el-card shadow="never" class="policy-card">
          <template #header>
            <div class="panel-header">
              <h3>开通治理基线</h3>
              <p>统一定义自助注册、试用时长、审批模式和控制台自动开通基线。</p>
            </div>
          </template>

          <el-form label-position="top">
            <el-form-item label="默认语言环境">
              <el-select v-model="localPolicy.defaultLocale" style="width: 100%">
                <el-option label="中文（简体）" value="zh-CN" />
                <el-option label="English" value="en-US" />
              </el-select>
            </el-form-item>
            <el-form-item label="默认试用天数">
              <el-input-number v-model="localPolicy.trialDays" :min="1" :max="365" style="width: 100%" />
            </el-form-item>
            <el-form-item label="开通审批模式">
              <el-segmented
                v-model="localPolicy.tenantApprovalMode"
                :options="[
                  { label: '人工双人审批', value: 'MANUAL' },
                  { label: '自动放行开通', value: 'AUTO' }
                ]"
              />
            </el-form-item>
            <div class="switch-stack">
              <el-form-item>
                <el-switch v-model="localPolicy.autoOpenConsole" active-text="租户创建后自动部署控制台" />
              </el-form-item>
              <el-form-item>
                <el-switch v-model="localPolicy.allowSelfService" active-text="允许门户自助在线申请租户" />
              </el-form-item>
            </div>
            <el-button
              type="primary"
              :loading="persisting"
              @click="persistBaselineConfig('租户开通治理基线策略已持久化生效')"
            >
              保存开通基线
            </el-button>
          </el-form>
        </el-card>

        <!-- Right Column: Welcome & Notification Templates -->
        <el-card shadow="never" class="policy-card">
          <template #header>
            <div class="panel-header">
              <h3>开通通知与欢迎信模板</h3>
              <p>租户完成审批交付后，系统自动发送欢迎信与 Webhook 事件。</p>
            </div>
          </template>

          <el-form label-position="top">
            <el-form-item label="交付通知邮件主题">
              <el-input v-model="welcomeTemplate.emailSubject" />
            </el-form-item>
            <el-form-item label="交付通知短信内容模版">
              <el-input v-model="welcomeTemplate.smsTemplate" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="租户开通 Webhook 推送订阅">
              <el-input v-model="welcomeTemplate.webhookEndpoint" placeholder="https://..." />
            </el-form-item>
            <el-button
              type="primary"
              plain
              :loading="persisting"
              @click="persistBaselineConfig('交付通知与 Webhook 回调模版已持久化保存')"
            >
              更新通知模版
            </el-button>
          </el-form>
        </el-card>
      </div>
    </el-collapse-transition>

    <el-dialog v-model="createDialogVisible" title="登记企业租户入驻申请" width="480px" destroy-on-close>
      <el-form :model="createForm" label-position="top">
        <el-form-item label="申请企业名称" required>
          <el-input v-model="createForm.tenantName" placeholder="例如：上海云帆数智科技有限公司" />
        </el-form-item>
        <el-form-item label="拟用租户编码" required>
          <el-input v-model="createForm.tenantCode" placeholder="小写字母/数字/下划线，如 yunfan_tech" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="联系人姓名">
              <el-input v-model="createForm.contactName" placeholder="姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="createForm.contactMobile" placeholder="手机号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="意向订阅套餐">
          <el-select v-model="createForm.packageTier" style="width: 100%">
            <el-option label="标准版 (100席位)" value="标准版 (100席位)" />
            <el-option label="专业成长版 (300席位)" value="专业成长版 (300席位)" />
            <el-option label="企业版 (500席位)" value="企业版 (500席位)" />
            <el-option label="至尊旗舰版 (独库)" value="至尊旗舰版 (独库)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateApplication">提交入驻工单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.onboarding-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.header-right-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.dual-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.switch-stack {
  padding: 12px 14px;
  background: var(--sb-tier2-bg);
  border-radius: 10px;
  border: 1px solid var(--sb-border-subtle);
  margin-bottom: 16px;
}

.panel-header-flex {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.archived-label {
  color: var(--sb-text-muted);
  font-size: 12px;
}

.mono-code {
  font-family: var(--sb-font-mono);
  font-size: 12px;
  color: var(--sb-text-secondary);
}
</style>
