<script setup lang="ts">
import BaseCard from "@/components/base/BaseCard.vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";

defineProps<{
  storagePolicy: {
    defaultAdapter: string;
  };
  uploadPolicy: {
    maxFileMb: number;
  };
  objectRows: Array<Record<string, unknown>>;
}>();

const objectColumns: ProTableColumn[] = [
  { prop: "fileName", label: "文件名", minWidth: 200 },
  { prop: "bizType", label: "业务类型", minWidth: 140 },
  { prop: "fileSize", label: "大小", minWidth: 120 },
  { prop: "status", label: "状态", minWidth: 100 }
];
</script>

<template>
  <div class="overview-grid">
    <BaseCard>
      <template #header>
        <div class="panel-header">
          <h3>文件治理雷达</h3>
          <p>文件底座要同时覆盖存储、上传、生命周期和审计四条治理链路。</p>
        </div>
      </template>
      <div class="matrix-grid">
        <article class="matrix-item">
          <strong>存储适配</strong>
          <p>厂商适配、桶策略、对象版本、冷热分层</p>
        </article>
        <article class="matrix-item">
          <strong>上传通道</strong>
          <p>直传、分片、断点续传、图片压缩、病毒扫描</p>
        </article>
        <article class="matrix-item">
          <strong>生命周期</strong>
          <p>保留、归档、去重、清理、恢复与回收站</p>
        </article>
      </div>
    </BaseCard>

    <BaseCard>
      <template #header>
        <div class="panel-header">
          <h3>当前策略快照</h3>
          <p>先看平台默认策略，再决定各业务空间的覆写范围。</p>
        </div>
      </template>
      <div class="signal-stack">
        <article class="signal-item">
          <span>主适配器</span>
          <strong>{{ storagePolicy.defaultAdapter }}</strong>
        </article>
        <article class="signal-item">
          <span>上传上限</span>
          <strong>{{ uploadPolicy.maxFileMb }} MB</strong>
        </article>
        <article class="signal-item">
          <span>默认审计</span>
          <strong>上传 / 下载 / 分享 / 预览</strong>
        </article>
      </div>
    </BaseCard>

    <ProTable
      title="文件对象"
      subtitle="真实文件对象台账。"
      :columns="objectColumns"
      :data="objectRows"
      compact
      empty-title="当前没有文件对象"
      empty-description="先创建上传会话或写入文件对象。"
    />
  </div>
</template>
