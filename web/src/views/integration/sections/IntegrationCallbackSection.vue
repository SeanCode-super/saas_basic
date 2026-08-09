<script setup lang="ts">
defineProps<{
  webhookPolicy: {
    callbackTimeoutMs: number;
    retryCount: number;
    signRequest: boolean;
    deduplicateEvent: boolean;
    deadLetterQueue: boolean;
  };
  savePolicy: (message: string) => void;
}>();
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>回调策略</h3>
        <p>Webhook 投递必须有统一的签名、重试、去重和死信补偿口径。</p>
      </div>
    </template>
    <el-form label-position="top">
      <el-form-item label="回调超时（毫秒）">
        <el-input-number v-model="webhookPolicy.callbackTimeoutMs" :min="100" :max="30000" style="width: 100%" />
      </el-form-item>
      <el-form-item label="重试次数">
        <el-input-number v-model="webhookPolicy.retryCount" :min="0" :max="20" style="width: 100%" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="webhookPolicy.signRequest" active-text="启用请求签名" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="webhookPolicy.deduplicateEvent" active-text="启用事件去重" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="webhookPolicy.deadLetterQueue" active-text="投递失败进入死信队列" />
      </el-form-item>
      <el-button type="primary" @click="savePolicy('回调投递策略已保存')">保存回调策略</el-button>
    </el-form>
  </el-card>
</template>
