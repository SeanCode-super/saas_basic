<script setup lang="ts">
import { computed, ref } from "vue";
import { Plus, DocumentChecked, View, Download, Warning } from "@element-plus/icons-vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";

const props = defineProps<{
  loading?: boolean;
  accessLogs: Array<Record<string, unknown>>;
  accessLogForm: {
    fileId: number;
    accessType: string;
    operatorIp: string;
    success: boolean;
    remark: string;
  };
  objectRows: Array<Record<string, unknown>>;
  createAccessAudit: () => void | Promise<void>;
}>();

const dialogVisible = ref(false);
const filterAction = ref("");
const filterSuccess = ref<string>("");

const columns: ProTableColumn[] = [
  { prop: "id", label: "日志 ID", minWidth: 80 },
  { prop: "fileId", label: "文件对象", minWidth: 120 },
  { prop: "accessType", label: "访问动作", minWidth: 110 },
  { prop: "operatorUserId", label: "操作人 ID", minWidth: 100 },
  { prop: "operatorIp", label: "来源 IP", minWidth: 140 },
  { prop: "success", label: "访问结果", minWidth: 100 },
  { prop: "occurredAt", label: "发生时间", minWidth: 180 },
  { prop: "remark", label: "审计说明", minWidth: 160 }
];

const totalLogs = computed(() => props.accessLogs.length);
const previewCount = computed(() => props.accessLogs.filter((l) => l.accessType === "PREVIEW").length);
const downloadCount = computed(() => props.accessLogs.filter((l) => l.accessType === "DOWNLOAD").length);
const failedCount = computed(() => props.accessLogs.filter((l) => l.success === false).length);

const filteredLogs = computed(() => {
  return props.accessLogs.filter((row) => {
    if (filterAction.value && row.accessType !== filterAction.value) return false;
    if (filterSuccess.value === "true" && row.success !== true) return false;
    if (filterSuccess.value === "false" && row.success !== false) return false;
    return true;
  });
});

function openRecordModal() {
  if (props.objectRows.length > 0 && !props.accessLogForm.fileId) {
    props.accessLogForm.fileId = Number(props.objectRows[0].id);
  }
  props.accessLogForm.accessType = "PREVIEW";
  props.accessLogForm.operatorIp = "127.0.0.1";
  props.accessLogForm.success = true;
  props.accessLogForm.remark = "控制台登记访问留痕";
  dialogVisible.value = true;
}

async function handleSaveLog() {
  await props.createAccessAudit();
  dialogVisible.value = false;
}

function resolveActionBadge(action: any) {
  switch (action) {
    case "DOWNLOAD":
      return { label: "下载 (DOWNLOAD)", type: "success" };
    case "PREVIEW":
      return { label: "预览 (PREVIEW)", type: "primary" };
    case "SHARE":
      return { label: "分享 (SHARE)", type: "warning" };
    case "UPLOAD":
      return { label: "上传 (UPLOAD)", type: "info" };
    default:
      return { label: String(action), type: "info" };
  }
}
</script>

<template>
  <div class="file-audit-container">
    <!-- Top Metrics Overview Strip -->
    <div class="audit-metric-strip">
      <div class="strip-left">
        <div class="metric-item">
          <el-icon class="icon-blue"><DocumentChecked /></el-icon>
          <span class="label">审计总量</span>
          <span class="value">{{ totalLogs }} 条</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-cyan"><View /></el-icon>
          <span class="label">在线预览</span>
          <span class="value">{{ previewCount }} 次</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-emerald"><Download /></el-icon>
          <span class="label">文件下载</span>
          <span class="value">{{ downloadCount }} 次</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-rose"><Warning /></el-icon>
          <span class="label">异常阻断</span>
          <span class="value" :class="{ 'has-fail': failedCount > 0 }">{{ failedCount }} 条</span>
        </div>
      </div>
    </div>

    <!-- Main Audit Log ProTable with Query Bar -->
    <el-card shadow="never" class="audit-table-card">
      <div class="audit-filter-bar">
        <div class="filter-inputs">
          <el-select v-model="filterAction" placeholder="筛选访问动作" clearable style="width: 160px">
            <el-option label="全部动作" value="" />
            <el-option label="预览 (PREVIEW)" value="PREVIEW" />
            <el-option label="下载 (DOWNLOAD)" value="DOWNLOAD" />
            <el-option label="分享 (SHARE)" value="SHARE" />
            <el-option label="上传 (UPLOAD)" value="UPLOAD" />
          </el-select>

          <el-select v-model="filterSuccess" placeholder="筛选访问结果" clearable style="width: 140px">
            <el-option label="全部结果" value="" />
            <el-option label="成功 (Success)" value="true" />
            <el-option label="失败 (Failed)" value="false" />
          </el-select>
        </div>

        <el-button type="primary" :icon="Plus" @click="openRecordModal">登记访问记录</el-button>
      </div>

      <ProTable
        title=""
        subtitle="记录全量对象存储的文件读写、外链分享、下载取证与安全溯源审计流水。"
        :loading="loading"
        :columns="columns"
        :data="filteredLogs"
        empty-title="暂无文件访问审计记录"
        empty-description="任何预览、下载或操作行为均会自动在此记录审计凭据。"
      >
        <template #fileId="{ row }">
          <el-tag size="small" type="info" effect="plain">FILE #{{ row.fileId }}</el-tag>
        </template>

        <template #accessType="{ row }">
          <el-tag :type="resolveActionBadge(row.accessType).type as any" size="small">
            {{ resolveActionBadge(row.accessType).label }}
          </el-tag>
        </template>

        <template #operatorIp="{ row }">
          <code>{{ row.operatorIp }}</code>
        </template>

        <template #success="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'" size="small" effect="dark">
            {{ row.success ? "成功" : "失败" }}
          </el-tag>
        </template>

        <template #occurredAt="{ row }">
          <span style="font-size: 13px; color: #475569">
            {{ String(row.occurredAt || "").replace("T", " ").substring(0, 19) }}
          </span>
        </template>
      </ProTable>
    </el-card>

    <!-- Dialog for Manual Audit Log Registration -->
    <el-dialog
      v-model="dialogVisible"
      title="登记文件访问审计事件"
      width="540px"
      append-to-body
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="关联文件对象" required>
          <el-select v-model="accessLogForm.fileId" placeholder="选择文件对象" style="width: 100%">
            <el-option
              v-for="row in objectRows"
              :key="String(row.id)"
              :label="`#${row.id} - ${row.fileName || row.objectKey}`"
              :value="Number(row.id)"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="访问动作类型" required>
          <el-select v-model="accessLogForm.accessType" style="width: 100%">
            <el-option label="预览文件 (PREVIEW)" value="PREVIEW" />
            <el-option label="下载文件 (DOWNLOAD)" value="DOWNLOAD" />
            <el-option label="外链分享 (SHARE)" value="SHARE" />
          </el-select>
        </el-form-item>

        <el-form-item label="访问来源 IP" required>
          <el-input v-model="accessLogForm.operatorIp" placeholder="例如: 127.0.0.1 或客户端 IP" />
        </el-form-item>

        <el-form-item label="访问执行结果">
          <div style="padding-top: 4px">
            <el-switch v-model="accessLogForm.success" active-text="本次访问操作成功" />
          </div>
        </el-form-item>

        <el-form-item label="审计备注">
          <el-input v-model="accessLogForm.remark" type="textarea" :rows="2" placeholder="填写触发原因或业务上下文" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveLog">确认登记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.file-audit-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Audit Metric Strip */
.audit-metric-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 2px rgb(0 0 0 / 0.03);

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
    .icon-cyan { color: #0891b2; }
    .icon-emerald { color: #059669; }
    .icon-rose { color: #e11d48; }

    .label {
      font-size: 13px;
      color: #64748b;
    }

    .value {
      font-size: 13px;
      font-weight: 600;
      color: #0f172a;

      &.has-fail {
        color: #e11d48;
        background: #fff1f2;
        padding: 2px 8px;
        border-radius: 4px;
      }
    }
  }

  .divider {
    width: 1px;
    height: 14px;
    background: #e2e8f0;
  }
}

.audit-table-card {
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.audit-filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;

  .filter-inputs {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

@media (max-width: 1024px) {
  .audit-stats-grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
