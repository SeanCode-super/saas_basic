<script setup lang="ts">
defineProps<{
  riskPolicy: {
    abnormalLoginAlert: boolean;
    privilegeEscalationAlert: boolean;
    exportBurstAlert: boolean;
  };
  savePolicy: (message: string) => void;
}>();
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>风险识别基线</h3>
        <p>风险策略必须跟登录、授权和数据导出这些高危动作绑定。</p>
      </div>
    </template>

    <el-form label-position="top">
      <el-form-item>
        <el-switch v-model="riskPolicy.abnormalLoginAlert" active-text="异常登录预警" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="riskPolicy.privilegeEscalationAlert" active-text="权限提升预警" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="riskPolicy.exportBurstAlert" active-text="批量导出预警" />
      </el-form-item>
      <el-button type="primary" @click="savePolicy('风险识别策略已保存')">保存风险策略</el-button>
    </el-form>
  </el-card>
</template>
