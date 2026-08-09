<script setup lang="ts">
import BaseCard from "@/components/base/BaseCard.vue";

defineProps<{
  connectionPolicy: {
    defaultTimeoutMs: number;
    rotationCycleDays: number;
    encryptSecrets: boolean;
    testBeforeEnable: boolean;
    allowPublicNetwork: boolean;
  };
  savePolicy: (message: string) => void;
}>();
</script>

<template>
  <div class="policy-workbench">
    <BaseCard tone="soft">
      <template #header>
        <div class="panel-header">
          <h3>连接治理</h3>
          <p>任何可接入数据源都要先满足连通性检测、超时控制和凭证轮换基线。</p>
        </div>
      </template>
      <el-form label-position="top">
        <div class="dialog-grid">
          <el-form-item label="默认超时（毫秒）">
            <el-input-number v-model="connectionPolicy.defaultTimeoutMs" :min="1000" :max="60000" style="width: 100%" />
          </el-form-item>
          <el-form-item label="凭证轮换周期（天）">
            <el-input-number v-model="connectionPolicy.rotationCycleDays" :min="1" :max="365" style="width: 100%" />
          </el-form-item>
        </div>
        <div class="policy-toggle-stack">
          <el-form-item>
            <el-switch v-model="connectionPolicy.encryptSecrets" active-text="强制加密保存连接凭证" />
          </el-form-item>
          <el-form-item>
            <el-switch v-model="connectionPolicy.testBeforeEnable" active-text="启用前必须完成连通性检测" />
          </el-form-item>
          <el-form-item>
            <el-switch v-model="connectionPolicy.allowPublicNetwork" active-text="允许公网可达连接" />
          </el-form-item>
        </div>
        <el-button type="primary" @click="savePolicy('连接治理策略已保存')">保存连接策略</el-button>
      </el-form>
    </BaseCard>

    <BaseCard tone="contrast">
      <template #header>
        <div class="panel-header">
          <h3>连接治理信号</h3>
          <p>这些执行信号决定接入台账是否能进入启用态和公网边界。</p>
        </div>
      </template>
      <div class="signal-stack">
        <article class="signal-item">
          <span>默认超时</span>
          <strong>{{ connectionPolicy.defaultTimeoutMs }} ms</strong>
        </article>
        <article class="signal-item">
          <span>凭证轮换</span>
          <strong>{{ connectionPolicy.rotationCycleDays }} 天</strong>
        </article>
        <article class="signal-item">
          <span>公网策略</span>
          <strong>{{ connectionPolicy.allowPublicNetwork ? "允许" : "默认禁止" }}</strong>
        </article>
      </div>
    </BaseCard>
  </div>
</template>

<style scoped lang="scss">
.policy-workbench {
  display: grid;
  gap: 16px;
}

.policy-toggle-stack {
  display: grid;
  gap: 6px;
  margin-bottom: 12px;
}
</style>
