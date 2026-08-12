<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import {
  ArrowRight,
  Edit,
  Folder,
  Key,
  MoreFilled,
  Plus,
  Search,
  Switch,
  View
} from "@element-plus/icons-vue";
import {
  createIamMenuPermission,
  replaceIamMenuSubjectGrants,
  updateIamMenuPermission,
  updateIamMenuPermissionStatus
} from "@/api/modules/iam";
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
import { localizeMenuType, localizeSubjectType } from "../iam-meta";

type SubjectType = "USER" | "DEPARTMENT" | "POSITION" | "COMPANY" | "TENANT";

interface ResourceNode extends IamMenuRow {
  children: ResourceNode[];
  actionCount: number;
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
  openMenuCreate: (defaults?: { parentId?: number; menuType?: string }) => void;
  openMenuEdit: (row: IamMenuRow) => void;
  toggleMenu: (row: IamMenuRow) => void;
  refreshData: () => Promise<void>;
}>();

const activeView = ref("roles");
const resourceKeyword = ref("");
const selectedResourceId = ref<number | null>(null);
const selectedRoleId = ref<number | null>(null);
const selectedMenuIds = ref<number[]>([]);
const selectedActionCodes = ref<string[]>([]);
const savingRole = ref(false);
const resourceTreeRef = ref<{ setCurrentKey: (key: number) => void }>();

const exceptionDialog = reactive({
  visible: false,
  mode: "create" as "create" | "edit",
  id: 0
});

const exceptionForm = reactive({
  menuId: 0,
  subjectType: "USER" as SubjectType,
  subjectValue: "",
  actionCodes: [] as string[],
  status: "ENABLED",
  remark: ""
});

const visibleResources = computed(() => props.menus.filter((item) => item.menuType !== "BUTTON"));
const actionResources = computed(() => props.menus.filter((item) => item.menuType === "BUTTON"));

const actionsByMenu = computed(() => {
  const result = new Map<number, IamMenuRow[]>();
  for (const action of actionResources.value) {
    const actions = result.get(action.parentId) ?? [];
    actions.push(action);
    result.set(action.parentId, actions);
  }
  for (const actions of result.values()) {
    actions.sort((left, right) => left.sortNo - right.sortNo || left.id - right.id);
  }
  return result;
});

const enabledActionsByMenu = computed(() => {
  const result = new Map<number, IamMenuRow[]>();
  for (const [menuId, actions] of actionsByMenu.value) {
    result.set(menuId, actions.filter((action) => action.status === "ENABLED"));
  }
  return result;
});

const resourceTree = computed<ResourceNode[]>(() => {
  return buildResourceTree(visibleResources.value, resourceKeyword.value);
});

const grantResourceTree = computed(() =>
  buildResourceTree(visibleResources.value.filter((item) => item.status === "ENABLED"))
);

const selectedResource = computed(() => props.menus.find((item) => item.id === selectedResourceId.value) ?? null);
const selectedResourceActions = computed(() => actionsByMenu.value.get(selectedResourceId.value ?? 0) ?? []);

const enabledRoles = computed(() => props.roles.filter((item) => item.status === "ENABLED"));
const currentRole = computed(() => props.roles.find((item) => item.id === selectedRoleId.value) ?? null);
const grantableResourceIds = computed(
  () => new Set(props.menus.filter((item) => ["MENU", "LINK"].includes(item.menuType)).map((item) => item.id))
);
const roleGrantSummary = computed(() =>
  `${selectedMenuIds.value.length} 个页面，${selectedActionCodes.value.length} 个操作`
);

const rolePermissionGroups = computed(() => {
  const directoryGroups = grantResourceTree.value
    .filter((root) => root.menuType === "DIRECTORY")
    .map((root) => ({ key: String(root.id), label: root.menuName, pages: flattenGrantablePages(root) }))
    .filter((group) => group.pages.length > 0);
  const standalonePages = grantResourceTree.value
    .filter((root) => root.menuType !== "DIRECTORY")
    .flatMap(flattenGrantablePages);
  return standalonePages.length
    ? [{ key: "standalone", label: "独立页面", pages: standalonePages }, ...directoryGroups]
    : directoryGroups;
});

const companyDepartments = computed(() => props.departments.filter((item) => Number(item.parentId ?? 0) <= 0));
const exceptionSubjectOptions: Array<{ label: string; value: SubjectType }> = [
  { label: "指定用户", value: "USER" },
  { label: "指定部门", value: "DEPARTMENT" },
  { label: "指定岗位", value: "POSITION" },
  { label: "指定公司", value: "COMPANY" },
  { label: "全租户", value: "TENANT" }
];

const subjectValueOptions = computed(() => {
  switch (exceptionForm.subjectType) {
    case "USER":
      return props.users.map((item) => ({ label: `${item.nickname} (${item.username})`, value: String(item.id) }));
    case "DEPARTMENT":
      return props.departments.map((item) => ({ label: item.deptFullName, value: String(item.id) }));
    case "POSITION":
      return props.positions.map((item) => ({ label: item.positionName, value: String(item.id) }));
    case "COMPANY":
      return companyDepartments.value.map((item) => ({ label: item.deptName, value: String(item.id) }));
    case "TENANT":
    default:
      return [{ label: "当前租户", value: String(props.currentTenantId) }];
  }
});

const menuOptions = computed(() =>
  visibleResources.value
    .filter((item) => ["MENU", "LINK"].includes(item.menuType))
    .map((item) => ({ label: resolveMenuPath(item), value: item.id }))
);

const exceptionActionOptions = computed(() => enabledActionsByMenu.value.get(exceptionForm.menuId) ?? []);
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
  { prop: "subjectType", label: "类型", minWidth: 110, formatter: (row) => localizeSubjectType(String(row.subjectType ?? "")) },
  { prop: "menuName", label: "可访问页面", minWidth: 180 },
  { prop: "accessSummary", label: "页面操作", minWidth: 240 },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" },
  { prop: "actions", label: "操作", minWidth: 160, slot: "actions" }
];

watch(
  visibleResources,
  (resources) => {
    if (!selectedResourceId.value || !resources.some((item) => item.id === selectedResourceId.value)) {
      selectedResourceId.value = resources[0]?.id ?? null;
    }
  },
  { immediate: true }
);

watch(
  enabledRoles,
  (roles) => {
    if (!selectedRoleId.value || !roles.some((item) => item.id === selectedRoleId.value)) {
      selectedRoleId.value = roles[0]?.id ?? null;
    }
  },
  { immediate: true }
);

watch([selectedRoleId, () => props.menuPermissions, () => props.menus], syncRolePermissions, { immediate: true });

watch(
  () => exceptionForm.subjectType,
  () => {
    exceptionForm.subjectValue = subjectValueOptions.value[0]?.value ?? "";
  }
);

watch(
  () => exceptionForm.menuId,
  () => {
    exceptionForm.actionCodes = [];
  }
);

function flattenGrantablePages(root: ResourceNode) {
  const result: ResourceNode[] = [];
  const visit = (node: ResourceNode) => {
    if (["MENU", "LINK"].includes(node.menuType)) {
      result.push(node);
    }
    node.children.forEach(visit);
  };
  visit(root);
  return result;
}

function buildResourceTree(source: IamMenuRow[], search = ""): ResourceNode[] {
  const keyword = search.trim().toLowerCase();
  const nodes = new Map<number, ResourceNode>();
  for (const menu of source) {
    nodes.set(menu.id, {
      ...menu,
      actionCount: actionsByMenu.value.get(menu.id)?.length ?? 0,
      children: []
    });
  }
  const roots: ResourceNode[] = [];
  for (const node of nodes.values()) {
    const parent = nodes.get(node.parentId);
    if (parent) {
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  }
  const sortNodes = (items: ResourceNode[]): ResourceNode[] =>
    items
      .sort((left, right) => left.sortNo - right.sortNo || left.id - right.id)
      .map((item) => ({ ...item, children: sortNodes(item.children) }));
  const filterNodes = (items: ResourceNode[]): ResourceNode[] =>
    items.flatMap((item) => {
      const children = filterNodes(item.children);
      const matched = !keyword || item.menuName.toLowerCase().includes(keyword) || item.menuCode.toLowerCase().includes(keyword);
      return matched || children.length ? [{ ...item, children }] : [];
    });
  return filterNodes(sortNodes(roots));
}

function syncRolePermissions() {
  if (!selectedRoleId.value) {
    selectedMenuIds.value = [];
    selectedActionCodes.value = [];
    return;
  }
  const grants = props.menuPermissions.filter(
    (row) => row.subjectType === "ROLE" && row.subjectValue === String(selectedRoleId.value) && row.status === "ENABLED"
  );
  selectedMenuIds.value = grants.map((item) => item.menuId).filter((menuId) => grantableResourceIds.value.has(menuId));
  const enabledActionCodes = new Set(
    Array.from(enabledActionsByMenu.value.values()).flatMap((actions) => actions.map((action) => action.menuCode))
  );
  selectedActionCodes.value = grants
    .flatMap((item) => parseActionCodes(item.buttonCodesJson))
    .filter((code) => enabledActionCodes.has(code));
}

function selectResource(row: ResourceNode | IamMenuRow) {
  selectedResourceId.value = row.id;
  nextTick(() => resourceTreeRef.value?.setCurrentKey(row.id));
}

function selectRole(roleId: number) {
  selectedRoleId.value = roleId;
}

function toggleGroup(group: { pages: ResourceNode[] }, checked: boolean) {
  const menuIds = group.pages.map((page) => page.id);
  const actionCodes = group.pages.flatMap((page) => (enabledActionsByMenu.value.get(page.id) ?? []).map((item) => item.menuCode));
  selectedMenuIds.value = checked
    ? Array.from(new Set([...selectedMenuIds.value, ...menuIds]))
    : selectedMenuIds.value.filter((id) => !menuIds.includes(id));
  selectedActionCodes.value = checked
    ? Array.from(new Set([...selectedActionCodes.value, ...actionCodes]))
    : selectedActionCodes.value.filter((code) => !actionCodes.includes(code));
}

function isGroupSelected(group: { pages: ResourceNode[] }) {
  const menuSelected = group.pages.every((page) => selectedMenuIds.value.includes(page.id));
  const actionCodes = group.pages.flatMap((page) =>
    (enabledActionsByMenu.value.get(page.id) ?? []).map((item) => item.menuCode)
  );
  return menuSelected && actionCodes.every((code) => selectedActionCodes.value.includes(code));
}

function isGroupIndeterminate(group: { pages: ResourceNode[] }) {
  const menuIds = group.pages.map((page) => page.id);
  const actionCodes = group.pages.flatMap((page) =>
    (enabledActionsByMenu.value.get(page.id) ?? []).map((item) => item.menuCode)
  );
  const selected = menuIds.filter((id) => selectedMenuIds.value.includes(id)).length
    + actionCodes.filter((code) => selectedActionCodes.value.includes(code)).length;
  return selected > 0 && selected < menuIds.length + actionCodes.length;
}

function toggleMenuGrant(menuId: number, checked: boolean) {
  selectedMenuIds.value = checked
    ? Array.from(new Set([...selectedMenuIds.value, menuId]))
    : selectedMenuIds.value.filter((id) => id !== menuId);
  if (!checked) {
    const actionCodes = (enabledActionsByMenu.value.get(menuId) ?? []).map((item) => item.menuCode);
    selectedActionCodes.value = selectedActionCodes.value.filter((code) => !actionCodes.includes(code));
  }
}

function toggleAction(menuId: number, code: string, checked: boolean) {
  selectedActionCodes.value = checked
    ? Array.from(new Set([...selectedActionCodes.value, code]))
    : selectedActionCodes.value.filter((item) => item !== code);
  if (checked && !selectedMenuIds.value.includes(menuId)) {
    selectedMenuIds.value = [...selectedMenuIds.value, menuId];
  }
}

async function saveRolePermissions() {
  if (!selectedRoleId.value) {
    return;
  }
  savingRole.value = true;
  try {
    await replaceIamMenuSubjectGrants({
      subjectType: "ROLE",
      subjectValue: String(selectedRoleId.value),
      grants: selectedMenuIds.value.map((menuId) => ({
        menuId,
        actionCodes: (enabledActionsByMenu.value.get(menuId) ?? [])
          .map((item) => item.menuCode)
          .filter((code) => selectedActionCodes.value.includes(code))
      }))
    });
    await props.refreshData();
    ElMessage.success("角色权限已保存");
  } finally {
    savingRole.value = false;
  }
}

function openExceptionCreate() {
  exceptionDialog.mode = "create";
  exceptionDialog.id = 0;
  exceptionForm.menuId = menuOptions.value[0]?.value ?? 0;
  exceptionForm.subjectType = "USER";
  exceptionForm.subjectValue = subjectValueOptions.value[0]?.value ?? "";
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
  if (!selectedMenu) {
    return;
  }
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
  ElMessage.success("例外授权已保存");
}

async function toggleException(row: IamMenuPermissionRow) {
  await updateIamMenuPermissionStatus(row.id, { status: row.status === "ENABLED" ? "DISABLED" : "ENABLED" });
  await props.refreshData();
}

function resolveMenuPath(menu: IamMenuRow) {
  const names = [menu.menuName];
  let parentId = menu.parentId;
  while (parentId > 0) {
    const parent = props.menus.find((item) => item.id === parentId);
    if (!parent) {
      break;
    }
    names.unshift(parent.menuName);
    parentId = parent.parentId;
  }
  return names.join(" / ");
}

function resolveSubjectLabel(subjectType: string, subjectValue: string) {
  switch (subjectType) {
    case "USER":
      return props.users.find((item) => String(item.id) === subjectValue)?.nickname ?? subjectValue;
    case "DEPARTMENT":
      return props.departments.find((item) => String(item.id) === subjectValue)?.deptFullName ?? subjectValue;
    case "POSITION":
      return props.positions.find((item) => String(item.id) === subjectValue)?.positionName ?? subjectValue;
    case "COMPANY":
      return companyDepartments.value.find((item) => String(item.id) === subjectValue)?.deptName ?? subjectValue;
    case "TENANT":
      return "当前租户";
    default:
      return subjectValue;
  }
}

function parseActionCodes(buttonCodesJson?: string) {
  if (!buttonCodesJson) {
    return [];
  }
  try {
    const values = JSON.parse(buttonCodesJson) as string[];
    return Array.isArray(values) ? values : [];
  } catch {
    return [];
  }
}

function summarizeActions(menuId: number, buttonCodesJson?: string) {
  const codes = parseActionCodes(buttonCodesJson);
  if (!codes.length) {
    return "仅访问页面";
  }
  return codes
    .map((code) => actionsByMenu.value.get(menuId)?.find((action) => action.menuCode === code)?.menuName ?? code)
    .join("、");
}
</script>

<template>
  <section class="permission-center">
    <el-tabs v-model="activeView" class="permission-center__tabs">
      <el-tab-pane label="角色授权" name="roles">
        <div class="role-permission-layout">
          <aside class="role-list" aria-label="角色列表">
            <div class="role-list__header">
              <strong>角色</strong>
              <span>{{ enabledRoles.length }}</span>
            </div>
            <button
              v-for="role in enabledRoles"
              :key="role.id"
              class="role-list__item"
              :class="{ 'is-active': selectedRoleId === role.id }"
              @click="selectRole(role.id)"
            >
              <span>{{ role.roleName }}</span>
              <ArrowRight />
            </button>
          </aside>

          <BaseCard class="role-permission-panel">
            <template #header>
              <div class="permission-panel__header">
                <div>
                  <strong>{{ currentRole?.roleName ?? "选择角色" }}</strong>
                  <span>{{ roleGrantSummary }}</span>
                </div>
                <el-button
                  v-if="canWrite"
                  v-button-permission="{ code: 'iam_menu_permission:save', fallback: 'iam:menu:write' }"
                  type="primary"
                  :loading="savingRole"
                  @click="saveRolePermissions"
                >保存授权</el-button>
              </div>
            </template>

            <div class="permission-groups">
              <section v-for="group in rolePermissionGroups" :key="group.key" class="permission-group">
                <header class="permission-group__header">
                  <el-checkbox
                    :model-value="isGroupSelected(group)"
                    :indeterminate="isGroupIndeterminate(group)"
                    @change="(value: boolean | string | number) => toggleGroup(group, Boolean(value))"
                  >{{ group.label }}</el-checkbox>
                  <span>{{ group.pages.length }} 个页面</span>
                </header>

                <div class="permission-page-list">
                  <div v-for="page in group.pages" :key="page.id" class="permission-page">
                    <el-checkbox
                      :model-value="selectedMenuIds.includes(page.id)"
                      @change="(value: boolean | string | number) => toggleMenuGrant(page.id, Boolean(value))"
                    >{{ page.menuName }}</el-checkbox>
                    <div v-if="enabledActionsByMenu.get(page.id)?.length" class="permission-page__actions">
                      <el-checkbox
                        v-for="action in enabledActionsByMenu.get(page.id)"
                        :key="action.id"
                        :model-value="selectedActionCodes.includes(action.menuCode)"
                        @change="(value: boolean | string | number) => toggleAction(page.id, action.menuCode, Boolean(value))"
                      >{{ action.menuName }}</el-checkbox>
                    </div>
                  </div>
                </div>
              </section>
            </div>
          </BaseCard>
        </div>
      </el-tab-pane>

      <el-tab-pane label="资源结构" name="resources">
        <div class="resource-layout">
          <BaseCard class="resource-tree-card">
            <template #header>
              <div class="resource-tree__header">
                <strong>资源结构</strong>
                <el-dropdown v-if="canWrite" trigger="click">
                  <el-button :icon="Plus" aria-label="新增资源" />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="openMenuCreate({ menuType: 'DIRECTORY' })">新增目录</el-dropdown-item>
                      <el-dropdown-item @click="openMenuCreate({ menuType: 'MENU' })">新增页面</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
            <el-input v-model="resourceKeyword" :prefix-icon="Search" clearable placeholder="搜索资源" />
            <el-tree
              ref="resourceTreeRef"
              class="resource-tree"
              :data="resourceTree"
              node-key="id"
              :props="{ label: 'menuName', children: 'children' }"
              default-expand-all
              highlight-current
              @node-click="selectResource"
            >
              <template #default="{ data }">
                <span class="resource-tree__node">
                  <component :is="data.menuType === 'DIRECTORY' ? Folder : View" />
                  <span>{{ data.menuName }}</span>
                  <small v-if="data.actionCount">{{ data.actionCount }}</small>
                </span>
              </template>
            </el-tree>
          </BaseCard>

          <BaseCard v-if="selectedResource" class="resource-detail-card">
            <template #header>
              <div class="resource-detail__header">
                <div>
                  <strong>{{ selectedResource.menuName }}</strong>
                  <BaseStatusTag
                    :status="selectedResource.status"
                    :type="selectedResource.status === 'ENABLED' ? 'success' : 'danger'"
                  />
                </div>
                <el-dropdown v-if="canWrite" trigger="click">
                  <el-button :icon="MoreFilled" aria-label="资源操作" />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item :icon="Edit" @click="openMenuEdit(selectedResource)">编辑资源</el-dropdown-item>
                      <el-dropdown-item
                        v-if="selectedResource.menuType === 'DIRECTORY'"
                        :icon="Plus"
                        @click="openMenuCreate({ parentId: selectedResource.id, menuType: 'MENU' })"
                      >新增子页面</el-dropdown-item>
                      <el-dropdown-item
                        v-if="selectedResource.menuType === 'MENU'"
                        :icon="Key"
                        @click="openMenuCreate({ parentId: selectedResource.id, menuType: 'BUTTON' })"
                      >新增页面操作</el-dropdown-item>
                      <el-dropdown-item :icon="Switch" @click="toggleMenu(selectedResource)">
                        {{ selectedResource.status === "ENABLED" ? "停用资源" : "启用资源" }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>

            <dl class="resource-detail__meta">
              <div><dt>资源类型</dt><dd>{{ localizeMenuType(selectedResource.menuType) }}</dd></div>
              <div><dt>上级资源</dt><dd>{{ props.menus.find((item) => item.id === selectedResource?.parentId)?.menuName ?? "顶级" }}</dd></div>
              <div><dt>导航显示</dt><dd>{{ selectedResource.visible ? "显示" : "隐藏" }}</dd></div>
              <div><dt>排序</dt><dd>{{ selectedResource.sortNo }}</dd></div>
            </dl>

            <section v-if="selectedResource.menuType === 'MENU'" class="action-catalog">
              <header>
                <strong>页面操作</strong>
                <el-button
                  v-if="canWrite && selectedResource.menuType === 'MENU'"
                  :icon="Plus"
                  @click="openMenuCreate({ parentId: selectedResource.id, menuType: 'BUTTON' })"
                >新增操作</el-button>
              </header>
              <div v-if="selectedResourceActions.length" class="action-catalog__list">
                <button v-for="action in selectedResourceActions" :key="action.id" @click="openMenuEdit(action)">
                  <Key />
                  <span>{{ action.menuName }}</span>
                  <BaseStatusTag :status="action.status" :type="action.status === 'ENABLED' ? 'success' : 'danger'" />
                </button>
              </div>
              <el-empty v-else description="暂无页面操作" :image-size="56" />
            </section>

            <el-collapse class="resource-technical">
              <el-collapse-item title="高级信息" name="technical">
                <dl class="resource-detail__meta resource-detail__meta--technical">
                  <div><dt>资源标识</dt><dd>{{ selectedResource.menuCode }}</dd></div>
                  <div><dt>访问权限</dt><dd>{{ selectedResource.permissionCode || "-" }}</dd></div>
                  <div><dt>路由</dt><dd>{{ selectedResource.routePath || "-" }}</dd></div>
                  <div><dt>组件</dt><dd>{{ selectedResource.componentPath || "-" }}</dd></div>
                </dl>
              </el-collapse-item>
            </el-collapse>
          </BaseCard>
        </div>
      </el-tab-pane>

      <el-tab-pane label="例外授权" name="exceptions">
        <ProTable
          :columns="exceptionColumns"
          :data="exceptionRows"
          :loading="loading"
          compact
          title="例外授权"
        >
          <template #toolbar>
            <el-button v-if="canWrite" type="primary" :icon="Plus" @click="openExceptionCreate">新增例外</el-button>
          </template>
          <template #status="{ row }">
            <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
          </template>
          <template #actions="{ row }">
            <el-button link type="primary" @click="openExceptionEdit(row)">编辑</el-button>
            <el-button link @click="toggleException(row)">{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
          </template>
        </ProTable>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="exceptionDialog.visible"
      :title="exceptionDialog.mode === 'create' ? '新增例外授权' : '编辑例外授权'"
      width="620px"
    >
      <el-form label-position="top">
        <div class="exception-form-grid">
          <el-form-item label="对象类型">
            <el-select v-model="exceptionForm.subjectType">
              <el-option v-for="item in exceptionSubjectOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="授权对象">
            <el-select v-model="exceptionForm.subjectValue" filterable>
              <el-option v-for="item in subjectValueOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="可访问页面">
          <el-select v-model="exceptionForm.menuId" filterable>
            <el-option v-for="item in menuOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="exceptionActionOptions.length" label="页面操作">
          <el-checkbox-group v-model="exceptionForm.actionCodes" class="exception-actions">
            <el-checkbox v-for="action in exceptionActionOptions" :key="action.id" :value="action.menuCode">
              {{ action.menuName }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="exceptionForm.status">
            <el-radio-button label="启用" value="ENABLED" />
            <el-radio-button label="停用" value="DISABLED" />
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="exceptionForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exceptionDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveException">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.permission-center {
  min-width: 0;
}

.permission-center__tabs :deep(.el-tabs__header) {
  margin: 0 0 14px;
  padding: 0 4px;
  background: #fff;
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-md);
}

.permission-center__tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.role-permission-layout,
.resource-layout {
  min-width: 0;
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr);
  gap: 14px;
}

.role-list {
  align-self: start;
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-md);
  overflow: hidden;
  background: #fff;
}

.role-list__header,
.resource-tree__header,
.permission-panel__header,
.resource-detail__header,
.action-catalog > header {
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.role-list__header {
  padding: 0 14px;
  border-bottom: 1px solid var(--sb-border-color);

  span {
    color: var(--sb-text-tertiary);
    font-size: 12px;
  }
}

.role-list__item {
  width: 100%;
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 14px;
  border: 0;
  border-bottom: 1px solid #edf0f3;
  background: transparent;
  color: var(--sb-text-primary);
  text-align: left;
  cursor: pointer;

  &:last-child {
    border-bottom: 0;
  }

  &:hover,
  &.is-active {
    background: #f2f6fc;
  }

  &.is-active {
    color: var(--sb-primary-color);
    box-shadow: inset 3px 0 var(--sb-primary-color);
  }

  svg {
    width: 14px;
    color: #9ba5b1;
  }
}

.role-permission-panel :deep(.el-card__body) {
  padding: 0;
}

.permission-panel__header > div,
.resource-detail__header > div {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;

  > span {
    color: var(--sb-text-tertiary);
    font-size: 12px;
  }
}

.permission-groups {
  display: grid;
}

.permission-group + .permission-group {
  border-top: 1px solid var(--sb-border-color);
}

.permission-group__header {
  min-height: 46px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 16px;
  background: #f6f8fa;

  span {
    color: var(--sb-text-tertiary);
    font-size: 12px;
  }
}

.permission-page {
  min-height: 48px;
  display: grid;
  grid-template-columns: minmax(150px, 220px) minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  padding: 8px 16px 8px 32px;
  border-top: 1px solid #edf0f3;
}

.permission-page__actions,
.exception-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 18px;
}

.resource-tree-card {
  align-self: start;
}

.resource-tree-card :deep(.el-card__body) {
  padding: 12px;
}

.resource-tree {
  margin-top: 10px;
  background: transparent;
}

.resource-tree :deep(.el-tree-node__content) {
  height: 36px;
  border-radius: 4px;
}

.resource-tree__node {
  min-width: 0;
  flex: 1;
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding-right: 8px;

  svg {
    width: 15px;
    color: #718196;
  }

  > span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    min-width: 20px;
    color: var(--sb-text-tertiary);
    text-align: right;
  }
}

.resource-detail-card :deep(.el-card__body) {
  padding: 0;
}

.resource-detail__meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 0;
  border-bottom: 1px solid var(--sb-border-color);
}

.resource-detail__meta > div {
  min-width: 0;
  padding: 14px 16px;
  border-right: 1px solid var(--sb-border-color);

  &:last-child {
    border-right: 0;
  }
}

.resource-detail__meta dt {
  color: var(--sb-text-tertiary);
  font-size: 12px;
}

.resource-detail__meta dd {
  margin: 5px 0 0;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-catalog {
  padding: 0 16px 16px;
}

.action-catalog__list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border: 1px solid var(--sb-border-color);
  border-radius: 5px;
  overflow: hidden;
}

.action-catalog__list button {
  min-height: 46px;
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  padding: 0 12px;
  border: 0;
  border-bottom: 1px solid #edf0f3;
  background: #fff;
  text-align: left;
  cursor: pointer;

  &:nth-child(odd) {
    border-right: 1px solid #edf0f3;
  }

  &:hover {
    background: #f5f8fc;
  }

  > svg {
    width: 15px;
    color: #718196;
  }
}

.resource-technical {
  padding: 0 16px 8px;
  border-top: 1px solid var(--sb-border-color);
}

.resource-detail__meta--technical {
  border: 0;

  > div {
    padding: 8px 12px;
  }
}

.exception-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.exception-form-grid :deep(.el-select),
.el-form-item :deep(.el-select) {
  width: 100%;
}

@media (max-width: 980px) {
  .role-permission-layout,
  .resource-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .role-list {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  }

  .role-list__header {
    grid-column: 1 / -1;
  }

  .role-list__item {
    width: 100%;
    border-right: 1px solid #edf0f3;
  }
}

@media (max-width: 640px) {
  .role-list {
    grid-template-columns: minmax(0, 1fr);
  }

  .permission-panel__header {
    align-items: flex-start;
  }

  .permission-panel__header > div {
    display: grid;
    gap: 3px;
  }

  .permission-page,
  .exception-form-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .permission-page {
    gap: 7px;
    padding-left: 16px;
  }

  .resource-detail__meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .resource-detail__meta > div:nth-child(2) {
    border-right: 0;
  }

  .resource-detail__meta > div:nth-child(-n + 2) {
    border-bottom: 1px solid var(--sb-border-color);
  }

  .action-catalog__list {
    grid-template-columns: minmax(0, 1fr);
  }

  .action-catalog__list button:nth-child(odd) {
    border-right: 0;
  }
}
</style>
