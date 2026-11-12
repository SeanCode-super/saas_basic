<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Check, CopyDocument, Key, RefreshRight } from "@element-plus/icons-vue";
import { getTransportPolicy, invalidateTransportPolicyCache } from "@/api/http";
import {
  activateTransportSecurityKey,
  configureTransportSecurityKey,
  updateTransportSecurityPolicy
} from "@/api/modules/system";
import type { RetainedKeySummary, TransportSecurityOverview } from "@/api/modules/system";
import { generateX25519KeyPairHex } from "@/utils/transport-crypto";

const props = defineProps<{
  visible: boolean;
  overview: TransportSecurityOverview | null;
}>();

const emit = defineEmits<{
  "update:visible": [value: boolean];
  updated: [overview: TransportSecurityOverview];
}>();

const dialogVisible = computed({
  get: () => props.visible,
  set: (val: boolean) => emit("update:visible", val)
});

const activeTab = ref<"strategy" | "custom">("strategy");
const submitting = ref(false);
const activatingKid = ref<string | null>(null);
const previewDerivedPub = ref("");

const form = reactive({
  keyRotationHours: 24,
  maxGraceKeys: 3,
  generateKeyId: "",
  generateRemark: "",
  customKeyId: "",
  privateKeyInput: "",
  customRemark: ""
});

watch(
  () => [props.visible, props.overview] as const,
  ([open, ov]) => {
    if (open && ov) {
      form.keyRotationHours = ov.keySummary.keyRotationHours || ov.keyRotationHours || 24;
      form.maxGraceKeys = ov.keySummary.maxGraceKeys || ov.maxGraceKeys || 3;
      previewDerivedPub.value = "";
    }
  },
  { immediate: true }
);

const activeKey = computed(() => props.overview?.keySummary ?? null);
const retainedKeys = computed<RetainedKeySummary[]>(() => activeKey.value?.retainedKeys ?? []);

function sourceLabel(source?: string): string {
  return source === "CUSTOM_IMPORTED" ? "手动配置" : "自动轮换";
}

function formatEpoch(ms?: number): string {
  if (!ms) return "--";
  const d = new Date(ms);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

async function copyText(text: string | undefined, label: string) {
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success(`已复制${label}`);
  } catch {
    ElMessage.warning("当前环境不允许剪贴板写入，请手动选中复制");
  }
}

async function syncClientPolicy() {
  invalidateTransportPolicyCache();
  await getTransportPolicy(true);
}

function handleFillSampleKey() {
  const sample = generateX25519KeyPairHex();
  form.privateKeyInput = sample.privateKeyHex;
  previewDerivedPub.value = sample.publicKeyBase64;
  if (!form.customKeyId.trim()) {
    form.customKeyId = `k_custom_${Date.now().toString(36)}`;
  }
  ElMessage.success("已生成标准 RFC 7748 32 字节 X25519 私钥并填入");
}

async function handleSaveCycleOnly() {
  const ov = props.overview;
  if (!ov) return;
  submitting.value = true;
  try {
    const next = await updateTransportSecurityPolicy({
      mode: ov.mode,
      scopeMode: ov.scopeMode,
      encryptRequest: ov.encryptRequest,
      encryptResponse: ov.encryptResponse,
      encryptQuery: ov.encryptQuery,
      replayProtection: ov.replayProtection,
      timestampSkewSeconds: ov.timestampSkewSeconds,
      keyRotationHours: form.keyRotationHours,
      maxGraceKeys: form.maxGraceKeys,
      includePaths: ov.includePaths,
      excludePaths: ov.excludePaths,
      sensitivePaths: ov.sensitivePaths,
      remark: "更新密钥轮换周期与宽限保留策略"
    });
    await syncClientPolicy();
    emit("updated", next);
    ElMessage.success("密钥轮换周期与宽限设置已保存");
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? "保存密钥策略失败");
  } finally {
    submitting.value = false;
  }
}

async function handleGenerateAndRotate() {
  submitting.value = true;
  try {
    const next = await configureTransportSecurityKey({
      mode: "GENERATE",
      keyId: form.generateKeyId.trim() || undefined,
      keyRotationHours: form.keyRotationHours,
      maxGraceKeys: form.maxGraceKeys,
      remark: form.generateRemark.trim() || undefined
    });
    form.generateKeyId = "";
    form.generateRemark = "";
    await syncClientPolicy();
    emit("updated", next);
    ElMessage.success(`已生成并切换活跃密钥：${next.keySummary.activeKid}`);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? "生成轮换密钥失败");
  } finally {
    submitting.value = false;
  }
}

async function handleImportCustomKey() {
  if (!form.privateKeyInput.trim()) {
    ElMessage.warning("请先填入或生成 X25519 私钥");
    return;
  }
  submitting.value = true;
  try {
    const next = await configureTransportSecurityKey({
      mode: "CUSTOM",
      keyId: form.customKeyId.trim() || undefined,
      privateKeyInput: form.privateKeyInput.trim(),
      keyRotationHours: form.keyRotationHours,
      maxGraceKeys: form.maxGraceKeys,
      remark: form.customRemark.trim() || undefined
    });
    form.privateKeyInput = "";
    form.customRemark = "";
    previewDerivedPub.value = "";
    await syncClientPolicy();
    emit("updated", next);
    activeTab.value = "strategy";
    ElMessage.success(`自定义密钥已启用：${next.keySummary.activeKid}`);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? "导入自定义密钥失败");
  } finally {
    submitting.value = false;
  }
}

async function handleActivateRetained(row: RetainedKeySummary) {
  try {
    await ElMessageBox.confirm(
      `确认将活跃公钥切换回 ${row.keyId} 吗？客户端将在下次刷新策略时无感切换。`,
      "激活历史密钥",
      { confirmButtonText: "确认切换", cancelButtonText: "取消", type: "warning" }
    );
  } catch {
    return;
  }
  activatingKid.value = row.keyId;
  try {
    const next = await activateTransportSecurityKey(row.keyId);
    await syncClientPolicy();
    emit("updated", next);
    ElMessage.success(`已重新激活密钥：${next.keySummary.activeKid}`);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? "激活密钥失败");
  } finally {
    activatingKid.value = null;
  }
}
</script>

<template>
  <el-dialog v-model="dialogVisible" title="传输密钥治理与自定义配置 (X25519 + Vault)" width="min(780px, 94vw)" destroy-on-close>
    <div v-if="activeKey" class="key-dialog-body">
      <!-- 1. 当前活跃密钥摘要 -->
      <div class="active-key-card">
        <div class="active-key-head">
          <div class="key-id-line">
            <span class="key-icon"><el-icon><Key /></el-icon></span>
            <div>
              <div class="key-title-row">
                <strong>{{ activeKey.activeKid }}</strong>
                <el-tag size="small" :type="activeKey.keySource === 'CUSTOM_IMPORTED' ? 'warning' : 'success'" effect="light">
                  {{ sourceLabel(activeKey.keySource) }}
                </el-tag>
                <el-tag size="small" type="info" effect="plain">
                  Vault {{ activeKey.vaultActiveKeyVersion }} · AES-256-GCM 持久化
                </el-tag>
              </div>
              <div class="key-sub-meta">签发时间 {{ formatEpoch(activeKey.issuedAtEpochMs) }} · 宽限池保留 {{ activeKey.retainedKeyCount }} 个可用密钥</div>
            </div>
          </div>
        </div>

        <div class="key-copy-grid">
          <div class="copy-item">
            <span class="copy-label">活跃公钥 (Base64)</span>
            <div class="copy-value-row">
              <code>{{ activeKey.publicKey }}</code>
              <el-button link type="primary" size="small" :icon="CopyDocument" @click="copyText(activeKey.publicKey, '公钥')">
                复制
              </el-button>
            </div>
          </div>
          <div class="copy-item">
            <span class="copy-label">SHA-256 指纹</span>
            <div class="copy-value-row">
              <code>{{ activeKey.fingerprintSha256 }}</code>
              <el-button link type="primary" size="small" :icon="CopyDocument" @click="copyText(activeKey.fingerprintSha256, 'SHA-256 指纹')">
                复制
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 2. 轮换策略 & 导入自定义密钥 -->
      <el-tabs v-model="activeTab" class="key-tabs">
        <el-tab-pane label="轮换策略与自动生成" name="strategy">
          <div class="tab-panel-grid">
            <div class="field-cell">
              <label>建议轮换周期 (小时)</label>
              <el-input-number v-model="form.keyRotationHours" :min="1" :max="720" :step="12" size="small" />
            </div>
            <div class="field-cell">
              <label>宽限保留历史密钥数 (个)</label>
              <el-input-number v-model="form.maxGraceKeys" :min="1" :max="10" :step="1" size="small" />
            </div>
            <div class="field-cell">
              <label>新密钥 ID 前缀/名称 (可选)</label>
              <el-input v-model="form.generateKeyId" size="small" placeholder="留空自动生成 k_xxx" clearable />
            </div>
            <div class="field-cell">
              <label>轮换备注 (可选)</label>
              <el-input v-model="form.generateRemark" size="small" placeholder="例如：例行季度密钥轮换" clearable />
            </div>
          </div>
          <div class="tab-actions">
            <el-button size="small" :loading="submitting" :icon="Check" @click="handleSaveCycleOnly">
              仅保存周期与宽限设置
            </el-button>
            <el-button type="primary" size="small" :loading="submitting" :icon="RefreshRight" @click="handleGenerateAndRotate">
              立即生成并切换新密钥
            </el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="手动导入自定义密钥" name="custom">
          <div class="custom-key-form">
            <div class="custom-row-two">
              <div class="field-cell">
                <label>自定义密钥 ID (kid，可选)</label>
                <el-input v-model="form.customKeyId" size="small" placeholder="3-64位字母数字/._:-，如 k_prod_2026_v1" clearable />
              </div>
              <div class="field-cell">
                <label>配置说明 (可选)</label>
                <el-input v-model="form.customRemark" size="small" placeholder="例如：导入统一 KMS 托管密钥" clearable />
              </div>
            </div>

            <div class="field-cell">
              <div class="priv-label-bar">
                <label>X25519 私钥内容（支持 PKCS#8 PEM/Base64 或 32 字节 Hex/Base64 种子）</label>
                <el-button link type="primary" size="small" @click="handleFillSampleKey">
                  随机生成一组标准 X25519 密钥填入
                </el-button>
              </div>
              <el-input
                v-model="form.privateKeyInput"
                type="textarea"
                :rows="3"
                placeholder="例如 64 位 Hex 字符串，或 -----BEGIN PRIVATE KEY----- ... -----END PRIVATE KEY-----"
              />
              <div v-if="previewDerivedPub" class="derived-preview">
                本地预推导公钥：<code>{{ previewDerivedPub }}</code>
              </div>
            </div>

            <div class="tab-actions">
              <el-button type="primary" size="small" :loading="submitting" :icon="Check" @click="handleImportCustomKey">
                校验私钥并落库生效
              </el-button>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 3. 历史宽限密钥环列表 -->
      <div class="history-section">
        <div class="history-title">当前密钥环（活跃与宽限期历史密钥）</div>
        <el-table :data="retainedKeys" size="small" max-height="190" border>
          <el-table-column prop="keyId" label="密钥 ID (kid)" min-width="160">
            <template #default="{ row }">
              <code class="mono-sm">{{ row.keyId }}</code>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="96">
            <template #default="{ row }">
              <el-tag size="small" :type="row.keySource === 'CUSTOM_IMPORTED' ? 'warning' : 'info'" effect="plain">
                {{ sourceLabel(row.keySource) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="SHA-256 指纹" min-width="135">
            <template #default="{ row }">
              <code class="mono-sm">{{ row.fingerprintSha256.slice(0, 18) }}...</code>
            </template>
          </el-table-column>
          <el-table-column label="签发时间" width="140">
            <template #default="{ row }">
              <span class="time-text">{{ formatEpoch(row.issuedAtEpochMs) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态 / 操作" width="110" align="right">
            <template #default="{ row }">
              <el-tag v-if="row.active" type="success" size="small">当前活跃</el-tag>
              <el-button
                v-else
                link
                type="primary"
                size="small"
                :loading="activatingKid === row.keyId"
                @click="handleActivateRetained(row)"
              >
                设为活跃
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.key-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.active-key-card {
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid #bfdbfe;
  background: #f8fbff;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.active-key-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.key-id-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.key-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #dbeafe;
  color: #1d4ed8;
  display: grid;
  place-items: center;
}

.key-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 14px;
  color: #0f172a;
}

.key-sub-meta {
  font-size: 12px;
  color: #475569;
  margin-top: 2px;
}

.key-copy-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;

  @media (max-width: 680px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.copy-item {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 10px;
}

.copy-label {
  display: block;
  font-size: 11.5px;
  color: #475569;
  margin-bottom: 4px;
}

.copy-value-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;

  code {
    font-family: var(--sb-font-mono, monospace);
    font-size: 12px;
    color: #0f172a;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.tab-panel-grid,
.custom-row-two {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;

  @media (max-width: 640px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.custom-key-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.field-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;

  label {
    font-size: 12px;
    font-weight: 600;
    color: #475569;
  }
}

.priv-label-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px;
}

.derived-preview {
  font-size: 11.5px;
  color: #0369a1;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 6px;
  padding: 5px 8px;
  word-break: break-all;
}

.tab-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}

.history-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.history-title {
  font-size: 12.5px;
  font-weight: 600;
  color: #334155;
}

.mono-sm {
  font-family: var(--sb-font-mono, monospace);
  font-size: 11.5px;
  color: #1e293b;
}

.time-text {
  font-size: 12px;
  color: #475569;
}
</style>
