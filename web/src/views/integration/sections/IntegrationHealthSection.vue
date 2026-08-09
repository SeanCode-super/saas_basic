<script setup lang="ts">
import { computed } from "vue";
import BaseCard from "@/components/base/BaseCard.vue";
import type { DatasourceRow } from "@/api/modules/integration";

const props = defineProps<{
  rows: DatasourceRow[];
}>();

const passedCount = computed(() => props.rows.filter((item) => item.testStatus === "PASSED").length);
const untestedCount = computed(() => props.rows.filter((item) => item.testStatus === "UNTESTED").length);
</script>

<template>
  <BaseCard>
    <template #header>
      <div class="panel-header">
        <h3>健康检测</h3>
        <p>集成底座要把连通性、认证失败和回调异常聚合成统一健康信号。</p>
      </div>
    </template>

    <div class="matrix-grid">
      <article class="matrix-item">
        <strong>连通性检测</strong>
        <p>已通过 {{ passedCount }} 条，待检测 {{ untestedCount }} 条。</p>
      </article>
      <article class="matrix-item">
        <strong>认证与凭证</strong>
        <p>凭证过期、口令轮换失败、证书异常和权限拒绝。</p>
      </article>
      <article class="matrix-item">
        <strong>投递与补偿</strong>
        <p>回调失败告警、死信堆积、重复投递和人工介入恢复。</p>
      </article>
    </div>
  </BaseCard>
</template>
