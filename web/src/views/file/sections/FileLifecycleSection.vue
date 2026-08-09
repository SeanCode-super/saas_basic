<script setup lang="ts">
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";

defineProps<{
  loading?: boolean;
  lifecycleRows: Array<Record<string, unknown>>;
  lifecycleForm: {
    policyCode: string;
    policyName: string;
    fileScope: string;
    retentionDays: number;
    archiveAfterDays: number;
    deleteAfterDays: number;
    deduplicateEnabled: boolean;
    versionRetentionCount: number;
    status: string;
    remark: string;
  };
  saveLifecyclePolicy: () => void | Promise<void>;
  editLifecycle: (row: any) => void;
}>();

const columns: ProTableColumn[] = [
  { prop: "policyCode", label: "策略编码", minWidth: 160 },
  { prop: "policyName", label: "策略名称", minWidth: 160 },
  { prop: "fileScope", label: "适用范围", minWidth: 120 },
  { prop: "retentionDays", label: "保留天数", minWidth: 120 },
  { prop: "archiveAfterDays", label: "归档天数", minWidth: 120 },
  { prop: "deleteAfterDays", label: "删除天数", minWidth: 120 },
  { prop: "status", label: "状态", minWidth: 100 }
];
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>生命周期策略</h3>
        <p>这里直接定义文件保留、归档、删除和版本留存，不再停留在说明卡片。</p>
      </div>
    </template>

    <el-form label-position="top">
      <el-form-item label="策略编码"><el-input v-model="lifecycleForm.policyCode" /></el-form-item>
      <el-form-item label="策略名称"><el-input v-model="lifecycleForm.policyName" /></el-form-item>
      <el-form-item label="适用范围">
        <el-select v-model="lifecycleForm.fileScope">
          <el-option label="通用文件" value="GENERAL" />
          <el-option label="图片资源" value="IMAGE" />
          <el-option label="附件档案" value="ATTACHMENT" />
        </el-select>
      </el-form-item>
      <el-form-item label="保留天数"><el-input-number v-model="lifecycleForm.retentionDays" :min="1" :max="3650" style="width: 100%" /></el-form-item>
      <el-form-item label="归档天数"><el-input-number v-model="lifecycleForm.archiveAfterDays" :min="0" :max="3650" style="width: 100%" /></el-form-item>
      <el-form-item label="删除天数"><el-input-number v-model="lifecycleForm.deleteAfterDays" :min="1" :max="3650" style="width: 100%" /></el-form-item>
      <el-form-item label="版本保留数"><el-input-number v-model="lifecycleForm.versionRetentionCount" :min="1" :max="100" style="width: 100%" /></el-form-item>
      <el-form-item><el-switch v-model="lifecycleForm.deduplicateEnabled" active-text="启用去重治理" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="lifecycleForm.status">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="lifecycleForm.remark" type="textarea" :rows="2" /></el-form-item>
      <el-button type="primary" @click="saveLifecyclePolicy">保存生命周期策略</el-button>
    </el-form>

    <ProTable
      title="生命周期规则"
      subtitle="真实生命周期策略台账。"
      :loading="loading"
      :columns="columns"
      :data="lifecycleRows"
      compact
      empty-title="当前没有生命周期策略"
      empty-description="先保存一条策略，文件保留和归档规则才会落地。"
    >
      <template #actions="{ row }">
        <el-button link type="primary" @click="editLifecycle(row)">编辑</el-button>
      </template>
    </ProTable>
  </el-card>
</template>
