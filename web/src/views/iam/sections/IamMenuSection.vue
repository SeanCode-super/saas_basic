<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import {
  ArrowDown,
  ArrowRight,
  Check,
  CircleCheck,
  CircleClose,
  Close,
  CopyDocument,
  Cpu,
  DataBoard,
  Document,
  DocumentChecked,
  Edit,
  Expand,
  Filter,
  Fold,
  Folder,
  Key,
  Link,
  Operation,
  Plus,
  Promotion,
  Refresh,
  Search,
  Setting,
  Switch,
  View
} from "@element-plus/icons-vue";
import {
  createIamMenuPermission,
  fetchRoleMenuIds,
  replaceIamMenuSubjectGrants,
  replaceRoleMenus,
  updateIamMenu,
  updateIamMenuPermission,
  updateIamMenuPermissionStatus
} from "@/api/modules/iam";
import { reloadRedisCache } from "@/api/modules/system";
import type {
  IamDepartmentRow,
  IamMenuPermissionRow,
  IamMenuRow,
  IamPositionRow,
  IamRoleRow,
  IamUserRow
} from "@/api/modules/iam";
import BaseCard from "@/components/base/BaseCard.vue";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
import { resolveAppMeta } from "@/config/app-taxonomy";
import { localizeMenuType, localizeSubjectType } from "../iam-meta";

type SubjectType = "USER" | "DEPARTMENT" | "POSITION" | "COMPANY" | "TENANT";

interface DomainMeta {
  key: string;
  label: string;
  icon: string;
  desc: string;
  tagType: "primary" | "success" | "warning" | "danger" | "info";
  customStyle?: Record<string, string>;
}

const DOMAIN_META_LIST: DomainMeta[] = [
  {
    key: "overview",
    label: "平台总览",
    icon: "DataBoard",
    desc: "全平台运行全景、仪表盘与全局上下文",
    tagType: "success"
  },
  {
    key: "foundation",
    label: "基础治理",
    icon: "Setting",
    desc: "多租户管控、IAM 身份权限组织与系统底层基线",
    tagType: "primary"
  },
  {
    key: "runtime",
    label: "运行支撑",
    icon: "Cpu",
    desc: "工作流中心、开放集成、文件存储与分布式调度中心",
    tagType: "info",
    customStyle: { color: "#0284c7", background: "#f0f9ff", borderColor: "#bae6fd" }
  },
  {
    key: "engineering",
    label: "工程交付",
    icon: "Promotion",
    desc: "低代码生成、模板治理与工程流水线资产交付",
    tagType: "warning"
  },
  {
    key: "assurance",
    label: "审计追踪",
    icon: "DocumentChecked",
    desc: "全链路审计台账、风险监控与合规追踪控制",
    tagType: "info",
    customStyle: { color: "#7c3aed", background: "#f5f3ff", borderColor: "#ddd6fe" }
  }
];

const DOMAIN_META_MAP = new Map(DOMAIN_META_LIST.map((d) => [d.key, d]));

interface MenuTreeNode extends IamMenuRow {
  children?: MenuTreeNode[];
  actionCount: number;
  buttonActions?: IamMenuRow[];
  isDomainRoot?: boolean;
  domainKey?: string;
  domainMeta?: DomainMeta;
}

interface MatrixPage {
  id: number;
  name: string;
  code: string;
  routePath?: string;
  icon?: string;
  buttons: IamMenuRow[];
}

interface MatrixModule {
  id: number;
  name: string;
  code: string;
  icon?: string;
  type: string;
  pages: MatrixPage[];
}

interface MatrixDomainGroup {
  key: string;
  label: string;
  icon: string;
  desc: string;
  modules: MatrixModule[];
}

const props = defineProps<{
  menus: IamMenuRow[];
  menuPermissions: IamMenuPermissionRow[];
  users: IamUserRow[];
  roles: IamRoleRow[];
  departments: IamDepartmentRow[];
  positions: IamPositionRow[];
  loading: boolean;
  canWrite: boolean;
  currentTenantId: number;
  openMenuCreate: (defaults?: { parentId?: number; menuType?: string; metaJson?: string }) => void;
  openMenuEdit: (row: IamMenuRow) => void;
  toggleMenu: (row: IamMenuRow) => void;
  refreshData: () => Promise<void>;
}>();

const syncingCache = ref(false);

async function handleSyncMenuCache() {
  syncingCache.value = true;
  try {
    const res = await reloadRedisCache();
    ElMessage.success(res?.message || "菜单与系统缓存已从数据库强同步重载！");
    await props.refreshData();
  } catch (err: any) {
    ElMessage.error(err?.message || "重载菜单缓存失败");
  } finally {
    syncingCache.value = false;
  }
}

const activeTab = ref<"resources" | "roles" | "exceptions">("resources");

function resolveRowDomainKey(row: IamMenuRow): string {
  let rootRow = row;
  if (row.parentId && row.parentId > 0) {
    let curr = row;
    const visited = new Set<number>();
    while (curr.parentId && curr.parentId > 0 && !visited.has(curr.id)) {
      visited.add(curr.id);
      const parent = props.menus.find((m) => m.id === curr.parentId);
      if (!parent) break;
      curr = parent;
    }
    rootRow = curr;
  }

  if (rootRow.metaJson) {
    try {
      const parsed = typeof rootRow.metaJson === "string" ? JSON.parse(rootRow.metaJson) : rootRow.metaJson;
      if (parsed?.cluster && DOMAIN_META_MAP.has(parsed.cluster)) {
        return parsed.cluster;
      }
    } catch {
      // ignore
    }
  }

  const meta = resolveAppMeta(rootRow.menuCode, rootRow.menuName);
  return meta?.cluster || "foundation";
}

function resolveRowDomain(row: IamMenuRow): DomainMeta {
  const key = resolveRowDomainKey(row);
  return DOMAIN_META_MAP.get(key) || DOMAIN_META_LIST[1];
}

function getNavPosition(row: IamMenuRow): "AUTO" | "TOP" | "SIDE" | "BOTH" {
  if (!row.metaJson) return "AUTO";
  try {
    const parsed = typeof row.metaJson === "string" ? JSON.parse(row.metaJson) : row.metaJson;
    return parsed.navPosition || "AUTO";
  } catch {
    return "AUTO";
  }
}

function getNavPositionTag(row: IamMenuRow) {
  const pos = getNavPosition(row);
  switch (pos) {
    case "TOP":
      return { label: "仅顶部", type: "warning" as const, effect: "light" as const, desc: "固定在顶部导航展示" };
    case "SIDE":
      return { label: "仅左侧", type: "primary" as const, effect: "light" as const, desc: "固定在左侧菜单树展示" };
    case "BOTH":
      return { label: "顶+侧双显", type: "success" as const, effect: "light" as const, desc: "顶部与左侧双端可见" };
    default:
      return { label: "跟随系统", type: "info" as const, effect: "plain" as const, desc: "根据全局布局模式智能呈现" };
  }
}

async function handleQuickChangeNavPosition(row: IamMenuRow, pos: string) {
  let parsed: Record<string, any> = {};
  try {
    parsed = row.metaJson ? JSON.parse(row.metaJson) : {};
  } catch {}
  parsed.navPosition = pos;
  const newMetaJson = JSON.stringify(parsed);

  try {
    await updateIamMenu(row.id, {
      tenantId: props.currentTenantId,
      parentId: row.parentId,
      menuType: row.menuType,
      menuCode: row.menuCode,
      menuName: row.menuName,
      routePath: row.routePath,
      activeMenu: row.activeMenu,
      redirect: row.redirect,
      componentPath: row.componentPath,
      componentName: row.componentName,
      permissionCode: row.permissionCode,
      icon: row.icon,
      visible: row.visible,
      keepAlive: row.keepAlive,
      isFrame: row.isFrame,
      frameSrc: row.frameSrc,
      isAffix: row.isAffix,
      sortNo: row.sortNo,
      status: row.status,
      metaJson: newMetaJson,
      remark: row.remark
    });
    const label = getNavPositionTag({ ...row, metaJson: newMetaJson }).label;
    ElMessage.success(`「${row.menuName}」显示位置已切换为「${label}」`);
    await props.refreshData();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "更新显示位置失败");
  }
}

// ================= Tab 1: 树形表格 (TreeTable) =================
const tableSearchKeyword = ref("");
const tableTypeFilter = ref("ALL");
const tableStatusFilter = ref("ALL");
const tableDomainFilter = ref<string>("ALL");
const isTableExpanded = ref(false);
const showButtonsInTree = ref(false); // 默认精简呈现目录与页面，开关开启后展开显示按钮节点
const tableRef = ref();

// 列可见性控制 (Column Visibility Control)
type MenuColumnKey =
  | "icon"
  | "domain"
  | "menuType"
  | "navPosition"
  | "routeOrPermi"
  | "componentPath"
  | "visible"
  | "status"
  | "sortNo";

interface ColumnConfig {
  key: MenuColumnKey;
  label: string;
  defaultVisible: boolean;
  desc?: string;
}

const ALL_COLUMNS: ColumnConfig[] = [
  { key: "icon", label: "图标", defaultVisible: true, desc: "菜单前置图标" },
  { key: "domain", label: "所属主域", defaultVisible: true, desc: "系统治理所属主域" },
  { key: "menuType", label: "类型", defaultVisible: true, desc: "目录 / 页面 / 按钮" },
  { key: "navPosition", label: "显示位置", defaultVisible: true, desc: "顶部导航 / 左侧菜单快捷配置" },
  { key: "routeOrPermi", label: "路由 / 权限标识", defaultVisible: true, desc: "前端访问路由或安全鉴权标识" },
  { key: "componentPath", label: "Vue 组件", defaultVisible: false, desc: "前端页面源码组件路径" },
  { key: "visible", label: "导航状态", defaultVisible: true, desc: "是否在侧边栏/顶栏直接呈现" },
  { key: "status", label: "启用状态", defaultVisible: true, desc: "资源全局启用或停用" },
  { key: "sortNo", label: "排序", defaultVisible: true, desc: "同级展示序号" }
];

const columnVisibility = reactive<Record<MenuColumnKey, boolean>>({
  icon: true,
  domain: true,
  menuType: true,
  navPosition: true,
  routeOrPermi: true,
  componentPath: false,
  visible: true,
  status: true,
  sortNo: true
});

const currentViewPreset = ref<"business" | "developer" | "custom">("business");

function applyViewPreset(preset: "business" | "developer") {
  currentViewPreset.value = preset;
  if (preset === "business") {
    columnVisibility.icon = true;
    columnVisibility.domain = true;
    columnVisibility.menuType = true;
    columnVisibility.navPosition = true;
    columnVisibility.routeOrPermi = true;
    columnVisibility.componentPath = false;
    columnVisibility.visible = true;
    columnVisibility.status = true;
    columnVisibility.sortNo = true;
  } else if (preset === "developer") {
    columnVisibility.icon = true;
    columnVisibility.domain = true;
    columnVisibility.menuType = true;
    columnVisibility.navPosition = true;
    columnVisibility.routeOrPermi = true;
    columnVisibility.componentPath = true;
    columnVisibility.visible = true;
    columnVisibility.status = true;
    columnVisibility.sortNo = true;
  }
}

function handleColumnVisibilityChange() {
  currentViewPreset.value = "custom";
}

function resetColumnVisibility() {
  applyViewPreset("business");
}

const menuStats = computed(() => {
  const total = props.menus.length;
  const directories = props.menus.filter((m) => m.menuType === "DIRECTORY").length;
  const pages = props.menus.filter((m) => ["MENU", "LINK"].includes(m.menuType)).length;
  const buttons = props.menus.filter((m) => m.menuType === "BUTTON").length;
  const enabledCount = props.menus.filter((m) => m.status === "ENABLED").length;
  const activeRate = total > 0 ? Math.round((enabledCount / total) * 100) : 100;

  const domainCounts: Record<string, number> = {
    overview: 0,
    foundation: 0,
    runtime: 0,
    engineering: 0,
    assurance: 0
  };
  const rootMenus = props.menus.filter((m) => !m.parentId || m.parentId === 0);
  for (const root of rootMenus) {
    const dKey = resolveRowDomainKey(root);
    if (domainCounts[dKey] !== undefined) {
      domainCounts[dKey]++;
    }
  }

  return { total, directories, pages, buttons, activeRate, domainCounts, totalRoots: rootMenus.length };
});

const actionsByMenu = computed(() => {
  const map = new Map<number, IamMenuRow[]>();
  for (const m of props.menus.filter((m) => m.menuType === "BUTTON")) {
    const list = map.get(m.parentId) ?? [];
    list.push(m);
    map.set(m.parentId, list);
  }
  return map;
});

const menuTreeData = computed<MenuTreeNode[]>(() => {
  const kw = tableSearchKeyword.value.trim().toLowerCase();
  const typeFilter = tableTypeFilter.value;
  const statusFilter = tableStatusFilter.value;
  const domainFilter = tableDomainFilter.value;
  const showButtons = showButtonsInTree.value;

  // Filter pool
  const pool = showButtons
    ? props.menus
    : props.menus.filter((m) => m.menuType !== "BUTTON");

  const nodeMap = new Map<number, MenuTreeNode>();
  for (const item of pool) {
    const childButtons = actionsByMenu.value.get(item.id) || [];
    nodeMap.set(item.id, {
      ...item,
      children: [],
      actionCount: childButtons.length,
      buttonActions: childButtons
    });
  }

  const roots: MenuTreeNode[] = [];
  for (const item of nodeMap.values()) {
    if (item.parentId > 0 && nodeMap.has(item.parentId)) {
      nodeMap.get(item.parentId)!.children!.push(item);
    } else {
      roots.push(item);
    }
  }

  // Filter by domainFilter if set
  const filteredRoots = roots.filter((r) => {
    if (domainFilter === "ALL") return true;
    return resolveRowDomainKey(r) === domainFilter;
  });

  const sortAndFilter = (nodes: MenuTreeNode[]): MenuTreeNode[] => {
    return nodes
      .sort((a, b) => a.sortNo - b.sortNo || a.id - b.id)
      .flatMap((node) => {
        const filteredChildren = sortAndFilter(node.children || []);
        const matchKw =
          !kw ||
          node.menuName.toLowerCase().includes(kw) ||
          node.menuCode.toLowerCase().includes(kw) ||
          (node.permissionCode && node.permissionCode.toLowerCase().includes(kw)) ||
          (node.routePath && node.routePath.toLowerCase().includes(kw));

        const matchType = typeFilter === "ALL" || node.menuType === typeFilter;
        const matchStatus = statusFilter === "ALL" || node.status === statusFilter;

        const isSelfMatch = matchKw && matchType && matchStatus;
        if (isSelfMatch || filteredChildren.length > 0) {
          return [
            {
              ...node,
              children: filteredChildren.length > 0 ? filteredChildren : undefined
            }
          ];
        }
        return [];
      });
  };

  return sortAndFilter(filteredRoots);
});

function toggleExpandAll() {
  isTableExpanded.value = !isTableExpanded.value;
  const toggle = (nodes: MenuTreeNode[], expand: boolean) => {
    for (const node of nodes) {
      tableRef.value?.toggleRowExpansion(node, expand);
      if (node.children?.length) {
        toggle(node.children, expand);
      }
    }
  };
  toggle(menuTreeData.value, isTableExpanded.value);
}

function copyText(text?: string) {
  if (!text) return;
  navigator.clipboard.writeText(text);
  ElMessage.success(`已复制: ${text}`);
}

// ================= Tab 2: 角色授权矩阵 (Enterprise Permission Matrix) =================
const roleSearchKeyword = ref("");
const roleGrantFilterKeyword = ref("");
const selectedRoleId = ref<number | null>(null);
const selectedMenuIds = ref<number[]>([]);
const checkStrictly = ref(false);
const savingRole = ref(false);
const collapsedModuleIds = ref<Set<number>>(new Set());

function toggleModuleCollapse(moduleId: number) {
  const next = new Set(collapsedModuleIds.value);
  if (next.has(moduleId)) {
    next.delete(moduleId);
  } else {
    next.add(moduleId);
  }
  collapsedModuleIds.value = next;
}

function expandAllModules() {
  collapsedModuleIds.value = new Set();
}

function collapseAllModules() {
  collapsedModuleIds.value = new Set(matrixModuleGroups.value.map((m) => m.id));
}

const enabledRoles = computed(() => {
  const kw = roleSearchKeyword.value.trim().toLowerCase();
  return props.roles
    .filter((r) => r.status === "ENABLED")
    .filter((r) => !kw || r.roleName.toLowerCase().includes(kw) || r.roleCode.toLowerCase().includes(kw));
});

const currentRole = computed(() => props.roles.find((r) => r.id === selectedRoleId.value) ?? null);

const pagesAndButtonsCount = computed(() => {
  const pages = props.menus.filter(
    (m) => ["MENU", "LINK"].includes(m.menuType) && selectedMenuIds.value.includes(m.id)
  ).length;
  const buttons = props.menus.filter(
    (m) => m.menuType === "BUTTON" && selectedMenuIds.value.includes(m.id)
  ).length;
  return { pages, buttons };
});

const matrixModuleGroups = computed<MatrixModule[]>(() => {
  const kw = roleGrantFilterKeyword.value.trim().toLowerCase();

  const topMenus = props.menus
    .filter((m) => (!m.parentId || m.parentId === 0) && m.status === "ENABLED")
    .sort((a, b) => a.sortNo - b.sortNo || a.id - b.id);

  const modules: MatrixModule[] = [];

  for (const top of topMenus) {
    let childPages: IamMenuRow[] = [];
    if (top.menuType === "DIRECTORY") {
      childPages = props.menus
        .filter(
          (m) => m.parentId === top.id && ["MENU", "LINK"].includes(m.menuType) && m.status === "ENABLED"
        )
        .sort((a, b) => a.sortNo - b.sortNo || a.id - b.id);
    } else {
      childPages = [top];
    }

    const pages: MatrixPage[] = [];
    for (const page of childPages) {
      const buttons = props.menus
        .filter((m) => m.parentId === page.id && m.menuType === "BUTTON" && m.status === "ENABLED")
        .sort((a, b) => a.sortNo - b.sortNo || a.id - b.id);

      if (kw) {
        const matchPage =
          page.menuName.toLowerCase().includes(kw) || page.menuCode.toLowerCase().includes(kw);
        const matchModule =
          top.menuName.toLowerCase().includes(kw) || top.menuCode.toLowerCase().includes(kw);
        const matchButtons = buttons.filter(
          (b) => b.menuName.toLowerCase().includes(kw) || b.menuCode.toLowerCase().includes(kw)
        );

        if (matchPage || matchModule || matchButtons.length > 0) {
          pages.push({
            id: page.id,
            name: page.menuName,
            code: page.menuCode,
            routePath: page.routePath,
            icon: page.icon,
            buttons
          });
        }
      } else {
        pages.push({
          id: page.id,
          name: page.menuName,
          code: page.menuCode,
          routePath: page.routePath,
          icon: page.icon,
          buttons
        });
      }
    }

    if (pages.length > 0) {
      modules.push({
        id: top.id,
        name: top.menuName,
        code: top.menuCode,
        icon: top.icon,
        type: top.menuType,
        pages
      });
    }
  }

  return modules;
});

const matrixDomainGroups = computed<MatrixDomainGroup[]>(() => {
  const groups: MatrixDomainGroup[] = DOMAIN_META_LIST.map((dm) => ({
    key: dm.key,
    label: dm.label,
    icon: dm.icon,
    desc: dm.desc,
    modules: []
  }));

  const groupMap = new Map(groups.map((g) => [g.key, g]));

  for (const mod of matrixModuleGroups.value) {
    const row = props.menus.find((m) => m.id === mod.id);
    const domainKey = row ? resolveRowDomainKey(row) : "foundation";
    const group = groupMap.get(domainKey) || groups[1];
    group.modules.push(mod);
  }

  // Filter out empty domains if searching
  if (roleGrantFilterKeyword.value.trim()) {
    return groups.filter((g) => g.modules.length > 0);
  }
  return groups;
});

function toggleDomainSelection(domain: MatrixDomainGroup, selected: boolean) {
  const allDomainMenuIds: number[] = [];
  for (const mod of domain.modules) {
    allDomainMenuIds.push(mod.id);
    for (const page of mod.pages) {
      allDomainMenuIds.push(page.id);
      for (const btn of page.buttons) {
        allDomainMenuIds.push(btn.id);
      }
    }
  }

  const current = new Set(selectedMenuIds.value);
  if (selected) {
    allDomainMenuIds.forEach((id) => current.add(id));
  } else {
    allDomainMenuIds.forEach((id) => current.delete(id));
  }
  selectedMenuIds.value = Array.from(current);
}

function isDomainAllSelected(domain: MatrixDomainGroup): boolean {
  const allIds: number[] = [];
  for (const mod of domain.modules) {
    allIds.push(mod.id);
    for (const page of mod.pages) {
      allIds.push(page.id);
      for (const btn of page.buttons) {
        allIds.push(btn.id);
      }
    }
  }
  return allIds.length > 0 && allIds.every((id) => selectedMenuIds.value.includes(id));
}

function isDomainIndeterminate(domain: MatrixDomainGroup): boolean {
  const allIds: number[] = [];
  for (const mod of domain.modules) {
    allIds.push(mod.id);
    for (const page of mod.pages) {
      allIds.push(page.id);
      for (const btn of page.buttons) {
        allIds.push(btn.id);
      }
    }
  }
  if (allIds.length === 0) return false;
  const count = allIds.filter((id) => selectedMenuIds.value.includes(id)).length;
  return count > 0 && count < allIds.length;
}

function isModuleAllSelected(module: MatrixModule): boolean {
  const allIds = [
    module.id,
    ...module.pages.map((p) => p.id),
    ...module.pages.flatMap((p) => p.buttons.map((b) => b.id))
  ];
  return allIds.length > 0 && allIds.every((id) => selectedMenuIds.value.includes(id));
}

function isModuleIndeterminate(module: MatrixModule): boolean {
  const allIds = [
    module.id,
    ...module.pages.map((p) => p.id),
    ...module.pages.flatMap((p) => p.buttons.map((b) => b.id))
  ];
  const count = allIds.filter((id) => selectedMenuIds.value.includes(id)).length;
  return count > 0 && count < allIds.length;
}

function toggleModuleSelection(module: MatrixModule, checked: boolean) {
  const allIds = [
    module.id,
    ...module.pages.map((p) => p.id),
    ...module.pages.flatMap((p) => p.buttons.map((b) => b.id))
  ];
  if (checked) {
    selectedMenuIds.value = Array.from(new Set([...selectedMenuIds.value, ...allIds]));
  } else {
    const idSet = new Set(allIds);
    selectedMenuIds.value = selectedMenuIds.value.filter((id) => !idSet.has(id));
  }
}

function isPageSelected(page?: MatrixPage): boolean {
  if (!page) return false;
  return selectedMenuIds.value.includes(page.id);
}

function isPageIndeterminate(page?: MatrixPage): boolean {
  if (!page || page.buttons.length === 0) return false;
  const count = page.buttons.filter((b) => selectedMenuIds.value.includes(b.id)).length;
  return count > 0 && count < page.buttons.length;
}

function togglePageSelection(page: MatrixPage, moduleId: number, checked: boolean) {
  const pageAndButtonIds = [page.id, ...page.buttons.map((b) => b.id)];
  if (checked) {
    selectedMenuIds.value = Array.from(new Set([...selectedMenuIds.value, ...pageAndButtonIds, moduleId]));
  } else {
    const idSet = new Set(pageAndButtonIds);
    selectedMenuIds.value = selectedMenuIds.value.filter((id) => !idSet.has(id));
  }
}

function toggleButtonSelection(buttonId: number, pageId: number, moduleId: number) {
  if (selectedMenuIds.value.includes(buttonId)) {
    selectedMenuIds.value = selectedMenuIds.value.filter((id) => id !== buttonId);
  } else {
    selectedMenuIds.value = Array.from(new Set([...selectedMenuIds.value, buttonId, pageId, moduleId]));
  }
}

function toggleAllPageButtons(page: MatrixPage, moduleId: number) {
  const btnIds = page.buttons.map((b) => b.id);
  const allBtnsChecked = btnIds.every((id) => selectedMenuIds.value.includes(id));
  if (allBtnsChecked) {
    const idSet = new Set(btnIds);
    selectedMenuIds.value = selectedMenuIds.value.filter((id) => !idSet.has(id));
  } else {
    selectedMenuIds.value = Array.from(new Set([...selectedMenuIds.value, ...btnIds, page.id, moduleId]));
  }
}

function getModuleSelectedPageCount(module: MatrixModule): number {
  return module.pages.filter((p) => selectedMenuIds.value.includes(p.id)).length;
}

function getModuleSelectedButtonCount(module: MatrixModule): number {
  return module.pages.flatMap((p) => p.buttons).filter((b) => selectedMenuIds.value.includes(b.id)).length;
}

function getModuleTotalButtonCount(module: MatrixModule): number {
  return module.pages.flatMap((p) => p.buttons).length;
}

function getRoleAssignedCount(roleId: number): number {
  return props.menuPermissions.filter(
    (p) => p.subjectType === "ROLE" && p.subjectValue === String(roleId) && p.status === "ENABLED"
  ).length;
}

watch(
  enabledRoles,
  (roles) => {
    if (!selectedRoleId.value && roles.length) {
      selectedRoleId.value = roles[0].id;
    }
  },
  { immediate: true }
);

watch(
  selectedRoleId,
  async (roleId) => {
    if (!roleId) {
      selectedMenuIds.value = [];
      return;
    }
    try {
      const ids = await fetchRoleMenuIds(roleId);
      selectedMenuIds.value = ids || [];
    } catch {
      const roleGrants = props.menuPermissions.filter(
        (p) => p.subjectType === "ROLE" && p.subjectValue === String(roleId) && p.status === "ENABLED"
      );
      selectedMenuIds.value = roleGrants.map((g) => g.menuId);
    }
  },
  { immediate: true }
);

function toggleSelectAllRoleMenus(checkAll: boolean) {
  if (checkAll) {
    selectedMenuIds.value = props.menus.filter((m) => m.status === "ENABLED").map((m) => m.id);
  } else {
    selectedMenuIds.value = [];
  }
}

function selectReadOnlyRoleMenus() {
  const readKeywords = ["view", "list", "query", "export", "detail", "get", "info", "查", "看", "导", "详", "阅"];
  const targetIds: number[] = [];

  for (const m of props.menus) {
    if (m.status !== "ENABLED") continue;
    if (m.menuType === "DIRECTORY" || m.menuType === "MENU" || m.menuType === "LINK") {
      targetIds.push(m.id);
    } else if (m.menuType === "BUTTON") {
      const isRead = readKeywords.some(
        (k) => m.menuCode.toLowerCase().includes(k) || m.menuName.toLowerCase().includes(k)
      );
      if (isRead) {
        targetIds.push(m.id);
      }
    }
  }
  selectedMenuIds.value = Array.from(new Set(targetIds));
  ElMessage.success("已快速勾选只读/查询类权限");
}

async function saveRoleGrants() {
  if (!selectedRoleId.value) return;
  savingRole.value = true;
  try {
    await replaceRoleMenus(selectedRoleId.value, selectedMenuIds.value);
    await props.refreshData();
    ElMessage.success(`角色「${currentRole.value?.roleName}」原子权限已保存！`);
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "保存角色权限失败");
  } finally {
    savingRole.value = false;
  }
}

// ================= Tab 3: 例外授权 (Exceptions) =================
const exceptionDialog = reactive({ visible: false, mode: "create" as "create" | "edit", id: 0 });
const exceptionForm = reactive({
  menuId: 0,
  subjectType: "USER" as SubjectType,
  subjectValue: "",
  actionCodes: [] as string[],
  status: "ENABLED",
  remark: ""
});

const exceptionRows = computed(() =>
  props.menuPermissions
    .filter((row) => row.subjectType !== "ROLE")
    .map((row) => ({
      ...row,
      menuName: props.menus.find((item) => item.id === row.menuId)?.menuName ?? row.menuCode,
      subjectLabel: resolveSubjectLabel(row.subjectType, row.subjectValue),
      accessSummary: summarizeActions(row.menuId, row.buttonCodesJson)
    }))
);

const exceptionColumns: ProTableColumn[] = [
  { prop: "subjectLabel", label: "授权对象", minWidth: 180 },
  { prop: "subjectType", label: "对象类型", minWidth: 110, formatter: (row) => localizeSubjectType(String(row.subjectType ?? "")) },
  { prop: "menuName", label: "可访问页面", minWidth: 180 },
  { prop: "accessSummary", label: "细粒度操作", minWidth: 240 },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" },
  { prop: "actions", label: "操作", minWidth: 160, slot: "actions" }
];

function resolveSubjectLabel(subjectType: string, subjectValue: string) {
  switch (subjectType) {
    case "USER":
      return props.users.find((item) => String(item.id) === subjectValue)?.nickname ?? subjectValue;
    case "DEPARTMENT":
      return props.departments.find((item) => String(item.id) === subjectValue)?.deptFullName ?? subjectValue;
    case "POSITION":
      return props.positions.find((item) => String(item.id) === subjectValue)?.positionName ?? subjectValue;
    case "COMPANY":
      return props.departments.find((item) => String(item.id) === subjectValue)?.deptName ?? subjectValue;
    case "TENANT":
      return "当前租户";
    default:
      return subjectValue;
  }
}

function parseActionCodes(buttonCodesJson?: string) {
  if (!buttonCodesJson) return [];
  try {
    const list = JSON.parse(buttonCodesJson);
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

function summarizeActions(menuId: number, buttonCodesJson?: string) {
  const codes = parseActionCodes(buttonCodesJson);
  if (!codes.length) return "仅基础页面访问";
  return codes
    .map((code) => actionsByMenu.value.get(menuId)?.find((a) => a.menuCode === code)?.menuName ?? code)
    .join("、");
}

function openExceptionCreate() {
  exceptionDialog.mode = "create";
  exceptionDialog.id = 0;
  exceptionForm.menuId = props.menus.find((m) => m.menuType === "MENU")?.id ?? 0;
  exceptionForm.subjectType = "USER";
  exceptionForm.subjectValue = String(props.users[0]?.id ?? "");
  exceptionForm.actionCodes = [];
  exceptionForm.status = "ENABLED";
  exceptionForm.remark = "";
  exceptionDialog.visible = true;
}

function openExceptionEdit(row: IamMenuPermissionRow) {
  exceptionDialog.mode = "edit";
  exceptionDialog.id = row.id;
  exceptionForm.menuId = row.menuId;
  exceptionForm.subjectType = row.subjectType as SubjectType;
  exceptionForm.subjectValue = row.subjectValue;
  exceptionForm.actionCodes = parseActionCodes(row.buttonCodesJson);
  exceptionForm.status = row.status;
  exceptionForm.remark = row.remark ?? "";
  exceptionDialog.visible = true;
}

async function saveException() {
  if (!exceptionForm.subjectValue || !exceptionForm.menuId) {
    ElMessage.error("请选择授权对象和页面");
    return;
  }
  const selectedMenu = props.menus.find((item) => item.id === exceptionForm.menuId);
  if (!selectedMenu) return;
  const payload = {
    tenantId: props.currentTenantId,
    menuId: selectedMenu.id,
    menuCode: selectedMenu.menuCode,
    subjectType: exceptionForm.subjectType,
    subjectValue: exceptionForm.subjectValue,
    buttonCodesJson: JSON.stringify(exceptionForm.actionCodes),
    status: exceptionForm.status,
    remark: exceptionForm.remark || undefined
  };
  if (exceptionDialog.mode === "create") {
    await createIamMenuPermission(payload);
  } else {
    await updateIamMenuPermission(exceptionDialog.id, payload);
  }
  exceptionDialog.visible = false;
  await props.refreshData();
  ElMessage.success("例外授权已成功保存");
}

async function toggleException(row: IamMenuPermissionRow) {
  await updateIamMenuPermissionStatus(row.id, { status: row.status === "ENABLED" ? "DISABLED" : "ENABLED" });
  await props.refreshData();
}

function resolveMenuTypeTag(type: string) {
  switch (type) {
    case "DIRECTORY":
      return { label: "目录", type: "primary" as const };
    case "MENU":
      return { label: "页面", type: "success" as const };
    case "BUTTON":
      return { label: "按钮操作", type: "warning" as const };
    case "LINK":
      return { label: "外部链接", type: "info" as const };
    default:
      return { label: type, type: "info" as const };
  }
}
</script>

<template>
  <section class="menu-hub">
    <el-tabs v-model="activeTab" class="menu-hub__tabs">
      <!-- TAB 1: 树形表格 (TreeTable) -->
      <el-tab-pane label="菜单与按钮目录" name="resources">
        <!-- 业务主域快速切换沉浸式标签栏 -->
        <div class="menu-domain-tabs-wrapper">
          <div class="domain-segmented-bar">
            <button
              class="domain-seg-btn"
              :class="{ 'is-active': tableDomainFilter === 'ALL' }"
              @click="tableDomainFilter = 'ALL'"
            >
              <el-icon class="seg-icon"><Operation /></el-icon>
              <span>全部主域</span>
              <span class="seg-count">{{ menuStats.totalRoots }}</span>
            </button>
            <button
              v-for="domain in DOMAIN_META_LIST"
              :key="domain.key"
              class="domain-seg-btn"
              :class="{ 'is-active': tableDomainFilter === domain.key, [`domain--${domain.key}`]: true }"
              @click="tableDomainFilter = domain.key"
            >
              <el-icon class="seg-icon"><component :is="domain.icon" /></el-icon>
              <span>{{ domain.label }}</span>
              <span class="seg-count">{{ menuStats.domainCounts[domain.key] || 0 }}</span>
            </button>
          </div>
        </div>

        <BaseCard class="treetable-card">
          <template #header>
            <div class="table-toolbar">
              <div class="toolbar-left">
                <el-input
                  v-model="tableSearchKeyword"
                  placeholder="搜索名称 / 标识 / 路由 / 权限码..."
                  :prefix-icon="Search"
                  clearable
                  style="width: 250px"
                />
                <el-select v-model="tableTypeFilter" style="width: 110px">
                  <el-option label="全部类型" value="ALL" />
                  <el-option label="目录" value="DIRECTORY" />
                  <el-option label="页面" value="MENU" />
                  <el-option label="按钮" value="BUTTON" />
                  <el-option label="外链" value="LINK" />
                </el-select>
                <el-select v-model="tableStatusFilter" style="width: 100px">
                  <el-option label="全部状态" value="ALL" />
                  <el-option label="启用" value="ENABLED" />
                  <el-option label="停用" value="DISABLED" />
                </el-select>
                <el-switch
                  v-model="showButtonsInTree"
                  active-text="平铺展示按钮行"
                  inactive-text="仅看目录与页面"
                  style="margin-left: 6px"
                />
              </div>

              <div class="toolbar-right">
                <!-- 企业级视图模式切换 -->
                <el-radio-group v-model="currentViewPreset" size="small" @change="(val: any) => applyViewPreset(val)">
                  <el-radio-button value="business">业务视图</el-radio-button>
                  <el-radio-button value="developer">研发全景</el-radio-button>
                </el-radio-group>

                <!-- 列设置 -->
                <el-popover placement="bottom-end" :width="240" trigger="click">
                  <template #reference>
                    <el-button :icon="Operation" size="small">列设置</el-button>
                  </template>
                  <div class="column-settings-popover">
                    <div class="popover-header">
                      <span>表格展示列</span>
                      <el-button link type="primary" size="small" @click="resetColumnVisibility">重置</el-button>
                    </div>
                    <el-divider style="margin: 8px 0" />
                    <div class="popover-body">
                      <div v-for="col in ALL_COLUMNS" :key="col.key" class="col-item">
                        <el-checkbox
                          v-model="columnVisibility[col.key]"
                          @change="handleColumnVisibilityChange"
                        >
                          <span class="col-item-label">{{ col.label }}</span>
                        </el-checkbox>
                      </div>
                    </div>
                  </div>
                </el-popover>

                <el-button link :icon="isTableExpanded ? Fold : Expand" @click="toggleExpandAll">
                  {{ isTableExpanded ? "全部折叠" : "全部展开" }}
                </el-button>
                <el-button
                  v-if="canWrite"
                  type="primary"
                  :icon="Plus"
                  @click="openMenuCreate({ menuType: 'DIRECTORY' })"
                >新增顶级目录</el-button>
                <el-button
                  v-if="canWrite"
                  type="primary"
                  plain
                  :icon="Plus"
                  @click="openMenuCreate({ menuType: 'MENU' })"
                >新增顶级页面</el-button>
                <el-button :icon="Refresh" @click="props.refreshData">刷新</el-button>
                <el-button
                  v-if="canWrite"
                  type="warning"
                  plain
                  :icon="Refresh"
                  :loading="syncingCache"
                  @click="handleSyncMenuCache"
                >
                  重载缓存 (Sync DB)
                </el-button>
              </div>
            </div>
          </template>

          <el-table
            ref="tableRef"
            :data="menuTreeData"
            row-key="id"
            :tree-props="{ children: 'children' }"
            :default-expand-all="false"
            v-loading="loading"
            class="enterprise-treetable"
            stripe
          >
            <!-- 资源名称 -->
            <el-table-column prop="menuName" label="资源名称" min-width="260">
              <template #default="{ row }">
                <span class="resource-name-cell">
                  <el-icon v-if="row.menuType === 'DIRECTORY'" class="type-icon type-icon--dir"><Folder /></el-icon>
                  <el-icon v-else-if="row.menuType === 'MENU'" class="type-icon type-icon--menu"><View /></el-icon>
                  <el-icon v-else-if="row.menuType === 'BUTTON'" class="type-icon type-icon--btn"><Key /></el-icon>
                  <el-icon v-else class="type-icon type-icon--link"><Link /></el-icon>
                  <strong class="resource-name-text">{{ row.menuName }}</strong>
                  <span v-if="row.menuCode" class="resource-code-hint">{{ row.menuCode }}</span>
                  <span v-if="row.actionCount && !showButtonsInTree" class="action-count-badge" title="该页面包含的操作权限项数量">
                    {{ row.actionCount }} 权限项
                  </span>
                </span>
              </template>
            </el-table-column>

            <!-- 图标列 -->
            <el-table-column v-if="columnVisibility.icon" label="图标" width="60" align="center">
              <template #default="{ row }">
                <el-icon v-if="row.icon" class="table-custom-icon" :title="`图标: ${row.icon}`">
                  <component :is="row.icon" />
                </el-icon>
                <span v-else class="text-placeholder">-</span>
              </template>
            </el-table-column>

            <!-- 所属业务主域 -->
            <el-table-column v-if="columnVisibility.domain" label="所属主域" width="105" align="center">
              <template #default="{ row }">
                <span v-if="row.menuType !== 'BUTTON'" class="domain-subtle-tag" :class="`domain-subtle--${resolveRowDomainKey(row)}`">
                  {{ resolveRowDomain(row)?.label }}
                </span>
                <span v-else class="text-placeholder">-</span>
              </template>
            </el-table-column>

            <!-- 类型 -->
            <el-table-column v-if="columnVisibility.menuType" prop="menuType" label="类型" width="85" align="center">
              <template #default="{ row }">
                <el-tag :type="resolveMenuTypeTag(row.menuType).type" size="small" effect="plain" class="type-badge-plain">
                  {{ resolveMenuTypeTag(row.menuType).label }}
                </el-tag>
              </template>
            </el-table-column>

            <!-- 导航显示位置 (独立控制在顶部还是左侧) -->
            <el-table-column v-if="columnVisibility.navPosition" label="显示位置" width="130" align="center">
              <template #default="{ row }">
                <el-dropdown
                  v-if="canWrite && row.menuType !== 'BUTTON'"
                  trigger="click"
                  @command="(pos: string) => handleQuickChangeNavPosition(row, pos)"
                >
                  <span
                    class="nav-position-badge"
                    :class="`nav-pos-${getNavPosition(row).toLowerCase()}`"
                    title="点击快捷切换此功能在顶部或左侧的显示位置"
                  >
                    <span>{{ getNavPositionTag(row).label }}</span>
                    <el-icon class="pos-arrow"><ArrowDown /></el-icon>
                  </span>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="AUTO">
                        <span style="font-weight: 500">跟随系统 (AUTO) - 默认</span>
                      </el-dropdown-item>
                      <el-dropdown-item command="TOP">
                        <span style="color: #d97706; font-weight: 500">仅在顶部导航 (TOP)</span>
                      </el-dropdown-item>
                      <el-dropdown-item command="SIDE">
                        <span style="color: #2563eb; font-weight: 500">仅在左侧菜单 (SIDE)</span>
                      </el-dropdown-item>
                      <el-dropdown-item command="BOTH">
                        <span style="color: #059669; font-weight: 500">顶部与左侧双显 (BOTH)</span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <span
                  v-else-if="row.menuType !== 'BUTTON'"
                  class="nav-position-badge"
                  :class="`nav-pos-${getNavPosition(row).toLowerCase()}`"
                >
                  <span>{{ getNavPositionTag(row).label }}</span>
                </span>
                <span v-else class="text-placeholder">-</span>
              </template>
            </el-table-column>

            <!-- 访问路由 / 权限标识 -->
            <el-table-column v-if="columnVisibility.routeOrPermi" label="路由 / 权限标识" min-width="190">
              <template #default="{ row }">
                <code v-if="row.routePath" class="code-pill" :title="row.routePath">{{ row.routePath }}</code>
                <button
                  v-else-if="row.permissionCode || (row.menuType === 'BUTTON' && row.menuCode)"
                  class="permi-code-btn"
                  title="点击复制权限标识"
                  @click="copyText(row.permissionCode || row.menuCode)"
                >
                  <span>{{ row.permissionCode || row.menuCode }}</span>
                  <el-icon><CopyDocument /></el-icon>
                </button>
                <span v-else class="text-placeholder">-</span>
              </template>
            </el-table-column>

            <!-- 组件路径 & 名称 (研发视图) -->
            <el-table-column v-if="columnVisibility.componentPath" prop="componentPath" label="Vue 组件" min-width="160">
              <template #default="{ row }">
                <span v-if="row.componentPath" class="comp-path" :title="row.componentPath">
                  {{ row.componentPath.split('/').pop() }}
                </span>
                <span v-else class="text-placeholder">-</span>
              </template>
            </el-table-column>

            <!-- 导航可见 -->
            <el-table-column v-if="columnVisibility.visible" prop="visible" label="导航" width="70" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.menuType !== 'BUTTON'" size="small" :type="row.visible ? 'success' : 'info'" effect="light">
                  {{ row.visible ? "显示" : "隐藏" }}
                </el-tag>
                <span v-else class="text-placeholder">-</span>
              </template>
            </el-table-column>

            <!-- 状态 -->
            <el-table-column v-if="columnVisibility.status" prop="status" label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.status === 'ENABLED'"
                  :disabled="!canWrite"
                  size="small"
                  @change="() => toggleMenu(row)"
                />
              </template>
            </el-table-column>

            <!-- 排序 -->
            <el-table-column v-if="columnVisibility.sortNo" prop="sortNo" label="排序" width="55" align="center" />

            <!-- 操作 -->
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <div class="table-action-cell">
                  <el-button
                    v-if="canWrite && row.menuType === 'DIRECTORY'"
                    link
                    type="primary"
                    size="small"
                    @click="openMenuCreate({ parentId: row.id, menuType: 'MENU' })"
                  >+ 页面</el-button>

                  <el-button
                    v-if="canWrite && row.menuType === 'MENU'"
                    link
                    type="primary"
                    size="small"
                    @click="openMenuCreate({ parentId: row.id, menuType: 'BUTTON' })"
                  >+ 按钮</el-button>

                  <el-button
                    v-if="canWrite"
                    link
                    type="primary"
                    size="small"
                    @click="openMenuEdit(row)"
                  >编辑</el-button>

                  <el-dropdown v-if="canWrite" trigger="click">
                    <el-button link type="primary" size="small" class="action-more-btn">
                      更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item
                          :class="row.status === 'ENABLED' ? 'action-danger-text' : 'action-success-text'"
                          @click="toggleMenu(row)"
                        >
                          <el-icon v-if="row.status === 'ENABLED'"><CircleClose /></el-icon>
                          <el-icon v-else><CircleCheck /></el-icon>
                          <span>{{ row.status === 'ENABLED' ? '停用资源' : '启用资源' }}</span>
                        </el-dropdown-item>
                        <el-dropdown-item
                          v-if="row.permissionCode || row.menuCode"
                          :divided="true"
                          @click="copyText(row.permissionCode || row.menuCode)"
                        >
                          <el-icon><CopyDocument /></el-icon>
                          <span>复制权限标识</span>
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </BaseCard>
      </el-tab-pane>

      <!-- TAB 2: 企业级功能与权限矩阵 (Enterprise Permission Matrix) -->
      <el-tab-pane label="角色授权分配" name="roles">
        <div class="role-matrix-layout">
          <aside class="role-sidebar">
            <div class="role-sidebar__header">
              <div class="role-sidebar-title-row">
                <strong>系统角色 ({{ enabledRoles.length }})</strong>
                <el-tag size="small" type="primary">RBAC</el-tag>
              </div>
              <el-input
                v-model="roleSearchKeyword"
                placeholder="过滤角色名称/编码..."
                clearable
                size="small"
                :prefix-icon="Search"
                style="margin-top: 8px"
              />
            </div>
            <div class="role-sidebar__list">
              <button
                v-for="role in enabledRoles"
                :key="role.id"
                class="role-item-btn"
                :class="{ 'is-active': selectedRoleId === role.id }"
                @click="selectedRoleId = role.id"
              >
                <div class="role-item-info">
                  <div class="role-name">{{ role.roleName }}</div>
                  <div class="role-code">{{ role.roleCode }}</div>
                </div>
                <div class="role-item-meta">
                  <span class="role-perm-badge">
                    {{ getRoleAssignedCount(role.id) }} 项
                  </span>
                  <el-icon><ArrowRight /></el-icon>
                </div>
              </button>
            </div>
          </aside>

          <BaseCard class="role-matrix-panel">
            <template #header>
              <div class="matrix-header">
                <div class="matrix-header-info">
                  <strong class="role-title">{{ currentRole?.roleName ?? "选择角色" }}</strong>
                  <el-tag size="small" type="primary" style="margin-left: 8px">{{ currentRole?.roleCode }}</el-tag>
                  <span class="matrix-stat-text">
                    已勾选 {{ pagesAndButtonsCount.pages }} 个页面，{{ pagesAndButtonsCount.buttons }} 个细粒度按钮操作
                  </span>
                </div>
                <div class="matrix-header__actions">
                  <el-button
                    v-if="canWrite"
                    type="primary"
                    :loading="savingRole"
                    :icon="Check"
                    @click="saveRoleGrants"
                  >保存角色授权</el-button>
                </div>
              </div>
            </template>

            <!-- 快捷操作控制栏 -->
            <div class="matrix-quick-bar">
              <div class="quick-left">
                <el-button size="small" type="primary" plain @click="toggleSelectAllRoleMenus(true)">全选全部</el-button>
                <el-button size="small" @click="selectReadOnlyRoleMenus">一键只读权限</el-button>
                <el-button size="small" @click="toggleSelectAllRoleMenus(false)">清空勾选</el-button>
                <el-button size="small" @click="expandAllModules">展开全部模块</el-button>
                <el-button size="small" @click="collapseAllModules">折叠全部模块</el-button>
              </div>
              <div class="quick-right">
                <el-input
                  v-model="roleGrantFilterKeyword"
                  placeholder="按模块/页面/操作关键字快速过滤..."
                  clearable
                  size="small"
                  :prefix-icon="Search"
                  style="width: 260px"
                />
              </div>
            </div>

            <!-- 企业级按主域分组卡片流 -->
            <div class="matrix-domain-stream">
              <div
                v-for="domain in matrixDomainGroups"
                :key="domain.key"
                class="matrix-domain-group"
              >
                <!-- 主域分段标头 -->
                <div class="matrix-domain-header">
                  <div class="domain-header-left">
                    <el-icon class="domain-header-icon"><component :is="domain.icon" /></el-icon>
                    <strong class="domain-header-title">{{ domain.label }}</strong>
                    <span class="domain-header-desc">{{ domain.desc }}</span>
                    <span class="domain-header-pill">{{ domain.modules.length }} 个业务模块</span>
                  </div>
                  <div class="domain-header-right">
                    <el-checkbox
                      :model-value="isDomainAllSelected(domain)"
                      :indeterminate="isDomainIndeterminate(domain)"
                      @change="(val) => toggleDomainSelection(domain, !!val)"
                    >
                      全选本主域
                    </el-checkbox>
                  </div>
                </div>

                <!-- 模块卡片列表 -->
                <div class="matrix-cards-container">
                  <div
                    v-for="module in domain.modules"
                    :key="module.id"
                    class="matrix-module-card"
                  >
                    <!-- 模块卡片头部 -->
                    <div class="module-card-header">
                      <div class="module-title-box" @click="toggleModuleCollapse(module.id)" style="cursor: pointer; user-select: none;">
                        <el-icon class="module-collapse-arrow" style="font-size: 13px; color: #64748b; margin-right: 2px;">
                          <ArrowRight v-if="collapsedModuleIds.has(module.id)" />
                          <ArrowDown v-else />
                        </el-icon>
                        <el-icon class="module-icon">
                          <component :is="module.icon || (module.type === 'DIRECTORY' ? 'Folder' : 'View')" />
                        </el-icon>
                        <strong class="module-name">{{ module.name }}</strong>
                        <el-tag size="small" type="info" effect="plain" class="module-code">{{ module.code }}</el-tag>
                        <span class="module-collapse-tip" style="font-size: 11px; color: #94a3b8; margin-left: 4px;">
                          {{ collapsedModuleIds.has(module.id) ? '(已折叠，点击展开)' : '' }}
                        </span>
                      </div>
                      <div class="module-ctrl-box">
                        <span class="module-counter-pill">
                          已选 {{ getModuleSelectedPageCount(module) }}/{{ module.pages.length }} 页面 ·
                          {{ getModuleSelectedButtonCount(module) }}/{{ getModuleTotalButtonCount(module) }} 按钮
                        </span>
                        <el-checkbox
                          :model-value="isModuleAllSelected(module)"
                          :indeterminate="isModuleIndeterminate(module)"
                          @change="(val) => toggleModuleSelection(module, !!val)"
                        >
                          本模块全选
                        </el-checkbox>
                      </div>
                    </div>

                    <!-- 模块内页面与按钮矩阵列表 -->
                    <div class="module-card-body" v-show="!collapsedModuleIds.has(module.id)">
                      <div
                        v-for="page in module.pages"
                        :key="page.id"
                        class="matrix-page-row"
                        :class="{ 'is-page-checked': isPageSelected(page) }"
                      >
                        <!-- 页面主选择项 (左侧) -->
                        <div class="page-access-col">
                          <el-checkbox
                            :model-value="isPageSelected(page)"
                            :indeterminate="isPageIndeterminate(page)"
                            @change="(val) => togglePageSelection(page, module.id, !!val)"
                          >
                            <span class="page-title">{{ page.name }}</span>
                          </el-checkbox>
                          <div class="page-meta-tags">
                            <small class="page-code-tag">{{ page.code }}</small>
                            <small v-if="page.routePath" class="page-route-tag">{{ page.routePath }}</small>
                          </div>
                        </div>

                        <!-- 按钮操作横向芯片组 (右侧，解决纵向挤压) -->
                        <div class="action-chips-col">
                          <div v-if="page.buttons.length" class="action-chips-wrap">
                            <button
                              type="button"
                              class="select-all-page-btn"
                              @click="toggleAllPageButtons(page, module.id)"
                            >
                              {{ page.buttons.every((b) => selectedMenuIds.includes(b.id)) ? "本页全清" : "本页全选" }}
                            </button>

                            <button
                              v-for="btn in page.buttons"
                              :key="btn.id"
                              type="button"
                              class="action-chip"
                              :class="{ 'is-selected': selectedMenuIds.includes(btn.id) }"
                              @click="toggleButtonSelection(btn.id, page.id, module.id)"
                            >
                              <el-icon v-if="selectedMenuIds.includes(btn.id)" class="chip-check"><Check /></el-icon>
                              <span class="chip-text">{{ btn.menuName }}</span>
                              <small class="chip-code">{{ btn.menuCode }}</small>
                            </button>
                          </div>
                          <div v-else class="no-actions-indicator">
                            <span>基础页面访问权限 (无独立细粒度操作)</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="matrixDomainGroups.length === 0" class="empty-matrix-hint">
                <p>未找到符合过滤条件的模块或功能页面</p>
              </div>
            </div>
          </BaseCard>
        </div>
      </el-tab-pane>

      <!-- TAB 3: 例外与高级补充授权 -->
      <el-tab-pane label="例外与特定授权" name="exceptions">
        <ProTable
          :columns="exceptionColumns"
          :data="exceptionRows"
          :loading="loading"
          compact
          title="例外主体补充授权"
        >
          <template #toolbar>
            <el-button v-if="canWrite" type="primary" :icon="Plus" @click="openExceptionCreate">新增例外授权</el-button>
          </template>
          <template #status="{ row }">
            <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
          </template>
          <template #actions="{ row }">
            <el-button link type="primary" @click="openExceptionEdit(row)">编辑</el-button>
            <el-button link @click="toggleException(row)">{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
          </template>
        </ProTable>
      </el-tab-pane>
    </el-tabs>

    <!-- 例外授权编辑弹窗 -->
    <el-dialog
      v-model="exceptionDialog.visible"
      :title="exceptionDialog.mode === 'create' ? '新增例外授权' : '编辑例外授权'"
      width="620px"
    >
      <el-form label-position="top">
        <el-form-item label="对象类型">
          <el-select v-model="exceptionForm.subjectType">
            <el-option label="指定用户" value="USER" />
            <el-option label="指定部门" value="DEPARTMENT" />
            <el-option label="指定岗位" value="POSITION" />
            <el-option label="指定公司" value="COMPANY" />
            <el-option label="全租户" value="TENANT" />
          </el-select>
        </el-form-item>
        <el-form-item label="可访问页面">
          <el-select v-model="exceptionForm.menuId" filterable style="width: 100%">
            <el-option
              v-for="item in props.menus.filter((m) => ['MENU', 'LINK'].includes(m.menuType))"
              :key="item.id"
              :label="item.menuName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="细粒度操作">
          <el-checkbox-group v-model="exceptionForm.actionCodes">
            <el-checkbox
              v-for="btn in actionsByMenu.get(exceptionForm.menuId) || []"
              :key="btn.id"
              :value="btn.menuCode"
            >{{ btn.menuName }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="exceptionForm.status">
            <el-radio-button label="启用" value="ENABLED" />
            <el-radio-button label="停用" value="DISABLED" />
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注说明">
          <el-input v-model="exceptionForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exceptionDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveException">保存例外授权</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.menu-hub {
  min-width: 0;

  :deep(.el-tabs__header) {
    margin: 0 0 16px;
    padding: 0 4px;
    background: #fff;
    border: 1px solid var(--sb-border-color);
    border-radius: var(--sb-radius-md);
  }

  :deep(.el-tabs__nav-wrap::after) {
    display: none;
  }
}

/* 顶部统计卡片 */

/* 业务主域快速切换沉浸式分段标签 */
.menu-domain-tabs-wrapper {
  margin-bottom: 14px;

  .domain-segmented-bar {
    display: inline-flex;
    align-items: center;
    background: #f1f5f9;
    padding: 3px;
    border-radius: 8px;
    gap: 3px;
    border: 1px solid #e2e8f0;

    .domain-seg-btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 14px;
      border-radius: 6px;
      border: none;
      background: transparent;
      color: #64748b;
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s ease;

      .seg-icon {
        font-size: 14px;
        opacity: 0.8;
      }

      .seg-count {
        font-size: 11px;
        font-weight: 600;
        padding: 0 6px;
        border-radius: 9999px;
        background: rgb(148 163 184 / 0.25);
        color: #475569;
        line-height: 16px;
      }

      &:hover {
        color: #1e293b;
        background: rgb(255 255 255 / 0.6);
      }

      &.is-active {
        background: #fff;
        color: #2563eb;
        font-weight: 600;
        box-shadow: 0 1px 3px rgb(0 0 0 / 0.08), 0 1px 2px rgb(0 0 0 / 0.04);

        .seg-icon {
          opacity: 1;
          color: #2563eb;
        }

        .seg-count {
          background: #eff6ff;
          color: #2563eb;
        }
      }
    }
  }
}

/* TreeTable */
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;

  .toolbar-left,
  .toolbar-right {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }
}

.enterprise-treetable {
  width: 100%;

  .resource-name-cell {
    display: inline-flex;
    align-items: center;
    gap: 8px;

    .type-icon {
      font-size: 16px;
      flex-shrink: 0;

      &--dir { color: #2563eb; }
      &--menu { color: #10b981; }
      &--btn { color: #f59e0b; }
      &--link { color: #8b5cf6; }
    }

    .resource-name-text {
      font-size: 13.5px;
      color: #0f172a;
      font-weight: 600;
      white-space: nowrap;
    }

    .resource-code-hint {
      font-family: var(--sb-font-mono, monospace);
      font-size: 11px;
      color: #94a3b8;
      background: #f8fafc;
      padding: 1px 6px;
      border-radius: 4px;
      border: 1px solid #f1f5f9;
    }

    .action-count-badge {
      font-size: 10.5px;
      padding: 1px 7px;
      border-radius: 9999px;
      background: #f0fdf4;
      border: 1px solid #bbf7d0;
      color: #16a34a;
      font-weight: 500;
      white-space: nowrap;
    }
  }

  .table-custom-icon {
    font-size: 16px;
    color: #475569;
  }

  .domain-subtle-tag {
    font-size: 11.5px;
    padding: 2px 8px;
    border-radius: 4px;
    font-weight: 500;
    white-space: nowrap;
    background: #f1f5f9;
    color: #475569;
    border: 1px solid #e2e8f0;

    &.domain-subtle--overview {
      background: #f0fdf4;
      color: #166534;
      border-color: #bbf7d0;
    }

    &.domain-subtle--foundation {
      background: #eff6ff;
      color: #1e40af;
      border-color: #bfdbfe;
    }

    &.domain-subtle--runtime {
      background: #f0f9ff;
      color: #0369a1;
      border-color: #bae6fd;
    }

    &.domain-subtle--engineering {
      background: #fffbeb;
      color: #92400e;
      border-color: #fde68a;
    }

    &.domain-subtle--assurance {
      background: #faf5ff;
      color: #6b21a8;
      border-color: #e9d5ff;
    }
  }

  .type-badge-plain {
    font-size: 11.5px;
    font-weight: 500;
  }

  .text-placeholder {
    color: #cbd5e1;
    font-size: 12px;
  }

  .code-pill {
    display: inline-block;
    max-width: 240px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-family: var(--sb-font-mono, monospace);
    font-size: 11.5px;
    padding: 2px 6px;
    border-radius: 4px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    color: #334155;
  }

  .permi-code-btn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-family: var(--sb-font-mono, monospace);
    font-size: 11.5px;
    padding: 2px 6px;
    border-radius: 4px;
    background: #fffbeb;
    border: 1px solid #fde68a;
    color: #b45309;
    cursor: pointer;
    transition: all 0.15s ease;

    &:hover {
      background: #fef3c7;
      color: #92400e;
    }
  }

  .comp-path {
    font-size: 12px;
    color: #64748b;
    font-family: var(--sb-font-mono, monospace);
  }
}

/* Role Matrix Layout */
.role-matrix-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
}

.role-sidebar {
  background: #fff;
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-md);
  overflow: hidden;

  &__header {
    padding: 14px;
    border-bottom: 1px solid var(--sb-border-color);

    .role-sidebar-title-row {
      display: flex;
      align-items: center;
      justify-content: space-between;

      strong {
        font-size: 14px;
        color: #0f172a;
      }
    }
  }

  &__list {
    max-height: calc(100vh - 280px);
    overflow-y: auto;
  }
}

.role-item-btn {
  width: 100%;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 0;
  border-bottom: 1px solid #f1f5f9;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;

  .role-item-info {
    min-width: 0;
    flex: 1;

    .role-name {
      font-size: 13.5px;
      font-weight: 600;
      color: #1e293b;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .role-code {
      font-size: 11.5px;
      color: #64748b;
      font-family: monospace;
      margin-top: 2px;
    }
  }

  .role-item-meta {
    display: flex;
    align-items: center;
    gap: 6px;

    .role-perm-badge {
      padding: 1px 6px;
      border-radius: 9999px;
      background: #f1f5f9;
      color: #64748b;
      font-size: 10.5px;
      font-weight: 600;
    }
  }

  &:hover {
    background: #f8fafc;
  }

  &.is-active {
    background: #eff6ff;
    border-left: 3px solid #2563eb;

    .role-name {
      color: #2563eb;
    }

    .role-perm-badge {
      background: #dbeafe;
      color: #1d4ed8;
    }
  }
}

.role-matrix-panel {
  min-width: 0;

  .matrix-header {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .matrix-header-info {
      display: flex;
      align-items: center;
      gap: 8px;

      .role-title {
        font-size: 16px;
        color: #0f172a;
      }

      .matrix-stat-text {
        font-size: 13px;
        color: #64748b;
        margin-left: 8px;
      }
    }
  }

  .matrix-quick-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 14px;
    margin-bottom: 14px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
  }
}

/* 企业级功能与权限矩阵卡片流 */
.matrix-domain-stream {
  max-height: calc(100vh - 340px);
  overflow: hidden auto;
  padding-right: 6px;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: #cbd5e1;
    border-radius: 4px;
  }
}

.matrix-domain-group {
  margin-bottom: 22px;

  &:last-child {
    margin-bottom: 0;
  }

  .matrix-domain-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 16px;
    border-radius: 6px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-left: 4px solid #2563eb;
    margin-bottom: 12px;

    .domain-header-left {
      display: flex;
      align-items: center;
      gap: 10px;

      .domain-header-icon {
        font-size: 16px;
        color: #2563eb;
      }

      .domain-header-title {
        font-size: 14px;
        color: #0f172a;
        font-weight: 700;
      }

      .domain-header-desc {
        font-size: 12px;
        color: #64748b;
      }

      .domain-header-pill {
        font-size: 11px;
        padding: 2px 8px;
        border-radius: 10px;
        background: #fff;
        border: 1px solid #cbd5e1;
        color: #475569;
        font-weight: 500;
      }
    }

    .domain-header-right {
      display: flex;
      align-items: center;
    }
  }
}

.matrix-cards-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.matrix-module-card {
  flex-shrink: 0;
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
  box-shadow: 0 1px 2px rgb(0 0 0 / 0.02);
  transition: border-color 0.15s ease;

  &:hover {
    border-color: #cbd5e1;
  }

  .module-card-header {
    flex-shrink: 0;
    min-height: 48px;
    box-sizing: border-box;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 16px;
    background: #f8fafc;
    border-bottom: 1px solid #e2e8f0;

    .module-title-box {
      display: flex;
      align-items: center;
      gap: 8px;

      .module-icon {
        font-size: 16px;
        color: #2563eb;
      }

      .module-name {
        font-size: 14px;
        color: #0f172a;
        font-weight: 650;
      }

      .module-code {
        font-family: monospace;
        font-size: 11px;
      }
    }

    .module-ctrl-box {
      display: flex;
      align-items: center;
      gap: 14px;

      .module-counter-pill {
        font-size: 12px;
        color: #64748b;
        background: #fff;
        padding: 2px 8px;
        border-radius: 12px;
        border: 1px solid #e2e8f0;
      }
    }
  }

  .module-card-body {
    display: flex;
    flex-direction: column;
  }

  .matrix-page-row {
    flex-shrink: 0;
    min-height: 52px;
    box-sizing: border-box;
    display: flex;
    align-items: center;
    padding: 12px 16px;
    border-bottom: 1px solid #f1f5f9;
    transition: background-color 0.15s ease;

    &:last-child {
      border-bottom: none;
    }

    &:hover {
      background-color: #f8fafc;
    }

    &.is-page-checked {
      background-color: #fafcff;
    }

    .page-access-col {
      width: 250px;
      flex-shrink: 0;
      display: flex;
      flex-direction: column;
      gap: 4px;

      .page-title {
        font-weight: 600;
        font-size: 13.5px;
        color: #1e293b;
      }

      .page-meta-tags {
        display: flex;
        align-items: center;
        gap: 6px;
        padding-left: 24px;

        .page-code-tag {
          font-family: monospace;
          color: #94a3b8;
          font-size: 11px;
        }

        .page-route-tag {
          font-family: monospace;
          color: #64748b;
          font-size: 11px;
          background: #f1f5f9;
          padding: 1px 4px;
          border-radius: 3px;
        }
      }
    }

    .action-chips-col {
      flex: 1;
      min-width: 0;
      padding-left: 16px;
      border-left: 1px dashed #e2e8f0;

      .action-chips-wrap {
        display: flex;
        align-items: center;
        gap: 8px;
        flex-wrap: wrap;

        .select-all-page-btn {
          flex-shrink: 0;
          padding: 3px 8px;
          border-radius: 4px;
          border: 1px solid #cbd5e1;
          background: #fff;
          color: #475569;
          font-size: 11px;
          cursor: pointer;
          transition: all 0.15s;

          &:hover {
            border-color: #2563eb;
            color: #2563eb;
            background: #eff6ff;
          }
        }

        .action-chip {
          flex-shrink: 0;
          display: inline-flex;
          align-items: center;
          gap: 5px;
          padding: 4px 10px;
          border-radius: 6px;
          border: 1px solid #e2e8f0;
          background: #fff;
          color: #334155;
          font-size: 12px;
          cursor: pointer;
          transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);

          .chip-check {
            font-size: 12px;
            color: #2563eb;
          }

          .chip-text {
            font-weight: 500;
          }

          .chip-code {
            font-family: monospace;
            font-size: 10px;
            color: #94a3b8;
          }

          &:hover {
            border-color: #bfdbfe;
            background: #f0f7ff;
            color: #2563eb;
          }

          &.is-selected {
            border-color: #2563eb;
            background: #eff6ff;
            color: #2563eb;
            font-weight: 600;

            .chip-code {
              color: #3b82f6;
            }
          }
        }
      }

      .no-actions-indicator {
        font-size: 12px;
        color: #94a3b8;
        font-style: italic;
      }
    }
  }
}

.empty-matrix-hint {
  padding: 40px 0;
  text-align: center;
  color: #94a3b8;
  font-size: 13.5px;
}

/* 导航显示位置胶囊徽章 */
.nav-position-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 2px 7px;
  border-radius: 4px;
  font-size: 11.5px;
  font-weight: 500;
  line-height: 18px;
  white-space: nowrap;
  transition: all 0.2s ease;
  user-select: none;
  cursor: pointer;

  .pos-arrow {
    font-size: 10px;
    opacity: 0.75;
    transition: transform 0.2s ease;
  }

  &:hover .pos-arrow {
    opacity: 1;
    transform: translateY(1px);
  }

  &.nav-pos-auto {
    background: #f1f5f9;
    color: #475569;
    border: 1px solid #e2e8f0;

    &:hover {
      background: #e2e8f0;
      color: #1e293b;
    }
  }

  &.nav-pos-top {
    background: #fffbeb;
    color: #b45309;
    border: 1px solid #fde68a;

    &:hover {
      background: #fef3c7;
      color: #92400e;
    }
  }

  &.nav-pos-side {
    background: #eff6ff;
    color: #1d4ed8;
    border: 1px solid #bfdbfe;

    &:hover {
      background: #dbeafe;
      color: #1e40af;
    }
  }

  &.nav-pos-both {
    background: #f0fdf4;
    color: #15803d;
    border: 1px solid #bbf7d0;

    &:hover {
      background: #dcfce7;
      color: #166534;
    }
  }
}

/* 列设置浮窗 */
.column-settings-popover {
  .popover-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-weight: 600;
    font-size: 13px;
    color: #1e293b;
  }

  .popover-body {
    max-height: 280px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 6px;

    .col-item {
      display: flex;
      align-items: center;

      :deep(.el-checkbox) {
        width: 100%;
        margin-right: 0;

        .el-checkbox__label {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          font-size: 12.5px;
        }
      }

      .col-item-label {
        font-weight: 500;
        color: #334155;
      }

      .col-item-desc {
        font-size: 11px;
        color: #94a3b8;
      }
    }
  }
}

/* 表格操作列统一规范 */
.table-action-cell {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  flex-wrap: nowrap;

  .el-button + .el-button {
    margin-left: 0;
  }

  .action-more-btn {
    display: inline-flex;
    align-items: center;
    font-size: 12px;
    padding: 0 4px;

    .el-icon--right {
      margin-left: 2px;
      font-size: 10px;
    }
  }
}

.action-danger-text {
  color: #ef4444 !important;

  &:hover {
    color: #dc2626 !important;
    background-color: #fef2f2 !important;
  }
}

.action-success-text {
  color: #10b981 !important;

  &:hover {
    color: #059669 !important;
    background-color: #f0fdf4 !important;
  }
}
</style>
