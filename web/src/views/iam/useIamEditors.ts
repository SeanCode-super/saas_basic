import { reactive, ref, type Ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createIamApiResource,
  createIamDataScope,
  createIamDepartment,
  createIamEmployee,
  transferIamEmployee,
  createIamLoginPolicy,
  createIamMenu,
  createIamPasswordPolicy,
  createIamPosition,
  createIamRole,
  createIamUser,
  updateIamApiResource,
  updateIamApiResourceStatus,
  updateIamDataScope,
  updateIamDataScopeStatus,
  updateIamDepartment,
  updateIamDepartmentStatus,
  updateIamEmployee,
  updateIamEmployeeStatus,
  updateIamLoginPolicy,
  updateIamLoginPolicyStatus,
  updateIamMenu,
  updateIamMenuStatus,
  updateIamPasswordPolicy,
  updateIamPasswordPolicyStatus,
  updateIamPosition,
  updateIamPositionStatus,
  updateIamRole,
  updateIamRoleStatus,
  updateIamUser,
  updateIamUserStatus
} from "@/api/modules/iam";
import type {
  IamApiResourceRow,
  IamDataScopeRow,
  IamDepartmentRow,
  IamEmployeeRow,
  IamLoginPolicyRow,
  IamMenuRow,
  IamPasswordPolicyRow,
  IamPositionRow,
  IamRoleRow,
  IamUserRow
} from "@/api/modules/iam";
import type {
  ApiFormState,
  DataScopeFormState,
  DepartmentFormState,
  DialogMode,
  EmployeeFormState,
  LoginPolicyFormState,
  MenuFormState,
  PasswordPolicyFormState,
  PositionFormState,
  RoleFormState,
  UserFormState
} from "./iam-dialog-types";

interface UseIamEditorsParams {
  getCurrentTenantId: () => number;
  loadData: () => Promise<void>;
  departments: Ref<IamDepartmentRow[]>;
  positions: Ref<IamPositionRow[]>;
  employees: Ref<IamEmployeeRow[]>;
  users: Ref<IamUserRow[]>;
  roles: Ref<IamRoleRow[]>;
  menus: Ref<IamMenuRow[]>;
}

export function useIamEditors(params: UseIamEditorsParams) {
  const submitting = ref(false);

  const departmentDialog = reactive({ visible: false, mode: "create" as DialogMode, id: 0 });
  const positionDialog = reactive({ visible: false, mode: "create" as DialogMode, id: 0 });
  const employeeDialog = reactive({ visible: false, mode: "create" as DialogMode, id: 0 });
  const userDialog = reactive({ visible: false, mode: "create" as DialogMode, id: 0 });
  const roleDialog = reactive({ visible: false, mode: "create" as DialogMode, id: 0 });
  const apiDialog = reactive({ visible: false, mode: "create" as DialogMode, id: 0 });
  const menuDialog = reactive({ visible: false, mode: "create" as DialogMode, id: 0 });
  const dataScopeDialog = reactive({ visible: false, mode: "create" as DialogMode, id: 0 });
  const loginPolicyDialog = reactive({ visible: false, mode: "create" as DialogMode, id: 0 });
  const passwordPolicyDialog = reactive({ visible: false, mode: "create" as DialogMode, id: 0 });

  const departmentForm = reactive<DepartmentFormState>({
    parentId: 0,
    deptCode: "",
    deptName: "",
    deptFullName: "",
    leaderUserId: 0,
    status: "ENABLED",
    sortNo: 1,
    remark: ""
  });

  const positionForm = reactive<PositionFormState>({
    positionCode: "",
    positionName: "",
    positionLevel: "",
    status: "ENABLED",
    sortNo: 1,
    remark: ""
  });

  const employeeForm = reactive<EmployeeFormState>({
    employeeNo: "",
    employeeName: "",
    deptId: 0,
    positionId: 0,
    mobile: "",
    email: "",
    gender: "MALE",
    hireDate: "",
    employeeStatus: "ACTIVE",
    remark: ""
  });

  const userForm = reactive<UserFormState>({
    userCode: "",
    username: "",
    nickname: "",
    employeeId: 0,
    userType: "PLATFORM",
    status: "ENABLED",
    mobile: "",
    email: "",
    password: "",
    needResetPassword: true,
    remark: ""
  });

  const roleForm = reactive<RoleFormState>({
    roleGroupId: 1,
    roleCode: "",
    roleName: "",
    roleType: "PLATFORM",
    dataScopeType: "ALL",
    status: "ENABLED",
    system: false,
    sortNo: 1,
    remark: ""
  });

  const apiForm = reactive<ApiFormState>({
    resourceCode: "",
    resourceName: "",
    httpMethod: "GET",
    urlPattern: "",
    authRequired: true,
    status: "ENABLED",
    remark: ""
  });

  const menuForm = reactive<MenuFormState>({
    parentId: 0,
    menuType: "MENU",
    menuCode: "",
    menuName: "",
    routePath: "",
    componentPath: "",
    permissionCode: "",
    icon: "",
    visible: true,
    keepAlive: true,
    sortNo: 1,
    status: "ENABLED",
    metaJson: "",
    remark: ""
  });

  const dataScopeForm = reactive<DataScopeFormState>({
    scopeCode: "",
    scopeName: "",
    scopeType: "ORG_TREE",
    scopeRuleJson: "",
    status: "ENABLED",
    remark: ""
  });

  const loginPolicyForm = reactive<LoginPolicyFormState>({
    policyCode: "",
    policyName: "",
    allowPasswordLogin: true,
    allowSmsLogin: false,
    allowEmailLogin: false,
    allowSocialLogin: false,
    forceMfa: false,
    sessionTimeoutMinutes: 120,
    maxFailedCount: 5,
    lockMinutes: 30,
    ipAllowlistJson: "",
    deviceTrustDays: 7,
    status: "ENABLED",
    remark: ""
  });

  const passwordPolicyForm = reactive<PasswordPolicyFormState>({
    policyCode: "",
    policyName: "",
    minLength: 10,
    maxLength: 32,
    requireUppercase: true,
    requireLowercase: true,
    requireNumber: true,
    requireSpecial: true,
    passwordHistoryLimit: 5,
    passwordExpireDays: 90,
    tempPasswordExpireHours: 24,
    status: "ENABLED",
    remark: ""
  });

  function resetDepartmentForm() {
    departmentForm.parentId = 0;
    departmentForm.deptCode = "";
    departmentForm.deptName = "";
    departmentForm.deptFullName = "";
    departmentForm.leaderUserId = 0;
    departmentForm.status = "ENABLED";
    departmentForm.sortNo = params.departments.value.length + 1;
    departmentForm.remark = "";
  }

  function resetPositionForm() {
    positionForm.positionCode = "";
    positionForm.positionName = "";
    positionForm.positionLevel = "";
    positionForm.status = "ENABLED";
    positionForm.sortNo = params.positions.value.length + 1;
    positionForm.remark = "";
  }

  function resetEmployeeForm() {
    employeeForm.employeeNo = "";
    employeeForm.employeeName = "";
    employeeForm.deptId = params.departments.value[0]?.id ?? 0;
    employeeForm.positionId = params.positions.value[0]?.id ?? 0;
    employeeForm.mobile = "";
    employeeForm.email = "";
    employeeForm.gender = "MALE";
    employeeForm.hireDate = "";
    employeeForm.employeeStatus = "ACTIVE";
    employeeForm.remark = "";
  }

  function resetUserForm() {
    userForm.userCode = "";
    userForm.username = "";
    userForm.nickname = "";
    userForm.employeeId = 0;
    userForm.userType = "PLATFORM";
    userForm.status = "ENABLED";
    userForm.mobile = "";
    userForm.email = "";
    userForm.password = "";
    userForm.needResetPassword = true;
    userForm.remark = "";
  }

  function resetRoleForm() {
    roleForm.roleGroupId = 1;
    roleForm.roleCode = "";
    roleForm.roleName = "";
    roleForm.roleType = "PLATFORM";
    roleForm.dataScopeType = "ALL";
    roleForm.status = "ENABLED";
    roleForm.system = false;
    roleForm.sortNo = params.roles.value.length + 1;
    roleForm.remark = "";
  }

  function resetApiForm() {
    apiForm.resourceCode = "";
    apiForm.resourceName = "";
    apiForm.httpMethod = "GET";
    apiForm.urlPattern = "";
    apiForm.authRequired = true;
    apiForm.status = "ENABLED";
    apiForm.remark = "";
  }

  function resetMenuForm() {
    menuForm.parentId = 0;
    menuForm.menuType = "MENU";
    menuForm.menuCode = "";
    menuForm.menuName = "";
    menuForm.routePath = "";
    menuForm.componentPath = "";
    menuForm.permissionCode = "";
    menuForm.icon = "";
    menuForm.visible = true;
    menuForm.keepAlive = true;
    menuForm.sortNo = params.menus.value.length + 1;
    menuForm.status = "ENABLED";
    menuForm.metaJson = "";
    menuForm.remark = "";
  }

  function resetDataScopeForm() {
    dataScopeForm.scopeCode = "";
    dataScopeForm.scopeName = "";
    dataScopeForm.scopeType = "ORG_TREE";
    dataScopeForm.scopeRuleJson = "";
    dataScopeForm.status = "ENABLED";
    dataScopeForm.remark = "";
  }

  function resetLoginPolicyForm() {
    loginPolicyForm.policyCode = "";
    loginPolicyForm.policyName = "";
    loginPolicyForm.allowPasswordLogin = true;
    loginPolicyForm.allowSmsLogin = false;
    loginPolicyForm.allowEmailLogin = false;
    loginPolicyForm.allowSocialLogin = false;
    loginPolicyForm.forceMfa = false;
    loginPolicyForm.sessionTimeoutMinutes = 120;
    loginPolicyForm.maxFailedCount = 5;
    loginPolicyForm.lockMinutes = 30;
    loginPolicyForm.ipAllowlistJson = "";
    loginPolicyForm.deviceTrustDays = 7;
    loginPolicyForm.status = "ENABLED";
    loginPolicyForm.remark = "";
  }

  function resetPasswordPolicyForm() {
    passwordPolicyForm.policyCode = "";
    passwordPolicyForm.policyName = "";
    passwordPolicyForm.minLength = 10;
    passwordPolicyForm.maxLength = 32;
    passwordPolicyForm.requireUppercase = true;
    passwordPolicyForm.requireLowercase = true;
    passwordPolicyForm.requireNumber = true;
    passwordPolicyForm.requireSpecial = true;
    passwordPolicyForm.passwordHistoryLimit = 5;
    passwordPolicyForm.passwordExpireDays = 90;
    passwordPolicyForm.tempPasswordExpireHours = 24;
    passwordPolicyForm.status = "ENABLED";
    passwordPolicyForm.remark = "";
  }

  function openDepartmentCreate() {
    departmentDialog.mode = "create";
    departmentDialog.id = 0;
    resetDepartmentForm();
    departmentDialog.visible = true;
  }

  function openDepartmentEdit(row: IamDepartmentRow) {
    departmentDialog.mode = "edit";
    departmentDialog.id = row.id;
    departmentForm.parentId = row.parentId;
    departmentForm.deptCode = row.deptCode;
    departmentForm.deptName = row.deptName;
    departmentForm.deptFullName = row.deptFullName;
    departmentForm.leaderUserId = row.leaderUserId ?? 0;
    departmentForm.status = row.status;
    departmentForm.sortNo = row.sortNo;
    departmentForm.remark = row.remark ?? "";
    departmentDialog.visible = true;
  }

  function openPositionCreate() {
    positionDialog.mode = "create";
    positionDialog.id = 0;
    resetPositionForm();
    positionDialog.visible = true;
  }

  function openPositionEdit(row: IamPositionRow) {
    positionDialog.mode = "edit";
    positionDialog.id = row.id;
    positionForm.positionCode = row.positionCode;
    positionForm.positionName = row.positionName;
    positionForm.positionLevel = row.positionLevel ?? "";
    positionForm.status = row.status;
    positionForm.sortNo = row.sortNo;
    positionForm.remark = row.remark ?? "";
    positionDialog.visible = true;
  }

  function openEmployeeCreate() {
    employeeDialog.mode = "create";
    employeeDialog.id = 0;
    resetEmployeeForm();
    employeeDialog.visible = true;
  }

  function openEmployeeEdit(row: IamEmployeeRow) {
    employeeDialog.mode = "edit";
    employeeDialog.id = row.id;
    employeeForm.employeeNo = row.employeeNo;
    employeeForm.employeeName = row.employeeName;
    employeeForm.deptId = row.deptId;
    employeeForm.positionId = row.positionId;
    employeeForm.mobile = row.mobile ?? "";
    employeeForm.email = row.email ?? "";
    employeeForm.gender = row.gender ?? "MALE";
    employeeForm.hireDate = row.hireDate ?? "";
    employeeForm.employeeStatus = row.employeeStatus;
    employeeForm.remark = row.remark ?? "";
    employeeDialog.visible = true;
  }

  function openEmployeeTransfer(row: IamEmployeeRow) {
    employeeDialog.mode = "transfer";
    employeeDialog.id = row.id;
    employeeForm.employeeNo = row.employeeNo;
    employeeForm.employeeName = row.employeeName;
    employeeForm.deptId = row.deptId;
    employeeForm.positionId = row.positionId;
    employeeForm.mobile = row.mobile ?? "";
    employeeForm.email = row.email ?? "";
    employeeForm.gender = row.gender ?? "MALE";
    employeeForm.hireDate = row.hireDate ?? "";
    employeeForm.employeeStatus = row.employeeStatus;
    employeeForm.remark = row.remark ?? "";
    employeeDialog.visible = true;
  }

  function openUserCreate() {
    userDialog.mode = "create";
    userDialog.id = 0;
    resetUserForm();
    userDialog.visible = true;
  }

  function openUserEdit(row: IamUserRow) {
    userDialog.mode = "edit";
    userDialog.id = row.id;
    userForm.userCode = row.userCode;
    userForm.username = row.username;
    userForm.nickname = row.nickname;
    userForm.employeeId = row.employeeId ?? 0;
    userForm.userType = row.userType;
    userForm.status = row.status;
    userForm.mobile = row.mobile ?? "";
    userForm.email = row.email ?? "";
    userForm.password = "";
    userForm.needResetPassword = false;
    userForm.remark = row.remark ?? "";
    userDialog.visible = true;
  }

  function openRoleCreate() {
    roleDialog.mode = "create";
    roleDialog.id = 0;
    resetRoleForm();
    roleDialog.visible = true;
  }

  function openRoleEdit(row: IamRoleRow) {
    roleDialog.mode = "edit";
    roleDialog.id = row.id;
    roleForm.roleGroupId = row.roleGroupId || 1;
    roleForm.roleCode = row.roleCode;
    roleForm.roleName = row.roleName;
    roleForm.roleType = row.roleType;
    roleForm.dataScopeType = row.dataScopeType;
    roleForm.status = row.status;
    roleForm.system = row.system;
    roleForm.sortNo = row.sortNo;
    roleForm.remark = row.remark ?? "";
    roleDialog.visible = true;
  }

  function openApiCreate() {
    apiDialog.mode = "create";
    apiDialog.id = 0;
    resetApiForm();
    apiDialog.visible = true;
  }

  function openApiEdit(row: IamApiResourceRow) {
    apiDialog.mode = "edit";
    apiDialog.id = row.id;
    apiForm.resourceCode = row.resourceCode;
    apiForm.resourceName = row.resourceName;
    apiForm.httpMethod = row.httpMethod;
    apiForm.urlPattern = row.urlPattern;
    apiForm.authRequired = row.authRequired;
    apiForm.status = row.status;
    apiForm.remark = row.remark ?? "";
    apiDialog.visible = true;
  }

  function openMenuCreate() {
    menuDialog.mode = "create";
    menuDialog.id = 0;
    resetMenuForm();
    menuDialog.visible = true;
  }

  function openMenuEdit(row: IamMenuRow) {
    menuDialog.mode = "edit";
    menuDialog.id = row.id;
    menuForm.parentId = row.parentId;
    menuForm.menuType = row.menuType;
    menuForm.menuCode = row.menuCode;
    menuForm.menuName = row.menuName;
    menuForm.routePath = row.routePath ?? "";
    menuForm.componentPath = row.componentPath ?? "";
    menuForm.permissionCode = row.permissionCode ?? "";
    menuForm.icon = row.icon ?? "";
    menuForm.visible = row.visible;
    menuForm.keepAlive = row.keepAlive;
    menuForm.sortNo = row.sortNo;
    menuForm.status = row.status;
    menuForm.metaJson = row.metaJson ?? "";
    menuForm.remark = row.remark ?? "";
    menuDialog.visible = true;
  }

  function openDataScopeCreate() {
    dataScopeDialog.mode = "create";
    dataScopeDialog.id = 0;
    resetDataScopeForm();
    dataScopeDialog.visible = true;
  }

  function openDataScopeEdit(row: IamDataScopeRow) {
    dataScopeDialog.mode = "edit";
    dataScopeDialog.id = row.id;
    dataScopeForm.scopeCode = row.scopeCode;
    dataScopeForm.scopeName = row.scopeName;
    dataScopeForm.scopeType = row.scopeType;
    dataScopeForm.scopeRuleJson = row.scopeRuleJson ?? "";
    dataScopeForm.status = row.status;
    dataScopeForm.remark = row.remark ?? "";
    dataScopeDialog.visible = true;
  }

  function openLoginPolicyCreate() {
    loginPolicyDialog.mode = "create";
    loginPolicyDialog.id = 0;
    resetLoginPolicyForm();
    loginPolicyDialog.visible = true;
  }

  function openLoginPolicyEdit(row: IamLoginPolicyRow) {
    loginPolicyDialog.mode = "edit";
    loginPolicyDialog.id = row.id;
    loginPolicyForm.policyCode = row.policyCode;
    loginPolicyForm.policyName = row.policyName;
    loginPolicyForm.allowPasswordLogin = row.allowPasswordLogin;
    loginPolicyForm.allowSmsLogin = row.allowSmsLogin;
    loginPolicyForm.allowEmailLogin = row.allowEmailLogin;
    loginPolicyForm.allowSocialLogin = row.allowSocialLogin;
    loginPolicyForm.forceMfa = row.forceMfa;
    loginPolicyForm.sessionTimeoutMinutes = row.sessionTimeoutMinutes;
    loginPolicyForm.maxFailedCount = row.maxFailedCount;
    loginPolicyForm.lockMinutes = row.lockMinutes;
    loginPolicyForm.ipAllowlistJson = row.ipAllowlistJson ?? "";
    loginPolicyForm.deviceTrustDays = row.deviceTrustDays;
    loginPolicyForm.status = row.status;
    loginPolicyForm.remark = row.remark ?? "";
    loginPolicyDialog.visible = true;
  }

  function openPasswordPolicyCreate() {
    passwordPolicyDialog.mode = "create";
    passwordPolicyDialog.id = 0;
    resetPasswordPolicyForm();
    passwordPolicyDialog.visible = true;
  }

  function openPasswordPolicyEdit(row: IamPasswordPolicyRow) {
    passwordPolicyDialog.mode = "edit";
    passwordPolicyDialog.id = row.id;
    passwordPolicyForm.policyCode = row.policyCode;
    passwordPolicyForm.policyName = row.policyName;
    passwordPolicyForm.minLength = row.minLength;
    passwordPolicyForm.maxLength = row.maxLength;
    passwordPolicyForm.requireUppercase = row.requireUppercase;
    passwordPolicyForm.requireLowercase = row.requireLowercase;
    passwordPolicyForm.requireNumber = row.requireNumber;
    passwordPolicyForm.requireSpecial = row.requireSpecial;
    passwordPolicyForm.passwordHistoryLimit = row.passwordHistoryLimit;
    passwordPolicyForm.passwordExpireDays = row.passwordExpireDays;
    passwordPolicyForm.tempPasswordExpireHours = row.tempPasswordExpireHours;
    passwordPolicyForm.status = row.status;
    passwordPolicyForm.remark = row.remark ?? "";
    passwordPolicyDialog.visible = true;
  }

  async function withSubmit(task: () => Promise<void>) {
    submitting.value = true;
    try {
      await task();
    } catch (error: any) {
      ElMessage.error(error?.response?.data?.message ?? error?.message ?? "保存失败");
    } finally {
      submitting.value = false;
    }
  }

  async function runToggle(task: () => Promise<unknown>, successMessage: string, fallbackMessage: string) {
    try {
      await task();
      ElMessage.success(successMessage);
      await params.loadData();
    } catch (error: any) {
      ElMessage.error(error?.response?.data?.message ?? fallbackMessage);
    }
  }

  async function runBatchStatusUpdate<T>(options: {
    rows: T[];
    nextStatus: string;
    getId: (row: T) => number;
    getCurrentStatus: (row: T) => string;
    update: (id: number, status: string) => Promise<unknown>;
    label: string;
    successMessage: string;
    fallbackMessage: string;
  }) {
    if (!options.rows.length) {
      ElMessage.warning(`请先选择要${options.label}的记录`);
      return;
    }

    const targets = options.rows.filter((row) => options.getCurrentStatus(row) !== options.nextStatus);
    if (!targets.length) {
      ElMessage.info(`所选记录已全部处于${options.label}状态`);
      return;
    }

    try {
      await ElMessageBox.confirm(
        `将 ${targets.length} 条记录批量设为${options.label}，这会立即影响当前控制面的可用状态，是否继续？`,
        "批量状态变更",
        {
          type: "warning",
          confirmButtonText: "确认执行",
          cancelButtonText: "取消"
        }
      );
    } catch {
      return;
    }

    try {
      await Promise.all(targets.map((row) => options.update(options.getId(row), options.nextStatus)));
      ElMessage.success(options.successMessage);
      await params.loadData();
    } catch (error: any) {
      ElMessage.error(error?.response?.data?.message ?? options.fallbackMessage);
    }
  }

  async function submitDepartment() {
    await withSubmit(async () => {
      const payload = {
        tenantId: params.getCurrentTenantId(),
        parentId: departmentForm.parentId,
        deptCode: departmentForm.deptCode,
        deptName: departmentForm.deptName,
        deptFullName: departmentForm.deptFullName,
        leaderUserId: departmentForm.leaderUserId,
        status: departmentForm.status,
        sortNo: departmentForm.sortNo,
        remark: departmentForm.remark
      };
      if (departmentDialog.mode === "create") {
        await createIamDepartment(payload);
        ElMessage.success("部门已创建");
      } else {
        await updateIamDepartment(departmentDialog.id, payload);
        ElMessage.success("部门已更新");
      }
      departmentDialog.visible = false;
      await params.loadData();
    });
  }

  async function submitPosition() {
    await withSubmit(async () => {
      const payload = {
        tenantId: params.getCurrentTenantId(),
        positionCode: positionForm.positionCode,
        positionName: positionForm.positionName,
        positionLevel: positionForm.positionLevel,
        status: positionForm.status,
        sortNo: positionForm.sortNo,
        remark: positionForm.remark
      };
      if (positionDialog.mode === "create") {
        await createIamPosition(payload);
        ElMessage.success("岗位已创建");
      } else {
        await updateIamPosition(positionDialog.id, payload);
        ElMessage.success("岗位已更新");
      }
      positionDialog.visible = false;
      await params.loadData();
    });
  }

  async function submitEmployee() {
    await withSubmit(async () => {
      if (employeeDialog.mode === "transfer") {
        await transferIamEmployee(employeeDialog.id, {
          deptId: employeeForm.deptId,
          positionId: employeeForm.positionId,
          remark: employeeForm.remark
        });
        ElMessage.success("组织异动已保存");
        employeeDialog.visible = false;
        await params.loadData();
        return;
      }
      const payload = {
        tenantId: params.getCurrentTenantId(),
        employeeNo: employeeForm.employeeNo,
        employeeName: employeeForm.employeeName,
        deptId: employeeForm.deptId,
        positionId: employeeForm.positionId,
        mobile: employeeForm.mobile,
        email: employeeForm.email,
        gender: employeeForm.gender,
        hireDate: employeeForm.hireDate || undefined,
        employeeStatus: employeeForm.employeeStatus,
        remark: employeeForm.remark
      };
      if (employeeDialog.mode === "create") {
        await createIamEmployee(payload);
        ElMessage.success("员工档案已创建");
      } else {
        await updateIamEmployee(employeeDialog.id, payload);
        ElMessage.success("员工档案已更新");
      }
      employeeDialog.visible = false;
      await params.loadData();
    });
  }

  async function submitUser() {
    await withSubmit(async () => {
      const payload = {
        tenantId: params.getCurrentTenantId(),
        userCode: userForm.userCode,
        username: userForm.username,
        nickname: userForm.nickname,
        employeeId: userForm.employeeId || undefined,
        userType: userForm.userType,
        status: userForm.status,
        mobile: userForm.mobile,
        email: userForm.email,
        password: userForm.password || undefined,
        needResetPassword: userForm.needResetPassword,
        remark: userForm.remark
      };
      if (userDialog.mode === "create") {
        await createIamUser(payload);
        ElMessage.success("用户已创建，未填写密码时默认使用 Admin@123456");
      } else {
        await updateIamUser(userDialog.id, payload);
        ElMessage.success("用户已更新");
      }
      userDialog.visible = false;
      await params.loadData();
    });
  }

  async function submitRole() {
    await withSubmit(async () => {
      const payload = {
        tenantId: params.getCurrentTenantId(),
        roleGroupId: roleForm.roleGroupId,
        roleCode: roleForm.roleCode,
        roleName: roleForm.roleName,
        roleType: roleForm.roleType,
        dataScopeType: roleForm.dataScopeType,
        status: roleForm.status,
        system: roleForm.system,
        sortNo: roleForm.sortNo,
        remark: roleForm.remark
      };
      if (roleDialog.mode === "create") {
        await createIamRole(payload);
        ElMessage.success("角色已创建");
      } else {
        await updateIamRole(roleDialog.id, payload);
        ElMessage.success("角色已更新");
      }
      roleDialog.visible = false;
      await params.loadData();
    });
  }

  async function submitApiResource() {
    await withSubmit(async () => {
      const payload = {
        tenantId: params.getCurrentTenantId(),
        resourceCode: apiForm.resourceCode,
        resourceName: apiForm.resourceName,
        httpMethod: apiForm.httpMethod,
        urlPattern: apiForm.urlPattern,
        authRequired: apiForm.authRequired,
        status: apiForm.status,
        remark: apiForm.remark
      };
      if (apiDialog.mode === "create") {
        await createIamApiResource(payload);
        ElMessage.success("权限资源已创建");
      } else {
        await updateIamApiResource(apiDialog.id, payload);
        ElMessage.success("权限资源已更新");
      }
      apiDialog.visible = false;
      await params.loadData();
    });
  }

  async function submitMenu() {
    await withSubmit(async () => {
      const payload = {
        tenantId: params.getCurrentTenantId(),
        parentId: menuForm.parentId || undefined,
        menuType: menuForm.menuType,
        menuCode: menuForm.menuCode,
        menuName: menuForm.menuName,
        routePath: menuForm.routePath || undefined,
        componentPath: menuForm.componentPath || undefined,
        permissionCode: menuForm.permissionCode || undefined,
        icon: menuForm.icon || undefined,
        visible: menuForm.visible,
        keepAlive: menuForm.keepAlive,
        sortNo: menuForm.sortNo,
        status: menuForm.status,
        metaJson: menuForm.metaJson || undefined,
        remark: menuForm.remark
      };
      if (menuDialog.mode === "create") {
        await createIamMenu(payload);
        ElMessage.success("菜单已创建");
      } else {
        await updateIamMenu(menuDialog.id, payload);
        ElMessage.success("菜单已更新");
      }
      menuDialog.visible = false;
      await params.loadData();
    });
  }

  async function submitDataScope() {
    await withSubmit(async () => {
      const payload = {
        tenantId: params.getCurrentTenantId(),
        scopeCode: dataScopeForm.scopeCode,
        scopeName: dataScopeForm.scopeName,
        scopeType: dataScopeForm.scopeType,
        scopeRuleJson: dataScopeForm.scopeRuleJson || undefined,
        status: dataScopeForm.status,
        remark: dataScopeForm.remark
      };
      if (dataScopeDialog.mode === "create") {
        await createIamDataScope(payload);
        ElMessage.success("数据权限范围已创建");
      } else {
        await updateIamDataScope(dataScopeDialog.id, payload);
        ElMessage.success("数据权限范围已更新");
      }
      dataScopeDialog.visible = false;
      await params.loadData();
    });
  }

  async function submitLoginPolicy() {
    await withSubmit(async () => {
      const payload = {
        tenantId: params.getCurrentTenantId(),
        policyCode: loginPolicyForm.policyCode,
        policyName: loginPolicyForm.policyName,
        allowPasswordLogin: loginPolicyForm.allowPasswordLogin,
        allowSmsLogin: loginPolicyForm.allowSmsLogin,
        allowEmailLogin: loginPolicyForm.allowEmailLogin,
        allowSocialLogin: loginPolicyForm.allowSocialLogin,
        forceMfa: loginPolicyForm.forceMfa,
        sessionTimeoutMinutes: loginPolicyForm.sessionTimeoutMinutes,
        maxFailedCount: loginPolicyForm.maxFailedCount,
        lockMinutes: loginPolicyForm.lockMinutes,
        ipAllowlistJson: loginPolicyForm.ipAllowlistJson || undefined,
        deviceTrustDays: loginPolicyForm.deviceTrustDays,
        status: loginPolicyForm.status,
        remark: loginPolicyForm.remark
      };
      if (loginPolicyDialog.mode === "create") {
        await createIamLoginPolicy(payload);
        ElMessage.success("登录策略已创建");
      } else {
        await updateIamLoginPolicy(loginPolicyDialog.id, payload);
        ElMessage.success("登录策略已更新");
      }
      loginPolicyDialog.visible = false;
      await params.loadData();
    });
  }

  async function submitPasswordPolicy() {
    await withSubmit(async () => {
      const payload = {
        tenantId: params.getCurrentTenantId(),
        policyCode: passwordPolicyForm.policyCode,
        policyName: passwordPolicyForm.policyName,
        minLength: passwordPolicyForm.minLength,
        maxLength: passwordPolicyForm.maxLength,
        requireUppercase: passwordPolicyForm.requireUppercase,
        requireLowercase: passwordPolicyForm.requireLowercase,
        requireNumber: passwordPolicyForm.requireNumber,
        requireSpecial: passwordPolicyForm.requireSpecial,
        passwordHistoryLimit: passwordPolicyForm.passwordHistoryLimit,
        passwordExpireDays: passwordPolicyForm.passwordExpireDays,
        tempPasswordExpireHours: passwordPolicyForm.tempPasswordExpireHours,
        status: passwordPolicyForm.status,
        remark: passwordPolicyForm.remark
      };
      if (passwordPolicyDialog.mode === "create") {
        await createIamPasswordPolicy(payload);
        ElMessage.success("密码策略已创建");
      } else {
        await updateIamPasswordPolicy(passwordPolicyDialog.id, payload);
        ElMessage.success("密码策略已更新");
      }
      passwordPolicyDialog.visible = false;
      await params.loadData();
    });
  }

  async function toggleDepartment(row: IamDepartmentRow) {
    await runToggle(
      () => updateIamDepartmentStatus(row.id, { status: nextBinaryStatus(row.status) }),
      "部门状态已更新",
      "更新部门状态失败"
    );
  }

  async function togglePosition(row: IamPositionRow) {
    await runToggle(
      () => updateIamPositionStatus(row.id, { status: nextBinaryStatus(row.status) }),
      "岗位状态已更新",
      "更新岗位状态失败"
    );
  }

  async function toggleEmployee(row: IamEmployeeRow) {
    await runToggle(
      () => updateIamEmployeeStatus(row.id, { status: nextEmployeeStatus(row.employeeStatus) }),
      "员工状态已更新",
      "更新员工状态失败"
    );
  }

  async function toggleUser(row: IamUserRow) {
    await runToggle(
      () => updateIamUserStatus(row.id, { status: nextBinaryStatus(row.status) }),
      "用户状态已更新",
      "更新用户状态失败"
    );
  }

  async function toggleRole(row: IamRoleRow) {
    await runToggle(
      () => updateIamRoleStatus(row.id, { status: nextBinaryStatus(row.status) }),
      "角色状态已更新",
      "更新角色状态失败"
    );
  }

  async function toggleApiResource(row: IamApiResourceRow) {
    await runToggle(
      () => updateIamApiResourceStatus(row.id, { status: nextBinaryStatus(row.status) }),
      "权限资源状态已更新",
      "更新权限资源状态失败"
    );
  }

  async function toggleMenu(row: IamMenuRow) {
    await runToggle(
      () => updateIamMenuStatus(row.id, { status: nextBinaryStatus(row.status) }),
      "菜单状态已更新",
      "更新菜单状态失败"
    );
  }

  async function toggleDataScope(row: IamDataScopeRow) {
    await runToggle(
      () => updateIamDataScopeStatus(row.id, { status: nextBinaryStatus(row.status) }),
      "数据权限范围状态已更新",
      "更新数据权限范围失败"
    );
  }

  async function toggleLoginPolicy(row: IamLoginPolicyRow) {
    await runToggle(
      () => updateIamLoginPolicyStatus(row.id, { status: nextBinaryStatus(row.status) }),
      "登录策略状态已更新",
      "更新登录策略失败"
    );
  }

  async function togglePasswordPolicy(row: IamPasswordPolicyRow) {
    await runToggle(
      () => updateIamPasswordPolicyStatus(row.id, { status: nextBinaryStatus(row.status) }),
      "密码策略状态已更新",
      "更新密码策略失败"
    );
  }

  async function batchEnableDepartments(rows: IamDepartmentRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "ENABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamDepartmentStatus(id, { status }),
      label: "启用",
      successMessage: "已批量启用部门",
      fallbackMessage: "批量启用部门失败"
    });
  }

  async function batchDisableDepartments(rows: IamDepartmentRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "DISABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamDepartmentStatus(id, { status }),
      label: "停用",
      successMessage: "已批量停用部门",
      fallbackMessage: "批量停用部门失败"
    });
  }

  async function batchEnablePositions(rows: IamPositionRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "ENABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamPositionStatus(id, { status }),
      label: "启用",
      successMessage: "已批量启用岗位",
      fallbackMessage: "批量启用岗位失败"
    });
  }

  async function batchDisablePositions(rows: IamPositionRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "DISABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamPositionStatus(id, { status }),
      label: "停用",
      successMessage: "已批量停用岗位",
      fallbackMessage: "批量停用岗位失败"
    });
  }

  async function batchEnableEmployees(rows: IamEmployeeRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "ACTIVE",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.employeeStatus,
      update: (id, status) => updateIamEmployeeStatus(id, { status }),
      label: "启用",
      successMessage: "已批量启用员工",
      fallbackMessage: "批量启用员工失败"
    });
  }

  async function batchDisableEmployees(rows: IamEmployeeRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "INACTIVE",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.employeeStatus,
      update: (id, status) => updateIamEmployeeStatus(id, { status }),
      label: "停用",
      successMessage: "已批量停用员工",
      fallbackMessage: "批量停用员工失败"
    });
  }

  async function batchEnableUsers(rows: IamUserRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "ENABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamUserStatus(id, { status }),
      label: "启用",
      successMessage: "已批量启用用户",
      fallbackMessage: "批量启用用户失败"
    });
  }

  async function batchDisableUsers(rows: IamUserRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "DISABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamUserStatus(id, { status }),
      label: "停用",
      successMessage: "已批量停用用户",
      fallbackMessage: "批量停用用户失败"
    });
  }

  async function batchEnableRoles(rows: IamRoleRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "ENABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamRoleStatus(id, { status }),
      label: "启用",
      successMessage: "已批量启用角色",
      fallbackMessage: "批量启用角色失败"
    });
  }

  async function batchDisableRoles(rows: IamRoleRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "DISABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamRoleStatus(id, { status }),
      label: "停用",
      successMessage: "已批量停用角色",
      fallbackMessage: "批量停用角色失败"
    });
  }

  async function batchEnableApiResources(rows: IamApiResourceRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "ENABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamApiResourceStatus(id, { status }),
      label: "启用",
      successMessage: "已批量启用权限资源",
      fallbackMessage: "批量启用权限资源失败"
    });
  }

  async function batchDisableApiResources(rows: IamApiResourceRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "DISABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamApiResourceStatus(id, { status }),
      label: "停用",
      successMessage: "已批量停用权限资源",
      fallbackMessage: "批量停用权限资源失败"
    });
  }

  async function batchEnableMenus(rows: IamMenuRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "ENABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamMenuStatus(id, { status }),
      label: "启用",
      successMessage: "已批量启用菜单",
      fallbackMessage: "批量启用菜单失败"
    });
  }

  async function batchDisableMenus(rows: IamMenuRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "DISABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamMenuStatus(id, { status }),
      label: "停用",
      successMessage: "已批量停用菜单",
      fallbackMessage: "批量停用菜单失败"
    });
  }

  async function batchEnableDataScopes(rows: IamDataScopeRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "ENABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamDataScopeStatus(id, { status }),
      label: "启用",
      successMessage: "已批量启用数据范围",
      fallbackMessage: "批量启用数据范围失败"
    });
  }

  async function batchDisableDataScopes(rows: IamDataScopeRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "DISABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamDataScopeStatus(id, { status }),
      label: "停用",
      successMessage: "已批量停用数据范围",
      fallbackMessage: "批量停用数据范围失败"
    });
  }

  async function batchEnableLoginPolicies(rows: IamLoginPolicyRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "ENABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamLoginPolicyStatus(id, { status }),
      label: "启用",
      successMessage: "已批量启用登录策略",
      fallbackMessage: "批量启用登录策略失败"
    });
  }

  async function batchDisableLoginPolicies(rows: IamLoginPolicyRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "DISABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamLoginPolicyStatus(id, { status }),
      label: "停用",
      successMessage: "已批量停用登录策略",
      fallbackMessage: "批量停用登录策略失败"
    });
  }

  async function batchEnablePasswordPolicies(rows: IamPasswordPolicyRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "ENABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamPasswordPolicyStatus(id, { status }),
      label: "启用",
      successMessage: "已批量启用密码策略",
      fallbackMessage: "批量启用密码策略失败"
    });
  }

  async function batchDisablePasswordPolicies(rows: IamPasswordPolicyRow[]) {
    await runBatchStatusUpdate({
      rows,
      nextStatus: "DISABLED",
      getId: (row) => row.id,
      getCurrentStatus: (row) => row.status,
      update: (id, status) => updateIamPasswordPolicyStatus(id, { status }),
      label: "停用",
      successMessage: "已批量停用密码策略",
      fallbackMessage: "批量停用密码策略失败"
    });
  }

  return {
    submitting,
    departmentDialog,
    positionDialog,
    employeeDialog,
    userDialog,
    roleDialog,
    apiDialog,
    menuDialog,
    dataScopeDialog,
    loginPolicyDialog,
    passwordPolicyDialog,
    departmentForm,
    positionForm,
    employeeForm,
    userForm,
    roleForm,
    apiForm,
    menuForm,
    dataScopeForm,
    loginPolicyForm,
    passwordPolicyForm,
    openDepartmentCreate,
    openDepartmentEdit,
    openPositionCreate,
    openPositionEdit,
    openEmployeeCreate,
    openEmployeeEdit,
    openEmployeeTransfer,
    openUserCreate,
    openUserEdit,
    openRoleCreate,
    openRoleEdit,
    openApiCreate,
    openApiEdit,
    openMenuCreate,
    openMenuEdit,
    openDataScopeCreate,
    openDataScopeEdit,
    openLoginPolicyCreate,
    openLoginPolicyEdit,
    openPasswordPolicyCreate,
    openPasswordPolicyEdit,
    submitDepartment,
    submitPosition,
    submitEmployee,
    submitUser,
    submitRole,
    submitApiResource,
    submitMenu,
    submitDataScope,
    submitLoginPolicy,
    submitPasswordPolicy,
    toggleDepartment,
    togglePosition,
    toggleEmployee,
    toggleUser,
    toggleRole,
    toggleApiResource,
    toggleMenu,
    toggleDataScope,
    toggleLoginPolicy,
    togglePasswordPolicy,
    batchEnableDepartments,
    batchDisableDepartments,
    batchEnablePositions,
    batchDisablePositions,
    batchEnableEmployees,
    batchDisableEmployees,
    batchEnableUsers,
    batchDisableUsers,
    batchEnableRoles,
    batchDisableRoles,
    batchEnableApiResources,
    batchDisableApiResources,
    batchEnableMenus,
    batchDisableMenus,
    batchEnableDataScopes,
    batchDisableDataScopes,
    batchEnableLoginPolicies,
    batchDisableLoginPolicies,
    batchEnablePasswordPolicies,
    batchDisablePasswordPolicies
  };
}

function nextBinaryStatus(status: string) {
  return status === "ENABLED" ? "DISABLED" : "ENABLED";
}

function nextEmployeeStatus(status: string) {
  return status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
}
