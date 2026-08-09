<script setup lang="ts">
import BaseCard from "@/components/base/BaseCard.vue";

defineProps<{
  isolationPolicy: {
    platformIsolation: string;
    businessIsolation: string;
    financeIsolation: string;
    allowUpgradeIsolation: boolean;
    enforceReadWriteSplit: boolean;
  };
  isolationModeLabelMap: Record<string, string>;
  savePolicy: (message: string) => void;
}>();
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>隔离升级</h3>
        <p>租户隔离不能只有一个 `tenant_id`，必须规划从共享到高隔离的升级路径。</p>
      </div>
    </template>

    <div class="dual-grid">
      <el-form label-position="top">
        <el-form-item label="平台数据隔离">
          <el-select v-model="isolationPolicy.platformIsolation">
            <el-option label="平台模式" value="PLATFORM" />
            <el-option label="共享 Schema" value="SHARED_SCHEMA" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务数据隔离">
          <el-select v-model="isolationPolicy.businessIsolation">
            <el-option label="共享 Schema" value="SHARED_SCHEMA" />
            <el-option label="独立 Schema" value="ISOLATED_SCHEMA" />
            <el-option label="独立数据库" value="DEDICATED_DATABASE" />
          </el-select>
        </el-form-item>
        <el-form-item label="财务数据隔离">
          <el-select v-model="isolationPolicy.financeIsolation">
            <el-option label="独立 Schema" value="ISOLATED_SCHEMA" />
            <el-option label="独立数据库" value="DEDICATED_DATABASE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-switch v-model="isolationPolicy.allowUpgradeIsolation" active-text="允许租户升级隔离级别" />
        </el-form-item>
        <el-form-item>
          <el-switch v-model="isolationPolicy.enforceReadWriteSplit" active-text="高隔离租户强制读写分离" />
        </el-form-item>
        <el-button type="primary" @click="savePolicy('租户隔离升级策略已保存')">保存隔离策略</el-button>
      </el-form>

      <BaseCard class="embedded-card">
        <template #header>
          <div class="panel-header">
            <h3>升级路径说明</h3>
            <p>平台在隔离升级上要可预期、可迁移、可回滚。</p>
          </div>
        </template>
        <div class="blueprint-stack">
          <article class="blueprint-item">
            <strong>标准租户</strong>
            <p>默认共享 Schema，适用于大部分标准企业租户。</p>
          </article>
          <article class="blueprint-item">
            <strong>高价值租户</strong>
            <p>业务或财务数据可升级到独 Schema / 独库模式。</p>
          </article>
          <article class="blueprint-item">
            <strong>监管租户</strong>
            <p>强制读写分离并保留专项审计、备份和灾备方案。</p>
          </article>
        </div>
      </BaseCard>
    </div>
  </el-card>
</template>
