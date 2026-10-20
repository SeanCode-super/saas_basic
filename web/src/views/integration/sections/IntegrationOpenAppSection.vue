<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Plus,
  Refresh,
  Key,
  Lock,
  Delete,
  Promotion
} from "@element-plus/icons-vue";
import {
  fetchOpenApps,
  saveOpenApp,
  regenerateOpenAppSecret,
  deleteOpenApp,
  type OpenAppRow
} from "@/api/modules/integration";
import { http } from "@/api/http";

const loading = ref(false);
const rows = ref<OpenAppRow[]>([]);

// Dialog for App Creation/Editing
const dialogVisible = ref(false);
const submitting = ref(false);
const form = reactive({
  id: undefined as number | undefined,
  appName: "",
  status: "ENABLED",
  rateLimit: 60,
  allowedPermissions: "*",
  remark: ""
});

// AppSecret Reveal Dialog
const secretDialogVisible = ref(false);
const activeSecret = ref({
  appName: "",
  appKey: "",
  appSecret: ""
});

// Online HMAC Signature Debugger
const debugDrawerVisible = ref(false);
const debugLoading = ref(false);
const debugForm = reactive({
  appKey: "",
  appSecret: "",
  nonce: "",
  timestamp: "",
  body: '{"action":"test_ping"}',
  signature: ""
});
const debugResponse = ref<any>(null);

async function loadData() {
  loading.value = true;
  try {
    rows.value = await fetchOpenApps();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "加载开放平台应用列表失败");
  } finally {
    loading.value = false;
  }
}

function openCreateDialog() {
  form.id = undefined;
  form.appName = "";
  form.status = "ENABLED";
  form.rateLimit = 60;
  form.allowedPermissions = "*";
  form.remark = "";
  dialogVisible.value = true;
}

async function handleSaveApp() {
  if (!form.appName.trim()) {
    ElMessage.warning("请输入应用名称");
    return;
  }
  submitting.value = true;
  try {
    const res = await saveOpenApp({
      id: form.id,
      appName: form.appName.trim(),
      status: form.status,
      rateLimit: form.rateLimit,
      allowedPermissions: form.allowedPermissions,
      remark: form.remark
    });
    ElMessage.success(`开放应用「${res.appName}」保存成功！`);
    dialogVisible.value = false;
    await loadData();
    if (!form.id) {
      activeSecret.value = {
        appName: res.appName,
        appKey: res.appKey,
        appSecret: res.appSecret
      };
      secretDialogVisible.value = true;
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "保存开放应用失败");
  } finally {
    submitting.value = false;
  }
}

async function handleRegenerateSecret(row: OpenAppRow) {
  try {
    await ElMessageBox.confirm(
      `重置 AppSecret 后，原秘钥将立即失效，所有已接入的外部系统必须同步更新。确认重置「${row.appName}」的秘钥？`,
      "安全警告",
      {
        confirmButtonText: "确认重置",
        cancelButtonText: "取消",
        type: "warning"
      }
    );
    const updated = await regenerateOpenAppSecret(row.id);
    ElMessage.success("秘钥已成功轮转重置！请妥善保存新秘钥。");
    activeSecret.value = {
      appName: updated.appName,
      appKey: updated.appKey,
      appSecret: updated.appSecret
    };
    secretDialogVisible.value = true;
    await loadData();
  } catch {
    // cancelled
  }
}

async function handleDeleteApp(row: OpenAppRow) {
  try {
    await ElMessageBox.confirm(
      `确定彻底删除开放应用「${row.appName} (${row.appKey})」吗？删除后不可恢复！`,
      "确认删除",
      {
        confirmButtonText: "彻底删除",
        cancelButtonText: "取消",
        type: "warning"
      }
    );
    await deleteOpenApp(row.id);
    ElMessage.success("开放应用已删除！");
    await loadData();
  } catch {
    // cancelled
  }
}

function openHmacDebugger(row?: OpenAppRow) {
  if (row) {
    debugForm.appKey = row.appKey;
    debugForm.appSecret = row.appSecret;
  }
  debugForm.nonce = Math.random().toString(36).substring(2, 10);
  debugForm.timestamp = Date.now().toString();
  debugResponse.value = null;
  debugDrawerVisible.value = true;
}

// Compute HMAC-SHA256 in browser using Web Crypto API
async function computeHmacSha256(secret: string, message: string): Promise<string> {
  const enc = new TextEncoder();
  const key = await window.crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: { name: "SHA-256" } },
    false,
    ["sign"]
  );
  const signature = await window.crypto.subtle.sign("HMAC", key, enc.encode(message));
  const hashArray = Array.from(new Uint8Array(signature));
  return hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function executeHmacTest() {
  if (!debugForm.appKey || !debugForm.appSecret) {
    ElMessage.warning("请填入 AppKey 和 AppSecret");
    return;
  }
  debugLoading.value = true;
  debugResponse.value = null;
  try {
    // Sign payload = appKey + nonce + timestamp + (body || "")
    const signPayload = debugForm.appKey + debugForm.nonce + debugForm.timestamp + (debugForm.body || "");
    const sig = await computeHmacSha256(debugForm.appSecret, signPayload);
    debugForm.signature = sig;

    const res = await http.post("/../open/api/v1/echo", JSON.parse(debugForm.body || "{}"), {
      headers: {
        "X-App-Key": debugForm.appKey,
        "X-Timestamp": debugForm.timestamp,
        "X-Nonce": debugForm.nonce,
        "X-Signature": sig
      }
    });
    debugResponse.value = res.data;
    ElMessage.success("HMAC-SHA256 签名鉴权成功！");
  } catch (error: any) {
    debugResponse.value = error?.response?.data || error?.message;
    ElMessage.error(error?.response?.data?.message || "HMAC 签名鉴权测试失败");
  } finally {
    debugLoading.value = false;
  }
}

onMounted(() => {
  void loadData();
});
</script>

<template>
  <div class="open-app-section">
    <!-- Header Banner -->
    <div class="section-header-card">
      <div class="header-meta">
        <div class="title-row">
          <h3>开发者开放平台 (Open API Gateway)</h3>
          <el-tag type="success" effect="plain" round>企业级认证</el-tag>
        </div>
        <p>
          为第三方企业生态与集成方提供安全的 Open API 凭据托管、防重放攻击与防篡改 HMAC-SHA256 签名鉴权及细粒度流控治理。
        </p>
      </div>
      <div class="header-actions">
        <el-button :icon="Promotion" @click="() => openHmacDebugger()">
          在线 HMAC 调试器
        </el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">
          新建接入应用
        </el-button>
      </div>
    </div>

    <!-- Apps Table -->
    <el-card shadow="never" class="apps-card">
      <template #header>
        <div class="card-header-row">
          <span>应用凭证接入台账</span>
          <el-button :icon="Refresh" circle size="small" @click="loadData" />
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border stripe style="width: 100%">
        <el-table-column prop="appName" label="应用名称" min-width="140" />
        <el-table-column prop="appKey" label="AppKey" width="220">
          <template #default="{ row }">
            <span class="mono-code">{{ row.appKey }}</span>
          </template>
        </el-table-column>
        <el-table-column label="AppSecret 秘钥" width="160" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :icon="Lock"
              @click="
                activeSecret = { appName: row.appName, appKey: row.appKey, appSecret: row.appSecret };
                secretDialogVisible = true;
              "
            >
              查看秘钥
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="rateLimit" label="流控上限" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.rateLimit }} QPS</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'danger'" size="small">
              {{ row.status === "ENABLED" ? "启用中" : "已禁用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注说明" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="接入时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Promotion" @click="openHmacDebugger(row)">
              联调
            </el-button>
            <el-button link type="warning" :icon="Key" @click="handleRegenerateSecret(row)">
              重置秘钥
            </el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDeleteApp(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑开放应用' : '新建 Open API 接入应用'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="应用名称" required>
          <el-input v-model="form.appName" placeholder="例如: 钉钉考勤同步助手 / 财务系统直连" />
        </el-form-item>
        <el-form-item label="单机并发流控限制 (QPS)">
          <el-input-number v-model="form.rateLimit" :min="1" :max="10000" />
        </el-form-item>
        <el-form-item label="授权范围 / 权限表达式">
          <el-input v-model="form.allowedPermissions" placeholder="* (全部开放) 或特定接口 scope" />
        </el-form-item>
        <el-form-item label="备注说明">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="对接业务背景与负责人信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSaveApp">确定创建</el-button>
      </template>
    </el-dialog>

    <!-- Secret View/Reveal Dialog -->
    <el-dialog
      v-model="secretDialogVisible"
      title="Open API 应用安全凭证"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="机密凭证安全告知"
        description="AppSecret 是系统鉴权的唯一非对称凭证，请勿将其暴露在客户端代码或前端请求中。"
        style="margin-bottom: 16px"
      />
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="应用名称">{{ activeSecret.appName }}</el-descriptions-item>
        <el-descriptions-item label="AppKey">
          <span class="mono-code">{{ activeSecret.appKey }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="AppSecret">
          <span class="mono-code" style="color: #dc2626;">{{ activeSecret.appSecret }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="secretDialogVisible = false">我已妥善记录</el-button>
      </template>
    </el-dialog>

    <!-- Online HMAC Debugger Drawer -->
    <el-drawer
      v-model="debugDrawerVisible"
      title="在线 HMAC-SHA256 签名调试器"
      size="560px"
      :destroy-on-close="true"
    >
      <div class="debugger-drawer-content">
        <p class="drawer-desc">
          基于 SHA-256 HMAC 算法生成 API 签名头，支持在线模拟网关签名鉴权与防重放检验。
        </p>
        <el-form label-position="top">
          <el-form-item label="AppKey">
            <el-input v-model="debugForm.appKey" placeholder="ak_xxxxxxxx" />
          </el-form-item>
          <el-form-item label="AppSecret">
            <el-input v-model="debugForm.appSecret" type="password" show-password placeholder="sk_xxxxxxxx" />
          </el-form-item>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="Nonce (随机串)">
                <el-input v-model="debugForm.nonce" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="Timestamp (毫秒)">
                <el-input v-model="debugForm.timestamp" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="请求 Payload (JSON)">
            <el-input v-model="debugForm.body" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item v-if="debugForm.signature" label="计算出的 HMAC-SHA256 签名">
            <el-input :model-value="debugForm.signature" readonly class="mono-code" />
          </el-form-item>
          <div style="margin-top: 10px">
            <el-button type="primary" :icon="Promotion" :loading="debugLoading" @click="executeHmacTest">
              发送测试请求并验证签名
            </el-button>
          </div>
        </el-form>

        <div v-if="debugResponse" class="debug-response-card">
          <div class="response-header">网关响应内容：</div>
          <pre class="response-pre">{{ JSON.stringify(debugResponse, null, 2) }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.open-app-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-header-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 18px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;

  .header-meta {
    .title-row {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 6px;

      h3 {
        margin: 0;
        font-size: 16px;
        font-weight: 600;
        color: #0f172a;
      }
    }

    p {
      margin: 0;
      font-size: 13px;
      color: #64748b;
    }
  }

  .header-actions {
    display: flex;
    gap: 10px;
  }
}

.apps-card {
  border-radius: 8px;

  .card-header-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-weight: 600;
    font-size: 14px;
    color: #1e293b;
  }
}

.mono-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.debugger-drawer-content {
  .drawer-desc {
    font-size: 13px;
    color: #64748b;
    margin-bottom: 16px;
  }

  .debug-response-card {
    margin-top: 20px;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    background: #f8fafc;
    padding: 12px;

    .response-header {
      font-size: 12px;
      font-weight: 600;
      color: #334155;
      margin-bottom: 8px;
    }

    .response-pre {
      margin: 0;
      font-family: ui-monospace, monospace;
      font-size: 12px;
      color: #0f172a;
      overflow-x: auto;
    }
  }
}
</style>
