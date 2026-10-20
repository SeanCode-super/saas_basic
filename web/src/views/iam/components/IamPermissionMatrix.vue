<script setup lang="ts">
import { computed, ref } from "vue";
import { Edit, Filter, Search } from "@element-plus/icons-vue";
import type {
  IamDataPermissionRuleRow,
  IamMenuPermissionRow,
  IamMenuRow,
  IamRoleRow
} from "@/api/modules/iam";
import BaseCard from "@/components/base/BaseCard.vue";

const props = defineProps<{
  roles: IamRoleRow[];
  menus: IamMenuRow[];
  menuPermissions: IamMenuPermissionRow[];
  dataPermissionRules: IamDataPermissionRuleRow[];
  loading?: boolean;
}>();

const emit = defineEmits<{
  (e: "openRoleConfig", role: IamRoleRow): void;
}>();

const searchKeyword = ref("");
const selectedRoleFilter = ref<number[]>([]);
const selectedModuleFilter = ref<string[]>([]);

// Filter enabled roles
const activeRoles = computed(() => {
  const list = props.roles.filter((r) => r.status === "ENABLED");
  if (selectedRoleFilter.value.length === 0) {
    return list;
  }
  const set = new Set(selectedRoleFilter.value);
  return list.filter((r) => set.has(r.id));
});

// Menus & Actions
const visibleMenus = computed(() => props.menus.filter((m) => m.menuType !== "BUTTON"));
const buttonMenus = computed(() => props.menus.filter((m) => m.menuType === "BUTTON"));

const actionsByMenuId = computed(() => {
  const map = new Map<number, IamMenuRow[]>();
  for (const btn of buttonMenus.value) {
    if (btn.status === "ENABLED") {
      const list = map.get(btn.parentId) ?? [];
      list.push(btn);
      map.set(btn.parentId, list);
    }
  }
  return map;
});

interface MatrixRowItem {
  id: number;
  moduleName: string;
  pageName: string;
  pageCode: string;
  actions: IamMenuRow[];
}

const allAvailableModules = computed<string[]>(() => {
  const set = new Set<string>();
  const roots = visibleMenus.value.filter((m) => m.parentId === 0 || !m.parentId);
  for (const root of roots) {
    if (root.menuType === "DIRECTORY") {
      set.add(root.menuName);
    } else {
      set.add("独立应用");
    }
  }
  return Array.from(set);
});

const matrixRows = computed<MatrixRowItem[]>(() => {
  const kw = searchKeyword.value.trim().toLowerCase();
  const selectedMods = selectedModuleFilter.value;
  const roots = visibleMenus.value.filter((m) => m.parentId === 0 || !m.parentId);
  const rows: MatrixRowItem[] = [];

  for (const root of roots) {
    const modName = root.menuType === "DIRECTORY" ? root.menuName : "独立应用";
    if (selectedMods.length > 0 && !selectedMods.includes(modName)) {
      continue;
    }

    const children = visibleMenus.value.filter((m) => m.parentId === root.id);
    if (root.menuType === "DIRECTORY") {
      for (const child of children) {
        const actions = actionsByMenuId.value.get(child.id) ?? [];
        const match =
          !kw ||
          root.menuName.toLowerCase().includes(kw) ||
          child.menuName.toLowerCase().includes(kw) ||
          child.menuCode.toLowerCase().includes(kw) ||
          actions.some((a) => a.menuName.toLowerCase().includes(kw));
        if (match) {
          rows.push({
            id: child.id,
            moduleName: root.menuName,
            pageName: child.menuName,
            pageCode: child.menuCode,
            actions
          });
        }
      }
    } else {
      const actions = actionsByMenuId.value.get(root.id) ?? [];
      const match =
        !kw ||
        root.menuName.toLowerCase().includes(kw) ||
        root.menuCode.toLowerCase().includes(kw) ||
        actions.some((a) => a.menuName.toLowerCase().includes(kw));
      if (match) {
        rows.push({
          id: root.id,
          moduleName: "独立应用",
          pageName: root.menuName,
          pageCode: root.menuCode,
          actions
        });
      }
    }
  }
  return rows;
});

// Cache grants per role
const roleGrantsMap = computed(() => {
  const map = new Map<number, Map<number, { pageGranted: boolean; actionCodes: Set<string> }>>();
  for (const role of props.roles) {
    const roleGrants = props.menuPermissions.filter(
      (p) => p.subjectType === "ROLE" && p.subjectValue === String(role.id) && p.status === "ENABLED"
    );
    const pageMap = new Map<number, { pageGranted: boolean; actionCodes: Set<string> }>();
    for (const g of roleGrants) {
      let codes: string[] = [];
      if (g.buttonCodesJson) {
        try {
          const parsed = JSON.parse(g.buttonCodesJson);
          if (Array.isArray(parsed)) codes = parsed;
        } catch {}
      }
      pageMap.set(g.menuId, { pageGranted: true, actionCodes: new Set(codes) });
    }
    map.set(role.id, pageMap);
  }
  return map;
});

function getRoleAccessCell(roleId: number, pageId: number, pageActions: IamMenuRow[]) {
  const rolePages = roleGrantsMap.value.get(roleId);
  if (!rolePages) return { granted: false, label: "-", tooltip: "无访问权限" };

  const access = rolePages.get(pageId);
  if (!access || !access.pageGranted) {
    return { granted: false, label: "-", tooltip: "未授权此页面" };
  }

  if (pageActions.length === 0) {
    return {
      granted: true,
      type: "view",
      label: "可访问",
      tooltip: "拥有页面访问权限（无细粒度按钮）"
    };
  }

  const grantedActionNames: string[] = [];
  for (const act of pageActions) {
    if (access.actionCodes.has(act.menuCode)) {
      grantedActionNames.push(act.menuName);
    }
  }

  if (grantedActionNames.length === 0) {
    return {
      granted: true,
      type: "view",
      label: "仅浏览",
      tooltip: "仅可查阅页面，未分配新增/编辑/删除等写入操作"
    };
  }

  if (grantedActionNames.length === pageActions.length) {
    return {
      granted: true,
      type: "full",
      label: "全部操作",
      tooltip: `拥有全部 ${pageActions.length} 项操作：${grantedActionNames.join("、")}`
    };
  }

  return {
    granted: true,
    type: "partial",
    label: `${grantedActionNames.length}/${pageActions.length} 操作`,
    tooltip: `已授权操作：${grantedActionNames.join("、")}`
  };
}

function getRoleDataScopeBadge(roleId: number) {
  const rule = props.dataPermissionRules.find(
    (r) => r.subjectType === "ROLE" && r.subjectValue === String(roleId) && r.status === "ENABLED"
  );
  if (!rule) return "默认范围";
  switch (rule.scopeType) {
    case "ALL":
      return "全域 (ALL)";
    case "DEPT_AND_CHILDREN":
      return "部门树穿透";
    case "DEPT_ONLY":
      return "仅本部门";
    case "SELF_AND_SUBORDINATES":
      return "本人及下属";
    case "SELF":
      return "仅本人";
    case "CUSTOM":
      return "自定义范围";
    default:
      return rule.scopeType;
  }
}
</script>

<template>
  <BaseCard
    title="全景权限矩阵 (Permission Matrix)"
    subtitle="直观对比各个角色在企业全模块的功能页面与关键操作的授权覆盖率，拒绝权限盲区与越权风险。"
    class="iam-permission-matrix-card"
  >
    <!-- Filter bar -->
    <div class="matrix-toolbar">
      <div class="filter-left">
        <el-input
          v-model="searchKeyword"
          placeholder="按功能模块或操作名称过滤..."
          clearable
          :prefix-icon="Search"
          class="matrix-search"
        />
        <el-select
          v-model="selectedModuleFilter"
          multiple
          collapse-tags
          collapse-tags-tooltip
          placeholder="筛选所属模块 (默认全部)"
          clearable
          class="role-select"
        >
          <el-option
            v-for="m in allAvailableModules"
            :key="m"
            :label="m"
            :value="m"
          />
        </el-select>
        <el-select
          v-model="selectedRoleFilter"
          multiple
          collapse-tags
          collapse-tags-tooltip
          placeholder="对比指定角色 (默认全部)"
          clearable
          class="role-select"
        >
          <el-option
            v-for="r in roles.filter(r => r.status === 'ENABLED')"
            :key="r.id"
            :label="r.roleName"
            :value="r.id"
          />
        </el-select>
      </div>

      <div class="stats-pills">
        <span class="stat-tag">对比 <strong>{{ activeRoles.length }}</strong> 个有效角色</span>
        <span class="stat-tag">覆盖 <strong>{{ matrixRows.length }}</strong> 个功能页面</span>
      </div>
    </div>

    <!-- Panoramic Table -->
    <div class="matrix-table-container">
      <table class="matrix-table">
        <thead>
          <tr>
            <th class="col-sticky-module">所属模块</th>
            <th class="col-sticky-page">功能页面 / 操作</th>
            <th v-for="role in activeRoles" :key="role.id" class="col-role-header">
              <div class="role-cell-header">
                <div class="role-name-text">{{ role.roleName }}</div>
                <div class="role-scope-chip">{{ getRoleDataScopeBadge(role.id) }}</div>
                <el-button
                  link
                  type="primary"
                  size="small"
                  :icon="Edit"
                  class="edit-config-link"
                  @click="emit('openRoleConfig', role)"
                >
                  配置
                </el-button>
              </div>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in matrixRows" :key="row.id">
            <td class="col-sticky-module">
              <span class="module-badge">{{ row.moduleName }}</span>
            </td>
            <td class="col-sticky-page">
              <div class="page-title-wrap">
                <span class="page-title">{{ row.pageName }}</span>
                <span class="page-code-sub">{{ row.pageCode }}</span>
              </div>
              <div v-if="row.actions.length > 0" class="mini-actions-row">
                <span v-for="a in row.actions" :key="a.id" class="mini-act-badge">
                  {{ a.menuName }}
                </span>
              </div>
            </td>
            <td v-for="role in activeRoles" :key="role.id" class="col-cell-access">
              <template v-for="cell in [getRoleAccessCell(role.id, row.id, row.actions)]" :key="cell.label">
                <el-tooltip :content="cell.tooltip" placement="top" :show-after="200">
                  <div
                    class="access-chip"
                    :class="{
                      'chip-full': cell.type === 'full',
                      'chip-partial': cell.type === 'partial',
                      'chip-view': cell.type === 'view',
                      'chip-none': !cell.granted
                    }"
                  >
                    <span v-if="cell.granted" class="chip-dot" />
                    <span>{{ cell.label }}</span>
                  </div>
                </el-tooltip>
              </template>
            </td>
          </tr>
          <tr v-if="matrixRows.length === 0">
            <td :colspan="activeRoles.length + 2" class="empty-cell">
              <el-empty description="未找到匹配的功能模块" :image-size="60" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </BaseCard>
</template>

<style scoped lang="scss">
.iam-permission-matrix-card {
  margin-top: 16px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}

.matrix-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 16px;
  flex-wrap: wrap;

  .filter-left {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
    min-width: 300px;

    .matrix-search {
      max-width: 300px;
    }

    .role-select {
      min-width: 240px;
    }
  }

  .stats-pills {
    display: flex;
    gap: 8px;

    .stat-tag {
      font-size: 12px;
      color: #64748b;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      padding: 4px 10px;
      border-radius: 6px;

      strong {
        color: #2563eb;
      }
    }
  }
}

.matrix-table-container {
  overflow-x: auto;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
}

.matrix-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  text-align: left;

  thead th {
    background: #f8fafc;
    color: #475569;
    font-size: 13px;
    font-weight: 600;
    padding: 12px 16px;
    border-bottom: 1px solid #e2e8f0;
    white-space: nowrap;
    position: sticky;
    top: 0;
    z-index: 2;
  }

  tbody td {
    padding: 12px 16px;
    border-bottom: 1px solid #f1f5f9;
    font-size: 13px;
    vertical-align: middle;
  }

  tbody tr:hover td {
    background: #f8fafc;
  }

  /* Sticky columns */
  .col-sticky-module {
    position: sticky;
    left: 0;
    background: #fff;
    z-index: 1;
    min-width: 140px;
    max-width: 160px;
    border-right: 1px solid #f1f5f9;
  }

  thead th.col-sticky-module {
    background: #f8fafc;
    z-index: 3;
  }

  .col-sticky-page {
    min-width: 200px;
    border-right: 1px solid #f1f5f9;
  }

  .col-role-header {
    min-width: 140px;
    text-align: center;
    border-right: 1px solid #f1f5f9;

    &:last-child {
      border-right: none;
    }
  }

  .col-cell-access {
    text-align: center;
    border-right: 1px solid #f1f5f9;

    &:last-child {
      border-right: none;
    }
  }
}

.module-badge {
  display: inline-block;
  padding: 2px 8px;
  background: #f1f5f9;
  color: #334155;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

.page-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 6px;

  .page-title {
    font-weight: 600;
    color: #1e293b;
  }

  .page-code-sub {
    font-size: 11px;
    color: #94a3b8;
    font-family: monospace;
  }
}

.mini-actions-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;

  .mini-act-badge {
    font-size: 11px;
    padding: 1px 6px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 4px;
    color: #64748b;
  }
}

.role-cell-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;

  .role-name-text {
    font-size: 13px;
    font-weight: 600;
    color: #0f172a;
  }

  .role-scope-chip {
    font-size: 10px;
    padding: 1px 6px;
    border-radius: 10px;
    background: #eff6ff;
    color: #2563eb;
    border: 1px solid #dbeafe;
  }

  .edit-config-link {
    font-size: 11px;
    padding: 0;
    height: auto;
    margin-top: 2px;
  }
}

/* Access Chips in cells */
.access-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  transition: all 0.15s ease;
  user-select: none;

  .chip-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
  }

  &.chip-full {
    background: #ecfdf5;
    color: #059669;
    border: 1px solid #a7f3d0;

    .chip-dot {
      background: #10b981;
    }
  }

  &.chip-partial {
    background: #eff6ff;
    color: #2563eb;
    border: 1px solid #bfdbfe;

    .chip-dot {
      background: #3b82f6;
    }
  }

  &.chip-view {
    background: #f0fdf4;
    color: #166534;
    border: 1px solid #bbf7d0;

    .chip-dot {
      background: #22c55e;
    }
  }

  &.chip-none {
    color: #cbd5e1;
    font-size: 14px;
    font-weight: 400;
  }
}

.empty-cell {
  text-align: center;
  padding: 40px !important;
}
</style>
