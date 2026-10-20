<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox, ElNotification } from "element-plus";
import {
  Delete,
  DocumentDelete,
  Files,
  Filter,
  InfoFilled,
  Link,
  Operation,
  Refresh,
  RefreshRight,
  Search,
  Tickets,
  TrendCharts,
  Briefcase,
  User,
  Setting,
  WarningFilled
} from "@element-plus/icons-vue";
import {
  fetchTrashAppSummaries,
  fetchTrashList,
  purgeTrashItem,
  restoreTrashItem
} from "@/api/modules/trash";
import type { TrashAppSummary, TrashItem } from "@/api/modules/trash";

const loading = ref(false);
const trashList = ref<TrashItem[]>([]);
const appSummaries = ref<TrashAppSummary[]>([]);

// Filter states
const selectedAppCode = ref("ALL");
const selectedModuleName = ref("");
const searchKeyword = ref("");

const appIconMap: Record<string, any> = {
  ALL: Files,
  workflow: Tickets,
  crm: TrendCharts,
  erp: Briefcase,
  iam: User,
  system: Setting
};

// 提取当前列表中所有不同的业务模块类型供下拉动态筛选
const availableModules = computed(() => {
  const set = new Set<string>();
  trashList.value.forEach((item) => {
    if (item.moduleName) set.add(item.moduleName);
  });
  return Array.from(set);
});

// 前端联动筛选
const filteredList = computed(() => {
  return trashList.value.filter((item) => {
    if (selectedAppCode.value !== "ALL" && item.appCode !== selectedAppCode.value) {
      return false;
    }
    if (selectedModuleName.value && item.moduleName !== selectedModuleName.value) {
      return false;
    }
    if (searchKeyword.value.trim()) {
      const q = searchKeyword.value.trim().toLowerCase();
      const matchTitle = item.title?.toLowerCase().includes(q);
      const matchId = String(item.recordId).includes(q);
      const matchTable = item.tableName.toLowerCase().includes(q);
      const matchModule = item.moduleName.toLowerCase().includes(q);
      return matchTitle || matchId || matchTable || matchModule;
    }
    return true;
  });
});

async function loadData() {
  loading.value = true;
  try {
    const [listRes, summaryRes] = await Promise.all([
      fetchTrashList(),
      fetchTrashAppSummaries()
    ]);
    trashList.value = listRes.data || [];
    appSummaries.value = summaryRes.data || [];
  } catch (err: any) {
    ElMessage.error(err.message || "获取回收站数据失败");
  } finally {
    loading.value = false;
  }
}

function handleSelectApp(appCode: string) {
  selectedAppCode.value = appCode;
  selectedModuleName.value = "";
}

function handleResetFilters() {
  selectedAppCode.value = "ALL";
  selectedModuleName.value = "";
  searchKeyword.value = "";
}

async function handleRestore(row: TrashItem) {
  const isCascade = row.hasCascade;
  try {
    const confirmMessage = isCascade
      ? `检测到 [${row.moduleName}] "${row.title}" 具有从属业务关系链（如关联子项、中间授权或节点配置）。系统已默认开启【级联自愈恢复】，还原后主从业务数据将一并无损激活，确认继续吗？`
      : `确认将 [${row.moduleName}] 的记录 "${row.title}" 恢复至正常业务状态吗？`;

    await ElMessageBox.confirm(confirmMessage, "业务数据无损自愈确认", {
      confirmButtonText: "确认完整自愈",
      cancelButtonText: "取消",
      type: "info"
    });

    const res = await restoreTrashItem(row.tableName, row.recordId, true);
    ElNotification({
      title: "自愈恢复成功",
      message: res.data?.message || `记录 "${row.title}" 已成功还原回业务模块！`,
      type: "success",
      duration: 4000
    });
    loadData();
  } catch (err: any) {
    if (err !== "cancel") ElMessage.error(err.message || "还原失败");
  }
}

async function handlePurge(row: TrashItem) {
  try {
    await ElMessageBox.confirm(
      `【不可逆警告】确认彻底从底层物理粉碎 [${row.moduleName}] 记录 "${row.title}" 吗？物理粉碎后该记录及其从属数据将永久抹除，不可找回！`,
      "永久粉碎高危操作",
      {
        confirmButtonText: "彻底粉碎",
        cancelButtonText: "取消",
        type: "warning",
        confirmButtonClass: "el-button--danger"
      }
    );
    await purgeTrashItem(row.tableName, row.recordId);
    ElMessage.success(`记录 "${row.title}" 已从底层数据库彻底物理清除！`);
    loadData();
  } catch (err: any) {
    if (err !== "cancel") ElMessage.error(err.message || "粉碎失败");
  }
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="trash-section" v-loading="loading">
    <!-- Compact Trash Telemetry & Facet Strip -->
    <div class="trash-telemetry-bar">
      <div class="facet-pills">
        <button
          v-for="app in appSummaries"
          :key="app.appCode"
          type="button"
          class="facet-pill-btn"
          :class="{ 'is-active': selectedAppCode === app.appCode }"
          @click="handleSelectApp(app.appCode)"
        >
          <el-icon class="pill-icon">
            <component :is="appIconMap[app.appCode] || Operation" />
          </el-icon>
          <span class="pill-label">{{ app.appName }}</span>
          <span class="pill-count">{{ app.count }}</span>
        </button>
      </div>

      <div class="trash-bar-actions">
        <el-button type="primary" size="small" :icon="Refresh" @click="loadData">刷新回收站</el-button>
      </div>
    </div>

    <!-- 检索与多维筛选操作栏 -->
    <div class="trash-filter-toolbar">
      <div class="filter-left">
        <el-input
          v-model="searchKeyword"
          placeholder="按数据标题、记录主键 ID 或物理表检索..."
          prefix-icon="Search"
          clearable
          style="width: 320px;"
        />

        <el-select
          v-model="selectedModuleName"
          placeholder="所属业务实体类型"
          clearable
          style="width: 200px;"
        >
          <el-option label="全部实体类型" value="" />
          <el-option
            v-for="mod in availableModules"
            :key="mod"
            :label="mod"
            :value="mod"
          />
        </el-select>

        <el-button :icon="Filter" @click="handleResetFilters">重置筛选</el-button>
      </div>

      <div class="filter-right">
        <span class="result-count-label">
          当前筛选展示: <strong>{{ filteredList.length }}</strong> 条记录
        </span>
      </div>
    </div>

    <!-- 数据表格卡片 -->
    <div class="trash-table-card">
      <el-table :data="filteredList" border stripe style="width: 100%">
        <el-table-column prop="appName" label="所属应用" width="145">
          <template #default="{ row }">
            <div class="app-badge-cell">
              <el-icon class="app-icon">
                <component :is="appIconMap[row.appCode] || Operation" />
              </el-icon>
              <span>{{ row.appName || "通用底座" }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="moduleName" label="业务实体" width="150">
          <template #default="{ row }">
            <el-tag size="small" type="primary" effect="plain" class="module-tag">
              {{ row.moduleName }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="title" label="数据标识 / 业务标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="record-title">{{ row.title }}</span>
          </template>
        </el-table-column>

        <el-table-column label="自愈模式" width="130" align="center">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.hasCascade"
              content="该实体包含从属业务链，一键还原时自动级联恢复关联记录"
              placement="top"
            >
              <el-tag size="small" type="success" effect="light" class="cascade-badge">
                <span class="badge-inner">
                  <el-icon class="badge-icon"><Link /></el-icon>
                  <span class="badge-text">级联自愈</span>
                </span>
              </el-tag>
            </el-tooltip>
            <el-tag v-else size="small" type="info" effect="plain" class="standalone-badge">
              <span class="badge-inner">
                <span class="badge-text">单表自愈</span>
              </span>
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="tableName" label="底层物理表" width="180">
          <template #default="{ row }">
            <span class="table-name-mono">{{ row.tableName }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="recordId" label="主键 ID" width="95" align="center">
          <template #default="{ row }">
            <span class="record-id-mono">#{{ row.recordId }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="deletedAt" label="删除时间" width="175">
          <template #default="{ row }">
            <span class="deleted-at-mono">
              {{ row.deletedAt ? row.deletedAt.replace('T', ' ').substring(0, 19) : '-' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="自愈操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              :icon="RefreshRight"
              @click="handleRestore(row)"
            >
              一键还原
            </el-button>
            <el-button
              type="danger"
              size="small"
              text
              :icon="Delete"
              @click="handlePurge(row)"
            >
              彻底粉碎
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="filteredList.length === 0 && !loading" class="empty-state">
        <el-empty description="当前筛选条件下没有待恢复的软删除数据" />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.trash-section {
  padding: 0;
}

/* Sleek Trash Telemetry & Facet Pill Strip */
.trash-telemetry-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--sb-border-color, #e2e8f0);
  border-radius: 8px;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;

  .facet-pills {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .facet-pill-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 5px 12px;
    border-radius: 6px;
    border: 1px solid #e2e8f0;
    background: #f8fafc;
    color: #475569;
    font-size: 13px;
    cursor: pointer;
    transition: all 0.2s ease;

    .pill-icon {
      font-size: 14px;
    }

    .pill-label {
      font-weight: 500;
    }

    .pill-count {
      font-size: 11px;
      padding: 1px 6px;
      border-radius: 10px;
      background: #e2e8f0;
      color: #334155;
      font-weight: 600;
    }

    &:hover {
      border-color: #3b82f6;
      background: #eff6ff;
      color: #2563eb;

      .pill-count {
        background: #dbeafe;
        color: #1d4ed8;
      }
    }

    &.is-active {
      background: #2563eb;
      border-color: #2563eb;
      color: #ffffff;

      .pill-count {
        background: rgba(255, 255, 255, 0.25);
        color: #ffffff;
      }
    }
  }

  .trash-bar-actions {
    display: inline-flex;
    align-items: center;
  }
}

/* 检索过滤工具条 */
.trash-filter-toolbar {
  background: #ffffff;
  border: 1px solid var(--sb-border-color);
  border-radius: 9px;
  padding: 12px 16px;
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  box-shadow: var(--sb-shadow-xs);
}

.filter-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-right {
  font-size: 13px;
  color: #64748b;

  strong {
    color: #2563eb;
    font-weight: 700;
  }
}

.app-badge-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #334155;
  font-weight: 500;

  .app-icon {
    font-size: 15px;
    color: #64748b;
  }
}

.record-title {
  font-weight: 600;
  color: #0f172a;
  font-size: 13.5px;
  line-height: 1.4;
}

:deep(.el-tag) {
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  vertical-align: middle !important;
  height: 24px !important;
  line-height: 1 !important;
  box-sizing: border-box !important;
}

:deep(.el-tag__content) {
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  line-height: 1 !important;
  white-space: nowrap !important;
  height: 100% !important;
}

.badge-inner {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  vertical-align: middle;
  gap: 4px;
  line-height: 1;
  white-space: nowrap;

  .badge-icon {
    font-size: 12px;
    line-height: 1;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    margin: 0;
  }

  .badge-text {
    font-size: 11.5px;
    line-height: 1;
    display: inline-block;
  }
}

.cascade-badge {
  cursor: help;
  padding: 0 8px !important;
}

.standalone-badge {
  padding: 0 8px !important;
}

.table-name-mono,
.record-id-mono,
.deleted-at-mono {
  font-family: monospace;
  font-size: 12px;
  color: #64748b;
}

.record-id-mono {
  color: #475569;
  font-weight: 500;
}

.trash-table-card {
  background: #ffffff;
  border: 1px solid var(--sb-border-color);
  border-radius: 10px;
  padding: 16px;
  box-shadow: var(--sb-shadow-xs);
}

.empty-state {
  padding: 30px 0;
}
</style>
