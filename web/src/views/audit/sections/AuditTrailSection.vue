<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  Download,
  Operation,
  Refresh,
  Search,
  Setting,
  User,
  View
} from "@element-plus/icons-vue";
import { fetchLoginLogs, fetchOperationLogs } from "@/api/modules/audit";
import type { LoginLog, OperationLog } from "@/api/modules/audit";

const props = defineProps<{
  auditPolicy: {
    retentionDays: number;
    recordPermissionChange: boolean;
    recordConfigChange: boolean;
    recordDataExport: boolean;
    recordFileDownload: boolean;
  };
  savePolicy: (message: string) => void;
}>();

const activeTab = ref("operations");
const loadingOperations = ref(false);
const loadingLogins = ref(false);

const operationLogs = ref<OperationLog[]>([]);
const loginLogs = ref<LoginLog[]>([]);

const opFilter = reactive({
  bizModule: "",
  operationType: ""
});

const loginFilter = reactive({
  username: "",
  success: ""
});

// Detail Drawer
const detailDrawerVisible = ref(false);
const activeLogDetail = ref<Record<string, any> | null>(null);

async function loadOperationLogs() {
  loadingOperations.value = true;
  try {
    operationLogs.value = await fetchOperationLogs({
      bizModule: opFilter.bizModule || undefined,
      operationType: opFilter.operationType || undefined
    });
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "加载操作审计日志失败");
  } finally {
    loadingOperations.value = false;
  }
}

async function loadLoginLogs() {
  loadingLogins.value = true;
  try {
    const successParam = loginFilter.success === "" ? undefined : loginFilter.success === "true";
    loginLogs.value = await fetchLoginLogs({
      username: loginFilter.username || undefined,
      success: successParam
    });
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "加载登录审计日志失败");
  } finally {
    loadingLogins.value = false;
  }
}

function handleTabChange(tab: any) {
  if (tab === "operations" && !operationLogs.value.length) {
    loadOperationLogs();
  } else if (tab === "logins" && !loginLogs.value.length) {
    loadLoginLogs();
  }
}

function openDetail(row: Record<string, any>) {
  activeLogDetail.value = row;
  detailDrawerVisible.value = true;
}

function exportCsv(type: "operations" | "logins") {
  let content = "";
  let filename = "";
  if (type === "operations") {
    content = "ID,发生时间,操作账号,业务模块,业务对象,动作代码,执行结果\n";
    operationLogs.value.forEach((r) => {
      content += `${r.id},"${r.occurredAt}","${r.operatorName}","${r.bizModule}","${r.bizType}:${r.bizId}","${r.operationType}","${r.success ? '成功' : '失败'}"\n`;
    });
    filename = `operation-audit-logs-${Date.now()}.csv`;
  } else {
    content = "ID,发生时间,登录账号,方式,客户端IP,状态,失败原因\n";
    loginLogs.value.forEach((r) => {
      content += `${r.id},"${r.occurredAt}","${r.username}","${r.loginType}","${r.loginIp}","${r.success ? '成功' : '失败'}","${r.failReason || '-'}"\n`;
    });
    filename = `login-audit-logs-${Date.now()}.csv`;
  }
  const blob = new Blob([content], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
  ElMessage.success("审计留痕数据已成功导出！");
}

onMounted(() => {
  loadOperationLogs();
});
</script>

<template>
  <div class="audit-trail-container">
    <el-tabs v-model="activeTab" type="border-card" class="audit-tabs" @tab-change="handleTabChange">
      <!-- Tab 1: Operation Logs -->
      <el-tab-pane name="operations">
        <template #label>
          <span class="tab-label">
            <el-icon><Operation /></el-icon>
            <span>业务操作审计</span>
            <el-badge :value="operationLogs.length" type="primary" class="tab-badge" />
          </span>
        </template>

        <div class="table-toolbar">
          <div class="filter-group">
            <el-input
              v-model="opFilter.bizModule"
              placeholder="业务模块 (如: iam, tenant)"
              clearable
              style="width: 200px"
              @keyup.enter="loadOperationLogs"
            />
            <el-input
              v-model="opFilter.operationType"
              placeholder="动作代码 (如: UPDATE, DELETE)"
              clearable
              style="width: 210px"
              @keyup.enter="loadOperationLogs"
            />
            <el-button type="primary" :icon="Search" @click="loadOperationLogs">查询</el-button>
            <el-button :icon="Refresh" @click="opFilter.bizModule = ''; opFilter.operationType = ''; loadOperationLogs()">重置</el-button>
          </div>
          <div class="action-group">
            <el-button :icon="Download" plain @click="exportCsv('operations')">导出 CSV 留痕</el-button>
            <el-button :icon="Refresh" :loading="loadingOperations" @click="loadOperationLogs">刷新</el-button>
          </div>
        </div>

        <el-table :data="operationLogs" stripe v-loading="loadingOperations" style="width: 100%" max-height="620">
          <el-table-column prop="occurredAt" label="操作时间" width="180">
            <template #default="{ row }">
              <span class="mono-text">{{ row.occurredAt?.replace('T', ' ')?.substring(0, 19) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="operatorName" label="操作人" width="140">
            <template #default="{ row }">
              <el-tag size="small" type="info" effect="plain">{{ row.operatorName || `UID #${row.operatorUserId}` }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="bizModule" label="所属模块" width="130">
            <template #default="{ row }">
              <el-tag size="small" type="primary">{{ row.bizModule }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="operationType" label="操作动作" width="150">
            <template #default="{ row }">
              <el-tag size="small" :type="row.operationType?.includes('DELETE') ? 'danger' : row.operationType?.includes('UPDATE') ? 'warning' : 'success'">
                {{ row.operationType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="业务对象" min-width="180">
            <template #default="{ row }">
              <span class="mono-text">{{ row.bizType || '-' }}: {{ row.bizId || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="success" label="执行结果" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.success" size="small" type="success" effect="light">成功</el-tag>
              <el-tag v-else size="small" type="danger" effect="light">失败</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- Tab 2: Login Logs -->
      <el-tab-pane name="logins">
        <template #label>
          <span class="tab-label">
            <el-icon><User /></el-icon>
            <span>登录认证审计</span>
            <el-badge :value="loginLogs.length" type="success" class="tab-badge" />
          </span>
        </template>

        <div class="table-toolbar">
          <div class="filter-group">
            <el-input
              v-model="loginFilter.username"
              placeholder="账号名称..."
              clearable
              style="width: 180px"
              @keyup.enter="loadLoginLogs"
            />
            <el-select v-model="loginFilter.success" placeholder="登录状态" clearable style="width: 140px">
              <el-option label="登录成功" value="true" />
              <el-option label="登录失败" value="false" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="loadLoginLogs">查询</el-button>
            <el-button :icon="Refresh" @click="loginFilter.username = ''; loginFilter.success = ''; loadLoginLogs()">重置</el-button>
          </div>
          <div class="action-group">
            <el-button :icon="Download" plain @click="exportCsv('logins')">导出 CSV 留痕</el-button>
            <el-button :icon="Refresh" :loading="loadingLogins" @click="loadLoginLogs">刷新</el-button>
          </div>
        </div>

        <el-table :data="loginLogs" stripe v-loading="loadingLogins" style="width: 100%" max-height="620">
          <el-table-column prop="occurredAt" label="登录时间" width="180">
            <template #default="{ row }">
              <span class="mono-text">{{ row.occurredAt?.replace('T', ' ')?.substring(0, 19) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="username" label="登录账号" width="150" />
          <el-table-column prop="loginType" label="认证方式" width="130">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.loginType || 'PASSWORD' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="loginIp" label="客户端 IP" width="150">
            <template #default="{ row }">
              <span class="mono-text">{{ row.loginIp }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="success" label="认证结果" width="110" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.success" size="small" type="success">登录成功</el-tag>
              <el-tag v-else size="small" type="danger">失败拦截</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="failReason" label="失败原因 / 响应状态" min-width="180">
            <template #default="{ row }">
              <span v-if="row.failReason" style="color: #ef4444; font-size: 13px;">{{ row.failReason }}</span>
              <span v-else style="color: #64748b; font-size: 13px;">凭证核验通过</span>
            </template>
          </el-table-column>
          <el-table-column label="详情" width="80" align="center">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" @click="openDetail(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- Tab 3: Retention Policy -->
      <el-tab-pane name="policy">
        <template #label>
          <span class="tab-label">
            <el-icon><Setting /></el-icon>
            <span>审计留痕基线与策略</span>
          </span>
        </template>

        <div class="policy-wrapper">
          <div class="policy-card">
            <h4>审计日志保留周期与事件捕获</h4>
            <p>关键动作必须纳入统一审计目录，按企业等保三级要求至少留存 180 天。</p>
            <el-form label-position="top" style="max-width: 600px; margin-top: 20px;">
              <el-form-item label="审计保留天数 (合规底线 180 天)">
                <el-input-number v-model="auditPolicy.retentionDays" :min="30" :max="3650" style="width: 100%" />
              </el-form-item>
              <el-form-item>
                <el-switch v-model="auditPolicy.recordPermissionChange" active-text="记录权限变更事件 (角色、接口、菜单、数据范围)" />
              </el-form-item>
              <el-form-item>
                <el-switch v-model="auditPolicy.recordConfigChange" active-text="记录系统与租户配置变更事件" />
              </el-form-item>
              <el-form-item>
                <el-switch v-model="auditPolicy.recordDataExport" active-text="记录大规模数据导出高危事件" />
              </el-form-item>
              <el-form-item>
                <el-switch v-model="auditPolicy.recordFileDownload" active-text="记录机密文件下载事件" />
              </el-form-item>
              <el-button type="primary" @click="savePolicy('审计留痕基线与策略已持久化生效')">保存留痕策略</el-button>
            </el-form>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- Detail Drawer -->
    <el-drawer v-model="detailDrawerVisible" title="审计日志全景详情" size="520px">
      <div v-if="activeLogDetail" class="detail-box">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="记录 ID">{{ activeLogDetail.id }}</el-descriptions-item>
          <el-descriptions-item label="租户 ID">{{ activeLogDetail.tenantId }}</el-descriptions-item>
          <el-descriptions-item label="时间戳">{{ activeLogDetail.occurredAt }}</el-descriptions-item>
          <el-descriptions-item label="操作/登录人">{{ activeLogDetail.operatorName || activeLogDetail.username }}</el-descriptions-item>
          <el-descriptions-item v-if="activeLogDetail.bizModule" label="业务模块">{{ activeLogDetail.bizModule }}</el-descriptions-item>
          <el-descriptions-item v-if="activeLogDetail.operationType" label="动作代码">{{ activeLogDetail.operationType }}</el-descriptions-item>
          <el-descriptions-item v-if="activeLogDetail.bizType" label="对象类型">{{ activeLogDetail.bizType }}</el-descriptions-item>
          <el-descriptions-item v-if="activeLogDetail.bizId" label="对象标识">{{ activeLogDetail.bizId }}</el-descriptions-item>
          <el-descriptions-item v-if="activeLogDetail.loginIp" label="客户端 IP">{{ activeLogDetail.loginIp }}</el-descriptions-item>
          <el-descriptions-item label="执行结果">
            <el-tag :type="activeLogDetail.success ? 'success' : 'danger'">{{ activeLogDetail.success ? '成功' : '失败' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="activeLogDetail.failReason" label="失败原因">{{ activeLogDetail.failReason }}</el-descriptions-item>
        </el-descriptions>
        <div class="json-dump">
          <h4>原始事件载荷 (Event Payload)</h4>
          <pre>{{ JSON.stringify(activeLogDetail, null, 2) }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.audit-trail-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.audit-tabs {
  background: #fff;
  border-radius: var(--sb-radius-md);
  border: 1px solid var(--sb-border-color);
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.tab-badge {
  margin-left: 4px;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--sb-surface-soft, #f8fafc);
  border-radius: var(--sb-radius-sm);
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filter-group,
.action-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mono-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: var(--sb-text-secondary);
}

.policy-wrapper {
  padding: 16px 20px;
}

.policy-card {
  h4 {
    margin: 0 0 6px;
    font-size: 16px;
    font-weight: 600;
  }

  p {
    margin: 0;
    font-size: 13px;
    color: var(--sb-text-secondary);
  }
}

.detail-box {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.json-dump {
  h4 {
    margin: 8px 0;
    font-size: 14px;
    font-weight: 600;
  }

  pre {
    background: #0f172a;
    color: #38bdf8;
    padding: 12px;
    border-radius: 6px;
    font-size: 12px;
    overflow-x: auto;
    max-height: 280px;
  }
}
</style>
