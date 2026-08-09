<script setup lang="ts">
import BaseCard from "@/components/base/BaseCard.vue";

defineProps<{
  securityPolicy: {
    minLength: number;
    passwordHistory: number;
    sessionTimeoutMinutes: number;
    requireSpecial: boolean;
    forceMfa: boolean;
  };
  savePolicy: (message: string) => void;
}>();
</script>

<template>
  <div class="policy-workbench">
    <BaseCard tone="soft">
      <template #header>
        <div class="panel-header">
          <h3>安全策略</h3>
          <p>密码、会话、多因子和历史密码应由底座统一定义。</p>
        </div>
      </template>

      <el-form label-position="top">
        <div class="dialog-grid">
          <el-form-item label="密码最小长度">
            <el-input-number v-model="securityPolicy.minLength" :min="8" :max="32" style="width: 100%" />
          </el-form-item>
          <el-form-item label="密码历史保留次数">
            <el-input-number v-model="securityPolicy.passwordHistory" :min="1" :max="24" style="width: 100%" />
          </el-form-item>
          <el-form-item label="会话超时（分钟）">
            <el-input-number v-model="securityPolicy.sessionTimeoutMinutes" :min="30" :max="1440" style="width: 100%" />
          </el-form-item>
        </div>
        <div class="policy-toggle-stack">
          <el-form-item>
            <el-switch v-model="securityPolicy.requireSpecial" active-text="密码必须包含特殊字符" />
          </el-form-item>
          <el-form-item>
            <el-switch v-model="securityPolicy.forceMfa" active-text="强制多因子认证" />
          </el-form-item>
        </div>
        <el-button type="primary" @click="savePolicy('系统安全策略已保存')">保存安全策略</el-button>
      </el-form>
    </BaseCard>

    <BaseCard tone="contrast">
      <template #header>
        <div class="panel-header">
          <h3>执行信号</h3>
          <p>当前安全基线会直接影响密码生命周期、登录链路和会话风险面。</p>
        </div>
      </template>
      <div class="signal-stack">
        <article class="signal-item">
          <span>密码长度</span>
          <strong>{{ securityPolicy.minLength }} 位起</strong>
        </article>
        <article class="signal-item">
          <span>历史密码</span>
          <strong>保留 {{ securityPolicy.passwordHistory }} 次</strong>
        </article>
        <article class="signal-item">
          <span>会话时长</span>
          <strong>{{ securityPolicy.sessionTimeoutMinutes }} 分钟</strong>
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
