<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  Bell,
  Briefcase,
  Collection,
  Cpu,
  CreditCard,
  DataAnalysis,
  DataBoard,
  DataLine,
  Document,
  Download,
  Grid,
  Key,
  Lock,
  MagicStick,
  Monitor,
  Operation,
  Printer,
  Promotion,
  Search,
  Service,
  Setting,
  TrendCharts,
  User
} from "@element-plus/icons-vue";
import { interpretAiCommand, type AiCommandResponse } from "@/api/modules/ai";
import { executeSyncExport } from "@/api/modules/export";

const props = defineProps<{
  visible: boolean;
}>();

const emit = defineEmits<{
  (e: "update:visible", val: boolean): void;
  (e: "command", action: string): void;
}>();

const router = useRouter();
const route = useRoute();
const searchKeyword = ref("");
const selectedIndex = ref(0);
const inputRef = ref<HTMLInputElement | null>(null);
const aiLoading = ref(false);
const aiExporting = ref(false);
const aiResult = ref<AiCommandResponse | null>(null);

interface CommandItem {
  id: string;
  category: "FAST_ACTION" | "APPS" | "PLATFORM";
  title: string;
  subtitle: string;
  icon: unknown;
  action: () => void;
}

const aiQuickExamples = [
  "统计各行业 CRM 客户分布",
  "分析销售商机漏斗各阶段预计金额",
  "统计财务回款状态金额占比",
  "帮我建一个设备巡检管理表，包含设备名称、检查人、隐患等级、巡检时间"
];

const allCommands: CommandItem[] = [
  {
    id: "action-ai-copilot",
    category: "FAST_ACTION",
    title: "AI 数据副驾驶 (NL2SQL 驾驶舱)",
    subtitle: "用自然语言查询业务库，自动生成 ECharts 图表、脱敏数据与分析报告",
    icon: DataAnalysis,
    action: () => void router.push("/ai/data-copilot")
  },
  {
    id: "action-ai-knowledge",
    category: "FAST_ACTION",
    title: "AI 企业知识库与合同合审 (RAG)",
    subtitle: "DOCX/XLSX/MD 切分入库、混合检索带页码引用问答及合同合规扫描",
    icon: Collection,
    action: () => void router.push("/ai/knowledge")
  },
  {
    id: "action-ai-agents",
    category: "FAST_ACTION",
    title: "智能体工坊 (Agent Studio)",
    subtitle: "编排专属数字员工人设、挂载知识库与 MCP 工具权限及实时沙盒联调",
    icon: Service,
    action: () => void router.push("/ai/agents")
  },
  {
    id: "action-ai-finetune",
    category: "FAST_ACTION",
    title: "模型微调与评测 (LoRA Studio)",
    subtitle: "自动采集脱敏样本、导出 Alpaca/ShareGPT JSONL 与 A/B 准确率评估",
    icon: DataLine,
    action: () => void router.push("/ai/finetune")
  },
  {
    id: "action-download-center",
    category: "FAST_ACTION",
    title: "数据导出与下载中心 (Export Center)",
    subtitle: "SXSSF 流式导出 Excel / CSV / 水印报告、模板管理与大文件异步下载",
    icon: Download,
    action: () => void router.push("/file/download-center")
  },
  {
    id: "action-ai-app-builder",
    category: "FAST_ACTION",
    title: "AI 智能应用工坊 (自然语言建表建页)",
    subtitle: "输入业务场景描述，自动生成实体表结构、后端接口与动态 Vue 页面",
    icon: MagicStick,
    action: () => void router.push("/codegen/ai-builder")
  },
  {
    id: "action-profile",
    category: "FAST_ACTION",
    title: "个人中心 (Profile / MFA)",
    subtitle: "查看个人账号信息、绑定 TOTP 多因子认证与恢复码",
    icon: User,
    action: () => void router.push("/profile")
  },
  {
    id: "action-monitor",
    category: "FAST_ACTION",
    title: "服务与应用监控 (Server APM)",
    subtitle: "实时 CPU、JVM 堆、接口 P95/P99 分位延迟、告警规则引擎",
    icon: Monitor,
    action: () => void router.push("/server-monitor")
  },
  {
    id: "action-screen",
    category: "FAST_ACTION",
    title: "数字大屏驾驶舱 (Data Screen)",
    subtitle: "全屏自适应科技感运营数据大屏",
    icon: DataBoard,
    action: () => {
      const routeUrl = router.resolve({ path: "/screen" });
      window.open(routeUrl.href, "_blank");
    }
  },
  {
    id: "action-doc",
    category: "FAST_ACTION",
    title: "Knife4j 在线 API 接口文档",
    subtitle: "SpringDoc OpenAPI 3 交互式接口调试台",
    icon: Document,
    action: () => window.open("http://localhost:8080/doc.html", "_blank")
  },
  {
    id: "app-crm",
    category: "APPS",
    title: "客户关系 CRM",
    subtitle: "公海池抢单、销售线索、商机阶段漏斗、合同回款与安全导出",
    icon: TrendCharts,
    action: () => void router.push("/crm/customer")
  },
  {
    id: "app-erp",
    category: "APPS",
    title: "进销存 ERP",
    subtitle: "商品档案 SKU、实时多仓库存、采购履约入库台账",
    icon: Briefcase,
    action: () => void router.push("/erp")
  },
  {
    id: "app-workflow",
    category: "APPS",
    title: "工作流中心 (BPM)",
    subtitle: "流程定义、节点设计器、待办与审批历史",
    icon: Operation,
    action: () => void router.push("/workflow")
  },
  {
    id: "app-print",
    category: "APPS",
    title: "单据套打与报表中心 (Print)",
    subtitle: "增值税发票、采购单、物流面单套打，防伪印章与一键打印",
    icon: Printer,
    action: () => void router.push("/print")
  },
  {
    id: "app-pay",
    category: "APPS",
    title: "统一聚合支付中台 (Pay Center)",
    subtitle: "微信支付、支付宝签名验真回调、Outbox 自动结算账单",
    icon: CreditCard,
    action: () => void router.push("/integration/pay")
  },
  {
    id: "app-message",
    category: "APPS",
    title: "多渠道消息与通知网关 (Notify)",
    subtitle: "企业微信、钉钉、飞书推送，短信与邮件调度审计",
    icon: Bell,
    action: () => void router.push("/integration/message")
  },
  {
    id: "plat-tenant",
    category: "PLATFORM",
    title: "多租户管控与灾备中心",
    subtitle: "租户开通、应用授权、真 ZIP 快照备份与校验恢复",
    icon: Setting,
    action: () => void router.push("/tenant")
  },
  {
    id: "plat-iam",
    category: "PLATFORM",
    title: "身份与权限中心 (IAM)",
    subtitle: "用户账号、角色权限矩阵、数据权限范围、部门组织",
    icon: Key,
    action: () => void router.push("/iam")
  },
  {
    id: "plat-audit",
    category: "PLATFORM",
    title: "审计中心与字段级 Diff",
    subtitle: "全链路操作审计日志、Before/After 字段对照对比",
    icon: Lock,
    action: () => void router.push("/audit/trail")
  },
  {
    id: "plat-codegen",
    category: "PLATFORM",
    title: "代码生成与 AI 应用引擎 (Codegen)",
    subtitle: "元数据 CRUD 生成与自然语言 AI 建模",
    icon: Cpu,
    action: () => void router.push("/codegen")
  },
  {
    id: "plat-system",
    category: "PLATFORM",
    title: "系统中心与传输安全策略",
    subtitle: "可控传输加密 (X25519+AES-GCM)、安全基线、字典与序列规则",
    icon: Grid,
    action: () => void router.push("/system")
  }
];

const filteredCommands = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase();
  if (!kw) return allCommands;
  return allCommands.filter(
    (c) =>
      c.title.toLowerCase().includes(kw) ||
      c.subtitle.toLowerCase().includes(kw) ||
      c.id.toLowerCase().includes(kw)
  );
});

watch(searchKeyword, () => {
  if (aiResult.value) {
    aiResult.value = null;
  }
});

watch(
  () => props.visible,
  (val) => {
    if (val) {
      searchKeyword.value = "";
      selectedIndex.value = 0;
      aiResult.value = null;
      void nextTick(() => {
        inputRef.value?.focus();
      });
    }
  }
);

async function runAiInterpretation(customInput?: string) {
  const text = (customInput ?? searchKeyword.value).trim();
  if (!text) return;
  searchKeyword.value = text;
  aiLoading.value = true;
  try {
    aiResult.value = await interpretAiCommand({
      input: text,
      currentPath: route.fullPath,
      executeNow: true
    });
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "AI 指令解析失败");
  } finally {
    aiLoading.value = false;
  }
}

interface WorkflowBlueprintNode {
  id: string;
  type: string;
  name: string;
  assigneeRole?: string;
  condition?: string;
}

interface CompoundStepItem {
  step: number;
  action: string;
  title: string;
  status: string;
  route: string;
}

const workflowBlueprintNodes = computed<WorkflowBlueprintNode[]>(() => {
  const bp = aiResult.value?.actionPayload?.workflowBlueprint as { nodes?: WorkflowBlueprintNode[] } | undefined;
  return Array.isArray(bp?.nodes) ? bp.nodes : [];
});

const compoundSteps = computed<CompoundStepItem[]>(() => {
  const steps = aiResult.value?.actionPayload?.steps;
  return Array.isArray(steps) ? (steps as CompoundStepItem[]) : [];
});

async function handleExportFromAiResult(format: "XLSX" | "PDF" | "HTML_REPORT") {
  const res = aiResult.value;
  if (!res) return;
  aiExporting.value = true;
  try {
    if (res.nl2sqlResult && res.nl2sqlResult.rows.length > 0) {
      const nl = res.nl2sqlResult;
      const out = await executeSyncExport({
        taskName: nl.chartTitle || nl.question,
        sourceType: "NL2SQL",
        sourceCode: nl.tableNames[0] || "nl2sql",
        exportFormat: format,
        adHocColumns: nl.columns,
        adHocColumnLabels: nl.columnLabels,
        adHocRows: nl.rows,
        insights: nl.insights,
        includeWatermark: true,
        desensitizeEnabled: true,
        async: false
      });
      ElMessage.success(`已导出 ${format}（追溯码 ${out.traceCode}）`);
    } else {
      const sourceCode = String(res.actionPayload?.sourceCode || "crm_customer");
      const out = await executeSyncExport({
        taskName: String(res.actionPayload?.question || sourceCode),
        sourceType: "DATASET",
        sourceCode,
        exportFormat: format,
        includeWatermark: true,
        desensitizeEnabled: true,
        async: false
      });
      ElMessage.success(`已导出 ${format}（追溯码 ${out.traceCode}）`);
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "导出失败");
  } finally {
    aiExporting.value = false;
  }
}

function handleAiFollowAction() {
  const res = aiResult.value;
  if (!res) return;
  emit("update:visible", false);
  if (res.intentCode === "CREATE_ENTITY") {
    void router.push({
      path: "/codegen/ai-builder",
      query: { prompt: String(res.actionPayload?.prompt || searchKeyword.value), autoDesign: "1" }
    });
    return;
  }
  if (res.intentCode === "QUERY_DATA" || res.intentCode === "CHAT") {
    void router.push({
      path: "/ai/data-copilot",
      query: { q: searchKeyword.value.trim() }
    });
    return;
  }
  void router.push(res.suggestedRoute || "/file/download-center");
}

function handleKeyDown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key === "Enter") {
    e.preventDefault();
    void runAiInterpretation();
    return;
  }
  if (e.key === "ArrowDown") {
    e.preventDefault();
    if (filteredCommands.value.length > 0) {
      selectedIndex.value = (selectedIndex.value + 1) % filteredCommands.value.length;
    }
  } else if (e.key === "ArrowUp") {
    e.preventDefault();
    if (filteredCommands.value.length > 0) {
      selectedIndex.value =
        (selectedIndex.value - 1 + filteredCommands.value.length) % filteredCommands.value.length;
    }
  } else if (e.key === "Enter") {
    e.preventDefault();
    if (aiResult.value) {
      handleAiFollowAction();
    } else if (filteredCommands.value.length > 0 && filteredCommands.value[selectedIndex.value]) {
      executeCommand(filteredCommands.value[selectedIndex.value]);
    } else if (searchKeyword.value.trim()) {
      void runAiInterpretation();
    }
  } else if (e.key === "Escape") {
    emit("update:visible", false);
  }
}

function executeCommand(cmd: CommandItem) {
  emit("update:visible", false);
  cmd.action();
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :show-close="false"
    width="720px"
    class="command-palette-dialog"
    destroy-on-close
    align-center
    @update:model-value="(val) => emit('update:visible', val)"
    @keydown="handleKeyDown"
  >
    <div class="palette-container">
      <div class="palette-search-bar">
        <el-icon class="search-icon"><Search /></el-icon>
        <input
          ref="inputRef"
          v-model="searchKeyword"
          type="text"
          class="search-input"
          placeholder="搜索页面/功能，或直接用自然语言提问、建表、导出报表..."
        />
        <el-button
          v-if="searchKeyword.trim()"
          type="primary"
          size="small"
          :icon="MagicStick"
          :loading="aiLoading"
          @click="runAiInterpretation()"
        >
          AI 执行
        </el-button>
        <kbd class="esc-kbd">ESC</kbd>
      </div>

      <div v-if="!searchKeyword.trim() && !aiResult" class="ai-example-strip">
        <span class="strip-label">✨ 试试自然语言指令：</span>
        <el-tag
          v-for="ex in aiQuickExamples"
          :key="ex"
          size="small"
          effect="plain"
          class="ex-tag"
          @click="runAiInterpretation(ex)"
        >
          {{ ex }}
        </el-tag>
      </div>

      <!-- Inline AI Intent & NL2SQL Execution Preview -->
      <div v-if="aiLoading || aiResult" v-loading="aiLoading" class="ai-result-panel">
        <template v-if="aiResult">
          <div class="ai-result-header">
            <div class="ai-intent-meta">
              <el-tag type="primary" effect="dark" size="small">{{ aiResult.intentLabel }}</el-tag>
              <span class="confidence-pill">置信度 {{ Math.round(aiResult.confidence * 100) }}%</span>
            </div>
            <div class="ai-header-actions">
              <el-button
                v-if="aiResult.nl2sqlResult || aiResult.intentCode === 'EXPORT_DATA' || aiResult.intentCode === 'COMPOUND_PLAN'"
                size="small"
                :icon="Download"
                :loading="aiExporting"
                @click="handleExportFromAiResult('XLSX')"
              >
                导出 Excel
              </el-button>
              <el-button
                v-if="aiResult.nl2sqlResult || aiResult.intentCode === 'EXPORT_DATA' || aiResult.intentCode === 'COMPOUND_PLAN'"
                size="small"
                :icon="Document"
                :loading="aiExporting"
                @click="handleExportFromAiResult('PDF')"
              >
                生成 PDF
              </el-button>
              <el-button
                v-if="aiResult.nl2sqlResult || aiResult.intentCode === 'EXPORT_DATA' || aiResult.intentCode === 'COMPOUND_PLAN'"
                size="small"
                :icon="Document"
                :loading="aiExporting"
                @click="handleExportFromAiResult('HTML_REPORT')"
              >
                分析报告
              </el-button>
              <el-button type="primary" size="small" :icon="Promotion" @click="handleAiFollowAction">
                {{ aiResult.intentCode === "CREATE_ENTITY" ? "前往 AI 应用工坊建模" : "在工作台展开" }}
              </el-button>
            </div>
          </div>

          <p class="ai-explanation">{{ aiResult.explanation }}</p>

          <div v-if="compoundSteps.length" style="display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 10px;">
            <el-tag
              v-for="s in compoundSteps"
              :key="s.step"
              :type="s.status === 'DONE' ? 'success' : 'primary'"
              effect="light"
              size="small"
            >
              Step {{ s.step }}: {{ s.title }}
            </el-tag>
          </div>

          <div v-if="workflowBlueprintNodes.length" style="display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-bottom: 10px;">
            <template v-for="(node, idx) in workflowBlueprintNodes" :key="node.id">
              <el-tag
                size="small"
                :type="node.type === 'GATEWAY' ? 'warning' : node.type === 'APPROVAL' ? 'primary' : 'success'"
              >
                {{ node.name }}
              </el-tag>
              <span v-if="idx < workflowBlueprintNodes.length - 1" style="color: #94a3b8; font-size: 12px;">→</span>
            </template>
          </div>

          <div v-if="aiResult.nl2sqlResult" class="nl2sql-mini-preview">
            <div v-if="aiResult.nl2sqlResult.insights.length" class="mini-insights">
              💡 {{ aiResult.nl2sqlResult.insights[0] }}
            </div>
            <el-table
              v-if="aiResult.nl2sqlResult.rows.length"
              :data="aiResult.nl2sqlResult.rows.slice(0, 6)"
              size="small"
              border
              max-height="210"
            >
              <el-table-column
                v-for="col in aiResult.nl2sqlResult.columns.slice(0, 6)"
                :key="col"
                :prop="col"
                :label="aiResult.nl2sqlResult.columnLabels[col] || col"
                show-overflow-tooltip
              />
            </el-table>
            <div class="mini-sql-line">
              <code>{{ aiResult.nl2sqlResult.sql }}</code>
            </div>
          </div>
        </template>
      </div>

      <!-- Standard Command Search List -->
      <div v-else-if="filteredCommands.length > 0" class="palette-list">
        <div
          v-for="(cmd, idx) in filteredCommands"
          :key="cmd.id"
          class="palette-item"
          :class="{ 'is-selected': idx === selectedIndex }"
          @mouseenter="selectedIndex = idx"
          @click="executeCommand(cmd)"
        >
          <div class="item-icon-box">
            <el-icon><component :is="cmd.icon" /></el-icon>
          </div>
          <div class="item-info">
            <div class="item-title">
              {{ cmd.title }}
              <span class="category-tag">
                {{ cmd.category === "FAST_ACTION" ? "指令" : cmd.category === "APPS" ? "应用" : "底座" }}
              </span>
            </div>
            <div class="item-subtitle">{{ cmd.subtitle }}</div>
          </div>
          <div v-if="idx === selectedIndex" class="item-enter-hint">
            <kbd>↵ Enter</kbd>
          </div>
        </div>
      </div>

      <div v-else class="palette-empty">
        <el-icon class="empty-icon"><MagicStick /></el-icon>
        <p>没有匹配的固定菜单 — 直接按 <strong>Enter</strong> 让 AI 智能执行「{{ searchKeyword }}」</p>
      </div>

      <div class="palette-footer">
        <span class="footer-tip"><kbd>↑</kbd> <kbd>↓</kbd> 导航</span>
        <span class="footer-tip"><kbd>Enter</kbd> 执行/跳转</span>
        <span class="footer-tip"><kbd>⌘/Ctrl</kbd>+<kbd>Enter</kbd> 强制 AI 解析</span>
        <span class="footer-tip"><kbd>Esc</kbd> 关闭</span>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
:deep(.el-dialog.command-palette-dialog) {
  border-radius: 12px;
  overflow: hidden;
  padding: 0;
  box-shadow: 0 20px 45px rgb(15 23 42 / 0.25);
  border: 1px solid rgb(226 232 240 / 0.8);
}

:deep(.el-dialog__header) {
  display: none;
}

:deep(.el-dialog__body) {
  padding: 0;
}

.palette-container {
  display: flex;
  flex-direction: column;
  background: #fff;
}

.palette-search-bar {
  display: flex;
  align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid #f1f5f9;
  gap: 10px;
}

.search-icon {
  font-size: 20px;
  color: #94a3b8;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 15px;
  color: #0f172a;
  background: transparent;
}

.search-input::placeholder {
  color: #94a3b8;
}

.esc-kbd {
  font-size: 11px;
  padding: 3px 6px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  color: #64748b;
  font-family: monospace;
}

.ai-example-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  background: #f8fafc;
  border-bottom: 1px solid #f1f5f9;
}

.strip-label {
  font-size: 12px;
  font-weight: 600;
  color: #2563eb;
}

.ex-tag {
  cursor: pointer;
}

.ai-result-panel {
  padding: 16px 20px;
  min-height: 120px;
}

.ai-result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.ai-intent-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.confidence-pill {
  font-size: 12px;
  color: #64748b;
}

.ai-header-actions {
  display: flex;
  gap: 6px;
}

.ai-explanation {
  margin: 0 0 12px;
  font-size: 13px;
  color: #334155;
}

.nl2sql-mini-preview {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mini-insights {
  font-size: 12px;
  padding: 8px 12px;
  background: #eff6ff;
  border-left: 3px solid #2563eb;
  border-radius: 4px;
  color: #1e3a8a;
}

.mini-sql-line {
  font-size: 11px;
  color: #64748b;
  background: #f8fafc;
  padding: 6px 8px;
  border-radius: 4px;
  overflow-x: auto;
}

.palette-list {
  max-height: 380px;
  overflow-y: auto;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.palette-item {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
  gap: 14px;
}

.palette-item.is-selected {
  background: #eff6ff;
}

.item-icon-box {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #f8fafc;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: #475569;
  border: 1px solid #e2e8f0;
  transition: all 0.15s;
}

.palette-item.is-selected .item-icon-box {
  background: #2563eb;
  color: #fff;
  border-color: #2563eb;
}

.item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.item-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 8px;
}

.category-tag {
  font-size: 10px;
  padding: 1px 5px;
  background: #e2e8f0;
  color: #475569;
  border-radius: 3px;
  font-weight: normal;
}

.palette-item.is-selected .category-tag {
  background: #dbeafe;
  color: #1d4ed8;
}

.item-subtitle {
  font-size: 12px;
  color: #64748b;
}

.item-enter-hint kbd {
  font-size: 11px;
  padding: 2px 6px;
  background: #dbeafe;
  border-radius: 4px;
  color: #1d4ed8;
  font-family: monospace;
}

.palette-empty {
  padding: 36px 20px;
  text-align: center;
  color: #64748b;
}

.empty-icon {
  font-size: 30px;
  margin-bottom: 8px;
  color: #2563eb;
}

.palette-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 16px;
  padding: 10px 20px;
  background: #f8fafc;
  border-top: 1px solid #f1f5f9;
  font-size: 11px;
  color: #94a3b8;
}

.footer-tip kbd {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 3px;
  padding: 1px 4px;
  font-family: monospace;
  color: #64748b;
}
</style>
