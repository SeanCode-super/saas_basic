<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import {
  CircleCheck,
  CopyDocument,
  Cpu,
  Download,
  Folder,
  FolderOpened,
  Grid,
  Key,
  List,
  OfficeBuilding,
  Plus,
  Search,
  UserFilled
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import type { TreeInstance } from "element-plus";
import BaseCard from "@/components/base/BaseCard.vue";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
import {
  createIamDataPermissionRule,
  createIamRole,
  replaceIamMenuSubjectGrants
} from "@/api/modules/iam";
import type {
  IamDataPermissionRuleRow,
  IamDepartmentRow,
  IamEmployeeRow,
  IamMenuPermissionRow,
  IamMenuRow,
  IamPositionRow,
  IamRoleRow,
  IamUserRow
} from "@/api/modules/iam";
import IamRoleDrawer from "../components/IamRoleDrawer.vue";
import IamPermissionMatrix from "../components/IamPermissionMatrix.vue";
import IamPermissionInspector from "../components/IamPermissionInspector.vue";
import ExportDialog from "@/components/platform/ExportDialog.vue";

const props = withDefaults(
  defineProps<{
    mode?: "all" | "user" | "role";
    userColumns: ProTableColumn[];
    roleColumns: ProTableColumn[];
    users: IamUserRow[];
    roles: IamRoleRow[];
    menus?: IamMenuRow[];
    menuPermissions?: IamMenuPermissionRow[];
    dataPermissionRules?: IamDataPermissionRuleRow[];
    departments?: IamDepartmentRow[];
    positions?: IamPositionRow[];
    employees?: IamEmployeeRow[];
    currentTenantId?: number;
    canWrite?: boolean;
    loading: boolean;
    openUserCreate: () => void;
    openUserEdit: (row: IamUserRow) => void;
    toggleUser: (row: IamUserRow) => void;
    batchEnableUsers: (rows: IamUserRow[]) => void | Promise<void>;
    batchDisableUsers: (rows: IamUserRow[]) => void | Promise<void>;
    openRoleCreate: () => void;
    openRoleEdit: (row: IamRoleRow) => void;
    toggleRole: (row: IamRoleRow) => void;
    batchEnableRoles: (rows: IamRoleRow[]) => void | Promise<void>;
    batchDisableRoles: (rows: IamRoleRow[]) => void | Promise<void>;
    refreshData?: () => Promise<void>;
  }>(),
  {
    mode: "all",
    menus: () => [],
    menuPermissions: () => [],
    dataPermissionRules: () => [],
    departments: () => [],
    positions: () => [],
    employees: () => [],
    currentTenantId: 1,
    canWrite: true,
    refreshData: async () => {}
  }
);

// Role view mode: table vs matrix
const roleViewMode = ref<"table" | "matrix">("table");

// 3-in-1 Role Drawer state
const roleDrawerVisible = ref(false);
const activeDrawerRole = ref<IamRoleRow | null>(null);

// Permission Inspector state
const inspectorVisible = ref(false);
const inspectorTargetUserId = ref<number | null>(null);

function asRoleRows(rows: object[]) {
  return rows as IamRoleRow[];
}

function openRoleConfig(role: IamRoleRow) {
  activeDrawerRole.value = role;
  roleDrawerVisible.value = true;
}

// Clone Role state & handlers
const cloneDialogVisible = ref(false);
const cloningRole = ref(false);
const sourceRole = ref<IamRoleRow | null>(null);
const cloneForm = reactive({
  roleName: "",
  roleCode: "",
  remark: ""
});

function openCloneDialog(role: IamRoleRow) {
  sourceRole.value = role;
  cloneForm.roleName = `${role.roleName} - 副本`;
  cloneForm.roleCode = `${role.roleCode}_copy`;
  cloneForm.remark = `克隆自「${role.roleName} (${role.roleCode})」`;
  cloneDialogVisible.value = true;
}

async function handleConfirmClone() {
  if (!sourceRole.value) return;
  if (!cloneForm.roleName.trim() || !cloneForm.roleCode.trim()) {
    ElMessage.warning("请完整填写克隆角色的名称与编码");
    return;
  }

  cloningRole.value = true;
  try {
    // 1. Create the new role
    const newRole = await createIamRole({
      tenantId: props.currentTenantId,
      roleName: cloneForm.roleName.trim(),
      roleCode: cloneForm.roleCode.trim(),
      roleType: sourceRole.value.roleType || "PLATFORM",
      dataScopeType: sourceRole.value.dataScopeType || "DEPT_AND_CHILDREN",
      status: "ENABLED",
      remark: cloneForm.remark.trim()
    });

    // 2. Clone Menu & Button permissions
    const sourceGrants = props.menuPermissions.filter(
      (p) => p.subjectType === "ROLE" && p.subjectValue === String(sourceRole.value!.id) && p.status === "ENABLED"
    );
    const grantsPayload = sourceGrants.map((g) => {
      let actionCodes: string[] = [];
      if (g.buttonCodesJson) {
        try {
          const list = JSON.parse(g.buttonCodesJson);
          if (Array.isArray(list)) actionCodes = list;
        } catch {}
      }
      return {
        menuId: g.menuId,
        actionCodes
      };
    });

    if (grantsPayload.length > 0) {
      await replaceIamMenuSubjectGrants({
        subjectType: "ROLE",
        subjectValue: String(newRole.id),
        grants: grantsPayload
      });
    }

    // 3. Clone Data Scope Rule
    const sourceRule = props.dataPermissionRules.find(
      (r) => r.subjectType === "ROLE" && r.subjectValue === String(sourceRole.value!.id) && r.status === "ENABLED"
    );
    if (sourceRule) {
      await createIamDataPermissionRule({
        tenantId: props.currentTenantId,
        resourceCode: sourceRule.resourceCode || "*",
        resourceName: sourceRule.resourceName || "全局数据资源",
        subjectType: "ROLE",
        subjectValue: String(newRole.id),
        scopeType: sourceRule.scopeType,
        configJson: sourceRule.configJson,
        status: "ENABLED",
        remark: `克隆自「${sourceRole.value.roleName}」数据范围`
      });
    }

    ElMessage.success(`角色「${cloneForm.roleName}」克隆成功！已完整继承原角色的页面与数据权限`);
    cloneDialogVisible.value = false;
    if (props.refreshData) {
      await props.refreshData();
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || "克隆角色失败");
  } finally {
    cloningRole.value = false;
  }
}

function openUserInspector(user?: IamUserRow | null) {
  inspectorTargetUserId.value = user?.id ?? props.users[0]?.id ?? null;
  inspectorVisible.value = true;
}

// User workspace state & filters
const userSearchKeyword = ref("");
const userTypeFilter = ref("ALL");
const userStatusFilter = ref("ALL");
const userBindingFilter = ref("ALL");
const selectedUserRows = ref<IamUserRow[]>([]);

function handleUserSelectionChange(rows: IamUserRow[]) {
  selectedUserRows.value = rows;
}

function handleBatchEnableSelected() {
  if (!selectedUserRows.value.length) return;
  props.batchEnableUsers(selectedUserRows.value);
}

function handleBatchDisableSelected() {
  if (!selectedUserRows.value.length) return;
  props.batchDisableUsers(selectedUserRows.value);
}

const userStats = computed(() => {
  const total = props.users.length;
  const enabled = props.users.filter((u) => u.status === "ENABLED").length;
  const admin = props.users.filter((u) => u.userType === "SUPER_ADMIN" || u.userType === "SYSTEM").length;
  const bound = props.users.filter((u) => Number(u.employeeId) > 0).length;
  const enableRate = total > 0 ? Math.round((enabled / total) * 100) : 0;
  const boundRate = total > 0 ? Math.round((bound / total) * 100) : 0;
  return {
    total,
    enabled,
    enableRate,
    admin,
    bound,
    boundRate
  };
});

interface UserDeptTreeNode {
  id: number;
  label: string;
  count: number;
  deptCode?: string;
  isSpecial?: boolean;
  children: UserDeptTreeNode[];
}

function getDeptAndDescendantIds(deptId: number, departments: IamDepartmentRow[] = []): Set<number> {
  const ids = new Set<number>([deptId]);
  let added = true;
  while (added) {
    added = false;
    for (const d of departments) {
      if (ids.has(Number(d.parentId)) && !ids.has(d.id)) {
        ids.add(d.id);
        added = true;
      }
    }
  }
  return ids;
}

const deptTreeRef = ref<TreeInstance>();
const deptTreeSearch = ref("");
const selectedDeptId = ref<number>(0);

const deptUserCountMap = computed(() => {
  const map = new Map<number, number>();
  const empDeptMap = new Map<number, number>();
  props.employees?.forEach((e) => {
    if (e.deptId) empDeptMap.set(e.id, e.deptId);
  });
  props.users.forEach((u) => {
    if (u.employeeId && empDeptMap.has(u.employeeId)) {
      const dId = empDeptMap.get(u.employeeId)!;
      map.set(dId, (map.get(dId) || 0) + 1);
    }
  });
  return map;
});

const unboundUsersCount = computed(() => {
  const empDeptMap = new Map<number, number>();
  props.employees?.forEach((e) => {
    if (e.deptId) empDeptMap.set(e.id, e.deptId);
  });
  return props.users.filter((u) => !u.employeeId || !empDeptMap.has(u.employeeId)).length;
});

const deptTreeData = computed(() => {
  const list = props.departments || [];
  const map = new Map<number, UserDeptTreeNode>();

  list.forEach((d) => {
    const allSubIds = getDeptAndDescendantIds(d.id, list);
    let subTotal = 0;
    allSubIds.forEach((id) => {
      subTotal += deptUserCountMap.value.get(id) || 0;
    });

    map.set(d.id, {
      id: d.id,
      label: d.deptName,
      deptCode: d.deptCode,
      count: subTotal,
      children: []
    });
  });

  const roots: UserDeptTreeNode[] = [];
  list.forEach((d) => {
    const node = map.get(d.id);
    if (!node) return;
    const parentId = Number(d.parentId ?? 0);
    if (parentId > 0 && map.has(parentId)) {
      map.get(parentId)!.children.push(node);
    } else {
      roots.push(node);
    }
  });

  const allNode: UserDeptTreeNode = {
    id: 0,
    label: "全部组织与部门",
    count: props.users.length,
    isSpecial: true,
    children: roots
  };

  const unassignedNode: UserDeptTreeNode = {
    id: -1,
    label: "未分配部门人员",
    count: unboundUsersCount.value,
    isSpecial: true,
    children: []
  };

  return [allNode, unassignedNode];
});

function handleDeptNodeClick(node: UserDeptTreeNode) {
  selectedDeptId.value = node.id;
  userPage.value = 1;
}

function filterDeptTreeNode(value: string, data: any) {
  if (!value) return true;
  return data.label.toLowerCase().includes(value.toLowerCase());
}

watch(deptTreeSearch, (val) => {
  deptTreeRef.value?.filter(val);
});

const selectedDeptLabel = computed(() => {
  if (selectedDeptId.value === 0) return "全部组织与部门";
  if (selectedDeptId.value === -1) return "未分配部门人员";
  const d = props.departments?.find((item) => item.id === selectedDeptId.value);
  return d ? (d.deptFullName || d.deptName) : "全部组织";
});

const filteredUsers = computed(() => {
  let deptMatchIds: Set<number> | null = null;
  if (selectedDeptId.value !== 0) {
    if (selectedDeptId.value > 0) {
      deptMatchIds = getDeptAndDescendantIds(selectedDeptId.value, props.departments || []);
    }
  }

  const empDeptMap = new Map<number, number>();
  props.employees?.forEach((e) => {
    if (e.deptId) empDeptMap.set(e.id, e.deptId);
  });

  return props.users.filter((user) => {
    if (selectedDeptId.value !== 0) {
      if (selectedDeptId.value === -1) {
        if (user.employeeId && empDeptMap.has(user.employeeId)) {
          return false;
        }
      } else if (deptMatchIds) {
        if (!user.employeeId || !empDeptMap.has(user.employeeId)) {
          return false;
        }
        const userDeptId = empDeptMap.get(user.employeeId)!;
        if (!deptMatchIds.has(userDeptId)) {
          return false;
        }
      }
    }

    if (userSearchKeyword.value.trim()) {
      const kw = userSearchKeyword.value.trim().toLowerCase();
      const matchNick = (user.nickname || "").toLowerCase().includes(kw);
      const matchName = (user.username || "").toLowerCase().includes(kw);
      const matchCode = (user.userCode || "").toLowerCase().includes(kw);
      const matchMobile = (user.mobile || "").toLowerCase().includes(kw);
      const matchEmail = (user.email || "").toLowerCase().includes(kw);
      if (!matchNick && !matchName && !matchCode && !matchMobile && !matchEmail) {
        return false;
      }
    }
    if (userTypeFilter.value !== "ALL" && user.userType !== userTypeFilter.value) {
      return false;
    }
    if (userStatusFilter.value !== "ALL" && user.status !== userStatusFilter.value) {
      return false;
    }
    if (userBindingFilter.value === "BOUND" && (!user.employeeId || Number(user.employeeId) <= 0)) {
      return false;
    }
    if (userBindingFilter.value === "UNBOUND" && Number(user.employeeId) > 0) {
      return false;
    }
    return true;
  });
});

// 用户端分页
const userPage = ref(1);
const userPageSize = ref(10);
const paginatedUsers = computed(() => {
  const start = (userPage.value - 1) * userPageSize.value;
  return filteredUsers.value.slice(start, start + userPageSize.value);
});

watch(
  () => filteredUsers.value.length,
  (len) => {
    const maxPage = Math.max(1, Math.ceil(len / userPageSize.value));
    if (userPage.value > maxPage) {
      userPage.value = 1;
    }
  }
);

function getEmployee(employeeId?: number | null) {
  if (!employeeId || employeeId <= 0) return null;
  return props.employees?.find((e) => e.id === employeeId) || null;
}

function copyText(text?: string) {
  if (!text) return;
  navigator.clipboard.writeText(text);
  ElMessage.success("已复制到剪贴板");
}

function getAvatarColor(name: string) {
  const colors = [
    "#2563eb", "#7c3aed", "#0891b2", "#059669", "#d97706",
    "#dc2626", "#4f46e5", "#0284c7", "#16a34a"
  ];
  let hash = 0;
  for (let i = 0; i < (name || "").length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  return colors[Math.abs(hash) % colors.length];
}

function getUserTypeBadge(type: string) {
  switch (type) {
    case "SUPER_ADMIN":
      return { text: "超级管理员", type: "danger" };
    case "SYSTEM":
      return { text: "系统管理", type: "primary" };
    default:
      return { text: "业务用户", type: "info" };
  }
}

async function handleRoleSaved() {
  if (props.refreshData) {
    await props.refreshData();
  }
}

const iamExportVisible = ref(false);

function handleExportUsers() {
  iamExportVisible.value = true;
}
</script>

<template>
  <div class="module-section-stack">
    <!-- User / Account Enterprise Workspace -->
    <div v-if="!mode || mode === 'all' || mode === 'user'" class="user-workspace-wrapper">
      <!-- Sleek User Telemetry Strip -->
      <div class="user-telemetry-strip">
        <div class="telemetry-items">
          <div class="telemetry-item">
            <span class="telemetry-icon blue"><el-icon><UserFilled /></el-icon></span>
            <span class="telemetry-label">账号总数</span>
            <strong class="telemetry-val">{{ userStats.total }} 个</strong>
          </div>
          <div class="telemetry-divider" />
          <div class="telemetry-item">
            <span class="telemetry-icon emerald"><el-icon><CircleCheck /></el-icon></span>
            <span class="telemetry-label">正常启用</span>
            <strong class="telemetry-val">{{ userStats.enabled }} 个</strong>
            <el-tag type="success" size="small" effect="plain">{{ userStats.enableRate }}%</el-tag>
          </div>
          <div class="telemetry-divider" />
          <div class="telemetry-item">
            <span class="telemetry-icon amber"><el-icon><Key /></el-icon></span>
            <span class="telemetry-label">特权/管理</span>
            <strong class="telemetry-val">{{ userStats.admin }} 个</strong>
          </div>
          <div class="telemetry-divider" />
          <div class="telemetry-item">
            <span class="telemetry-icon indigo"><el-icon><OfficeBuilding /></el-icon></span>
            <span class="telemetry-label">员工实名绑定率</span>
            <strong class="telemetry-val">{{ userStats.boundRate }}% ({{ userStats.bound }}/{{ userStats.total }})</strong>
          </div>
        </div>
      </div>

      <!-- Main Split Workspace: Left Org Tree + Right User Management Table -->
      <div class="user-workspace-split">
        <!-- 左侧：可视化组织架构树面板 -->
        <aside class="user-tree-panel">
          <div class="tree-panel-header">
            <div class="panel-title-row">
              <span class="panel-title">组织架构树</span>
              <el-tag size="small" type="primary" effect="plain" class="tree-count-tag">
                {{ props.departments?.length || 0 }} 个部门
              </el-tag>
            </div>
            <el-input
              v-model="deptTreeSearch"
              :prefix-icon="Search"
              clearable
              placeholder="搜索部门名称..."
              class="tree-search-input"
            />
          </div>

          <div class="tree-scroll-container">
            <el-tree
              ref="deptTreeRef"
              :data="deptTreeData"
              node-key="id"
              default-expand-all
              highlight-current
              :current-node-key="selectedDeptId"
              :expand-on-click-node="false"
              :filter-node-method="filterDeptTreeNode"
              @node-click="handleDeptNodeClick"
            >
              <template #default="{ data }">
                <div class="custom-tree-node" :class="{ 'is-selected': selectedDeptId === data.id }">
                  <div class="node-main">
                    <el-icon class="node-icon">
                      <OfficeBuilding v-if="data.id === 0" />
                      <UserFilled v-else-if="data.id === -1" />
                      <FolderOpened v-else-if="data.children && data.children.length > 0" />
                      <Folder v-else />
                    </el-icon>
                    <span class="node-label">{{ data.label }}</span>
                  </div>
                  <span class="node-badge">{{ data.count }}人</span>
                </div>
              </template>
            </el-tree>
          </div>
        </aside>

        <!-- 右侧：用户列表工作台与分页器 -->
        <main class="user-content-panel">
          <BaseCard class="user-table-card">
            <template #header>
              <div class="user-toolbar">
                <div class="toolbar-filters">
                  <el-input
                    v-model="userSearchKeyword"
                    placeholder="搜索用户名 / 显示名 / 手机 / 邮箱..."
                    :prefix-icon="Search"
                    clearable
                    style="width: 240px"
                  />
                  <el-select v-model="userTypeFilter" placeholder="账号类型" style="width: 120px">
                    <el-option label="全部类型" value="ALL" />
                    <el-option label="超级管理员" value="SUPER_ADMIN" />
                    <el-option label="系统管理" value="SYSTEM" />
                    <el-option label="业务用户" value="NORMAL" />
                  </el-select>
                  <el-select v-model="userStatusFilter" placeholder="状态筛选" style="width: 110px">
                    <el-option label="全部状态" value="ALL" />
                    <el-option label="正常启用" value="ENABLED" />
                    <el-option label="已停用" value="DISABLED" />
                  </el-select>
                  <el-select v-model="userBindingFilter" placeholder="员工绑定" style="width: 130px">
                    <el-option label="全部绑定状态" value="ALL" />
                    <el-option label="已绑定员工" value="BOUND" />
                    <el-option label="未绑定 (独立账号)" value="UNBOUND" />
                  </el-select>
                  <el-tag
                    v-if="selectedDeptId !== 0"
                    closable
                    type="primary"
                    effect="light"
                    class="active-dept-filter-tag"
                    @close="selectedDeptId = 0"
                  >
                    部门：{{ selectedDeptLabel }}
                  </el-tag>
                </div>
                <div class="toolbar-actions">
                  <el-button
                    :icon="Download"
                    plain
                    @click="handleExportUsers"
                  >
                    导出用户 Excel
                  </el-button>
                  <el-button
                    :icon="Cpu"
                    plain
                    @click="openUserInspector(null)"
                  >
                    权限透视诊断器
                  </el-button>
                  <el-button
                    v-button-permission="{ code: 'iam_user:create', fallback: 'iam:user:write' }"
                    type="primary"
                    :icon="Plus"
                    @click="openUserCreate"
                  >
                    新增用户
                  </el-button>
                </div>
              </div>
            </template>

            <!-- Batch actions bar when items selected -->
            <transition name="el-fade-in-linear">
              <div v-if="selectedUserRows.length > 0" class="batch-selection-bar">
                <span class="batch-hint">
                  已勾选 <strong>{{ selectedUserRows.length }}</strong> 个用户账号
                </span>
                <div class="batch-btn-group">
                  <el-button type="primary" size="small" @click="handleBatchEnableSelected">批量启用</el-button>
                  <el-button size="small" @click="handleBatchDisableSelected">批量停用</el-button>
                </div>
              </div>
            </transition>

            <!-- User Enterprise Table (Paginated) -->
            <el-table
              :data="paginatedUsers"
              row-key="id"
              v-loading="loading"
              class="enterprise-user-table"
              stripe
              @selection-change="handleUserSelectionChange"
            >
              <el-table-column type="selection" width="46" align="center" />

              <!-- User Profile -->
              <el-table-column label="用户账号" min-width="220">
                <template #default="{ row }">
                  <div class="user-profile-cell">
                    <div
                      class="user-avatar"
                      :style="{ backgroundColor: getAvatarColor(row.nickname || row.username) }"
                    >
                      {{ (row.nickname || row.username || "U").slice(0, 1).toUpperCase() }}
                    </div>
                    <div class="user-info-text">
                      <div class="user-primary-row">
                        <strong class="user-nickname">{{ row.nickname || row.username }}</strong>
                        <span class="user-username-badge">@{{ row.username }}</span>
                      </div>
                      <div class="user-code-row">
                        <button
                          class="code-copy-btn"
                          title="点击复制用户唯一编码"
                          @click="copyText(row.userCode || row.publicId)"
                        >
                          <span>{{ row.userCode || row.publicId }}</span>
                          <el-icon><CopyDocument /></el-icon>
                        </button>
                      </div>
                    </div>
                  </div>
                </template>
              </el-table-column>

              <!-- User Type -->
              <el-table-column label="身份类型" min-width="120">
                <template #default="{ row }">
                  <el-tag
                    size="small"
                    :type="getUserTypeBadge(row.userType).type as any"
                    effect="light"
                    class="user-type-tag"
                  >
                    {{ getUserTypeBadge(row.userType).text }}
                  </el-tag>
                </template>
              </el-table-column>

              <!-- Bound Employee -->
              <el-table-column label="实名员工归属" min-width="190">
                <template #default="{ row }">
                  <div v-if="Number(row.employeeId) > 0" class="employee-binding-cell">
                    <el-popover placement="top" :width="260" trigger="hover">
                      <template #reference>
                        <div class="bound-emp-pill">
                          <el-icon class="emp-icon"><OfficeBuilding /></el-icon>
                          <span class="emp-name">{{ getEmployee(row.employeeId)?.employeeName || `员工ID #${row.employeeId}` }}</span>
                          <span v-if="getEmployee(row.employeeId)?.employeeNo" class="emp-no">
                            ({{ getEmployee(row.employeeId)?.employeeNo }})
                          </span>
                        </div>
                      </template>
                      <div class="emp-popover-detail">
                        <div class="emp-pop-title">关联组织与岗位</div>
                        <div class="emp-pop-item">
                          <span class="pop-label">所属部门:</span>
                          <span class="pop-val">{{ getEmployee(row.employeeId)?.deptFullName || getEmployee(row.employeeId)?.deptName || '-' }}</span>
                        </div>
                        <div class="emp-pop-item">
                          <span class="pop-label">当前岗位:</span>
                          <span class="pop-val">{{ getEmployee(row.employeeId)?.positionName || '-' }}</span>
                        </div>
                        <div class="emp-pop-item">
                          <span class="pop-label">联系电话:</span>
                          <span class="pop-val">{{ getEmployee(row.employeeId)?.mobile || '-' }}</span>
                        </div>
                      </div>
                    </el-popover>
                  </div>
                  <div v-else class="unbound-emp-cell">
                    <el-tag size="small" type="info" effect="plain" class="unbound-tag">
                      未绑定实名员工
                    </el-tag>
                  </div>
                </template>
              </el-table-column>

              <!-- Contact -->
              <el-table-column label="联系方式" min-width="180">
                <template #default="{ row }">
                  <div class="contact-cell">
                    <div v-if="row.mobile" class="contact-item">
                      <span class="contact-val">{{ row.mobile }}</span>
                    </div>
                    <div v-if="row.email" class="contact-item text-muted">
                      <span class="contact-val">{{ row.email }}</span>
                    </div>
                    <span v-if="!row.mobile && !row.email" class="text-placeholder">-</span>
                  </div>
                </template>
              </el-table-column>

              <!-- Status -->
              <el-table-column label="账号状态" min-width="100" align="center">
                <template #default="{ row }">
                  <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
                </template>
              </el-table-column>

              <!-- Actions -->
              <el-table-column label="操作" min-width="210">
                <template #default="{ row }">
                  <div class="action-cell">
                    <el-button
                      link
                      type="primary"
                      :icon="Cpu"
                      title="透视与模拟诊断该用户的实际权限"
                      @click="openUserInspector(row)"
                    >
                      透视诊断
                    </el-button>
                    <el-button
                      v-button-permission="{ code: 'iam_user:edit', fallback: 'iam:user:write' }"
                      link
                      type="primary"
                      @click="openUserEdit(row)"
                    >
                      编辑
                    </el-button>
                    <el-button
                      v-button-permission="{ code: 'iam_user:toggle', fallback: 'iam:user:write' }"
                      link
                      :type="row.status === 'ENABLED' ? 'danger' : 'success'"
                      @click="toggleUser(row)"
                    >
                      {{ row.status === "ENABLED" ? "停用" : "启用" }}
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>

            <!-- 底部通用分页器 (Precision Glass Pagination) -->
            <div class="user-pagination-bar">
              <el-pagination
                v-model:current-page="userPage"
                v-model:page-size="userPageSize"
                :page-sizes="[10, 20, 50, 100]"
                :total="filteredUsers.length"
                layout="total, sizes, prev, pager, next, jumper"
                @size-change="userPage = 1"
              />
            </div>
          </BaseCard>
        </main>
      </div>
    </div>

    <!-- Role Section: Table or Matrix -->
    <div v-if="!mode || mode === 'all' || mode === 'role'" class="role-section-wrapper">
      <!-- Prominent Segmented View Switcher -->
      <div class="role-view-control-bar">
        <div class="view-switch-box">
          <el-radio-group v-model="roleViewMode" size="default" class="segmented-role-switch">
            <el-radio-button value="table">
              <el-icon><List /></el-icon>
              <span>角色列表 ({{ roles.length }})</span>
            </el-radio-button>
            <el-radio-button value="matrix">
              <el-icon><Grid /></el-icon>
              <span>全景权限矩阵 (Matrix)</span>
            </el-radio-button>
          </el-radio-group>
        </div>
        <div class="view-right-actions">
          <el-button
            v-if="roleViewMode === 'table'"
            v-button-permission="{ code: 'iam_role:create', fallback: 'iam:role:write' }"
            type="primary"
            :icon="Plus"
            @click="openRoleCreate"
          >
            新增角色
          </el-button>
        </div>
      </div>

      <ProTable
        v-if="roleViewMode === 'table'"
        :columns="roleColumns"
        :data="roles"
        :loading="loading"
        selectable
        compact
        title="角色注册表"
        subtitle="角色负责能力编排，点击「配置权限」在统一抽屉中一站式治理功能、数据范围与成员。"
      >
        <template #bulkActions="{ rows }">
          <el-button type="primary" plain @click="batchEnableRoles(asRoleRows(rows))">批量启用</el-button>
          <el-button plain @click="batchDisableRoles(asRoleRows(rows))">批量停用</el-button>
        </template>
        <template #status="{ row }">
          <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
        </template>
        <template #actions="{ row }">
          <el-button
            link
            type="primary"
            :icon="Key"
            class="config-perm-btn"
            title="一站式配置页面权限、数据范围与授权成员"
            @click="openRoleConfig(row)"
          >
            配置权限
          </el-button>
          <el-button
            link
            type="primary"
            :icon="CopyDocument"
            title="一键克隆该角色的功能权限与数据范围"
            @click="openCloneDialog(row)"
          >
            复制
          </el-button>
          <el-button v-button-permission="{ code: 'iam_role:edit', fallback: 'iam:role:write' }" link type="primary" @click="openRoleEdit(row)">编辑</el-button>
          <el-button v-button-permission="{ code: 'iam_role:toggle', fallback: 'iam:role:write' }" link :type="row.status === 'ENABLED' ? 'danger' : 'success'" @click="toggleRole(row)">{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
        </template>
      </ProTable>

      <!-- Matrix Mode -->
      <div v-else class="matrix-mode-wrapper">
        <IamPermissionMatrix
          :roles="roles"
          :menus="menus"
          :menu-permissions="menuPermissions"
          :data-permission-rules="dataPermissionRules"
          :loading="loading"
          @open-role-config="openRoleConfig"
        />
      </div>
    </div>

    <!-- Clone Role Dialog -->
    <el-dialog
      v-model="cloneDialogVisible"
      title="一键克隆 / 复制角色"
      width="520px"
      :destroy-on-close="true"
    >
      <div class="clone-dialog-content">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 16px"
        >
          <template #title>
            将深度复制原角色「<strong>{{ sourceRole?.roleName }}</strong>」包含的全部<strong>页面访问权限</strong>、<strong>操作按钮权限</strong>以及<strong>数据行级范围策略</strong>。
          </template>
        </el-alert>

        <el-form label-position="top">
          <el-form-item label="新角色名称" required>
            <el-input v-model="cloneForm.roleName" placeholder="例如：华南大区主管" />
          </el-form-item>
          <el-form-item label="新角色唯一编码" required>
            <el-input v-model="cloneForm.roleCode" placeholder="例如：south_director" />
          </el-form-item>
          <el-form-item label="备注说明">
            <el-input
              v-model="cloneForm.remark"
              type="textarea"
              :rows="2"
              placeholder="请输入角色定位与说明"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="cloneDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="cloningRole" @click="handleConfirmClone">
            确认克隆
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 3-in-1 Role Drawer -->
    <IamRoleDrawer
      v-model:visible="roleDrawerVisible"
      :role="activeDrawerRole"
      :menus="menus"
      :menu-permissions="menuPermissions"
      :data-permission-rules="dataPermissionRules"
      :departments="departments"
      :positions="positions"
      :users="users"
      :current-tenant-id="currentTenantId"
      :can-write="canWrite"
      @saved="handleRoleSaved"
    />

    <!-- Member Permission Inspector -->
    <IamPermissionInspector
      v-model:visible="inspectorVisible"
      :initial-user-id="inspectorTargetUserId"
      :users="users"
      :departments="departments"
      :menus="menus"
      :current-tenant-id="currentTenantId"
    />

    <ExportDialog
      v-model:visible="iamExportVisible"
      source-code="iam_user"
      default-title="IAM 企业用户台账（自动脱敏导出）"
    />
  </div>
</template>

<style scoped lang="scss">
/* User Enterprise Workspace Styles */
.user-workspace-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.user-workspace-split {
  display: grid;
  grid-template-columns: 270px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.user-tree-panel {
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(28px) saturate(210%) contrast(104%);
  -webkit-backdrop-filter: blur(28px) saturate(210%) contrast(104%);
  border: 1px solid rgba(255, 255, 255, 0.88);
  border-radius: 20px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 16px 40px -10px rgba(12, 34, 56, 0.08);
  display: flex;
  flex-direction: column;
  min-height: 580px;

  .tree-panel-header {
    padding: 14px 16px;
    border-bottom: 1px solid rgba(226, 232, 240, 0.6);

    .panel-title-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 10px;

      .panel-title {
        font-size: 14px;
        font-weight: 650;
        color: #0f172a;
      }
    }

    .tree-search-input {
      :deep(.el-input__wrapper) {
        border-radius: 6px;
        background: rgba(255, 255, 255, 0.75);
      }
    }
  }

  .tree-scroll-container {
    flex: 1;
    overflow-y: auto;
    padding: 8px 6px;

    :deep(.el-tree-node__content) {
      height: 36px;
      border-radius: 6px;
      padding-right: 8px;
      margin: 1px 0;
      transition: all 120ms ease;

      &:hover {
        background-color: rgba(241, 245, 249, 0.85);
      }
    }

    :deep(.el-tree-node.is-current > .el-tree-node__content) {
      background-color: rgba(219, 234, 254, 0.85);
      color: #1d4ed8;
      font-weight: 600;

      .node-icon {
        color: #2563eb;
      }

      .node-badge {
        background-color: #2563eb;
        color: #ffffff;
      }
    }
  }
}

.custom-tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  font-size: 13px;

  .node-main {
    display: flex;
    align-items: center;
    gap: 7px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;

    .node-icon {
      font-size: 15px;
      color: #64748b;
    }

    .node-label {
      color: #1e293b;
      font-weight: 500;
    }
  }

  .node-badge {
    padding: 1px 6px;
    font-size: 11px;
    font-weight: 550;
    border-radius: 10px;
    background: rgba(241, 245, 249, 0.9);
    color: #475569;
    font-family: var(--sb-font-mono);
  }
}

.user-content-panel {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.user-pagination-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 14px 16px;
  border-top: 1px solid rgba(226, 232, 240, 0.6);
  background: rgba(255, 255, 255, 0.35);
  backdrop-filter: blur(10px);
}

.active-dept-filter-tag {
  font-weight: 550;
}

/* Sleek User Telemetry Strip */
.user-telemetry-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--el-bg-color, #fff);
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
      &.emerald { background: #ecfdf5; color: #059669; }
      &.amber { background: #fffbeb; color: #d97706; }
      &.indigo { background: #eef2ff; color: #4f46e5; }
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

.user-table-card {
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px rgb(0 0 0 / 0.03);

  :deep(.el-card__header) {
    padding: 14px 18px;
    border-bottom: 1px solid #f1f5f9;
  }

  :deep(.el-card__body) {
    padding: 0;
  }
}

.user-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;

  .toolbar-filters {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .toolbar-actions {
    display: flex;
    align-items: center;
    gap: 10px;
  }
}

.batch-selection-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background: rgba(239, 246, 255, 0.65);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid rgba(219, 234, 254, 0.6);

  .batch-hint {
    font-size: 13px;
    color: #1e40af;

    strong {
      color: #1d4ed8;
      font-size: 14px;
    }
  }

  .batch-btn-group {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.enterprise-user-table {
  width: 100%;
  background: transparent !important;

  :deep(.el-table__header) th {
    background: rgba(255, 255, 255, 0.45) !important;
    backdrop-filter: blur(14px);
    -webkit-backdrop-filter: blur(14px);
    color: var(--sb-text-primary);
    font-size: 12.5px;
    font-weight: 650;
    padding: 11px 0;
    border-bottom: 1px solid rgba(203, 213, 225, 0.6) !important;
  }

  :deep(.el-table__row) {
    background: transparent !important;
    td {
      background: transparent !important;
      padding: 10px 0;
      border-bottom: 1px solid rgba(226, 232, 240, 0.45) !important;
    }
    &:hover td {
      background: rgba(239, 246, 255, 0.65) !important;
      backdrop-filter: blur(8px);
    }
  }

  .user-profile-cell {
    display: flex;
    align-items: center;
    gap: 12px;

    .user-avatar {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 15px;
      flex-shrink: 0;
      box-shadow: 0 2px 5px rgb(0 0 0 / 0.1);
    }

    .user-info-text {
      display: flex;
      flex-direction: column;
      gap: 3px;
      min-width: 0;

      .user-primary-row {
        display: flex;
        align-items: center;
        gap: 6px;

        .user-nickname {
          font-size: 13px;
          color: #0f172a;
          font-weight: 600;
        }

        .user-username-badge {
          font-size: 11px;
          font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
          background: #f1f5f9;
          color: #64748b;
          padding: 1px 5px;
          border-radius: 4px;
        }
      }

      .user-code-row {
        .code-copy-btn {
          border: none;
          background: transparent;
          display: inline-flex;
          align-items: center;
          gap: 4px;
          padding: 0;
          font-size: 11px;
          color: #94a3b8;
          font-family: ui-monospace, SFMono-Regular, monospace;
          cursor: pointer;
          transition: color 0.15s ease;

          &:hover {
            color: #2563eb;
          }
        }
      }
    }
  }

  .user-type-tag {
    font-weight: 600;
    font-size: 12px;
  }

  .employee-binding-cell {
    .bound-emp-pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 3px 8px;
      border-radius: 6px;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      cursor: pointer;
      transition: all 0.15s ease;

      &:hover {
        background: #f1f5f9;
        border-color: #cbd5e1;
      }

      .emp-icon {
        color: #4f46e5;
        font-size: 13px;
      }

      .emp-name {
        font-size: 12px;
        font-weight: 500;
        color: #1e293b;
      }

      .emp-no {
        font-size: 11px;
        color: #64748b;
        font-family: monospace;
      }
    }
  }

  .unbound-emp-cell {
    .unbound-tag {
      font-size: 11px;
    }
  }

  .contact-cell {
    display: flex;
    flex-direction: column;
    gap: 2px;
    font-size: 12px;

    .contact-item {
      display: flex;
      align-items: center;
      gap: 4px;
      color: #334155;

      &.text-muted {
        color: #64748b;
      }
    }

    .text-placeholder {
      color: #94a3b8;
    }
  }

  .action-cell {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.emp-popover-detail {
  padding: 4px;

  .emp-pop-title {
    font-size: 13px;
    font-weight: 600;
    color: #0f172a;
    padding-bottom: 6px;
    margin-bottom: 6px;
    border-bottom: 1px solid #f1f5f9;
  }

  .emp-pop-item {
    display: flex;
    font-size: 12px;
    line-height: 1.8;

    .pop-label {
      width: 66px;
      color: #64748b;
      flex-shrink: 0;
    }

    .pop-val {
      color: #1e293b;
      font-weight: 500;
    }
  }
}

.user-toolbar-cluster {
  display: flex;
  align-items: center;
  gap: 10px;
}

.role-section-wrapper {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .role-view-control-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 20px;
    background: rgba(255, 255, 255, 0.58);
    backdrop-filter: blur(28px) saturate(210%) contrast(104%);
    -webkit-backdrop-filter: blur(28px) saturate(210%) contrast(104%);
    border-radius: var(--sb-radius-glass-md, 18px);
    border: 1px solid rgba(255, 255, 255, 0.85);
    box-shadow:
      inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98),
      0 10px 25px -5px rgba(12, 34, 56, 0.05);

    .segmented-role-switch {
      :deep(.el-radio-button__inner) {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 8px 16px;
        font-size: 13px;
        font-weight: 500;
      }
    }

    .view-right-actions {
      display: flex;
      align-items: center;
      gap: 10px;
    }
  }
}

.config-perm-btn {
  font-weight: 600;
  color: #2563eb;
}

.matrix-mode-wrapper {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.clone-dialog-content {
  padding: 4px 0;
}
</style>
