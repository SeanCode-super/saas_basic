<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Check,
  Close,
  Collection,
  Delete,
  DocumentCopy,
  Download,
  Lock,
  Plus,
  RefreshRight,
  Timer,
  VideoPlay
} from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import ExportDialog from "@/components/platform/ExportDialog.vue";
import {
  approveExportTask,
  deleteExportTask,
  deleteExportTemplate,
  deleteReportSchedule,
  downloadExportTask,
  executeSyncExport,
  fetchExportDatasets,
  fetchExportStats,
  fetchExportTasks,
  fetchExportTemplates,
  fetchReportSchedules,
  rejectExportTask,
  saveReportSchedule,
  triggerReportSchedule,
  type ExportDatasetInfo,
  type ExportStatsSummary,
  type ExportTaskRow,
  type ExportTemplateRow,
  type ReportScheduleRow
} from "@/api/modules/export";

const loading = ref(false);
const activeTab = ref<"tasks" | "templates" | "schedules">("tasks");
const tasks = ref<ExportTaskRow[]>([]);
const templates = ref<ExportTemplateRow[]>([]);
const schedules = ref<ReportScheduleRow[]>([]);
const datasets = ref<ExportDatasetInfo[]>([]);
const stats = ref<ExportStatsSummary>({
  totalExports: 0,
  completedExports: 0,
  pendingApprovals: 0,
  totalRowsExported: 0,
  totalFileBytes: 0,
  desensitizedExports: 0,
  activeTemplates: 0,
  activeSchedules: 0
});
const statusFilter = ref("ALL");
const exportDialogVisible = ref(false);
const downloadingId = ref<string | number | null>(null);
const scheduleDialogVisible = ref(false);
const savingSchedule = ref(false);
const scheduleForm = reactive({
  scheduleName: "",
  sourceCode: "crm_customer",
  exportFormat: "PDF",
  frequency: "WEEKLY",
  runHour: 8,
  runMinute: 30,
  includeWatermark: true,
  desensitizeEnabled: true,
  remark: ""
});
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

function formatStatusTagType(status: string): "success" | "danger" | "warning" | "info" {
  if (status === "COMPLETED") return "success";
  if (status === "FAILED" || status === "REJECTED") return "danger";
  if (status === "PENDING_APPROVAL") return "warning";
  return "info";
}

async function loadAll(silent = false) {
  if (!silent) loading.value = true;
  try {
    const [taskRows, statData, tplRows, schedRows, dsRows] = await Promise.all([
      fetchExportTasks(),
      fetchExportStats(),
      fetchExportTemplates(),
      fetchReportSchedules(),
      fetchExportDatasets()
    ]);
    tasks.value = taskRows;
    stats.value = statData;
    templates.value = tplRows;
    schedules.value = schedRows;
    datasets.value = dsRows;
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
    const ext =
      row.exportFormat === "CSV"
        ? "csv"
        : row.exportFormat === "PDF"
        ? "pdf"
        : row.exportFormat === "HTML_REPORT"
        ? "html"
        : "xlsx";
    await downloadExportTask(row.id, `${row.taskName}_${row.traceCode}.${ext}`);
    ElMessage.success(`文件下载已开始（追溯码 ${row.traceCode}）`);
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "下载失败");
  } finally {
    downloadingId.value = null;
  }
}

async function handleApproveTask(row: ExportTaskRow) {
  try {
    const { value } = await ElMessageBox.prompt(
      `确认批准「${row.taskName}」高风险/大批量导出任务吗？（追溯码 ${row.traceCode}）`,
      "数据安全导出审批",
      { inputPlaceholder: "审批意见（可选，如：合规业务核查放行）", confirmButtonText: "审批通过", cancelButtonText: "取消" }
    );
    await approveExportTask(row.id, value || "管理员复核通过，准予导出");
    ElMessage.success("审批已通过，后台正在生成带防伪水印导出文件");
    await loadAll(true);
  } catch {
    // cancelled
  }
}

async function handleRejectTask(row: ExportTaskRow) {
  try {
    const { value } = await ElMessageBox.prompt(
      `请输入驳回「${row.taskName}」导出申请的原因：`,
      "驳回高危导出申请",
      { inputPlaceholder: "如：未签署数据外发授权单", confirmButtonText: "确认驳回", cancelButtonText: "取消" }
    );
    await rejectExportTask(row.id, value || "未通过数据安全合规审查");
    ElMessage.warning("已驳回该导出申请");
    await loadAll(true);
  } catch {
    // cancelled
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
      exportFormat: (tpl.defaultFormat as "XLSX" | "CSV" | "PDF" | "HTML_REPORT") || "XLSX",
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

async function handleSaveSchedule() {
  if (!scheduleForm.scheduleName.trim()) {
    ElMessage.warning("请输入定时报表名称");
    return;
  }
  savingSchedule.value = true;
  try {
    await saveReportSchedule({
      scheduleName: scheduleForm.scheduleName.trim(),
      sourceCode: scheduleForm.sourceCode,
      exportFormat: scheduleForm.exportFormat,
      frequency: scheduleForm.frequency,
      runHour: scheduleForm.runHour,
      runMinute: scheduleForm.runMinute,
      includeWatermark: scheduleForm.includeWatermark,
      desensitizeEnabled: scheduleForm.desensitizeEnabled,
      remark: scheduleForm.remark
    });
    ElMessage.success("定时报表订阅已创建");
    scheduleDialogVisible.value = false;
    scheduleForm.scheduleName = "";
    scheduleForm.remark = "";
    await loadAll(true);
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "创建定时报表失败");
  } finally {
    savingSchedule.value = false;
  }
}

async function handleTriggerSchedule(row: ReportScheduleRow) {
  try {
    const task = await triggerReportSchedule(row.id);
    ElMessage.success(`已触发定时报表执行（${task.traceCode}），稍后可在下载队列获取文件`);
    activeTab.value = "tasks";
    await loadAll(true);
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "触发失败");
  }
}

async function handleDeleteSchedule(row: ReportScheduleRow) {
  try {
    await ElMessageBox.confirm(`确认删除定时报表计划「${row.scheduleName}」？`, "删除确认", { type: "warning" });
    await deleteReportSchedule(row.id);
    ElMessage.success("已删除定时报表计划");
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
    description="流式防 OOM Excel / CSV / 二进制 PDF 报表队列 · 敏感审批风控、防伪水印追溯与 L2 定时自动报表"
    badge="Enterprise Export"
  >
    <template #actions>
      <el-button :icon="RefreshRight" :loading="loading" @click="loadAll(false)">刷新</el-button>
      <el-button :icon="Timer" @click="scheduleDialogVisible = true">新建定时报表</el-button>
      <el-button type="primary" :icon="Plus" @click="exportDialogVisible = true">发起安全导出</el-button>
    </template>

    <div class="kpi-strip">
      <div class="kpi-card">
        <span class="kpi-label">累计导出任务</span>
        <strong class="kpi-value">{{ stats.totalExports }} 次</strong>
        <span class="kpi-note">已完成 {{ stats.completedExports }} · 待审批 {{ stats.pendingApprovals || 0 }}</span>
      </div>
      <div class="kpi-card">
        <span class="kpi-label">累计输出数据行</span>
        <strong class="kpi-value">{{ stats.totalRowsExported }} 行</strong>
        <span class="kpi-note">SXSSF 流式内存保护 + 二进制 PDF</span>
      </div>
      <div class="kpi-card">
        <span class="kpi-label">安全脱敏防护任务</span>
        <strong class="kpi-value">{{ stats.desensitizedExports }} 次</strong>
        <span class="kpi-note">敏感字段自动遮掩 + Trace 水印</span>
      </div>
      <div class="kpi-card">
        <span class="kpi-label">模板与定时订阅</span>
        <strong class="kpi-value">{{ formatBytes(stats.totalFileBytes) }}</strong>
        <span class="kpi-note">{{ stats.activeTemplates }} 套模板 · {{ stats.activeSchedules || 0 }} 项定时报表</span>
      </div>
    </div>

    <BaseCard class="center-card">
      <div class="toolbar">
        <el-radio-group v-model="activeTab" size="default">
          <el-radio-button value="tasks">下载任务队列 ({{ tasks.length }})</el-radio-button>
          <el-radio-button value="templates">字段模板管理 ({{ templates.length }})</el-radio-button>
          <el-radio-button value="schedules">定时报表订阅 ({{ schedules.length }})</el-radio-button>
        </el-radio-group>

        <div v-if="activeTab === 'tasks'" class="filter-group">
          <el-select v-model="statusFilter" style="width: 160px" size="default">
            <el-option label="全部状态" value="ALL" />
            <el-option label="待管理员审批" value="PENDING_APPROVAL" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="排队中" value="PENDING" />
            <el-option label="已驳回" value="REJECTED" />
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
                <span v-if="row.approveReason"> · {{ row.approveReason }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="exportFormat" label="格式" width="90">
          <template #default="{ row }">
            <el-tag
              :type="row.exportFormat === 'PDF' ? 'danger' : row.exportFormat === 'HTML_REPORT' ? 'success' : 'primary'"
              size="small"
            >
              {{ row.exportFormat === "HTML_REPORT" ? "AI 报告" : row.exportFormat }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="安全水印与追溯码" min-width="195">
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

        <el-table-column label="行数 / 体积" width="110">
          <template #default="{ row }">
            <div>{{ row.totalRows }} 行</div>
            <div class="sub-text">{{ formatBytes(row.fileSize) }}</div>
          </template>
        </el-table-column>

        <el-table-column label="状态与进度" width="135">
          <template #default="{ row }">
            <el-tag size="small" :type="formatStatusTagType(row.status)">
              {{
                row.status === "PENDING_APPROVAL"
                  ? "待风控审批"
                  : row.status === "REJECTED"
                  ? "审批已驳回"
                  : row.status
              }}
            </el-tag>
            <el-progress
              v-if="row.status === 'PENDING' || row.status === 'PROCESSING'"
              :percentage="row.progressPercent"
              :stroke-width="6"
              style="margin-top: 4px"
            />
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            <span>{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="165" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING_APPROVAL'">
              <el-button type="success" link :icon="Check" @click="handleApproveTask(row)">通过</el-button>
              <el-button type="warning" link :icon="Close" @click="handleRejectTask(row)">驳回</el-button>
            </template>
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

      <el-table v-else-if="activeTab === 'templates'" v-loading="loading" :data="templates" stripe>
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

      <el-table v-else v-loading="loading" :data="schedules" stripe>
        <el-table-column prop="scheduleName" label="定时报表名称" min-width="220">
          <template #default="{ row }">
            <strong>{{ row.scheduleName }}</strong>
            <div class="sub-text">{{ row.remark || row.scheduleCode }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="sourceCode" label="数据源" width="150" />
        <el-table-column label="周期与格式" width="180">
          <template #default="{ row }">
            <el-tag size="small" type="primary">{{ row.frequency }}</el-tag>
            <el-tag size="small" type="danger" style="margin-left: 6px">{{ row.exportFormat }}</el-tag>
            <div class="sub-text">{{ String(row.runHour).padStart(2, "0") }}:{{ String(row.runMinute).padStart(2, "0") }} 自动执行</div>
          </template>
        </el-table-column>
        <el-table-column label="下次执行时间" width="170">
          <template #default="{ row }">{{ formatTime(row.nextRunAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="VideoPlay" @click="handleTriggerSchedule(row)">立即执行一次</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDeleteSchedule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </BaseCard>

    <ExportDialog
      v-model:visible="exportDialogVisible"
      @exported="loadAll(true)"
    />

    <el-dialog v-model="scheduleDialogVisible" title="新建 L2 定时自动报表计划" width="520px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="报表名称" required>
          <el-input v-model="scheduleForm.scheduleName" placeholder="如：每周 CRM 商机漏斗分析 PDF 周报" />
        </el-form-item>
        <el-form-item label="业务数据源">
          <el-select v-model="scheduleForm.sourceCode" style="width: 100%">
            <el-option v-for="ds in datasets" :key="ds.sourceCode" :label="`${ds.sourceName} (${ds.domain})`" :value="ds.sourceCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="输出格式">
          <el-radio-group v-model="scheduleForm.exportFormat">
            <el-radio-button value="PDF">PDF 分析报告</el-radio-button>
            <el-radio-button value="XLSX">Excel</el-radio-button>
            <el-radio-button value="CSV">CSV</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="触发频次">
          <el-radio-group v-model="scheduleForm.frequency">
            <el-radio-button value="DAILY">每日</el-radio-button>
            <el-radio-button value="WEEKLY">每周一</el-radio-button>
            <el-radio-button value="MONTHLY">每月1日</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="安全治理">
          <el-switch v-model="scheduleForm.includeWatermark" active-text="Trace 水印" style="margin-right: 16px" />
          <el-switch v-model="scheduleForm.desensitizeEnabled" active-text="敏感脱敏" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="scheduleForm.remark" placeholder="完成后将自动发送通知并归档至下载中心" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scheduleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingSchedule" @click="handleSaveSchedule">保存计划</el-button>
      </template>
    </el-dialog>
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
  border-radius: 16px;
  background: var(--app-surface-card, rgba(255, 255, 255, 0.92));
  border: 1px solid rgba(226, 232, 240, 0.92);
  box-shadow: 0 6px 18px -6px rgba(15, 23, 42, 0.05);
}

.kpi-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--app-text-secondary, #334155);
}

.kpi-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--app-text-primary, #0f172a);
}

.kpi-note {
  font-size: 12px;
  color: var(--app-text-muted, #475569);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 10px;
}

.task-name-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.task-meta,
.sub-text {
  font-size: 12px;
  color: #475569;
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
