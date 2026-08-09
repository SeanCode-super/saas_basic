<script setup lang="ts">
import type { SystemDictItemRow, SystemDictTypeRow } from "@/api/modules/system";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import ProTable, { type ProTableColumn } from "@/components/pro/ProTable.vue";

defineProps<{
  localePolicy: {
    defaultLocale: string;
    fallbackLocale: string;
    dictionaryCacheMinutes: number;
    autoPublishI18n: boolean;
  };
  loading?: boolean;
  dictTypeColumns: ProTableColumn[];
  dictTypes: SystemDictTypeRow[];
  dictItemColumns: ProTableColumn[];
  dictItems: SystemDictItemRow[];
  activeDictType?: SystemDictTypeRow | null;
  savePolicy: (message: string) => void;
  selectDictType: (row: SystemDictTypeRow) => void;
  openCreateDictType: () => void;
  openCreateDictItem: () => void;
  editDictType: (row: SystemDictTypeRow) => void;
  editDictItem: (row: SystemDictItemRow) => void;
  toggleDictTypeStatus: (row: SystemDictTypeRow) => void | Promise<void>;
  toggleDictItemStatus: (row: SystemDictItemRow) => void | Promise<void>;
}>();
</script>

<template>
  <section class="module-section-stack">
    <el-card shadow="never">
      <template #header>
        <div class="panel-header">
          <h3>语言策略</h3>
          <p>这里只保留语言基线，真正的字典管理在下方按类型和字典项进行。</p>
        </div>
      </template>

      <el-form label-position="top">
        <el-form-item label="默认语言">
          <el-select v-model="localePolicy.defaultLocale">
            <el-option label="中文（简体）" value="zh-CN" />
            <el-option label="English" value="en-US" />
          </el-select>
        </el-form-item>
        <el-form-item label="回退语言">
          <el-select v-model="localePolicy.fallbackLocale">
            <el-option label="中文（简体）" value="zh-CN" />
            <el-option label="English" value="en-US" />
          </el-select>
        </el-form-item>
        <el-form-item label="字典缓存周期（分钟）">
          <el-input-number v-model="localePolicy.dictionaryCacheMinutes" :min="1" :max="1440" style="width: 100%" />
        </el-form-item>
        <el-form-item>
          <el-switch v-model="localePolicy.autoPublishI18n" active-text="更新国际化资源后自动刷新缓存" />
        </el-form-item>
        <el-button type="primary" @click="savePolicy('语言与字典策略已保存，仅支持中文和英文')">保存语言策略</el-button>
      </el-form>
    </el-card>

    <ProTable
      title="字典类型"
      subtitle="先定义字典类型，再承载字典项、颜色、标签和默认值。"
      :loading="loading"
      :columns="dictTypeColumns"
      :data="dictTypes"
      selectable
      compact
      empty-title="当前没有字典类型"
      empty-description="先新增一个字典类型，例如租户状态、任务状态、数据源类型。"
      @row-click="(row) => selectDictType(row as SystemDictTypeRow)"
    >
      <template #toolbar>
        <el-button type="primary" @click="openCreateDictType">新增字典类型</el-button>
      </template>
      <template #status="{ row }">
        <BaseStatusTag :status="row.status" />
      </template>
      <template #actions="{ row }">
        <el-button link @click="editDictType(row)">编辑</el-button>
        <el-button link type="primary" @click="selectDictType(row)">查看字典项</el-button>
        <el-button link @click="toggleDictTypeStatus(row)">
          {{ row.status === "ENABLED" ? "停用" : "启用" }}
        </el-button>
      </template>
    </ProTable>

    <ProTable
      title="字典项"
      :subtitle="activeDictType ? `当前字典：${activeDictType.dictName}（${activeDictType.dictCode}）` : '先在上方选择一个字典类型，再维护它的字典项。'"
      :loading="loading"
      :columns="dictItemColumns"
      :data="dictItems"
      selectable
      compact
      empty-title="当前没有字典项"
      empty-description="请选择一个字典类型后新增字典项。"
    >
      <template #toolbar>
        <el-button :disabled="!activeDictType" type="primary" @click="openCreateDictItem">新增字典项</el-button>
      </template>
      <template #status="{ row }">
        <BaseStatusTag :status="row.status" />
      </template>
      <template #defaultItem="{ row }">
        <el-tag :type="row.defaultItem ? 'success' : 'info'" effect="light">
          {{ row.defaultItem ? "默认" : "普通" }}
        </el-tag>
      </template>
      <template #actions="{ row }">
        <el-button link @click="editDictItem(row)">编辑</el-button>
        <el-button link @click="toggleDictItemStatus(row)">
          {{ row.status === "ENABLED" ? "停用" : "启用" }}
        </el-button>
      </template>
    </ProTable>
  </section>
</template>
