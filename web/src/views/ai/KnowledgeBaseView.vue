<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Collection,
  Delete,
  DocumentChecked,
  MagicStick,
  Plus,
  RefreshRight,
  Search,
  Upload
} from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import {
  askKnowledge,
  deleteKnowledgeDoc,
  fetchKnowledgeBases,
  fetchKnowledgeChunks,
  fetchKnowledgeDocs,
  ingestKnowledgeText,
  reviewDocumentCompliance,
  saveKnowledgeBase,
  uploadKnowledgeDoc,
  type DocumentReviewResponse,
  type KnowledgeAskResponse,
  type KnowledgeBaseRow,
  type KnowledgeChunkRow,
  type KnowledgeDocRow
} from "@/api/modules/ai";

const loading = ref(false);
const kbs = ref<KnowledgeBaseRow[]>([]);
const docs = ref<KnowledgeDocRow[]>([]);
const activeKbId = ref<number | undefined>(undefined);
const activeRightTab = ref<"rag" | "review">("rag");

// RAG Ask
const askInput = ref("");
const asking = ref(false);
const askResult = ref<KnowledgeAskResponse | null>(null);
const quickQuestions = [
  "公司商务合同的首付款比例和尾款账期红线分别是多少？",
  "违约赔偿责任上限如何约定？是否允许无限连带赔偿？",
  "多租户导出在什么情况下会触发管理员审批或强制脱敏？"
];

// Document Compliance Review
const reviewTitle = ref("某云计算项目补充合作协议（待审版）");
const reviewText = ref(
  "第一条 付款方式：本合同总金额人民币 800,000 元，签署后首付款 10%（即 80,000 元），其余 90% 尾款在项目验收完成 90 天后分期支付。\n"
  + "第二条 违约责任：如乙方交付延期或出现系统故障，乙方须承担甲方全部间接损失及无限连带赔偿责任。\n"
  + "第三条 知识产权：本协议签署之日起，乙方交付的全部源码及通用组件知识产权永久无条件全部归甲方所有。"
);
const reviewing = ref(false);
const reviewResult = ref<DocumentReviewResponse | null>(null);

// Dialogs
const kbDialogVisible = ref(false);
const savingKb = ref(false);
const kbForm = reactive({
  kbName: "",
  category: "COMPLIANCE",
  chunkSize: 480,
  chunkOverlap: 80,
  remark: ""
});

const textDialogVisible = ref(false);
const savingText = ref(false);
const textForm = reactive({
  docName: "",
  fileType: "MD",
  content: ""
});

const chunkDrawerVisible = ref(false);
const chunkDocTitle = ref("");
const chunksLoading = ref(false);
const docChunks = ref<KnowledgeChunkRow[]>([]);
const fileInputRef = ref<HTMLInputElement | null>(null);
const uploading = ref(false);

const activeKb = computed(() => kbs.value.find((k) => k.id === activeKbId.value));

async function loadKbsAndDocs() {
  loading.value = true;
  try {
    kbs.value = await fetchKnowledgeBases();
    if (!activeKbId.value && kbs.value.length > 0) {
      activeKbId.value = kbs.value[0].id;
    }
    docs.value = await fetchKnowledgeDocs(activeKbId.value);
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "加载知识库失败");
  } finally {
    loading.value = false;
  }
}

async function selectKb(id: number) {
  activeKbId.value = id;
  docs.value = await fetchKnowledgeDocs(id);
}

async function handleCreateKb() {
  if (!kbForm.kbName.trim()) {
    ElMessage.warning("请输入知识库名称");
    return;
  }
  savingKb.value = true;
  try {
    const created = await saveKnowledgeBase({
      kbName: kbForm.kbName.trim(),
      category: kbForm.category,
      chunkSize: kbForm.chunkSize,
      chunkOverlap: kbForm.chunkOverlap,
      remark: kbForm.remark
    });
    ElMessage.success(`知识库「${created.kbName}」已创建`);
    kbDialogVisible.value = false;
    kbForm.kbName = "";
    kbForm.remark = "";
    activeKbId.value = created.id;
    await loadKbsAndDocs();
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "创建知识库失败");
  } finally {
    savingKb.value = false;
  }
}

function triggerFilePick() {
  if (!activeKbId.value) {
    ElMessage.warning("请先在左侧选择目标知识库");
    return;
  }
  fileInputRef.value?.click();
}

async function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file || !activeKbId.value) return;
  uploading.value = true;
  try {
    const doc = await uploadKnowledgeDoc(activeKbId.value, file);
    ElMessage.success(`文档「${doc.docName}」已完成解析并切分 ${doc.chunkCount} 个语义块`);
    await loadKbsAndDocs();
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "上传解析文档失败");
  } finally {
    uploading.value = false;
    input.value = "";
  }
}

async function handleIngestText() {
  if (!activeKbId.value || !textForm.docName.trim() || !textForm.content.trim()) {
    ElMessage.warning("请填写文档标题和正文内容");
    return;
  }
  savingText.value = true;
  try {
    const doc = await ingestKnowledgeText({
      kbId: activeKbId.value,
      docName: textForm.docName.trim(),
      fileType: textForm.fileType,
      content: textForm.content.trim()
    });
    ElMessage.success(`文本已索引入库（${doc.chunkCount} 个语义切片）`);
    textDialogVisible.value = false;
    textForm.docName = "";
    textForm.content = "";
    await loadKbsAndDocs();
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "文本入库失败");
  } finally {
    savingText.value = false;
  }
}

async function openChunkInspector(doc: KnowledgeDocRow) {
  chunkDocTitle.value = doc.docName;
  chunkDrawerVisible.value = true;
  chunksLoading.value = true;
  try {
    docChunks.value = await fetchKnowledgeChunks(doc.id);
  } catch {
    docChunks.value = [];
  } finally {
    chunksLoading.value = false;
  }
}

async function handleDeleteDoc(doc: KnowledgeDocRow) {
  try {
    await ElMessageBox.confirm(`确认删除文档「${doc.docName}」及其向量索引切片吗？`, "删除确认", { type: "warning" });
    await deleteKnowledgeDoc(doc.id);
    ElMessage.success("文档及索引已清理");
    await loadKbsAndDocs();
  } catch {
    // cancelled
  }
}

async function handleAsk(custom?: string) {
  const q = (custom ?? askInput.value).trim();
  if (!q) {
    ElMessage.warning("请输入您想检索的制度或业务问题");
    return;
  }
  askInput.value = q;
  asking.value = true;
  try {
    askResult.value = await askKnowledge({
      kbId: activeKbId.value,
      question: q,
      topK: 4
    });
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "混合检索问答失败");
  } finally {
    asking.value = false;
  }
}

async function handleReviewDocument() {
  if (!reviewText.value.trim()) {
    ElMessage.warning("请粘贴待审查的合同或标书条款内容");
    return;
  }
  reviewing.value = true;
  try {
    reviewResult.value = await reviewDocumentCompliance({
      kbId: activeKbId.value,
      documentTitle: reviewTitle.value.trim() || "待审协议",
      documentText: reviewText.value.trim()
    });
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "合同合审失败");
  } finally {
    reviewing.value = false;
  }
}

onMounted(() => {
  void loadKbsAndDocs();
});
</script>

<template>
  <ControlSurface
    title="AI 企业知识库与合同合审 (Hybrid RAG)"
    description="DOCX / XLSX / Markdown 智能解析切分 · BM25 + 向量 RRF 混合召回 · 权威页码溯源引用 · 合同条款自动风险体检"
    badge="Enterprise RAG"
  >
    <template #actions>
      <el-button :icon="RefreshRight" :loading="loading" @click="loadKbsAndDocs">刷新</el-button>
      <el-button :icon="Plus" @click="kbDialogVisible = true">新建知识库</el-button>
      <el-button type="primary" :icon="DocumentChecked" @click="activeRightTab = 'review'">合同条款体检</el-button>
    </template>

    <input
      ref="fileInputRef"
      type="file"
      accept=".txt,.md,.csv,.json,.html,.docx,.xlsx"
      style="display: none;"
      @change="handleFileChange"
    >

    <div class="kb-layout">
      <!-- Left: KB Selector & Docs -->
      <div class="kb-sidebar">
        <BaseCard class="kb-panel">
          <div class="panel-head">
            <h3>知识库列表 ({{ kbs.length }})</h3>
          </div>
          <div class="kb-list">
            <div
              v-for="kb in kbs"
              :key="kb.id"
              class="kb-card"
              :class="{ 'is-active': kb.id === activeKbId }"
              @click="selectKb(kb.id)"
            >
              <div class="kb-card-top">
                <el-icon><Collection /></el-icon>
                <strong>{{ kb.kbName }}</strong>
              </div>
              <div class="kb-card-meta">
                <el-tag size="small" effect="plain">{{ kb.category }}</el-tag>
                <span>{{ kb.docCount }} 文档 · {{ kb.chunkCount }} 切片</span>
              </div>
              <p v-if="kb.remark" class="kb-remark">{{ kb.remark }}</p>
            </div>
          </div>
        </BaseCard>

        <BaseCard class="docs-panel">
          <div class="panel-head">
            <h3>知识文档 ({{ docs.length }})</h3>
            <div class="head-btns">
              <el-button size="small" :icon="Upload" :loading="uploading" @click="triggerFilePick">上传文件</el-button>
              <el-button size="small" type="primary" plain :icon="Plus" @click="textDialogVisible = true">录入文本</el-button>
            </div>
          </div>

          <el-table :data="docs" size="small" stripe max-height="360">
            <el-table-column prop="docName" label="文档标题" min-width="115" show-overflow-tooltip />
            <el-table-column prop="fileType" label="格式" width="54" align="center" />
            <el-table-column prop="chunkCount" label="切片" width="48" align="center" />
            <el-table-column label="操作" width="84" align="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openChunkInspector(row)">切片</el-button>
                <el-button link type="danger" size="small" :icon="Delete" @click="handleDeleteDoc(row)" />
              </template>
            </el-table-column>
          </el-table>
        </BaseCard>
      </div>

      <!-- Right: Hybrid RAG QA & Contract Compliance Review -->
      <BaseCard class="rag-workspace">
        <div class="workspace-tabs">
          <el-radio-group v-model="activeRightTab">
            <el-radio-button value="rag">混合检索智能问答 (RAG)</el-radio-button>
            <el-radio-button value="review">智能合同 / 标书红线审查</el-radio-button>
          </el-radio-group>
          <span v-if="activeKb" class="active-kb-badge">
            当前作用域：{{ activeKb.kbName }} (Chunk={{ activeKb.chunkSize }})
          </span>
        </div>

        <template v-if="activeRightTab === 'rag'">
          <div class="rag-search-box">
            <el-input
              v-model="askInput"
              size="large"
              placeholder="输入规章制度、合同时效、风控标准等问题，自动附带原文引用页码..."
              clearable
              @keyup.enter="handleAsk()"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
              <template #append>
                <el-button type="primary" :loading="asking" @click="handleAsk()">检索回答</el-button>
              </template>
            </el-input>

            <div class="quick-prompts">
              <span class="prompt-lbl">常用检索：</span>
              <el-tag
                v-for="q in quickQuestions"
                :key="q"
                size="small"
                effect="plain"
                class="q-tag"
                @click="handleAsk(q)"
              >
                {{ q }}
              </el-tag>
            </div>
          </div>

          <div v-if="asking || askResult" v-loading="asking" class="rag-answer-area">
            <template v-if="askResult">
              <div class="answer-box">
                <div class="answer-meta">
                  <el-tag size="small" type="success">{{ askResult.engine }}</el-tag>
                  <span class="latency-txt">耗时 {{ askResult.latencyMs }} ms · 命中 {{ askResult.citations.length }} 条依据</span>
                </div>
                <div class="answer-body">{{ askResult.answer }}</div>
              </div>

              <div v-if="askResult.citations.length" class="citations-grid">
                <div v-for="cit in askResult.citations" :key="cit.chunkId" class="citation-card">
                  <div class="cit-header">
                    <el-tag size="small" type="primary" effect="dark">[{{ cit.citationNo }}]</el-tag>
                    <strong class="cit-doc">《{{ cit.docName }}》</strong>
                    <span class="cit-page">P{{ cit.pageNo }} · {{ cit.sectionTitle || "正文" }}</span>
                    <span class="cit-score">RRF {{ cit.score }}</span>
                  </div>
                  <p class="cit-excerpt">{{ cit.excerpt }}</p>
                </div>
              </div>
            </template>
          </div>
          <el-empty v-else description="输入问题或点击上方推荐问题，体验 BM25 + 向量混合召回与页码引用溯源" />
        </template>

        <!-- Contract Compliance Review -->
        <template v-else>
          <div class="review-form">
            <div class="review-row">
              <el-input v-model="reviewTitle" placeholder="待审查合同或标书标题" style="max-width: 440px" />
              <el-button type="primary" :icon="MagicStick" :loading="reviewing" @click="handleReviewDocument">
                开始红线合审体检
              </el-button>
            </div>
            <el-input
              v-model="reviewText"
              type="textarea"
              :rows="5"
              placeholder="请粘贴商务合同付款、违约赔偿、知识产权或采购招标核心条款..."
            />
          </div>

          <div v-if="reviewResult" v-loading="reviewing" class="review-output">
            <div class="review-score-bar">
              <div class="score-badge" :class="reviewResult.highRiskCount > 0 ? 'is-danger' : 'is-safe'">
                <span class="score-num">{{ reviewResult.complianceScore }}</span>
                <span class="score-lbl">合规分</span>
              </div>
              <div class="score-summary">
                <div class="risk-counters">
                  <el-tag type="danger" size="small">高风险 {{ reviewResult.highRiskCount }} 项</el-tag>
                  <el-tag type="warning" size="small">中风险 {{ reviewResult.mediumRiskCount }} 项</el-tag>
                </div>
                <p>{{ reviewResult.summary }}</p>
              </div>
            </div>

            <div class="risk-list">
              <div v-for="(item, idx) in reviewResult.risks" :key="idx" class="risk-card">
                <div class="risk-card-head">
                  <el-tag
                    size="small"
                    :type="item.riskLevel === 'HIGH' ? 'danger' : item.riskLevel === 'MEDIUM' ? 'warning' : 'success'"
                  >
                    {{ item.riskLevel }} · {{ item.category }}
                  </el-tag>
                  <span class="policy-ref">依据：{{ item.policyCitation }}</span>
                </div>
                <div class="risk-excerpt">原文片段：{{ item.clauseExcerpt }}</div>
                <div class="risk-issue">风险诊断：{{ item.issueSummary }}</div>
                <div class="risk-fix">修订建议：{{ item.suggestedRevision }}</div>
              </div>
            </div>
          </div>
        </template>
      </BaseCard>
    </div>

    <!-- Create KB Dialog -->
    <el-dialog v-model="kbDialogVisible" title="新建企业知识库" width="480px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="知识库名称" required>
          <el-input v-model="kbForm.kbName" placeholder="如：研发交付与安全标准库" />
        </el-form-item>
        <el-form-item label="领域分类">
          <el-select v-model="kbForm.category" style="width: 100%">
            <el-option label="法务与风控合规" value="COMPLIANCE" />
            <el-option label="产品与运营 SOP" value="OPERATIONS" />
            <el-option label="技术架构与研发规范" value="ENGINEERING" />
          </el-select>
        </el-form-item>
        <el-form-item label="切片长度">
          <el-input-number v-model="kbForm.chunkSize" :min="150" :max="1200" :step="60" />
        </el-form-item>
        <el-form-item label="说明备注">
          <el-input v-model="kbForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kbDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingKb" @click="handleCreateKb">创建知识库</el-button>
      </template>
    </el-dialog>

    <!-- Text Ingest Dialog -->
    <el-dialog v-model="textDialogVisible" title="直接录入企业制度文本" width="560px" destroy-on-close>
      <el-form label-width="88px">
        <el-form-item label="文档标题" required>
          <el-input v-model="textForm.docName" placeholder="如：售后 SLA 响应与赔付规范.md" />
        </el-form-item>
        <el-form-item label="正文内容" required>
          <el-input
            v-model="textForm.content"
            type="textarea"
            :rows="7"
            placeholder="支持 # 标题分段，系统将按段落与滑窗自动切分并生成混合检索向量..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="textDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingText" @click="handleIngestText">切分并建索引</el-button>
      </template>
    </el-dialog>

    <!-- Chunk Inspector Drawer -->
    <el-drawer v-model="chunkDrawerVisible" :title="`语义切片透视 · ${chunkDocTitle}`" size="520px">
      <div v-loading="chunksLoading" class="chunk-drawer-list">
        <div v-for="ch in docChunks" :key="ch.id" class="chunk-item">
          <div class="chunk-meta">
            <el-tag size="small" type="info">#{{ ch.chunkIndex + 1 }} · 第 {{ ch.pageNo }} 页</el-tag>
            <strong>{{ ch.sectionTitle || "正文段落" }}</strong>
          </div>
          <p class="chunk-content">{{ ch.content }}</p>
          <div v-if="ch.keywords" class="chunk-kws">关键词：{{ ch.keywords }}</div>
        </div>
      </div>
    </el-drawer>
  </ControlSurface>
</template>

<style scoped lang="scss">
.kb-layout {
  display: grid;
  grid-template-columns: 380px minmax(0, 1fr);
  gap: 16px;
}

.kb-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;

  h3 {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
  }
}

.head-btns {
  display: flex;
  gap: 6px;
}

.kb-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.kb-card {
  padding: 14px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  transition: all 0.16s ease;

  &:hover {
    border-color: #93c5fd;
    background: #f8fafc;
  }

  &.is-active {
    border-color: #2563eb;
    background: #eff6ff;
    box-shadow: 0 2px 10px rgba(37, 99, 235, 0.10);
  }
}

.kb-card-top {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  color: #0f172a;
}

.kb-card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #334155;
  font-weight: 500;
}

.kb-remark {
  margin: 6px 0 0;
  font-size: 12px;
  color: #475569;
  line-height: 1.5;
}

.workspace-tabs {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 10px;
}

.active-kb-badge {
  font-size: 12px;
  color: #334155;
  font-weight: 500;
  padding: 4px 12px;
  border-radius: 999px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}

.quick-prompts {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.prompt-lbl {
  font-size: 12px;
  color: #475569;
  font-weight: 500;
}

.q-tag {
  cursor: pointer;
}

.answer-box {
  margin-top: 16px;
  padding: 16px;
  border-radius: 12px;
  background: rgba(239, 246, 255, 0.94);
  border: 1px solid #bfdbfe;
}

.answer-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.latency-txt {
  font-size: 12px;
  color: #475569;
}

.answer-body {
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  color: #0f172a;
}

.citations-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.citation-card {
  padding: 12px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
}

.cit-header {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 6px;
  font-size: 12px;
  color: #0f172a;
}

.cit-page,
.cit-score {
  color: #475569;
}

.cit-excerpt {
  margin: 0;
  font-size: 12px;
  color: #334155;
  line-height: 1.5;
}

.review-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.review-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.review-output {
  margin-top: 16px;
}

.review-score-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  margin-bottom: 12px;
}

.score-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 68px;
  height: 68px;
  border-radius: 12px;
  color: #fff;
  flex-shrink: 0;

  &.is-danger {
    background: linear-gradient(135deg, #dc2626, #ef4444);
  }

  &.is-safe {
    background: linear-gradient(135deg, #059669, #10b981);
  }
}

.score-num {
  font-size: 22px;
  font-weight: 700;
}

.score-lbl {
  font-size: 11px;
}

.score-summary p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #334155;
}

.risk-counters {
  display: flex;
  gap: 8px;
}

.risk-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.risk-card {
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid rgba(148 163 184 / 0.25);
  background: #fff;
}

.risk-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.policy-ref {
  font-size: 12px;
  color: #475569;
}

.risk-excerpt,
.risk-issue,
.risk-fix {
  font-size: 12px;
  line-height: 1.55;
  margin-top: 4px;
  color: #334155;
}

.risk-fix {
  color: #0f766e;
  font-weight: 500;
}

.chunk-drawer-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chunk-item {
  padding: 12px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.chunk-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.chunk-content {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
}

.chunk-kws {
  margin-top: 6px;
  font-size: 11px;
  color: #475569;
}

@media (max-width: 1100px) {
  .kb-layout,
  .citations-grid {
    grid-template-columns: 1fr;
  }
}
</style>
