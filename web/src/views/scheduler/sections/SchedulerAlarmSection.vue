<script setup lang="ts">
import { computed, ref } from "vue";
import { Plus, Bell, Warning, Message, Clock } from "@element-plus/icons-vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";

const props = defineProps<{
  jobs: Array<Record<string, unknown>>;
  alarms: Array<Record<string, unknown>>;
  alarmPolicy: {
    jobId: number;
    alarmCode: string;
    alarmName: string;
    notifyChannel: string;
    onFailure: boolean;
    onTimeout: boolean;
    onMisfire: boolean;
    silenceMinutes: number;
    receiverJson: string;
    templateCode: string;
    status: string;
    remark: string;
  };
  savePolicy: () => void | Promise<void>;
  editAlarm: (row: any) => void;
}>();

const dialogVisible = ref(false);
const isEditing = ref(false);

const columns: ProTableColumn[] = [
  { prop: "jobName", label: "所属调度任务", minWidth: 150 },
  { prop: "alarmCode", label: "告警编码", minWidth: 160 },
  { prop: "alarmName", label: "告警名称", minWidth: 160 },
  { prop: "channelType", label: "通知渠道", minWidth: 110 },
  { prop: "triggerRule", label: "触发规则", minWidth: 120 },
  { prop: "silenceMinutes", label: "静默周期", minWidth: 100 },
  { prop: "status", label: "状态", minWidth: 90 }
];

const totalAlarms = computed(() => props.alarms.length);

const formattedAlarms = computed(() => {
  return props.alarms.map((row) => {
    const matchedJob = props.jobs.find((j) => Number(j.id) === Number(row.jobId));
    return {
      ...row,
      jobName: matchedJob ? matchedJob.name : `任务 #${row.jobId}`
    };
  });
});

function openCreateAlarmModal() {
  isEditing.value = false;
  if (props.jobs.length > 0 && !props.alarmPolicy.jobId) {
    props.alarmPolicy.jobId = Number(props.jobs[0].id);
  }
  props.alarmPolicy.alarmCode = "";
  props.alarmPolicy.alarmName = "";
  props.alarmPolicy.notifyChannel = "WEBHOOK";
  props.alarmPolicy.onFailure = true;
  props.alarmPolicy.onTimeout = true;
  props.alarmPolicy.onMisfire = true;
  props.alarmPolicy.silenceMinutes = 10;
  props.alarmPolicy.receiverJson = "{\"webhook\":\"https://ops.example.com/hooks/scheduler\"}";
  props.alarmPolicy.templateCode = "scheduler_failure";
  props.alarmPolicy.status = "ENABLED";
  props.alarmPolicy.remark = "";
  dialogVisible.value = true;
}

function handleEditAlarm(row: any) {
  isEditing.value = true;
  props.editAlarm(row);
  dialogVisible.value = true;
}

async function handleSave() {
  await props.savePolicy();
  dialogVisible.value = false;
}

function resolveChannelBadge(channel: any) {
  switch (channel) {
    case "WEBHOOK":
      return { label: "Webhook 机器人", type: "primary" };
    case "EMAIL":
      return { label: "邮件通知", type: "success" };
    case "WECHAT_WORK":
      return { label: "企业微信", type: "warning" };
    default:
      return { label: String(channel), type: "info" };
  }
}

function resolveTriggerBadge(rule: any) {
  switch (rule) {
    case "ON_FAILURE":
      return { label: "任务执行失败", type: "danger" };
    case "ON_TIMEOUT":
      return { label: "任务执行超时", type: "warning" };
    case "ON_MISFIRE":
      return { label: "调度漏触发", type: "info" };
    default:
      return { label: String(rule), type: "info" };
  }
}
</script>

<template>
  <div class="scheduler-alarm-container">
    <!-- Compact Alarm Metrics Strip -->
    <div class="alarm-metric-strip">
      <div class="metric-item">
        <span class="metric-icon blue"><el-icon><Bell /></el-icon></span>
        <span class="metric-label">告警规则</span>
        <strong class="metric-val">{{ totalAlarms }} 条</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-icon rose"><el-icon><Warning /></el-icon></span>
        <span class="metric-label">触发响应</span>
        <strong class="metric-val">失败/超时秒级捕获</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-icon purple"><el-icon><Message /></el-icon></span>
        <span class="metric-label">触达渠道</span>
        <strong class="metric-val">Webhook / 邮件 / 企微</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-icon amber"><el-icon><Clock /></el-icon></span>
        <span class="metric-label">防风暴静默</span>
        <strong class="metric-val">10 分钟</strong>
      </div>
    </div>

    <!-- Main Alarm Rules ProTable -->
    <el-card shadow="never" class="alarm-table-card">
      <ProTable
        title="调度告警规则台账"
        subtitle="统一收敛任务失败、超时阻断、漏触发异常告警出口，保障调度任务链路高可靠运行。"
        :columns="columns"
        :data="formattedAlarms"
        empty-title="当前暂无告警规则"
        empty-description="点击上方「新增告警规则」按钮建立任务异常防御基线。"
      >
        <template #toolbar>
          <el-button type="primary" :icon="Plus" @click="openCreateAlarmModal">新增告警规则</el-button>
        </template>

        <template #jobName="{ row }">
          <strong style="color: #1e293b">{{ row.jobName }}</strong>
        </template>

        <template #channelType="{ row }">
          <el-tag :type="resolveChannelBadge(row.channelType).type as any" size="small">
            {{ resolveChannelBadge(row.channelType).label }}
          </el-tag>
        </template>

        <template #triggerRule="{ row }">
          <el-tag :type="resolveTriggerBadge(row.triggerRule).type as any" size="small" effect="plain">
            {{ resolveTriggerBadge(row.triggerRule).label }}
          </el-tag>
        </template>

        <template #silenceMinutes="{ row }">
          <span>{{ row.silenceMinutes }} 分钟</span>
        </template>

        <template #status="{ row }">
          <BaseStatusTag :status="String(row.status)" />
        </template>

        <template #actions="{ row }">
          <el-button link type="primary" size="small" @click="handleEditAlarm(row)">编辑规则</el-button>
        </template>
      </ProTable>
    </el-card>

    <!-- Dialog: Clean Modal for Alarm Rule Create / Edit -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑调度告警规则' : '新增调度告警规则'"
      width="640px"
      append-to-body
      destroy-on-close
    >
      <el-form label-position="top">
        <div class="form-row-2">
          <el-form-item label="关联调度任务" required>
            <el-select v-model="alarmPolicy.jobId" style="width: 100%">
              <el-option
                v-for="row in jobs"
                :key="String(row.id)"
                :label="`${row.name} (${row.code})`"
                :value="Number(row.id)"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="告警通知渠道" required>
            <el-select v-model="alarmPolicy.notifyChannel" style="width: 100%">
              <el-option label="Webhook 机器人 (WEBHOOK)" value="WEBHOOK" />
              <el-option label="电子邮件 (EMAIL)" value="EMAIL" />
              <el-option label="企业微信 (WECHAT_WORK)" value="WECHAT_WORK" />
            </el-select>
          </el-form-item>
        </div>

        <div class="form-row-2">
          <el-form-item label="告警规则编码" required>
            <el-input v-model="alarmPolicy.alarmCode" placeholder="例如: quota_refresh_failure" />
          </el-form-item>
          <el-form-item label="告警规则名称" required>
            <el-input v-model="alarmPolicy.alarmName" placeholder="例如: 租户配额定时任务失败告警" />
          </el-form-item>
        </div>

        <el-form-item label="告警触发条件">
          <div class="trigger-switches-row">
            <el-switch v-model="alarmPolicy.onFailure" active-text="执行失败告警" />
            <el-switch v-model="alarmPolicy.onTimeout" active-text="执行超时告警" />
            <el-switch v-model="alarmPolicy.onMisfire" active-text="漏触发补偿告警" />
          </div>
        </el-form-item>

        <div class="form-row-2">
          <el-form-item label="告警静默周期 (分钟)">
            <el-input-number v-model="alarmPolicy.silenceMinutes" :min="0" :max="1440" style="width: 100%" />
          </el-form-item>
          <el-form-item label="模板编码">
            <el-input v-model="alarmPolicy.templateCode" placeholder="例如: scheduler_failure" />
          </el-form-item>
        </div>

        <el-form-item label="接收端 JSON 配置 (Webhook URL / 邮箱列表)">
          <el-input v-model="alarmPolicy.receiverJson" type="textarea" :rows="2" placeholder='{"webhook": "https://..."}' />
        </el-form-item>

        <div class="form-row-2">
          <el-form-item label="规则状态">
            <el-select v-model="alarmPolicy.status" style="width: 100%">
              <el-option label="启用 (ENABLED)" value="ENABLED" />
              <el-option label="停用 (DISABLED)" value="DISABLED" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注说明">
            <el-input v-model="alarmPolicy.remark" placeholder="备注信息" />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存规则</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.scheduler-alarm-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* Compact Metric Strip */
.alarm-metric-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--sb-border-color, #e2e8f0);
  border-radius: 8px;
  flex-wrap: wrap;
  gap: 12px;

  .metric-item {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;

    .metric-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 26px;
      height: 26px;
      border-radius: 6px;
      font-size: 14px;

      &.blue { background: #eff6ff; color: #0284c7; }
      &.rose { background: #fff1f2; color: #e11d48; }
      &.purple { background: #faf5ff; color: #7c3aed; }
      &.amber { background: #fffbeb; color: #d97706; }
    }

    .metric-label {
      color: #64748b;
      font-size: 12px;
    }

    .metric-val {
      color: #0f172a;
      font-weight: 600;
    }
  }

  .metric-divider {
    width: 1px;
    height: 18px;
    background: #e2e8f0;
  }
}



.alarm-table-card {
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.form-row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.trigger-switches-row {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 8px 12px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}

@media (max-width: 1024px) {
  .alarm-stats-grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
