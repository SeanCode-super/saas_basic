<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  ChatDotRound,
  Message,
  DocumentCopy,
  Delete,
  Refresh,
  Position,
  CircleCheckFilled,
  WarningFilled,
  Check,
  Promotion
} from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";
import {
  fetchMessageChannels,
  sendTestSms,
  sendTestMail,
  fetchMessageLogs,
  clearMessageLogs
} from "@/api/modules/integration";
import type {
  MessageChannelsData,
  MessageLogItem,
  SmsSendPayload,
  SmsSendResult,
  MailSendPayload,
  MailSendResult
} from "@/api/modules/integration";

const loading = ref(false);
const activeTab = ref<"sms" | "mail" | "logs">("sms");
const channelsData = ref<MessageChannelsData>({ smsChannels: [], mailChannels: [] });
const messageLogs = ref<MessageLogItem[]>([]);

// SMS form
const smsSending = ref(false);
const smsResult = ref<SmsSendResult | null>(null);
const smsForm = reactive<SmsSendPayload>({
  phone: "13800138000",
  channelCode: "MOCK",
  signName: "【SaaS底座】",
  templateCode: "SMS_AUTH_CODE",
  templateParams: {
    code: "682910",
    expireMinutes: 5
  }
});
const rawSmsParams = ref(JSON.stringify(smsForm.templateParams, null, 2));

// Mail form
const mailSending = ref(false);
const mailResult = ref<MailSendResult | null>(null);
const mailForm = reactive<MailSendPayload>({
  to: "admin@saasbasics.com",
  subject: "【系统通知】欢迎体验企业级多渠道消息网关",
  content: "这是一封来自 SaaS 基础底座消息网关的测试邮件。\n系统已支持 Aliyun/Tencent 短信与 SMTP 邮件多渠道无缝调度。",
  isHtml: false,
  cc: ""
});

async function loadData() {
  loading.value = true;
  try {
    const [channels, logs] = await Promise.all([
      fetchMessageChannels(),
      fetchMessageLogs()
    ]);
    channelsData.value = channels;
    messageLogs.value = logs;
  } catch (err: any) {
    ElMessage.error(err.message || "加载消息通道信息失败");
  } finally {
    loading.value = false;
  }
}

async function handleSendSms() {
  if (!smsForm.phone?.trim()) {
    ElMessage.warning("请输入目标手机号");
    return;
  }
  let parsedParams: any = {};
  if (rawSmsParams.value?.trim()) {
    try {
      parsedParams = JSON.parse(rawSmsParams.value);
    } catch {
      ElMessage.error("参数 JSON 格式不合法，请检查");
      return;
    }
  }

  smsSending.value = true;
  try {
    const res = await sendTestSms({
      ...smsForm,
      templateParams: parsedParams
    });
    smsResult.value = res;
    if (res.success) {
      ElMessage.success("短信测试发送成功！");
    } else {
      ElMessage.warning(`短信发送失败: ${res.message}`);
    }
    await reloadLogs();
  } catch (err: any) {
    ElMessage.error(err.message || "短信发送请求异常");
  } finally {
    smsSending.value = false;
  }
}

async function handleSendMail() {
  if (!mailForm.to?.trim()) {
    ElMessage.warning("请输入收件人邮箱");
    return;
  }
  if (!mailForm.subject?.trim()) {
    ElMessage.warning("请输入邮件主题");
    return;
  }
  if (!mailForm.content?.trim()) {
    ElMessage.warning("请输入邮件正文");
    return;
  }

  mailSending.value = true;
  try {
    const res = await sendTestMail(mailForm);
    mailResult.value = res;
    if (res.success) {
      ElMessage.success("邮件测试投递成功！");
    } else {
      ElMessage.warning(`邮件投递失败: ${res.message}`);
    }
    await reloadLogs();
  } catch (err: any) {
    ElMessage.error(err.message || "邮件投递请求异常");
  } finally {
    mailSending.value = false;
  }
}

async function reloadLogs() {
  try {
    messageLogs.value = await fetchMessageLogs();
  } catch (err: any) {
    console.error(err);
  }
}

async function handleClearLogs() {
  try {
    await ElMessageBox.confirm("确定要清空消息网关的所有审计下发记录吗？", "清空审计记录", {
      confirmButtonText: "确定清空",
      cancelButtonText: "取消",
      type: "warning"
    });
    await clearMessageLogs();
    messageLogs.value = [];
    ElMessage.success("下发记录已清空");
  } catch (err: any) {
    if (err !== "cancel") {
      ElMessage.error(err.message || "清空记录失败");
    }
  }
}

const stats = computed(() => {
  const total = messageLogs.value.length;
  const successCount = messageLogs.value.filter((i) => i.status === "SUCCESS").length;
  const rate = total > 0 ? ((successCount / total) * 100).toFixed(1) : "100.0";
  return {
    total,
    smsCount: messageLogs.value.filter((i) => i.type === "SMS").length,
    mailCount: messageLogs.value.filter((i) => i.type === "MAIL").length,
    rate
  };
});

onMounted(loadData);
</script>

<template>
  <div class="message-gateway-container">
    <!-- 顶部核心遥测指标栏 (Sleek 40px Strip) -->
    <div class="message-metric-strip">
      <div class="strip-left">
        <div class="metric-item">
          <el-icon class="icon-blue"><ChatDotRound /></el-icon>
          <span class="label">SMS 短信渠道</span>
          <span class="value">{{ channelsData.smsChannels.length }} 个可用</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-purple"><Message /></el-icon>
          <span class="label">Mail 邮件网关</span>
          <span class="value">SMTP 就绪</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-green"><Position /></el-icon>
          <span class="label">累计调度</span>
          <span class="value">{{ stats.total }} 次 (短: {{ stats.smsCount }} / 邮: {{ stats.mailCount }})</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-orange"><CircleCheckFilled /></el-icon>
          <span class="label">派发成功率</span>
          <span class="value">{{ stats.rate }}%</span>
        </div>
      </div>
    </div>

    <!-- 主控操作台与选项卡 -->
    <div class="gateway-workbench mt-4">
      <div class="workbench-tabs">
        <button
          type="button"
          class="tab-btn"
          :class="{ active: activeTab === 'sms' }"
          @click="activeTab = 'sms'"
        >
          <el-icon><ChatDotRound /></el-icon>
          <span>短信发送测试 (SMS Gateway)</span>
        </button>
        <button
          type="button"
          class="tab-btn"
          :class="{ active: activeTab === 'mail' }"
          @click="activeTab = 'mail'"
        >
          <el-icon><Message /></el-icon>
          <span>邮件发送测试 (Mail Gateway)</span>
        </button>
        <button
          type="button"
          class="tab-btn"
          :class="{ active: activeTab === 'logs' }"
          @click="activeTab = 'logs'"
        >
          <el-icon><Position /></el-icon>
          <span>分发审计台账 (Audit Logs)</span>
          <span v-if="messageLogs.length" class="badge-count">{{ messageLogs.length }}</span>
        </button>
      </div>

      <!-- TAB 1: SMS -->
      <div v-if="activeTab === 'sms'" class="tab-panel">
        <div class="panel-two-cols">
          <BaseCard title="短信通道配置与下发调试">
            <el-form label-position="top" class="custom-form">
              <el-form-item label="分发通道 (SPI Provider)" required>
                <el-radio-group v-model="smsForm.channelCode">
                  <el-radio-button
                    v-for="ch in channelsData.smsChannels"
                    :key="ch.channelCode"
                    :value="ch.channelCode"
                  >
                    {{ ch.channelName }}
                  </el-radio-button>
                </el-radio-group>
              </el-form-item>

              <div class="form-row-2">
                <el-form-item label="目标手机号码" required>
                  <el-input v-model="smsForm.phone" placeholder="如 13800138000" clearable />
                </el-form-item>
                <el-form-item label="短信签名 (SignName)">
                  <el-input v-model="smsForm.signName" placeholder="如 【SaaS底座】" />
                </el-form-item>
              </div>

              <div class="form-row-2">
                <el-form-item label="模板编码 (TemplateCode)">
                  <el-input v-model="smsForm.templateCode" placeholder="如 SMS_AUTH_CODE" />
                </el-form-item>
                <el-form-item label="动态参数 (JSON Format)">
                  <el-input
                    v-model="rawSmsParams"
                    type="textarea"
                    :rows="3"
                    placeholder='{"code": "123456"}'
                  />
                </el-form-item>
              </div>

              <div class="form-actions-bar">
                <el-button
                  type="primary"
                  :icon="Promotion"
                  :loading="smsSending"
                  @click="handleSendSms"
                >
                  立即下发测试短信
                </el-button>
              </div>
            </el-form>
          </BaseCard>

          <BaseCard title="短信实时回执 (Dispatch Result)">
            <div v-if="smsResult" class="result-box">
              <div class="result-status-line">
                <el-tag :type="smsResult.success ? 'success' : 'danger'" effect="dark">
                  {{ smsResult.success ? '发送成功' : '发送失败' }}
                </el-tag>
                <span class="result-channel">通道: {{ smsResult.channelCode }}</span>
                <span class="result-time">{{ smsResult.sentAt }}</span>
              </div>
              <div class="result-item mt-2">
                <span class="label">Message ID:</span>
                <span class="val code-font">{{ smsResult.messageId }}</span>
              </div>
              <div class="result-item">
                <span class="label">接收手机:</span>
                <span class="val">{{ smsResult.phone }}</span>
              </div>
              <div class="result-item">
                <span class="label">通道返回信息:</span>
                <span class="val font-semibold text-primary">{{ smsResult.message }}</span>
              </div>
            </div>
            <div v-else class="empty-result">
              <el-icon class="empty-icon"><ChatDotRound /></el-icon>
              <span>请在左侧填写短信配置并点击下发测试</span>
            </div>
          </BaseCard>
        </div>
      </div>

      <!-- TAB 2: Mail -->
      <div v-if="activeTab === 'mail'" class="tab-panel">
        <div class="panel-two-cols">
          <BaseCard title="邮件网关配置与投递调试">
            <el-form label-position="top" class="custom-form">
              <div class="form-row-2">
                <el-form-item label="收件人邮箱 (To)" required>
                  <el-input v-model="mailForm.to" placeholder="如 user@example.com" clearable />
                </el-form-item>
                <el-form-item label="抄送人邮箱 (CC)">
                  <el-input v-model="mailForm.cc" placeholder="可选，如 ops@example.com" clearable />
                </el-form-item>
              </div>

              <el-form-item label="邮件主题 (Subject)" required>
                <el-input v-model="mailForm.subject" placeholder="请输入邮件主题" />
              </el-form-item>

              <el-form-item label="邮件正文内容" required>
                <el-input
                  v-model="mailForm.content"
                  type="textarea"
                  :rows="6"
                  placeholder="请输入邮件正文..."
                />
              </el-form-item>

              <div class="form-actions-bar">
                <el-button
                  type="primary"
                  :icon="Promotion"
                  :loading="mailSending"
                  @click="handleSendMail"
                >
                  立即投递测试邮件
                </el-button>
              </div>
            </el-form>
          </BaseCard>

          <BaseCard title="邮件投递回执 (Delivery Result)">
            <div v-if="mailResult" class="result-box">
              <div class="result-status-line">
                <el-tag :type="mailResult.success ? 'success' : 'danger'" effect="dark">
                  {{ mailResult.success ? '投递成功' : '投递失败' }}
                </el-tag>
                <span class="result-time">{{ mailResult.sentAt }}</span>
              </div>
              <div class="result-item mt-2">
                <span class="label">Message ID:</span>
                <span class="val code-font">{{ mailResult.messageId }}</span>
              </div>
              <div class="result-item">
                <span class="label">收件人:</span>
                <span class="val">{{ mailResult.to }}</span>
              </div>
              <div class="result-item">
                <span class="label">主题:</span>
                <span class="val">{{ mailResult.subject }}</span>
              </div>
              <div class="result-item">
                <span class="label">回执消息:</span>
                <span class="val font-semibold text-primary">{{ mailResult.message }}</span>
              </div>
            </div>
            <div v-else class="empty-result">
              <el-icon class="empty-icon"><Message /></el-icon>
              <span>请在左侧填写邮件内容并点击投递测试</span>
            </div>
          </BaseCard>
        </div>
      </div>

      <!-- TAB 3: Logs -->
      <div v-if="activeTab === 'logs'" class="tab-panel">
        <BaseCard title="消息网关下发全量审计台账">
          <template #extra>
            <div class="log-actions">
              <el-button
                type="primary"
                plain
                size="small"
                :icon="Refresh"
                :loading="loading"
                @click="loadData"
              >
                刷新台账
              </el-button>
              <el-button
                type="danger"
                plain
                size="small"
                :icon="Delete"
                @click="handleClearLogs"
              >
                清空台账
              </el-button>
            </div>
          </template>

          <el-table
            :data="messageLogs"
            v-loading="loading"
            stripe
            border
            style="width: 100%"
            max-height="600"
          >
            <el-table-column type="index" label="#" width="60" align="center" />
            <el-table-column prop="type" label="消息类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.type === 'SMS' ? 'success' : 'warning'" effect="plain">
                  {{ row.type === 'SMS' ? '📱 短信' : '✉️ 邮件' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="channel" label="路由通道" width="130" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="info" effect="dark">{{ row.channel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="target" label="下发目标" width="160" />
            <el-table-column prop="title" label="签名/主题" width="180" show-overflow-tooltip />
            <el-table-column prop="content" label="正文/参数摘要" min-width="260" show-overflow-tooltip />
            <el-table-column prop="costMillis" label="耗时" width="90" align="center">
              <template #default="{ row }">
                <span>{{ row.costMillis }} ms</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sentAt" label="发生时间" width="180" align="center" />
          </el-table>
        </BaseCard>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message-gateway-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-metric-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--el-bg-color);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
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

    .icon-blue { color: #0ea5e9; }
    .icon-purple { color: #8b5cf6; }
    .icon-green { color: #10b981; }
    .icon-orange { color: #f59e0b; }

    .label {
      font-size: 13px;
      color: var(--el-text-color-secondary);
    }

    .value {
      font-size: 13px;
      font-weight: 600;
      color: var(--el-text-color-primary);
    }
  }

  .divider {
    width: 1px;
    height: 14px;
    background: var(--el-border-color-lighter);
  }
}

.gateway-workbench {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.workbench-tabs {
  display: flex;
  gap: 8px;
  background: var(--el-bg-color);
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 6px;
  border: 1px solid transparent;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tab-btn:hover {
  background: var(--el-fill-color);
  color: var(--el-color-primary);
}

.tab-btn.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
  font-weight: 600;
}

.badge-count {
  background: var(--el-color-primary);
  color: #fff;
  border-radius: 10px;
  padding: 1px 6px;
  font-size: 11px;
  font-weight: 700;
}

.panel-two-cols {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 16px;
}

@media (max-width: 992px) {
  .panel-two-cols {
    grid-template-columns: 1fr;
  }
}

.form-row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.form-actions-bar {
  margin-top: 16px;
}

.result-box {
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px;
}

.result-status-line {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.result-channel {
  font-size: 13px;
  font-weight: 600;
}

.result-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: auto;
}

.result-item {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  font-size: 13px;
}

.result-item .label {
  color: var(--el-text-color-secondary);
  width: 100px;
  flex-shrink: 0;
}

.empty-result {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 16px;
  color: var(--el-text-color-placeholder);
  gap: 12px;
  font-size: 14px;
}

.empty-icon {
  font-size: 48px;
  color: var(--el-text-color-placeholder);
}

.log-actions {
  display: flex;
  gap: 8px;
}

.code-font {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.mt-2 {
  margin-top: 8px;
}

.mt-4 {
  margin-top: 16px;
}

.font-semibold {
  font-weight: 600;
}

.text-primary {
  color: var(--el-color-primary);
}
</style>
