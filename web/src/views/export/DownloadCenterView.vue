<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Collection,
  Delete,
  DocumentCopy,
  Download,
  Lock,
  Plus,
  RefreshRight
} from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import ExportDialog from "@/components/platform/ExportDialog.vue";
import {
  deleteExportTask,
  deleteExportTemplate,
  downloadExportTask,
  executeSyncExport,
  fetchExportStats,
  fetchExportTasks,
  fetchExportTemplates,
  type ExportStatsSummary,
  type ExportTaskRow,
  type ExportTemplateRow
} from "@/api/modules/export";

const loading = ref(false);
const activeTab = ref<"tasks" | "templates">("tasks");
const tasks = ref<ExportTaskRow[]>([]);
const templates = ref<ExportTemplateRow[]>([]);
const stats = ref<ExportStatsSummary>({
  totalExports: 0,
  completedExports: 0,
  totalRowsExported: 0,
  totalFileBytes: 0,
  desensitizedExports: 0,
  activeTemplates: 0
});
const statusFilter = ref("ALL");
const exportDialogVisible = ref(false);
const downloadingId = ref<string | number | null>(null);
let pollTimer: number | null = null;

const filteredTasks = computed(() => {
  if (statusFilter.value === "ALL") return tasks.value;
  return tasks.value.filter((t) => t.status === statusFilter.value);
});

const hasInflightTasks = computed(() =>
  tasks.value.some((t) => t.status === "PENDING" || t.status === "PROCESSING")
);

function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return "0 B";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function formatTime(iso?: string): string {
  if (!iso) return "-";
  return iso.replace("T", " ").slice(0, 19);
}

async function loadAll(silent = false) {
  if (!silent) loading.value = true;
  try {
    const [taskRows, statData, tplRows] = await Promise.all([
      fetchExportTasks(),
      fetchExportStats(),
      fetchExportTemplates()
    ]);
    tasks.value = taskRows;
    stats.value = statData;
    templates.value = tplRows;
  } catch (err) {
    if (!silent) {
      ElMessage.error(err instanceof Error ? err.message : "加载下载中心失败");
    }
  } finally {
    if (!silent) loading.value = false;
  }
}

async function handleDownload(row: ExportTaskRow) {
  downloadingId.value = row.id;
  try {
    const ext = row.exportFormat === "CSV" ? "csv" : row.exportFormat === "HTML_REPORT" ? "html" : "xlsx";
    await downloadExportTask(row.id, `${row.taskName}_${row.traceCode}.${ext}`);
    ElMessage.success(`文件下载已开始（追溯码 ${row.traceCode}）`);
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "下载失败");
  } finally {
    downloadingId.value = null;
  }
}

async function handleDeleteTask(row: ExportTaskRow) {
  try {
    await ElMessageBox.confirm(`确认删除导出记录「${row.taskName}」及其存储文件吗？`, "删除确认", {
      type: "warning"
    });
    await deleteExportTask(row.id);
    ElMessage.success("导出记录已清理");
    await loadAll(true);
  } catch {
    // cancelled
  }
}

async function handleRunTemplate(tpl: ExportTemplateRow) {
  try {
    const res = await executeSyncExport({
      taskName: tpl.templateName,
      sourceType: "DATASET",
      sourceCode: tpl.sourceCode,
      exportFormat: (tpl.defaultFormat as "XLSX" | "CSV" | "HTML_REPORT") || "XLSX",
      selectedFields: tpl.fields,
      includeWatermark: tpl.includeWatermark,
      desensitizeEnabled: tpl.desensitizeEnabled,
      async: false
    });
    ElMessage.success(`已按模板导出（追溯码 ${res.traceCode}）`);
    await loadAll(true);
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "按模板导出失败");
  }
}

async function handleDeleteTemplate(tpl: ExportTemplateRow) {
  try {
    await ElMessageBox.confirm(`确认删除导出模板「${tpl.templateName}」？`, "确认删除", {
      type: "warning"
    });
    await deleteExportTemplate(tpl.id);
    ElMessage.success("模板已删除");
    await loadAll(true);
  } catch {
    // cancelled
  }
}

function copyText(text: string, label: string) {
  void navigator.clipboard.writeText(text);
  ElMessage.success(`已复制${label}`);
}

function startPolling() {
  pollTimer = window.setInterval(() => {
    if (hasInflightTasks.value) {
      void loadAll(true);
    }
  }, 2500);
}

onMounted(() => {
  void loadAll();
  startPolling();
});

onUnmounted(() => {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer);
  }
});
</script>

<template>
  <ControlSurface
    title="数据导出与下载中心"
    description="流式防 OOM Excel / CSV / AI 分析报告统一队列 · 自动脱敏、可见水印与追溯审计"
    badge="Enterprise Export"
  >
    <template #actions>
      <el-button :icon="RefreshRight" :loading="loading" @click="loadAll(false)">刷新</el-button>
      <el-button type="primary" :icon="Plus" @click="exportDialogVisible = true">发起安全导出</el-button>
    </template>

    <div class="kpi-strip">
      <div class="kpi-card">
        <span class="kpi-label">累计导出任务</span>
        <strong class="kpi-value">{{ stats.totalExports }} 次</strong>
        <span class="kpi-note">已完成 {{ stats.completedExports }} 次</span>
      </div>
      <div class="kpi-card">
        <span class="kpi-label">累计输出数据行</span>
        <strong class="kpi-value">{{ stats.totalRowsExported }} 行</strong>
        <span class="kpi-note">SXSSF 流式内存保护</span>
      </div>
      <div class="kpi-card">
        <span class="kpi-label">安全脱敏防护任务</span>
        <strong class="kpi-value">{{ stats.desensitizedExports }} 次</strong>
        <span class="kpi-note">敏感字段自动遮掩 + 水印</span>
      </div>
      <div class="kpi-card">
        <span class="kpi-label">暂存文件总容量</span>
        <strong class="kpi-value">{{ formatBytes(stats.totalFileBytes) }}</strong>
        <span class="kpi-note">活跃模板 {{ stats.activeTemplates }} 套 · 保留 7 天</span>
      </div>
    </div>

    <BaseCard class="center-card">
      <div class="toolbar">
        <el-radio-group v-model="activeTab" size="default">
          <el-radio-button value="tasks">下载任务队列 ({{ tasks.length }})</el-radio-button>
          <el-radio-button value="templates">字段模板管理 ({{ templates.length }})</el-radio-button>
        </el-radio-group>

        <div v-if="activeTab === 'tasks'" class="filter-group">
          <el-select v-model="statusFilter" style="width: 150px" size="default">
            <el-option label="全部状态" value="ALL" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="排队中" value="PENDING" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </div>
      </div>

      <el-table v-if="activeTab === 'tasks'" v-loading="loading" :data="filteredTasks" stripe>
        <el-table-column prop="taskName" label="导出任务名称" min-width="200">
          <template #default="{ row }">
            <div class="task-name-cell">
              <strong>{{ row.taskName }}</strong>
              <div class="task-meta">
                <code>{{ row.sourceCode }}</code>
                <span v-if="row.operatorName"> · {{ row.operatorName }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="exportFormat" label="格式" width="120">
          <template #default="{ row }">
            <el-tag :type="row.exportFormat === 'HTML_REPORT' ? 'success' : 'primary'" size="small">
              {{ row.exportFormat === 'HTML_REPORT' ? 'AI 报告' : row.exportFormat }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="安全水印与追溯码" min-width="210">
          <template #default="{ row }">
            <div class="trace-cell">
              <el-tag size="small" effect="plain" class="trace-tag" @click="copyText(row.traceCode, '追溯码')">
                {{ row.traceCode }} <el-icon><DocumentCopy /></el-icon>
              </el-tag>
              <el-tag v-if="row.desensitized" size="small" type="warning" effect="plain">
                <el-icon><Lock /></el-icon> 已脱敏
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="行数 / 体积" width="140">
          <template #default="{ row }">
            <div>{{ row.totalRows }} 行</div>
            <div class="sub-text">{{ formatBytes(row.fileSize) }}</div>
          </template>
        </el-table-column>

        <el-table-column label="状态与进度" width="170">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.status === 'COMPLETED' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'"
            >
              {{ row.status }}
            </el-tag>
            <el-progress
              v-if="row.status === 'PENDING' || row.status === 'PROCESSING'"
              :percentage="row.progressPercent"
              :stroke-width="6"
              style="margin-top: 4px"
            />
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            <span>{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'COMPLETED'"
              type="primary"
              link
              :icon="Download"
              :loading="downloadingId === row.id"
              @click="handleDownload(row)"
            >
              下载
            </el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDeleteTask(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else v-loading="loading" :data="templates" stripe>
        <el-table-column prop="templateName" label="模板名称" min-width="200">
          <template #default="{ row }">
            <strong>{{ row.templateName }}</strong>
            <div class="sub-text">{{ row.remark || row.templateCode }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="sourceCode" label="数据源" width="150" />
        <el-table-column label="字段数" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.fields.length }} 列</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="defaultFormat" label="默认格式" width="120" />
        <el-table-column label="安全策略" width="180">
          <template #default="{ row }">
            <el-tag v-if="row.includeWatermark" size="small" type="info">水印</el-tag>
            <el-tag v-if="row.desensitizeEnabled" size="small" type="warning" style="margin-left: 6px">脱敏</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Collection" @click="handleRunTemplate(row)">一键导出</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDeleteTemplate(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </BaseCard>

    <ExportDialog
      v-model:visible="exportDialogVisible"
      @exported="loadAll(true)"
    />
  </ControlSurface>
</template>

<style scoped lang="scss">
.kpi-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.kpi-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 18px;
  border-radius: 14px;
  background: var(--app-surface-card, rgba(255 255 255 / 0.85));
  border: 1px solid var(--app-border-soft, rgba(148 163 184 / 0.25));
}

.kpi-label {
  font-size: 12px;
  color: var(--app-text-secondary, #64748b);
}

.kpi-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--app-text-primary, #0f172a);
}

.kpi-note {
  font-size: 12px;
  color: var(--app-text-muted, #64748b);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.task-name-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.task-meta,
.sub-text {
  font-size: 12px;
  color: #64748b;
}

.trace-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.trace-tag {
  cursor: pointer;
}

@media (max-width: 1024px) {
  .kpi-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
