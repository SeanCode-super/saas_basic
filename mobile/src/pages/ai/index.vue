<template>
  <view class="mobile-ai-page">
    <!-- iOS 27 沉浸式顶栏返回导航 -->
    <view class="ios-nav-bar">
      <view class="ios-nav-back-pill" @click="goBack">
        <AppIcon name="chevron-left" :size="16" color="#0f172a" />
        <text class="ios-nav-back-text">返回</text>
      </view>
      <text class="ios-nav-title">AI 智能工作台与导出</text>
      <view class="ios-nav-placeholder" />
    </view>

    <view class="segment-tabs">
      <view
        class="seg-item"
        :class="{ 'is-active': activeTab === 'copilot' }"
        @click="activeTab = 'copilot'"
      >
        <text>AI 查数 / 知识库</text>
      </view>
      <view
        class="seg-item"
        :class="{ 'is-active': activeTab === 'exports' }"
        @click="switchToExports"
      >
        <text>导出下载中心 ({{ exportCount }})</text>
      </view>
    </view>

    <!-- Tab 1: AI Copilot & RAG -->
    <view v-if="activeTab === 'copilot'" class="tab-body">
      <view class="mode-switch">
        <view
          class="mode-pill"
          :class="{ 'is-selected': aiMode === 'nl2sql' }"
          @click="aiMode = 'nl2sql'"
        >
          <text>NL2SQL 经营查数</text>
        </view>
        <view
          class="mode-pill"
          :class="{ 'is-selected': aiMode === 'rag' }"
          @click="aiMode = 'rag'"
        >
          <text>企业知识库 RAG</text>
        </view>
      </view>

      <view class="ask-card">
        <textarea
          v-model="question"
          class="ask-input"
          placeholder="输入业务问题，例如：统计各行业 CRM 客户分布 / 合同首付款比例红线..."
        />
        <view class="example-row">
          <view
            v-for="ex in quickExamples"
            :key="ex"
            class="ex-chip"
            @click="runExample(ex)"
          >
            <text>{{ ex }}</text>
          </view>
        </view>
        <button class="primary-btn" :disabled="running" @click="handleAsk">
          {{ running ? "AI 思考分析中..." : aiMode === "nl2sql" ? "执行安全查数" : "检索知识库依据" }}
        </button>
      </view>

      <!-- NL2SQL Result -->
      <view v-if="aiMode === 'nl2sql' && nl2sqlRes" class="result-card">
        <view class="result-head">
          <text class="badge">命中 {{ nl2sqlRes.rowCount }} 条 · {{ nl2sqlRes.latencyMs }}ms</text>
          <view class="export-mini-btn" @click="queueExportPdf">
            <text>生成 PDF 存入下载中心</text>
          </view>
        </view>
        <view v-if="nl2sqlRes.insights.length" class="insight-box">
          <text>💡 {{ nl2sqlRes.insights[0] }}</text>
        </view>
        <view v-for="(row, rIdx) in nl2sqlRes.rows.slice(0, 6)" :key="rIdx" class="data-row-card">
          <view v-for="col in nl2sqlRes.columns.slice(0, 4)" :key="col" class="kv-line">
            <text class="kv-k">{{ nl2sqlRes.columnLabels[col] || col }}</text>
            <text class="kv-v">{{ row[col] ?? "-" }}</text>
          </view>
        </view>
      </view>

      <!-- RAG Result -->
      <view v-if="aiMode === 'rag' && ragRes" class="result-card">
        <view class="result-head">
          <text class="badge">{{ ragRes.engine }} · {{ ragRes.latencyMs }}ms</text>
        </view>
        <text class="rag-answer">{{ ragRes.answer }}</text>
        <view v-for="c in ragRes.citations" :key="c.citationNo" class="cite-card">
          <text class="cite-title">[{{ c.citationNo }}] 《{{ c.docName }}》 第{{ c.pageNo }}页</text>
          <text class="cite-body">{{ c.excerpt }}</text>
        </view>
      </view>
    </view>

    <!-- Tab 2: Mobile Download Center -->
    <view v-else class="tab-body">
      <view class="refresh-row">
        <text class="sub-hint">所有导出文件均已脱敏并加注防伪水印</text>
        <view class="mini-refresh" @click="loadExportTasks">
          <text>刷新</text>
        </view>
      </view>

      <view v-if="tasks.length === 0" class="empty-card">
        <text>暂无导出文件任务，可在 AI 查数结果中一键生成 PDF 报告</text>
      </view>

      <view v-for="t in tasks" :key="t.id" class="export-task-card">
        <view class="task-row-top">
          <text class="task-name">{{ t.taskName }}</text>
          <text class="task-status" :class="t.status === 'COMPLETED' ? 'ok' : 'wait'">{{ t.status }}</text>
        </view>
        <view class="task-meta">
          <text>{{ t.exportFormat }} · {{ t.totalRows }} 行 · {{ t.traceCode }}</text>
        </view>
        <view v-if="t.status === 'COMPLETED'" class="task-actions">
          <view class="download-btn" @click="handleMobileDownload(t)">
            <text>下载 / 预览文件</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import AppIcon from "../../components/ui/AppIcon.vue";
import { getCachedData, setCachedData } from "../../utils/warm-cache";
import {
  apiMobileAskKnowledge,
  apiMobileListExportTasks,
  apiMobileNl2Sql,
  apiMobileSubmitExport,
  type MobileExportTask,
  type MobileNl2SqlResponse,
  type MobileRagResponse
} from "../../api/ai";

const activeTab = ref<"copilot" | "exports">("copilot");
const aiMode = ref<"nl2sql" | "rag">("nl2sql");
const question = ref("");
const running = ref(false);
const nl2sqlRes = ref<MobileNl2SqlResponse | null>(null);
const ragRes = ref<MobileRagResponse | null>(null);

// 核心优化：温热任务数首帧直出，杜绝由 (0) 突跳至 (38) 的视觉断层
const cachedTaskCount = getCachedData("AI_EXPORT_COUNT", 0);
const tasks = ref<MobileExportTask[]>(getCachedData("AI_EXPORT_TASKS", []));
const exportCount = computed(() => {
  return tasks.value.length > 0 ? tasks.value.length : cachedTaskCount;
});

const quickExamples = computed(() =>
  aiMode.value === "nl2sql"
    ? ["统计各行业 CRM 客户分布", "统计财务回款状态金额占比", "查询商务合同金额排名"]
    : ["合同首付款和尾款账期红线是多少？", "大额采购违约金与比价要求"]
);

onLoad((opts: Record<string, any> | undefined) => {
  if (opts?.tab === "exports") {
    activeTab.value = "exports";
  }
});

function goBack() {
  uni.navigateBack({
    fail: () => {
      uni.switchTab({ url: "/pages/index/index" });
    }
  });
}

function runExample(ex: string) {
  question.value = ex;
  void handleAsk();
}

async function handleAsk() {
  const q = question.value.trim();
  if (!q) {
    uni.showToast({ title: "请输入问题内容", icon: "none" });
    return;
  }
  running.value = true;
  try {
    if (aiMode.value === "nl2sql") {
      nl2sqlRes.value = await apiMobileNl2Sql(q);
    } else {
      ragRes.value = await apiMobileAskKnowledge(q);
    }
  } catch (err: any) {
    uni.showToast({ title: err?.message || "分析请求失败", icon: "none" });
  } finally {
    running.value = false;
  }
}

async function queueExportPdf() {
  if (!nl2sqlRes.value) return;
  try {
    const task = await apiMobileSubmitExport({
      taskName: `移动端分析_${nl2sqlRes.value.question.slice(0, 16)}`,
      sourceType: "NL2SQL",
      sourceCode: nl2sqlRes.value.tableNames[0] || "nl2sql_result",
      exportFormat: "PDF",
      adHocColumns: nl2sqlRes.value.columns,
      adHocColumnLabels: nl2sqlRes.value.columnLabels,
      adHocRows: nl2sqlRes.value.rows,
      insights: nl2sqlRes.value.insights
    });
    uni.showToast({ title: `已入队 ${task.traceCode}`, icon: "success" });
    activeTab.value = "exports";
    await loadExportTasks();
  } catch (err: any) {
    uni.showToast({ title: err?.message || "提交导出失败", icon: "none" });
  }
}

async function loadExportTasks() {
  try {
    const res = await apiMobileListExportTasks();
    if (Array.isArray(res)) {
      tasks.value = res;
      setCachedData("AI_EXPORT_TASKS", res);
      setCachedData("AI_EXPORT_COUNT", res.length);
    }
  } catch {
  }
}

function switchToExports() {
  activeTab.value = "exports";
  void loadExportTasks();
}

async function handleMobileDownload(task: MobileExportTask) {
  const token = uni.getStorageSync("sb_access_token") || "";
  const url = `/api/exports/tasks/${task.id}/download`;
  // #ifdef H5
  try {
    const resp = await fetch(url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    });
    if (!resp.ok) throw new Error("下载失败");
    const blob = await resp.blob();
    const objUrl = URL.createObjectURL(blob);
    const link = document.createElement("a");
    const ext = task.exportFormat === "CSV" ? "csv" : task.exportFormat === "PDF" ? "pdf" : "xlsx";
    link.href = objUrl;
    link.download = `${task.taskName}_${task.traceCode}.${ext}`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.setTimeout(() => URL.revokeObjectURL(objUrl), 1000);
  } catch {
    uni.showToast({ title: "下载失败", icon: "none" });
  }
  // #endif
}

onMounted(() => {
  void loadExportTasks();
});
</script>

<style scoped>
.mobile-ai-page {
  min-height: 100vh;
  background-color: transparent;
  padding: calc(14px + env(safe-area-inset-top)) 14px calc(32px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.segment-tabs {
  display: flex;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  padding: 4px;
  border-radius: 9999px;
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.95), 0 4px 14px rgba(12, 34, 56, 0.03);
  margin-bottom: 14px;
}

.seg-item {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: 13px;
  color: #64748b;
  font-weight: 550;
  border-radius: 9999px;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  cursor: pointer;
}

.seg-item.is-active {
  background: #ffffff;
  color: #2563eb;
  font-weight: 700;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
}

.tab-body {
  padding: 0;
}

.mode-switch {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.mode-pill {
  padding: 6px 14px;
  border-radius: 9999px;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.85);
  color: #475569;
  font-weight: 550;
  border: 1px solid rgba(226, 232, 240, 0.90);
  transition: all 0.15s ease;
  cursor: pointer;
}

.mode-pill.is-selected {
  background: #0f172a;
  color: #ffffff;
  border-color: #0f172a;
  font-weight: 650;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.2);
}

.ask-card,
.result-card,
.export-task-card,
.empty-card {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 18px;
  padding: 16px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 1), 0 8px 24px -4px rgba(12, 34, 56, 0.04);
  margin-bottom: 14px;
}

.ask-input {
  width: 100%;
  height: 76px;
  padding: 10px;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  font-size: 13.5px;
  background: rgba(248, 250, 252, 0.8);
}

.example-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 10px 0;
}

.ex-chip {
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 6px;
  background: #eff6ff;
  color: #1d4ed8;
  cursor: pointer;
}

.primary-btn {
  width: 100%;
  height: 42px;
  line-height: 42px;
  border-radius: 12px;
  background: linear-gradient(135deg, #0284c7 0%, #2563eb 55%, #4f46e5 100%);
  color: #ffffff;
  font-size: 14px;
  font-weight: 650;
  border: 1px solid rgba(255, 255, 255, 0.35);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.5), 0 6px 20px -3px rgba(37, 99, 235, 0.35);
  cursor: pointer;
  transition: transform 0.1s ease;
}

.primary-btn:active {
  transform: scale(0.98);
}

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.badge {
  font-size: 12px;
  color: #059669;
  font-weight: 600;
}

.export-mini-btn {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  background: #eff6ff;
  color: #2563eb;
}

.insight-box {
  padding: 8px 10px;
  border-radius: 8px;
  background: #eff6ff;
  font-size: 12px;
  color: #1e3a8a;
  margin-bottom: 10px;
}

.data-row-card {
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
  margin-bottom: 8px;
}

.kv-line {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  padding: 2px 0;
}

.kv-k {
  color: #64748b;
}

.kv-v {
  color: #0f172a;
  font-weight: 500;
}

.rag-answer {
  display: block;
  font-size: 13px;
  line-height: 1.6;
  color: #0f172a;
  margin-bottom: 10px;
}

.cite-card {
  padding: 8px;
  border-radius: 8px;
  background: #f8fafc;
  margin-bottom: 6px;
}

.cite-title {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #1d4ed8;
}

.cite-body {
  display: block;
  font-size: 11px;
  color: #475569;
  margin-top: 2px;
}

.refresh-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.sub-hint {
  font-size: 12px;
  color: #64748b;
}

.mini-refresh {
  font-size: 12px;
  color: #2563eb;
}

.task-row-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-name {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.task-status {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
}

.task-status.ok {
  background: #dcfce7;
  color: #166534;
}

.task-status.wait {
  background: #fef3c7;
  color: #92400e;
}

.task-meta {
  font-size: 12px;
  color: #64748b;
  margin-top: 6px;
}

.task-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.download-btn {
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 6px;
  background: #2563eb;
  color: #ffffff;
}
</style>
