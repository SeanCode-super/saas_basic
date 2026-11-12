<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import * as echarts from "echarts";
import { ElMessage } from "element-plus";
import {
  Cpu,
  DataAnalysis,
  DocumentCopy,
  Download,
  Lock,
  MagicStick,
  Promotion,
  Search
} from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import ExportDialog from "@/components/platform/ExportDialog.vue";
import {
  executeNl2SqlQuery,
  fetchAiCommandHistory,
  fetchNl2SqlSchemas,
  fetchPublicAiModels,
  type AiCommandHistoryRow,
  type Nl2SqlQueryResponse,
  type Nl2SqlSchemaTable
} from "@/api/modules/ai";
import { executeSyncExport } from "@/api/modules/export";

const route = useRoute();
const router = useRouter();

const question = ref("");
const selectedModel = ref("deepseek-chat");
const loading = ref(false);
const exporting = ref(false);
const result = ref<Nl2SqlQueryResponse | null>(null);
const schemas = ref<Nl2SqlSchemaTable[]>([]);
const historyList = ref<AiCommandHistoryRow[]>([]);
const modelOptions = ref<Array<{ id: string; name: string }>>([
  { id: "deepseek-chat", name: "DeepSeek-V3" },
  { id: "deepseek-reasoner", name: "DeepSeek-R1" }
]);
const activeResultTab = ref<"chart" | "table" | "sql">("chart");
const exportDialogVisible = ref(false);
const chartContainerRef = ref<HTMLElement | null>(null);
let chartInstance: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;

const presetQuestions = [
  "统计各行业 CRM 客户分布与成交情况",
  "分析销售商机各阶段的预计金额漏斗",
  "统计财务回款各状态金额占比",
  "查看商务合同金额排名前列清单",
  "查询 ERP 各商品实时库存水位",
  "统计各业务模块操作审计频次"
];

async function initCatalogAndHistory() {
  try {
    const [schemaRows, histRows, models] = await Promise.all([
      fetchNl2SqlSchemas(),
      fetchAiCommandHistory(15),
      fetchPublicAiModels()
    ]);
    schemas.value = schemaRows;
    historyList.value = histRows;
    if (models.length > 0) {
      modelOptions.value = models.map((m) => ({ id: m.id, name: m.name }));
      const def = models.find((m) => m.isDefault);
      if (def) selectedModel.value = def.id;
    }
  } catch {
    // non-fatal initialization
  }
}

async function runQuery(customPrompt?: string) {
  const q = (customPrompt ?? question.value).trim();
  if (!q) {
    ElMessage.warning("请输入你想查询或分析的自然语言问题");
    return;
  }
  question.value = q;
  loading.value = true;
  try {
    result.value = await executeNl2SqlQuery({
      question: q,
      modelId: selectedModel.value,
      maxRows: 50
    });
    activeResultTab.value = result.value.recommendedChartType !== "TABLE" ? "chart" : "table";
    await nextTick();
    renderChart();
    historyList.value = await fetchAiCommandHistory(15);
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "NL2SQL 查询执行失败");
  } finally {
    loading.value = false;
  }
}

function renderChart() {
  const res = result.value;
  if (!res || !chartContainerRef.value || res.rows.length === 0 || !res.chartSeriesField) {
    if (chartInstance) {
      chartInstance.clear();
    }
    return;
  }
  if (!chartInstance) {
    chartInstance = echarts.init(chartContainerRef.value);
    resizeObserver = new ResizeObserver(() => {
      chartInstance?.resize();
    });
    resizeObserver.observe(chartContainerRef.value);
  }

  const xField = res.chartXAxis || res.columns[0];
  const yField = res.chartSeriesField;
  const xLabel = res.columnLabels[xField] || xField;
  const yLabel = res.columnLabels[yField] || yField;
  const categories = res.rows.map((r) => String(r[xField] ?? "-"));
  const values = res.rows.map((r) => Number(r[yField] ?? 0));

  if (res.recommendedChartType === "PIE") {
    chartInstance.setOption({
      tooltip: { trigger: "item" },
      legend: { bottom: 0 },
      series: [
        {
          name: yLabel,
          type: "pie",
          radius: ["42%", "70%"],
          itemStyle: { borderRadius: 8, borderColor: "#fff", borderWidth: 2 },
          data: categories.map((name, idx) => ({ name, value: values[idx] }))
        }
      ]
    }, true);
  } else {
    const isLine = res.recommendedChartType === "LINE";
    chartInstance.setOption({
      tooltip: { trigger: "axis" },
      grid: { left: 56, right: 24, top: 28, bottom: 48 },
      xAxis: { type: "category", name: xLabel, data: categories },
      yAxis: { type: "value", name: yLabel },
      series: [
        {
          name: yLabel,
          type: isLine ? "line" : "bar",
          smooth: isLine,
          barMaxWidth: 42,
          itemStyle: { borderRadius: isLine ? 0 : [6, 6, 0, 0], color: "#2563eb" },
          data: values
        }
      ]
    }, true);
  }
}

async function handleQuickExport(format: "XLSX" | "CSV" | "HTML_REPORT") {
  const res = result.value;
  if (!res) return;
  exporting.value = true;
  try {
    const out = await executeSyncExport({
      taskName: res.chartTitle || res.question,
      sourceType: "NL2SQL",
      sourceCode: res.tableNames[0] || "nl2sql",
      exportFormat: format,
      adHocColumns: res.columns,
      adHocColumnLabels: res.columnLabels,
      adHocRows: res.rows,
      insights: res.insights,
      includeWatermark: true,
      desensitizeEnabled: true,
      async: false
    });
    ElMessage.success(`已安全导出 ${format}（追溯码 ${out.traceCode}）`);
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "导出失败");
  } finally {
    exporting.value = false;
  }
}

function copySql() {
  if (!result.value?.sql) return;
  void navigator.clipboard.writeText(result.value.sql);
  ElMessage.success("安全 SQL 已复制到剪贴板");
}

function openAiAppBuilder() {
  void router.push({
    path: "/codegen/ai-builder",
    query: question.value.trim() ? { prompt: question.value.trim(), autoDesign: "1" } : undefined
  });
}

watch(activeResultTab, async (val) => {
  if (val === "chart") {
    await nextTick();
    renderChart();
    chartInstance?.resize();
  }
});

onMounted(async () => {
  await initCatalogAndHistory();
  const initialQ = typeof route.query.q === "string" ? route.query.q : "";
  await runQuery(initialQ || presetQuestions[0]);
});

onUnmounted(() => {
  resizeObserver?.disconnect();
  chartInstance?.dispose();
});
</script>

<template>
  <ControlSurface
    title="AI 数据副驾驶 (NL2SQL 驾驶舱)"
    description="自然语言直连多租户安全数据库 · 自动 SQL 沙箱校验、字段脱敏、智能可视化与水印报告导出"
    badge="AI Copilot"
  >
    <template #actions>
      <el-button :icon="MagicStick" @click="openAiAppBuilder">转入自然语言建表建页</el-button>
      <el-button :icon="Promotion" @click="router.push('/file/download-center')">下载中心</el-button>
    </template>

    <div class="copilot-layout">
      <div class="copilot-main">
        <BaseCard class="prompt-card">
          <div class="prompt-bar">
            <el-input
              v-model="question"
              size="large"
              placeholder="用自然语言问任何业务数据，例如：分析销售商机各阶段的预计金额漏斗"
              clearable
              @keyup.enter="runQuery()"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>

            <el-select v-model="selectedModel" size="large" style="width: 190px">
              <el-option v-for="m in modelOptions" :key="m.id" :label="m.name" :value="m.id" />
            </el-select>

            <el-button type="primary" size="large" :icon="DataAnalysis" :loading="loading" @click="runQuery()">
              AI 分析查询
            </el-button>
          </div>

          <div class="preset-chips">
            <span class="chip-label">快捷提问：</span>
            <el-button
              v-for="item in presetQuestions"
              :key="item"
              size="small"
              round
              @click="runQuery(item)"
            >
              {{ item }}
            </el-button>
          </div>
        </BaseCard>

        <BaseCard v-loading="loading" class="result-card">
          <template v-if="result">
            <div class="result-header">
              <div class="result-title-area">
                <h3>{{ result.chartTitle }}</h3>
                <div class="meta-badges">
                  <el-tag size="small" type="success">{{ result.rowCount }} 条结果</el-tag>
                  <el-tag size="small" type="info">{{ result.executionEngine }} · {{ result.latencyMs }}ms</el-tag>
                  <el-tag v-if="result.maskedFields.length" size="small" type="warning">
                    <el-icon><Lock /></el-icon> 已脱敏: {{ result.maskedFields.join(", ") }}
                  </el-tag>
                </div>
              </div>

              <div class="export-actions">
                <el-button size="small" :icon="Download" :loading="exporting" @click="handleQuickExport('XLSX')">
                  导出 Excel
                </el-button>
                <el-button size="small" :icon="Download" :loading="exporting" @click="handleQuickExport('HTML_REPORT')">
                  生成 AI 分析报告
                </el-button>
                <el-button size="small" type="primary" plain @click="exportDialogVisible = true">
                  高级导出配置
                </el-button>
              </div>
            </div>

            <div v-if="result.insights.length" class="insights-banner">
              <div class="insights-title">💡 AI 自动数据洞察</div>
              <ul>
                <li v-for="(ins, i) in result.insights" :key="i">{{ ins }}</li>
              </ul>
            </div>

            <el-tabs v-model="activeResultTab">
              <el-tab-pane v-if="result.chartSeriesField" label="智能可视化图表" name="chart">
                <div ref="chartContainerRef" class="chart-canvas" />
              </el-tab-pane>

              <el-tab-pane label="结构化数据明细" name="table">
                <el-table :data="result.rows" stripe max-height="420">
                  <el-table-column
                    v-for="col in result.columns"
                    :key="col"
                    :prop="col"
                    :label="result.columnLabels[col] || col"
                    min-width="130"
                    show-overflow-tooltip
                  />
                </el-table>
              </el-tab-pane>

              <el-tab-pane label="多租户安全 SQL 与执行校验" name="sql">
                <div class="sql-viewer">
                  <div class="sql-toolbar">
                    <span>命中数据表：<code>{{ result.tableNames.join(", ") }}</code></span>
                    <el-button size="small" :icon="DocumentCopy" @click="copySql">复制 SQL</el-button>
                  </div>
                  <pre class="sql-code">{{ result.sql }}</pre>
                </div>
              </el-tab-pane>
            </el-tabs>

            <div v-if="result.followUpSuggestions.length" class="follow-ups">
              <span class="chip-label">🔗 继续追问：</span>
              <el-tag
                v-for="fq in result.followUpSuggestions"
                :key="fq"
                class="follow-tag"
                effect="plain"
                @click="runQuery(fq)"
              >
                {{ fq }}
              </el-tag>
            </div>
          </template>
        </BaseCard>
      </div>

      <aside class="copilot-sidebar">
        <BaseCard>
          <template #header>
            <div class="side-title"><el-icon><Cpu /></el-icon> 可用多租户 Schema ({{ schemas.length }})</div>
          </template>
          <div class="schema-list">
            <div v-for="tbl in schemas" :key="tbl.tableName" class="schema-item" @click="runQuery(`查询 ${tbl.tableLabel} 最新情况`)">
              <div class="schema-name">{{ tbl.tableLabel }}</div>
              <div class="schema-meta">
                <code>{{ tbl.tableName }}</code>
                <el-tag size="small">{{ tbl.domain }}</el-tag>
              </div>
            </div>
          </div>
        </BaseCard>

        <BaseCard v-if="historyList.length" style="margin-top: 14px">
          <template #header>
            <div class="side-title">近期智能指令历史</div>
          </template>
          <div class="history-list">
            <div v-for="h in historyList" :key="h.id" class="history-item" @click="runQuery(h.rawCommand)">
              <div class="history-cmd">{{ h.rawCommand }}</div>
              <div class="history-meta">{{ h.intentCode }} · {{ h.rowCount }}行 · {{ h.latencyMs }}ms</div>
            </div>
          </div>
        </BaseCard>
      </aside>
    </div>

    <ExportDialog
      v-model:visible="exportDialogVisible"
      :default-title="result?.chartTitle || 'NL2SQL数据导出'"
      :ad-hoc-columns="result?.columns || []"
      :ad-hoc-column-labels="result?.columnLabels || {}"
      :ad-hoc-rows="result?.rows || []"
      :insights="result?.insights || []"
    />
  </ControlSurface>
</template>

<style scoped lang="scss">
.copilot-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
}

.copilot-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.prompt-bar {
  display: flex;
  gap: 10px;
  align-items: center;
}

.preset-chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

.chip-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.result-title-area h3 {
  margin: 0 0 6px;
  font-size: 17px;
}

.meta-badges {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.export-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.insights-banner {
  background: linear-gradient(135deg, rgba(37 99 235 / 0.08), rgba(14 165 233 / 0.05));
  border-left: 4px solid #2563eb;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 14px;

  .insights-title {
    font-weight: 600;
    font-size: 13px;
    margin-bottom: 6px;
    color: #1e3a8a;
  }

  ul {
    margin: 0;
    padding-left: 18px;
    font-size: 13px;
    line-height: 1.65;
  }
}

.chart-canvas {
  width: 100%;
  height: 320px;
}

.sql-viewer {
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 10px;
  padding: 14px;
}

.sql-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 12px;
  color: #94a3b8;
}

.sql-code {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
}

.follow-ups {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--app-border-soft, #e2e8f0);
}

.follow-tag {
  cursor: pointer;
}

.side-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 13px;
}

.schema-list,
.history-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
}

.schema-item,
.history-item {
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  background: var(--app-surface-muted, #f8fafc);
  transition: background 0.15s ease;

  &:hover {
    background: rgba(37 99 235 / 0.08);
  }
}

.schema-name,
.history-cmd {
  font-size: 13px;
  font-weight: 500;
}

.schema-meta,
.history-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
  font-size: 11px;
  color: #64748b;
}

@media (max-width: 1120px) {
  .copilot-layout {
    grid-template-columns: 1fr;
  }
}
</style>
