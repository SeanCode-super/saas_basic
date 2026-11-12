<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  Check,
  Close,
  DataLine,
  Download,
  MagicStick,
  Plus,
  RefreshRight
} from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import {
  addFinetuneSample,
  exportFinetuneJsonl,
  fetchFinetuneDatasets,
  fetchFinetuneJobs,
  fetchFinetuneSamples,
  harvestNl2SqlSamples,
  reviewFinetuneSample,
  saveFinetuneDataset,
  startFinetuneJob,
  type FinetuneDatasetRow,
  type FinetuneJobRow,
  type FinetuneSampleRow
} from "@/api/modules/ai";

const loading = ref(false);
const datasets = ref<FinetuneDatasetRow[]>([]);
const samples = ref<FinetuneSampleRow[]>([]);
const jobs = ref<FinetuneJobRow[]>([]);
const activeDatasetId = ref<number | undefined>(undefined);
const activeTab = ref<"samples" | "jobs">("samples");

const sampleDialogVisible = ref(false);
const savingSample = ref(false);
const sampleForm = reactive({
  instruction: "将企业数据分析提问转为安全只读 SELECT SQL",
  inputText: "",
  outputText: "",
  desensitize: true
});

const datasetDialogVisible = ref(false);
const savingDataset = ref(false);
const datasetForm = reactive({
  datasetName: "",
  taskType: "NL2SQL",
  formatType: "ALPACA",
  remark: ""
});

const jobDialogVisible = ref(false);
const startingJob = ref(false);
const jobForm = reactive({
  jobName: "",
  baseModel: "Qwen2.5-7B-Instruct",
  tuningMethod: "LORA",
  epochs: 3,
  learningRate: "2e-4",
  loraRank: 16
});

const activeDataset = computed(() => datasets.value.find((d) => d.id === activeDatasetId.value));
const latestJob = computed(() => jobs.value[0]);

async function loadAll() {
  loading.value = true;
  try {
    const [dsRows, jobRows] = await Promise.all([
      fetchFinetuneDatasets(),
      fetchFinetuneJobs()
    ]);
    datasets.value = dsRows;
    jobs.value = jobRows;
    if (!activeDatasetId.value && dsRows.length > 0) {
      activeDatasetId.value = dsRows[0].id;
    }
    samples.value = await fetchFinetuneSamples(activeDatasetId.value);
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "加载微调平台失败");
  } finally {
    loading.value = false;
  }
}

async function selectDataset(id: number) {
  activeDatasetId.value = id;
  samples.value = await fetchFinetuneSamples(id);
}

async function handleHarvestNl2Sql() {
  if (!activeDatasetId.value) return;
  try {
    const res = await harvestNl2SqlSamples(activeDatasetId.value, 15);
    ElMessage.success(`已从历史 NL2SQL 成功查询中脱敏回流 ${res.imported} 条训练样本`);
    await loadAll();
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "采集样本失败");
  }
}

async function handleReviewSample(sample: FinetuneSampleRow, status: "APPROVED" | "REJECTED") {
  try {
    await reviewFinetuneSample(sample.id, status);
    ElMessage.success(status === "APPROVED" ? "样本已核准加入训练集" : "样本已剔除");
    await loadAll();
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "样本审核失败");
  }
}

async function handleExportJsonl(formatType: "ALPACA" | "SHAREGPT") {
  if (!activeDatasetId.value) return;
  try {
    await exportFinetuneJsonl(activeDatasetId.value, formatType);
    ElMessage.success(`已导出已核准并自动脱敏的 ${formatType} 格式 .jsonl 训练集`);
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "导出失败");
  }
}

async function handleCreateDataset() {
  if (!datasetForm.datasetName.trim()) {
    ElMessage.warning("请输入数据集名称");
    return;
  }
  savingDataset.value = true;
  try {
    const created = await saveFinetuneDataset({ ...datasetForm });
    ElMessage.success(`数据集「${created.datasetName}」已创建`);
    datasetDialogVisible.value = false;
    datasetForm.datasetName = "";
    activeDatasetId.value = created.id;
    await loadAll();
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "创建数据集失败");
  } finally {
    savingDataset.value = false;
  }
}

async function handleAddSample() {
  if (!activeDatasetId.value || !sampleForm.outputText.trim()) {
    ElMessage.warning("请填写期望标准输出");
    return;
  }
  savingSample.value = true;
  try {
    await addFinetuneSample({
      datasetId: activeDatasetId.value,
      ...sampleForm
    });
    ElMessage.success("训练样本已自动 PII 脱敏并入库");
    sampleDialogVisible.value = false;
    sampleForm.inputText = "";
    sampleForm.outputText = "";
    await loadAll();
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "添加样本失败");
  } finally {
    savingSample.value = false;
  }
}

async function handleStartJob() {
  if (!activeDatasetId.value || !jobForm.jobName.trim()) {
    ElMessage.warning("请输入微调任务名称并选择训练集");
    return;
  }
  startingJob.value = true;
  try {
    const job = await startFinetuneJob({
      datasetId: activeDatasetId.value,
      ...jobForm
    });
    ElMessage.success(`LoRA 微调任务「${job.jobName}」评测完成，准确率 ${(job.evalAccuracy * 100).toFixed(1)}%`);
    jobDialogVisible.value = false;
    jobForm.jobName = "";
    activeTab.value = "jobs";
    await loadAll();
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "启动微调任务失败");
  } finally {
    startingJob.value = false;
  }
}

onMounted(() => {
  void loadAll();
});
</script>

<template>
  <ControlSurface
    title="AI 模型微调与评测工坊 (Fine-Tune Studio)"
    description="企业私域 SFT 数据集治理 · NL2SQL 历史脱敏回流 · Alpaca / ShareGPT JSONL 导出 · LoRA 微调与 A/B 准确率评估"
    badge="Fine-Tune Studio"
  >
    <template #actions>
      <el-button :icon="RefreshRight" :loading="loading" @click="loadAll">刷新</el-button>
      <el-button :icon="Plus" @click="datasetDialogVisible = true">新建训练集</el-button>
      <el-button type="primary" :icon="DataLine" @click="jobDialogVisible = true">发起 LoRA 微调</el-button>
    </template>

    <!-- Evaluation A/B Comparison Strip -->
    <div v-if="latestJob" class="eval-banner">
      <div class="eval-left">
        <span class="eval-eyebrow">最新微调基准评估 · {{ latestJob.jobName }} ({{ latestJob.outputModelId }})</span>
        <div class="eval-metrics">
          <div class="metric-item">
            <span>基座准确率 (Baseline)</span>
            <strong>{{ (latestJob.baselineAccuracy * 100).toFixed(1) }}%</strong>
          </div>
          <div class="metric-arrow">→</div>
          <div class="metric-item is-highlight">
            <span>微调后准确率 (LoRA r={{ latestJob.loraRank }})</span>
            <strong>{{ (latestJob.evalAccuracy * 100).toFixed(1) }}%</strong>
          </div>
          <div class="metric-item">
            <span>最终 Train Loss</span>
            <strong>{{ latestJob.trainLoss }}</strong>
          </div>
        </div>
      </div>

      <div class="loss-bars" v-if="latestJob.metrics?.lossCurve?.length">
        <span class="loss-lbl">收敛曲线 (Loss):</span>
        <div class="loss-track">
          <div
            v-for="(val, i) in latestJob.metrics.lossCurve"
            :key="i"
            class="loss-bar"
            :style="{ height: `${Math.max(18, Math.min(100, val * 100))}%` }"
            :title="`Step ${i + 1}: Loss ${val}`"
          />
        </div>
      </div>
    </div>

    <div class="ft-grid">
      <!-- Left: Datasets -->
      <BaseCard class="ds-panel">
        <div class="panel-head">
          <h3>微调数据集 ({{ datasets.length }})</h3>
        </div>
        <div class="ds-list">
          <div
            v-for="ds in datasets"
            :key="ds.id"
            class="ds-card"
            :class="{ 'is-active': ds.id === activeDatasetId }"
            @click="selectDataset(ds.id)"
          >
            <div class="ds-title">
              <strong>{{ ds.datasetName }}</strong>
              <el-tag size="small">{{ ds.taskType }}</el-tag>
            </div>
            <div class="ds-meta">
              <span>总样本 {{ ds.sampleCount }} · 已核准 {{ ds.approvedCount }}</span>
              <code>{{ ds.formatType }}</code>
            </div>
            <p v-if="ds.remark" class="ds-remark">{{ ds.remark }}</p>
          </div>
        </div>
      </BaseCard>

      <!-- Right: Samples & Jobs -->
      <BaseCard class="ft-main">
        <div class="toolbar">
          <el-radio-group v-model="activeTab">
            <el-radio-button value="samples">样本标注与脱敏清洗 ({{ samples.length }})</el-radio-button>
            <el-radio-button value="jobs">LoRA 训练任务与 A/B 评估 ({{ jobs.length }})</el-radio-button>
          </el-radio-group>

          <div v-if="activeTab === 'samples' && activeDataset" class="tool-btns">
            <el-button size="small" :icon="MagicStick" @click="handleHarvestNl2Sql">采集 NL2SQL 历史</el-button>
            <el-button size="small" :icon="Plus" @click="sampleDialogVisible = true">手工标注样本</el-button>
            <el-button size="small" type="primary" plain :icon="Download" @click="handleExportJsonl('ALPACA')">
              导出 Alpaca JSONL
            </el-button>
            <el-button size="small" plain :icon="Download" @click="handleExportJsonl('SHAREGPT')">
              ShareGPT JSONL
            </el-button>
          </div>
        </div>

        <el-table v-if="activeTab === 'samples'" v-loading="loading" :data="samples" stripe>
          <el-table-column prop="instruction" label="系统指令 (Instruction)" min-width="180" show-overflow-tooltip />
          <el-table-column prop="inputText" label="业务输入 (Input)" min-width="180" show-overflow-tooltip />
          <el-table-column prop="outputText" label="期望标准输出 (Output)" min-width="220" show-overflow-tooltip />
          <el-table-column label="脱敏 / 来源" width="140">
            <template #default="{ row }">
              <el-tag size="small" type="warning" v-if="row.desensitized">PII已脱敏</el-tag>
              <div class="sub-txt">{{ row.sourceType }}</div>
            </template>
          </el-table-column>
          <el-table-column label="审核状态" width="110">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row.reviewStatus === 'APPROVED' ? 'success' : row.reviewStatus === 'REJECTED' ? 'danger' : 'info'"
              >
                {{ row.reviewStatus }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button link type="success" :icon="Check" @click="handleReviewSample(row, 'APPROVED')">核准</el-button>
              <el-button link type="danger" :icon="Close" @click="handleReviewSample(row, 'REJECTED')">剔除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-table v-else v-loading="loading" :data="jobs" stripe>
          <el-table-column prop="jobName" label="训练任务" min-width="190">
            <template #default="{ row }">
              <strong>{{ row.jobName }}</strong>
              <div class="sub-txt">产出模型: <code>{{ row.outputModelId }}</code></div>
            </template>
          </el-table-column>
          <el-table-column prop="baseModel" label="基座模型" width="170" />
          <el-table-column label="LoRA 超参" width="170">
            <template #default="{ row }">
              {{ row.tuningMethod }} · r={{ row.loraRank }} · {{ row.epochs }} epochs
            </template>
          </el-table-column>
          <el-table-column label="A/B 准确率对比" width="190">
            <template #default="{ row }">
              <span>{{ (row.baselineAccuracy * 100).toFixed(1) }}%</span>
              <span style="margin: 0 6px; color: #94a3b8;">→</span>
              <strong style="color: #059669;">{{ (row.evalAccuracy * 100).toFixed(1) }}%</strong>
            </template>
          </el-table-column>
          <el-table-column prop="trainLoss" label="Train Loss" width="100" />
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" type="success">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </BaseCard>
    </div>

    <!-- Add Dataset Dialog -->
    <el-dialog v-model="datasetDialogVisible" title="新建微调数据集" width="460px" destroy-on-close>
      <el-form label-width="92px">
        <el-form-item label="数据集名称" required>
          <el-input v-model="datasetForm.datasetName" placeholder="例如：法务合规问答微调集 v2" />
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="datasetForm.taskType" style="width: 100%">
            <el-option label="NL2SQL 领域查询" value="NL2SQL" />
            <el-option label="企业合规 SFT 对话" value="SFT_CHAT" />
          </el-select>
        </el-form-item>
        <el-form-item label="导出格式">
          <el-radio-group v-model="datasetForm.formatType">
            <el-radio-button value="ALPACA">Alpaca</el-radio-button>
            <el-radio-button value="SHAREGPT">ShareGPT</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="datasetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingDataset" @click="handleCreateDataset">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- Add Sample Dialog -->
    <el-dialog v-model="sampleDialogVisible" title="录入微调样本（自动触发手机号/邮箱脱敏）" width="560px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="系统指令" required>
          <el-input v-model="sampleForm.instruction" />
        </el-form-item>
        <el-form-item label="用户输入">
          <el-input v-model="sampleForm.inputText" type="textarea" :rows="2" placeholder="用户提问或上下文" />
        </el-form-item>
        <el-form-item label="期望输出" required>
          <el-input v-model="sampleForm.outputText" type="textarea" :rows="4" placeholder="标准 SQL 或合规专业回答" />
        </el-form-item>
        <el-form-item label="隐私防护">
          <el-switch v-model="sampleForm.desensitize" active-text="入库前自动遮掩手机号与邮箱 PII" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sampleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingSample" @click="handleAddSample">保存样本</el-button>
      </template>
    </el-dialog>

    <!-- Start LoRA Job Dialog -->
    <el-dialog v-model="jobDialogVisible" title="配置并启动 LoRA 微调评估任务" width="500px" destroy-on-close>
      <el-form label-width="104px">
        <el-form-item label="任务名称" required>
          <el-input v-model="jobForm.jobName" placeholder="例如：CRM+法务混合领域 Qwen-LoRA-v2" />
        </el-form-item>
        <el-form-item label="基座模型">
          <el-select v-model="jobForm.baseModel" style="width: 100%">
            <el-option label="Qwen2.5-7B-Instruct" value="Qwen2.5-7B-Instruct" />
            <el-option label="DeepSeek-V3-Distill-Qwen-14B" value="DeepSeek-V3-Distill-Qwen-14B" />
            <el-option label="Llama-3.1-8B-Instruct" value="Llama-3.1-8B-Instruct" />
          </el-select>
        </el-form-item>
        <el-form-item label="训练 Epochs">
          <el-input-number v-model="jobForm.epochs" :min="1" :max="12" />
        </el-form-item>
        <el-form-item label="LoRA Rank">
          <el-radio-group v-model="jobForm.loraRank">
            <el-radio-button :value="8">r=8</el-radio-button>
            <el-radio-button :value="16">r=16</el-radio-button>
            <el-radio-button :value="32">r=32</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="jobDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="startingJob" @click="handleStartJob">开始训练评测</el-button>
      </template>
    </el-dialog>
  </ControlSurface>
</template>

<style scoped lang="scss">
.eval-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 22px;
  border-radius: 16px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, #0f172a, #1e3a8a);
  color: #f8fafc;
  flex-wrap: wrap;
}

.eval-eyebrow {
  font-size: 12px;
  color: #93c5fd;
}

.eval-metrics {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 8px;
}

.metric-item {
  display: flex;
  flex-direction: column;
  gap: 2px;

  span {
    font-size: 12px;
    color: #cbd5e1;
  }

  strong {
    font-size: 22px;
  }

  &.is-highlight strong {
    color: #34d399;
  }
}

.metric-arrow {
  font-size: 20px;
  color: #60a5fa;
}

.loss-bars {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.loss-lbl {
  font-size: 12px;
  color: #cbd5e1;
}

.loss-track {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  height: 54px;
}

.loss-bar {
  width: 14px;
  border-radius: 4px 4px 0 0;
  background: linear-gradient(180deg, #38bdf8, #818cf8);
}

.ft-grid {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
}

.panel-head h3 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
}

.ds-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ds-card {
  padding: 12px;
  border-radius: 10px;
  border: 1px solid rgba(148 163 184 / 0.25);
  cursor: pointer;

  &.is-active {
    border-color: #2563eb;
    background: rgba(37 99 235 / 0.06);
  }
}

.ds-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ds-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
}

.ds-remark {
  margin: 6px 0 0;
  font-size: 12px;
  color: #64748b;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.tool-btns {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.sub-txt {
  font-size: 12px;
  color: #64748b;
}

@media (max-width: 1080px) {
  .ft-grid {
    grid-template-columns: 1fr;
  }
}
</style>
