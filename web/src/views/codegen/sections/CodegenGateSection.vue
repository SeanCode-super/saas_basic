<script setup lang="ts">
defineProps<{
  qualityGate: {
    requireAuditField: boolean;
    requireDictBinding: boolean;
    requireQueryForm: boolean;
    requireListPermission: boolean;
    requireSqlArtifact: boolean;
  };
  savePolicy: (message: string) => void;
}>();
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>交付门禁</h3>
        <p>企业级生成器必须附带交付质量门禁，不能只负责“能生成”。</p>
      </div>
    </template>

    <el-form label-position="top">
      <el-form-item>
        <el-switch v-model="qualityGate.requireAuditField" active-text="必须带审计字段" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="qualityGate.requireDictBinding" active-text="必须校验字典绑定" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="qualityGate.requireQueryForm" active-text="必须生成查询表单" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="qualityGate.requireListPermission" active-text="必须生成列表权限码" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="qualityGate.requireSqlArtifact" active-text="必须输出 SQL 产物" />
      </el-form-item>
      <el-button type="primary" @click="savePolicy('交付门禁策略已保存')">保存门禁策略</el-button>
    </el-form>
  </el-card>
</template>
