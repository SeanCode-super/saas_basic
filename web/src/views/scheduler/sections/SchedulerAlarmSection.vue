<script setup lang="ts">
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";

defineProps<{
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

const columns: ProTableColumn[] = [
  { prop: "jobId", label: "任务 ID", minWidth: 100 },
  { prop: "alarmCode", label: "告警编码", minWidth: 180 },
  { prop: "alarmName", label: "告警名称", minWidth: 180 },
  { prop: "channelType", label: "渠道", minWidth: 120 },
  { prop: "triggerRule", label: "触发规则", minWidth: 120 },
  { prop: "silenceMinutes", label: "静默分钟", minWidth: 120 },
  { prop: "status", label: "状态", minWidth: 100 }
];
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>告警基线</h3>
        <p>任务异常要有统一告警和补偿出口，而不是沉默失败。</p>
      </div>
    </template>

    <div class="alarm-grid">
      <el-form label-position="top">
        <el-form-item label="绑定任务">
          <el-select v-model="alarmPolicy.jobId">
            <el-option v-for="row in jobs" :key="String(row.id)" :label="String(row.name)" :value="Number(row.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警编码">
          <el-input v-model="alarmPolicy.alarmCode" />
        </el-form-item>
        <el-form-item label="告警名称">
          <el-input v-model="alarmPolicy.alarmName" />
        </el-form-item>
        <el-form-item label="通知渠道">
          <el-select v-model="alarmPolicy.notifyChannel">
            <el-option label="Webhook" value="WEBHOOK" />
            <el-option label="邮件" value="EMAIL" />
            <el-option label="企业微信" value="WECHAT_WORK" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-switch v-model="alarmPolicy.onFailure" active-text="失败时告警" />
        </el-form-item>
        <el-form-item>
          <el-switch v-model="alarmPolicy.onTimeout" active-text="超时时告警" />
        </el-form-item>
        <el-form-item>
          <el-switch v-model="alarmPolicy.onMisfire" active-text="漏触发时告警" />
        </el-form-item>
        <el-form-item label="静默分钟">
          <el-input-number v-model="alarmPolicy.silenceMinutes" :min="0" :max="1440" style="width: 100%" />
        </el-form-item>
        <el-form-item label="接收人配置 JSON">
          <el-input v-model="alarmPolicy.receiverJson" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="模板编码">
          <el-input v-model="alarmPolicy.templateCode" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="alarmPolicy.status">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="alarmPolicy.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>

      <div class="alarm-aside">
        <strong>统一异常口径</strong>
        <p>这里现在直接保存真实告警规则，并且能回查已经落库的告警台账。</p>
        <el-button type="primary" @click="savePolicy">保存告警策略</el-button>
      </div>
    </div>

    <ProTable
      title="告警规则"
      subtitle="真实调度告警规则。"
      :columns="columns"
      :data="alarms"
      compact
      empty-title="当前没有告警规则"
      empty-description="保存一条告警规则后会立即出现在这里。"
    >
      <template #actions="{ row }">
        <el-button link type="primary" @click="editAlarm(row)">编辑</el-button>
      </template>
    </ProTable>
  </el-card>
</template>

<style scoped lang="scss">
.alarm-grid {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 18px;
}

.alarm-aside {
  padding: 18px;
  border-radius: 20px;
  background: rgb(15 98 254 / 0.05);

  p {
    margin: 10px 0 0;
    color: var(--sb-text-secondary);
    line-height: 1.7;
  }
}

@media (max-width: 1080px) {
  .alarm-grid {
    grid-template-columns: 1fr;
  }
}
</style>
