<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Cpu,
  Refresh,
  Plus,
  Connection,
  Edit,
  Delete,
  Key,
  Lightning,
  Coin,
  DocumentCopy,
  CircleCheckFilled,
  CircleCloseFilled,
  Lock,
  Tools,
  Tickets,
  Document,
  Promotion,
  Check,
  Close
} from "@element-plus/icons-vue";
import {
  fetchAiProviders,
  saveAiProvider,
  deleteAiProvider,
  fetchAiModels,
  saveAiModel,
  deleteAiModel,
  testAiConnection,
  rotateAllVaultCredentials,
  fetchAiPrompts,
  fetchPromptVersions,
  savePromptVersion,
  publishPromptVersion,
  fetchMcpServers,
  saveMcpServer,
  deleteMcpServer,
  probeMcpServer,
  fetchAiTools,
  updateAiToolGovernance,
  fetchAiTenantQuota,
  updateAiTenantQuota,
  fetchAiRuns,
  createAiRun,
  fetchPendingToolApprovals,
  approveAiToolCall,
  rejectAiToolCall,
  type AiProviderRow,
  type AiProviderSavePayload,
  type AiModelConfigRow,
  type AiModelConfigSavePayload,
  type AiTestConnectionResult,
  type AiPromptRow,
  type AiPromptVersionRow,
  type AiMcpServerRow,
  type AiMcpServerSavePayload,
  type AiMcpProbeResult,
  type AiToolRow,
  type AiTenantQuotaRow,
  type AiRunRow,
  type AiToolCallRow
} from "@/api/modules/ai";

const router = useRouter();
const loading = ref(false);
const activeTab = ref<"providers" | "models" | "mcp" | "runs" | "prompts">("providers");

// Core Data State
const providers = ref<AiProviderRow[]>([]);
const models = ref<AiModelConfigRow[]>([]);
const mcpServers = ref<AiMcpServerRow[]>([]);
const aiTools = ref<AiToolRow[]>([]);
const prompts = ref<AiPromptRow[]>([]);
const tenantQuota = ref<AiTenantQuotaRow | null>(null);
const aiRuns = ref<AiRunRow[]>([]);
const pendingApprovals = ref<AiToolCallRow[]>([]);

// Test Connection State
const testingMap = ref<Record<string, boolean>>({});
const testResultDialog = ref(false);
const currentTestResult = ref<AiTestConnectionResult | null>(null);
const currentTestingTarget = ref("");
const rotatingVault = ref(false);

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

// MCP Server Dialog & Probe Result
const mcpDialogVisible = ref(false);
const mcpForm = ref<AiMcpServerSavePayload>({
  serverCode: "",
  serverName: "",
  transportType: "STREAMABLE_HTTP",
  endpointUrl: "",
  authHeaderName: "Authorization",
  authToken: "",
  allowedTools: [],
  status: "ENABLED",
  remark: ""
});
const mcpProbeResult = ref<AiMcpProbeResult | null>(null);
const mcpProbeDialogVisible = ref(false);

// Run Launcher Dialog
const runDialogVisible = ref(false);
const runForm = ref({
  modelId: "deepseek-chat",
  promptCode: "tianshu-assistant",
  inputPrompt: "请对当前底座运行状况做简要诊断分析",
  toolCode: ""
});
const launchingRun = ref(false);

// Quota Edit Dialog
const quotaDialogVisible = ref(false);
const quotaForm = ref({
  monthlyTokenLimit: 5000000,
  rpmLimit: 60
});

// Prompt Version Drawer & Dialog
const promptVersionsDialogVisible = ref(false);
const currentPromptCode = ref("");
const promptVersions = ref<AiPromptVersionRow[]>([]);
const promptSaveDialogVisible = ref(false);
const promptForm = ref({
  promptCode: "",
  promptName: "",
  category: "GENERAL",
  systemPrompt: "",
  userTemplate: "{{question}}",
  modelHint: "deepseek-chat",
  changelog: ""
});

// Computed Stats
const defaultProvider = computed(() => providers.value.find((p) => p.isDefault === 1) || providers.value[0]);
const defaultModel = computed(() => models.value.find((m) => m.isDefault === 1) || models.value[0]);
const totalKeyConfigured = computed(() => providers.value.filter((p) => p.hasApiKey).length);
const encryptedProvidersCount = computed(() => providers.value.filter((p) => p.encryptedAtRest).length);

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
    const [pList, mList, sList, tList, prList, qData, rList, appList] = await Promise.all([
      fetchAiProviders(),
      fetchAiModels(),
      fetchMcpServers(),
      fetchAiTools(),
      fetchAiPrompts(),
      fetchAiTenantQuota(),
      fetchAiRuns(),
      fetchPendingToolApprovals()
    ]);
    providers.value = pList || [];
    models.value = mList || [];
    mcpServers.value = sList || [];
    aiTools.value = tList || [];
    prompts.value = prList || [];
    tenantQuota.value = qData || null;
    aiRuns.value = rList || [];
    pendingApprovals.value = appList || [];
  } catch (err: any) {
    ElMessage.error(err?.message || "加载 AI 治理与模型中台数据失败");
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

// ----------------- Vault Key Rotation -----------------
async function handleRotateAllVaultKeys() {
  try {
    await ElMessageBox.confirm(
      "将使用当前 Vault AES-256-GCM 激活主密钥重新封装所有服务商 API Key 与 MCP 认证凭据，实现零停机密钥轮换或明文升级，确定继续吗？",
      "Vault 密钥轮换确认",
      { type: "info", confirmButtonText: "立即执行轮换", cancelButtonText: "取消" }
    );
    rotatingVault.value = true;
    const res = await rotateAllVaultCredentials();
    ElMessage.success(res.message);
    loadData();
  } catch {
    // cancelled
  } finally {
    rotatingVault.value = false;
  }
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
    ElMessage.success("服务商配置保存成功，凭据已通过 AES-256-GCM 安全密封");
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

// ----------------- MCP & Tool Whitelist Handlers -----------------
function handleAddMcpServer() {
  mcpForm.value = {
    serverCode: "",
    serverName: "",
    transportType: "STREAMABLE_HTTP",
    endpointUrl: "",
    authHeaderName: "Authorization",
    authToken: "",
    allowedTools: [],
    status: "ENABLED",
    remark: ""
  };
  mcpDialogVisible.value = true;
}

async function submitMcpForm() {
  if (!mcpForm.value.serverCode.trim() || !mcpForm.value.serverName.trim() || !mcpForm.value.endpointUrl.trim()) {
    ElMessage.warning("请完整填写 MCP 服务编码、名称与端点 URL");
    return;
  }
  try {
    await saveMcpServer(mcpForm.value);
    ElMessage.success("MCP 服务已注册（遵循默认全拒绝原则，认证 Token 已加密存储）");
    mcpDialogVisible.value = false;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.message || "保存 MCP 服务失败");
  }
}

async function handleProbeMcp(row: AiMcpServerRow) {
  try {
    const res = await probeMcpServer(row.id);
    mcpProbeResult.value = res;
    mcpProbeDialogVisible.value = true;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.message || "MCP 探测请求失败");
  }
}

async function handleDeleteMcp(row: AiMcpServerRow) {
  try {
    await ElMessageBox.confirm(`确定移除 MCP 服务【${row.serverName}】吗？`, "安全确认", { type: "warning" });
    await deleteMcpServer(row.id);
    ElMessage.success("MCP 服务已移除");
    loadData();
  } catch {
    // cancelled
  }
}

async function handleToggleToolWhitelist(tool: AiToolRow, val: boolean) {
  try {
    await updateAiToolGovernance(tool.id, { whitelisted: val });
    ElMessage.success(`工具 [${tool.toolCode}] 白名单已${val ? "放行" : "收回"}`);
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.message || "更新工具白名单失败");
  }
}

// ----------------- Runs, Approvals & Quota -----------------
function openLaunchRunDialog(presetToolCode?: string) {
  runForm.value = {
    modelId: defaultModel.value?.modelId || "deepseek-chat",
    promptCode: "tianshu-assistant",
    inputPrompt: presetToolCode === "platform.cache.evict"
      ? "请求执行租户限流缓存清理操作（高危写入风控演示）"
      : "请执行当前平台诊断任务",
    toolCode: presetToolCode || ""
  };
  runDialogVisible.value = true;
}

async function submitLaunchRun() {
  launchingRun.value = true;
  try {
    const res = await createAiRun({
      modelId: runForm.value.modelId,
      promptCode: runForm.value.promptCode || undefined,
      inputPrompt: runForm.value.inputPrompt,
      toolCode: runForm.value.toolCode || undefined,
      toolArguments: runForm.value.toolCode ? { scope: "rate_limit", limit: 5 } : undefined
    });
    runDialogVisible.value = false;
    activeTab.value = "runs";
    if (res.status === "WAITING_APPROVAL") {
      ElMessage.warning(`Run ${res.runNo} 触发高危风控拦截，已挂起等待人工审批！`);
    } else {
      ElMessage.success(`Run ${res.runNo} 执行完毕 (${res.status})，消耗 ${res.totalTokens} Tokens`);
    }
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.message || "执行 Run 失败");
  } finally {
    launchingRun.value = false;
  }
}

async function handleApproveCall(call: AiToolCallRow) {
  try {
    const res = await approveAiToolCall(call.id, "控制台管理员复核审批放行");
    ElMessage.success(`已批准执行 ${call.toolCode}，Run 状态流转为 ${res.status}`);
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.message || "审批执行失败");
  }
}

async function handleRejectCall(call: AiToolCallRow) {
  try {
    const res = await rejectAiToolCall(call.id, "安全管理员驳回高危写操作");
    ElMessage.info(`已驳回调用 ${call.toolCode}，Run 状态已终结为 ${res.status}`);
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.message || "驳回操作失败");
  }
}

function openQuotaDialog() {
  quotaForm.value = {
    monthlyTokenLimit: tenantQuota.value?.monthlyTokenLimit || 5000000,
    rpmLimit: tenantQuota.value?.rpmLimit || 60
  };
  quotaDialogVisible.value = true;
}

async function submitQuotaForm() {
  try {
    await updateAiTenantQuota(quotaForm.value);
    ElMessage.success("租户 AI Token 预算与 RPM 配额更新成功");
    quotaDialogVisible.value = false;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.message || "更新配额失败");
  }
}

// ----------------- Prompts & Versions -----------------
async function openPromptVersions(row: AiPromptRow) {
  currentPromptCode.value = row.promptCode;
  promptVersions.value = await fetchPromptVersions(row.promptCode);
  promptVersionsDialogVisible.value = true;
}

function openCreatePromptDialog(existing?: AiPromptRow) {
  promptForm.value = {
    promptCode: existing ? existing.promptCode : "",
    promptName: existing ? existing.promptName : "",
    category: existing ? existing.category : "GENERAL",
    systemPrompt: existing ? existing.systemPrompt : "",
    userTemplate: existing ? existing.userTemplate : "{{question}}",
    modelHint: existing ? existing.modelHint : "deepseek-chat",
    changelog: ""
  };
  promptSaveDialogVisible.value = true;
}

async function submitPromptVersion() {
  if (!promptForm.value.promptCode.trim() || !promptForm.value.systemPrompt.trim()) {
    ElMessage.warning("Prompt 编码与 System 指令不可为空");
    return;
  }
  try {
    await savePromptVersion({
      ...promptForm.value,
      publishImmediately: true
    });
    ElMessage.success("已发布不可变 Prompt 新版本");
    promptSaveDialogVisible.value = false;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.message || "发布 Prompt 版本失败");
  }
}

async function handleActivatePromptVersion(ver: AiPromptVersionRow) {
  try {
    await publishPromptVersion(currentPromptCode.value, ver.versionNo);
    ElMessage.success(`已将 ${currentPromptCode.value} 生效指针切换至 v${ver.versionNo}`);
    promptVersions.value = await fetchPromptVersions(currentPromptCode.value);
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.message || "切换版本失败");
  }
}

function runStatusType(status: string): "success" | "warning" | "danger" | "info" | "primary" {
  if (status === "SUCCEEDED") return "success";
  if (status === "WAITING_APPROVAL") return "warning";
  if (status === "FAILED" || status === "REJECTED") return "danger";
  if (status === "RUNNING") return "primary";
  return "info";
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
            <span class="hero-tag">天枢云基 · M3 级 AI 核心治理中台</span>
            <span class="hero-subtag">AES-256-GCM Vault · MCP JSON-RPC · Token 计量 · Run 审批</span>
          </div>
          <h1 class="hero-heading">AI 智能模型网关与 M3 安全治理中心</h1>
          <p class="hero-desc">
            全链路私钥密文托管与轮换 · MCP 工具白名单风控与高危写人机审批 · 租户级 Token 配额与速率限流保护
          </p>
        </div>
      </div>
      <div class="hero-right">
        <el-button :icon="Refresh" circle :loading="loading" @click="loadData" title="刷新状态" />
        <el-button plain @click="router.push('/ai/finetune')">模型微调工坊</el-button>
        <el-button plain @click="router.push('/ai/agents')">智能体工坊</el-button>
        <el-button type="warning" plain :icon="Lock" :loading="rotatingVault" @click="handleRotateAllVaultKeys">
          Vault 密钥轮换
        </el-button>
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
          <el-icon :size="20"><Lock /></el-icon>
        </div>
        <div class="stat-content">
          <span class="stat-label">Vault 加密托管服务商</span>
          <div class="stat-number-row">
            <span class="stat-num">{{ encryptedProvidersCount }}</span>
            <span class="stat-unit">/ {{ providers.length }} 家</span>
          </div>
          <span class="stat-foot">已配凭证: {{ totalKeyConfigured }} 家 (AES-256-GCM)</span>
        </div>
      </div>

      <div class="stat-glass-card">
        <div class="stat-icon-wrap purple">
          <el-icon :size="20"><Tools /></el-icon>
        </div>
        <div class="stat-content">
          <span class="stat-label">MCP &amp; 平台受控工具</span>
          <div class="stat-number-row">
            <span class="stat-num">{{ aiTools.filter((t) => t.whitelisted).length }}</span>
            <span class="stat-unit">/ {{ aiTools.length }} 个放行</span>
          </div>
          <span class="stat-foot">MCP 服务器: {{ mcpServers.length }} 个 (默认全拒绝)</span>
        </div>
      </div>

      <div class="stat-glass-card">
        <div class="stat-icon-wrap green">
          <el-icon :size="20"><Coin /></el-icon>
        </div>
        <div class="stat-content">
          <span class="stat-label">租户本月 Token 用量</span>
          <div class="stat-number-row">
            <span class="stat-num">{{ tenantQuota?.usedTokensMonth || 0 }}</span>
            <span class="stat-unit">({{ tenantQuota?.usagePercent || 0 }}%)</span>
          </div>
          <span class="stat-foot">限额 {{ ((tenantQuota?.monthlyTokenLimit || 5000000) / 10000).toFixed(0) }}w · RPM {{ tenantQuota?.rpmLimit || 60 }}</span>
        </div>
      </div>

      <div class="stat-glass-card">
        <div class="stat-icon-wrap orange">
          <el-icon :size="20"><Tickets /></el-icon>
        </div>
        <div class="stat-content">
          <span class="stat-label">待人工审批高危 ToolCall</span>
          <div class="stat-number-row">
            <span class="stat-num">{{ pendingApprovals.length }}</span>
            <span class="stat-unit">条待审</span>
          </div>
          <span class="stat-foot">主推模型: {{ defaultModel?.modelId || defaultProvider?.providerCode || "deepseek-chat" }}</span>
        </div>
      </div>
    </div>

    <!-- Main Workspace Tabs -->
    <div class="glass-tabs-card">
      <div class="tab-header-strip">
        <div class="tab-pills">
          <button class="tab-pill" :class="{ active: activeTab === 'providers' }" @click="activeTab = 'providers'">
            <el-icon><Connection /></el-icon>
            服务商与 Vault 凭据
            <span class="pill-badge">{{ providers.length }}</span>
          </button>
          <button class="tab-pill" :class="{ active: activeTab === 'models' }" @click="activeTab = 'models'">
            <el-icon><Cpu /></el-icon>
            模型编排目录
            <span class="pill-badge">{{ models.length }}</span>
          </button>
          <button class="tab-pill" :class="{ active: activeTab === 'mcp' }" @click="activeTab = 'mcp'">
            <el-icon><Tools /></el-icon>
            MCP 与工具白名单
            <span class="pill-badge">{{ aiTools.length }}</span>
          </button>
          <button class="tab-pill" :class="{ active: activeTab === 'runs' }" @click="activeTab = 'runs'">
            <el-icon><Tickets /></el-icon>
            Run 状态机与审批
            <span class="pill-badge">{{ pendingApprovals.length }}</span>
          </button>
          <button class="tab-pill" :class="{ active: activeTab === 'prompts' }" @click="activeTab = 'prompts'">
            <el-icon><Document /></el-icon>
            Prompt 版本库
            <span class="pill-badge">{{ prompts.length }}</span>
          </button>
        </div>
        <div class="tab-quick-actions">
          <el-button size="small" type="primary" plain :icon="Promotion" @click="openLaunchRunDialog()">
            发起受控 Run
          </el-button>
          <el-button size="small" type="danger" plain :icon="Lightning" @click="openLaunchRunDialog('platform.cache.evict')">
            高危审批演练
          </el-button>
        </div>
      </div>

      <!-- Tab 1: Providers & Credential Vault -->
      <div v-if="activeTab === 'providers'" class="tab-pane-content">
        <el-table :data="providers" style="width: 100%" v-loading="loading" row-key="id">
          <el-table-column prop="providerName" label="服务商名称" min-width="205">
            <template #default="{ row }">
              <div class="name-cell">
                <span class="provider-title">{{ row.providerName }}</span>
                <div class="provider-badges">
                  <el-tag v-if="row.isDefault === 1" size="small" type="success" effect="dark">首选默认</el-tag>
                  <el-tag size="small" type="info" effect="plain">{{ row.providerCode }}</el-tag>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column prop="baseUrl" label="API Base URL" min-width="220">
            <template #default="{ row }">
              <div class="url-copy-row">
                <code class="url-code">{{ row.baseUrl }}</code>
                <el-button :icon="DocumentCopy" link size="small" @click="copyText(row.baseUrl)" title="复制URL" />
              </div>
            </template>
          </el-table-column>

          <el-table-column label="Vault 加密凭据状态" min-width="220">
            <template #default="{ row }">
              <div class="key-status-cell">
                <template v-if="row.hasApiKey">
                  <span class="key-masked">
                    <el-icon class="icon-key"><Key /></el-icon>
                    {{ row.apiKeyMasked }}
                  </span>
                  <div class="provider-badges">
                    <el-tag size="small" :type="row.encryptedAtRest ? 'success' : 'warning'" effect="plain">
                      {{ row.encryptedAtRest ? `🔐 AES-256-GCM (${row.keyVersion})` : "明文待轮换" }}
                    </el-tag>
                    <el-tag v-if="row.cipherDigest" size="small" type="info" effect="plain">
                      #{{ row.cipherDigest.slice(0, 8) }}
                    </el-tag>
                  </div>
                </template>
                <span v-else class="key-missing">● 演示模式 (未配Key)</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column prop="modelCount" label="挂载模型" width="90" align="center">
            <template #default="{ row }">
              <el-badge :value="row.modelCount || 0" type="primary" />
            </template>
          </el-table-column>

          <el-table-column prop="status" label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'danger'" size="small">
                {{ row.status === "ENABLED" ? "启用" : "停用" }}
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
                  :loading="testingMap[`${row.id}_default`]"
                  @click="runTestConnection(row.id)"
                >
                  连通性测试
                </el-button>
                <el-button size="small" :icon="Edit" link type="primary" @click="handleEditProvider(row)">编辑</el-button>
                <el-button size="small" :icon="Delete" link type="danger" @click="handleDeleteProvider(row)">删除</el-button>
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
                <el-tag v-if="row.isDefault === 1" size="small" type="success" effect="dark">默认选中</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="providerName" label="归属服务商" min-width="140">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ row.providerName || "#" + row.providerId }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="类型分类" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.category === 'REASONING'" size="small" type="warning" effect="plain">深度思维链</el-tag>
              <el-tag v-else-if="row.category === 'CODER'" size="small" type="success" effect="plain">代码生成</el-tag>
              <el-tag v-else size="small" type="primary" effect="plain">通用对话</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="contextWindow" label="上下文" width="90" align="center">
            <template #default="{ row }"><span class="mono-spec">{{ row.contextWindow }}</span></template>
          </el-table-column>
          <el-table-column prop="temperature" label="温度" width="80" align="center">
            <template #default="{ row }"><span class="mono-spec">{{ row.temperature }}</span></template>
          </el-table-column>
          <el-table-column label="管理操作" width="210" fixed="right" align="center">
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
                <el-button size="small" :icon="Edit" link type="primary" @click="handleEditModel(row)">编辑</el-button>
                <el-button size="small" :icon="Delete" link type="danger" @click="handleDeleteModel(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- Tab 3: MCP Servers & Tool Whitelist -->
      <div v-if="activeTab === 'mcp'" class="tab-pane-content">
        <div class="section-toolbar">
          <div>
            <h3 class="sub-heading">MCP 服务器接入矩阵（默认全拒绝，需白名单显式授权）</h3>
          </div>
          <el-button type="primary" size="small" :icon="Plus" @click="handleAddMcpServer">注册 MCP 服务</el-button>
        </div>

        <el-table :data="mcpServers" border size="small" style="width: 100%; margin-bottom: 20px;">
          <el-table-column prop="serverName" label="MCP 服务名称" min-width="180">
            <template #default="{ row }">
              <strong>{{ row.serverName }}</strong>
              <div class="mono-spec">{{ row.serverCode }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="transportType" label="传输协议" width="150" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.transportType === 'BUILTIN_LOOPBACK' ? 'success' : 'primary'">
                {{ row.transportType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="endpointUrl" label="JSON-RPC 端点" min-width="220" show-overflow-tooltip>
            <template #default="{ row }"><code class="url-code">{{ row.endpointUrl }}</code></template>
          </el-table-column>
          <el-table-column label="认证状态" width="140">
            <template #default="{ row }">
              <span class="mono-spec">{{ row.authTokenMasked }}</span>
            </template>
          </el-table-column>
          <el-table-column label="健康状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.healthStatus === 'ONLINE' ? 'success' : 'info'">
                {{ row.healthStatus }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" align="center">
            <template #default="{ row }">
              <el-button size="small" type="primary" plain @click="handleProbeMcp(row)">MCP 握手探测</el-button>
              <el-button
                v-if="row.serverCode !== 'builtin-platform-mcp'"
                size="small"
                link
                type="danger"
                @click="handleDeleteMcp(row)"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="section-toolbar">
          <h3 class="sub-heading">AI 受控工具目录与风险门禁策略</h3>
        </div>
        <el-table :data="aiTools" border size="small" style="width: 100%">
          <el-table-column prop="toolCode" label="工具编码" min-width="170">
            <template #default="{ row }"><code class="model-id-badge">{{ row.toolCode }}</code></template>
          </el-table-column>
          <el-table-column prop="toolName" label="工具名称" min-width="160" />
          <el-table-column prop="permissionCode" label="所需权限码" width="150">
            <template #default="{ row }"><el-tag size="small" type="info">{{ row.permissionCode }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="riskLevel" label="风险等级" width="120" align="center">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row.riskLevel === 'HIGH' ? 'danger' : row.riskLevel === 'LOW' ? 'warning' : 'success'"
              >
                {{ row.riskLevel }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="adapterType" label="适配器" width="130" align="center" />
          <el-table-column label="白名单放行" width="110" align="center">
            <template #default="{ row }">
              <el-switch
                :model-value="row.whitelisted"
                @change="(val: string | number | boolean) => handleToggleToolWhitelist(row, Boolean(val))"
              />
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- Tab 4: Run State Machine, Approvals & Tenant Quota -->
      <div v-if="activeTab === 'runs'" class="tab-pane-content">
        <div class="quota-banner" v-if="tenantQuota">
          <div class="quota-left">
            <span class="quota-title">租户 #{{ tenantQuota.tenantId }} 月度 Token 预算 ({{ tenantQuota.billingMonth }})</span>
            <el-progress
              :percentage="Math.min(100, tenantQuota.usagePercent)"
              :stroke-width="10"
              style="width: 280px"
            />
            <span class="mono-spec">
              已用 {{ tenantQuota.usedTokensMonth }} / {{ tenantQuota.monthlyTokenLimit }} Tokens · 预估花费 ${{ tenantQuota.estimatedCostMonth }}
            </span>
          </div>
          <el-button size="small" type="primary" plain @click="openQuotaDialog">调整预算与 RPM 限流</el-button>
        </div>

        <div v-if="pendingApprovals.length > 0" class="approval-alert-section">
          <div class="section-toolbar">
            <h3 class="sub-heading text-danger">⏳ 待人工审批的高危 ToolCall 队列 ({{ pendingApprovals.length }})</h3>
          </div>
          <el-table :data="pendingApprovals" border size="small" style="width: 100%; margin-bottom: 18px;">
            <el-table-column prop="callNo" label="调用单号" width="150" />
            <el-table-column prop="toolCode" label="高危工具" min-width="160">
              <template #default="{ row }"><code class="model-id-badge">{{ row.toolCode }}</code></template>
            </el-table-column>
            <el-table-column prop="riskLevel" label="风险级别" width="100" align="center">
              <template #default="{ row }"><el-tag type="danger" size="small">{{ row.riskLevel }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="idempotencyKey" label="幂等防护键" min-width="180" show-overflow-tooltip />
            <el-table-column prop="argumentsSummary" label="调用参数" min-width="150" show-overflow-tooltip />
            <el-table-column label="审批决策" width="190" align="center">
              <template #default="{ row }">
                <el-button size="small" type="success" :icon="Check" @click="handleApproveCall(row)">批准执行</el-button>
                <el-button size="small" type="danger" plain :icon="Close" @click="handleRejectCall(row)">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="section-toolbar">
          <h3 class="sub-heading">AI Run 执行审计与链路状态机历史</h3>
        </div>
        <el-table :data="aiRuns" border size="small" style="width: 100%">
          <el-table-column prop="runNo" label="Run 流水号" width="160">
            <template #default="{ row }"><code class="mono-spec">{{ row.runNo }}</code></template>
          </el-table-column>
          <el-table-column prop="modelId" label="模型" width="140" />
          <el-table-column prop="status" label="状态机" width="150" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="runStatusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="totalTokens" label="Tokens" width="90" align="right" />
          <el-table-column prop="latencyMs" label="耗时" width="90" align="right">
            <template #default="{ row }">{{ row.latencyMs }} ms</template>
          </el-table-column>
          <el-table-column prop="traceId" label="W3C TraceId" min-width="150" show-overflow-tooltip>
            <template #default="{ row }"><code class="mono-spec">{{ row.traceId || "-" }}</code></template>
          </el-table-column>
          <el-table-column prop="outputSummary" label="输出摘要" min-width="220" show-overflow-tooltip />
        </el-table>
      </div>

      <!-- Tab 5: Prompt Version Library -->
      <div v-if="activeTab === 'prompts'" class="tab-pane-content">
        <div class="section-toolbar">
          <h3 class="sub-heading">版本化 Prompt 资产库（支持不可变发布与一键回滚）</h3>
          <el-button type="primary" size="small" :icon="Plus" @click="openCreatePromptDialog()">新建 Prompt 版本</el-button>
        </div>
        <el-table :data="prompts" border size="small" style="width: 100%">
          <el-table-column prop="promptCode" label="Prompt 编码" width="160">
            <template #default="{ row }"><code class="model-id-badge">{{ row.promptCode }}</code></template>
          </el-table-column>
          <el-table-column prop="promptName" label="名称" width="190" />
          <el-table-column prop="activeVersion" label="生效版本" width="100" align="center">
            <template #default="{ row }"><el-tag type="success" size="small">v{{ row.activeVersion }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="systemPrompt" label="系统指令预览" min-width="260" show-overflow-tooltip />
          <el-table-column label="操作" width="200" align="center">
            <template #default="{ row }">
              <el-button size="small" type="primary" plain @click="openPromptVersions(row)">版本历史</el-button>
              <el-button size="small" link type="success" @click="openCreatePromptDialog(row)">发布新版</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- Provider Edit/Create Dialog -->
    <el-dialog v-model="providerDialogVisible" :title="providerDialogTitle" width="640px" destroy-on-close>
      <div class="preset-pill-row">
        <span class="preset-label">快捷预填模版：</span>
        <button v-for="p in providerPresets" :key="p.code" type="button" class="preset-pill-btn" @click="applyPreset(p)">
          {{ p.name }}
        </button>
      </div>
      <el-form label-position="top">
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
          <el-input v-model="providerForm.baseUrl" placeholder="例如：https://api.deepseek.com" />
        </el-form-item>
        <el-form-item label="API Key 访问凭证（保存时由 Vault 自动 AES-256-GCM 加密）">
          <el-input v-model="providerForm.apiKey" type="password" show-password placeholder="sk-xxxxxxxx..." />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="设为首选默认服务商">
              <el-switch v-model="providerForm.isDefault" :active-value="1" :inactive-value="0" active-text="首选" inactive-text="普通" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="服务商状态">
              <el-switch v-model="providerForm.status" active-value="ENABLED" inactive-value="DISABLED" active-text="启用" inactive-text="禁用" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="providerDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitProviderForm">加密保存配置</el-button>
      </template>
    </el-dialog>

    <!-- Model Edit/Create Dialog -->
    <el-dialog v-model="modelDialogVisible" :title="modelDialogTitle" width="600px" destroy-on-close>
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属服务商" required>
              <el-select v-model="modelForm.providerId" style="width: 100%">
                <el-option v-for="p in providers" :key="p.id" :label="p.providerName" :value="p.id" />
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
            <el-form-item label="模型标识符 (Model ID)" required>
              <el-input v-model="modelForm.modelId" placeholder="例如：deepseek-chat" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="前端展示名称" required>
              <el-input v-model="modelForm.modelName" placeholder="例如：DeepSeek-V3" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="上下文窗口">
              <el-input v-model="modelForm.contextWindow" placeholder="例如：64k" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大输出 Token">
              <el-input-number v-model="modelForm.maxTokens" :min="256" :max="32768" :step="512" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="采样温度">
          <el-slider v-model="modelForm.temperature" :min="0" :max="2" :step="0.05" show-input />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modelDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitModelForm">保存模型</el-button>
      </template>
    </el-dialog>

    <!-- MCP Server Dialog -->
    <el-dialog v-model="mcpDialogVisible" title="注册外部 MCP 服务（JSON-RPC 2.0）" width="560px" destroy-on-close>
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="MCP 服务编码" required>
              <el-input v-model="mcpForm.serverCode" placeholder="如：erp-mcp-gateway" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="MCP 服务名称" required>
              <el-input v-model="mcpForm.serverName" placeholder="如：ERP 库存 MCP 服务" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="传输协议">
          <el-select v-model="mcpForm.transportType" style="width: 100%">
            <el-option label="Streamable HTTP (JSON-RPC 2.0)" value="STREAMABLE_HTTP" />
            <el-option label="SSE 传输协议" value="SSE" />
          </el-select>
        </el-form-item>
        <el-form-item label="MCP 端点 URL" required>
          <el-input v-model="mcpForm.endpointUrl" placeholder="https://mcp.example.com/mcp" />
        </el-form-item>
        <el-form-item label="认证 Token（保存时通过 Vault 加密）">
          <el-input v-model="mcpForm.authToken" type="password" show-password placeholder="选填 Bearer Token" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mcpDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitMcpForm">确认注册</el-button>
      </template>
    </el-dialog>

    <!-- MCP Probe Result Dialog -->
    <el-dialog v-model="mcpProbeDialogVisible" title="MCP JSON-RPC 2.0 握手与工具发现报告" width="600px">
      <div v-if="mcpProbeResult">
        <el-alert
          :title="mcpProbeResult.message"
          :type="mcpProbeResult.success ? 'success' : 'error'"
          :closable="false"
          show-icon
          style="margin-bottom: 14px"
        />
        <el-table :data="mcpProbeResult.discoveredTools" border size="small">
          <el-table-column prop="name" label="工具名" width="180" />
          <el-table-column prop="description" label="描述" min-width="200" />
          <el-table-column label="白名单" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.whitelisted ? 'success' : 'danger'">
                {{ row.whitelisted ? "已放行" : "拒绝" }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- Launch Run Dialog -->
    <el-dialog v-model="runDialogVisible" title="发起 M3 受控 AI Run（含工具编排与风控状态机）" width="540px">
      <el-form label-position="top">
        <el-form-item label="目标模型">
          <el-select v-model="runForm.modelId" style="width: 100%">
            <el-option v-for="m in models" :key="m.id" :label="`${m.modelName} (${m.modelId})`" :value="m.modelId" />
          </el-select>
        </el-form-item>
        <el-form-item label="可选工具调用（选择 HIGH 风险工具将进入 WAITING_APPROVAL）">
          <el-select v-model="runForm.toolCode" clearable placeholder="仅纯对话推理" style="width: 100%">
            <el-option
              v-for="t in aiTools"
              :key="t.id"
              :label="`[${t.riskLevel}] ${t.toolCode} - ${t.toolName}`"
              :value="t.toolCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务指令输入">
          <el-input v-model="runForm.inputPrompt" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="runDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="launchingRun" @click="submitLaunchRun">提交执行 Run</el-button>
      </template>
    </el-dialog>

    <!-- Quota Dialog -->
    <el-dialog v-model="quotaDialogVisible" title="配置租户 AI 月度 Token 配额与速率上限" width="440px">
      <el-form label-position="top">
        <el-form-item label="月度 Token 预算上限">
          <el-input-number v-model="quotaForm.monthlyTokenLimit" :min="1000" :step="100000" style="width: 100%" />
        </el-form-item>
        <el-form-item label="AI 网关并发保护上限 (RPM)">
          <el-input-number v-model="quotaForm.rpmLimit" :min="5" :max="5000" :step="10" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quotaDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitQuotaForm">保存配额策略</el-button>
      </template>
    </el-dialog>

    <!-- Prompt Save & Versions Dialogs -->
    <el-dialog v-model="promptSaveDialogVisible" title="创建并发布不可变 Prompt 新版本" width="580px">
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Prompt 编码" required>
              <el-input v-model="promptForm.promptCode" placeholder="如：erp-advisor" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Prompt 名称">
              <el-input v-model="promptForm.promptName" placeholder="展示名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="System Prompt 系统指令" required>
          <el-input v-model="promptForm.systemPrompt" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="版本变更日志">
          <el-input v-model="promptForm.changelog" placeholder="如：优化输出格式与合规限制" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="promptSaveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPromptVersion">发布不可变版本</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="promptVersionsDialogVisible" :title="`版本历史: ${currentPromptCode}`" width="680px">
      <el-table :data="promptVersions" border size="small">
        <el-table-column prop="versionNo" label="版本" width="80" align="center">
          <template #default="{ row }">v{{ row.versionNo }}</template>
        </el-table-column>
        <el-table-column prop="changelog" label="变更说明" width="160" />
        <el-table-column prop="systemPrompt" label="系统指令快照" min-width="240" show-overflow-tooltip />
        <el-table-column label="回滚/激活" width="110" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleActivatePromptVersion(row)">设为生效</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- Test Connection Result Dialog -->
    <el-dialog v-model="testResultDialog" title="连通性握手诊断报告" width="540px">
      <div v-if="currentTestResult" class="test-result-wrapper">
        <div class="result-status-header" :class="currentTestResult.success ? 'success' : 'failed'">
          <el-icon :size="40">
            <CircleCheckFilled v-if="currentTestResult.success" />
            <CircleCloseFilled v-else />
          </el-icon>
          <div class="result-status-text">
            <h3>{{ currentTestResult.success ? "握手成功 · 连通正常" : "连通失败 · 需要排查" }}</h3>
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
        <el-button type="primary" @click="testResultDialog = false">我知道了</el-button>
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
  min-width: 0;
}

.glass-hero-banner {
  background: linear-gradient(135deg, rgb(255 255 255 / 0.78) 0%, rgb(240 244 255 / 0.65) 100%);
  backdrop-filter: blur(20px);
  border: 1px solid rgb(255 255 255 / 0.6);
  border-radius: 16px;
  padding: 22px 26px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  box-shadow: 0 10px 30px rgb(0 30 80 / 0.05);

  .hero-left {
    display: flex;
    align-items: center;
    gap: 18px;
    min-width: 0;
    flex: 1 1 380px;

    .hero-icon-bubble {
      width: 54px;
      height: 54px;
      flex-shrink: 0;
      border-radius: 14px;
      background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 8px 16px rgb(37 99 235 / 0.25);
    }

    .hero-title-group {
      min-width: 0;
      flex: 1;

      .hero-badge-row {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        margin-bottom: 6px;

        .hero-tag {
          font-size: 11px;
          font-weight: 700;
          color: #2563eb;
          background: rgb(37 99 235 / 0.1);
          padding: 2px 10px;
          border-radius: 12px;
          white-space: nowrap;
        }

        .hero-subtag {
          font-size: 11px;
          color: #64748b;
          background: rgb(100 116 139 / 0.1);
          padding: 2px 10px;
          border-radius: 12px;
          white-space: nowrap;
        }
      }

      .hero-heading {
        margin: 0 0 6px;
        font-size: 19px;
        font-weight: 700;
        color: #1e293b;
        line-height: 1.3;
      }

      .hero-desc {
        margin: 0;
        font-size: 13px;
        color: #64748b;
        line-height: 1.5;
      }
    }
  }

  .hero-right {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    flex-shrink: 0;
  }
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;

  .stat-glass-card {
    background: rgb(255 255 255 / 0.72);
    backdrop-filter: blur(16px);
    border: 1px solid rgb(255 255 255 / 0.6);
    border-radius: 14px;
    padding: 18px;
    min-width: 0;
    display: flex;
    align-items: center;
    gap: 14px;
    box-shadow: 0 4px 16px rgb(0 0 0 / 0.03);

    .stat-icon-wrap {
      width: 44px;
      height: 44px;
      flex-shrink: 0;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;

      &.blue { background: rgb(59 130 246 / 0.12); color: #2563eb; }
      &.purple { background: rgb(147 51 234 / 0.12); color: #7c3aed; }
      &.green { background: rgb(16 185 129 / 0.12); color: #059669; }
      &.orange { background: rgb(245 158 11 / 0.12); color: #d97706; }
    }

    .stat-content {
      display: flex;
      flex-direction: column;
      min-width: 0;

      .stat-label { font-size: 12px; color: #64748b; }

      .stat-number-row {
        margin: 2px 0;

        .stat-num { font-size: 22px; font-weight: 700; color: #1e293b; }
        .stat-unit { font-size: 12px; color: #64748b; margin-left: 4px; }
      }

      .stat-foot { font-size: 11px; color: #94a3b8; line-height: 1.4; }
    }
  }
}

.glass-tabs-card {
  background: rgb(255 255 255 / 0.78);
  backdrop-filter: blur(20px);
  border: 1px solid rgb(255 255 255 / 0.6);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 10px 30px rgb(0 0 0 / 0.04);

  .tab-header-strip {
    padding: 14px 18px;
    border-bottom: 1px solid rgb(226 232 240 / 0.8);
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;

    .tab-pills {
      display: flex;
      gap: 6px;
      flex: 1 1 auto;
      flex-wrap: wrap;
      min-width: 0;

      .tab-pill {
        display: inline-flex;
        align-items: center;
        gap: 5px;
        flex-shrink: 0;
        white-space: nowrap;
        padding: 6px 10px;
        border-radius: 10px;
        border: 1px solid transparent;
        background: transparent;
        color: #64748b;
        font-size: 12.5px;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.2s ease;

        &:hover { color: #1e293b; background: rgb(241 245 249 / 0.8); }

        &.active {
          color: #2563eb;
          background: #eff6ff;
          border-color: rgb(37 99 235 / 0.2);
          font-weight: 600;
        }

        .pill-badge {
          background: rgb(0 0 0 / 0.06);
          padding: 1px 6px;
          border-radius: 10px;
          font-size: 11px;
        }
      }
    }

    .tab-quick-actions {
      display: flex;
      gap: 8px;
      flex-shrink: 0;
    }
  }

  .tab-pane-content {
    padding: 16px 18px 22px;
  }
}

.section-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.sub-heading {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.quota-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;

  .quota-left {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;

    .quota-title {
      font-size: 13px;
      font-weight: 600;
      color: #1e293b;
    }
  }
}

.name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;

  .provider-title { font-weight: 600; color: #1e293b; font-size: 14px; }
  .provider-badges { display: flex; gap: 6px; flex-wrap: wrap; }
}

.url-copy-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.url-code {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 12px;
  color: #334155;
  font-family: monospace;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.key-status-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;

  .key-masked {
    display: flex;
    align-items: center;
    gap: 4px;
    font-family: monospace;
    font-size: 12px;
    color: #059669;
  }

  .key-missing { font-size: 12px; color: #94a3b8; }
}

.model-id-badge {
  background: #eff6ff;
  color: #1d4ed8;
  padding: 3px 8px;
  border-radius: 6px;
  font-family: monospace;
  font-size: 12px;
  border: 1px solid rgb(29 78 216 / 0.15);
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

.preset-pill-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 8px;

  .preset-label { font-size: 12px; color: #64748b; }

  .preset-pill-btn {
    border: 1px solid #cbd5e1;
    background: #fff;
    border-radius: 12px;
    padding: 2px 10px;
    font-size: 12px;
    cursor: pointer;
    color: #334155;

    &:hover { background: #eff6ff; border-color: #3b82f6; color: #1d4ed8; }
  }
}

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

    &.success { background: #ecfdf5; color: #059669; border: 1px solid #a7f3d0; }
    &.failed { background: #fef2f2; color: #dc2626; border: 1px solid #fecaca; }
  }

  .result-label { font-size: 12px; font-weight: 600; color: #475569; }
  .result-msg-text { margin: 0; font-size: 13px; color: #1e293b; background: #f8fafc; padding: 10px; border-radius: 8px; }

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

@media (max-width: 1200px) {
  .ai-config-container {
    padding: 16px;
    gap: 16px;
  }

  .glass-hero-banner {
    padding: 18px 20px;

    .hero-right {
      width: 100%;
    }
  }

  .stats-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .glass-tabs-card .tab-header-strip {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (max-width: 640px) {
  .ai-config-container {
    padding: 12px;
    gap: 12px;
  }

  .glass-hero-banner {
    padding: 14px 16px;

    .hero-left {
      align-items: flex-start;

      .hero-title-group .hero-badge-row {
        .hero-tag,
        .hero-subtag {
          white-space: normal;
        }
      }
    }
  }

  .stats-row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
