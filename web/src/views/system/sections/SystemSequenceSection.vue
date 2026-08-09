<script setup lang="ts">
defineProps<{
  sequencePolicy: {
    tenantPrefix: string;
    orderPrefix: string;
    resetCycle: string;
    sequenceLength: number;
  };
  savePolicy: (message: string) => void;
}>();
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>序列规则</h3>
        <p>租户编码、订单号、工单号这类编号规则应该由底座统一定义和重置。</p>
      </div>
    </template>

    <el-form label-position="top">
      <el-form-item label="租户编码前缀">
        <el-input v-model="sequencePolicy.tenantPrefix" />
      </el-form-item>
      <el-form-item label="订单编码前缀">
        <el-input v-model="sequencePolicy.orderPrefix" />
      </el-form-item>
      <el-form-item label="重置周期">
        <el-select v-model="sequencePolicy.resetCycle">
          <el-option label="每日" value="DAILY" />
          <el-option label="每月" value="MONTHLY" />
          <el-option label="每年" value="YEARLY" />
        </el-select>
      </el-form-item>
      <el-form-item label="流水长度">
        <el-input-number v-model="sequencePolicy.sequenceLength" :min="4" :max="18" style="width: 100%" />
      </el-form-item>
      <el-button type="primary" @click="savePolicy('编号规则基线已保存')">保存序列规则</el-button>
    </el-form>
  </el-card>
</template>
