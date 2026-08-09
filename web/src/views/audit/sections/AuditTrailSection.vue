<script setup lang="ts">
defineProps<{
  auditPolicy: {
    retentionDays: number;
    recordPermissionChange: boolean;
    recordConfigChange: boolean;
    recordDataExport: boolean;
    recordFileDownload: boolean;
  };
  savePolicy: (message: string) => void;
}>();
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>审计留痕基线</h3>
        <p>关键动作必须纳入统一审计目录，不能依赖业务方自行记录。</p>
      </div>
    </template>

    <el-form label-position="top">
      <el-form-item label="审计保留天数">
        <el-input-number v-model="auditPolicy.retentionDays" :min="30" :max="3650" style="width: 100%" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="auditPolicy.recordPermissionChange" active-text="记录权限变更事件" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="auditPolicy.recordConfigChange" active-text="记录配置变更事件" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="auditPolicy.recordDataExport" active-text="记录数据导出事件" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="auditPolicy.recordFileDownload" active-text="记录文件下载事件" />
      </el-form-item>
      <el-button type="primary" @click="savePolicy('审计留痕基线已保存')">保存留痕策略</el-button>
    </el-form>
  </el-card>
</template>
