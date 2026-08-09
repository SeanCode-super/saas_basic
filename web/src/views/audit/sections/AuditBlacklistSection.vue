<script setup lang="ts">
defineProps<{
  riskPolicy: {
    blacklistEnabled: boolean;
  };
  savePolicy: (message: string) => void;
}>();
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>黑名单治理</h3>
        <p>账号、IP、设备和租户禁用规则需要同一控制面统一管理。</p>
      </div>
    </template>
    <el-form label-position="top">
      <el-form-item>
        <el-switch v-model="riskPolicy.blacklistEnabled" active-text="启用黑名单治理" />
      </el-form-item>
      <el-button type="primary" @click="savePolicy('黑名单治理策略已保存')">保存黑名单策略</el-button>
    </el-form>
  </el-card>
</template>
