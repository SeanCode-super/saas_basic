<script setup lang="ts">
import BaseCard from "@/components/base/BaseCard.vue";
</script>

<template>
  <div class="overview-grid">
    <BaseCard>
      <template #header>
        <div class="panel-header">
          <h3>生成器控制面</h3>
          <p>一份元数据必须能驱动数据库、后端、前端、权限和菜单骨架。</p>
        </div>
      </template>
      <div class="matrix-grid">
        <article class="matrix-item">
          <strong>元数据模型</strong>
          <p>表、字段、索引、字典、权限、菜单和 SQL 产物。</p>
        </article>
        <article class="matrix-item">
          <strong>统一模板</strong>
          <p>后端、前端、接口、路由与页面工作台统一输出。</p>
        </article>
        <article class="matrix-item">
          <strong>交付门禁</strong>
          <p>审计字段、权限码、查询表单、SQL 产物必须附带。</p>
        </article>
      </div>
    </BaseCard>
  </div>
</template>
