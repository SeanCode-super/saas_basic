<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Check, CircleCheck, Connection, Document, Key, Lock, RefreshRight, Setting } from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";
import { getTransportPolicy, invalidateTransportPolicyCache } from "@/api/http";
import {
  fetchTransportSecurityOverview,
  rotateTransportSecurityKey,
  updateTransportSecurityPolicy,
  verifyTransportRoundtrip
} from "@/api/modules/system";
import type {
  TransportCryptoMode,
  TransportRoundtripResult,
  TransportScopeMode,
  TransportSecurityOverview
} from "@/api/modules/system";
import type { TransportCryptoTrace } from "@/utils/transport-crypto";

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
const keyRotating = ref(false);
const testingRoundtrip = ref(false);
const testDialogVisible = ref(false);
const showPathRules = ref(false);

const transportOverview = ref<TransportSecurityOverview | null>(null);
const transportForm = reactive<{
  mode: TransportCryptoMode;
  scopeMode: TransportScopeMode;
  encryptRequest: boolean;
  encryptResponse: boolean;
  encryptQuery: boolean;
  replayProtection: boolean;
  timestampSkewSeconds: number;
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

function parsePathLines(raw: string): string[] {
  return raw
    .split(/[\n,;]+/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
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
  if (scope === "CUSTOM") {
    showPathRules.value = true;
  }
}

async function loadTransportOverview() {
  transportLoading.value = true;
  try {
    const data = await fetchTransportSecurityOverview();
    applyOverviewToForm(data);
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
      includePaths: parsePathLines(transportForm.includePathsText),
      excludePaths: parsePathLines(transportForm.excludePathsText),
      sensitivePaths: parsePathLines(transportForm.sensitivePathsText),
      remark: transportForm.remark || "安全策略更新"
    });
    applyOverviewToForm(updated);
    invalidateTransportPolicyCache();
    await getTransportPolicy(true);
    ElMessage.success(`传输加密策略已生效（${updated.mode} · v${updated.policyVersion}）`);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? "保存传输加密策略失败");
  } finally {
    transportSaving.value = false;
  }
}

async function handleRotateKey() {
  try {
    await ElMessageBox.confirm(
      "将生成新的 X25519 密钥对并无缝切换，历史密钥会在宽限期内保留，不影响在途请求。",
      "轮换传输密钥",
      { confirmButtonText: "确认轮换", cancelButtonText: "取消", type: "warning" }
    );
  } catch {
    return;
  }
  keyRotating.value = true;
  try {
    const updated = await rotateTransportSecurityKey();
    applyOverviewToForm(updated);
    invalidateTransportPolicyCache();
    await getTransportPolicy(true);
    ElMessage.success(`已轮换密钥：${updated.keySummary.activeKid}`);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? "轮换密钥失败");
  } finally {
    keyRotating.value = false;
  }
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

function handleExportDossier() {
  const dossier = {
    title: "企业安全基线合规报告",
    generatedAt: new Date().toISOString(),
    score: complianceScore.value,
    policy: props.securityPolicy,
    transportEncryption: transportOverview.value
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
        <div class="metric-pill">
          <span class="metric-k">密钥</span>
          <code class="metric-code">{{ transportOverview?.keySummary.activeKid || "--" }}</code>
        </div>
        <div class="metric-sep" />
        <div class="metric-pill">
          <span class="metric-k">加密 / 拦截</span>
          <strong class="metric-v">{{ transportOverview?.telemetry.encryptedRequests ?? 0 }} / {{ transportOverview?.telemetry.rejectedPlaintext ?? 0 }}</strong>
        </div>
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
            <el-button size="small" :icon="Key" :loading="keyRotating" @click="handleRotateKey">轮换密钥</el-button>
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
              <el-button link type="primary" size="small" :icon="Setting" @click="showPathRules = !showPathRules">
                {{ showPathRules ? "收起路径规则" : "编辑路径规则" }}
              </el-button>
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
                已覆盖全量业务接口，文件流、WebSocket 与健康探针自动豁免。
              </template>
              <template v-else-if="transportForm.scopeMode === 'SENSITIVE_ONLY'">
                仅对登录认证、权限、系统、AI 与租户等 {{ parsePathLines(transportForm.sensitivePathsText).length }} 类敏感路径生效。
              </template>
              <template v-else>
                按下方自定义包含规则与豁免白名单精准控制生效接口。
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

        <!-- 按需展开的高级路径配置 -->
        <div v-if="showPathRules" class="path-editor-panel">
          <div class="path-editor-col">
            <label>包含路径</label>
            <el-input v-model="transportForm.includePathsText" type="textarea" :rows="3" placeholder="/api/**" />
          </div>
          <div class="path-editor-col">
            <label>高敏接口列表</label>
            <el-input v-model="transportForm.sensitivePathsText" type="textarea" :rows="3" placeholder="/api/auth/login" />
          </div>
          <div class="path-editor-col">
            <label>明文豁免白名单</label>
            <el-input v-model="transportForm.excludePathsText" type="textarea" :rows="3" placeholder="/api/health/**" />
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
  font-size: 12.5px;
  font-weight: 600;
  color: #334155;
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
  gap: 6px;

  label {
    font-size: 12px;
    font-weight: 600;
    color: #334155;
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
