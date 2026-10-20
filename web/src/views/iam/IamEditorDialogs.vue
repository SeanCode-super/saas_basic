<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { InfoFilled } from "@element-plus/icons-vue";
import IconPicker from "./components/IconPicker.vue";
import type { IamDepartmentRow, IamMenuRow, IamPositionRow, IamRoleRow } from "@/api/modules/iam";
import type {
  ApiFormState,
  DataScopeFormState,
  DepartmentFormState,
  EmployeeFormState,
  IamDialogState,
  LoginPolicyFormState,
  MenuFormState,
  PasswordPolicyFormState,
  PositionFormState,
  RoleFormState,
  SelectOption,
  UserFormState
} from "./iam-dialog-types";

const props = defineProps<{
  submitting: boolean;
  departmentDialog: IamDialogState;
  positionDialog: IamDialogState;
  employeeDialog: IamDialogState;
  userDialog: IamDialogState;
  roleDialog: IamDialogState;
  apiDialog: IamDialogState;
  menuDialog: IamDialogState;
  dataScopeDialog: IamDialogState;
  loginPolicyDialog: IamDialogState;
  passwordPolicyDialog: IamDialogState;
  departmentForm: DepartmentFormState;
  positionForm: PositionFormState;
  employeeForm: EmployeeFormState;
  userForm: UserFormState;
  roleForm: RoleFormState;
  apiForm: ApiFormState;
  menuForm: MenuFormState;
  dataScopeForm: DataScopeFormState;
  loginPolicyForm: LoginPolicyFormState;
  passwordPolicyForm: PasswordPolicyFormState;
  departmentOptions: SelectOption[];
  userOptions: SelectOption[];
  positionOptions: SelectOption[];
  employeeOptions: SelectOption[];
  menuParentOptions: SelectOption[];
  departments: IamDepartmentRow[];
  menus: IamMenuRow[];
  positions: IamPositionRow[];
  roles?: IamRoleRow[];
  submitDepartment: () => Promise<void> | void;
  submitPosition: () => Promise<void> | void;
  submitEmployee: () => Promise<void> | void;
  submitUser: () => Promise<void> | void;
  submitRole: () => Promise<void> | void;
  submitApiResource: () => Promise<void> | void;
  submitMenu: () => Promise<void> | void;
  submitDataScope: () => Promise<void> | void;
  submitLoginPolicy: () => Promise<void> | void;
  submitPasswordPolicy: () => Promise<void> | void;
}>();

const validMenuParentOptions = computed(() => {
  const allowedParentTypes = props.menuForm.menuType === "BUTTON" ? ["MENU"] : ["DIRECTORY"];
  const sourceMenus = props.menuParentOptions.filter((item) => {
    if (item.value === 0) {
      return props.menuForm.menuType !== "BUTTON";
    }
    const menu = props.menus.find((candidate) => candidate.id === item.value);
    return Boolean(menu && allowedParentTypes.includes(menu.menuType) && item.value !== props.menuDialog.id);
  });
  return sourceMenus;
});

watch(
  () => props.menuForm.menuType,
  () => {
    if (!validMenuParentOptions.value.some((item) => item.value === props.menuForm.parentId)) {
      props.menuForm.parentId = validMenuParentOptions.value[0]?.value ?? 0;
    }
  }
);

const menuCluster = ref<string>("foundation");
const menuNavPosition = ref<string>("AUTO");

watch(
  () => [props.menuDialog.visible, props.menuForm.metaJson],
  () => {
    if (!props.menuDialog.visible) return;
    try {
      const parsed = props.menuForm.metaJson ? JSON.parse(props.menuForm.metaJson) : {};
      menuCluster.value = parsed.cluster || "foundation";
      menuNavPosition.value = parsed.navPosition || "AUTO";
    } catch {
      menuCluster.value = "foundation";
      menuNavPosition.value = "AUTO";
    }
  },
  { immediate: true }
);

function handleClusterChange(newCluster: string) {
  try {
    const parsed = props.menuForm.metaJson ? JSON.parse(props.menuForm.metaJson) : {};
    parsed.cluster = newCluster;
    props.menuForm.metaJson = JSON.stringify(parsed);
  } catch {
    props.menuForm.metaJson = JSON.stringify({ cluster: newCluster });
  }
}

function handleNavPositionChange(newPos: string) {
  try {
    const parsed = props.menuForm.metaJson ? JSON.parse(props.menuForm.metaJson) : {};
    parsed.navPosition = newPos;
    props.menuForm.metaJson = JSON.stringify(parsed);
  } catch {
    props.menuForm.metaJson = JSON.stringify({ navPosition: newPos });
  }
}

interface DeptTreeNode {
  id: number;
  deptName: string;
  deptFullName: string;
  children?: DeptTreeNode[];
}

const templateDeptTree = computed(() => {
  const map = new Map<number, DeptTreeNode>();
  (props.departments || []).forEach((dept) => {
    map.set(dept.id, {
      id: dept.id,
      deptName: dept.deptName,
      deptFullName: dept.deptFullName || dept.deptName,
      children: []
    });
  });

  const roots: DeptTreeNode[] = [];
  (props.departments || []).forEach((dept) => {
    const node = map.get(dept.id);
    if (!node) return;
    const parentId = Number(dept.parentId ?? 0);
    if (parentId > 0 && map.has(parentId)) {
      map.get(parentId)!.children!.push(node);
    } else {
      roots.push(node);
    }
  });

  return roots;
});

const customTemplateDeptIds = ref<number[]>([]);

watch(
  () => props.dataScopeDialog.visible,
  (visible) => {
    if (visible) {
      try {
        if (props.dataScopeForm.scopeRuleJson) {
          const parsed = JSON.parse(props.dataScopeForm.scopeRuleJson);
          customTemplateDeptIds.value = parsed.orgIds || parsed.departmentIds || [];
        } else {
          customTemplateDeptIds.value = [];
        }
      } catch {
        customTemplateDeptIds.value = [];
      }
    }
  }
);

watch(customTemplateDeptIds, (val) => {
  if (props.dataScopeForm.scopeType === "CUSTOM") {
    props.dataScopeForm.scopeRuleJson = val.length
      ? JSON.stringify({ orgIds: val, includeChildren: true })
      : "";
  }
});

function getDataScopeTemplateDesc(type: string) {
  switch (type) {
    case "ALL_ACCESS":
      return "全量访问：企业内所有部门与成员的数据均可查阅，适用于最高决策层与平台审计。";
    case "ORG_TREE":
      return "组织树穿透：登录用户自动查阅本部门及所有下级子部门的数据。";
    case "DEPARTMENT":
      return "单部门限制：仅能查阅本人当前直接所在部门的数据，不穿透下级。";
    case "SELF_ONLY":
      return "仅本人数据：仅能查阅由本人创建或作为经办人的业务数据。";
    case "CUSTOM":
      return "自定义勾选：可视化指定具体开放的部门组织。";
    default:
      return "标准数据访问策略模板。";
  }
}
</script>

<template>
  <el-dialog v-model="departmentDialog.visible" :title="departmentDialog.mode === 'create' ? '新增部门' : '编辑部门'" width="620px">
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="上级部门">
          <el-select v-model="departmentForm.parentId" style="width: 100%">
            <el-option v-for="item in departmentOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="departmentForm.leaderUserId" style="width: 100%">
            <el-option v-for="item in userOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门编码" required>
          <el-input v-model="departmentForm.deptCode" placeholder="如 dept_tech" />
        </el-form-item>
        <el-form-item label="部门名称" required>
          <el-input v-model="departmentForm.deptName" placeholder="如 技术研发中心" />
        </el-form-item>
        <el-form-item label="部门全称">
          <el-input v-model="departmentForm.deptFullName" disabled placeholder="由系统按组织树自动生成" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="departmentForm.sortNo" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="departmentForm.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item label="备注">
        <el-input v-model="departmentForm.remark" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="departmentDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitDepartment">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="positionDialog.visible" :title="positionDialog.mode === 'create' ? '新增岗位' : '编辑岗位'" width="560px">
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="岗位编码" required>
          <el-input v-model="positionForm.positionCode" placeholder="如 pos_arch" />
        </el-form-item>
        <el-form-item label="岗位名称" required>
          <el-input v-model="positionForm.positionName" placeholder="如 架构师" />
        </el-form-item>
        <el-form-item label="岗位等级">
          <el-input v-model="positionForm.positionLevel" placeholder="例如 P6" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="positionForm.sortNo" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="positionForm.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item label="备注">
        <el-input v-model="positionForm.remark" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="positionDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitPosition">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="employeeDialog.visible"
    :title="employeeDialog.mode === 'create' ? '新增员工' : employeeDialog.mode === 'transfer' ? '组织异动' : '编辑员工'"
    width="680px"
  >
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="员工编号" :required="employeeDialog.mode !== 'transfer'">
          <el-input v-model="employeeForm.employeeNo" placeholder="如 EMP1001" :disabled="employeeDialog.mode === 'transfer'" />
        </el-form-item>
        <el-form-item label="员工姓名" :required="employeeDialog.mode !== 'transfer'">
          <el-input v-model="employeeForm.employeeName" placeholder="如 张三" :disabled="employeeDialog.mode === 'transfer'" />
        </el-form-item>
        <el-form-item label="所属部门" :required="employeeDialog.mode !== 'transfer'">
          <el-tree-select
            v-model="employeeForm.deptId"
            :data="templateDeptTree"
            node-key="id"
            :props="{ label: 'deptFullName', children: 'children' }"
            filterable
            check-strictly
            placeholder="展开组织架构树选择归属部门"
            style="width: 100%"
            :disabled="employeeDialog.mode === 'transfer'"
          />
        </el-form-item>
        <el-form-item label="岗位" :required="employeeDialog.mode !== 'transfer'">
          <el-select v-model="employeeForm.positionId" placeholder="选择岗位" style="width: 100%">
            <el-option v-for="item in positionOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="employeeForm.mobile" :disabled="employeeDialog.mode === 'transfer'" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="employeeForm.email" :disabled="employeeDialog.mode === 'transfer'" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="employeeForm.gender" style="width: 100%" :disabled="employeeDialog.mode === 'transfer'">
            <el-option label="男" value="MALE" />
            <el-option label="女" value="FEMALE" />
          </el-select>
        </el-form-item>
        <el-form-item label="入职日期">
          <el-date-picker
            v-model="employeeForm.hireDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            :disabled="employeeDialog.mode === 'transfer'"
          />
        </el-form-item>
        <el-form-item label="员工状态">
          <el-select v-model="employeeForm.employeeStatus" style="width: 100%" :disabled="employeeDialog.mode === 'transfer'">
            <el-option label="在职" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
            <el-option label="待入职" value="ONBOARDING" />
            <el-option label="离职流程中" value="OFFBOARDING" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item label="备注">
        <el-input v-model="employeeForm.remark" type="textarea" :rows="2" />
      </el-form-item>

      <div v-if="employeeDialog.mode === 'create'" class="account-onboard-card">
        <div class="account-onboard-switch-row">
          <el-switch v-model="employeeForm.createAccount" active-text="同时为该员工开通系统登录账号" />
          <span class="account-onboard-hint">开启后无需单独去用户中心创建账号，一步完成入职授权</span>
        </div>

        <div v-if="employeeForm.createAccount" class="account-onboard-fields">
          <div class="form-grid">
            <el-form-item label="登录账号 (用户名)" required>
              <el-input
                v-model="employeeForm.accountUsername"
                placeholder="建议输入工号或姓名拼音，例如 zhangsan"
              />
            </el-form-item>
            <el-form-item label="初始登录密码" required>
              <el-input
                v-model="employeeForm.accountPassword"
                placeholder="默认 User@123456（首次登录强制重置）"
              />
            </el-form-item>
          </div>
          <el-form-item label="分配初始业务角色">
            <el-select
              v-model="employeeForm.accountRoleIds"
              multiple
              collapse-tags
              collapse-tags-tooltip
              clearable
              placeholder="选择该员工被授予的角色权限（可多选）"
              style="width: 100%"
            >
              <el-option
                v-for="role in (roles || [])"
                :key="role.id"
                :label="role.roleName"
                :value="role.id"
              />
            </el-select>
          </el-form-item>
        </div>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="employeeDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitEmployee">{{ employeeDialog.mode === "transfer" ? "保存异动" : "保存" }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="userDialog.visible" :title="userDialog.mode === 'create' ? '新增用户' : '编辑用户'" width="680px">
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="用户编码">
          <el-input v-model="userForm.userCode" placeholder="留空则与用户名保持一致" />
        </el-form-item>
        <el-form-item label="用户名 (登录账号)" required>
          <el-input v-model="userForm.username" placeholder="如 admin 或 zhangsan" />
        </el-form-item>
        <el-form-item label="显示名 (真实姓名/昵称)" required>
          <el-input v-model="userForm.nickname" placeholder="如 张三" />
        </el-form-item>
        <el-form-item label="绑定员工">
          <el-select v-model="userForm.employeeId" style="width: 100%">
            <el-option v-for="item in employeeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="账号类型">
          <el-select v-model="userForm.userType" style="width: 100%">
            <el-option label="平台账号" value="PLATFORM" />
            <el-option label="员工账号" value="EMPLOYEE" />
            <el-option label="租户账号" value="TENANT" />
            <el-option label="外部账号" value="EXTERNAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="userForm.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="userForm.mobile" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="userForm.email" />
        </el-form-item>
        <el-form-item :label="userDialog.mode === 'create' ? '初始密码' : '重置密码（留空则不修改）'">
          <el-input v-model="userForm.password" type="password" show-password placeholder="默认 Admin@123456" />
        </el-form-item>
      </div>
      <el-form-item>
        <el-switch v-model="userForm.needResetPassword" active-text="下次登录强制修改密码" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="userForm.remark" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="userDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitUser">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="roleDialog.visible" :title="roleDialog.mode === 'create' ? '新增角色' : '编辑角色'" width="620px">
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="角色组 ID">
          <el-input-number v-model="roleForm.roleGroupId" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="角色编码" required>
          <el-input v-model="roleForm.roleCode" placeholder="如 role_admin" />
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="roleForm.roleName" placeholder="如 系统管理员" />
        </el-form-item>
        <el-form-item label="角色类型">
          <el-select v-model="roleForm.roleType" style="width: 100%">
            <el-option label="平台角色" value="PLATFORM" />
            <el-option label="租户角色" value="TENANT" />
            <el-option label="系统角色" value="SYSTEM" />
            <el-option label="自定义角色" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="roleForm.dataScopeType" style="width: 100%">
            <el-option label="全部数据" value="ALL" />
            <el-option label="本部门" value="DEPARTMENT" />
            <el-option label="本部门及子部门" value="DEPARTMENT_AND_CHILDREN" />
            <el-option label="仅本人" value="SELF" />
            <el-option label="自定义范围" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="roleForm.sortNo" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="roleForm.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item>
        <el-switch v-model="roleForm.system" active-text="系统角色" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="roleForm.remark" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="roleDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitRole">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="apiDialog.visible" :title="apiDialog.mode === 'create' ? '新增权限资源' : '编辑权限资源'" width="620px">
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="权限编码" required>
          <el-input v-model="apiForm.resourceCode" placeholder="如 iam:user:list" />
        </el-form-item>
        <el-form-item label="资源名称" required>
          <el-input v-model="apiForm.resourceName" placeholder="如 用户列表查询" />
        </el-form-item>
        <el-form-item label="请求方法">
          <el-select v-model="apiForm.httpMethod" style="width: 100%">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="PATCH" value="PATCH" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
        <el-form-item label="接口规则" required>
          <el-input v-model="apiForm.urlPattern" placeholder="例如 /api/iam/users" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="apiForm.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item>
        <el-switch v-model="apiForm.authRequired" active-text="需要鉴权" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="apiForm.remark" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="apiDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitApiResource">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="menuDialog.visible"
    :title="menuDialog.mode === 'create' ? '新增权限资源' : '编辑权限资源'"
    width="680px"
  >
    <el-form label-position="top">
      <el-form-item label="资源类型">
        <el-radio-group v-model="menuForm.menuType">
          <el-radio-button label="目录" value="DIRECTORY" />
          <el-radio-button label="页面" value="MENU" />
          <el-radio-button label="页面操作" value="BUTTON" />
          <el-radio-button label="外部链接" value="LINK" />
        </el-radio-group>
      </el-form-item>
      <div class="form-grid">
        <el-form-item label="上级资源">
          <el-select v-model="menuForm.parentId" style="width: 100%">
            <el-option v-for="item in validMenuParentOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="menuForm.menuType === 'BUTTON' ? '操作名称' : '资源名称'" required>
          <el-input
            v-model="menuForm.menuName"
            :placeholder="menuForm.menuType === 'BUTTON' ? '例如：新增用户' : '例如：用户管理'"
          />
        </el-form-item>
        <el-form-item label="资源标识 (唯一编码)" required>
          <el-input v-model="menuForm.menuCode" placeholder="系统唯一标识，如 iam_user" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="menuForm.sortNo" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="menuForm.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!menuForm.parentId || menuForm.parentId === 0" label="所属业务主域 (顶栏一级大类)">
          <el-select v-model="menuCluster" @change="handleClusterChange" style="width: 100%">
            <el-option label="平台总览 (overview)" value="overview" />
            <el-option label="基础治理 (foundation)" value="foundation" />
            <el-option label="运行支撑 (runtime)" value="runtime" />
            <el-option label="工程交付 (engineering)" value="engineering" />
            <el-option label="审计追踪 (assurance)" value="assurance" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="menuForm.menuType !== 'BUTTON'" label="导航显示位置 (单个独立控制顶部/左侧)">
          <el-select v-model="menuNavPosition" @change="handleNavPositionChange" style="width: 100%">
            <el-option label="智能跟随系统布局 (AUTO)" value="AUTO" />
            <el-option label="仅在顶部导航显示 (TOP)" value="TOP" />
            <el-option label="仅在左侧菜单显示 (SIDE)" value="SIDE" />
            <el-option label="顶部与左侧双显 (BOTH)" value="BOTH" />
          </el-select>
        </el-form-item>
      </div>
      <div v-if="menuForm.menuType !== 'BUTTON'" class="switch-grid">
        <el-switch v-model="menuForm.visible" active-text="导航可见" />
        <el-switch v-if="menuForm.menuType === 'MENU'" v-model="menuForm.keepAlive" active-text="页面缓存 (KeepAlive)" />
        <el-switch v-if="menuForm.menuType === 'MENU'" v-model="menuForm.isAffix" active-text="固定标签栏" />
        <el-switch v-if="menuForm.menuType === 'MENU' || menuForm.menuType === 'LINK'" v-model="menuForm.isFrame" active-text="内嵌外链 (Iframe)" />
      </div>
      <el-form-item v-if="menuForm.isFrame" label="内嵌外链地址 (Iframe Src)" required>
        <el-input v-model="menuForm.frameSrc" placeholder="例如 https://grafana.example.com" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="menuForm.remark" type="textarea" :rows="2" />
      </el-form-item>

      <el-collapse class="menu-advanced-settings">
        <el-collapse-item title="高级路由与开发配置" name="advanced">
          <div class="form-grid">
            <el-form-item label="访问权限标识">
              <el-input v-model="menuForm.permissionCode" placeholder="例如 iam:menu:query 或按钮权限" />
            </el-form-item>
            <el-form-item v-if="menuForm.menuType === 'MENU' || menuForm.menuType === 'LINK'" label="路由访问路径">
              <el-input v-model="menuForm.routePath" placeholder="例如 /iam/menu" />
            </el-form-item>
            <el-form-item v-if="menuForm.menuType === 'DIRECTORY'" label="重定向路径">
              <el-input v-model="menuForm.redirect" placeholder="例如 /iam/user" />
            </el-form-item>
            <el-form-item v-if="menuForm.menuType === 'MENU'" label="页面组件路径">
              <el-input v-model="menuForm.componentPath" placeholder="例如 @/views/iam/IamMenuView.vue" />
            </el-form-item>
            <el-form-item v-if="menuForm.menuType === 'MENU'" label="组件内部名称 (KeepAlive Name)">
              <el-input v-model="menuForm.componentName" placeholder="例如 IamMenuView" />
            </el-form-item>
            <el-form-item v-if="menuForm.menuType === 'MENU'" label="侧边栏高亮路由 (activeMenu)">
              <el-input v-model="menuForm.activeMenu" placeholder="用于详情页高亮父级路由" />
            </el-form-item>
            <el-form-item label="菜单显示图标">
              <IconPicker v-model="menuForm.icon" placeholder="点击选择开源图标库" />
            </el-form-item>
          </div>
          <el-form-item label="扩展元数据 (JSON)">
            <el-input v-model="menuForm.metaJson" type="textarea" :rows="3" placeholder='例如 {"section":"menu"}' />
          </el-form-item>
        </el-collapse-item>
      </el-collapse>
    </el-form>
    <template #footer>
      <el-button @click="menuDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitMenu">保存资源</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="dataScopeDialog.visible" :title="dataScopeDialog.mode === 'create' ? '新增数据范围' : '编辑数据范围'" width="660px">
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="范围编码" required>
          <el-input v-model="dataScopeForm.scopeCode" placeholder="如 scope_dept_dev" />
        </el-form-item>
        <el-form-item label="范围名称" required>
          <el-input v-model="dataScopeForm.scopeName" placeholder="如 研发中心专属数据范围" />
        </el-form-item>
        <el-form-item label="范围类型">
          <el-select v-model="dataScopeForm.scopeType" style="width: 100%">
            <el-option label="全量访问" value="ALL_ACCESS" />
            <el-option label="组织树范围" value="ORG_TREE" />
            <el-option label="单部门" value="DEPARTMENT" />
            <el-option label="仅本人" value="SELF_ONLY" />
            <el-option label="自定义规则" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="dataScopeForm.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </div>
      <div v-if="dataScopeForm.scopeType === 'CUSTOM'" style="margin-bottom: 16px">
        <el-form-item label="允许包含的组织部门">
          <el-tree-select
            v-model="customTemplateDeptIds"
            :data="templateDeptTree"
            node-key="id"
            :props="{ label: 'deptFullName', children: 'children' }"
            multiple
            show-checkbox
            check-strictly
            collapse-tags
            collapse-tags-tooltip
            filterable
            clearable
            placeholder="展开组织架构树，勾选允许访问的部门"
            style="width: 100%"
          />
        </el-form-item>
      </div>
      <div v-else class="scope-policy-hint" style="margin-bottom: 16px">
        <el-icon class="scope-hint-icon"><InfoFilled /></el-icon>
        <span class="scope-hint-desc">{{ getDataScopeTemplateDesc(dataScopeForm.scopeType) }}</span>
      </div>
      <el-form-item label="备注">
        <el-input v-model="dataScopeForm.remark" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dataScopeDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitDataScope">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="loginPolicyDialog.visible" :title="loginPolicyDialog.mode === 'create' ? '新增登录策略' : '编辑登录策略'" width="760px">
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="策略编码" required>
          <el-input v-model="loginPolicyForm.policyCode" placeholder="如 policy_standard_login" />
        </el-form-item>
        <el-form-item label="策略名称" required>
          <el-input v-model="loginPolicyForm.policyName" placeholder="如 标准登录安全策略" />
        </el-form-item>
        <el-form-item label="会话超时（分钟）">
          <el-input-number v-model="loginPolicyForm.sessionTimeoutMinutes" :min="5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="失败阈值">
          <el-input-number v-model="loginPolicyForm.maxFailedCount" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="锁定时长（分钟）">
          <el-input-number v-model="loginPolicyForm.lockMinutes" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="设备信任（天）">
          <el-input-number v-model="loginPolicyForm.deviceTrustDays" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="loginPolicyForm.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </div>
      <div class="switch-grid switch-grid--wide">
        <el-switch v-model="loginPolicyForm.allowPasswordLogin" active-text="允许密码登录" />
        <el-switch v-model="loginPolicyForm.allowSmsLogin" active-text="允许短信登录" />
        <el-switch v-model="loginPolicyForm.allowEmailLogin" active-text="允许邮箱登录" />
        <el-switch v-model="loginPolicyForm.allowSocialLogin" active-text="允许社交登录" />
        <el-switch v-model="loginPolicyForm.forceMfa" active-text="强制 MFA" />
      </div>
      <el-form-item label="IP 白名单 JSON">
        <el-input v-model="loginPolicyForm.ipAllowlistJson" type="textarea" :rows="4" placeholder='例如 ["10.0.0.0/8","192.168.1.10"]' />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="loginPolicyForm.remark" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="loginPolicyDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitLoginPolicy">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="passwordPolicyDialog.visible" :title="passwordPolicyDialog.mode === 'create' ? '新增密码策略' : '编辑密码策略'" width="760px">
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="策略编码" required>
          <el-input v-model="passwordPolicyForm.policyCode" placeholder="如 policy_strong_pwd" />
        </el-form-item>
        <el-form-item label="策略名称" required>
          <el-input v-model="passwordPolicyForm.policyName" placeholder="如 强密码策略" />
        </el-form-item>
        <el-form-item label="最小长度">
          <el-input-number v-model="passwordPolicyForm.minLength" :min="6" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最大长度">
          <el-input-number v-model="passwordPolicyForm.maxLength" :min="8" style="width: 100%" />
        </el-form-item>
        <el-form-item label="历史记忆次数">
          <el-input-number v-model="passwordPolicyForm.passwordHistoryLimit" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="过期天数">
          <el-input-number v-model="passwordPolicyForm.passwordExpireDays" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="临时密码时效（小时）">
          <el-input-number v-model="passwordPolicyForm.tempPasswordExpireHours" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="passwordPolicyForm.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </div>
      <div class="switch-grid switch-grid--wide">
        <el-switch v-model="passwordPolicyForm.requireUppercase" active-text="必须包含大写字母" />
        <el-switch v-model="passwordPolicyForm.requireLowercase" active-text="必须包含小写字母" />
        <el-switch v-model="passwordPolicyForm.requireNumber" active-text="必须包含数字" />
        <el-switch v-model="passwordPolicyForm.requireSpecial" active-text="必须包含特殊字符" />
      </div>
      <el-form-item label="备注">
        <el-input v-model="passwordPolicyForm.remark" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="passwordPolicyDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitPasswordPolicy">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.switch-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;
  margin-bottom: 18px;
}

.menu-advanced-settings {
  margin-top: 10px;
  border-top: 1px solid var(--sb-border-color);
}

.switch-grid--wide {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.scope-policy-hint {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 6px;
  background-color: #f8fafc;
  border: 1px solid #e2e8f0;

  .scope-hint-icon {
    font-size: 16px;
    color: #2563eb;
    margin-top: 2px;
    flex-shrink: 0;
  }

  .scope-hint-desc {
    font-size: 12px;
    color: #475569;
    line-height: 1.5;
  }
}

.account-onboard-card {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 6px;
  background-color: #f8fafc;
  border: 1px solid #cbd5e1;

  .account-onboard-switch-row {
    display: flex;
    align-items: center;
    gap: 12px;

    .account-onboard-hint {
      font-size: 12px;
      color: #64748b;
    }
  }

  .account-onboard-fields {
    margin-top: 12px;
    padding-top: 10px;
    border-top: 1px dashed #cbd5e1;
  }
}

@media (max-width: 860px) {
  .form-grid,
  .switch-grid,
  .switch-grid--wide {
    grid-template-columns: 1fr;
  }
}
</style>
