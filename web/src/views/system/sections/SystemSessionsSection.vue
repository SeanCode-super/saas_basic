<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Monitor,
  User,
  Refresh,
  SwitchButton,
  CopyDocument,
  Lock,
  Iphone,
  Search,
  CircleCheck,
  WarningFilled
} from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";
import { fetchOnlineSessions, kickoutOnlineSession } from "@/api/modules/auth";
import type { OnlineSession } from "@/api/modules/auth";
import { useAuthStore } from "@/stores/modules/auth";

const authStore = useAuthStore();
const loading = ref(false);
const sessions = ref<OnlineSession[]>([]);
const searchKeyword = ref("");
const selectedSessions = ref<OnlineSession[]>([]);

async function loadSessions() {
  loading.value = true;
  try {
    const list = await fetchOnlineSessions();
    sessions.value = list;
  } catch (err: any) {
    ElMessage.error(err.message || "获取在线会话失败");
  } finally {
    loading.value = false;
  }
}

const filteredSessions = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase();
  if (!kw) return sessions.value;
  return sessions.value.filter(
    (s) =>
      s.sessionNo.toLowerCase().includes(kw) ||
      s.username.toLowerCase().includes(kw) ||
      (s.nickname && s.nickname.toLowerCase().includes(kw)) ||
      (s.loginIp && s.loginIp.toLowerCase().includes(kw)) ||
      (s.userAgent && s.userAgent.toLowerCase().includes(kw))
  );
});

const stats = computed(() => {
  const total = sessions.value.length;
  const platform = sessions.value.filter((s) => s.userType === "PLATFORM").length;
  const tenant = sessions.value.filter((s) => s.userType !== "PLATFORM").length;
  const current = sessions.value.find((s) => s.isCurrent);
  return { total, platform, tenant, current };
});

function parseUserAgent(ua?: string) {
  if (!ua) return { os: "未知系统", browser: "未知客户端", isMobile: false };
  let os = "其他系统";
  if (ua.includes("Mac OS") || ua.includes("Macintosh")) os = "macOS";
  else if (ua.includes("Windows NT 10.0")) os = "Windows 10/11";
  else if (ua.includes("Windows")) os = "Windows";
  else if (ua.includes("Android")) os = "Android";
  else if (ua.includes("iPhone") || ua.includes("iPad")) os = "iOS";
  else if (ua.includes("Linux")) os = "Linux";

  let browser = "Web 浏览器";
  if (ua.includes("Edg/")) browser = "Microsoft Edge";
  else if (ua.includes("Chrome/")) browser = "Google Chrome";
  else if (ua.includes("Safari/") && !ua.includes("Chrome")) browser = "Apple Safari";
  else if (ua.includes("Firefox/")) browser = "Mozilla Firefox";
  else if (ua.includes("curl/")) browser = "cURL 终端";
  else if (ua.includes("Python") || ua.includes("urllib")) browser = "Python SDK / 脚本";
  else if (ua.includes("Postman")) browser = "Postman 客户端";

  const isMobile = ua.includes("Mobile") || ua.includes("Android") || ua.includes("iPhone");
  return { os, browser, isMobile };
}

function copySessionNo(no: string) {
  navigator.clipboard.writeText(no);
  ElMessage.success(`会话编号已复制: ${no}`);
}

async function handleKickout(row: OnlineSession) {
  const isSelf = row.isCurrent;
  const confirmMsg = isSelf
    ? "您正在强退【当前本机登录会话】！强制下线后系统将立即注销并跳转至登录页，是否确认？"
    : `确定要强制下线用户【${row.nickname || row.username}】(${row.loginIp || "未知IP"}) 的会话吗？该客户端凭证将立即作废。`;

  try {
    await ElMessageBox.confirm(confirmMsg, isSelf ? "警告：注销当前会话" : "强制下线确认", {
      confirmButtonText: "确认下线",
      cancelButtonText: "取消",
      type: isSelf ? "error" : "warning"
    });

    await kickoutOnlineSession(row.sessionNo);
    ElMessage.success("会话已成功下线并作废");

    if (isSelf) {
      await authStore.logout();
      return;
    }

    await loadSessions();
  } catch (e: any) {
    if (e !== "cancel") {
      ElMessage.error(e.message || "强制下线失败");
    }
  }
}

async function handleBatchKickout() {
  const candidates = selectedSessions.value.filter((s) => !s.isCurrent);
  if (candidates.length === 0) {
    ElMessage.warning("请选择要批量下线的其他用户会话（无法直接批量选择当前登录会话）");
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确定要批量强制下线选中的 ${candidates.length} 个在线会话吗？操作不可逆。`,
      "批量强退会话治理",
      {
        confirmButtonText: "立即批量下线",
        cancelButtonText: "取消",
        type: "warning"
      }
    );

    for (const item of candidates) {
      try {
        await kickoutOnlineSession(item.sessionNo);
      } catch (err) {
        console.error(`Kickout failed for ${item.sessionNo}`, err);
      }
    }

    ElMessage.success(`已批量下线 ${candidates.length} 个活跃会话`);
    await loadSessions();
  } catch (e) {
    // cancel
  }
}

onMounted(() => {
  loadSessions();
});
</script>

<template>
  <div class="system-sessions-section">
    <!-- Compact Session Telemetry Strip -->
    <div class="session-telemetry-bar">
      <div class="telemetry-items">
        <div class="telemetry-item">
          <span class="telemetry-icon blue"><el-icon><Monitor /></el-icon></span>
          <span class="telemetry-label">在线会话</span>
          <strong class="telemetry-val">{{ stats.total }} 个</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon rose"><el-icon><Lock /></el-icon></span>
          <span class="telemetry-label">平台特权</span>
          <strong class="telemetry-val">{{ stats.platform }} 个</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon green"><el-icon><User /></el-icon></span>
          <span class="telemetry-label">租户成员</span>
          <strong class="telemetry-val">{{ stats.tenant }} 个</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon purple"><el-icon><CircleCheck /></el-icon></span>
          <span class="telemetry-label">本机登录</span>
          <strong class="telemetry-val">{{ stats.current?.username || "当前终端" }} ({{ stats.current?.loginIp || "本地" }})</strong>
        </div>
      </div>
    </div>

    <!-- Main Table Card -->
    <BaseCard tone="soft">
      <template #header>
        <div class="panel-header-row">
          <div>
            <h3 class="panel-title">在线会话监控与治理</h3>
            <p class="panel-subtitle">
              实时监控 Redis / 数据库中全局签发的访问令牌及双令牌刷新链，支持会话实时审计与特权一键强退。
            </p>
          </div>
          <div class="header-actions">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索账号、IP、终端或会话号"
              :prefix-icon="Search"
              clearable
              style="width: 260px"
            />
            <el-button
              type="danger"
              plain
              :icon="SwitchButton"
              :disabled="selectedSessions.length === 0"
              @click="handleBatchKickout"
            >
              批量强退 ({{ selectedSessions.length }})
            </el-button>
            <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadSessions">
              刷新会话
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="filteredSessions"
        border
        stripe
        style="width: 100%"
        @selection-change="(val: OnlineSession[]) => selectedSessions = val"
      >
        <el-table-column type="selection" width="48" align="center" :selectable="(row: OnlineSession) => !row.isCurrent" />

        <el-table-column label="会话编号" min-width="210">
          <template #default="{ row }">
            <div class="session-no-cell">
              <span class="mono-code">{{ row.sessionNo }}</span>
              <el-button
                link
                type="primary"
                :icon="CopyDocument"
                size="small"
                title="复制会话编号"
                @click="copySessionNo(row.sessionNo)"
              />
            </div>
          </template>
        </el-table-column>

        <el-table-column label="账号 / 昵称" min-width="160">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-primary">{{ row.username }}</div>
              <div v-if="row.nickname" class="user-sub">{{ row.nickname }}</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="主体类型" width="130" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.userType === 'PLATFORM' ? 'danger' : 'info'"
              effect="light"
              size="small"
            >
              {{ row.userType === 'PLATFORM' ? '平台特权' : '租户成员' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="登录 IP" width="150">
          <template #default="{ row }">
            <div class="mono-ip">{{ row.loginIp || "127.0.0.1" }}</div>
          </template>
        </el-table-column>

        <el-table-column label="客户端环境" min-width="220">
          <template #default="{ row }">
            <div class="client-cell">
              <div class="client-os">
                <el-icon v-if="parseUserAgent(row.userAgent).isMobile"><Iphone /></el-icon>
                <el-icon v-else><Monitor /></el-icon>
                <span>{{ parseUserAgent(row.userAgent).os }}</span>
              </div>
              <div class="client-browser">{{ parseUserAgent(row.userAgent).browser }}</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="登录时间" width="170">
          <template #default="{ row }">
            <span class="time-text">{{ row.loginTime?.replace('T', ' ').substring(0, 19) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="最近活跃" width="170">
          <template #default="{ row }">
            <span class="time-text">{{ (row.lastAccessAt || row.loginTime)?.replace('T', ' ').substring(0, 19) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="130" align="center">
          <template #default="{ row }">
            <div class="status-cell">
              <el-tag v-if="row.isCurrent" type="success" effect="dark" size="small">
                当前登录
              </el-tag>
              <el-tag v-else type="primary" effect="plain" size="small">
                活跃会话
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="治理操作" width="110" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              type="danger"
              link
              size="small"
              :icon="SwitchButton"
              @click="handleKickout(row)"
            >
              强退
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </BaseCard>
  </div>
</template>

<style scoped>
.system-sessions-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Compact Session Telemetry Bar */
.session-telemetry-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--sb-border-color, #e2e8f0);
  border-radius: 8px;
  flex-wrap: wrap;
  gap: 12px;

  .telemetry-items {
    display: flex;
    align-items: center;
    gap: 16px;
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
      border-radius: 4px;
      font-size: 13px;

      &.blue { background: #eff6ff; color: #2563eb; }
      &.rose { background: #fff1f2; color: #e11d48; }
      &.green { background: #f0fdf4; color: #16a34a; }
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
}

.stat-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-top: 4px;
}

.panel-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.panel-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.panel-subtitle {
  margin: 4px 0 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.session-no-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.mono-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  background: var(--el-fill-color-light);
  padding: 2px 6px;
  border-radius: 4px;
  color: var(--el-text-color-primary);
}

.mono-ip {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.user-cell {
  display: flex;
  flex-direction: column;
}

.user-primary {
  font-weight: 600;
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.user-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.client-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.client-os {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.client-browser {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.time-text {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.status-cell {
  display: flex;
  justify-content: center;
}
</style>
