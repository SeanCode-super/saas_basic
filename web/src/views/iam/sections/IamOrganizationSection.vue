<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import {
  ArrowDown,
  Check,
  CircleCheck,
  CircleClose,
  CopyDocument,
  Folder,
  FolderOpened,
  Key,
  OfficeBuilding,
  Plus,
  Refresh,
  Search,
  Suitcase,
  Switch,
  Timer,
  User,
  UserFilled,
  View
} from "@element-plus/icons-vue";
import { ElMessage, type TreeInstance } from "element-plus";
import BaseCard from "@/components/base/BaseCard.vue";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
import type { IamDepartmentRow, IamEmployeeRow, IamPositionRow } from "@/api/modules/iam";

interface DeptTreeNode {
  id: number;
  label: string;
  deptCode: string;
  deptFullName: string;
  leaderName?: string;
  status: string;
  memberCount: number;
  raw: IamDepartmentRow;
  children: DeptTreeNode[];
}

const props = defineProps<{
  mode?: "all" | "department" | "position" | "employee";
  departmentColumns: ProTableColumn[];
  positionColumns: ProTableColumn[];
  employeeColumns: ProTableColumn[];
  departments: IamDepartmentRow[];
  positions: IamPositionRow[];
  employees: IamEmployeeRow[];
  loading: boolean;
  localizeEmployeeStatus: (value: string) => string;
  openDepartmentCreate: (parentId?: number) => void;
  openDepartmentEdit: (row: IamDepartmentRow) => void;
  toggleDepartment: (row: IamDepartmentRow) => void;
  batchEnableDepartments: (rows: IamDepartmentRow[]) => void | Promise<void>;
  batchDisableDepartments: (rows: IamDepartmentRow[]) => void | Promise<void>;
  openPositionCreate: (codePrefix?: string) => void;
  openPositionEdit: (row: IamPositionRow) => void;
  togglePosition: (row: IamPositionRow) => void;
  batchEnablePositions: (rows: IamPositionRow[]) => void | Promise<void>;
  batchDisablePositions: (rows: IamPositionRow[]) => void | Promise<void>;
  openEmployeeCreate: (deptId?: number) => void;
  openEmployeeEdit: (row: IamEmployeeRow) => void;
  openEmployeeTransfer: (row: IamEmployeeRow) => void;
  toggleEmployee: (row: IamEmployeeRow) => void;
  batchEnableEmployees: (rows: IamEmployeeRow[]) => void | Promise<void>;
  batchDisableEmployees: (rows: IamEmployeeRow[]) => void | Promise<void>;
}>();

const treeRef = ref<TreeInstance>();
const searchKeyword = ref("");
const selectedDeptId = ref<number | null>(null);
const activeDetailTab = ref<"members" | "positions" | "subDepts">("members");
const includeSubDepartments = ref(true);

const memberCountMap = computed(() => {
  const counts = new Map<number, number>();
  props.employees.forEach((emp) => {
    counts.set(emp.deptId, (counts.get(emp.deptId) || 0) + 1);
  });
  return counts;
});

const deptTreeData = computed(() => {
  const map = new Map<number, DeptTreeNode>();

  props.departments.forEach((dept) => {
    map.set(dept.id, {
      id: dept.id,
      label: dept.deptName,
      deptCode: dept.deptCode,
      deptFullName: dept.deptFullName || dept.deptName,
      leaderName: dept.leaderName,
      status: dept.status,
      memberCount: memberCountMap.value.get(dept.id) || 0,
      raw: dept,
      children: []
    });
  });

  const roots: DeptTreeNode[] = [];
  props.departments.forEach((dept) => {
    const node = map.get(dept.id);
    if (!node) return;
    const parentId = Number(dept.parentId ?? 0);
    if (parentId > 0 && map.has(parentId)) {
      map.get(parentId)!.children.push(node);
    } else {
      roots.push(node);
    }
  });

  return roots;
});

const selectedDept = computed<IamDepartmentRow | undefined>(() => {
  if (selectedDeptId.value !== null) {
    const found = props.departments.find((d) => d.id === selectedDeptId.value);
    if (found) return found;
  }
  return props.departments[0];
});

watch(
  () => props.departments,
  (list) => {
    if (list.length > 0 && selectedDeptId.value === null) {
      selectedDeptId.value = list[0]?.id ?? null;
      nextTick(() => {
        if (selectedDeptId.value && treeRef.value) {
          treeRef.value.setCurrentKey(selectedDeptId.value);
        }
      });
    }
  },
  { immediate: true }
);

watch(searchKeyword, (val) => {
  treeRef.value?.filter(val);
});

function filterTreeNode(value: string, data: any) {
  if (!value) return true;
  const label = String(data.label ?? "");
  const deptCode = String(data.deptCode ?? "");
  return (
    label.toLowerCase().includes(value.toLowerCase()) ||
    deptCode.toLowerCase().includes(value.toLowerCase())
  );
}

function handleNodeClick(data: DeptTreeNode) {
  selectedDeptId.value = data.id;
}

function getSubDeptIds(rootId: number): number[] {
  const result: number[] = [rootId];
  const queue: number[] = [rootId];
  while (queue.length > 0) {
    const currentId = queue.shift()!;
    props.departments
      .filter((d) => d.parentId === currentId)
      .forEach((child) => {
        result.push(child.id);
        queue.push(child.id);
      });
  }
  return result;
}

const displayEmployees = computed(() => {
  if (!selectedDept.value) return props.employees;
  if (includeSubDepartments.value) {
    const targetDeptIds = getSubDeptIds(selectedDept.value.id);
    return props.employees.filter((e) => targetDeptIds.includes(e.deptId));
  }
  return props.employees.filter((e) => e.deptId === selectedDept.value?.id);
});

const directChildDepts = computed(() => {
  if (!selectedDept.value) return [];
  return props.departments.filter((d) => d.parentId === selectedDept.value?.id);
});

function asDepartmentRows(rows: object[]) {
  return rows as IamDepartmentRow[];
}

function asPositionRows(rows: object[]) {
  return rows as IamPositionRow[];
}

function asEmployeeRows(rows: object[]) {
  return rows as IamEmployeeRow[];
}

// ================= Employee Workspace State =================
const empSearchKeyword = ref("");
const empStatusFilter = ref("ALL");
const empDeptFilter = ref<number | null>(null);
const empPositionFilter = ref<number | null>(null);
const empAccountFilter = ref("ALL");
const empSelectedDeptId = ref<number | null>(null);
const employeeDrawerVisible = ref(false);
const profileEmployee = ref<IamEmployeeRow | null>(null);
const profileActiveTab = ref("basic");

const empStats = computed(() => {
  const total = props.employees.length;
  const active = props.employees.filter((e) => e.employeeStatus === "ACTIVE").length;
  const probation = props.employees.filter((e) => e.employeeStatus === "PROBATION").length;
  const inactive = props.employees.filter(
    (e) => e.employeeStatus !== "ACTIVE" && e.employeeStatus !== "PROBATION"
  ).length;
  const boundCount = props.employees.filter((e) => (e.boundUserCount || 0) > 0).length;
  const accountLinkRate = total > 0 ? Math.round((boundCount / total) * 100) : 100;
  return { total, active, probation, inactive, boundCount, accountLinkRate };
});

const filteredEmployees = computed(() => {
  const kw = empSearchKeyword.value.trim().toLowerCase();
  const status = empStatusFilter.value;
  const targetDeptId = empSelectedDeptId.value ?? empDeptFilter.value;
  const posId = empPositionFilter.value;
  const accountFilter = empAccountFilter.value;
  const allowedDeptIds = targetDeptId !== null ? getSubDeptIds(targetDeptId) : null;

  return props.employees.filter((emp) => {
    if (kw) {
      const matchKw =
        (emp.employeeNo && emp.employeeNo.toLowerCase().includes(kw)) ||
        (emp.employeeName && emp.employeeName.toLowerCase().includes(kw)) ||
        (emp.mobile && emp.mobile.includes(kw)) ||
        (emp.email && emp.email.toLowerCase().includes(kw));
      if (!matchKw) return false;
    }

    if (status !== "ALL" && emp.employeeStatus !== status) {
      return false;
    }

    if (allowedDeptIds !== null && !allowedDeptIds.includes(emp.deptId)) {
      return false;
    }

    if (posId !== null && emp.positionId !== posId) {
      return false;
    }

    if (accountFilter === "BOUND" && (!emp.boundUserCount || emp.boundUserCount <= 0)) {
      return false;
    }

    if (accountFilter === "UNBOUND" && emp.boundUserCount && emp.boundUserCount > 0) {
      return false;
    }

    return true;
  });
});

function openEmployee360(emp: IamEmployeeRow) {
  profileEmployee.value = emp;
  profileActiveTab.value = "basic";
  employeeDrawerVisible.value = true;
}

function resetEmpFilters() {
  empSearchKeyword.value = "";
  empStatusFilter.value = "ALL";
  empDeptFilter.value = null;
  empPositionFilter.value = null;
  empAccountFilter.value = "ALL";
  empSelectedDeptId.value = null;
}

function getDeptName(deptId?: number) {
  if (!deptId) return "-";
  return props.departments.find((d) => d.id === deptId)?.deptName || `部门 #${deptId}`;
}

function getPositionName(posId?: number) {
  if (!posId) return "-";
  return props.positions.find((p) => p.id === posId)?.positionName || `岗位 #${posId}`;
}

function copyText(text?: string) {
  if (!text) return;
  navigator.clipboard.writeText(text);
  ElMessage.success(`已复制: ${text}`);
}

// ================= Position Workspace State =================
const posSearchKeyword = ref("");
const posStatusFilter = ref("ALL");

const posStats = computed(() => {
  const total = props.positions.length;
  const active = props.positions.filter((p) => p.status === "ENABLED").length;
  const assigned = props.employees.filter((e) => e.positionId).length;
  const coveredDepts = new Set(props.employees.filter((e) => e.positionId).map((e) => e.deptId)).size;
  return { total, active, assigned, coveredDepts };
});

const filteredPositions = computed(() => {
  const kw = posSearchKeyword.value.trim().toLowerCase();
  const status = posStatusFilter.value;

  return props.positions.filter((pos) => {
    if (kw) {
      const match =
        pos.positionCode.toLowerCase().includes(kw) ||
        pos.positionName.toLowerCase().includes(kw);
      if (!match) return false;
    }
    if (status !== "ALL" && pos.status !== status) {
      return false;
    }
    return true;
  });
});

function getPositionEmployeeList(posId: number) {
  return props.employees.filter((e) => e.positionId === posId);
}
</script>

<template>
  <div class="org-structure-container">
    <!-- 岗位管理：企业级岗位职能治理工作台 -->
    <template v-if="mode === 'position'">
      <div class="position-enterprise-workspace">
        <!-- 岗位量化看板 -->
        <div class="org-stats-grid">
          <div class="stat-card">
            <div class="stat-icon-wrap is-primary"><el-icon><Suitcase /></el-icon></div>
            <div class="stat-info">
              <span class="stat-label">基准岗位总数</span>
              <strong class="stat-val text-primary">{{ posStats.total }}</strong>
              <span class="stat-sub">职能标准架构</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon-wrap is-success"><el-icon><CircleCheck /></el-icon></div>
            <div class="stat-info">
              <span class="stat-label">正常启用岗位</span>
              <strong class="stat-val text-success">{{ posStats.active }}</strong>
              <span class="stat-sub">有效承接权限</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon-wrap is-warning"><el-icon><UserFilled /></el-icon></div>
            <div class="stat-info">
              <span class="stat-label">在岗员工覆盖</span>
              <strong class="stat-val text-warning">{{ posStats.assigned }}</strong>
              <span class="stat-sub">人岗匹配关联</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon-wrap is-info"><el-icon><OfficeBuilding /></el-icon></div>
            <div class="stat-info">
              <span class="stat-label">部门覆盖度</span>
              <strong class="stat-val text-info">{{ posStats.coveredDepts }}</strong>
              <span class="stat-sub">组织体系覆盖</span>
            </div>
          </div>
        </div>

        <BaseCard class="position-table-card">
          <template #header>
            <div class="table-toolbar">
              <div class="toolbar-left">
                <el-input
                  v-model="posSearchKeyword"
                  placeholder="搜索岗位名称 / 岗位编码..."
                  :prefix-icon="Search"
                  clearable
                  style="width: 260px"
                />
                <el-select v-model="posStatusFilter" placeholder="状态筛选" style="width: 120px">
                  <el-option label="全部状态" value="ALL" />
                  <el-option label="启用" value="ENABLED" />
                  <el-option label="停用" value="DISABLED" />
                </el-select>
              </div>
              <div class="toolbar-right">
                <el-button
                  v-button-permission="{ code: 'iam_position:create', fallback: 'iam:user:write' }"
                  type="primary"
                  :icon="Plus"
                  @click="openPositionCreate()"
                >+ 新增岗位</el-button>
              </div>
            </div>
          </template>

          <el-table
            :data="filteredPositions"
            row-key="id"
            v-loading="loading"
            class="enterprise-position-table"
            stripe
          >
            <!-- 岗位名称 -->
            <el-table-column prop="positionName" label="岗位名称" min-width="180">
              <template #default="{ row }">
                <div class="position-name-cell">
                  <el-icon class="pos-icon"><Suitcase /></el-icon>
                  <strong>{{ row.positionName }}</strong>
                </div>
              </template>
            </el-table-column>

            <!-- 岗位编码 -->
            <el-table-column prop="positionCode" label="岗位编码" min-width="140">
              <template #default="{ row }">
                <button
                  class="code-copy-pill"
                  title="点击复制编码"
                  @click="copyText(row.positionCode)"
                >
                  <span>{{ row.positionCode }}</span>
                  <el-icon><CopyDocument /></el-icon>
                </button>
              </template>
            </el-table-column>

            <!-- 职能分类 -->
            <el-table-column label="职能分类" min-width="130">
              <template #default="{ row }">
                <el-tag size="small" type="primary" effect="plain">基准业务岗</el-tag>
              </template>
            </el-table-column>

            <!-- 在岗人员总数 -->
            <el-table-column label="当前在岗人员" min-width="140" align="center">
              <template #default="{ row }">
                <el-popover placement="top" :width="240" trigger="hover">
                  <template #reference>
                    <el-tag
                      size="small"
                      :type="getPositionEmployeeList(row.id).length ? 'success' : 'info'"
                      class="headcount-tag"
                    >
                      {{ getPositionEmployeeList(row.id).length }} 人在岗
                    </el-tag>
                  </template>
                  <div class="popover-personnel-list">
                    <strong>在岗员工名单:</strong>
                    <ul v-if="getPositionEmployeeList(row.id).length">
                      <li v-for="emp in getPositionEmployeeList(row.id)" :key="emp.id">
                        {{ emp.employeeName }} ({{ emp.employeeNo }})
                      </li>
                    </ul>
                    <p v-else class="text-muted">暂无在岗员工</p>
                  </div>
                </el-popover>
              </template>
            </el-table-column>

            <!-- 排序号 -->
            <el-table-column prop="sortNo" label="排序" width="80" align="center" />

            <!-- 状态 -->
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
              </template>
            </el-table-column>

            <!-- 操作 -->
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button v-button-permission="{ code: 'iam_position:edit', fallback: 'iam:user:write' }" link type="primary" @click="openPositionEdit(row)">编辑</el-button>
                <el-button v-button-permission="{ code: 'iam_position:toggle', fallback: 'iam:user:write' }" link :type="row.status === 'ENABLED' ? 'danger' : 'primary'" @click="togglePosition(row)">
                  {{ row.status === "ENABLED" ? "停用" : "启用" }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </BaseCard>
      </div>
    </template>

    <!-- 员工档案：企业级左树右表全景人才工作台 -->
    <template v-else-if="mode === 'employee'">
      <div class="employee-enterprise-workspace">
        <!-- 顶部核心量化看板 -->
        <div class="org-stats-grid">
          <div class="stat-card">
            <div class="stat-icon-wrap is-primary"><el-icon><UserFilled /></el-icon></div>
            <div class="stat-info">
              <span class="stat-label">在职员工数</span>
              <strong class="stat-val text-primary">{{ empStats.active }}</strong>
              <span class="stat-sub">总在册: {{ empStats.total }} 人</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon-wrap is-warning"><el-icon><Timer /></el-icon></div>
            <div class="stat-info">
              <span class="stat-label">试用期员工</span>
              <strong class="stat-val text-warning">{{ empStats.probation }}</strong>
              <span class="stat-sub">待转正述职评估</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon-wrap is-danger"><el-icon><CircleClose /></el-icon></div>
            <div class="stat-info">
              <span class="stat-label">停用/离职</span>
              <strong class="stat-val text-danger">{{ empStats.inactive }}</strong>
              <span class="stat-sub">已冻结访问权限</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon-wrap is-success"><el-icon><Key /></el-icon></div>
            <div class="stat-info">
              <span class="stat-label">账号关联覆盖率</span>
              <strong class="stat-val text-success">{{ empStats.accountLinkRate }}%</strong>
              <span class="stat-sub">已绑定系统账号: {{ empStats.boundCount }} 人</span>
            </div>
          </div>
        </div>

        <!-- 旗舰级左树右表联动布局 -->
        <div class="emp-split-layout">
          <!-- 左侧：部门架构组织树 -->
          <aside class="emp-dept-sidebar">
            <div class="dept-sidebar-header">
              <strong class="sidebar-title">部门架构导航</strong>
              <el-button link size="small" type="primary" @click="empSelectedDeptId = null">全公司</el-button>
            </div>
            <div class="dept-tree-wrap">
              <div
                class="all-dept-item"
                :class="{ 'is-active': empSelectedDeptId === null }"
                @click="empSelectedDeptId = null"
              >
                <el-icon><OfficeBuilding /></el-icon>
                <span>全部部门人员</span>
                <span class="dept-count-badge">{{ employees.length }}</span>
              </div>
              <el-tree
                :data="deptTreeData"
                node-key="id"
                default-expand-all
                highlight-current
                :expand-on-click-node="false"
                @node-click="(node) => empSelectedDeptId = node.id"
              >
                <template #default="{ data }">
                  <div class="emp-tree-node-row" :class="{ 'is-selected': empSelectedDeptId === data.id }">
                    <span class="node-label">{{ data.label }}</span>
                    <span v-if="data.memberCount > 0" class="node-badge">{{ data.memberCount }}</span>
                  </div>
                </template>
              </el-tree>
            </div>
          </aside>

          <!-- 右侧：员工主数据中心 -->
          <div class="emp-table-main">
            <!-- 多维高级筛选检索栏 -->
            <div class="emp-filter-panel">
              <div class="filter-row">
                <el-input
                  v-model="empSearchKeyword"
                  placeholder="搜索工号 / 姓名 / 手机 / 邮箱..."
                  :prefix-icon="Search"
                  clearable
                  style="width: 240px"
                />
                <el-select v-model="empStatusFilter" placeholder="员工状态" style="width: 120px">
                  <el-option label="全部状态" value="ALL" />
                  <el-option label="在职 (ACTIVE)" value="ACTIVE" />
                  <el-option label="试用期" value="PROBATION" />
                  <el-option label="停用/离职" value="INACTIVE" />
                </el-select>
                <el-select v-model="empPositionFilter" placeholder="担任岗位" clearable style="width: 140px">
                  <el-option
                    v-for="pos in positions"
                    :key="pos.id"
                    :label="pos.positionName"
                    :value="pos.id"
                  />
                </el-select>
                <el-select v-model="empAccountFilter" placeholder="账号状态" style="width: 130px">
                  <el-option label="全部账号" value="ALL" />
                  <el-option label="已绑定系统账号" value="BOUND" />
                  <el-option label="未绑定账号" value="UNBOUND" />
                </el-select>
                <el-button link :icon="Refresh" @click="resetEmpFilters">重置</el-button>
              </div>
              <div class="actions-row">
                <el-button
                  v-button-permission="{ code: 'iam_employee:create', fallback: 'iam:user:write' }"
                  type="primary"
                  :icon="Plus"
                  @click="openEmployeeCreate(empSelectedDeptId || undefined)"
                >+ 新增员工</el-button>
              </div>
            </div>

            <!-- 员工数据表格 -->
            <el-table
              :data="filteredEmployees"
              row-key="id"
              v-loading="loading"
              class="enterprise-employee-table"
              stripe
            >
              <!-- 员工基本信息 (头像+姓名) -->
              <el-table-column label="员工姓名" min-width="160">
                <template #default="{ row }">
                  <div class="employee-cell">
                    <span class="emp-avatar">{{ (row.employeeName || "员").slice(0, 1) }}</span>
                    <div class="emp-name-box">
                      <strong class="emp-name">{{ row.employeeName }}</strong>
                      <small v-if="row.gender" class="emp-gender">{{ row.gender === 'MALE' ? '男' : (row.gender === 'FEMALE' ? '女' : '未知') }}</small>
                    </div>
                  </div>
                </template>
              </el-table-column>

              <!-- 工号 -->
              <el-table-column prop="employeeNo" label="员工工号" min-width="130">
                <template #default="{ row }">
                  <button
                    class="code-copy-pill"
                    title="点击复制工号"
                    @click="copyText(row.employeeNo)"
                  >
                    <span>{{ row.employeeNo }}</span>
                    <el-icon><CopyDocument /></el-icon>
                  </button>
                </template>
              </el-table-column>

              <!-- 所属部门 -->
              <el-table-column label="所属部门" min-width="160">
                <template #default="{ row }">
                  <el-tag size="small" type="primary" effect="light" class="dept-tag">
                    <el-icon><OfficeBuilding /></el-icon>
                    {{ row.deptFullName || row.deptName || getDeptName(row.deptId) }}
                  </el-tag>
                </template>
              </el-table-column>

              <!-- 岗位 -->
              <el-table-column label="担任岗位" min-width="140">
                <template #default="{ row }">
                  <el-tag size="small" type="info" effect="plain" class="pos-tag">
                    <el-icon><Suitcase /></el-icon>
                    {{ row.positionName || getPositionName(row.positionId) }}
                  </el-tag>
                </template>
              </el-table-column>

              <!-- 入职日期 -->
              <el-table-column prop="hireDate" label="入职日期" width="120" align="center">
                <template #default="{ row }">
                  <span class="date-text">{{ row.hireDate || '-' }}</span>
                </template>
              </el-table-column>

              <!-- 系统账号绑定 -->
              <el-table-column label="系统账号状态" width="140" align="center">
                <template #default="{ row }">
                  <el-tag v-if="(row.boundUserCount || 0) > 0" type="success" size="small" effect="light">
                    已绑定账号 ({{ row.boundUserCount }})
                  </el-tag>
                  <el-tag v-else type="warning" size="small" effect="plain">
                    未关联系统账号
                  </el-tag>
                </template>
              </el-table-column>

              <!-- 在职状态 -->
              <el-table-column label="在职状态" width="100" align="center">
                <template #default="{ row }">
                  <BaseStatusTag
                    :status="localizeEmployeeStatus(String(row.employeeStatus))"
                    :type="row.employeeStatus === 'ACTIVE' ? 'success' : 'warning'"
                  />
                </template>
              </el-table-column>

              <!-- 操作栏 -->
              <el-table-column label="操作" width="180" fixed="right">
                <template #default="{ row }">
                  <div class="table-action-cell">
                    <el-button link type="primary" :icon="View" @click="openEmployee360(row)">360画像</el-button>
                    <el-button v-button-permission="{ code: 'iam_employee:edit', fallback: 'iam:user:write' }" link type="primary" @click="openEmployeeEdit(row)">编辑</el-button>
                    <el-dropdown trigger="click">
                      <el-button link type="primary" class="action-more-btn">
                        更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                      </el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item v-button-permission="{ code: 'iam_employee:transfer', fallback: 'iam:user:write' }" @click="openEmployeeTransfer(row)">
                            <el-icon><Switch /></el-icon>
                            <span>组织异动</span>
                          </el-dropdown-item>
                          <el-dropdown-item
                            v-button-permission="{ code: 'iam_employee:toggle', fallback: 'iam:user:write' }"
                            :divided="true"
                            :class="row.employeeStatus === 'ACTIVE' ? 'action-danger-text' : 'action-success-text'"
                            @click="toggleEmployee(row)"
                          >
                            <el-icon v-if="row.employeeStatus === 'ACTIVE'"><CircleClose /></el-icon>
                            <el-icon v-else><CircleCheck /></el-icon>
                            <span>{{ row.employeeStatus === "ACTIVE" ? "停用员工" : "启用员工" }}</span>
                          </el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <!-- 员工 360° 全景画像全景抽屉 -->
        <el-drawer
          v-model="employeeDrawerVisible"
          :title="`员工360°档案全景画像 - ${profileEmployee?.employeeName ?? ''}`"
          size="580px"
          destroy-on-close
          class="employee-360-drawer"
        >
          <div v-if="profileEmployee" class="profile-drawer-stack">
            <!-- 员工 Hero 概要卡片 -->
            <div class="profile-hero-card">
              <span class="profile-hero-avatar">{{ profileEmployee.employeeName.slice(0, 1) }}</span>
              <div class="profile-hero-info">
                <div class="name-status-row">
                  <h3>{{ profileEmployee.employeeName }}</h3>
                  <BaseStatusTag
                    :status="localizeEmployeeStatus(String(profileEmployee.employeeStatus))"
                    :type="profileEmployee.employeeStatus === 'ACTIVE' ? 'success' : 'warning'"
                  />
                </div>
                <div class="hero-subline">
                  <span class="hero-empno">{{ profileEmployee.employeeNo }}</span>
                  <span class="hero-sep">·</span>
                  <span>{{ getDeptName(profileEmployee.deptId) }}</span>
                  <span class="hero-sep">·</span>
                  <span>{{ getPositionName(profileEmployee.positionId) }}</span>
                </div>
              </div>
            </div>

            <!-- Profile Tabs -->
            <el-tabs v-model="profileActiveTab" class="profile-tabs">
              <el-tab-pane label="基本档案" name="basic">
                <div class="profile-detail-grid">
                  <div class="detail-item">
                    <span class="detail-label">员工姓名</span>
                    <span class="detail-val">{{ profileEmployee.employeeName }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">员工编号</span>
                    <span class="detail-val font-mono">{{ profileEmployee.employeeNo }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">性别</span>
                    <span class="detail-val">{{ profileEmployee.gender === 'MALE' ? '男' : (profileEmployee.gender === 'FEMALE' ? '女' : '未提供') }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">联系电话</span>
                    <span class="detail-val">{{ profileEmployee.mobile || '未登记' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">电子邮箱</span>
                    <span class="detail-val font-mono">{{ profileEmployee.email || '未登记' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">备注说明</span>
                    <span class="detail-val">{{ profileEmployee.remark || '未填写' }}</span>
                  </div>
                  <div class="detail-item full-row">
                    <span class="detail-label">入职日期</span>
                    <span class="detail-val">{{ profileEmployee.hireDate || '-' }}</span>
                  </div>
                </div>
              </el-tab-pane>

              <el-tab-pane label="组织与岗位" name="org">
                <div class="profile-detail-grid">
                  <div class="detail-item">
                    <span class="detail-label">所属部门</span>
                    <span class="detail-val">{{ getDeptName(profileEmployee.deptId) }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">部门ID</span>
                    <span class="detail-val font-mono">#{{ profileEmployee.deptId }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">现任岗位</span>
                    <span class="detail-val">{{ getPositionName(profileEmployee.positionId) }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">账号绑定状态</span>
                    <span class="detail-val">
                      {{ (profileEmployee.boundUserCount || 0) > 0 ? `已绑定 (${profileEmployee.boundUserCount} 个账号)` : '未绑定' }}
                    </span>
                  </div>
                </div>
              </el-tab-pane>

              <el-tab-pane label="系统访问审计" name="audit">
                <div class="profile-audit-box">
                  <div class="audit-item">
                    <el-icon class="audit-icon is-success"><CircleCheck /></el-icon>
                    <div class="audit-content">
                      <strong>身份凭证状态正常</strong>
                      <p>当前档案在组织中处于有效治理状态，可由系统管理员在「用户管理」中绑定或生成账号。</p>
                    </div>
                  </div>
                </div>
              </el-tab-pane>
            </el-tabs>
          </div>
        </el-drawer>
      </div>
    </template>

    <!-- 默认模式：旗舰级可视化组织架构与在岗人员工作台 -->
    <template v-else>
      <div class="org-workspace-split">
        <!-- 左侧：可视化组织树面板 -->
        <aside class="org-tree-panel">
          <div class="tree-panel-header">
            <div class="panel-title-row">
              <span class="panel-title">组织架构树</span>
              <el-button
                v-button-permission="{ code: 'iam_department:create', fallback: 'iam:user:write' }"
                type="primary"
                link
                :icon="Plus"
                @click="openDepartmentCreate(0)"
              >
                新增部门
              </el-button>
            </div>
            <el-input
              v-model="searchKeyword"
              :prefix-icon="Search"
              clearable
              placeholder="搜索部门名称或编码..."
              class="tree-search-input"
            />
          </div>

          <div class="tree-scroll-container">
            <el-tree
              ref="treeRef"
              :data="deptTreeData"
              node-key="id"
              default-expand-all
              highlight-current
              :expand-on-click-node="false"
              :filter-node-method="filterTreeNode"
              @node-click="handleNodeClick"
            >
              <template #default="{ data }">
                <div class="custom-tree-node">
                  <div class="node-main">
                    <el-icon class="node-icon">
                      <OfficeBuilding v-if="data.raw.parentId === 0" />
                      <FolderOpened v-else-if="data.children && data.children.length > 0" />
                      <Folder v-else />
                    </el-icon>
                    <span class="node-label">{{ data.label }}</span>
                  </div>
                  <div class="node-extra">
                    <span v-if="data.memberCount > 0" class="member-badge">{{ data.memberCount }}人</span>
                    <div class="node-hover-actions">
                      <el-tooltip content="添加下级子部门" placement="top" :show-after="300">
                        <el-button
                          link
                          type="primary"
                          :icon="Plus"
                          class="action-mini-btn"
                          @click.stop="openDepartmentCreate(data.id)"
                        />
                      </el-tooltip>
                    </div>
                  </div>
                </div>
              </template>
            </el-tree>
          </div>
        </aside>

        <!-- 右侧：当前部门工作台 -->
        <main class="org-content-panel">
          <!-- 部门基本信息 Hero 卡片 -->
          <div v-if="selectedDept" class="dept-hero-card">
            <div class="hero-header-row">
              <div class="hero-title-group">
                <div class="hero-breadcrumb">
                  <span>{{ selectedDept.deptFullName || selectedDept.deptName }}</span>
                </div>
                <div class="hero-title-line">
                  <h2 class="hero-title">{{ selectedDept.deptName }}</h2>
                  <el-tag size="small" effect="plain" type="info" class="dept-code-tag">
                    {{ selectedDept.deptCode }}
                  </el-tag>
                  <BaseStatusTag
                    :status="selectedDept.status"
                    :type="selectedDept.status === 'ENABLED' ? 'success' : 'danger'"
                  />
                </div>
              </div>

              <!-- 右侧快捷操作按钮 -->
              <div class="hero-action-buttons">
                <el-button
                  v-button-permission="{ code: 'iam_employee:create', fallback: 'iam:user:write' }"
                  type="primary"
                  :icon="User"
                  @click="openEmployeeCreate(selectedDept.id)"
                >
                  + 新增在岗人员
                </el-button>
                <el-button
                  v-button-permission="{ code: 'iam_department:create', fallback: 'iam:user:write' }"
                  plain
                  :icon="Plus"
                  @click="openDepartmentCreate(selectedDept.id)"
                >
                  新增子部门
                </el-button>
                <el-button
                  v-button-permission="{ code: 'iam_department:edit', fallback: 'iam:user:write' }"
                  plain
                  @click="openDepartmentEdit(selectedDept)"
                >
                  编辑部门
                </el-button>
                <el-button
                  v-button-permission="{ code: 'iam_department:toggle', fallback: 'iam:user:write' }"
                  plain
                  @click="toggleDepartment(selectedDept)"
                >
                  {{ selectedDept.status === 'ENABLED' ? '停用' : '启用' }}
                </el-button>
              </div>
            </div>

            <!-- 统计摘要指标 -->
            <div class="hero-meta-grid">
              <div class="meta-item">
                <span class="meta-label">部门负责人</span>
                <span class="meta-value">{{ selectedDept.leaderName || '暂未指定' }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">直属在编人员</span>
                <span class="meta-value text-tabular">{{ memberCountMap.get(selectedDept.id) || 0 }} 人</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">下级分支部门</span>
                <span class="meta-value text-tabular">{{ directChildDepts.length }} 个</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">部门全层级路径</span>
                <span class="meta-value text-path">{{ selectedDept.deptFullName || selectedDept.deptName }}</span>
              </div>
            </div>
          </div>

          <!-- 下方 Tab 视图切换 -->
          <div class="dept-detail-tabs">
            <el-radio-group v-model="activeDetailTab" size="default" class="segmented-control">
              <el-radio-button value="members">
                在岗成员 ({{ displayEmployees.length }})
              </el-radio-button>
              <el-radio-button value="positions">
                岗位体系 ({{ positions.length }})
              </el-radio-button>
              <el-radio-button value="subDepts">
                下级部门 ({{ directChildDepts.length }})
              </el-radio-button>
            </el-radio-group>

            <div v-if="activeDetailTab === 'members'" class="member-filter-row">
              <el-checkbox v-model="includeSubDepartments">
                包含所有下级子部门成员
              </el-checkbox>
            </div>
          </div>

          <!-- Tab 内容 1：在岗成员 -->
          <div v-show="activeDetailTab === 'members'" class="tab-table-wrapper">
            <ProTable
              :columns="employeeColumns"
              :data="displayEmployees"
              :loading="loading"
              selectable
              compact
              title="部门成员名录"
              subtitle="查看与维护归属于当前部门的所有正式成员、任职岗位与关联账号。"
            >
              <template #toolbar>
                <el-button
                  v-button-permission="{ code: 'iam_employee:create', fallback: 'iam:user:write' }"
                  type="primary"
                  @click="openEmployeeCreate(selectedDept?.id)"
                >
                  + 新增成员
                </el-button>
              </template>
              <template #bulkActions="{ rows }">
                <el-button type="primary" plain @click="batchEnableEmployees(asEmployeeRows(rows))">批量启用</el-button>
                <el-button plain @click="batchDisableEmployees(asEmployeeRows(rows))">批量停用</el-button>
              </template>
              <template #employeeStatus="{ row }">
                <BaseStatusTag
                  :status="localizeEmployeeStatus(String(row.employeeStatus))"
                  :type="row.employeeStatus === 'ACTIVE' ? 'success' : 'warning'"
                />
              </template>
              <template #actions="{ row }">
                <el-button v-button-permission="{ code: 'iam_employee:edit', fallback: 'iam:user:write' }" link type="primary" @click="openEmployeeEdit(row)">编辑</el-button>
                <el-button v-button-permission="{ code: 'iam_employee:transfer', fallback: 'iam:user:write' }" link type="primary" @click="openEmployeeTransfer(row)">调岗异动</el-button>
                <el-button v-button-permission="{ code: 'iam_employee:toggle', fallback: 'iam:user:write' }" link @click="toggleEmployee(row)">{{ row.employeeStatus === "ACTIVE" ? "停用" : "启用" }}</el-button>
              </template>
            </ProTable>
          </div>

          <!-- Tab 内容 2：岗位配置 -->
          <div v-show="activeDetailTab === 'positions'" class="tab-table-wrapper">
            <ProTable
              :columns="positionColumns"
              :data="positions"
              :loading="loading"
              selectable
              compact
              title="企业岗位体系"
              subtitle="规范企业内的岗位等级与责任分工，供员工入职或调岗时进行标准选定。"
            >
              <template #toolbar>
                <el-button
                  v-button-permission="{ code: 'iam_position:create', fallback: 'iam:user:write' }"
                  type="primary"
                  :icon="Suitcase"
                  @click="openPositionCreate()"
                >
                  + 新建岗位
                </el-button>
              </template>
              <template #bulkActions="{ rows }">
                <el-button type="primary" plain @click="batchEnablePositions(asPositionRows(rows))">批量启用</el-button>
                <el-button plain @click="batchDisablePositions(asPositionRows(rows))">批量停用</el-button>
              </template>
              <template #status="{ row }">
                <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
              </template>
              <template #actions="{ row }">
                <el-button v-button-permission="{ code: 'iam_position:edit', fallback: 'iam:user:write' }" link type="primary" @click="openPositionEdit(row)">编辑</el-button>
                <el-button v-button-permission="{ code: 'iam_position:toggle', fallback: 'iam:user:write' }" link @click="togglePosition(row)">{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
              </template>
            </ProTable>
          </div>

          <!-- Tab 内容 3：下级部门分支 -->
          <div v-show="activeDetailTab === 'subDepts'" class="tab-table-wrapper">
            <ProTable
              :columns="departmentColumns"
              :data="directChildDepts"
              :loading="loading"
              selectable
              compact
              title="直接下级部门分支"
              subtitle="当前部门直接管辖的子部门组织列表。"
            >
              <template #toolbar>
                <el-button
                  v-button-permission="{ code: 'iam_department:create', fallback: 'iam:user:write' }"
                  type="primary"
                  @click="openDepartmentCreate(selectedDept?.id)"
                >
                  + 新增直接子部门
                </el-button>
              </template>
              <template #bulkActions="{ rows }">
                <el-button type="primary" plain @click="batchEnableDepartments(asDepartmentRows(rows))">批量启用</el-button>
                <el-button plain @click="batchDisableDepartments(asDepartmentRows(rows))">批量停用</el-button>
              </template>
              <template #status="{ row }">
                <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
              </template>
              <template #actions="{ row }">
                <el-button v-button-permission="{ code: 'iam_department:edit', fallback: 'iam:user:write' }" link type="primary" @click="openDepartmentEdit(row)">编辑</el-button>
                <el-button v-button-permission="{ code: 'iam_department:toggle', fallback: 'iam:user:write' }" link @click="toggleDepartment(row)">{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
              </template>
            </ProTable>
          </div>
        </main>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.org-structure-container {
  width: 100%;
}

.org-workspace-split {
  display: grid;
  grid-template-columns: 290px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.org-tree-panel {
  background-color: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  min-height: 600px;
  max-height: calc(100vh - 160px);

  .tree-panel-header {
    padding: 14px 16px;
    border-bottom: 1px solid #f1f5f9;

    .panel-title-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 10px;

      .panel-title {
        font-size: 14px;
        font-weight: 600;
        color: #0f172a;
      }
    }

    .tree-search-input {
      :deep(.el-input__wrapper) {
        border-radius: 4px;
        box-shadow: 0 0 0 1px #cbd5e1 inset;
      }
    }
  }

  .tree-scroll-container {
    flex: 1;
    overflow-y: auto;
    padding: 8px 6px;

    :deep(.el-tree-node__content) {
      height: 36px;
      border-radius: 4px;
      padding-right: 8px;

      &:hover {
        background-color: #f1f5f9;

        .node-hover-actions {
          opacity: 1;
        }
      }
    }

    :deep(.el-tree-node.is-current > .el-tree-node__content) {
      background-color: #eff6ff;
      color: #2563eb;
      font-weight: 500;

      .node-icon {
        color: #2563eb;
      }

      .member-badge {
        background-color: #dbeafe;
        color: #1d4ed8;
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
    gap: 6px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;

    .node-icon {
      font-size: 15px;
      color: #64748b;
      flex-shrink: 0;
    }

    .node-label {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .node-extra {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-shrink: 0;

    .member-badge {
      font-size: 11px;
      padding: 1px 6px;
      border-radius: 10px;
      background-color: #f1f5f9;
      color: #64748b;
      font-variant-numeric: tabular-nums;
    }

    .node-hover-actions {
      opacity: 0;
      transition: opacity 0.15s ease;

      .action-mini-btn {
        padding: 2px 4px;
        height: auto;
        font-size: 13px;
      }
    }
  }
}

.org-content-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.dept-hero-card {
  background-color: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 18px 20px;

  .hero-header-row {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 16px;

    .hero-title-group {
      .hero-breadcrumb {
        font-size: 12px;
        color: #64748b;
        margin-bottom: 4px;
      }

      .hero-title-line {
        display: flex;
        align-items: center;
        gap: 10px;

        .hero-title {
          margin: 0;
          font-size: 20px;
          font-weight: 700;
          color: #0f172a;
          letter-spacing: -0.01em;
        }

        .dept-code-tag {
          font-family: var(--font-mono, monospace);
          font-size: 11px;
        }
      }
    }

    .hero-action-buttons {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }
  }

  .hero-meta-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
    padding-top: 14px;
    border-top: 1px solid #f1f5f9;

    .meta-item {
      display: flex;
      flex-direction: column;
      gap: 2px;

      .meta-label {
        font-size: 12px;
        color: #64748b;
      }

      .meta-value {
        font-size: 14px;
        font-weight: 600;
        color: #0f172a;

        &.text-tabular {
          font-variant-numeric: tabular-nums;
        }

        &.text-path {
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          font-size: 13px;
          color: #334155;
        }
      }
    }
  }
}

.dept-detail-tabs {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: -4px;

  .member-filter-row {
    font-size: 13px;
    color: #475569;
  }
}

.tab-table-wrapper {
  animation: fade-in 0.15s ease-in-out;
}

@keyframes fade-in {
  from {
    opacity: 0;
    transform: translateY(2px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1200px) {
  .org-workspace-split {
    grid-template-columns: 1fr;
  }

  .org-tree-panel {
    min-height: auto;
    max-height: 350px;
  }

  .dept-hero-card .hero-meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* 核心量化看板 (统计卡片网格) */
.org-stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 16px;

  .stat-card {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 16px 18px;
    border-radius: 8px;
    background: #fff;
    border: 1px solid #e2e8f0;
    box-shadow: 0 1px 3px rgb(0 0 0 / 0.02);

    .stat-icon-wrap {
      width: 44px;
      height: 44px;
      display: grid;
      place-items: center;
      border-radius: 10px;
      font-size: 20px;
      flex-shrink: 0;

      &.is-primary { background: #eff6ff; color: #2563eb; }
      &.is-success { background: #ecfdf5; color: #10b981; }
      &.is-warning { background: #fffbeb; color: #f59e0b; }
      &.is-danger { background: #fef2f2; color: #ef4444; }
      &.is-info { background: #f5f3ff; color: #8b5cf6; }
    }

    .stat-info {
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 2px;

      .stat-label {
        font-size: 12px;
        color: #64748b;
        font-weight: 500;
      }

      .stat-val {
        font-size: 22px;
        font-weight: 700;
        line-height: 1.2;
        font-family: var(--sb-font-mono, monospace);

        &.text-primary { color: #2563eb; }
        &.text-success { color: #10b981; }
        &.text-warning { color: #f59e0b; }
        &.text-danger { color: #ef4444; }
        &.text-info { color: #8b5cf6; }
      }

      .stat-sub {
        font-size: 11px;
        color: #94a3b8;
      }
    }
  }
}

/* 员工左树右表联动布局 */
.emp-split-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.emp-dept-sidebar {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgb(0 0 0 / 0.02);

  .dept-sidebar-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 14px;
    border-bottom: 1px solid #f1f5f9;
    background: #f8fafc;

    .sidebar-title {
      font-size: 13.5px;
      color: #0f172a;
      font-weight: 600;
    }
  }

  .dept-tree-wrap {
    padding: 8px;
    max-height: calc(100vh - 300px);
    overflow-y: auto;

    .all-dept-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 10px;
      border-radius: 6px;
      color: #475569;
      font-size: 13px;
      cursor: pointer;
      margin-bottom: 6px;
      transition: all 0.15s;

      &:hover {
        background: #f1f5f9;
        color: #0f172a;
      }

      &.is-active {
        background: #eff6ff;
        color: #2563eb;
        font-weight: 600;

        .dept-count-badge {
          background: #dbeafe;
          color: #1d4ed8;
        }
      }

      .dept-count-badge {
        margin-left: auto;
        padding: 1px 6px;
        border-radius: 9999px;
        background: #f1f5f9;
        color: #64748b;
        font-size: 11px;
        font-weight: 600;
      }
    }

    .emp-tree-node-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      width: 100%;
      font-size: 13px;
      padding-right: 6px;

      &.is-selected {
        color: #2563eb;
        font-weight: 600;
      }

      .node-badge {
        padding: 0 5px;
        border-radius: 9999px;
        background: #f1f5f9;
        color: #64748b;
        font-size: 10px;
      }
    }
  }
}

.emp-table-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.emp-filter-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;

  .filter-row {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }
}

.enterprise-employee-table,
.enterprise-position-table {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;

  .employee-cell {
    display: flex;
    align-items: center;
    gap: 10px;

    .emp-avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
      color: #fff;
      display: grid;
      place-items: center;
      font-weight: 600;
      font-size: 13px;
      flex-shrink: 0;
    }

    .emp-name-box {
      display: flex;
      align-items: center;
      gap: 6px;

      .emp-name {
        font-size: 13.5px;
        color: #0f172a;
      }

      .emp-gender {
        font-size: 11px;
        color: #94a3b8;
      }
    }
  }

  .code-copy-pill {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 2px 7px;
    border-radius: 4px;
    border: 1px dashed #cbd5e1;
    background: #f8fafc;
    color: #2563eb;
    font-family: monospace;
    font-size: 11.5px;
    cursor: pointer;
    transition: all 0.15s;

    &:hover {
      border-color: #2563eb;
      background: #eff6ff;
    }
  }

  .dept-tag,
  .pos-tag {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 11.5px;
  }

  .date-text {
    font-family: monospace;
    color: #64748b;
    font-size: 12px;
  }
}

/* 岗位专属样式 */
.position-enterprise-workspace {
  display: flex;
  flex-direction: column;
  gap: 14px;

  .position-table-card {
    .table-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;

      .toolbar-left {
        display: flex;
        align-items: center;
        gap: 10px;
        flex-wrap: wrap;
      }
    }
  }

  .position-name-cell {
    display: flex;
    align-items: center;
    gap: 8px;

    .pos-icon {
      font-size: 15px;
      color: #2563eb;
    }

    strong {
      color: #0f172a;
      font-size: 13.5px;
    }
  }

  .headcount-tag {
    cursor: pointer;
    transition: transform 0.15s;

    &:hover {
      transform: scale(1.05);
    }
  }
}

.popover-personnel-list {
  font-size: 12px;

  strong {
    display: block;
    margin-bottom: 6px;
    color: #0f172a;
  }

  ul {
    margin: 0;
    padding-left: 16px;
    color: #475569;
    max-height: 160px;
    overflow-y: auto;

    li {
      margin-bottom: 4px;
    }
  }

  .text-muted {
    color: #94a3b8;
    margin: 0;
  }
}

/* 员工 360° 画像抽屉 */
.employee-360-drawer {
  .profile-drawer-stack {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .profile-hero-card {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 16px 20px;
    border-radius: 8px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;

    .profile-hero-avatar {
      width: 52px;
      height: 52px;
      border-radius: 50%;
      background: linear-gradient(135deg, #2563eb 0%, #4f46e5 100%);
      color: #fff;
      display: grid;
      place-items: center;
      font-size: 20px;
      font-weight: 700;
      flex-shrink: 0;
      box-shadow: 0 4px 10px rgb(37 99 235 / 0.25);
    }

    .profile-hero-info {
      min-width: 0;
      flex: 1;

      .name-status-row {
        display: flex;
        align-items: center;
        gap: 10px;

        h3 {
          margin: 0;
          font-size: 17px;
          color: #0f172a;
        }
      }

      .hero-subline {
        display: flex;
        align-items: center;
        gap: 6px;
        margin-top: 4px;
        font-size: 12.5px;
        color: #64748b;

        .hero-empno {
          font-family: monospace;
          color: #2563eb;
          font-weight: 600;
        }

        .hero-sep {
          color: #cbd5e1;
        }
      }
    }
  }

  .profile-tabs {
    :deep(.el-tabs__header) {
      margin-bottom: 12px;
    }
  }

  .profile-detail-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;

    .detail-item {
      display: flex;
      flex-direction: column;
      gap: 3px;
      padding: 10px 14px;
      border-radius: 6px;
      background: #f8fafc;
      border: 1px solid #f1f5f9;

      &.full-row {
        grid-column: span 2;
      }

      .detail-label {
        font-size: 11.5px;
        color: #94a3b8;
      }

      .detail-val {
        font-size: 13px;
        color: #1e293b;
        font-weight: 500;

        &.font-mono {
          font-family: monospace;
        }
      }
    }
  }

  .profile-audit-box {
    .audit-item {
      display: flex;
      gap: 10px;
      padding: 14px;
      border-radius: 6px;
      background: #f0fdf4;
      border: 1px solid #bbf7d0;

      .audit-icon {
        font-size: 18px;
        color: #16a34a;
        margin-top: 2px;
      }

      .audit-content {
        strong {
          display: block;
          font-size: 13.5px;
          color: #166534;
          margin-bottom: 4px;
        }

        p {
          margin: 0;
          font-size: 12px;
          color: #15803d;
          line-height: 1.5;
        }
      }
    }
  }
}

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
    font-size: 13px;
    padding: 0 4px;

    .el-icon--right {
      margin-left: 2px;
      font-size: 11px;
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
