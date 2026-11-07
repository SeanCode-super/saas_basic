<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Cpu,
  Refresh,
  Plus,
  Connection,
  Check,
  Close,
  Edit,
  Delete,
  Key,
  Lightning,
  Coin,
  Setting,
  DocumentCopy,
  CircleCheckFilled,
  CircleCloseFilled,
  WarningFilled,
  Promotion
} from "@element-plus/icons-vue";
import {
  fetchAiProviders,
  saveAiProvider,
  deleteAiProvider,
  fetchAiModels,
  saveAiModel,
  deleteAiModel,
  testAiConnection,
  type AiProviderRow,
  type AiProviderSavePayload,
  type AiModelConfigRow,
  type AiModelConfigSavePayload,
  type AiTestConnectionResult
} from "@/api/modules/ai";

const loading = ref(false);
const activeTab = ref<"providers" | "models">("providers");

// Providers data
const providers = ref<AiProviderRow[]>([]);
const models = ref<AiModelConfigRow[]>([]);

// Test Connection State
const testingMap = ref<Record<string, boolean>>({});
const testResultDialog = ref(false);
const currentTestResult = ref<AiTestConnectionResult | null>(null);
const currentTestingTarget = ref("");

// Provider Edit Dialog
const providerDialogVisible = ref(false);
const providerDialogTitle = ref("新建 AI 服务商");
const providerForm = ref<AiProviderSavePayload>({
  providerCode: "",
  providerName: "",
  providerType: "OPENAI_COMPATIBLE",
  baseUrl: "",
  apiKey: "",
  status: "ENABLED",
  isDefault: 0,
  sortNo: 10,
  remark: ""
});

// Model Edit Dialog
const modelDialogVisible = ref(false);
const modelDialogTitle = ref("新增 AI 模型");
const modelForm = ref<AiModelConfigSavePayload>({
  providerId: 1,
  modelId: "",
  modelName: "",
  category: "CHAT",
  contextWindow: "64k",
  maxTokens: 4096,
  temperature: 0.7,
  isDefault: 0,
  status: "ENABLED",
  sortNo: 10
});

// Computed Stats
const defaultProvider = computed(() => providers.value.find((p) => p.isDefault === 1) || providers.value[0]);
const defaultModel = computed(() => models.value.find((m) => m.isDefault === 1) || models.value[0]);
const totalKeyConfigured = computed(() => providers.value.filter((p) => p.hasApiKey).length);

// Presets for quick provider autofill
const providerPresets = [
  {
    name: "DeepSeek 官方",
    code: "deepseek",
    type: "OPENAI_COMPATIBLE",
    baseUrl: "https://api.deepseek.com",
    remark: "高性价比通用与深度推理模型 (DeepSeek-V3 / R1)"
  },
  {
    name: "OpenAI 官方",
    code: "openai",
    type: "OPENAI_COMPATIBLE",
    baseUrl: "https://api.openai.com/v1",
    remark: "全球标杆模型 (GPT-4o / GPT-4o Mini)"
  },
  {
    name: "阿里通义千问",
    code: "qwen",
    type: "OPENAI_COMPATIBLE",
    baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
    remark: "阿里云百炼兼容模式 (Qwen-Plus / Qwen-Max)"
  },
  {
    name: "本地私有化 Ollama",
    code: "ollama",
    type: "OLLAMA",
    baseUrl: "http://localhost:11434/v1",
    remark: "内网 GPU 私有化部署，零数据外流风险"
  }
];

async function loadData() {
  loading.value = true;
  try {
    const [pList, mList] = await Promise.all([fetchAiProviders(), fetchAiModels()]);
    providers.value = pList || [];
    models.value = mList || [];
  } catch (err: any) {
    ElMessage.error(err?.message || "加载 AI 配置中心数据失败");
  } finally {
    loading.value = false;
  }
}

function copyText(val: string) {
  if (!val) return;
  navigator.clipboard.writeText(val);
  ElMessage.success("已复制到剪贴板");
}

function applyPreset(preset: (typeof providerPresets)[0]) {
  providerForm.value.providerName = preset.name;
  providerForm.value.providerCode = preset.code;
  providerForm.value.providerType = preset.type;
  providerForm.value.baseUrl = preset.baseUrl;
  providerForm.value.remark = preset.remark;
}

// ----------------- Provider Handlers -----------------
function handleAddProvider() {
  providerDialogTitle.value = "新建 AI 服务商";
  providerForm.value = {
    providerCode: "",
    providerName: "",
    providerType: "OPENAI_COMPATIBLE",
    baseUrl: "",
    apiKey: "",
    status: "ENABLED",
    isDefault: providers.value.length === 0 ? 1 : 0,
    sortNo: (providers.value.length + 1) * 10,
    remark: ""
  };
  providerDialogVisible.value = true;
}

function handleEditProvider(row: AiProviderRow) {
  providerDialogTitle.value = `编辑服务商: ${row.providerName}`;
  providerForm.value = {
    id: row.id,
    tenantId: row.tenantId,
    providerCode: row.providerCode,
    providerName: row.providerName,
    providerType: row.providerType,
    baseUrl: row.baseUrl,
    apiKey: row.apiKeyMasked || "",
    status: row.status,
    isDefault: row.isDefault,
    sortNo: row.sortNo,
    remark: row.remark || ""
  };
  providerDialogVisible.value = true;
}

async function submitProviderForm() {
  if (!providerForm.value.providerName.trim() || !providerForm.value.baseUrl.trim()) {
    ElMessage.warning("服务商名称与 Base URL 不能为空");
    return;
  }
  try {
    await saveAiProvider(providerForm.value);
    ElMessage.success("服务商配置保存成功");
    providerDialogVisible.value = false;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.message || "保存服务商失败");
  }
}

async function handleDeleteProvider(row: AiProviderRow) {
  try {
    await ElMessageBox.confirm(
      `确定要删除服务商【${row.providerName}】吗？若删除，其下属已绑定的模型配置将被级联清理。`,
      "安全确认",
      { type: "warning", confirmButtonText: "确认删除", cancelButtonText: "取消" }
    );
    await deleteAiProvider(row.id);
    ElMessage.success("服务商已成功删除");
    loadData();
  } catch {
    // cancelled
  }
}

// ----------------- Model Handlers -----------------
function handleAddModel() {
  modelDialogTitle.value = "新增 AI 模型配置";
  const firstProvId = providers.value[0]?.id || 1;
  modelForm.value = {
    providerId: firstProvId,
    modelId: "",
    modelName: "",
    category: "CHAT",
    contextWindow: "64k",
    maxTokens: 4096,
    temperature: 0.7,
    isDefault: models.value.length === 0 ? 1 : 0,
    status: "ENABLED",
    sortNo: (models.value.length + 1) * 10
  };
  modelDialogVisible.value = true;
}

function handleEditModel(row: AiModelConfigRow) {
  modelDialogTitle.value = `编辑模型: ${row.modelName}`;
  modelForm.value = {
    id: row.id,
    tenantId: row.tenantId,
    providerId: row.providerId,
    modelId: row.modelId,
    modelName: row.modelName,
    category: row.category,
    contextWindow: row.contextWindow,
    maxTokens: row.maxTokens,
    temperature: row.temperature,
    isDefault: row.isDefault,
    status: row.status,
    sortNo: row.sortNo
  };
  modelDialogVisible.value = true;
}

async function submitModelForm() {
  if (!modelForm.value.modelId.trim() || !modelForm.value.modelName.trim()) {
    ElMessage.warning("模型标识符与显示名称不能为空");
    return;
  }
  try {
    await saveAiModel(modelForm.value);
    ElMessage.success("模型配置保存成功");
    modelDialogVisible.value = false;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.message || "保存模型配置失败");
  }
}

async function handleDeleteModel(row: AiModelConfigRow) {
  try {
    await ElMessageBox.confirm(`确定要删除模型【${row.modelName}】(${row.modelId}) 吗？`, "确认删除", {
      type: "warning"
    });
    await deleteAiModel(row.id);
    ElMessage.success("模型配置已删除");
    loadData();
  } catch {
    // cancelled
  }
}

// ----------------- Connection Test Probe -----------------
async function runTestConnection(providerId: number, modelId?: string) {
  const targetKey = `${providerId}_${modelId || "default"}`;
  testingMap.value[targetKey] = true;
  currentTestingTarget.value = modelId ? `模型 [${modelId}]` : `服务商 [#${providerId}]`;

  try {
    const res = await testAiConnection({
      providerId,
      testModel: modelId
    });
    currentTestResult.value = res;
    testResultDialog.value = true;
    if (res.success) {
      ElMessage.success(`握手测试成功！时延: ${res.latencyMillis} ms`);
    } else {
      ElMessage.warning(`连通性测试未通过: ${res.message}`);
    }
  } catch (err: any) {
    currentTestResult.value = {
      success: false,
      latencyMillis: 0,
      message: err?.message || "网络请求失败或端点拒绝连接",
      replySnippet: ""
    };
    testResultDialog.value = true;
  } finally {
    testingMap.value[targetKey] = false;
  }
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="ai-config-container">
    <!-- Header Hero Banner -->
    <div class="glass-hero-banner">
      <div class="hero-left">
        <div class="hero-icon-bubble">
          <el-icon :size="28"><Cpu /></el-icon>
        </div>
        <div class="hero-title-group">
          <div class="hero-badge-row">
            <span class="hero-tag">云原生 · AI 动态模型网关</span>
            <span class="hero-subtag">OpenAI v1 Compatible · 私有化 Ollama</span>
          </div>
          <h1 class="hero-heading">AI 智能模型网关与多服务商编排中心</h1>
          <p class="hero-desc">
            统一编排 DeepSeek、OpenAI、通义千问与本地私有大模型 · 支持租户级 BYOK (自带Key) · 实时连通性时延探针与动态流式推理
          </p>
        </div>
      </div>
      <div class="hero-right">
        <el-button :icon="Refresh" circle :loading="loading" @click="loadData" title="刷新状态" />
        <el-button type="primary" :icon="Plus" class="glow-button" @click="handleAddProvider">
          新建服务商
        </el-button>
        <el-button type="success" :icon="Plus" plain @click="handleAddModel">
          添加模型
        </el-button>
      </div>
    </div>

    <!-- 4 Stats Cards -->
    <div class="stats-row">
      <div class="stat-glass-card">
        <div class="stat-icon-wrap blue">
          <el-icon :size="20"><Connection /></el-icon>
        </div>
        <div class="stat-content">
          <span class="stat-label">总接入服务商</span>
          <div class="stat-number-row">
            <span class="stat-num">{{ providers.length }}</span>
            <span class="stat-unit">家</span>
          </div>
          <span class="stat-foot">已配凭证: {{ totalKeyConfigured }} 家</span>
        </div>
      </div>

      <div class="stat-glass-card">
        <div class="stat-icon-wrap purple">
          <el-icon :size="20"><Cpu /></el-icon>
        </div>
        <div class="stat-content">
          <span class="stat-label">已编排大模型</span>
          <div class="stat-number-row">
            <span class="stat-num">{{ models.length }}</span>
            <span class="stat-unit">个</span>
          </div>
          <span class="stat-foot">在线启用: {{ models.filter((m) => m.status === 'ENABLED').length }} 个</span>
        </div>
      </div>

      <div class="stat-glass-card">
        <div class="stat-icon-wrap green">
          <el-icon :size="20"><Lightning /></el-icon>
        </div>
        <div class="stat-content">
          <span class="stat-label">首选主推引擎</span>
          <div class="stat-number-row">
            <span class="stat-highlight">{{ defaultModel?.modelName || 'DeepSeek-V3' }}</span>
          </div>
          <span class="stat-foot">服务商: {{ defaultProvider?.providerName || 'DeepSeek' }}</span>
        </div>
      </div>

      <div class="stat-glass-card">
        <div class="stat-icon-wrap orange">
          <el-icon :size="20"><Coin /></el-icon>
        </div>
        <div class="stat-content">
          <span class="stat-label">网关推理协议</span>
          <div class="stat-number-row">
            <span class="stat-proto">OpenAI + SSE</span>
          </div>
          <span class="stat-foot">支持断点流式打字机</span>
        </div>
      </div>
    </div>

    <!-- Main Workspace Tabs -->
    <div class="glass-tabs-card">
      <div class="tab-header-strip">
        <div class="tab-pills">
          <button
            class="tab-pill"
            :class="{ active: activeTab === 'providers' }"
            @click="activeTab = 'providers'"
          >
            <el-icon><Connection /></el-icon>
            服务商与凭证配置 (Providers)
            <span class="pill-badge">{{ providers.length }}</span>
          </button>
          <button
            class="tab-pill"
            :class="{ active: activeTab === 'models' }"
            @click="activeTab = 'models'"
          >
            <el-icon><Cpu /></el-icon>
            模型目录与参数编排 (Models)
            <span class="pill-badge">{{ models.length }}</span>
          </button>
        </div>
      </div>

      <!-- Tab 1: Providers Tab -->
      <div v-if="activeTab === 'providers'" class="tab-pane-content">
        <el-table :data="providers" style="width: 100%" v-loading="loading" row-key="id">
          <el-table-column prop="providerName" label="服务商名称" min-width="180">
            <template #default="{ row }">
              <div class="name-cell">
                <span class="provider-title">{{ row.providerName }}</span>
                <div class="provider-badges">
                  <el-tag v-if="row.isDefault === 1" size="small" type="success" effect="dark">
                    首选默认
                  </el-tag>
                  <el-tag size="small" type="info" effect="plain">{{ row.providerCode }}</el-tag>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column prop="providerType" label="协议标准" width="160">
            <template #default="{ row }">
              <el-tag v-if="row.providerType === 'OPENAI_COMPATIBLE'" size="small" type="primary">
                OpenAI 兼容
              </el-tag>
              <el-tag v-else-if="row.providerType === 'OLLAMA'" size="small" type="warning">
                本地 Ollama
              </el-tag>
              <el-tag v-else size="small" type="info">{{ row.providerType }}</el-tag>
            </template>
          </el-table-column>

          <el-table-column prop="baseUrl" label="API Base URL" min-width="240">
            <template #default="{ row }">
              <div class="url-copy-row">
                <code class="url-code">{{ row.baseUrl }}</code>
                <el-button
                  :icon="DocumentCopy"
                  link
                  size="small"
                  @click="copyText(row.baseUrl)"
                  title="复制URL"
                />
              </div>
            </template>
          </el-table-column>

          <el-table-column label="API Key 凭证" min-width="160">
            <template #default="{ row }">
              <div class="key-status-cell">
                <span v-if="row.hasApiKey" class="key-masked">
                  <el-icon class="icon-key"><Key /></el-icon>
                  {{ row.apiKeyMasked }}
                </span>
                <span v-else class="key-missing">
                  ● 演示模式 (未配Key)
                </span>
              </div>
            </template>
          </el-table-column>

          <el-table-column prop="modelCount" label="挂载模型" width="100" align="center">
            <template #default="{ row }">
              <el-badge :value="row.modelCount || 0" type="primary" class="badge-num" />
            </template>
          </el-table-column>

          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'danger'" size="small">
                {{ row.status === "ENABLED" ? "启用" : "停用" }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="管理操作" width="230" fixed="right" align="center">
            <template #default="{ row }">
              <div class="action-btn-group">
                <el-button
                  size="small"
                  type="warning"
                  plain
                  :loading="testingMap[`${row.id}_default`]"
                  @click="runTestConnection(row.id)"
                >
                  连通性测试
                </el-button>
                <el-button size="small" :icon="Edit" link type="primary" @click="handleEditProvider(row)">
                  编辑
                </el-button>
                <el-button
                  size="small"
                  :icon="Delete"
                  link
                  type="danger"
                  @click="handleDeleteProvider(row)"
                >
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- Tab 2: Models Tab -->
      <div v-if="activeTab === 'models'" class="tab-pane-content">
        <el-table :data="models" style="width: 100%" v-loading="loading" row-key="id">
          <el-table-column prop="modelId" label="模型实际标识 (Model ID)" min-width="170">
            <template #default="{ row }">
              <code class="model-id-badge">{{ row.modelId }}</code>
            </template>
          </el-table-column>

          <el-table-column prop="modelName" label="前端展示名称" min-width="190">
            <template #default="{ row }">
              <div class="name-cell">
                <span class="provider-title">{{ row.modelName }}</span>
                <el-tag v-if="row.isDefault === 1" size="small" type="success" effect="dark">
                  默认选中
                </el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column prop="providerName" label="归属服务商" min-width="140">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ row.providerName || ("#" + row.providerId) }}</el-tag>
            </template>
          </el-table-column>

          <el-table-column prop="category" label="类型分类" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.category === 'REASONING'" size="small" type="warning" effect="plain">
                深度思维链
              </el-tag>
              <el-tag v-else-if="row.category === 'CODER'" size="small" type="success" effect="plain">
                代码生成
              </el-tag>
              <el-tag v-else size="small" type="primary" effect="plain">
                通用对话
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column prop="contextWindow" label="上下文" width="90" align="center">
            <template #default="{ row }">
              <span class="mono-spec">{{ row.contextWindow }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="temperature" label="温度" width="80" align="center">
            <template #default="{ row }">
              <span class="mono-spec">{{ row.temperature }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="status" label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'danger'" size="small">
                {{ row.status === "ENABLED" ? "启用" : "禁用" }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="管理操作" width="220" fixed="right" align="center">
            <template #default="{ row }">
              <div class="action-btn-group">
                <el-button
                  size="small"
                  type="warning"
                  plain
                  :loading="testingMap[`${row.providerId}_${row.modelId}`]"
                  @click="runTestConnection(row.providerId, row.modelId)"
                >
                  握手探针
                </el-button>
                <el-button size="small" :icon="Edit" link type="primary" @click="handleEditModel(row)">
                  编辑
                </el-button>
                <el-button size="small" :icon="Delete" link type="danger" @click="handleDeleteModel(row)">
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- Provider Edit/Create Dialog -->
    <el-dialog
      v-model="providerDialogVisible"
      :title="providerDialogTitle"
      width="640px"
      destroy-on-close
      class="glass-dialog"
    >
      <div class="preset-pill-row">
        <span class="preset-label">快捷预填模版：</span>
        <button
          v-for="p in providerPresets"
          :key="p.code"
          type="button"
          class="preset-pill-btn"
          @click="applyPreset(p)"
        >
          {{ p.name }}
        </button>
      </div>

      <el-form label-position="top" class="custom-form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="服务商中文名称" required>
              <el-input v-model="providerForm.providerName" placeholder="例如：DeepSeek 官方开放平台" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="服务商代码 (唯一标识)" required>
              <el-input v-model="providerForm.providerCode" placeholder="例如：deepseek, openai" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="网关协议标准" required>
              <el-select v-model="providerForm.providerType" style="width: 100%">
                <el-option label="OpenAI 兼容协议 (OPENAI_COMPATIBLE)" value="OPENAI_COMPATIBLE" />
                <el-option label="本地 Ollama 私有化协议 (OLLAMA)" value="OLLAMA" />
                <el-option label="自定义协议 (CUSTOM)" value="CUSTOM" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序权重">
              <el-input-number v-model="providerForm.sortNo" :min="1" :max="999" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="API Base URL (端点根地址)" required>
          <el-input v-model="providerForm.baseUrl" placeholder="例如：https://api.deepseek.com 或 https://api.openai.com/v1" />
        </el-form-item>

        <el-form-item label="API Key 访问凭证 (支持加密保存与留空)">
          <el-input
            v-model="providerForm.apiKey"
            type="password"
            show-password
            placeholder="sk-xxxxxxxx... (留空则使用演示模式或用于无鉴权的内网 Ollama)"
          />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="设为首选默认服务商">
              <el-switch
                v-model="providerForm.isDefault"
                :active-value="1"
                :inactive-value="0"
                active-text="首选"
                inactive-text="普通"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="服务商状态">
              <el-switch
                v-model="providerForm.status"
                active-value="ENABLED"
                inactive-value="DISABLED"
                active-text="启用"
                inactive-text="禁用"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注说明">
          <el-input v-model="providerForm.remark" type="textarea" :rows="2" placeholder="接入说明、用途或账号备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="providerDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitProviderForm">保存配置</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Model Edit/Create Dialog -->
    <el-dialog
      v-model="modelDialogVisible"
      :title="modelDialogTitle"
      width="600px"
      destroy-on-close
      class="glass-dialog"
    >
      <el-form label-position="top" class="custom-form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属服务商" required>
              <el-select v-model="modelForm.providerId" style="width: 100%">
                <el-option
                  v-for="p in providers"
                  :key="p.id"
                  :label="p.providerName"
                  :value="p.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型分类" required>
              <el-select v-model="modelForm.category" style="width: 100%">
                <el-option label="通用对话 (CHAT)" value="CHAT" />
                <el-option label="深度推理链 (REASONING)" value="REASONING" />
                <el-option label="代码生成 (CODER)" value="CODER" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模型标识符 (API Model ID)" required>
              <el-input v-model="modelForm.modelId" placeholder="例如：deepseek-chat, gpt-4o-mini" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="前端展示名称" required>
              <el-input v-model="modelForm.modelName" placeholder="例如：DeepSeek-V3 (通用大模型)" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="上下文窗口规格">
              <el-input v-model="modelForm.contextWindow" placeholder="例如：64k, 128k" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大输出 Token (Max Tokens)">
              <el-input-number v-model="modelForm.maxTokens" :min="256" :max="32768" :step="512" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="默认采样温度 (Temperature: 0.0 精确 ~ 2.0 发散)">
          <div class="slider-box">
            <el-slider v-model="modelForm.temperature" :min="0" :max="2" :step="0.05" show-input />
          </div>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="前端默认勾选">
              <el-switch
                v-model="modelForm.isDefault"
                :active-value="1"
                :inactive-value="0"
                active-text="默认模型"
                inactive-text="普通"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用状态">
              <el-switch
                v-model="modelForm.status"
                active-value="ENABLED"
                inactive-value="DISABLED"
                active-text="启用"
                inactive-text="停用"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="modelDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitModelForm">保存模型</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Test Connection Result Dialog -->
    <el-dialog
      v-model="testResultDialog"
      title="连通性握手诊断报告"
      width="540px"
      class="glass-dialog"
    >
      <div v-if="currentTestResult" class="test-result-wrapper">
        <div class="result-status-header" :class="currentTestResult.success ? 'success' : 'failed'">
          <el-icon :size="40">
            <CircleCheckFilled v-if="currentTestResult.success" />
            <CircleCloseFilled v-else />
          </el-icon>
          <div class="result-status-text">
            <h3>{{ currentTestResult.success ? '握手成功 · 连通正常' : '连通失败 · 需要排查' }}</h3>
            <p>{{ currentTestingTarget }} · 往返时延: <strong>{{ currentTestResult.latencyMillis }} ms</strong></p>
          </div>
        </div>

        <div class="result-msg-box">
          <span class="result-label">服务端诊断消息：</span>
          <p class="result-msg-text">{{ currentTestResult.message }}</p>
        </div>

        <div v-if="currentTestResult.replySnippet" class="result-snippet-box">
          <span class="result-label">模型响应片段预览：</span>
          <pre class="snippet-code">{{ currentTestResult.replySnippet }}</pre>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="testResultDialog = false">我知道了</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.ai-config-container {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* Glass Hero Banner */
.glass-hero-banner {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.75) 0%, rgba(240, 244, 255, 0.6) 100%);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 16px;
  padding: 24px 28px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 10px 30px rgba(0, 30, 80, 0.05);

  .hero-left {
    display: flex;
    align-items: center;
    gap: 18px;

    .hero-icon-bubble {
      width: 54px;
      height: 54px;
      border-radius: 14px;
      background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 8px 16px rgba(37, 99, 235, 0.25);
    }

    .hero-title-group {
      .hero-badge-row {
        display: flex;
        gap: 8px;
        margin-bottom: 6px;

        .hero-tag {
          font-size: 11px;
          font-weight: 700;
          color: #2563eb;
          background: rgba(37, 99, 235, 0.1);
          padding: 2px 8px;
          border-radius: 12px;
          text-transform: uppercase;
        }

        .hero-subtag {
          font-size: 11px;
          color: #64748b;
          background: rgba(100, 116, 139, 0.1);
          padding: 2px 8px;
          border-radius: 12px;
        }
      }

      .hero-heading {
        margin: 0 0 6px;
        font-size: 20px;
        font-weight: 700;
        color: #1e293b;
      }

      .hero-desc {
        margin: 0;
        font-size: 13px;
        color: #64748b;
      }
    }
  }

  .hero-right {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

/* Stats Row */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;

  .stat-glass-card {
    background: rgba(255, 255, 255, 0.7);
    backdrop-filter: blur(16px);
    border: 1px solid rgba(255, 255, 255, 0.6);
    border-radius: 14px;
    padding: 18px;
    display: flex;
    align-items: center;
    gap: 14px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.03);

    .stat-icon-wrap {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;

      &.blue {
        background: rgba(59, 130, 246, 0.12);
        color: #2563eb;
      }

      &.purple {
        background: rgba(147, 51, 234, 0.12);
        color: #7c3aed;
      }

      &.green {
        background: rgba(16, 185, 129, 0.12);
        color: #059669;
      }

      &.orange {
        background: rgba(245, 158, 11, 0.12);
        color: #d97706;
      }
    }

    .stat-content {
      display: flex;
      flex-direction: column;

      .stat-label {
        font-size: 12px;
        color: #64748b;
      }

      .stat-number-row {
        margin: 2px 0;

        .stat-num {
          font-size: 22px;
          font-weight: 700;
          color: #1e293b;
        }

        .stat-unit {
          font-size: 12px;
          color: #64748b;
          margin-left: 4px;
        }

        .stat-highlight {
          font-size: 15px;
          font-weight: 700;
          color: #059669;
        }

        .stat-proto {
          font-size: 15px;
          font-weight: 700;
          color: #d97706;
        }
      }

      .stat-foot {
        font-size: 11px;
        color: #94a3b8;
      }
    }
  }
}

/* Tabs Card */
.glass-tabs-card {
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.04);

  .tab-header-strip {
    padding: 16px 20px;
    border-bottom: 1px solid rgba(226, 232, 240, 0.8);
    display: flex;
    justify-content: space-between;
    align-items: center;

    .tab-pills {
      display: flex;
      gap: 10px;

      .tab-pill {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 8px 16px;
        border-radius: 10px;
        border: 1px solid transparent;
        background: transparent;
        color: #64748b;
        font-size: 13px;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.2s ease;

        &:hover {
          color: #1e293b;
          background: rgba(241, 245, 249, 0.8);
        }

        &.active {
          color: #2563eb;
          background: #eff6ff;
          border-color: rgba(37, 99, 235, 0.2);
          font-weight: 600;
        }

        .pill-badge {
          background: rgba(0, 0, 0, 0.06);
          padding: 1px 7px;
          border-radius: 10px;
          font-size: 11px;
        }
      }
    }
  }

  .tab-pane-content {
    padding: 12px 16px 20px;
  }
}

/* Table Custom Cells */
.name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;

  .provider-title {
    font-weight: 600;
    color: #1e293b;
    font-size: 14px;
  }

  .provider-badges {
    display: flex;
    gap: 6px;
  }
}

.url-copy-row {
  display: flex;
  align-items: center;
  gap: 6px;

  .url-code {
    background: #f1f5f9;
    padding: 2px 6px;
    border-radius: 6px;
    font-size: 12px;
    color: #334155;
    font-family: monospace;
    max-width: 280px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.key-status-cell {
  .key-masked {
    display: flex;
    align-items: center;
    gap: 4px;
    font-family: monospace;
    font-size: 12px;
    color: #059669;

    .icon-key {
      font-size: 14px;
    }
  }

  .key-missing {
    font-size: 12px;
    color: #94a3b8;
  }
}

.model-id-badge {
  background: #eff6ff;
  color: #1d4ed8;
  padding: 3px 8px;
  border-radius: 6px;
  font-family: monospace;
  font-size: 12px;
  border: 1px solid rgba(29, 78, 216, 0.15);
}

.mono-spec {
  font-family: monospace;
  font-size: 12px;
  color: #475569;
}

.action-btn-group {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

/* Preset Pills in Dialog */
.preset-pill-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 8px;

  .preset-label {
    font-size: 12px;
    color: #64748b;
  }

  .preset-pill-btn {
    border: 1px solid #cbd5e1;
    background: #fff;
    border-radius: 12px;
    padding: 2px 10px;
    font-size: 12px;
    cursor: pointer;
    color: #334155;
    transition: all 0.15s ease;

    &:hover {
      background: #eff6ff;
      border-color: #3b82f6;
      color: #1d4ed8;
    }
  }
}

/* Result Dialog */
.test-result-wrapper {
  display: flex;
  flex-direction: column;
  gap: 14px;

  .result-status-header {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 16px;
    border-radius: 12px;

    &.success {
      background: #ecfdf5;
      color: #059669;
      border: 1px solid #a7f3d0;
    }

    &.failed {
      background: #fef2f2;
      color: #dc2626;
      border: 1px solid #fecaca;
    }

    .result-status-text {
      h3 {
        margin: 0 0 4px;
        font-size: 16px;
      }

      p {
        margin: 0;
        font-size: 13px;
        color: #475569;
      }
    }
  }

  .result-msg-box,
  .result-snippet-box {
    display: flex;
    flex-direction: column;
    gap: 6px;

    .result-label {
      font-size: 12px;
      font-weight: 600;
      color: #475569;
    }

    .result-msg-text {
      margin: 0;
      font-size: 13px;
      color: #1e293b;
      background: #f8fafc;
      padding: 10px;
      border-radius: 8px;
    }

    .snippet-code {
      margin: 0;
      padding: 10px;
      background: #0f172a;
      color: #38bdf8;
      border-radius: 8px;
      font-size: 12px;
      line-height: 1.5;
      max-height: 180px;
      overflow-y: auto;
      white-space: pre-wrap;
    }
  }
}
</style>
