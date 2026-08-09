<script setup lang="ts">
import BaseCard from "@/components/base/BaseCard.vue";
import type { IamOverview } from "@/api/modules/iam";

defineProps<{
  overview: IamOverview | null;
  policyRows: Array<{
    title: string;
    owner: string;
    baseline: string;
    source: string;
  }>;
}>();
</script>

<template>
  <div class="foundation-grid">
    <BaseCard>
      <template #header>
        <div class="header-stack">
          <h3>身份治理基线</h3>
          <p>基线说明保留，但计数、能力项和模型层级已经直接来自真实接口。</p>
        </div>
      </template>
      <div class="runtime-band">
        <div class="runtime-metric">
          <span>账号</span>
          <strong>{{ overview?.counters.accounts ?? 0 }}</strong>
        </div>
        <div class="runtime-metric">
          <span>员工</span>
          <strong>{{ overview?.counters.employees ?? 0 }}</strong>
        </div>
        <div class="runtime-metric">
          <span>部门</span>
          <strong>{{ overview?.counters.departments ?? 0 }}</strong>
        </div>
        <div class="runtime-metric">
          <span>岗位</span>
          <strong>{{ overview?.counters.positions ?? 0 }}</strong>
        </div>
        <div class="runtime-metric">
          <span>角色</span>
          <strong>{{ overview?.counters.roles ?? 0 }}</strong>
        </div>
        <div class="runtime-metric">
          <span>权限资源</span>
          <strong>{{ overview?.counters.apiResources ?? 0 }}</strong>
        </div>
      </div>
      <div class="policy-stack">
        <article v-for="item in policyRows" :key="item.title" class="policy-row">
          <div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.baseline }}</p>
          </div>
          <div class="policy-meta">
            <span>{{ item.owner }}</span>
            <code>{{ item.source }}</code>
          </div>
        </article>
      </div>
    </BaseCard>

    <BaseCard>
      <template #header>
        <div class="header-stack">
          <h3>运行态模型与能力</h3>
          <p>模型层级和能力标签都来自后端总览接口，用来校验 IAM 闭环是否完整。</p>
        </div>
      </template>
      <div class="relationship-grid">
        <div v-for="layer in overview?.modelLayers ?? []" :key="layer" class="relationship-node">
          {{ layer }}
        </div>
      </div>
      <div class="capability-grid">
        <div v-for="capability in overview?.capabilities ?? []" :key="capability" class="capability-chip">
          {{ capability }}
        </div>
      </div>
    </BaseCard>
  </div>
</template>

<style scoped lang="scss">
.foundation-grid {
  display: grid;
  grid-template-columns: 1fr 0.85fr;
  gap: 16px;
}

.header-stack {
  h3 {
    margin: 0;
  }

  p {
    margin: 6px 0 0;
    color: var(--sb-text-secondary);
  }
}

.policy-stack {
  display: grid;
  gap: 16px;
}

.runtime-band {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.runtime-metric {
  padding: 14px 16px;
  border: 1px solid var(--sb-border-color);
  border-radius: 16px;
  background: var(--sb-card-soft-bg);

  span {
    display: block;
    color: var(--sb-text-secondary);
    font-size: 12px;
  }

  strong {
    display: block;
    margin-top: 8px;
    font-size: 26px;
    line-height: 1;
  }
}

.policy-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid var(--sb-border-color);

  &:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }

  strong {
    display: block;
  }

  p {
    margin: 6px 0 0;
    color: var(--sb-text-secondary);
  }
}

.policy-meta {
  text-align: right;

  span,
  code {
    display: block;
  }

  span {
    color: var(--sb-text-secondary);
  }

  code {
    margin-top: 6px;
  }
}

.relationship-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.relationship-node {
  padding: 18px;
  border-radius: 18px;
  border: 1px solid var(--sb-border-color);
  background: linear-gradient(180deg, rgb(17 93 163 / 0.06), rgb(255 255 255 / 0.92));
  font-weight: 600;
  line-height: 1.6;
  text-transform: capitalize;
}

.capability-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.capability-chip {
  padding: 10px 14px;
  border-radius: 999px;
  border: 1px solid var(--sb-border-color);
  background: var(--sb-card-soft-bg);
  color: var(--sb-text-secondary);
}

@media (max-width: 1280px) {
  .foundation-grid {
    grid-template-columns: 1fr;
  }

  .runtime-band {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 860px) {
  .runtime-band,
  .relationship-grid {
    grid-template-columns: 1fr;
  }

  .policy-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .policy-meta {
    text-align: left;
  }
}
</style>
