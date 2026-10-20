<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  ArrowRight,
  Check,
  Close,
  Cpu,
  Delete,
  Key,
  Link,
  OfficeBuilding,
  Plus,
  Refresh,
  Search,
  User,
  UserFilled
} from "@element-plus/icons-vue";
import {
  fetchRoleUserAssignment,
  fetchUserRoleAssignment,
  saveRoleUserAssignment,
  saveUserRoleAssignment
} from "@/api/modules/iam";
import type {
  IamDepartmentRow,
  IamEmployeeRow,
  IamRoleRow,
  IamUserRow
} from "@/api/modules/iam";
import BaseCard from "@/components/base/BaseCard.vue";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import ProTable, { type ProTableColumn } from "@/components/pro/ProTable.vue";

const props = withDefaults(
  defineProps<{
    mode?: "all" | "user-role" | "role-api";
    userColumns?: ProTableColumn[];
    roleColumns?: ProTableColumn[];
    users: IamUserRow[];
    roles: IamRoleRow[];
    departments?: IamDepartmentRow[];
    employees?: IamEmployeeRow[];
    loading?: boolean;
    assigningUser?: boolean;
    assigningRole?: boolean;
    roleOptions?: Array<{ label: string; value: number }>;
    apiResourceOptions?: Array<{ label: string; value: number }>;
    openUserEdit?: (row: IamUserRow) => void;
    toggleUser?: (row: IamUserRow) => void;
    selectUser?: (userId: number | null) => void;
    openRoleEdit?: (row: IamRoleRow) => void;
    toggleRole?: (row: IamRoleRow) => void;
    selectRole?: (roleId: number | null) => void;
  }>(),
  {
    mode: "user-role",
    departments: () => [],
    employees: () => [],
    loading: false,
    assigningUser: false,
    assigningRole: false,
    roleOptions: () => [],
    apiResourceOptions: () => []
  }
);

const emit = defineEmits<{
  (e: "openInspector", userId?: number): void;
}>();

// Active assignment tab
const activeAssignmentPerspective = ref<"by-role" | "by-user" | "by-api">(
  props.mode === "role-api" ? "by-api" : "by-role"
);

watch(
  () => props.mode,
  (mode) => {
    if (mode === "role-api") {
      activeAssignmentPerspective.value = "by-api";
    } else if (activeAssignmentPerspective.value === "by-api") {
      activeAssignmentPerspective.value = "by-role";
    }
  }
);

// ================= Perspective 1: By Role (Role -> Members) =================
const roleFilterKeyword = ref("");
const activeRoleId = ref<number | null>(null);
const roleUsersLoading = ref(false);
const assignedUserIds = ref<number[]>([]);
const selectedMemberRows = ref<IamUserRow[]>([]);
const addMembersDialogVisible = ref(false);
const selectedNewUserIds = ref<number[]>([]);
const addMemberSearch = ref("");
const addMemberDeptFilter = ref<number | null>(null);
const savingRoleUsers = ref(false);

const filteredRoles = computed(() => {
  const kw = roleFilterKeyword.value.trim().toLowerCase();
  return props.roles.filter(
    (r) => !kw || r.roleName.toLowerCase().includes(kw) || r.roleCode.toLowerCase().includes(kw)
  );
});

const currentSelectedRole = computed<IamRoleRow | null>(
  () => props.roles.find((r) => r.id === activeRoleId.value) || props.roles[0] || null
);

// Map of roleId -> count of assigned users
const roleUserCountMap = reactive<Record<number, number>>({});

async function loadRoleMembers(roleId: number) {
  roleUsersLoading.value = true;
  try {
    const res = await fetchRoleUserAssignment(roleId);
    assignedUserIds.value = res.userIds || [];
    roleUserCountMap[roleId] = (res.userIds || []).length;
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "加载角色成员列表失败");
  } finally {
    roleUsersLoading.value = false;
  }
}

// All users assigned to current active role
const assignedUsers = computed<IamUserRow[]>(() => {
  const idSet = new Set(assignedUserIds.value);
  return props.users.filter((u) => idSet.has(u.id));
});

function handleRoleSelect(role: IamRoleRow) {
  activeRoleId.value = role.id;
  loadRoleMembers(role.id);
}

function openAddMembersDialog() {
  selectedNewUserIds.value = [];
  addMemberSearch.value = "";
  addMemberDeptFilter.value = null;
  addMembersDialogVisible.value = true;
}

const availableCandidatesForRole = computed(() => {
  const assignedSet = new Set(assignedUserIds.value);
  const kw = addMemberSearch.value.trim().toLowerCase();
  const deptFilter = addMemberDeptFilter.value;

  return props.users.filter((u) => {
    if (assignedSet.has(u.id)) return false;
    if (kw) {
      const matchName = (u.nickname || "").toLowerCase().includes(kw);
      const matchUser = (u.username || "").toLowerCase().includes(kw);
      const matchMobile = (u.mobile || "").includes(kw);
      if (!matchName && !matchUser && !matchMobile) return false;
    }
    if (deptFilter !== null) {
      const emp = props.employees.find((e) => e.id === u.employeeId);
      if (!emp || emp.deptId !== deptFilter) return false;
    }
    return true;
  });
});

async function handleConfirmAddMembers() {
  if (!currentSelectedRole.value || !selectedNewUserIds.value.length) {
    ElMessage.warning("请至少选择一位候选成员");
    return;
  }
  savingRoleUsers.value = true;
  try {
    const updatedUserIds = Array.from(new Set([...assignedUserIds.value, ...selectedNewUserIds.value]));
    await saveRoleUserAssignment({
      roleId: currentSelectedRole.value.id,
      tenantId: currentSelectedRole.value.tenantId || 1,
      userIds: updatedUserIds
    });
    ElMessage.success(`已为角色「${currentSelectedRole.value.roleName}」成功添加 ${selectedNewUserIds.value.length} 位成员！`);
    addMembersDialogVisible.value = false;
    await loadRoleMembers(currentSelectedRole.value.id);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "添加角色成员失败");
  } finally {
    savingRoleUsers.value = false;
  }
}

async function handleRemoveMember(user: IamUserRow) {
  if (!currentSelectedRole.value) return;
  try {
    await ElMessageBox.confirm(
      `确定从角色「${currentSelectedRole.value.roleName}」中移除成员「${user.nickname} (${user.username})」吗？`,
      "移除角色成员确认",
      { type: "warning", confirmButtonText: "确认移除", cancelButtonText: "取消" }
    );
    const updatedUserIds = assignedUserIds.value.filter((id) => id !== user.id);
    await saveRoleUserAssignment({
      roleId: currentSelectedRole.value.id,
      tenantId: currentSelectedRole.value.tenantId || 1,
      userIds: updatedUserIds
    });
    ElMessage.success(`已成功移除成员「${user.nickname}」！`);
    await loadRoleMembers(currentSelectedRole.value.id);
  } catch {
    // cancelled
  }
}

async function handleBatchRemoveMembers() {
  if (!currentSelectedRole.value || !selectedMemberRows.value.length) return;
  try {
    await ElMessageBox.confirm(
      `确定批量移除所选的 ${selectedMemberRows.value.length} 位成员吗？`,
      "批量移除确认",
      { type: "warning", confirmButtonText: "确认批量移除", cancelButtonText: "取消" }
    );
    const removeSet = new Set(selectedMemberRows.value.map((u) => u.id));
    const updatedUserIds = assignedUserIds.value.filter((id) => !removeSet.has(id));
    await saveRoleUserAssignment({
      roleId: currentSelectedRole.value.id,
      tenantId: currentSelectedRole.value.tenantId || 1,
      userIds: updatedUserIds
    });
    ElMessage.success(`已批量移除 ${selectedMemberRows.value.length} 位成员！`);
    selectedMemberRows.value = [];
    await loadRoleMembers(currentSelectedRole.value.id);
  } catch {
    // cancelled
  }
}

// ================= Perspective 2: By User (User -> Roles) =================
const userFilterKeyword = ref("");
const activeUserId = ref<number | null>(null);
const userRolesLoading = ref(false);
const userAssignedRoleIds = ref<number[]>([]);
const editUserRolesDialogVisible = ref(false);
const editingRoleIds = ref<number[]>([]);
const savingUserRoles = ref(false);

const filteredUsers = computed(() => {
  const kw = userFilterKeyword.value.trim().toLowerCase();
  return props.users.filter(
    (u) =>
      !kw ||
      (u.nickname && u.nickname.toLowerCase().includes(kw)) ||
      (u.username && u.username.toLowerCase().includes(kw)) ||
      (u.mobile && u.mobile.includes(kw))
  );
});

const currentSelectedUser = computed<IamUserRow | null>(
  () => props.users.find((u) => u.id === activeUserId.value) || props.users[0] || null
);

async function loadUserRoles(userId: number) {
  userRolesLoading.value = true;
  try {
    const user = props.users.find((u) => u.id === userId);
    const tenantId = user?.tenantId || 1;
    const res = await fetchUserRoleAssignment(userId, tenantId);
    userAssignedRoleIds.value = res.roleIds || [];
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "加载用户角色失败");
  } finally {
    userRolesLoading.value = false;
  }
}

const userRolesList = computed<IamRoleRow[]>(() => {
  const idSet = new Set(userAssignedRoleIds.value);
  return props.roles.filter((r) => idSet.has(r.id));
});

function handleUserSelect(user: IamUserRow) {
  activeUserId.value = user.id;
  loadUserRoles(user.id);
}

function openEditUserRolesDialog() {
  editingRoleIds.value = [...userAssignedRoleIds.value];
  editUserRolesDialogVisible.value = true;
}

async function handleConfirmEditUserRoles() {
  if (!currentSelectedUser.value) return;
  savingUserRoles.value = true;
  try {
    await saveUserRoleAssignment({
      userId: currentSelectedUser.value.id,
      tenantId: currentSelectedUser.value.tenantId || 1,
      roleIds: editingRoleIds.value
    });
    ElMessage.success(`用户「${currentSelectedUser.value.nickname}」的角色分配已成功更新！`);
    editUserRolesDialogVisible.value = false;
    await loadUserRoles(currentSelectedUser.value.id);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "保存用户角色失败");
  } finally {
    savingUserRoles.value = false;
  }
}

async function handleRemoveUserRole(role: IamRoleRow) {
  if (!currentSelectedUser.value) return;
  try {
    await ElMessageBox.confirm(
      `确定为用户「${currentSelectedUser.value.nickname}」解除角色「${role.roleName}」的授信吗？`,
      "解除角色确认",
      { type: "warning", confirmButtonText: "确认解除", cancelButtonText: "取消" }
    );
    const updatedRoleIds = userAssignedRoleIds.value.filter((id) => id !== role.id);
    await saveUserRoleAssignment({
      userId: currentSelectedUser.value.id,
      tenantId: currentSelectedUser.value.tenantId || 1,
      roleIds: updatedRoleIds
    });
    ElMessage.success(`已成功解除角色「${role.roleName}」！`);
    await loadUserRoles(currentSelectedUser.value.id);
  } catch {
    // cancelled
  }
}

// Helpers
function getEmployee(employeeId?: number | null) {
  if (!employeeId || employeeId <= 0) return null;
  return props.employees?.find((e) => e.id === employeeId) || null;
}

function getDeptName(deptId?: number | null) {
  if (!deptId) return "-";
  return props.departments?.find((d) => d.id === deptId)?.deptName || `部门 #${deptId}`;
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

onMounted(() => {
  if (props.roles.length > 0 && !activeRoleId.value) {
    activeRoleId.value = props.roles[0].id;
    loadRoleMembers(props.roles[0].id);
  }
  if (props.users.length > 0 && !activeUserId.value) {
    activeUserId.value = props.users[0].id;
    loadUserRoles(props.users[0].id);
  }
});
</script>

<template>
  <div class="enterprise-assignment-container">
    <!-- Top Mode Selector Tabs -->
    <div class="assignment-header-bar">
      <div class="perspective-nav">
        <el-segmented
          v-model="activeAssignmentPerspective"
          :options="[
            { label: '按角色分配成员 (Role-to-Users)', value: 'by-role' },
            { label: '按人员分配角色 (User-to-Roles)', value: 'by-user' }
          ]"
        />
      </div>
      <div class="perspective-meta">
        <el-tag type="info" effect="plain" class="meta-tag">
          全平台共 {{ roles.length }} 个业务角色 · {{ users.length }} 个实名账号
        </el-tag>
      </div>
    </div>

    <!-- ================= PERSPECTIVE 1: ROLE-TO-USERS ================= -->
    <div v-if="activeAssignmentPerspective === 'by-role'" class="split-assignment-layout">
      <!-- Left Rail: Roles List -->
      <aside class="assignment-rail">
        <div class="rail-header">
          <div class="rail-title-row">
            <strong>业务角色清单</strong>
            <el-badge :value="filteredRoles.length" type="primary" class="rail-badge" />
          </div>
          <el-input
            v-model="roleFilterKeyword"
            placeholder="搜索角色名称/编码..."
            clearable
            size="small"
            :prefix-icon="Search"
            style="margin-top: 8px"
          />
        </div>

        <div class="rail-items-scroll">
          <button
            v-for="role in filteredRoles"
            :key="role.id"
            class="rail-card-btn"
            :class="{ 'is-active': activeRoleId === role.id }"
            @click="handleRoleSelect(role)"
          >
            <div class="card-icon-tag" :class="role.roleType === 'SYSTEM' ? 'is-system' : 'is-custom'">
              <el-icon><Key /></el-icon>
            </div>
            <div class="card-meta">
              <span class="role-name">{{ role.roleName }}</span>
              <code class="role-code">{{ role.roleCode }}</code>
            </div>
            <div class="card-tail">
              <span v-if="roleUserCountMap[role.id] !== undefined" class="count-pill">
                {{ roleUserCountMap[role.id] }} 人
              </span>
              <el-icon class="arrow-icon"><ArrowRight /></el-icon>
            </div>
          </button>
        </div>
      </aside>

      <!-- Right Main: Role Members ProTable -->
      <main class="assignment-main">
        <BaseCard class="role-detail-card" v-if="currentSelectedRole">
          <template #header>
            <div class="main-panel-header">
              <div class="role-info-head">
                <div class="role-title-line">
                  <h3 class="role-name-lg">{{ currentSelectedRole.roleName }}</h3>
                  <el-tag size="small" type="primary" effect="plain">{{ currentSelectedRole.roleCode }}</el-tag>
                  <el-tag size="small" :type="currentSelectedRole.status === 'ENABLED' ? 'success' : 'danger'">
                    {{ currentSelectedRole.status === 'ENABLED' ? '启用中' : '已停用' }}
                  </el-tag>
                  <el-tag size="small" type="warning" effect="plain">
                    数据范围: {{ currentSelectedRole.dataScopeType || '默认策略' }}
                  </el-tag>
                </div>
                <p class="role-desc-text">
                  {{ currentSelectedRole.remark || '暂无说明备注。承接此角色的用户将直接获得对应菜单模块与操作按钮权限。' }}
                </p>
              </div>

              <div class="role-actions-head">
                <el-button
                  type="danger"
                  plain
                  size="small"
                  :icon="Delete"
                  :disabled="!selectedMemberRows.length"
                  @click="handleBatchRemoveMembers"
                >
                  批量移除 ({{ selectedMemberRows.length }})
                </el-button>
                <el-button
                  type="primary"
                  size="small"
                  :icon="Plus"
                  @click="openAddMembersDialog"
                >
                  为该角色添加成员
                </el-button>
                <el-button
                  size="small"
                  :icon="Refresh"
                  :loading="roleUsersLoading"
                  @click="loadRoleMembers(currentSelectedRole.id)"
                >
                  刷新
                </el-button>
              </div>
            </div>
          </template>

          <!-- Assigned Members Table -->
          <div class="members-table-wrap">
            <el-table
              :data="assignedUsers"
              v-loading="roleUsersLoading"
              row-key="id"
              stripe
              class="enterprise-members-table"
              @selection-change="(rows) => selectedMemberRows = rows"
            >
              <el-table-column type="selection" width="48" align="center" />
              <el-table-column label="成员账号与姓名" min-width="200">
                <template #default="{ row }">
                  <div class="member-user-cell">
                    <el-avatar
                      :size="32"
                      :style="{ background: getAvatarColor(row.nickname || row.username), color: '#fff', fontWeight: 600 }"
                    >
                      {{ (row.nickname || row.username || 'U').slice(0, 1).toUpperCase() }}
                    </el-avatar>
                    <div class="user-meta">
                      <strong class="user-nickname">{{ row.nickname }}</strong>
                      <span class="user-username">@{{ row.username }}</span>
                    </div>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="实名员工档案" min-width="160">
                <template #default="{ row }">
                  <div v-if="getEmployee(row.employeeId)" class="emp-cell">
                    <span class="emp-name">{{ getEmployee(row.employeeId)?.employeeName }}</span>
                    <small class="emp-no">{{ getEmployee(row.employeeId)?.employeeNo }}</small>
                  </div>
                  <span v-else class="text-muted">未关联实名</span>
                </template>
              </el-table-column>

              <el-table-column label="所属组织部门" min-width="160">
                <template #default="{ row }">
                  <el-tag size="small" type="info" effect="plain" v-if="getEmployee(row.employeeId)">
                    {{ getDeptName(getEmployee(row.employeeId)?.deptId) }}
                  </el-tag>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>

              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <BaseStatusTag :status="row.status" />
                </template>
              </el-table-column>

              <el-table-column label="操作" width="160" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="emit('openInspector', row.id)">
                    权限透视
                  </el-button>
                  <el-button link type="danger" size="small" @click="handleRemoveMember(row)">
                    移除授信
                  </el-button>
                </template>
              </el-table-column>

              <template #empty>
                <div class="custom-empty-state">
                  <div class="empty-icon-wrap">
                    <el-icon :size="40" color="#94a3b8"><UserFilled /></el-icon>
                  </div>
                  <h4 class="empty-title">该角色当前暂无成员</h4>
                  <p class="empty-desc">该角色已就绪但未指派给任何员工，点击下方按钮立即添加。</p>
                  <el-button type="primary" size="small" :icon="Plus" @click="openAddMembersDialog">
                    立即添加成员
                  </el-button>
                </div>
              </template>
            </el-table>
          </div>
        </BaseCard>
      </main>
    </div>

    <!-- ================= PERSPECTIVE 2: USER-TO-ROLES ================= -->
    <div v-else-if="activeAssignmentPerspective === 'by-user'" class="split-assignment-layout">
      <!-- Left Rail: Users List -->
      <aside class="assignment-rail">
        <div class="rail-header">
          <div class="rail-title-row">
            <strong>系统用户账号</strong>
            <el-badge :value="filteredUsers.length" type="primary" class="rail-badge" />
          </div>
          <el-input
            v-model="userFilterKeyword"
            placeholder="搜索用户名/显示名..."
            clearable
            size="small"
            :prefix-icon="Search"
            style="margin-top: 8px"
          />
        </div>

        <div class="rail-items-scroll">
          <button
            v-for="user in filteredUsers"
            :key="user.id"
            class="rail-card-btn"
            :class="{ 'is-active': activeUserId === user.id }"
            @click="handleUserSelect(user)"
          >
            <el-avatar
              :size="30"
              :style="{ background: getAvatarColor(user.nickname || user.username), color: '#fff', fontSize: '12px' }"
            >
              {{ (user.nickname || user.username || 'U').slice(0, 1).toUpperCase() }}
            </el-avatar>
            <div class="card-meta">
              <span class="role-name">{{ user.nickname }}</span>
              <code class="role-code">@{{ user.username }}</code>
            </div>
            <div class="card-tail">
              <span class="status-indicator" :class="user.status === 'ENABLED' ? 'is-green' : 'is-gray'" />
              <el-icon class="arrow-icon"><ArrowRight /></el-icon>
            </div>
          </button>
        </div>
      </aside>

      <!-- Right Main: User Roles Canvas -->
      <main class="assignment-main">
        <BaseCard class="user-detail-card" v-if="currentSelectedUser">
          <template #header>
            <div class="main-panel-header">
              <div class="user-info-head">
                <el-avatar
                  :size="48"
                  :style="{ background: getAvatarColor(currentSelectedUser.nickname || currentSelectedUser.username), color: '#fff', fontSize: '18px', fontWeight: 600 }"
                >
                  {{ (currentSelectedUser.nickname || currentSelectedUser.username || 'U').slice(0, 1).toUpperCase() }}
                </el-avatar>
                <div class="user-header-text">
                  <div class="user-title-line">
                    <h3 class="user-name-lg">{{ currentSelectedUser.nickname }}</h3>
                    <code class="user-code-badge">@{{ currentSelectedUser.username }}</code>
                    <BaseStatusTag :status="currentSelectedUser.status" />
                    <el-tag size="small" type="info" effect="plain" v-if="getEmployee(currentSelectedUser.employeeId)">
                      {{ getDeptName(getEmployee(currentSelectedUser.employeeId)?.deptId) }}
                    </el-tag>
                  </div>
                  <p class="user-contact-text">
                    {{ currentSelectedUser.mobile ? `手机: ${currentSelectedUser.mobile}` : '' }}
                    {{ currentSelectedUser.email ? ` · 邮箱: ${currentSelectedUser.email}` : '' }}
                    {{ !currentSelectedUser.mobile && !currentSelectedUser.email ? '账号身份凭证已核验有效' : '' }}
                  </p>
                </div>
              </div>

              <div class="user-actions-head">
                <el-button
                  type="success"
                  plain
                  size="small"
                  :icon="Cpu"
                  @click="emit('openInspector', currentSelectedUser.id)"
                >
                  权限透视诊断
                </el-button>
                <el-button
                  type="primary"
                  size="small"
                  :icon="Key"
                  @click="openEditUserRolesDialog"
                >
                  调整分配角色 ({{ userRolesList.length }})
                </el-button>
                <el-button
                  size="small"
                  :icon="Refresh"
                  :loading="userRolesLoading"
                  @click="loadUserRoles(currentSelectedUser.id)"
                >
                  刷新
                </el-button>
              </div>
            </div>
          </template>

          <!-- Current Assigned Roles Cards Grid -->
          <div class="user-roles-workspace">
            <div class="roles-section-header">
              <h4>当前拥有角色权限 ({{ userRolesList.length }})</h4>
              <span class="header-hint">角色授予的菜单路由与功能操作将合并为此账号的最终准入范围。</span>
            </div>

            <div v-if="userRolesList.length" class="roles-cards-grid">
              <div v-for="role in userRolesList" :key="role.id" class="role-grid-card">
                <div class="card-top">
                  <div class="role-badge-icon">
                    <el-icon><Key /></el-icon>
                  </div>
                  <div class="role-main-info">
                    <strong class="card-role-title">{{ role.roleName }}</strong>
                    <code class="card-role-code">{{ role.roleCode }}</code>
                  </div>
                  <el-button
                    type="danger"
                    link
                    size="small"
                    class="remove-role-btn"
                    @click="handleRemoveUserRole(role)"
                  >
                    解除
                  </el-button>
                </div>
                <div class="card-bottom">
                  <div class="scope-pill">
                    <span>数据范围:</span>
                    <strong>{{ role.dataScopeType || '全局' }}</strong>
                  </div>
                  <span class="role-type-badge">{{ role.roleType === 'SYSTEM' ? '内置系统角色' : '自定义业务角色' }}</span>
                </div>
              </div>
            </div>

            <div v-else class="custom-empty-state">
              <div class="empty-icon-wrap">
                <el-icon :size="40" color="#94a3b8"><Key /></el-icon>
              </div>
              <h4 class="empty-title">当前账号尚未分配任何角色</h4>
              <p class="empty-desc">该账号尚无任何业务角色，无法访问非公开的功能模块。</p>
              <el-button type="primary" size="small" :icon="Plus" @click="openEditUserRolesDialog">
                立即分配角色
              </el-button>
            </div>
          </div>
        </BaseCard>
      </main>
    </div>

    <!-- ================= ADD MEMBERS MODAL ================= -->
    <el-dialog
      v-model="addMembersDialogVisible"
      :title="`为角色「${currentSelectedRole?.roleName}」批量添加成员`"
      width="640px"
      :close-on-click-modal="false"
    >
      <div class="add-members-modal-body">
        <div class="modal-filters-row">
          <el-input
            v-model="addMemberSearch"
            placeholder="按姓名 / 手机 / 用户名快速过滤..."
            :prefix-icon="Search"
            clearable
            style="width: 60%"
          />
          <el-select
            v-model="addMemberDeptFilter"
            placeholder="筛选组织部门"
            clearable
            style="width: 38%"
          >
            <el-option
              v-for="dept in departments"
              :key="dept.id"
              :label="dept.deptName"
              :value="dept.id"
            />
          </el-select>
        </div>

        <div class="candidates-table-wrap">
          <el-table
            :data="availableCandidatesForRole"
            max-height="340"
            row-key="id"
            stripe
            @selection-change="(rows) => selectedNewUserIds = rows.map((r: any) => r.id)"
          >
            <el-table-column type="selection" width="48" align="center" />
            <el-table-column label="成员" min-width="160">
              <template #default="{ row }">
                <div class="candidate-row">
                  <strong class="cand-name">{{ row.nickname }}</strong>
                  <span class="cand-user">(@{{ row.username }})</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="部门" min-width="130">
              <template #default="{ row }">
                {{ getDeptName(getEmployee(row.employeeId)?.deptId) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <BaseStatusTag :status="row.status" />
              </template>
            </el-table-column>
          </el-table>
        </div>
        <p class="modal-summary-hint">
          已勾选 <strong>{{ selectedNewUserIds.length }}</strong> 位成员，确认后将立即建立角色映射。
        </p>
      </div>

      <template #footer>
        <el-button @click="addMembersDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="savingRoleUsers"
          :disabled="!selectedNewUserIds.length"
          @click="handleConfirmAddMembers"
        >
          确认添加授权 ({{ selectedNewUserIds.length }})
        </el-button>
      </template>
    </el-dialog>

    <!-- ================= EDIT USER ROLES MODAL ================= -->
    <el-dialog
      v-model="editUserRolesDialogVisible"
      :title="`调整用户「${currentSelectedUser?.nickname}」的分配角色`"
      width="560px"
      :close-on-click-modal="false"
    >
      <div class="edit-roles-modal-body">
        <p class="edit-roles-hint">请勾选需要授予此用户的业务角色，支持多角色组合：</p>
        <div class="role-checkboxes-grid">
          <el-checkbox-group v-model="editingRoleIds">
            <div v-for="role in roles" :key="role.id" class="role-check-item">
              <el-checkbox :label="role.id" border>
                <div class="check-content">
                  <span class="role-lbl">{{ role.roleName }}</span>
                  <code class="role-cd">{{ role.roleCode }}</code>
                </div>
              </el-checkbox>
            </div>
          </el-checkbox-group>
        </div>
      </div>

      <template #footer>
        <el-button @click="editUserRolesDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="savingUserRoles"
          @click="handleConfirmEditUserRoles"
        >
          保存角色分配
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.enterprise-assignment-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.assignment-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--sb-surface, #fff);
  border-radius: var(--sb-border-radius-md, 8px);
  border: 1px solid var(--sb-border-color, #e2e8f0);

  .meta-tag {
    font-size: 13px;
  }
}

.split-assignment-layout {
  display: grid;
  grid-template-columns: 290px minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
  min-height: 600px;
}

.assignment-rail {
  display: flex;
  flex-direction: column;
  background: var(--sb-surface, #fff);
  border-radius: var(--sb-border-radius-md, 8px);
  border: 1px solid var(--sb-border-color, #e2e8f0);
  overflow: hidden;

  .rail-header {
    padding: 14px 16px;
    border-bottom: 1px solid var(--sb-border-color, #f1f5f9);

    .rail-title-row {
      display: flex;
      justify-content: space-between;
      align-items: center;

      strong {
        font-size: 14px;
        color: var(--sb-text-primary, #0f172a);
      }
    }
  }

  .rail-items-scroll {
    flex: 1;
    overflow-y: auto;
    padding: 8px;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .rail-card-btn {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    width: 100%;
    text-align: left;
    background: transparent;
    border: 1px solid transparent;
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.15s ease-in-out;

    &:hover {
      background: #f8fafc;
    }

    &.is-active {
      background: #f0fdf4;
      border-color: #bbf7d0;

      .role-name {
        color: #15803d;
        font-weight: 600;
      }
    }

    .card-icon-tag {
      width: 28px;
      height: 28px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;

      &.is-system {
        background: #eff6ff;
        color: #2563eb;
      }

      &.is-custom {
        background: #f5f3ff;
        color: #7c3aed;
      }
    }

    .card-meta {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;

      .role-name {
        font-size: 13px;
        color: var(--sb-text-primary, #1e293b);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .role-code {
        font-size: 11px;
        color: var(--sb-text-secondary, #64748b);
        font-family: ui-monospace, monospace;
      }
    }

    .card-tail {
      display: flex;
      align-items: center;
      gap: 6px;

      .count-pill {
        padding: 2px 6px;
        border-radius: 10px;
        background: #e2e8f0;
        font-size: 11px;
        color: #475569;
        font-weight: 500;
      }

      .status-indicator {
        width: 8px;
        height: 8px;
        border-radius: 50%;

        &.is-green { background: #22c55e; }
        &.is-gray { background: #cbd5e1; }
      }

      .arrow-icon {
        font-size: 12px;
        color: #94a3b8;
      }
    }
  }
}

.assignment-main {
  min-width: 0;

  .role-detail-card,
  .user-detail-card {
    height: 100%;
    display: flex;
    flex-direction: column;
  }

  .main-panel-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 16px;
    width: 100%;
    padding: 4px 0;

    .role-info-head,
    .user-info-head {
      flex: 1;
      min-width: 0;
      display: flex;
      gap: 14px;
      align-items: center;
    }

    .user-header-text {
      flex: 1;
      min-width: 0;
    }

    .role-title-line,
    .user-title-line {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;

      .role-name-lg,
      .user-name-lg {
        margin: 0;
        font-size: 16px;
        font-weight: 600;
        color: var(--sb-text-primary, #0f172a);
      }

      .user-code-badge {
        font-size: 12px;
        padding: 2px 6px;
        background: #f1f5f9;
        border-radius: 4px;
        color: #475569;
      }
    }

    .role-desc-text,
    .user-contact-text {
      margin: 4px 0 0;
      font-size: 12px;
      color: var(--sb-text-secondary, #64748b);
    }

    .role-actions-head,
    .user-actions-head {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-shrink: 0;
    }
  }

  .member-user-cell {
    display: flex;
    align-items: center;
    gap: 10px;

    .user-meta {
      display: flex;
      flex-direction: column;

      .user-nickname {
        font-size: 13px;
        color: var(--sb-text-primary);
      }

      .user-username {
        font-size: 11px;
        color: var(--sb-text-secondary);
      }
    }
  }

  .emp-cell {
    display: flex;
    flex-direction: column;

    .emp-name {
      font-size: 13px;
      font-weight: 500;
    }

    .emp-no {
      font-size: 11px;
      color: var(--sb-text-secondary);
      font-family: ui-monospace, monospace;
    }
  }

  .text-muted {
    color: var(--sb-text-secondary);
    font-size: 12px;
  }

  .custom-empty-state {
    padding: 48px 0;
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;

    .empty-icon-wrap {
      width: 64px;
      height: 64px;
      border-radius: 50%;
      background: #f1f5f9;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 12px;
    }

    .empty-title {
      margin: 0;
      font-size: 15px;
      color: var(--sb-text-primary);
    }

    .empty-desc {
      margin: 6px 0 16px;
      font-size: 13px;
      color: var(--sb-text-secondary);
    }
  }

  .user-roles-workspace {
    padding: 16px 0;

    .roles-section-header {
      margin-bottom: 16px;

      h4 {
        margin: 0 0 4px;
        font-size: 14px;
        color: var(--sb-text-primary);
      }

      .header-hint {
        font-size: 12px;
        color: var(--sb-text-secondary);
      }
    }

    .roles-cards-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 14px;
    }

    .role-grid-card {
      border: 1px solid var(--sb-border-color, #e2e8f0);
      background: #fafafa;
      border-radius: 8px;
      padding: 14px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      transition: all 0.2s;

      &:hover {
        border-color: #cbd5e1;
        background: #fff;
        box-shadow: 0 4px 12px rgb(0 0 0 / 0.04);
      }

      .card-top {
        display: flex;
        align-items: center;
        gap: 10px;

        .role-badge-icon {
          width: 32px;
          height: 32px;
          border-radius: 6px;
          background: #eff6ff;
          color: #2563eb;
          display: flex;
          align-items: center;
          justify-content: center;
        }

        .role-main-info {
          flex: 1;
          min-width: 0;
          display: flex;
          flex-direction: column;

          .card-role-title {
            font-size: 14px;
            color: var(--sb-text-primary);
          }

          .card-role-code {
            font-size: 11px;
            color: var(--sb-text-secondary);
          }
        }
      }

      .card-bottom {
        display: flex;
        justify-content: space-between;
        align-items: center;
        border-top: 1px solid #f1f5f9;
        padding-top: 10px;
        font-size: 12px;

        .scope-pill {
          color: var(--sb-text-secondary);
          strong { color: #0284c7; margin-left: 4px; }
        }

        .role-type-badge {
          color: #64748b;
          font-size: 11px;
        }
      }
    }
  }
}

.add-members-modal-body {
  .modal-filters-row {
    display: flex;
    justify-content: space-between;
    margin-bottom: 12px;
  }

  .candidate-row {
    display: flex;
    align-items: center;
    gap: 6px;

    .cand-name { font-size: 13px; }
    .cand-user { font-size: 11px; color: var(--sb-text-secondary); }
  }

  .modal-summary-hint {
    margin: 12px 0 0;
    font-size: 12px;
    color: var(--sb-text-secondary);
    text-align: right;

    strong { color: #2563eb; }
  }
}

.edit-roles-modal-body {
  .edit-roles-hint {
    margin: 0 0 12px;
    font-size: 13px;
    color: var(--sb-text-secondary);
  }

  .role-checkboxes-grid {
    max-height: 320px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 8px;

    .role-check-item {
      :deep(.el-checkbox) {
        width: 100%;
        margin-right: 0;
        height: auto;
        padding: 8px 12px;
      }

      .check-content {
        display: flex;
        justify-content: space-between;
        align-items: center;
        width: 100%;
        margin-left: 8px;

        .role-lbl {
          font-weight: 500;
          font-size: 13px;
        }

        .role-cd {
          font-size: 11px;
          color: var(--sb-text-secondary);
        }
      }
    }
  }
}
</style>
