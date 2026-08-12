<script setup lang="ts">
import { computed, watch } from "vue";
import type { IamDepartmentRow, IamMenuRow, IamPositionRow } from "@/api/modules/iam";
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
        <el-form-item label="部门编码">
          <el-input v-model="departmentForm.deptCode" />
        </el-form-item>
        <el-form-item label="部门名称">
          <el-input v-model="departmentForm.deptName" />
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
        <el-form-item label="岗位编码">
          <el-input v-model="positionForm.positionCode" />
        </el-form-item>
        <el-form-item label="岗位名称">
          <el-input v-model="positionForm.positionName" />
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
        <el-form-item label="员工编号">
          <el-input v-model="employeeForm.employeeNo" :disabled="employeeDialog.mode === 'transfer'" />
        </el-form-item>
        <el-form-item label="员工姓名">
          <el-input v-model="employeeForm.employeeName" :disabled="employeeDialog.mode === 'transfer'" />
        </el-form-item>
        <el-form-item label="所属部门">
          <el-select v-model="employeeForm.deptId" style="width: 100%">
            <el-option v-for="item in departments" :key="item.id" :label="item.deptFullName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位">
          <el-select v-model="employeeForm.positionId" style="width: 100%">
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
        <el-input v-model="employeeForm.remark" type="textarea" :rows="3" />
      </el-form-item>
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
          <el-input v-model="userForm.userCode" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="userForm.username" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="userForm.nickname" />
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
        <el-form-item label="角色编码">
          <el-input v-model="roleForm.roleCode" />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input v-model="roleForm.roleName" />
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
        <el-form-item label="权限编码">
          <el-input v-model="apiForm.resourceCode" />
        </el-form-item>
        <el-form-item label="资源名称">
          <el-input v-model="apiForm.resourceName" />
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
        <el-form-item label="接口规则">
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
        <el-form-item :label="menuForm.menuType === 'BUTTON' ? '操作名称' : '资源名称'">
          <el-input
            v-model="menuForm.menuName"
            :placeholder="menuForm.menuType === 'BUTTON' ? '例如：新增用户' : '例如：用户管理'"
          />
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
      </div>
      <div v-if="menuForm.menuType !== 'BUTTON'" class="switch-grid">
        <el-switch v-model="menuForm.visible" active-text="导航可见" />
        <el-switch v-if="menuForm.menuType === 'MENU'" v-model="menuForm.keepAlive" active-text="页面缓存" />
      </div>
      <el-form-item label="备注">
        <el-input v-model="menuForm.remark" type="textarea" :rows="2" />
      </el-form-item>

      <el-collapse class="menu-advanced-settings">
        <el-collapse-item title="开发配置" name="advanced">
          <div class="form-grid">
            <el-form-item label="资源标识">
              <el-input v-model="menuForm.menuCode" placeholder="系统唯一标识" />
            </el-form-item>
            <el-form-item v-if="menuForm.menuType !== 'BUTTON'" label="访问权限">
              <el-input v-model="menuForm.permissionCode" placeholder="例如 iam:menu:query" />
            </el-form-item>
            <el-form-item v-if="menuForm.menuType === 'MENU' || menuForm.menuType === 'LINK'" label="访问路径">
              <el-input v-model="menuForm.routePath" placeholder="例如 /iam/menu" />
            </el-form-item>
            <el-form-item v-if="menuForm.menuType === 'MENU'" label="页面组件">
              <el-input v-model="menuForm.componentPath" placeholder="例如 @/views/iam/IamMenuView.vue" />
            </el-form-item>
            <el-form-item label="图标">
              <el-input v-model="menuForm.icon" placeholder="图标组件名称" />
            </el-form-item>
          </div>
          <el-form-item label="扩展元数据">
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
        <el-form-item label="范围编码">
          <el-input v-model="dataScopeForm.scopeCode" />
        </el-form-item>
        <el-form-item label="范围名称">
          <el-input v-model="dataScopeForm.scopeName" />
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
      <el-form-item label="规则 JSON">
        <el-input
          v-model="dataScopeForm.scopeRuleJson"
          type="textarea"
          :rows="5"
          placeholder='例如 {"orgIds":[1001,1002],"includeChildren":true}'
        />
      </el-form-item>
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
        <el-form-item label="策略编码">
          <el-input v-model="loginPolicyForm.policyCode" />
        </el-form-item>
        <el-form-item label="策略名称">
          <el-input v-model="loginPolicyForm.policyName" />
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
        <el-form-item label="策略编码">
          <el-input v-model="passwordPolicyForm.policyCode" />
        </el-form-item>
        <el-form-item label="策略名称">
          <el-input v-model="passwordPolicyForm.policyName" />
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

@media (max-width: 860px) {
  .form-grid,
  .switch-grid,
  .switch-grid--wide {
    grid-template-columns: 1fr;
  }
}
</style>
