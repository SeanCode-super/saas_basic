<script setup lang="ts">
defineProps<{
  runtimePolicy: {
    defaultLocale: string;
    trialDays: number;
    tenantApprovalMode: string;
    autoOpenConsole: boolean;
    allowSelfService: boolean;
  };
  savePolicy: (message: string) => void;
}>();
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>开通治理</h3>
        <p>统一定义自助注册、试用时长、审批模式和控制台自动开通基线。</p>
      </div>
    </template>

    <div class="policy-grid">
      <el-form label-position="top">
        <el-form-item label="默认语言环境">
          <el-select v-model="runtimePolicy.defaultLocale">
            <el-option label="中文（简体）" value="zh-CN" />
            <el-option label="English" value="en-US" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认试用天数">
          <el-input-number v-model="runtimePolicy.trialDays" :min="1" :max="365" style="width: 100%" />
        </el-form-item>
        <el-form-item label="审批模式">
          <el-segmented
            v-model="runtimePolicy.tenantApprovalMode"
            :options="[
              { label: '人工审批', value: 'MANUAL' },
              { label: '自动开通', value: 'AUTO' }
            ]"
          />
        </el-form-item>
        <el-form-item>
          <el-switch v-model="runtimePolicy.autoOpenConsole" active-text="租户创建后自动开通控制台" />
        </el-form-item>
        <el-form-item>
          <el-switch v-model="runtimePolicy.allowSelfService" active-text="允许自助申请租户" />
        </el-form-item>
        <el-button type="primary" @click="savePolicy('租户开通治理基线已保存')">保存开通基线</el-button>
      </el-form>
    </div>
  </el-card>
</template>
