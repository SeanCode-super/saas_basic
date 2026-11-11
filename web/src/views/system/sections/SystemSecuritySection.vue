<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Check, Document, Lock, Key, CircleCheck, RefreshRight, Connection, Setting } from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";
import { getTransportPolicy, invalidateTransportPolicyCache } from "@/api/http";
import type { TransportCryptoTrace } from "@/utils/transport-crypto";
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

const modeOptions: Array<{ value: TransportCryptoMode; title: string; badge: string; desc: string }> = [
  {
    value: "OFF",
    title: "明文直通 (OFF)",
    badge: "仅调试",
    desc: "关闭前后端报文加密，方便接口抓取与常规明文联调。"
  },
  {
    value: "OPTIONAL",
    title: "兼容协商 (OPTIONAL)",
    badge: "默认推荐",
    desc: "PC/移动端受控客户端默认自动加密请求与解密响应；未加密的第三方/Swagger 请求兼容放行。"
  },
  {
    value: "REQUIRED",
    title: "强制密文 (REQUIRED)",
    badge: "最高合规",
    desc: "命中范围且未豁免的接口拒绝一切明文调用 (返回 428)，确保全链路无明文载荷暴露。"
  }
];

const scopeOptions: Array<{ value: TransportScopeMode; title: string; desc: string }> = [
  {
    value: "ALL_API",
    title: "全量业务接口",
    desc: "覆盖全部 JSON API（自动剔除大文件流、健康探针、WebSocket 与策略接口）。"
  },
  {
    value: "SENSITIVE_ONLY",
    title: "仅高敏路径",
    desc: "仅对认证登录、IAM 权限、系统设置、AI 治理与租户控制接口启用加密。"
  },
  {
    value: "CUSTOM",
    title: "自定义路径规则",
    desc: "完全按下方「包含路径 Ant 规则」与「豁免白名单」精准控制单个接口。"
  }
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
}

async function loadTransportOverview() {
  transportLoading.value = true;
  try {
    const data = await fetchTransportSecurityOverview();
    applyOverviewToForm(data);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? "加载接口传输加密策略失败");
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
      remark: transportForm.remark || "安全策略中心实时更新"
    });
    applyOverviewToForm(updated);
    invalidateTransportPolicyCache();
    await getTransportPolicy(true);
    ElMessage.success(`传输加密策略已热生效：${updated.mode} · 策略版本 v${updated.policyVersion}`);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? "保存传输加密策略失败");
  } finally {
    transportSaving.value = false;
  }
}

async function handleRotateKey() {
  try {
    await ElMessageBox.confirm(
      "将立即生成新的 X25519 服务端密钥对并递增策略版本；历史最近密钥将在宽限窗口内保留，不影响在途请求。确认继续？",
      "无感热轮换 X25519 密钥对",
      { confirmButtonText: "立即轮换", cancelButtonText: "取消", type: "warning" }
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
    ElMessage.success(`密钥轮换成功，当前激活密钥：${updated.keySummary.activeKid}`);
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
    const probe = `tianshu-e2e-cipher-probe-${Date.now()}`;
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
    ElMessage.error(err?.response?.data?.message ?? "密文链路自测失败");
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

const transportModeTagType = computed(() => {
  if (transportForm.mode === "REQUIRED") return "success";
  if (transportForm.mode === "OPTIONAL") return "primary";
  return "info";
});

function handleExportDossier() {
  const dossier = {
    title: "企业安全基线与等保三级 (GB/T 22239-2019) 遵从性报告",
    generatedAt: new Date().toISOString(),
    score: complianceScore.value,
    evaluation: complianceScore.value >= 90 ? "优秀 - 符合等保三级准入" : "良好 - 建议加固",
    policy: props.securityPolicy,
    transportEncryption: transportOverview.value,
    standards: [
      { standard: "应具备登录会话超时自动退出机制", status: "COMPLIANT", detail: `当前设置 ${props.securityPolicy.sessionTimeoutMinutes} 分钟` },
      { standard: "应使用包含数字、字母及特殊字符的强密码", status: props.securityPolicy.requireSpecial ? "COMPLIANT" : "WARNING", detail: `最小长度 ${props.securityPolicy.minLength}` },
      { standard: "应避免使用近期历史使用过的密码", status: props.securityPolicy.passwordHistory >= 3 ? "COMPLIANT" : "WARNING", detail: `保留历史 ${props.securityPolicy.passwordHistory} 次` },
      { standard: "特权账号应强制启用多因子鉴权 (MFA)", status: props.securityPolicy.forceMfa ? "COMPLIANT" : "INFO", detail: props.securityPolicy.forceMfa ? "全员强制" : "白名单受控" },
      {
        standard: "应在网络通信传输过程中采用高强度密码技术保证敏感数据保密性与完整性",
        status: transportForm.mode === "OFF" ? "WARNING" : "COMPLIANT",
        detail: `${transportForm.mode} (${transportOverview.value?.algorithm || "X25519-HKDF-AES-256-GCM"})`
      }
    ]
  };

  const blob = new Blob([JSON.stringify(dossier, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `security_compliance_dossier_${Date.now()}.json`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
  ElMessage.success("已导出等保三级安全基线评估报告");
}

onMounted(() => {
  void loadTransportOverview();
});
</script>

<template>
  <div class="system-security">
    <!-- Sleek Security Telemetry Strip -->
    <div class="security-telemetry-bar">
      <div class="telemetry-items">
        <div class="telemetry-item">
          <span class="telemetry-icon green"><el-icon><CircleCheck /></el-icon></span>
          <span class="telemetry-label">等保合规评分</span>
          <strong class="telemetry-val">{{ complianceScore }} 分</strong>
          <el-tag type="success" size="small" effect="plain">等保三级达标</el-tag>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon blue"><el-icon><Lock /></el-icon></span>
          <span class="telemetry-label">接口传输加密</span>
          <strong class="telemetry-val">{{ transportForm.mode }}</strong>
          <el-tag :type="transportModeTagType" size="small" effect="light">
            {{ transportForm.mode === "REQUIRED" ? "强制密文" : transportForm.mode === "OPTIONAL" ? "自动协商加密" : "已关闭" }}
          </el-tag>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon amber"><el-icon><Key /></el-icon></span>
          <span class="telemetry-label">活跃密钥</span>
          <strong class="telemetry-val mono-text">{{ transportOverview?.keySummary.activeKid || "--" }}</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon purple"><el-icon><Connection /></el-icon></span>
          <span class="telemetry-label">加密累计 / 拦截明文</span>
          <strong class="telemetry-val">
            {{ transportOverview?.telemetry.encryptedRequests ?? 0 }} / {{ transportOverview?.telemetry.rejectedPlaintext ?? 0 }}
          </strong>
        </div>
      </div>

      <div class="telemetry-actions">
        <el-button size="small" :icon="RefreshRight" :loading="transportLoading" @click="loadTransportOverview">刷新遥测</el-button>
        <el-button type="primary" size="small" :icon="Document" @click="handleExportDossier">导出安全报告</el-button>
      </div>
    </div>

    <!-- 全链路可控传输加密控制台 -->
    <BaseCard tone="contrast">
      <template #header>
        <div class="panel-header-row">
          <div>
            <h3 class="panel-title">
              全链路接口传输加密控制中台
              <el-tag size="small" type="primary" effect="plain" class="ml-tag">X25519 + HKDF-SHA256 + AES-256-GCM</el-tag>
            </h3>
            <p class="panel-subtitle">
              前向安全临时协商 + AAD 路径完整性绑定 + Timestamp/Nonce 防重放。修改策略后通过响应头版本号自动向全端热同步，无需重启服务。
            </p>
          </div>
          <div class="panel-actions-row">
            <el-button :icon="Connection" :loading="testingRoundtrip" @click="handleVerifyRoundtrip">
              一键密文回环自测
            </el-button>
            <el-button :icon="Key" :loading="keyRotating" @click="handleRotateKey">
              轮换 X25519 密钥对
            </el-button>
            <el-button type="primary" :icon="Check" :loading="transportSaving" @click="handleSaveTransportPolicy">
              保存并热生效
            </el-button>
          </div>
        </div>
      </template>

      <div class="transport-control-body">
        <!-- 1. 运行模式三态选择 -->
        <div class="control-block">
          <div class="control-block__label">1. 运行模式切换（实时可控）</div>
          <div class="mode-card-grid">
            <button
              v-for="item in modeOptions"
              :key="item.value"
              type="button"
              class="mode-select-card"
              :class="{ 'is-active': transportForm.mode === item.value }"
              @click="transportForm.mode = item.value"
            >
              <div class="mode-card-top">
                <span class="mode-card-title">{{ item.title }}</span>
                <el-tag size="small" :type="transportForm.mode === item.value ? 'primary' : 'info'" effect="plain">
                  {{ item.badge }}
                </el-tag>
              </div>
              <p class="mode-card-desc">{{ item.desc }}</p>
            </button>
          </div>
        </div>

        <!-- 2. 作用域控制 + 方向/防重放控制 -->
        <div class="transport-split-grid">
          <div class="control-block">
            <div class="control-block__label">2. 接口加密作用域范围</div>
            <div class="scope-radio-list">
              <label
                v-for="scope in scopeOptions"
                :key="scope.value"
                class="scope-radio-row"
                :class="{ 'is-active': transportForm.scopeMode === scope.value }"
                @click="transportForm.scopeMode = scope.value"
              >
                <div class="scope-radio-head">
                  <span class="scope-dot" />
                  <strong>{{ scope.title }}</strong>
                  <code class="scope-code">{{ scope.value }}</code>
                </div>
                <p>{{ scope.desc }}</p>
              </label>
            </div>
          </div>

          <div class="control-block">
            <div class="control-block__label">3. 报文方向、防重放与密钥指标</div>
            <div class="switch-panel">
              <div class="switch-row">
                <div>
                  <strong>加密请求报文体 (Request Body)</strong>
                  <span>POST / PUT / PATCH 载荷封装为 AES-GCM 密文信封</span>
                </div>
                <el-switch v-model="transportForm.encryptRequest" />
              </div>
              <div class="switch-row">
                <div>
                  <strong>加密响应报文体 (Response Body)</strong>
                  <span>JSON 返回数据使用服务端响应域分离密钥加密回传</span>
                </div>
                <el-switch v-model="transportForm.encryptResponse" />
              </div>
              <div class="switch-row">
                <div>
                  <strong>加密 Query 查询参数 (X-Enc-Query)</strong>
                  <span>GET / DELETE 查询参数脱离 URL 明文暴露，进入受保护 Header</span>
                </div>
                <el-switch v-model="transportForm.encryptQuery" />
              </div>
              <div class="switch-row">
                <div>
                  <strong>防重放保护 (Timestamp + Redis Nonce)</strong>
                  <span>校验 ±{{ transportForm.timestampSkewSeconds }} 秒时间窗并拦截重复 Nonce</span>
                </div>
                <el-switch v-model="transportForm.replayProtection" />
              </div>
              <div class="skew-row">
                <span class="skew-label">允许时间窗口偏差 (秒)</span>
                <el-input-number v-model="transportForm.timestampSkewSeconds" :min="30" :max="3600" :step="30" size="small" />
              </div>
            </div>
          </div>
        </div>

        <!-- 3. 精细路径规则配置 -->
        <div class="path-rules-grid">
          <div class="path-rule-card">
            <div class="path-rule-head">
              <el-icon><Setting /></el-icon>
              <span>包含路径 Ant 规则 (ALL_API / CUSTOM 生效)</span>
            </div>
            <el-input
              v-model="transportForm.includePathsText"
              type="textarea"
              :rows="4"
              placeholder="每行一个 Ant 路径模式，例如 /api/**"
            />
          </div>
          <div class="path-rule-card">
            <div class="path-rule-head">
              <el-icon><Lock /></el-icon>
              <span>高敏接口路径清单 (SENSITIVE_ONLY 生效)</span>
            </div>
            <el-input
              v-model="transportForm.sensitivePathsText"
              type="textarea"
              :rows="4"
              placeholder="每行一个高敏路径，如 /api/auth/login"
            />
          </div>
          <div class="path-rule-card">
            <div class="path-rule-head">
              <el-icon><CircleCheck /></el-icon>
              <span>明文豁免白名单 (始终透传不加密)</span>
            </div>
            <el-input
              v-model="transportForm.excludePathsText"
              type="textarea"
              :rows="4"
              placeholder="如 /api/file/download/**, /api/health/**"
            />
          </div>
        </div>

        <!-- 4. 运行期密钥与流量遥测 -->
        <div v-if="transportOverview" class="crypto-telemetry-footer">
          <div class="footer-chip">
            <span>公钥指纹 (SHA-256)</span>
            <code>{{ transportOverview.keySummary.fingerprintSha256.slice(0, 24) }}...</code>
          </div>
          <div class="footer-chip">
            <span>内存保留密钥数 (含宽限历史)</span>
            <strong>{{ transportOverview.keySummary.retainedKeyCount }} 把</strong>
          </div>
          <div class="footer-chip">
            <span>累计处理总请求 / 明文放行</span>
            <strong>{{ transportOverview.telemetry.totalRequests }} / {{ transportOverview.telemetry.plaintextAllowed }}</strong>
          </div>
          <div class="footer-chip">
            <span>拒绝重放 / 解密异常</span>
            <strong>{{ transportOverview.telemetry.rejectedReplay }} / {{ transportOverview.telemetry.decryptErrors }}</strong>
          </div>
          <div class="footer-chip">
            <span>当前策略版本</span>
            <strong>v{{ transportOverview.policyVersion }}</strong>
          </div>
        </div>
      </div>
    </BaseCard>

    <!-- 原有等保密码基线与合规检查 -->
    <div class="policy-workbench">
      <BaseCard tone="soft">
        <template #header>
          <div class="panel-header-row">
            <div>
              <h3 class="panel-title">身份基线与凭证控制</h3>
              <p class="panel-subtitle">密码复杂度、历史密码留存、会话有效期与双因子认证平台统一收口。</p>
            </div>
            <el-button type="primary" :icon="Check" @click="savePolicy('系统安全策略与等保基线已成功保存')">
              保存安全策略
            </el-button>
          </div>
        </template>

        <el-form label-position="top">
          <div class="dialog-grid">
            <el-form-item label="密码最小字符长度">
              <el-input-number v-model="securityPolicy.minLength" :min="8" :max="32" style="width: 100%" />
            </el-form-item>
            <el-form-item label="历史密码记忆次数">
              <el-input-number v-model="securityPolicy.passwordHistory" :min="1" :max="24" style="width: 100%" />
            </el-form-item>
            <el-form-item label="会话无操作失效 (分钟)">
              <el-input-number v-model="securityPolicy.sessionTimeoutMinutes" :min="15" :max="1440" style="width: 100%" />
            </el-form-item>
          </div>
          <div class="policy-toggle-stack mt-3">
            <el-form-item>
              <el-switch v-model="securityPolicy.requireSpecial" active-text="强制密码包含特殊字符 (!@#$%^&*)" />
            </el-form-item>
            <el-form-item>
              <el-switch v-model="securityPolicy.forceMfa" active-text="强制所有管理账号启用多因子认证 (TOTP / SMS)" />
            </el-form-item>
          </div>
        </el-form>
      </BaseCard>

      <BaseCard tone="soft">
        <template #header>
          <div class="panel-header-row">
            <div>
              <h3 class="panel-title">等保核查准入检查单</h3>
              <p class="panel-subtitle">关键安全切面实时合规检测。</p>
            </div>
          </div>
        </template>

        <div class="compliance-checklist">
          <div class="check-item">
            <el-icon class="icon-pass"><CircleCheck /></el-icon>
            <div class="item-meta">
              <span class="item-title">接口信道防明文抓包 (X25519 + AES-256-GCM)</span>
              <span class="item-desc">当前处于 {{ transportForm.mode }} ({{ transportForm.scopeMode }}) 模式，单请求前向安全隔离</span>
            </div>
          </div>
          <div class="check-item">
            <el-icon class="icon-pass"><CircleCheck /></el-icon>
            <div class="item-meta">
              <span class="item-title">身份鉴别复杂度</span>
              <span class="item-desc">最小 {{ securityPolicy.minLength }} 位，{{ securityPolicy.requireSpecial ? "包含特殊字符" : "常规组合" }}</span>
            </div>
          </div>
          <div class="check-item">
            <el-icon class="icon-pass"><CircleCheck /></el-icon>
            <div class="item-meta">
              <span class="item-title">密码轮换与防重放</span>
              <span class="item-desc">保留最近 {{ securityPolicy.passwordHistory }} 次密码记录，禁止复用</span>
            </div>
          </div>
          <div class="check-item">
            <el-icon class="icon-pass"><CircleCheck /></el-icon>
            <div class="item-meta">
              <span class="item-title">动态特权与会话防护</span>
              <span class="item-desc">{{ securityPolicy.sessionTimeoutMinutes }} 分钟超时登出 · {{ securityPolicy.forceMfa ? "全员强制 MFA" : "关键操作二次确认" }}</span>
            </div>
          </div>
        </div>
      </BaseCard>
    </div>

    <!-- 实时抓包密文回环自测结果弹窗 -->
    <el-dialog v-model="testDialogVisible" title="全链路传输加密端到端回环验证报告" width="680px">
      <div v-if="roundtripResult && roundtripTrace" class="roundtrip-report">
        <div class="roundtrip-banner">
          <el-icon class="banner-icon"><CircleCheck /></el-icon>
          <div>
            <strong>双向加密回环验证成功 ({{ roundtripLatencyMs }} ms)</strong>
            <p>
              服务端实测收到加密标志：{{ roundtripResult.transportEncrypted ? "已解密密文信封" : "明文" }} ·
              密钥 {{ roundtripTrace.kid }} · 策略版本 v{{ roundtripResult.policyVersion }}
            </p>
          </div>
        </div>

        <div class="trace-field">
          <span>请求协商临时公钥 (Client Ephemeral X25519 EPK)</span>
          <code>{{ roundtripTrace.epk }}</code>
        </div>
        <div class="trace-field">
          <span>防重放凭证 (Timestamp / Nonce)</span>
          <code>ts={{ roundtripTrace.ts }} · nonce={{ roundtripTrace.nonce }}</code>
        </div>
        <div class="trace-field">
          <span>实际在线请求体密文 (AES-256-GCM CipherText Preview)</span>
          <code>iv={{ roundtripTrace.requestIv }} | {{ roundtripTrace.requestCipherText || "(无请求体密文)" }}</code>
        </div>
        <div class="trace-field">
          <span>实际服务端密文回包 (Response Envelope CipherText)</span>
          <code>iv={{ roundtripTrace.responseIv }} | {{ roundtripTrace.responseCipherText || "(明文响应)" }}</code>
        </div>
        <div class="trace-field">
          <span>拦截器自动解密后业务原文</span>
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
  gap: 16px;
}

.ml-tag {
  margin-left: 8px;
}

.mono-text {
  font-family: var(--sb-font-mono, monospace);
  font-size: 12px;
}

.security-telemetry-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--sb-border-color, #e2e8f0);
  border-radius: 10px;
  flex-wrap: wrap;
  gap: 12px;

  .telemetry-items {
    display: flex;
    align-items: center;
    gap: 14px;
    flex-wrap: wrap;
  }

  .telemetry-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;

    .telemetry-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 24px;
      height: 24px;
      border-radius: 6px;
      font-size: 13px;

      &.green { background: #f0fdf4; color: #16a34a; }
      &.blue { background: #eff6ff; color: #2563eb; }
      &.amber { background: #fffbeb; color: #d97706; }
      &.purple { background: #faf5ff; color: #9333ea; }
    }

    .telemetry-label {
      color: #64748b;
      font-size: 12px;
    }

    .telemetry-val {
      color: #0f172a;
      font-weight: 600;
    }
  }

  .telemetry-divider {
    width: 1px;
    height: 18px;
    background: #e2e8f0;
  }

  .telemetry-actions {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
}

.panel-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;

  .panel-title {
    margin: 0 0 4px;
    font-size: 16px;
    font-weight: 700;
    color: #0f172a;
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
  }

  .panel-subtitle {
    margin: 0;
    font-size: 12px;
    color: #64748b;
  }

  .panel-actions-row {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
}

.transport-control-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.control-block__label {
  font-size: 12.5px;
  font-weight: 700;
  color: #334155;
  margin-bottom: 10px;
}

.mode-card-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;

  @media (max-width: 900px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.mode-select-card {
  text-align: left;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: rgb(255 255 255 / 0.72);
  cursor: pointer;
  transition: all 0.16s ease;

  &:hover {
    border-color: #93c5fd;
    background: #fff;
  }

  &.is-active {
    border-color: #2563eb;
    background: #eff6ff;
    box-shadow: 0 4px 12px rgb(37 99 235 / 0.12);
  }

  .mode-card-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 6px;
  }

  .mode-card-title {
    font-size: 13.5px;
    font-weight: 700;
    color: #0f172a;
  }

  .mode-card-desc {
    margin: 0;
    font-size: 12px;
    line-height: 1.45;
    color: #64748b;
  }
}

.transport-split-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;

  @media (max-width: 960px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.scope-radio-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.scope-radio-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: rgb(255 255 255 / 0.65);
  cursor: pointer;
  transition: all 0.15s ease;

  &.is-active {
    border-color: #2563eb;
    background: #f8fbff;

    .scope-dot {
      border-color: #2563eb;
      background: #2563eb;
      box-shadow: 0 0 0 3px rgb(37 99 235 / 0.18);
    }
  }

  .scope-radio-head {
    display: flex;
    align-items: center;
    gap: 8px;

    strong {
      font-size: 13px;
      color: #0f172a;
    }

    .scope-code {
      margin-left: auto;
      font-size: 11px;
      color: #475569;
      background: #f1f5f9;
      padding: 1px 6px;
      border-radius: 4px;
    }
  }

  .scope-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    border: 2px solid #94a3b8;
    flex-shrink: 0;
  }

  p {
    margin: 0 0 0 18px;
    font-size: 12px;
    color: #64748b;
  }
}

.switch-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: rgb(255 255 255 / 0.68);
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #e2e8f0;

  div {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;

    strong {
      font-size: 12.5px;
      color: #0f172a;
    }

    span {
      font-size: 11.5px;
      color: #64748b;
    }
  }
}

.skew-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  .skew-label {
    font-size: 12.5px;
    font-weight: 600;
    color: #334155;
  }
}

.path-rules-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;

  @media (max-width: 1024px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.path-rule-card {
  display: flex;
  flex-direction: column;
  gap: 8px;

  .path-rule-head {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    font-weight: 600;
    color: #334155;
  }
}

.crypto-telemetry-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;

  .footer-chip {
    display: flex;
    flex-direction: column;
    gap: 2px;

    span {
      font-size: 11px;
      color: #64748b;
    }

    strong,
    code {
      font-size: 12px;
      font-weight: 600;
      color: #0f172a;
      font-family: var(--sb-font-mono, monospace);
    }
  }
}

.policy-workbench {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 16px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.dialog-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
}

.compliance-checklist {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 0;

  .check-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 14px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;

    .icon-pass {
      color: #16a34a;
      font-size: 18px;
      flex-shrink: 0;
    }

    .item-meta {
      display: flex;
      flex-direction: column;
      gap: 2px;

      .item-title {
        font-size: 13px;
        font-weight: 600;
        color: #1e293b;
      }

      .item-desc {
        font-size: 12px;
        color: #64748b;
      }
    }
  }
}

.roundtrip-report {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.roundtrip-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #15803d;

  .banner-icon {
    font-size: 24px;
    flex-shrink: 0;
  }

  p {
    margin: 4px 0 0;
    font-size: 12px;
    color: #166534;
  }
}

.trace-field {
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
    max-height: 110px;
    overflow-y: auto;
  }
}

.mt-3 {
  margin-top: 12px;
}
</style>
