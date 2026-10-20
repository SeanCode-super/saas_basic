<script setup lang="ts">
import { ref } from "vue";
import { Plus, Timer, Box, Delete, Finished } from "@element-plus/icons-vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";

const props = defineProps<{
  loading?: boolean;
  lifecycleRows: Array<Record<string, unknown>>;
  lifecycleForm: {
    policyCode: string;
    policyName: string;
    fileScope: string;
    retentionDays: number;
    archiveAfterDays: number;
    deleteAfterDays: number;
    deduplicateEnabled: boolean;
    versionRetentionCount: number;
    status: string;
    remark: string;
  };
  saveLifecyclePolicy: () => void | Promise<void>;
  editLifecycle: (row: any) => void;
}>();

const dialogVisible = ref(false);
const isEditing = ref(false);

const columns: ProTableColumn[] = [
  { prop: "policyCode", label: "策略编码", minWidth: 150 },
  { prop: "policyName", label: "策略名称", minWidth: 160 },
  { prop: "fileScope", label: "适用范围", minWidth: 110 },
  { prop: "retentionDays", label: "热期保留", minWidth: 100 },
  { prop: "archiveAfterDays", label: "冷归档", minWidth: 100 },
  { prop: "deleteAfterDays", label: "彻底删除", minWidth: 100 },
  { prop: "versionRetentionCount", label: "版本上限", minWidth: 90 },
  { prop: "deduplicateEnabled", label: "去重治理", minWidth: 100 },
  { prop: "status", label: "状态", minWidth: 90 }
];

function openCreateModal() {
  isEditing.value = false;
  props.lifecycleForm.policyCode = "";
  props.lifecycleForm.policyName = "";
  props.lifecycleForm.fileScope = "GENERAL";
  props.lifecycleForm.retentionDays = 180;
  props.lifecycleForm.archiveAfterDays = 30;
  props.lifecycleForm.deleteAfterDays = 365;
  props.lifecycleForm.versionRetentionCount = 5;
  props.lifecycleForm.deduplicateEnabled = true;
  props.lifecycleForm.status = "ENABLED";
  props.lifecycleForm.remark = "";
  dialogVisible.value = true;
}

function handleEdit(row: any) {
  isEditing.value = true;
  props.editLifecycle(row);
  dialogVisible.value = true;
}

async function handleSave() {
  await props.saveLifecyclePolicy();
  dialogVisible.value = false;
}

function formatScope(scope: any) {
  switch (scope) {
    case "IMAGE":
      return { label: "图片资源", type: "success" };
    case "ATTACHMENT":
      return { label: "附件档案", type: "warning" };
    default:
      return { label: "通用文件", type: "info" };
  }
}
</script>

<template>
  <div class="file-lifecycle-container">
    <!-- Top Lifecycle Radar Metrics Strip -->
    <div class="lifecycle-metric-strip">
      <div class="strip-left">
        <div class="metric-item">
          <el-icon class="icon-blue"><Timer /></el-icon>
          <span class="label">生效规则</span>
          <span class="value">{{ lifecycleRows.length }} 个</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-emerald"><Box /></el-icon>
          <span class="label">冷归档沉降</span>
          <span class="value">30 天</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-purple"><Delete /></el-icon>
          <span class="label">过期清理</span>
          <span class="value">365 天</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-cyan"><Finished /></el-icon>
          <span class="label">SHA-256 去重</span>
          <span class="value tag-active">已激活</span>
        </div>
      </div>
    </div>

    <!-- Main Policies ProTable -->
    <el-card shadow="never" class="lifecycle-card">
      <ProTable
        title="生命周期治理策略台账"
        subtitle="自动化执行文件对象的冷热分级、版本保留数管控、沉降归档与过期销毁规则。"
        :loading="loading"
        :columns="columns"
        :data="lifecycleRows"
        empty-title="暂无生命周期策略规则"
        empty-description="点击右上角「新建策略」添加第一条生命周期规则。"
      >
        <template #toolbar>
          <el-button type="primary" :icon="Plus" @click="openCreateModal">新建策略</el-button>
        </template>

        <template #fileScope="{ row }">
          <el-tag :type="formatScope(row.fileScope).type as any" size="small" effect="plain">
            {{ formatScope(row.fileScope).label }}
          </el-tag>
        </template>

        <template #retentionDays="{ row }">
          <span>{{ row.retentionDays }} 天</span>
        </template>

        <template #archiveAfterDays="{ row }">
          <span>{{ row.archiveAfterDays ? `${row.archiveAfterDays} 天` : "不归档" }}</span>
        </template>

        <template #deleteAfterDays="{ row }">
          <span :style="{ color: row.deleteAfterDays ? '#e11d48' : 'inherit' }">
            {{ row.deleteAfterDays ? `${row.deleteAfterDays} 天` : "永久保留" }}
          </span>
        </template>

        <template #versionRetentionCount="{ row }">
          <el-tag size="small" type="info">{{ row.versionRetentionCount ?? 1 }} 个</el-tag>
        </template>

        <template #deduplicateEnabled="{ row }">
          <el-tag size="small" :type="row.deduplicateEnabled ? 'success' : 'info'">
            {{ row.deduplicateEnabled ? "已启用" : "未启用" }}
          </el-tag>
        </template>

        <template #status="{ row }">
          <BaseStatusTag :status="String(row.status)" />
        </template>

        <template #actions="{ row }">
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
        </template>
      </ProTable>
    </el-card>

    <!-- Dialog: Clean Modal for Create / Edit -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑生命周期策略' : '新建生命周期策略'"
      width="640px"
      append-to-body
      destroy-on-close
    >
      <el-form label-position="top" class="lifecycle-dialog-form">
        <div class="form-row-2">
          <el-form-item label="策略编码" required>
            <el-input v-model="lifecycleForm.policyCode" placeholder="例如: default_archive_policy" />
          </el-form-item>
          <el-form-item label="策略名称" required>
            <el-input v-model="lifecycleForm.policyName" placeholder="例如: 默认通用归档策略" />
          </el-form-item>
        </div>

        <div class="form-row-2">
          <el-form-item label="适用范围">
            <el-select v-model="lifecycleForm.fileScope" style="width: 100%">
              <el-option label="通用文件 (GENERAL)" value="GENERAL" />
              <el-option label="图片资源 (IMAGE)" value="IMAGE" />
              <el-option label="附件档案 (ATTACHMENT)" value="ATTACHMENT" />
            </el-select>
          </el-form-item>
          <el-form-item label="生命周期状态">
            <el-select v-model="lifecycleForm.status" style="width: 100%">
              <el-option label="启用 (ENABLED)" value="ENABLED" />
              <el-option label="停用 (DISABLED)" value="DISABLED" />
            </el-select>
          </el-form-item>
        </div>

        <div class="form-row-3">
          <el-form-item label="热期保留天数">
            <el-input-number v-model="lifecycleForm.retentionDays" :min="1" :max="3650" style="width: 100%" />
          </el-form-item>
          <el-form-item label="冷归档天数">
            <el-input-number v-model="lifecycleForm.archiveAfterDays" :min="0" :max="3650" style="width: 100%" />
          </el-form-item>
          <el-form-item label="彻底删除天数">
            <el-input-number v-model="lifecycleForm.deleteAfterDays" :min="1" :max="3650" style="width: 100%" />
          </el-form-item>
        </div>

        <div class="form-row-2">
          <el-form-item label="历史版本保留数">
            <el-input-number v-model="lifecycleForm.versionRetentionCount" :min="1" :max="100" style="width: 100%" />
          </el-form-item>
          <el-form-item label="文件去重策略">
            <div style="padding-top: 6px">
              <el-switch v-model="lifecycleForm.deduplicateEnabled" active-text="启用 SHA-256 去重治理" />
            </div>
          </el-form-item>
        </div>

        <el-form-item label="备注说明">
          <el-input v-model="lifecycleForm.remark" type="textarea" :rows="2" placeholder="填写该策略的应用场景与维护备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存策略</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.file-lifecycle-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Lifecycle Metric Strip */
.lifecycle-metric-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #ffffff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);

  .strip-left {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
  }

  .metric-item {
    display: flex;
    align-items: center;
    gap: 8px;

    .icon-blue { color: #2563eb; }
    .icon-emerald { color: #059669; }
    .icon-purple { color: #7c3aed; }
    .icon-cyan { color: #0891b2; }

    .label {
      font-size: 13px;
      color: #64748b;
    }

    .value {
      font-size: 13px;
      font-weight: 600;
      color: #0f172a;

      &.tag-active {
        color: #059669;
        background: #ecfdf5;
        padding: 2px 8px;
        border-radius: 4px;
        font-size: 12px;
      }
    }
  }

  .divider {
    width: 1px;
    height: 14px;
    background: #e2e8f0;
  }
}

.lifecycle-card {
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

/* Form in Dialog */
.lifecycle-dialog-form {
  padding: 4px 8px;
}

.form-row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.form-row-3 {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 12px;
}

@media (max-width: 1024px) {
  .lifecycle-stats-grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
