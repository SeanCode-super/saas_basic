<script setup lang="ts">
import { computed, ref, watch } from "vue";
import {
  Collection,
  Setting,
  Plus,
  Refresh,
  Search,
  CopyDocument,
  MoreFilled,
  Edit,
  SwitchButton,
  Delete
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { SystemDictItemRow, SystemDictTypeRow } from "@/api/modules/system";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";

const props = withDefaults(
  defineProps<{
    localePolicy: {
      defaultLocale: string;
      fallbackLocale: string;
      dictionaryCacheMinutes: number;
      autoPublishI18n: boolean;
    };
    loading?: boolean;
    dictTypeColumns: ProTableColumn[];
    dictTypes: SystemDictTypeRow[];
    dictItemColumns: ProTableColumn[];
    dictItems: SystemDictItemRow[];
    activeDictType?: SystemDictTypeRow | null;
    savePolicy: (message: string) => void;
    selectDictType: (row: SystemDictTypeRow) => void;
    openCreateDictType: () => void;
    openCreateDictItem: () => void;
    editDictType: (row: SystemDictTypeRow) => void;
    editDictItem: (row: SystemDictItemRow) => void;
    toggleDictTypeStatus: (row: SystemDictTypeRow) => void | Promise<void>;
    toggleDictItemStatus: (row: SystemDictItemRow) => void | Promise<void>;
    deleteDictType?: (row: SystemDictTypeRow) => void | Promise<void>;
    deleteDictItem?: (row: SystemDictItemRow) => void | Promise<void>;
    refreshCache?: () => void | Promise<void>;
  }>(),
  {
    loading: false
  }
);

const activeTab = ref("dictionary");
const typeKeyword = ref("");
const itemKeyword = ref("");
const refreshing = ref(false);

const currentPage = ref(1);
const pageSize = ref(10);

const filteredDictTypes = computed(() => {
  if (!typeKeyword.value.trim()) return props.dictTypes;
  const kw = typeKeyword.value.trim().toLowerCase();
  return props.dictTypes.filter(
    (item) => item.dictName.toLowerCase().includes(kw) || item.dictCode.toLowerCase().includes(kw)
  );
});

const filteredDictItems = computed(() => {
  if (!itemKeyword.value.trim()) return props.dictItems;
  const kw = itemKeyword.value.trim().toLowerCase();
  return props.dictItems.filter(
    (item) => item.itemLabel.toLowerCase().includes(kw) || item.itemValue.toLowerCase().includes(kw)
  );
});

const paginatedDictItems = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredDictItems.value.slice(start, start + pageSize.value);
});

watch([itemKeyword, () => props.activeDictType?.id], () => {
  currentPage.value = 1;
});

async function handleRefreshCache() {
  if (!props.refreshCache) return;
  refreshing.value = true;
  try {
    await props.refreshCache();
  } finally {
    refreshing.value = false;
  }
}

function copyCode(code: string) {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(code);
    ElMessage.success(`字典编码【${code}】已复制到剪贴板`);
  }
}

function confirmDeleteType(item: SystemDictTypeRow) {
  if (!props.deleteDictType) return;
  ElMessageBox.confirm(
    `确定删除字典分类【${item.dictName} (${item.dictCode})】及其下所有字典项吗？此操作无法撤销。`,
    "删除确认",
    {
      confirmButtonText: "确定删除",
      cancelButtonText: "取消",
      confirmButtonClass: "el-button--danger",
      type: "warning"
    }
  )
    .then(() => {
      props.deleteDictType!(item);
    })
    .catch(() => {});
}

function resolveTagType(colorOrTag?: string): "primary" | "success" | "warning" | "danger" | "info" {
  if (!colorOrTag) return "info";
  const val = colorOrTag.toLowerCase();
  if (["primary", "success", "warning", "danger", "info"].includes(val)) {
    return val as "primary" | "success" | "warning" | "danger" | "info";
  }
  return "info";
}

function resolveColorHex(color?: string): string {
  if (!color) return "#94a3b8";
  if (color.startsWith("#") || color.startsWith("rgb")) return color;
  const presets: Record<string, string> = {
    primary: "#3b82f6",
    success: "#10b981",
    warning: "#f59e0b",
    danger: "#ef4444",
    info: "#94a3b8"
  };
  return presets[color.toLowerCase()] || "#94a3b8";
}

function resolveTagCustomStyle(color?: string) {
  if (color && (color.startsWith("#") || color.startsWith("rgb"))) {
    return {
      backgroundColor: color,
      borderColor: color,
      color: "#ffffff"
    };
  }
  return undefined;
}
</script>

<template>
  <div class="system-locale-container">
    <el-tabs v-model="activeTab" type="border-card" class="locale-tabs">
      <!-- Tab 1: Left Directory List + Right Detail Workspace -->
      <el-tab-pane name="dictionary">
        <template #label>
          <span class="tab-label">
            <el-icon><Collection /></el-icon>
            <span>数据字典治理工作台</span>
            <el-badge :value="dictTypes.length" type="primary" class="tab-badge" />
          </span>
        </template>

        <div class="dict-split-layout">
          <!-- Left Master Navigation: Directory Cards (290px) -->
          <div class="dict-sidebar">
            <div class="sidebar-header">
              <div class="sidebar-title">
                <strong>字典分类目录</strong>
                <span class="sidebar-count">{{ filteredDictTypes.length }}/{{ dictTypes.length }}</span>
              </div>
              <div class="sidebar-actions">
                <el-tooltip content="刷新字典全局缓存" placement="top">
                  <el-button
                    v-if="refreshCache"
                    size="small"
                    circle
                    :icon="Refresh"
                    :loading="refreshing"
                    @click="handleRefreshCache"
                  />
                </el-tooltip>
                <el-tooltip content="新增字典分类" placement="top">
                  <el-button
                    type="primary"
                    size="small"
                    circle
                    :icon="Plus"
                    @click="openCreateDictType"
                  />
                </el-tooltip>
              </div>
            </div>

            <div class="sidebar-search">
              <el-input
                v-model="typeKeyword"
                placeholder="搜索分类名称或编码..."
                size="small"
                clearable
                :prefix-icon="Search"
              />
            </div>

            <div class="sidebar-list">
              <div
                v-for="item in filteredDictTypes"
                :key="item.id"
                class="dict-type-card"
                :class="{ 'is-active': activeDictType?.id === item.id }"
                @click="selectDictType(item)"
              >
                <div class="dict-type-card__main">
                  <div class="dict-type-card__header">
                    <span class="dict-type-card__name">{{ item.dictName }}</span>
                    <div class="dict-type-card__badges">
                      <span class="dict-item-count-chip">{{ item.itemCount ?? 0 }} 项</span>
                      <span
                        class="dict-status-dot"
                        :class="item.status === 'ENABLED' ? 'is-enabled' : 'is-disabled'"
                        :title="item.status === 'ENABLED' ? '启用中' : '已停用'"
                      />
                    </div>
                  </div>
                  <div class="dict-type-card__footer">
                    <code class="dict-type-card__code">{{ item.dictCode }}</code>
                    <div class="dict-type-card__actions" @click.stop>
                      <el-dropdown trigger="click" placement="bottom-end">
                        <button class="card-more-btn" title="更多操作">
                          <el-icon><MoreFilled /></el-icon>
                        </button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item :icon="Edit" @click="editDictType(item)">编辑分类</el-dropdown-item>
                            <el-dropdown-item
                              :icon="SwitchButton"
                              @click="toggleDictTypeStatus(item)"
                            >
                              {{ item.status === "ENABLED" ? "停用分类" : "启用分类" }}
                            </el-dropdown-item>
                            <el-dropdown-item
                              v-if="deleteDictType"
                              divided
                              style="color: var(--el-color-danger);"
                              :icon="Delete"
                              @click="confirmDeleteType(item)"
                            >
                              删除分类
                            </el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="filteredDictTypes.length === 0" class="sidebar-empty">
                <el-empty description="未找到匹配分类" :image-size="60" />
              </div>
            </div>
          </div>

          <!-- Right Detail: Governance Workbench -->
          <div class="dict-workspace">
            <div v-if="activeDictType" class="dict-detail-container">
              <!-- Hero Meta Card -->
              <div class="dict-hero-card">
                <div class="dict-hero-card__left">
                  <div class="dict-hero-title-row">
                    <h3 class="dict-hero-title">{{ activeDictType.dictName }}</h3>
                    <div
                      class="dict-hero-code-badge"
                      title="点击快速复制字典编码"
                      @click="copyCode(activeDictType.dictCode)"
                    >
                      <code>{{ activeDictType.dictCode }}</code>
                      <el-icon class="copy-icon"><CopyDocument /></el-icon>
                    </div>
                    <el-tag size="small" effect="plain" type="info">
                      {{ activeDictType.dictScope === "PLATFORM" ? "平台全局" : "租户级" }}
                    </el-tag>
                    <BaseStatusTag :status="activeDictType.status" />
                  </div>
                  <div class="dict-hero-desc">
                    {{ activeDictType.remark || "系统内置通用数据字典，支持前端组件级缓存加速与国际化联动。" }}
                  </div>
                </div>
                <div class="dict-hero-card__right">
                  <el-button type="primary" :icon="Plus" @click="openCreateDictItem">
                    新增字典项
                  </el-button>
                  <el-button :icon="Edit" @click="editDictType(activeDictType)">
                    编辑分类档案
                  </el-button>
                </div>
              </div>

              <!-- Toolbar -->
              <div class="dict-workspace-toolbar">
                <div class="toolbar-left">
                  <el-input
                    v-model="itemKeyword"
                    placeholder="搜索字典项标签或键值..."
                    size="default"
                    clearable
                    :prefix-icon="Search"
                    style="width: 280px;"
                  />
                  <span class="items-count-text">共 {{ filteredDictItems.length }} 个明细项</span>
                </div>
              </div>

              <!-- Detail Table & Pagination -->
              <div class="dict-table-container">
                <el-table
                  v-loading="loading"
                  :data="paginatedDictItems"
                  row-key="id"
                  size="default"
                  stripe
                  class="dict-item-table"
                >
                  <el-table-column prop="itemValue" label="字典键值" min-width="120">
                    <template #default="{ row }">
                      <code class="dict-value-code">{{ row.itemValue }}</code>
                    </template>
                  </el-table-column>

                  <el-table-column prop="itemLabel" label="标签名称" min-width="130">
                    <template #default="{ row }">
                      <span class="dict-label-text">{{ row.itemLabel }}</span>
                    </template>
                  </el-table-column>

                  <el-table-column label="样式回显" min-width="120" align="center">
                    <template #default="{ row }">
                      <el-tag
                        :type="resolveTagType(row.itemColor || row.itemTag)"
                        :style="resolveTagCustomStyle(row.itemColor)"
                        size="small"
                        round
                      >
                        {{ row.itemLabel }}
                      </el-tag>
                    </template>
                  </el-table-column>

                  <el-table-column prop="itemColor" label="颜色" min-width="110" align="center">
                    <template #default="{ row }">
                      <div v-if="row.itemColor" class="dict-color-cell">
                        <span class="dict-color-dot" :style="{ backgroundColor: resolveColorHex(row.itemColor) }" />
                        <span class="dict-color-text">{{ row.itemColor }}</span>
                      </div>
                      <span v-else class="text-placeholder">-</span>
                    </template>
                  </el-table-column>

                  <el-table-column prop="itemTag" label="样式标签" min-width="100" align="center">
                    <template #default="{ row }">
                      <el-tag v-if="row.itemTag" size="small" effect="plain" type="info">
                        {{ row.itemTag }}
                      </el-tag>
                      <span v-else class="text-placeholder">-</span>
                    </template>
                  </el-table-column>

                  <el-table-column prop="sortNo" label="排序" width="75" align="center" />

                  <el-table-column prop="defaultItem" label="默认" width="85" align="center">
                    <template #default="{ row }">
                      <el-tag :type="row.defaultItem ? 'success' : 'info'" size="small" effect="light">
                        {{ row.defaultItem ? "默认项" : "普通" }}
                      </el-tag>
                    </template>
                  </el-table-column>

                  <el-table-column prop="status" label="状态" width="85" align="center">
                    <template #default="{ row }">
                      <BaseStatusTag :status="row.status" />
                    </template>
                  </el-table-column>

                  <el-table-column label="操作" width="160" align="center" fixed="right">
                    <template #default="{ row }">
                      <div class="table-actions-nowrap">
                        <el-button link type="primary" size="small" @click="editDictItem(row)">编辑</el-button>
                        <el-button
                          link
                          :type="row.status === 'ENABLED' ? 'warning' : 'success'"
                          size="small"
                          @click="toggleDictItemStatus(row)"
                        >
                          {{ row.status === "ENABLED" ? "停用" : "启用" }}
                        </el-button>
                        <el-popconfirm
                          v-if="deleteDictItem"
                          title="确定删除该字典项吗？"
                          confirm-button-text="删除"
                          cancel-button-text="取消"
                          confirm-button-type="danger"
                          @confirm="deleteDictItem(row)"
                        >
                          <template #reference>
                            <el-button link type="danger" size="small">删除</el-button>
                          </template>
                        </el-popconfirm>
                      </div>
                    </template>
                  </el-table-column>

                  <template #empty>
                    <el-empty description="当前分类下暂无字典项" :image-size="80" />
                  </template>
                </el-table>

                <!-- 企业级标准分页器 -->
                <div class="dict-pagination-bar">
                  <span class="pagination-info">共 {{ filteredDictItems.length }} 条记录</span>
                  <el-pagination
                    v-model:current-page="currentPage"
                    v-model:page-size="pageSize"
                    :page-sizes="[10, 20, 50, 100]"
                    :total="filteredDictItems.length"
                    layout="total, sizes, prev, pager, next, jumper"
                    background
                    size="small"
                  />
                </div>
              </div>
            </div>

            <div v-else class="dict-empty-guide">
              <el-empty description="请在左侧目录中点击选择一个字典分类以维护明细" :image-size="100" />
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 2: Internationalization & Cache Policies -->
      <el-tab-pane name="policy">
        <template #label>
          <span class="tab-label">
            <el-icon><Setting /></el-icon>
            <span>语言与缓存策略</span>
          </span>
        </template>

        <div class="policy-panel-wrap">
          <div class="policy-card">
            <div class="policy-card__header">
              <div>
                <h4>多语言与区域化基线</h4>
                <p>配置平台全局支持的语言、兜底语言以及国际化资源的多级缓存发布行为。</p>
              </div>
              <el-button type="primary" :icon="Refresh" @click="savePolicy('语言与字典策略已保存，仅支持中文和英文')">
                保存配置
              </el-button>
            </div>

            <el-form label-position="top" class="policy-form-grid">
              <el-form-item label="系统默认语言 (Default Locale)">
                <el-select v-model="localePolicy.defaultLocale" style="width: 100%">
                  <el-option label="中文（简体）- zh-CN" value="zh-CN" />
                  <el-option label="English (United States) - en-US" value="en-US" />
                </el-select>
              </el-form-item>

              <el-form-item label="兜底回退语言 (Fallback Locale)">
                <el-select v-model="localePolicy.fallbackLocale" style="width: 100%">
                  <el-option label="中文（简体）- zh-CN" value="zh-CN" />
                  <el-option label="English (United States) - en-US" value="en-US" />
                </el-select>
              </el-form-item>

              <el-form-item label="字典缓存时效（分钟）">
                <el-input-number
                  v-model="localePolicy.dictionaryCacheMinutes"
                  :min="1"
                  :max="1440"
                  style="width: 100%"
                />
              </el-form-item>

              <el-form-item label="发布联动缓存刷新">
                <div class="switch-field">
                  <el-switch v-model="localePolicy.autoPublishI18n" active-text="更新国际化资源后自动刷新各节点缓存" />
                </div>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.system-locale-container {
  width: 100%;
}

.locale-tabs {
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--sb-border-color, #e2e8f0);
  background: #fff;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

.tab-badge {
  margin-left: 4px;
}

/* Master-Detail Architecture */
.dict-split-layout {
  display: grid;
  grid-template-columns: 290px 1fr;
  gap: 0;
  min-height: 640px;
  border-top: 1px solid #f1f5f9;
}

/* Left Sidebar (290px) */
.dict-sidebar {
  display: flex;
  flex-direction: column;
  background: #f8fafc;
  border-right: 1px solid #e2e8f0;

  .sidebar-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 14px;
    border-bottom: 1px solid #e2e8f0;
    background: #fff;

    .sidebar-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13.5px;
      color: #1e293b;

      .sidebar-count {
        font-size: 11px;
        color: #64748b;
        background: #f1f5f9;
        padding: 1px 6px;
        border-radius: 10px;
        font-weight: 600;
      }
    }

    .sidebar-actions {
      display: flex;
      align-items: center;
      gap: 6px;
    }
  }

  .sidebar-search {
    padding: 10px 12px;
    background: #fff;
    border-bottom: 1px solid #e2e8f0;
  }

  .sidebar-list {
    flex: 1;
    overflow-y: auto;
    padding: 10px;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .sidebar-empty {
    padding: 40px 0;
  }
}

/* Dict Type Card (Zero-Wrapping Clean Design) */
.dict-type-card {
  position: relative;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fff;
  padding: 10px 12px;
  cursor: pointer;
  transition: all 0.18s ease;

  &:hover {
    border-color: #cbd5e1;
    background: #fdfdfe;
    transform: translateY(-1px);
    box-shadow: 0 2px 4px rgb(0 0 0 / 0.03);

    .card-more-btn {
      opacity: 1;
    }
  }

  &.is-active {
    background: #eff6ff;
    border-color: #93c5fd;
    border-left: 4px solid var(--el-color-primary, #0284c7);
    box-shadow: 0 1px 3px rgb(2 132 199 / 0.08);

    .dict-type-card__name {
      color: var(--el-color-primary, #0284c7);
    }

    .card-more-btn {
      opacity: 1;
    }
  }

  &__main {
    display: flex;
    flex-direction: column;
    gap: 5px;
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__name {
    font-size: 13.5px;
    font-weight: 650;
    color: #1e293b;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 170px;
  }

  &__badges {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__code {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 11.5px;
    color: #64748b;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 200px;
  }

  &__actions {
    display: flex;
    align-items: center;
  }
}

.dict-item-count-chip {
  font-size: 11px;
  color: #475569;
  background: #f1f5f9;
  padding: 1px 6px;
  border-radius: 4px;
}

.dict-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;

  &.is-enabled {
    background: #10b981;
    box-shadow: 0 0 0 2px rgb(16 185 129 / 0.2);
  }

  &.is-disabled {
    background: #cbd5e1;
  }
}

.card-more-btn {
  border: none;
  background: transparent;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  cursor: pointer;
  opacity: 0.3;
  transition: all 0.15s ease;

  &:hover {
    background: #e2e8f0;
    color: #0f172a;
  }
}

/* Right Detail Workspace */
.dict-workspace {
  background: #fff;
  padding: 18px 24px;
  overflow-y: auto;
}

.dict-detail-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Hero Meta Card */
.dict-hero-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 16px 20px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;

  &__left {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  &__right {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }
}

.dict-hero-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.dict-hero-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
}

.dict-hero-code-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #e0f2fe;
  color: #0369a1;
  padding: 2px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;

  code {
    font-size: 12.5px;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-weight: 600;
  }

  .copy-icon {
    font-size: 12px;
    opacity: 0.7;
  }

  &:hover {
    background: #bae6fd;

    .copy-icon {
      opacity: 1;
    }
  }
}

.dict-hero-desc {
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}

/* Workspace Toolbar */
.dict-workspace-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .items-count-text {
    font-size: 12.5px;
    color: #64748b;
  }
}

/* Table Container & Direct el-table */
.dict-table-container {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.dict-item-table {
  width: 100%;
}

.dict-value-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12.5px;
  font-weight: 600;
  color: #0369a1;
  background: #f0f9ff;
  padding: 2px 6px;
  border-radius: 4px;
}

.dict-label-text {
  font-weight: 550;
  color: #1e293b;
}

/* Pagination Bar */
.dict-pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  border-top: 1px solid #f1f5f9;

  .pagination-info {
    font-size: 13px;
    color: #64748b;
  }
}

/* Color indicator & tag styles */
.dict-color-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;

  .dict-color-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    display: inline-block;
    border: 1px solid rgb(0 0 0 / 0.1);
  }

  .dict-color-text {
    font-size: 12px;
    color: #475569;
    font-family: ui-monospace, SFMono-Regular, monospace;
  }
}

.text-placeholder {
  color: #cbd5e1;
}

.table-actions-nowrap {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.dict-empty-guide {
  min-height: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Policy Panel */
.policy-panel-wrap {
  padding: 16px;
  max-width: 860px;
}

.policy-card {
  padding: 24px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;

  &__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    margin-bottom: 24px;
    padding-bottom: 16px;
    border-bottom: 1px solid #e2e8f0;

    h4 {
      margin: 0 0 6px;
      font-size: 16px;
      color: #0f172a;
    }

    p {
      margin: 0;
      font-size: 13px;
      color: #64748b;
    }
  }
}

.policy-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.switch-field {
  padding-top: 6px;
}

@media (max-width: 1024px) {
  .dict-split-layout {
    grid-template-columns: 1fr;
  }

  .dict-sidebar {
    border-right: none;
    border-bottom: 1px solid #e2e8f0;
  }

  .policy-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
