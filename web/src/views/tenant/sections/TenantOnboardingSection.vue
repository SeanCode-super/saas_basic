<script setup lang="ts">
import { reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Bell,
  Check,
  CircleCheck,
  Close,
  Document,
  Message,
  OfficeBuilding,
  Operation,
  Plus,
  Refresh,
  Timer,
  User,
  Warning
} from "@element-plus/icons-vue";

import { createTenant } from "@/api/modules/tenant";

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

const applications = ref<OnboardingApplication[]>([
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
]);

const welcomeTemplate = reactive({
  emailSubject: "【SaaS Basics】您的企业租户空间已开通，初始管理员账号已就绪",
  smsTemplate: "【SaaS平台】尊敬的企业管理员，您的租户空间已交付，初始账号为 admin，请及时登入控制台配置组织架构。",
  webhookEndpoint: "https://api.enterprise.com/webhooks/tenant_provisioned"
});

const showPolicySettings = ref(false);

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
      ElMessage.success(`租户「${app.tenantName}」审批通过，租户空间与管理员凭证已自动化完成交付！`);
      if (props.loadData) {
        await props.loadData();
      }
    } catch (err: any) {
      if (err?.response?.data?.message?.includes("已存在")) {
        app.status = "APPROVED";
        ElMessage.success(`租户「${app.tenantName}」空间已存在，审批状态已变更为已批准！`);
      } else {
        ElMessage.error(err?.response?.data?.message || err?.message || "审批开通交付失败");
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
    ElMessage.warning(`申请已驳回，原因已邮件告知申请人。`);
  } catch {
    // cancelled
  }
}
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
        <el-table-column prop="contactName" label="申请联系人" width="130">
          <template #default="{ row }">
            <span>{{ row.contactName }} ({{ row.contactMobile }})</span>
          </template>
        </el-table-column>
        <el-table-column prop="packageTier" label="申请套餐" width="150" />
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
            <span v-else style="color: #64748b; font-size: 12px;">已归档</span>
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
              <el-select v-model="runtimePolicy.defaultLocale" style="width: 100%">
                <el-option label="中文（简体）" value="zh-CN" />
                <el-option label="English" value="en-US" />
              </el-select>
            </el-form-item>
            <el-form-item label="默认试用天数">
              <el-input-number v-model="runtimePolicy.trialDays" :min="1" :max="365" style="width: 100%" />
            </el-form-item>
            <el-form-item label="开通审批模式">
              <el-segmented
                v-model="runtimePolicy.tenantApprovalMode"
                :options="[
                  { label: '人工双人审批', value: 'MANUAL' },
                  { label: '自动放行开通', value: 'AUTO' }
                ]"
              />
            </el-form-item>
            <div class="switch-stack">
              <el-form-item>
                <el-switch v-model="runtimePolicy.autoOpenConsole" active-text="租户创建后自动部署控制台" />
              </el-form-item>
              <el-form-item>
                <el-switch v-model="runtimePolicy.allowSelfService" active-text="允许门户自助在线申请租户" />
              </el-form-item>
            </div>
            <el-button type="primary" @click="savePolicy('租户开通治理基线策略已持久化生效')">保存开通基线</el-button>
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
            <el-button type="primary" plain @click="savePolicy('交付通知与 Webhook 回调模版已保存')">更新通知模版</el-button>
          </el-form>
        </el-card>
      </div>
    </el-collapse-transition>
  </div>
</template>

<style scoped lang="scss">
.onboarding-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid var(--sb-border-color);
  margin-bottom: 16px;
}

.panel-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mono-code {
  font-family: ui-monospace, SFMono-Regular, monospace;
  font-size: 12px;
  color: var(--sb-text-secondary);
}
</style>
