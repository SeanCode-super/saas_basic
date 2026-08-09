<script setup lang="ts">
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";

defineProps<{
  loading?: boolean;
  accessLogs: Array<Record<string, unknown>>;
  accessLogForm: {
    fileId: number;
    accessType: string;
    operatorIp: string;
    success: boolean;
    remark: string;
  };
  objectRows: Array<Record<string, unknown>>;
  createAccessAudit: () => void | Promise<void>;
}>();

const columns: ProTableColumn[] = [
  { prop: "fileId", label: "文件 ID", minWidth: 100 },
  { prop: "accessType", label: "访问动作", minWidth: 120 },
  { prop: "operatorUserId", label: "操作人", minWidth: 100 },
  { prop: "operatorIp", label: "来源 IP", minWidth: 140 },
  { prop: "success", label: "结果", minWidth: 100 },
  { prop: "occurredAt", label: "发生时间", minWidth: 180 }
];
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>访问审计</h3>
        <p>这里直接查访问日志，也可以手动登记一条访问事件验证审计链路。</p>
      </div>
    </template>

    <el-form label-position="top">
      <el-form-item label="文件对象">
        <el-select v-model="accessLogForm.fileId">
          <el-option v-for="row in objectRows" :key="String(row.id)" :label="String(row.fileName)" :value="Number(row.id)" />
        </el-select>
      </el-form-item>
      <el-form-item label="访问动作">
        <el-select v-model="accessLogForm.accessType">
          <el-option label="预览" value="PREVIEW" />
          <el-option label="下载" value="DOWNLOAD" />
          <el-option label="分享" value="SHARE" />
        </el-select>
      </el-form-item>
      <el-form-item label="来源 IP"><el-input v-model="accessLogForm.operatorIp" /></el-form-item>
      <el-form-item><el-switch v-model="accessLogForm.success" active-text="本次访问成功" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="accessLogForm.remark" type="textarea" :rows="2" /></el-form-item>
      <el-button type="primary" @click="createAccessAudit">登记访问日志</el-button>
    </el-form>

    <ProTable
      title="访问日志"
      subtitle="真实文件访问留痕。"
      :loading="loading"
      :columns="columns"
      :data="accessLogs"
      compact
      empty-title="当前没有访问日志"
      empty-description="登记一次预览或下载动作，日志会立即出现在这里。"
    />
  </el-card>
</template>
