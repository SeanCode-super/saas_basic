<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import {
  ArrowRight,
  Check,
  Clock,
  Close,
  DataAnalysis,
  Delete,
  Document,
  Expand,
  Filter,
  Fold,
  Key,
  Plus,
  Search,
  User,
  Warning
} from "@element-plus/icons-vue";
import {
  createIamDataPermissionRule,
  fetchRoleUserAssignment,
  replaceIamMenuSubjectGrants,
  saveRoleUserAssignment,
  updateIamDataPermissionRule
} from "@/api/modules/iam";
import type {
  IamDataPermissionRuleRow,
  IamDepartmentRow,
  IamMenuRow,
  IamMenuPermissionRow,
  IamPositionRow,
  IamRoleRow,
  IamUserRow
} from "@/api/modules/iam";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";

const props = defineProps<{
  visible: boolean;
  role: IamRoleRow | null;
  menus: IamMenuRow[];
  menuPermissions: IamMenuPermissionRow[];
  dataPermissionRules: IamDataPermissionRuleRow[];
  departments: IamDepartmentRow[];
  positions: IamPositionRow[];
  users: IamUserRow[];
  currentTenantId: number;
  canWrite: boolean;
}>();

const emit = defineEmits<{
  (e: "update:visible", value: boolean): void;
  (e: "saved"): void;
}>();

const activeTab = ref<"menus" | "dataScope" | "members" | "audit">("menus");
const saving = ref(false);
const loadingMembers = ref(false);
const searchMenuKeyword = ref("");

// Tab 1: Menus & Actions
const selectedMenuIds = ref<number[]>([]);
const selectedActionCodes = ref<string[]>([]);
const collapsedGroupIds = ref<Set<number>>(new Set());
const showOnlyGranted = ref(false);
const expandedPageActionIds = ref<Set<number>>(new Set());

// Tab 2: Data Scope
type ScopeType =
  | "ALL"
  | "DEPT_AND_CHILDREN"
  | "DEPT_ONLY"
  | "SELF_AND_SUBORDINATES"
  | "SELF"
  | "CUSTOM";

const selectedScopeType = ref<ScopeType>("DEPT_AND_CHILDREN");
const existingRuleId = ref<number | null>(null);
const customDeptIds = ref<number[]>([]);
const customUserIds = ref<number[]>([]);

// Tab 3: Members
const assignedUserIds = ref<number[]>([]);
const pendingAddUserId = ref<number | null>(null);

// Tab 4: Audit Trail
interface RoleAuditRecord {
  id: string;
  timestamp: string;
  operator: string;
  action: string;
  summary: string;
  diffs?: string[];
}
const roleAuditLogs = ref<RoleAuditRecord[]>([]);

function getAuditStorageKey(roleId: number) {
  return `saas_iam_role_audit_${props.currentTenantId}_${roleId}`;
}

function loadRoleAuditLogs(roleId: number) {
  try {
    const raw = localStorage.getItem(getAuditStorageKey(roleId));
    if (raw) {
      roleAuditLogs.value = JSON.parse(raw);
    } else {
      roleAuditLogs.value = [
        {
          id: `init-${roleId}`,
          timestamp: new Date().toISOString().replace("T", " ").substring(0, 19),
          operator: "系统管理员",
          action: "角色初始创建",
          summary: `初始化角色「${props.role?.roleName || ""}」，分配默认基线权限及数据范围。`,
          diffs: [
            `角色编码: ${props.role?.roleCode || "-"}`,
            `数据范围: ${props.role?.dataScopeType || "DEPT_AND_CHILDREN"}`
          ]
        }
      ];
    }
  } catch {
    roleAuditLogs.value = [];
  }
}

function recordRoleAuditLog(action: string, summary: string, diffs: string[]) {
  if (!props.role) return;
  const newRecord: RoleAuditRecord = {
    id: `audit-${Date.now()}`,
    timestamp: new Date().toISOString().replace("T", " ").substring(0, 19),
    operator: "当前登录管理员",
    action,
    summary,
    diffs
  };
  roleAuditLogs.value = [newRecord, ...roleAuditLogs.value];
  try {
    localStorage.setItem(
      getAuditStorageKey(props.role.id),
      JSON.stringify(roleAuditLogs.value.slice(0, 50))
    );
  } catch {
    // ignore
  }
}

// Menu calculations
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
  for (const list of map.values()) {
    list.sort((a, b) => a.sortNo - b.sortNo || a.id - b.id);
  }
  return map;
});

interface MenuModuleGroup {
  id: number;
  name: string;
  pages: Array<{
    id: number;
    name: string;
    code: string;
    actions: IamMenuRow[];
  }>;
}

const menuGroups = computed<MenuModuleGroup[]>(() => {
  const kw = searchMenuKeyword.value.trim().toLowerCase();
  const onlyGranted = showOnlyGranted.value;
  const roots = visibleMenus.value.filter((m) => m.parentId === 0 || !m.parentId);
  const groups: MenuModuleGroup[] = [];

  for (const root of roots) {
    const pages: MenuModuleGroup["pages"] = [];
    const directChildren = visibleMenus.value.filter((m) => m.parentId === root.id);

    if (root.menuType === "DIRECTORY") {
      for (const child of directChildren) {
        const actions = actionsByMenuId.value.get(child.id) ?? [];
        const matchKw =
          !kw ||
          child.menuName.toLowerCase().includes(kw) ||
          child.menuCode.toLowerCase().includes(kw) ||
          actions.some((a) => a.menuName.toLowerCase().includes(kw));

        const matchGranted =
          !onlyGranted ||
          isPageChecked(child.id) ||
          actions.some((a) => isActionChecked(a.menuCode));

        if (matchKw && matchGranted) {
          pages.push({
            id: child.id,
            name: child.menuName,
            code: child.menuCode,
            actions
          });
        }
      }
    } else {
      const actions = actionsByMenuId.value.get(root.id) ?? [];
      const matchKw =
        !kw ||
        root.menuName.toLowerCase().includes(kw) ||
        root.menuCode.toLowerCase().includes(kw) ||
        actions.some((a) => a.menuName.toLowerCase().includes(kw));

      const matchGranted =
        !onlyGranted ||
        isPageChecked(root.id) ||
        actions.some((a) => isActionChecked(a.menuCode));

      if (matchKw && matchGranted) {
        pages.push({
          id: root.id,
          name: root.menuName,
          code: root.menuCode,
          actions
        });
      }
    }

    if (pages.length > 0) {
      groups.push({
        id: root.id,
        name: root.menuName,
        pages
      });
    }
  }
  return groups;
});

const totalSelectedSummary = computed(() => {
  return `${selectedMenuIds.value.length} 个页面 · ${selectedActionCodes.value.length} 个操作`;
});

// Data Scope options
const SCOPE_CARDS: Array<{
  type: ScopeType;
  title: string;
  subtitle: string;
  badge: string;
  badgeType: "danger" | "primary" | "info" | "success" | "warning";
}> = [
  {
    type: "ALL",
    title: "全企业全部数据 (ALL)",
    subtitle: "无任何部门边界限制，可穿透查阅全企业所有部门与人员数据。适合平台管理员、企业所有者与审计员。",
    badge: "最高权限",
    badgeType: "danger"
  },
  {
    type: "DEPT_AND_CHILDREN",
    title: "本部门及所有下级部门 (DEPT_AND_CHILDREN)",
    subtitle: "根据登录人所属部门，自动穿透查阅本部门及所有递归子部门数据。组织架构调整自动适配。",
    badge: "推荐总监/主管",
    badgeType: "primary"
  },
  {
    type: "DEPT_ONLY",
    title: "仅限本部门 (DEPT_ONLY)",
    subtitle: "仅能查阅本人当前直接所属部门录入的数据，不含任何下属子部门数据。适用于基层独立部门主管。",
    badge: "单部门隔离",
    badgeType: "info"
  },
  {
    type: "SELF_AND_SUBORDINATES",
    title: "本人及直属下属 (SELF_AND_SUBORDINATES)",
    subtitle: "以组织汇报链为准，可查阅本人经办及汇报线上下属人员的数据。适用于项目经理、销售组长。",
    badge: "汇报链穿透",
    badgeType: "primary"
  },
  {
    type: "SELF",
    title: "仅限本人经办 (SELF)",
    subtitle: "最严格的行级数据隔离，只能查阅本人作为创建人或责任人的数据，跨人数据完全不可见。",
    badge: "个人严格隔离",
    badgeType: "success"
  },
  {
    type: "CUSTOM",
    title: "自定义勾选范围 (CUSTOM)",
    subtitle: "无需配置复杂代码，自由勾选允许此角色查阅的特定部门与成员。",
    badge: "精细配置",
    badgeType: "warning"
  }
];

// Department Tree for Custom Scope
interface DeptNode {
  id: number;
  label: string;
  children?: DeptNode[];
}

const departmentTreeData = computed<DeptNode[]>(() => {
  const map = new Map<number, DeptNode>();
  props.departments.forEach((d) => {
    map.set(d.id, {
      id: d.id,
      label: d.deptFullName || d.deptName,
      children: []
    });
  });
  const roots: DeptNode[] = [];
  props.departments.forEach((d) => {
    const node = map.get(d.id);
    if (!node) return;
    const parentId = Number(d.parentId ?? 0);
    if (parentId > 0 && map.has(parentId)) {
      map.get(parentId)!.children!.push(node);
    } else {
      roots.push(node);
    }
  });
  return roots;
});

// Member rows
const assignedMemberRows = computed(() => {
  const set = new Set(assignedUserIds.value);
  return props.users.filter((u) => set.has(u.id));
});

const unassignedUsers = computed(() => {
  const set = new Set(assignedUserIds.value);
  return props.users.filter((u) => !set.has(u.id) && u.status === "ENABLED");
});

// Watch role change to initialize drawer states
watch(
  () => props.role,
  async (role) => {
    if (!role) return;

    // 1. Initialize Menus & Actions
    const roleGrants = props.menuPermissions.filter(
      (p) => p.subjectType === "ROLE" && p.subjectValue === String(role.id) && p.status === "ENABLED"
    );
    selectedMenuIds.value = roleGrants.map((g) => g.menuId);

    const actionCodes: string[] = [];
    for (const g of roleGrants) {
      if (g.buttonCodesJson) {
        try {
          const list = JSON.parse(g.buttonCodesJson);
          if (Array.isArray(list)) {
            for (const item of list) {
              actionCodes.push(item);
            }
          }
        } catch {
          // ignore
        }
      }
    }
    selectedActionCodes.value = Array.from(new Set(actionCodes));

    // 2. Initialize Data Scope
    const rule = props.dataPermissionRules.find(
      (r) => r.subjectType === "ROLE" && r.subjectValue === String(role.id) && r.status === "ENABLED"
    );
    if (rule) {
      existingRuleId.value = rule.id;
      selectedScopeType.value = (rule.scopeType as ScopeType) || "DEPT_AND_CHILDREN";
      if (rule.configJson) {
        try {
          const parsed = JSON.parse(rule.configJson);
          customDeptIds.value = parsed.departmentIds || [];
          customUserIds.value = parsed.userIds || [];
        } catch {
          customDeptIds.value = [];
          customUserIds.value = [];
        }
      }
    } else {
      existingRuleId.value = null;
      selectedScopeType.value = "DEPT_AND_CHILDREN";
      customDeptIds.value = [];
      customUserIds.value = [];
    }

    // 3. Auto-collapse modules that have 0 granted permissions to keep drawer uncluttered
    const emptyGroupIds = new Set<number>();
    for (const root of visibleMenus.value.filter((m) => m.parentId === 0 || !m.parentId)) {
      const directChildren = visibleMenus.value.filter((m) => m.parentId === root.id);
      const childIds = root.menuType === "DIRECTORY" ? directChildren.map((c) => c.id) : [root.id];
      const hasAnySelected = childIds.some((id) => selectedMenuIds.value.includes(id));
      if (!hasAnySelected) {
        emptyGroupIds.add(root.id);
      }
    }
    collapsedGroupIds.value = emptyGroupIds;
    expandedPageActionIds.value = new Set();

    // 4. Load Members for this role
    loadingMembers.value = true;
    try {
      const res = await fetchRoleUserAssignment(role.id, props.currentTenantId);
      assignedUserIds.value = res.userIds || [];
    } catch {
      assignedUserIds.value = [];
    } finally {
      loadingMembers.value = false;
    }

    // 5. Load Audit Trail
    loadRoleAuditLogs(role.id);
  },
  { immediate: true }
);

watch(searchMenuKeyword, (kw) => {
  const trimmed = kw.trim().toLowerCase();
  if (trimmed) {
    collapsedGroupIds.value = new Set();
    const autoExpandPages = new Set<number>();
    for (const group of menuGroups.value) {
      for (const page of group.pages) {
        if (
          page.actions.some(
            (a) =>
              a.menuName.toLowerCase().includes(trimmed) ||
              a.menuCode.toLowerCase().includes(trimmed)
          )
        ) {
          autoExpandPages.add(page.id);
        }
      }
    }
    expandedPageActionIds.value = autoExpandPages;
  }
});

function isGroupCollapsed(groupId: number): boolean {
  return collapsedGroupIds.value.has(groupId);
}

function toggleGroupCollapse(groupId: number) {
  const next = new Set(collapsedGroupIds.value);
  if (next.has(groupId)) {
    next.delete(groupId);
  } else {
    next.add(groupId);
  }
  collapsedGroupIds.value = next;
}

function expandAllGroups() {
  collapsedGroupIds.value = new Set();
}

function collapseAllGroups() {
  collapsedGroupIds.value = new Set(menuGroups.value.map((g) => g.id));
  expandedPageActionIds.value = new Set();
}

function getGroupStat(group: MenuModuleGroup) {
  const selectedPagesCount = group.pages.filter((p) => isPageChecked(p.id)).length;
  const allActions = group.pages.flatMap((p) => p.actions);
  const selectedActionsCount = allActions.filter((a) => isActionChecked(a.menuCode)).length;
  return {
    selectedPagesCount,
    totalPagesCount: group.pages.length,
    selectedActionsCount,
    totalActionsCount: allActions.length,
    hasGranted: selectedPagesCount > 0 || selectedActionsCount > 0
  };
}

function isPageActionExpanded(pageId: number): boolean {
  return expandedPageActionIds.value.has(pageId);
}

function togglePageActionExpand(pageId: number) {
  const next = new Set(expandedPageActionIds.value);
  if (next.has(pageId)) {
    next.delete(pageId);
  } else {
    next.add(pageId);
  }
  expandedPageActionIds.value = next;
}

function getPageActionStats(page: MenuModuleGroup["pages"][0]) {
  const total = page.actions.length;
  const selected = page.actions.filter((a) => isActionChecked(a.menuCode)).length;
  return {
    total,
    selected,
    allSelected: total > 0 && selected === total,
    noneSelected: selected === 0,
    hasPartial: selected > 0 && selected < total
  };
}

function selectAllPageActions(page: MenuModuleGroup["pages"][0]) {
  const codes = page.actions.map((a) => a.menuCode);
  selectedActionCodes.value = Array.from(new Set([...selectedActionCodes.value, ...codes]));
  if (!selectedMenuIds.value.includes(page.id)) {
    selectedMenuIds.value = [...selectedMenuIds.value, page.id];
  }
}

function selectReadOnlyPageActions(page: MenuModuleGroup["pages"][0]) {
  const readKeywords = ["view", "list", "query", "export", "detail", "get", "info", "查", "看", "导", "详", "阅"];
  const readActions = page.actions.filter((a) => {
    const code = a.menuCode.toLowerCase();
    const name = a.menuName.toLowerCase();
    return readKeywords.some((k) => code.includes(k) || name.includes(k));
  });
  const targets = readActions.length > 0 ? readActions : (page.actions.length > 0 ? [page.actions[0]] : []);
  const targetCodes = targets.map((a) => a.menuCode);
  const allPageCodes = page.actions.map((a) => a.menuCode);

  selectedActionCodes.value = [
    ...selectedActionCodes.value.filter((c) => !allPageCodes.includes(c)),
    ...targetCodes
  ];
  if (!selectedMenuIds.value.includes(page.id)) {
    selectedMenuIds.value = [...selectedMenuIds.value, page.id];
  }
}

function clearPageActions(page: MenuModuleGroup["pages"][0]) {
  const codes = page.actions.map((a) => a.menuCode);
  selectedActionCodes.value = selectedActionCodes.value.filter((c) => !codes.includes(c));
}

function isPageChecked(pageId: number): boolean {
  return selectedMenuIds.value.includes(pageId);
}

function togglePage(pageId: number, checked: boolean) {
  if (checked) {
    if (!selectedMenuIds.value.includes(pageId)) {
      selectedMenuIds.value = [...selectedMenuIds.value, pageId];
    }
  } else {
    selectedMenuIds.value = selectedMenuIds.value.filter((id) => id !== pageId);
    // Also remove any actions belonging to this page
    const actions = actionsByMenuId.value.get(pageId) ?? [];
    const actionCodes = actions.map((a) => a.menuCode);
    selectedActionCodes.value = selectedActionCodes.value.filter((c) => !actionCodes.includes(c));
    expandedPageActionIds.value.delete(pageId);
  }
}

function isActionChecked(code: string): boolean {
  return selectedActionCodes.value.includes(code);
}

function toggleAction(pageId: number, code: string, checked: boolean) {
  if (checked) {
    if (!selectedActionCodes.value.includes(code)) {
      selectedActionCodes.value = [...selectedActionCodes.value, code];
    }
    // Checking an action automatically checks its parent page
    if (!selectedMenuIds.value.includes(pageId)) {
      selectedMenuIds.value = [...selectedMenuIds.value, pageId];
    }
  } else {
    selectedActionCodes.value = selectedActionCodes.value.filter((c) => c !== code);
  }
}

function isGroupAllChecked(group: MenuModuleGroup): boolean {
  return group.pages.every((p) => isPageChecked(p.id));
}

function isGroupIndeterminate(group: MenuModuleGroup): boolean {
  const checkedCount = group.pages.filter((p) => isPageChecked(p.id)).length;
  return checkedCount > 0 && checkedCount < group.pages.length;
}

function toggleGroup(group: MenuModuleGroup, checked: boolean) {
  const pageIds = group.pages.map((p) => p.id);
  const actionCodes = group.pages.flatMap((p) => p.actions.map((a) => a.menuCode));
  if (checked) {
    selectedMenuIds.value = Array.from(new Set([...selectedMenuIds.value, ...pageIds]));
    selectedActionCodes.value = Array.from(new Set([...selectedActionCodes.value, ...actionCodes]));
  } else {
    selectedMenuIds.value = selectedMenuIds.value.filter((id) => !pageIds.includes(id));
    selectedActionCodes.value = selectedActionCodes.value.filter((c) => !actionCodes.includes(c));
  }
}

function selectAllPages() {
  const allPageIds = visibleMenus.value.map((m) => m.id);
  const allActionCodes = buttonMenus.value.map((m) => m.menuCode);
  selectedMenuIds.value = allPageIds;
  selectedActionCodes.value = allActionCodes;
}

function clearAllPages() {
  selectedMenuIds.value = [];
  selectedActionCodes.value = [];
}

function addMember() {
  if (!pendingAddUserId.value) return;
  if (!assignedUserIds.value.includes(pendingAddUserId.value)) {
    assignedUserIds.value = [...assignedUserIds.value, pendingAddUserId.value];
  }
  pendingAddUserId.value = null;
}

function removeMember(userId: number) {
  assignedUserIds.value = assignedUserIds.value.filter((id) => id !== userId);
}

async function handleSaveAll() {
  if (!props.role) return;
  saving.value = true;
  try {
    const roleId = props.role.id;

    // 1. Save Menu & Button Grants
    await replaceIamMenuSubjectGrants({
      subjectType: "ROLE",
      subjectValue: String(roleId),
      grants: selectedMenuIds.value.map((menuId) => {
        const availableActions = actionsByMenuId.value.get(menuId) ?? [];
        const actionCodes = availableActions
          .map((a) => a.menuCode)
          .filter((c) => selectedActionCodes.value.includes(c));
        return {
          menuId,
          actionCodes
        };
      })
    });

    // 2. Save Data Scope Rule
    let configJson: string | undefined = undefined;
    if (selectedScopeType.value === "CUSTOM") {
      configJson = JSON.stringify({
        departmentIds: customDeptIds.value,
        userIds: customUserIds.value
      });
    }

    if (existingRuleId.value) {
      await updateIamDataPermissionRule(existingRuleId.value, {
        tenantId: props.currentTenantId,
        resourceCode: "*",
        resourceName: "全局数据资源",
        subjectType: "ROLE",
        subjectValue: String(roleId),
        scopeType: selectedScopeType.value,
        configJson,
        status: "ENABLED",
        remark: "角色三合一抽屉统一配置"
      });
    } else {
      await createIamDataPermissionRule({
        tenantId: props.currentTenantId,
        resourceCode: "*",
        resourceName: "全局数据资源",
        subjectType: "ROLE",
        subjectValue: String(roleId),
        scopeType: selectedScopeType.value,
        configJson,
        status: "ENABLED",
        remark: "角色三合一抽屉统一配置"
      });
    }

    // 3. Save Role Users Assignment
    await saveRoleUserAssignment({
      roleId,
      tenantId: props.currentTenantId,
      userIds: assignedUserIds.value
    });

    recordRoleAuditLog(
      "全量配置更新",
      `管理员更新了角色「${props.role.roleName}」的权限、数据范围与成员配置`,
      [
        `功能权限: ${selectedMenuIds.value.length} 个页面 / ${selectedActionCodes.value.length} 个操作`,
        `数据范围: ${selectedScopeType.value}${selectedScopeType.value === 'CUSTOM' ? ` (部门: ${customDeptIds.value.length}, 人员: ${customUserIds.value.length})` : ''}`,
        `授权成员: ${assignedUserIds.value.length} 人`
      ]
    );

    ElMessage.success(`角色「${props.role.roleName}」权限与成员配置已成功保存！`);
    emit("saved");
    emit("update:visible", false);
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || "保存角色配置失败");
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    :size="720"
    :destroy-on-close="true"
    class="iam-role-drawer"
    @update:model-value="(val) => emit('update:visible', val)"
  >
    <template #header>
      <div class="drawer-header-content">
        <div class="role-badge-title">
          <div class="role-icon-box">
            <el-icon :size="20"><Key /></el-icon>
          </div>
          <div>
            <div class="title-row">
              <span class="role-name">{{ role?.roleName ?? "角色权限配置" }}</span>
              <el-tag size="small" type="info" class="code-tag">{{ role?.roleCode }}</el-tag>
              <BaseStatusTag :status="String(role?.status)" :type="role?.status === 'ENABLED' ? 'success' : 'danger'" />
            </div>
            <div class="role-desc">
              三合一角色控制台：在此集中治理该角色的页面操作权限、数据行级访问边界与授权员工成员。
            </div>
          </div>
        </div>
      </div>
    </template>

    <div class="drawer-tabs-wrapper">
      <el-radio-group v-model="activeTab" class="segmented-tabs">
        <el-radio-button value="menus">
          <el-icon><Document /></el-icon>
          <span>页面与操作权限</span>
          <span class="count-pill">{{ selectedMenuIds.length }}</span>
        </el-radio-button>
        <el-radio-button value="dataScope">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据行级范围</span>
          <span class="count-pill pill-blue">{{ selectedScopeType }}</span>
        </el-radio-button>
        <el-radio-button value="members">
          <el-icon><User /></el-icon>
          <span>授权成员</span>
          <span class="count-pill">{{ assignedUserIds.length }}</span>
        </el-radio-button>
        <el-radio-button value="audit">
          <el-icon><Clock /></el-icon>
          <span>变更审计</span>
          <span class="count-pill">{{ roleAuditLogs.length }}</span>
        </el-radio-button>
      </el-radio-group>
    </div>

    <div class="drawer-body-scroll">
      <!-- TAB 1: 页面与操作权限 -->
      <div v-show="activeTab === 'menus'" class="tab-pane-content">
        <div class="toolbar-bar">
          <div class="toolbar-left">
            <el-input
              v-model="searchMenuKeyword"
              placeholder="搜索菜单或按钮..."
              clearable
              :prefix-icon="Search"
              class="search-input"
            />
            <el-checkbox v-model="showOnlyGranted" class="filter-granted-checkbox">
              <span>仅看已授权功能</span>
            </el-checkbox>
          </div>
          <div class="toolbar-actions">
            <el-button link size="small" :icon="Expand" @click="expandAllGroups">全部展开</el-button>
            <el-divider direction="vertical" />
            <el-button link size="small" :icon="Fold" @click="collapseAllGroups">全部折叠</el-button>
            <el-divider direction="vertical" />
            <el-button link type="primary" size="small" @click="selectAllPages">全选</el-button>
            <el-divider direction="vertical" />
            <el-button link size="small" @click="clearAllPages">清空</el-button>
          </div>
        </div>

        <div v-if="menuGroups.length === 0" class="empty-search-box">
          <el-empty description="未找到匹配的功能模块" :image-size="60" />
        </div>

        <div v-else class="menu-groups-container">
          <div
            v-for="group in menuGroups"
            :key="group.id"
            class="menu-group-card"
            :class="{ 'is-collapsed': isGroupCollapsed(group.id) }"
          >
            <div class="group-header" @click="toggleGroupCollapse(group.id)">
              <div class="group-header-left">
                <el-icon
                  class="chevron-icon"
                  :class="{ 'is-open': !isGroupCollapsed(group.id) }"
                >
                  <ArrowRight />
                </el-icon>
                <el-checkbox
                  :model-value="isGroupAllChecked(group)"
                  :indeterminate="isGroupIndeterminate(group)"
                  @click.stop
                  @change="(val) => toggleGroup(group, Boolean(val))"
                >
                  <span class="group-title">{{ group.name }}</span>
                </el-checkbox>
              </div>

              <div class="group-header-right">
                <span
                  class="group-stat-pill"
                  :class="{ active: getGroupStat(group).hasGranted, inactive: !getGroupStat(group).hasGranted }"
                >
                  <template v-if="getGroupStat(group).hasGranted">
                    已选 {{ getGroupStat(group).selectedPagesCount }}/{{ getGroupStat(group).totalPagesCount }} 页面 · {{ getGroupStat(group).selectedActionsCount }}/{{ getGroupStat(group).totalActionsCount }} 操作
                  </template>
                  <template v-else>
                    未配置 ({{ group.pages.length }} 个子页面)
                  </template>
                </span>
              </div>
            </div>

            <div v-show="!isGroupCollapsed(group.id)" class="group-pages-list">
              <div
                v-for="page in group.pages"
                :key="page.id"
                class="page-row-card"
                :class="{
                  'is-active-page': isPageChecked(page.id),
                  'is-panel-open': isPageActionExpanded(page.id)
                }"
              >
                <div
                  class="page-meta-row"
                  @click="page.actions.length > 0 && togglePageActionExpand(page.id)"
                >
                  <div class="page-left-info" @click.stop>
                    <el-checkbox
                      :model-value="isPageChecked(page.id)"
                      @change="(val) => togglePage(page.id, Boolean(val))"
                    >
                      <span class="page-name">{{ page.name }}</span>
                      <span class="page-code">({{ page.code }})</span>
                    </el-checkbox>
                  </div>

                  <div class="page-right-ctrl" @click.stop>
                    <template v-if="page.actions.length > 0">
                      <button
                        type="button"
                        class="action-pill-toggle-btn"
                        :class="{
                          'is-expanded': isPageActionExpanded(page.id),
                          'has-actions': getPageActionStats(page).selected > 0
                        }"
                        @click="togglePageActionExpand(page.id)"
                      >
                        <span class="action-stat-text">
                          {{ getPageActionStats(page).selected > 0
                              ? `已赋 ${getPageActionStats(page).selected}/${getPageActionStats(page).total} 项操作`
                              : `${getPageActionStats(page).total} 项操作未配` }}
                        </span>
                        <el-icon class="toggle-chevron" :class="{ 'is-open': isPageActionExpanded(page.id) }">
                          <ArrowRight />
                        </el-icon>
                      </button>
                    </template>
                    <template v-else>
                      <el-tooltip
                        content="该功能为纯查阅/看板页面，勾选左侧复选框即可放行，无需额外配置操作按钮"
                        placement="top"
                        :show-after="200"
                      >
                        <span class="base-page-hint">整页查阅授权</span>
                      </el-tooltip>
                    </template>
                  </div>
                </div>

                <!-- Progressive Disclosure: Collapsible Action Panel -->
                <div
                  v-if="page.actions.length > 0 && isPageActionExpanded(page.id)"
                  class="page-actions-drawer-panel"
                >
                  <div class="action-panel-toolbar">
                    <div class="panel-guide-text">
                      <el-icon class="guide-icon"><Key /></el-icon>
                      <span>细粒度按钮权限配置</span>
                    </div>
                    <div class="panel-quick-actions">
                      <el-button link size="small" type="primary" @click="selectAllPageActions(page)">全部授予</el-button>
                      <el-divider direction="vertical" />
                      <el-button link size="small" @click="selectReadOnlyPageActions(page)">仅查阅/导出</el-button>
                      <el-divider direction="vertical" />
                      <el-button link size="small" @click="clearPageActions(page)">清空操作</el-button>
                    </div>
                  </div>

                  <div class="actions-pill-grid">
                    <div
                      v-for="act in page.actions"
                      :key="act.id"
                      class="action-pill-item"
                      :class="{ active: isActionChecked(act.menuCode) }"
                      @click="toggleAction(page.id, act.menuCode, !isActionChecked(act.menuCode))"
                    >
                      <el-icon v-if="isActionChecked(act.menuCode)" class="check-icon"><Check /></el-icon>
                      <span class="act-name">{{ act.menuName }}</span>
                      <span class="act-code">({{ act.menuCode }})</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- TAB 2: 数据行级范围 -->
      <div v-show="activeTab === 'dataScope'" class="tab-pane-content">
        <div class="scope-hint-banner">
          <el-icon class="banner-icon"><Warning /></el-icon>
          <div class="banner-text">
            <strong>行级数据隔离策略（Row-Level Security）</strong>
            <p>系统将在数据库引擎中自动改写 SQL 条件，防止员工越权查阅未授权部门或同事录入的敏行业务数据。</p>
          </div>
        </div>

        <div class="scope-cards-grid">
          <div
            v-for="card in SCOPE_CARDS"
            :key="card.type"
            class="scope-select-card"
            :class="{ selected: selectedScopeType === card.type }"
            @click="selectedScopeType = card.type"
          >
            <div class="card-radio-row">
              <div class="radio-indicator">
                <div v-if="selectedScopeType === card.type" class="radio-dot" />
              </div>
              <div class="card-title-wrap">
                <span class="card-title">{{ card.title }}</span>
                <el-tag size="small" :type="card.badgeType" effect="plain">{{ card.badge }}</el-tag>
              </div>
            </div>
            <div class="card-sub">{{ card.subtitle }}</div>
          </div>
        </div>

        <!-- Custom Scope Tree Selectors -->
        <div v-if="selectedScopeType === 'CUSTOM'" class="custom-scope-box">
          <div class="custom-field">
            <label class="field-label">允许访问的特定部门：</label>
            <el-tree-select
              v-model="customDeptIds"
              :data="departmentTreeData"
              multiple
              show-checkbox
              check-strictly
              placeholder="选择允许跨部门查阅的部门..."
              style="width: 100%"
              node-key="id"
            />
          </div>

          <div class="custom-field" style="margin-top: 14px">
            <label class="field-label">允许访问的特定人员：</label>
            <el-select
              v-model="customUserIds"
              multiple
              filterable
              placeholder="搜索并勾选允许查阅的人员..."
              style="width: 100%"
            >
              <el-option
                v-for="u in users"
                :key="u.id"
                :label="`${u.nickname} (${u.username})`"
                :value="u.id"
              />
            </el-select>
          </div>
        </div>

        <!-- Live Behavior Preview Box -->
        <div class="preview-sql-box">
          <div class="preview-header">
            <el-icon><DataAnalysis /></el-icon>
            <span>生效效果模拟预览</span>
          </div>
          <div class="preview-code">
            <span v-if="selectedScopeType === 'ALL'" class="code-comment">-- 全放行模式：无任何部门与成员行级过滤条件</span>
            <span v-else-if="selectedScopeType === 'DEPT_AND_CHILDREN'" class="code-comment">-- 动态部门树：WHERE t.dept_id IN (当前用户所属部门及所有子部门)</span>
            <span v-else-if="selectedScopeType === 'DEPT_ONLY'" class="code-comment">-- 严格单部门：WHERE t.dept_id = 当前用户直属部门ID</span>
            <span v-else-if="selectedScopeType === 'SELF_AND_SUBORDINATES'" class="code-comment">-- 汇报链模式：WHERE t.created_by IN (本人ID, 直属下级人员ID集合)</span>
            <span v-else-if="selectedScopeType === 'SELF'" class="code-comment">-- 仅限本人：WHERE t.created_by = 当前登录用户ID</span>
            <span v-else-if="selectedScopeType === 'CUSTOM'" class="code-comment">-- 自定义模式：WHERE t.dept_id IN (已勾选部门) OR t.created_by IN (已勾选人员)</span>
          </div>
        </div>
      </div>

      <!-- TAB 3: 授权成员 -->
      <div v-show="activeTab === 'members'" class="tab-pane-content">
        <div class="member-add-bar">
          <el-select
            v-model="pendingAddUserId"
            filterable
            placeholder="搜索并选择需要分配此角色的成员..."
            class="member-select"
          >
            <el-option
              v-for="u in unassignedUsers"
              :key="u.id"
              :label="`${u.nickname} (@${u.username}) · 类型: ${u.userType}`"
              :value="u.id"
            />
          </el-select>
          <el-button type="primary" :icon="Plus" :disabled="!pendingAddUserId" @click="addMember">
            添加成员
          </el-button>
        </div>

        <div v-loading="loadingMembers" class="members-table-wrapper">
          <div v-if="assignedMemberRows.length === 0" class="empty-members">
            <el-empty description="当前角色尚未分配任何成员" :image-size="60" />
          </div>
          <div v-else class="members-grid">
            <div v-for="user in assignedMemberRows" :key="user.id" class="member-chip-card">
              <div class="user-avatar-initial">{{ user.nickname?.charAt(0) || user.username?.charAt(0) }}</div>
              <div class="user-info-text">
                <div class="user-name-line">
                  <span class="user-nick">{{ user.nickname }}</span>
                  <span class="user-handle">@{{ user.username }}</span>
                </div>
                <div class="user-sub-line">
                  <span>账号类型: {{ user.userType }}</span>
                </div>
              </div>
              <el-button
                link
                type="danger"
                :icon="Delete"
                class="remove-btn"
                title="从此角色移除该成员"
                @click="removeMember(user.id)"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- TAB 4: 变更审计 -->
      <div v-show="activeTab === 'audit'" class="tab-pane-content audit-tab-pane">
        <div class="audit-hint-banner">
          <el-icon :size="18"><Clock /></el-icon>
          <div class="banner-text">
            <div class="banner-title">角色权限变更履历与安全合规审计</div>
            <div class="banner-sub">记录本角色的历次权限分配、行级数据范围调整及授权人员变动，满足等保与 SOC2 安全合规审计要求。</div>
          </div>
        </div>

        <div class="audit-timeline-wrapper">
          <div v-if="roleAuditLogs.length === 0" class="empty-audit">
            <el-empty description="暂无变更审计记录" :image-size="60" />
          </div>
          <el-timeline v-else>
            <el-timeline-item
              v-for="log in roleAuditLogs"
              :key="log.id"
              :timestamp="log.timestamp"
              placement="top"
              type="primary"
              size="large"
            >
              <div class="audit-log-card">
                <div class="log-header">
                  <div class="log-action-tag">
                    <el-tag size="small" effect="plain" type="primary">{{ log.action }}</el-tag>
                    <span class="log-operator">操作人：{{ log.operator }}</span>
                  </div>
                </div>
                <div class="log-summary">{{ log.summary }}</div>
                <div v-if="log.diffs && log.diffs.length > 0" class="log-diff-box">
                  <div class="diff-title">变更明细：</div>
                  <ul class="diff-list">
                    <li v-for="(item, idx) in log.diffs" :key="idx">{{ item }}</li>
                  </ul>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="drawer-footer-row">
        <div class="summary-stat">
          <span>当前配置：</span>
          <strong>{{ totalSelectedSummary }}</strong>
        </div>
        <div class="footer-buttons">
          <el-button @click="emit('update:visible', false)">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSaveAll">
            保存全部配置
          </el-button>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.iam-role-drawer {
  :deep(.el-drawer__header) {
    margin-bottom: 0;
    padding: 20px 24px 16px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  :deep(.el-drawer__body) {
    padding: 0;
    display: flex;
    flex-direction: column;
    background: #f8fafc;
  }

  :deep(.el-drawer__footer) {
    border-top: 1px solid var(--el-border-color-lighter);
    padding: 14px 24px;
    background: #fff;
  }
}

.drawer-header-content {
  .role-badge-title {
    display: flex;
    align-items: flex-start;
    gap: 14px;

    .role-icon-box {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      background: #eff6ff;
      color: #2563eb;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .title-row {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 4px;

      .role-name {
        font-size: 17px;
        font-weight: 600;
        color: #0f172a;
      }

      .code-tag {
        font-family: ui-monospace, monospace;
      }
    }

    .role-desc {
      font-size: 13px;
      color: #64748b;
      line-height: 1.5;
    }
  }
}

.drawer-tabs-wrapper {
  background: #fff;
  padding: 12px 24px;
  border-bottom: 1px solid var(--el-border-color-lighter);

  .segmented-tabs {
    display: flex;
    width: 100%;

    :deep(.el-radio-button) {
      flex: 1;

      .el-radio-button__inner {
        width: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        padding: 9px 16px;
        font-weight: 500;
      }
    }
  }

  .count-pill {
    padding: 1px 7px;
    border-radius: 10px;
    font-size: 11px;
    background: #f1f5f9;
    color: #475569;

    &.pill-blue {
      background: #eff6ff;
      color: #2563eb;
      font-family: monospace;
      font-size: 10px;
    }
  }
}

.drawer-body-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.tab-pane-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Toolbar */
.toolbar-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 16px;
    flex: 1;

    .search-input {
      max-width: 260px;
    }

    .filter-granted-checkbox {
      margin-bottom: 0;
      user-select: none;
    }
  }

  .toolbar-actions {
    display: flex;
    align-items: center;
    gap: 4px;
  }
}

.empty-search-box {
  background: #fff;
  border-radius: 10px;
  border: 1px dashed #cbd5e1;
  padding: 32px 0;
}

/* Menu Group Card */
.menu-groups-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.menu-group-card {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  overflow: hidden;
  transition: border-color 0.15s ease;

  &:hover {
    border-color: #cbd5e1;
  }

  .group-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 14px;
    background: #f8fafc;
    border-bottom: 1px solid #f1f5f9;
    cursor: pointer;
    user-select: none;
    transition: background-color 0.15s ease;

    &:hover {
      background: #f1f5f9;
    }

    .group-header-left {
      display: flex;
      align-items: center;
      gap: 8px;

      .chevron-icon {
        font-size: 13px;
        color: #94a3b8;
        transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1);

        &.is-open {
          transform: rotate(90deg);
        }
      }

      .group-title {
        font-size: 13px;
        font-weight: 600;
        color: #1e293b;
      }
    }

    .group-header-right {
      display: flex;
      align-items: center;
    }

    .group-stat-pill {
      font-size: 11px;
      padding: 2px 10px;
      border-radius: 12px;
      line-height: 1.4;

      &.active {
        background: #eff6ff;
        color: #2563eb;
        font-weight: 500;
        border: 1px solid #bfdbfe;
      }

      &.inactive {
        background: #f1f5f9;
        color: #94a3b8;
      }
    }
  }

  &.is-collapsed {
    .group-header {
      border-bottom: none;
    }
  }

  .group-pages-list {
    display: flex;
    flex-direction: column;
    padding: 6px 16px 10px;
  }

  .page-row-card {
    padding: 8px 10px;
    margin: 2px 0;
    border-radius: 8px;
    border: 1px solid transparent;
    transition: all 0.15s ease;

    &:hover {
      background: #f8fafc;
    }

    &.is-panel-open {
      background: #f8fafc;
      border-color: #e2e8f0;
    }

    .page-meta-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      min-height: 28px;
      cursor: pointer;

      .page-left-info {
        display: flex;
        align-items: center;

        .page-name {
          font-size: 13px;
          font-weight: 500;
          color: #334155;
        }

        .page-code {
          margin-left: 6px;
          font-size: 11px;
          color: #94a3b8;
          font-family: ui-monospace, monospace;
        }
      }

      .page-right-ctrl {
        display: flex;
        align-items: center;

        .base-page-hint {
          display: inline-flex;
          align-items: center;
          padding: 2px 8px;
          border-radius: 10px;
          font-size: 11px;
          color: #94a3b8;
          background: #f1f5f9;
          cursor: help;
          user-select: none;
        }

        .action-pill-toggle-btn {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          padding: 3px 10px;
          border-radius: 12px;
          border: 1px solid #e2e8f0;
          background: #fff;
          font-size: 11px;
          color: #64748b;
          cursor: pointer;
          transition: all 0.15s ease;
          outline: none;

          &:hover {
            border-color: #93c5fd;
            color: #2563eb;
            background: #eff6ff;
          }

          &.has-actions {
            background: #eff6ff;
            color: #2563eb;
            font-weight: 500;
            border-color: #bfdbfe;
          }

          &.is-expanded {
            border-color: #2563eb;
            background: #eff6ff;
            color: #2563eb;
          }

          .toggle-chevron {
            font-size: 10px;
            transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1);

            &.is-open {
              transform: rotate(90deg);
            }
          }
        }
      }
    }

    /* Sub-drawer panel for actions */
    .page-actions-drawer-panel {
      margin-top: 8px;
      margin-left: 24px;
      padding: 10px 12px;
      border-radius: 6px;
      background: #fff;
      border: 1px dashed #cbd5e1;
      display: flex;
      flex-direction: column;
      gap: 10px;

      .action-panel-toolbar {
        display: flex;
        align-items: center;
        justify-content: space-between;

        .panel-guide-text {
          display: flex;
          align-items: center;
          gap: 6px;
          font-size: 11px;
          color: #64748b;
          font-weight: 500;

          .guide-icon {
            font-size: 12px;
            color: #2563eb;
          }
        }

        .panel-quick-actions {
          display: flex;
          align-items: center;
        }
      }

      .actions-pill-grid {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;

        .action-pill-item {
          display: inline-flex;
          align-items: center;
          gap: 4px;
          padding: 4px 10px;
          border-radius: 6px;
          background: #f8fafc;
          border: 1px solid #e2e8f0;
          font-size: 12px;
          color: #475569;
          cursor: pointer;
          transition: all 0.15s ease;
          user-select: none;

          .act-name {
            font-size: 12px;
          }

          .act-code {
            font-size: 10px;
            color: #94a3b8;
            font-family: ui-monospace, monospace;
          }

          &:hover {
            border-color: #93c5fd;
            color: #2563eb;
          }

          &.active {
            background: #eff6ff;
            border-color: #2563eb;
            color: #2563eb;
            font-weight: 500;

            .act-code {
              color: #60a5fa;
            }
          }

          .check-icon {
            font-size: 11px;
          }
        }
      }
    }
  }
}

/* Data Scope Cards */
.scope-hint-banner {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 16px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;

  .banner-icon {
    font-size: 18px;
    color: #2563eb;
    margin-top: 2px;
  }

  .banner-text {
    strong {
      display: block;
      font-size: 13px;
      color: #1e3a8a;
      margin-bottom: 2px;
    }

    p {
      margin: 0;
      font-size: 12px;
      color: #3b82f6;
      line-height: 1.4;
    }
  }
}

.scope-cards-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.scope-select-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px 16px;
  cursor: pointer;
  transition: all 0.18s ease;

  &:hover {
    border-color: #93c5fd;
    box-shadow: 0 2px 6px rgb(37 99 235 / 0.05);
  }

  &.selected {
    border-color: #2563eb;
    background: #f0f7ff;
    box-shadow: 0 0 0 1px #2563eb;
  }

  .card-radio-row {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 6px;

    .radio-indicator {
      width: 16px;
      height: 16px;
      border-radius: 50%;
      border: 1.5px solid #cbd5e1;
      display: flex;
      align-items: center;
      justify-content: center;

      .radio-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background: #2563eb;
      }
    }

    .card-title-wrap {
      display: flex;
      align-items: center;
      gap: 8px;

      .card-title {
        font-size: 14px;
        font-weight: 600;
        color: #1e293b;
      }
    }
  }

  .card-sub {
    margin-left: 26px;
    font-size: 12px;
    color: #64748b;
    line-height: 1.45;
  }
}

.custom-scope-box {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 16px;

  .field-label {
    display: block;
    font-size: 13px;
    font-weight: 500;
    color: #334155;
    margin-bottom: 6px;
  }
}

.preview-sql-box {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 16px;

  .preview-header {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    font-weight: 600;
    color: #475569;
    margin-bottom: 8px;
  }

  .preview-code {
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    padding: 8px 12px;
    border-radius: 6px;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    color: #2563eb;
  }
}

/* Members Tab */
.member-add-bar {
  display: flex;
  gap: 12px;

  .member-select {
    flex: 1;
  }
}

.members-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 10px;
}

.member-chip-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 14px;

  .user-avatar-initial {
    width: 34px;
    height: 34px;
    border-radius: 50%;
    background: #e0e7ff;
    color: #4338ca;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 600;
    font-size: 14px;
    flex-shrink: 0;
  }

  .user-info-text {
    flex: 1;
    min-width: 0;

    .user-name-line {
      display: flex;
      align-items: center;
      gap: 6px;

      .user-nick {
        font-size: 13px;
        font-weight: 600;
        color: #1e293b;
      }

      .user-handle {
        font-size: 12px;
        color: #94a3b8;
      }
    }

    .user-sub-line {
      font-size: 11px;
      color: #64748b;
      margin-top: 2px;
    }
  }

  .remove-btn {
    opacity: 0.6;

    &:hover {
      opacity: 1;
    }
  }
}

/* Audit Tab */
.audit-hint-banner {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 20px;
  color: #1e40af;

  .banner-text {
    flex: 1;

    .banner-title {
      font-size: 13px;
      font-weight: 600;
      color: #1e3a8a;
      margin-bottom: 4px;
    }

    .banner-sub {
      font-size: 12px;
      color: #3b82f6;
      line-height: 1.5;
    }
  }
}

.audit-timeline-wrapper {
  padding: 8px 12px 24px 8px;

  .audit-log-card {
    background: #fff;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    padding: 12px 16px;
    box-shadow: 0 1px 3px rgb(0 0 0 / 0.04);

    .log-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 8px;

      .log-action-tag {
        display: flex;
        align-items: center;
        gap: 10px;

        .log-operator {
          font-size: 12px;
          color: #64748b;
        }
      }
    }

    .log-summary {
      font-size: 13px;
      font-weight: 500;
      color: #1e293b;
      margin-bottom: 8px;
      line-height: 1.5;
    }

    .log-diff-box {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      padding: 8px 12px;

      .diff-title {
        font-size: 11px;
        font-weight: 600;
        color: #475569;
        margin-bottom: 4px;
        text-transform: uppercase;
      }

      .diff-list {
        margin: 0;
        padding-left: 18px;
        font-size: 12px;
        color: #334155;
        line-height: 1.6;
        font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      }
    }
  }
}

/* Footer */
.drawer-footer-row {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .summary-stat {
    font-size: 13px;
    color: #64748b;

    strong {
      color: #1e293b;
    }
  }

  .footer-buttons {
    display: flex;
    gap: 10px;
  }
}
</style>
