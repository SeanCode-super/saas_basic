<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  Check,
  CircleCheck,
  Connection,
  DataAnalysis,
  Document,
  Key,
  Lock,
  Plus,
  RefreshRight,
  Setting
} from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";
import { getTransportPolicy, invalidateTransportPolicyCache } from "@/api/http";
import {
  fetchTransportApiCatalog,
  fetchTransportSecurityOverview,
  updateTransportSecurityPolicy,
  verifyTransportRoundtrip
} from "@/api/modules/system";
import type {
  ApiCatalogSnapshot,
  TransportCryptoMode,
  TransportRoundtripResult,
  TransportScopeMode,
  TransportSecurityOverview
} from "@/api/modules/system";
import { antMatch, type TransportCryptoTrace } from "@/utils/transport-crypto";
import TransportKeyManagerDialog from "./components/TransportKeyManagerDialog.vue";
import ApiRulePickerDialog, { type RuleTargetKey } from "./components/ApiRulePickerDialog.vue";

const props = defineProps<{
  securityPolicy: {
    minLength: number;
    passwordHistory: number;
    sessionTimeoutMinutes: number;
    requireSpecial: boolean;
    forceMfa: boolean;
  };
  savePolicy: (message: string) => void;
}>();

const transportLoading = ref(false);
const transportSaving = ref(false);
const testingRoundtrip = ref(false);
const testDialogVisible = ref(false);
const keyDialogVisible = ref(false);
const pickerDialogVisible = ref(false);
const pickerTargetField = ref<RuleTargetKey>("sensitive");
const showPathRules = ref(false);

const rawTextMode = reactive<Record<RuleTargetKey, boolean>>({
  include: false,
  sensitive: false,
  exclude: false
});

const transportOverview = ref<TransportSecurityOverview | null>(null);
const apiCatalog = ref<ApiCatalogSnapshot | null>(null);

const transportForm = reactive<{
  mode: TransportCryptoMode;
  scopeMode: TransportScopeMode;
  encryptRequest: boolean;
  encryptResponse: boolean;
  encryptQuery: boolean;
  replayProtection: boolean;
  timestampSkewSeconds: number;
  keyRotationHours: number;
  maxGraceKeys: number;
  includePathsText: string;
  sensitivePathsText: string;
  excludePathsText: string;
  remark: string;
}>({
  mode: "OPTIONAL",
  scopeMode: "ALL_API",
  encryptRequest: true,
  encryptResponse: true,
  encryptQuery: true,
  replayProtection: true,
  timestampSkewSeconds: 300,
  keyRotationHours: 24,
  maxGraceKeys: 3,
  includePathsText: "/api/**",
  sensitivePathsText: "/api/auth/login\n/api/auth/refresh-token\n/api/auth/sso/**\n/api/iam/**\n/api/system/**\n/api/ai/**\n/api/tenant/**",
  excludePathsText: "/api/security/transport/policy\n/api/health/**\n/api/ws/**\n/api/file/download/**\n/api/file/preview/**\n/api/file/upload/**",
  remark: ""
});

const roundtripTrace = ref<TransportCryptoTrace | null>(null);
const roundtripResult = ref<TransportRoundtripResult | null>(null);
const roundtripLatencyMs = ref(0);

const modeOptions: Array<{ value: TransportCryptoMode; label: string; hint: string; badge?: string }> = [
  { value: "OFF", label: "关闭加密", hint: "全明文传输（调试用）" },
  { value: "OPTIONAL", label: "兼容加密", hint: "客户端加密，兼容外部明文", badge: "推荐" },
  { value: "REQUIRED", label: "强制加密", hint: "仅放行密文，拒绝明文" }
];

const scopeOptions: Array<{ value: TransportScopeMode; label: string }> = [
  { value: "ALL_API", label: "全部接口" },
  { value: "SENSITIVE_ONLY", label: "仅高敏接口" },
  { value: "CUSTOM", label: "自定义路径" }
];

const rulePanelMeta: Array<{ key: RuleTargetKey; title: string; placeholder: string }> = [
  { key: "include", title: "包含路径", placeholder: "/api/**" },
  { key: "sensitive", title: "高敏接口列表", placeholder: "/api/auth/login" },
  { key: "exclude", title: "明文豁免白名单", placeholder: "/api/health/**" }
];

function parsePathLines(raw: string): string[] {
  return Array.from(
    new Set(
      raw
        .split(/[\n,;]+/)
        .map((line) => line.trim())
        .filter((line) => line.length > 0)
    )
  );
}

function getRulesByKey(key: RuleTargetKey): string[] {
  if (key === "include") return parsePathLines(transportForm.includePathsText);
  if (key === "sensitive") return parsePathLines(transportForm.sensitivePathsText);
  return parsePathLines(transportForm.excludePathsText);
}

function setRulesByKey(key: RuleTargetKey, list: string[]) {
  const text = Array.from(new Set(list.map((s) => s.trim()).filter(Boolean))).join("\n");
  if (key === "include") {
    transportForm.includePathsText = text;
  } else if (key === "sensitive") {
    transportForm.sensitivePathsText = text;
  } else {
    transportForm.excludePathsText = text;
  }
}

function removeRuleItem(key: RuleTargetKey, rule: string) {
  setRulesByKey(
    key,
    getRulesByKey(key).filter((item) => item !== rule)
  );
}

function countCatalogMatches(rules: string[]): number {
  const endpoints = apiCatalog.value?.endpoints ?? [];
  if (endpoints.length === 0 || rules.length === 0) return 0;
  return endpoints.filter((ep) => rules.some((rule) => antMatch(rule, ep.antPattern))).length;
}

function openRulePicker(target: RuleTargetKey) {
  pickerTargetField.value = target;
  pickerDialogVisible.value = true;
}

function handleApplyPickedRules(nextDrafts: Record<RuleTargetKey, string[]>) {
  setRulesByKey("include", nextDrafts.include);
  setRulesByKey("sensitive", nextDrafts.sensitive);
  setRulesByKey("exclude", nextDrafts.exclude);
  showPathRules.value = true;
  ElMessage.info("规则已更新到表单，请点击“保存生效”正式下发");
}

function applyOverviewToForm(data: TransportSecurityOverview) {
  transportOverview.value = data;
  transportForm.mode = data.mode;
  transportForm.scopeMode = data.scopeMode;
  transportForm.encryptRequest = data.encryptRequest;
  transportForm.encryptResponse = data.encryptResponse;
  transportForm.encryptQuery = data.encryptQuery;
  transportForm.replayProtection = data.replayProtection;
  transportForm.timestampSkewSeconds = data.timestampSkewSeconds;
  transportForm.keyRotationHours = data.keyRotationHours || data.keySummary?.keyRotationHours || 24;
  transportForm.maxGraceKeys = data.maxGraceKeys || data.keySummary?.maxGraceKeys || 3;
  transportForm.includePathsText = (data.includePaths || []).join("\n");
  transportForm.sensitivePathsText = (data.sensitivePaths || []).join("\n");
  transportForm.excludePathsText = (data.excludePaths || []).join("\n");
  transportForm.remark = data.remark || "";
  if (data.scopeMode === "CUSTOM") {
    showPathRules.value = true;
  }
}

function selectScope(scope: TransportScopeMode) {
  transportForm.scopeMode = scope;
  if (scope === "CUSTOM" || scope === "SENSITIVE_ONLY") {
    showPathRules.value = true;
  }
}

async function loadTransportOverview() {
  transportLoading.value = true;
  try {
    const [overviewData, catalogData] = await Promise.all([
      fetchTransportSecurityOverview(),
      fetchTransportApiCatalog().catch(() => null)
    ]);
    applyOverviewToForm(overviewData);
    if (catalogData) {
      apiCatalog.value = catalogData;
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? "加载传输加密配置失败");
  } finally {
    transportLoading.value = false;
  }
}

async function handleSaveTransportPolicy() {
  transportSaving.value = true;
  try {
    const updated = await updateTransportSecurityPolicy({
      mode: transportForm.mode,
      scopeMode: transportForm.scopeMode,
      encryptRequest: transportForm.encryptRequest,
      encryptResponse: transportForm.encryptResponse,
      encryptQuery: transportForm.encryptQuery,
      replayProtection: transportForm.replayProtection,
      timestampSkewSeconds: transportForm.timestampSkewSeconds,
      keyRotationHours: transportForm.keyRotationHours,
      maxGraceKeys: transportForm.maxGraceKeys,
      includePaths: parsePathLines(transportForm.includePathsText),
      excludePaths: parsePathLines(transportForm.excludePathsText),
      sensitivePaths: parsePathLines(transportForm.sensitivePathsText),
      remark: transportForm.remark || "安全策略更新"
    });
    applyOverviewToForm(updated);
    invalidateTransportPolicyCache();
    await getTransportPolicy(true);
    const catalogData = await fetchTransportApiCatalog().catch(() => null);
    if (catalogData) {
      apiCatalog.value = catalogData;
    }
    ElMessage.success(`传输加密策略已生效（${updated.mode} · v${updated.policyVersion}）`);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? "保存传输加密策略失败");
  } finally {
    transportSaving.value = false;
  }
}

function handleKeyManagerUpdated(updated: TransportSecurityOverview) {
  applyOverviewToForm(updated);
}

async function handleVerifyRoundtrip() {
  testingRoundtrip.value = true;
  roundtripTrace.value = null;
  roundtripResult.value = null;
  const started = performance.now();
  try {
    await getTransportPolicy(true);
    const probe = `probe-${Date.now()}`;
    const result = await verifyTransportRoundtrip(probe, {
      transportEncrypt: true,
      onCryptoTrace: (trace) => {
        roundtripTrace.value = { ...trace };
      }
    });
    roundtripResult.value = result;
    roundtripLatencyMs.value = Math.round(performance.now() - started);
    testDialogVisible.value = true;
    await loadTransportOverview();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? "加密回环测试失败");
  } finally {
    testingRoundtrip.value = false;
  }
}

const complianceScore = computed(() => {
  let score = 55;
  if (props.securityPolicy.minLength >= 8) score += 10;
  if (props.securityPolicy.requireSpecial) score += 8;
  if (props.securityPolicy.passwordHistory >= 3) score += 7;
  if (props.securityPolicy.sessionTimeoutMinutes <= 120) score += 5;
  if (props.securityPolicy.forceMfa) score += 5;
  if (transportForm.mode === "REQUIRED") score += 10;
  else if (transportForm.mode === "OPTIONAL") score += 7;
  return Math.min(100, score);
});

const modeLabel = computed(() => {
  if (transportForm.mode === "REQUIRED") return "强制加密";
  if (transportForm.mode === "OPTIONAL") return "兼容加密";
  return "已关闭";
});

const modeTagType = computed(() => {
  if (transportForm.mode === "REQUIRED") return "success";
  if (transportForm.mode === "OPTIONAL") return "primary";
  return "info";
});

const keySourceBadge = computed(() => {
  const ks = transportOverview.value?.keySummary;
  if (!ks) return "";
  return ks.keySource === "CUSTOM_IMPORTED" ? "自定义" : `${ks.keyRotationHours || 24}h轮换`;
});

function handleExportDossier() {
  const dossier = {
    title: "企业安全基线合规报告",
    generatedAt: new Date().toISOString(),
    score: complianceScore.value,
    policy: props.securityPolicy,
    transportEncryption: transportOverview.value,
    apiCatalogSummary: apiCatalog.value?.summary ?? null
  };
  const blob = new Blob([JSON.stringify(dossier, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `security_dossier_${Date.now()}.json`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
  ElMessage.success("已导出安全合规报告");
}

onMounted(() => {
  void loadTransportOverview();
});
</script>

<template>
  <div class="system-security">
    <!-- 1. 顶部精简状态栏 -->
    <div class="status-strip">
      <div class="status-metrics">
        <div class="metric-pill">
          <span class="metric-k">安全评分</span>
          <strong class="metric-v">{{ complianceScore }}</strong>
          <el-tag type="success" size="small" effect="plain">等保三级</el-tag>
        </div>
        <div class="metric-sep" />
        <div class="metric-pill">
          <span class="metric-k">传输加密</span>
          <el-tag :type="modeTagType" size="small" effect="light">{{ modeLabel }}</el-tag>
        </div>
        <div class="metric-sep" />
        <button type="button" class="metric-pill metric-clickable" @click="keyDialogVisible = true">
          <span class="metric-k">密钥</span>
          <code class="metric-code">{{ transportOverview?.keySummary.activeKid || "--" }}</code>
          <el-tag v-if="keySourceBadge" size="small" type="info" effect="plain">{{ keySourceBadge }}</el-tag>
        </button>
        <div class="metric-sep" />
        <div class="metric-pill">
          <span class="metric-k">加密 / 拦截</span>
          <strong class="metric-v">{{ transportOverview?.telemetry.encryptedRequests ?? 0 }} / {{ transportOverview?.telemetry.rejectedPlaintext ?? 0 }}</strong>
        </div>
        <template v-if="apiCatalog">
          <div class="metric-sep" />
          <button
            type="button"
            class="metric-pill metric-clickable"
            @click="openRulePicker(transportForm.scopeMode === 'SENSITIVE_ONLY' ? 'sensitive' : 'include')"
          >
            <span class="metric-k">已扫描接口</span>
            <strong class="metric-v">{{ apiCatalog.summary.totalEndpoints }}</strong>
          </button>
        </template>
      </div>

      <div class="status-actions">
        <el-button size="small" :icon="RefreshRight" :loading="transportLoading" @click="loadTransportOverview">刷新</el-button>
        <el-button size="small" :icon="Document" @click="handleExportDossier">导出报告</el-button>
      </div>
    </div>

    <!-- 2. 接口传输加密设置 -->
    <BaseCard tone="contrast">
      <template #header>
        <div class="card-head">
          <div class="card-title-wrap">
            <span class="card-icon blue"><el-icon><Lock /></el-icon></span>
            <div>
              <h3 class="card-title">接口传输加密</h3>
              <span class="card-badge-algo">X25519 · AES-256-GCM</span>
            </div>
          </div>
          <div class="card-actions">
            <el-button size="small" :icon="Connection" :loading="testingRoundtrip" @click="handleVerifyRoundtrip">链路自测</el-button>
            <el-button size="small" :icon="Key" @click="keyDialogVisible = true">密钥配置</el-button>
            <el-button type="primary" size="small" :icon="Check" :loading="transportSaving" @click="handleSaveTransportPolicy">保存生效</el-button>
          </div>
        </div>
      </template>

      <div class="section-stack">
        <!-- 加密模式三选一 -->
        <div class="setting-block">
          <div class="block-label">加密模式</div>
          <div class="mode-grid">
            <button
              v-for="item in modeOptions"
              :key="item.value"
              type="button"
              class="mode-btn"
              :class="{ 'is-active': transportForm.mode === item.value }"
              @click="transportForm.mode = item.value"
            >
              <div class="mode-btn-top">
                <strong>{{ item.label }}</strong>
                <span v-if="item.badge" class="rec-badge">{{ item.badge }}</span>
              </div>
              <span class="mode-btn-hint">{{ item.hint }}</span>
            </button>
          </div>
        </div>

        <!-- 左右分栏：左范围 + 右细项开关 -->
        <div class="two-col-grid">
          <div class="setting-group-box">
            <div class="group-header">
              <span>适用范围</span>
              <div class="group-head-links">
                <el-button
                  link
                  type="primary"
                  size="small"
                  :icon="DataAnalysis"
                  @click="openRulePicker(transportForm.scopeMode === 'SENSITIVE_ONLY' ? 'sensitive' : 'include')"
                >
                  多维选接口 / 看统计
                </el-button>
                <el-button link type="info" size="small" :icon="Setting" @click="showPathRules = !showPathRules">
                  {{ showPathRules ? "收起规则" : "展开规则" }}
                </el-button>
              </div>
            </div>
            <div class="scope-pills">
              <button
                v-for="scope in scopeOptions"
                :key="scope.value"
                type="button"
                class="scope-pill"
                :class="{ 'is-active': transportForm.scopeMode === scope.value }"
                @click="selectScope(scope.value)"
              >
                {{ scope.label }}
              </button>
            </div>
            <div class="scope-summary">
              <template v-if="transportForm.scopeMode === 'ALL_API'">
                覆盖 {{ apiCatalog ? `${countCatalogMatches(parsePathLines(transportForm.includePathsText))} / ${apiCatalog.summary.totalEndpoints} 个` : "全量" }} 业务接口，文件流、WebSocket 与健康探针自动豁免。
              </template>
              <template v-else-if="transportForm.scopeMode === 'SENSITIVE_ONLY'">
                仅对 {{ parsePathLines(transportForm.sensitivePathsText).length }} 条高敏规则（{{ apiCatalog ? `命中 ${countCatalogMatches(parsePathLines(transportForm.sensitivePathsText))} 个接口` : "敏感路径" }}）启用加密。
              </template>
              <template v-else>
                按下方包含规则与豁免白名单精准生效（{{ apiCatalog ? `命中 ${countCatalogMatches(parsePathLines(transportForm.includePathsText))} 个包含接口` : "自定义" }}）。
              </template>
            </div>
          </div>

          <div class="setting-group-box">
            <div class="group-header">
              <span>加密内容与防护</span>
            </div>
            <div class="toggle-list">
              <div class="toggle-item">
                <span>请求参数加密 (Request)</span>
                <el-switch v-model="transportForm.encryptRequest" size="small" />
              </div>
              <div class="toggle-item">
                <span>返回结果加密 (Response)</span>
                <el-switch v-model="transportForm.encryptResponse" size="small" />
              </div>
              <div class="toggle-item">
                <span>URL 查询串加密 (Query)</span>
                <el-switch v-model="transportForm.encryptQuery" size="small" />
              </div>
              <div class="toggle-item">
                <span>防重放拦截 (Timestamp + Nonce)</span>
                <el-switch v-model="transportForm.replayProtection" size="small" />
              </div>
              <div class="toggle-item">
                <span>时间窗口容差 (秒)</span>
                <el-input-number v-model="transportForm.timestampSkewSeconds" :min="30" :max="3600" :step="30" size="small" />
              </div>
            </div>
          </div>
        </div>

        <!-- 按需展开的可视化/文本双模式规则栏 -->
        <div v-if="showPathRules" class="path-editor-panel">
          <div v-for="panel in rulePanelMeta" :key="panel.key" class="path-editor-col">
            <div class="rule-col-head">
              <div class="rule-title-group">
                <label>{{ panel.title }}</label>
                <span v-if="apiCatalog" class="hit-badge">命中 {{ countCatalogMatches(getRulesByKey(panel.key)) }}</span>
              </div>
              <div class="rule-col-actions">
                <el-button link type="primary" size="small" :icon="Plus" @click="openRulePicker(panel.key)">选接口/包</el-button>
                <el-button link type="info" size="small" @click="rawTextMode[panel.key] = !rawTextMode[panel.key]">
                  {{ rawTextMode[panel.key] ? "标签视图" : "手写文本" }}
                </el-button>
              </div>
            </div>

            <el-input
              v-if="rawTextMode[panel.key]"
              v-model="transportForm[panel.key === 'include' ? 'includePathsText' : panel.key === 'sensitive' ? 'sensitivePathsText' : 'excludePathsText']"
              type="textarea"
              :rows="3"
              :placeholder="panel.placeholder"
            />
            <div v-else class="rule-tags-box">
              <el-tag
                v-for="rule in getRulesByKey(panel.key)"
                :key="rule"
                closable
                size="small"
                class="rule-chip"
                @close="removeRuleItem(panel.key, rule)"
              >
                <code>{{ rule }}</code>
              </el-tag>
              <button type="button" class="add-chip-btn" @click="openRulePicker(panel.key)">+ 添加 / 按包勾选</button>
            </div>
          </div>
        </div>
      </div>
    </BaseCard>

    <!-- 3. 账号与密码安全 -->
    <BaseCard tone="soft">
      <template #header>
        <div class="card-head">
          <div class="card-title-wrap">
            <span class="card-icon slate"><el-icon><Key /></el-icon></span>
            <h3 class="card-title">账号与密码基线</h3>
          </div>
          <el-button type="primary" size="small" :icon="Check" @click="savePolicy('账号与密码安全策略已保存')">
            保存基线
          </el-button>
        </div>
      </template>

      <div class="account-grid">
        <div class="account-inputs">
          <div class="field-item">
            <label>最短密码长度</label>
            <el-input-number v-model="securityPolicy.minLength" :min="8" :max="32" size="small" />
          </div>
          <div class="field-item">
            <label>历史密码记忆次数</label>
            <el-input-number v-model="securityPolicy.passwordHistory" :min="1" :max="24" size="small" />
          </div>
          <div class="field-item">
            <label>会话超时 (分钟)</label>
            <el-input-number v-model="securityPolicy.sessionTimeoutMinutes" :min="15" :max="1440" size="small" />
          </div>
        </div>

        <div class="account-toggles">
          <div class="toggle-item">
            <span>强制密码包含特殊字符</span>
            <el-switch v-model="securityPolicy.requireSpecial" size="small" />
          </div>
          <div class="toggle-item">
            <span>管理员强制启用多因子认证 (MFA)</span>
            <el-switch v-model="securityPolicy.forceMfa" size="small" />
          </div>
        </div>
      </div>
    </BaseCard>

    <!-- 链路自测结果弹窗 -->
    <el-dialog v-model="testDialogVisible" title="传输加密链路自测结果" width="620px">
      <div v-if="roundtripResult && roundtripTrace" class="roundtrip-box">
        <div class="roundtrip-ok">
          <el-icon><CircleCheck /></el-icon>
          <span>双向加密验证通过 ({{ roundtripLatencyMs }} ms) · {{ roundtripTrace.kid }} · v{{ roundtripResult.policyVersion }}</span>
        </div>
        <div class="trace-row">
          <span>请求密文</span>
          <code>{{ roundtripTrace.requestCipherText || "(无)" }}</code>
        </div>
        <div class="trace-row">
          <span>响应密文</span>
          <code>{{ roundtripTrace.responseCipherText || "(无)" }}</code>
        </div>
        <div class="trace-row">
          <span>解密还原结果</span>
          <code>{{ JSON.stringify(roundtripResult) }}</code>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="testDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 密钥管理与自定义配置弹窗 -->
    <TransportKeyManagerDialog
      v-model:visible="keyDialogVisible"
      :overview="transportOverview"
      @updated="handleKeyManagerUpdated"
    />

    <!-- 多维度接口选择器与统计弹窗 -->
    <ApiRulePickerDialog
      v-model:visible="pickerDialogVisible"
      v-model:target-field="pickerTargetField"
      :include-paths="parsePathLines(transportForm.includePathsText)"
      :sensitive-paths="parsePathLines(transportForm.sensitivePathsText)"
      :exclude-paths="parsePathLines(transportForm.excludePathsText)"
      @apply="handleApplyPickedRules"
      @catalog-loaded="(snap) => (apiCatalog = snap)"
    />
  </div>
</template>

<style scoped lang="scss">
.system-security {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.status-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  padding: 10px 16px;
  background: rgb(255 255 255 / 0.82);
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.status-metrics {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.metric-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;

  .metric-k {
    font-size: 12px;
    color: #64748b;
  }

  .metric-v {
    color: #0f172a;
    font-weight: 700;
  }

  .metric-code {
    font-family: var(--sb-font-mono, monospace);
    font-size: 12px;
    color: #1e293b;
    background: #f1f5f9;
    padding: 1px 6px;
    border-radius: 4px;
  }
}

.metric-clickable {
  border: none;
  background: transparent;
  padding: 2px 6px;
  border-radius: 6px;
  cursor: pointer;

  &:hover {
    background: #eff6ff;
  }
}

.metric-sep {
  width: 1px;
  height: 16px;
  background: #e2e8f0;
}

.status-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.card-title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;

  > div {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
}

.card-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 15px;

  &.blue {
    background: #eff6ff;
    color: #2563eb;
  }

  &.slate {
    background: #f1f5f9;
    color: #334155;
  }
}

.card-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.card-badge-algo {
  font-family: var(--sb-font-mono, monospace);
  font-size: 11px;
  font-weight: 600;
  color: #2563eb;
  background: #eff6ff;
  padding: 2px 8px;
  border-radius: 9999px;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.section-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.block-label {
  font-size: 12.5px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 8px;
}

.mode-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;

  @media (max-width: 820px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.mode-btn {
  display: flex;
  flex-direction: column;
  gap: 4px;
  text-align: left;
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: rgb(255 255 255 / 0.72);
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    border-color: #93c5fd;
    background: #fff;
  }

  &.is-active {
    border-color: #2563eb;
    background: #eff6ff;
    box-shadow: 0 2px 8px rgb(37 99 235 / 0.12);
  }

  .mode-btn-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;

    strong {
      font-size: 13.5px;
      color: #0f172a;
    }
  }

  .rec-badge {
    font-size: 10.5px;
    font-weight: 600;
    color: #2563eb;
    background: rgb(37 99 235 / 0.12);
    padding: 1px 7px;
    border-radius: 9999px;
  }

  .mode-btn-hint {
    font-size: 12px;
    color: #64748b;
  }
}

.two-col-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;

  @media (max-width: 900px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.setting-group-box {
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: rgb(255 255 255 / 0.65);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: #334155;
}

.group-head-links {
  display: flex;
  align-items: center;
  gap: 6px;
}

.scope-pills {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.scope-pill {
  height: 34px;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #334155;
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    border-color: #93c5fd;
    color: #1d4ed8;
  }

  &.is-active {
    background: #2563eb;
    border-color: #2563eb;
    color: #fff;
    font-weight: 600;
  }
}

.scope-summary {
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
}

.toggle-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.toggle-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 28px;
  font-size: 13px;
  color: #1e293b;
}

.path-editor-panel {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;

  @media (max-width: 960px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.path-editor-col {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.rule-col-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.rule-title-group {
  display: flex;
  align-items: center;
  gap: 6px;

  label {
    font-size: 12px;
    font-weight: 600;
    color: #334155;
  }

  .hit-badge {
    font-size: 11px;
    color: #1d4ed8;
    background: #eff6ff;
    padding: 0 6px;
    border-radius: 9999px;
  }
}

.rule-col-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.rule-tags-box {
  min-height: 76px;
  max-height: 126px;
  overflow-y: auto;
  padding: 8px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e2e8f0;
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  gap: 6px;
}

.rule-chip code {
  font-family: var(--sb-font-mono, monospace);
  font-size: 11.5px;
}

.add-chip-btn {
  border: 1px dashed #93c5fd;
  background: #f8fbff;
  color: #2563eb;
  font-size: 11.5px;
  padding: 2px 8px;
  border-radius: 4px;
  cursor: pointer;

  &:hover {
    background: #eff6ff;
    border-color: #2563eb;
  }
}

.account-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 16px;
  align-items: center;

  @media (max-width: 900px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.account-inputs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;

  @media (max-width: 720px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.field-item {
  display: flex;
  flex-direction: column;
  gap: 6px;

  label {
    font-size: 12px;
    color: #64748b;
  }
}

.account-toggles {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 10px;
  background: rgb(255 255 255 / 0.65);
  border: 1px solid #e2e8f0;
}

.roundtrip-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.roundtrip-ok {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #15803d;
  font-size: 13px;
  font-weight: 600;
}

.trace-row {
  display: flex;
  flex-direction: column;
  gap: 4px;

  span {
    font-size: 12px;
    font-weight: 600;
    color: #475569;
  }

  code {
    padding: 8px 10px;
    border-radius: 6px;
    background: #0f172a;
    color: #38bdf8;
    font-size: 11.5px;
    font-family: var(--sb-font-mono, monospace);
    word-break: break-all;
    max-height: 96px;
    overflow-y: auto;
  }
}
</style>
