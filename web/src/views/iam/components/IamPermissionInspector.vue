<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import {
  Check,
  Close,
  Compass,
  Cpu,
  DataAnalysis,
  Document,
  Key,
  Right,
  Search,
  User,
  Warning
} from "@element-plus/icons-vue";
import {
  fetchUserPermissionInspection,
  simulatePermission
} from "@/api/modules/iam";
import type {
  IamDepartmentRow,
  IamMenuRow,
  IamUserRow,
  PermissionSimulationResponse,
  UserPermissionInspection
} from "@/api/modules/iam";
import BaseCard from "@/components/base/BaseCard.vue";

const props = defineProps<{
  visible: boolean;
  initialUserId?: number | null;
  users: IamUserRow[];
  departments: IamDepartmentRow[];
  menus: IamMenuRow[];
  currentTenantId: number;
}>();

const emit = defineEmits<{
  (e: "update:visible", value: boolean): void;
}>();

const selectedUserId = ref<number | null>(null);
const loadingInspection = ref(false);
const inspectionData = ref<UserPermissionInspection | null>(null);

// Simulator Inputs
const testActionCode = ref<string>("iam_user:create");
const testDepartmentId = ref<number | null>(null);
const runningSimulation = ref(false);
const simulationResult = ref<PermissionSimulationResponse | null>(null);

// Sub-view toggle
const activeInspectorTab = ref<"menus" | "dataScope" | "simulator">("menus");

// Common system actions options for quick simulator test
const commonActionOptions = computed(() => {
  const buttons = props.menus.filter((m) => m.menuType === "BUTTON");
  return buttons.map((b) => ({
    label: `${b.menuName} (${b.menuCode})`,
    value: b.menuCode
  }));
});

watch(
  () => props.initialUserId,
  (val) => {
    if (val) {
      selectedUserId.value = val;
    } else if (!selectedUserId.value && props.users.length > 0) {
      selectedUserId.value = props.users[0]?.id ?? null;
    }
  },
  { immediate: true }
);

watch(
  selectedUserId,
  async (userId) => {
    if (!userId) {
      inspectionData.value = null;
      simulationResult.value = null;
      return;
    }
    await loadUserInspection(userId);
  },
  { immediate: true }
);

async function loadUserInspection(userId: number) {
  loadingInspection.value = true;
  simulationResult.value = null;
  try {
    const data = await fetchUserPermissionInspection(userId, props.currentTenantId);
    inspectionData.value = data;
    // Set default test target dept
    if (data.departmentId) {
      testDepartmentId.value = data.departmentId;
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "加载用户权限诊断透视失败");
  } finally {
    loadingInspection.value = false;
  }
}

async function runSimulator() {
  if (!selectedUserId.value) {
    ElMessage.warning("请先选择要诊断的用户");
    return;
  }
  runningSimulation.value = true;
  try {
    const res = await simulatePermission(
      {
        userId: selectedUserId.value,
        actionCode: testActionCode.value,
        targetDepartmentId: testDepartmentId.value || undefined
      },
      props.currentTenantId
    );
    simulationResult.value = res;
    activeInspectorTab.value = "simulator";
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "权限模拟执行失败");
  } finally {
    runningSimulation.value = false;
  }
}

const currentUserObj = computed(() => {
  return props.users.find((u) => u.id === selectedUserId.value) ?? null;
});

// Group visible menus by parent
const inspectedMenuGroups = computed(() => {
  if (!inspectionData.value) return [];
  const menus = inspectionData.value.menus;
  const roots = menus.filter((m) => m.parentId === 0 || !m.parentId);
  return roots.map((root) => {
    const children = menus.filter((m) => m.parentId === root.id);
    return {
      id: root.id,
      name: root.menuName,
      children: children.length > 0 ? children : [root]
    };
  });
});
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="成员权限透视与诊断器 (Permission Inspector & Simulator)"
    width="860px"
    class="iam-inspector-dialog"
    destroy-on-close
    @update:model-value="(val) => emit('update:visible', val)"
  >
    <!-- Top User Selector Bar -->
    <div class="inspector-top-bar">
      <div class="user-select-row">
        <label class="select-label">选择诊断目标成员：</label>
        <el-select
          v-model="selectedUserId"
          filterable
          placeholder="按姓名或工号搜索成员..."
          class="user-dropdown"
        >
          <el-option
            v-for="u in users"
            :key="u.id"
            :label="`${u.nickname} (@${u.username})`"
            :value="u.id"
          />
        </el-select>
        <el-button
          type="primary"
          :icon="Cpu"
          :loading="runningSimulation"
          @click="runSimulator"
        >
          运行实时诊断模拟
        </el-button>
      </div>

      <!-- Identity Card -->
      <div v-if="inspectionData" class="user-profile-banner">
        <div class="user-avatar-circle">
          {{ inspectionData.nickname?.charAt(0) || inspectionData.username?.charAt(0) }}
        </div>
        <div class="user-main-info">
          <div class="name-line">
            <span class="user-name">{{ inspectionData.nickname }}</span>
            <span class="user-handle">@{{ inspectionData.username }}</span>
            <el-tag size="small" type="success" effect="plain">账号正常</el-tag>
          </div>
          <div class="org-line">
            <span>组织架构：<strong>{{ inspectionData.departmentName }}</strong></span>
            <span class="divider">/</span>
            <span>职位：<strong>{{ inspectionData.positionName }}</strong></span>
            <span class="divider">/</span>
            <span>员工姓名：<strong>{{ inspectionData.employeeName }}</strong></span>
          </div>
        </div>
        <div class="roles-pill-column">
          <span class="pill-label">生效角色 ({{ inspectionData.roles.length }}):</span>
          <div class="roles-chips">
            <el-tag
              v-for="r in inspectionData.roles"
              :key="r.id"
              size="small"
              type="primary"
              effect="light"
            >
              {{ r.roleName }}
            </el-tag>
            <span v-if="inspectionData.roles.length === 0" class="no-role-text">未绑定任何有效角色</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Main Diagnostic Content -->
    <div v-loading="loadingInspection" class="inspector-body">
      <div class="tabs-control-header">
        <el-radio-group v-model="activeInspectorTab" size="default" class="diagnostic-nav-tabs">
          <el-radio-button value="menus">
            <el-icon><Document /></el-icon>
            <span>有效页面与操作 ({{ inspectionData?.menus.length ?? 0 }})</span>
          </el-radio-button>
          <el-radio-button value="dataScope">
            <el-icon><DataAnalysis /></el-icon>
            <span>行级数据边界 ({{ inspectionData?.dataScopeRules.length ?? 0 }})</span>
          </el-radio-button>
          <el-radio-button value="simulator">
            <el-icon><Cpu /></el-icon>
            <span>权限实时模拟诊断</span>
          </el-radio-button>
        </el-radio-group>
      </div>

      <!-- TAB 1: 有效页面与操作 -->
      <div v-show="activeInspectorTab === 'menus'" class="tab-content-panel">
        <div v-if="!inspectionData || inspectionData.menus.length === 0" class="empty-box">
          <el-empty description="该成员暂无任何可访问的页面权限" :image-size="60" />
        </div>
        <div v-else class="menu-atlas-grid">
          <div v-for="group in inspectedMenuGroups" :key="group.id" class="module-group-box">
            <div class="module-group-title">{{ group.name }}</div>
            <div class="page-chips-flow">
              <div v-for="page in group.children" :key="page.id" class="page-chip">
                <span class="chip-name">{{ page.menuName }}</span>
                <span class="chip-code">{{ page.menuCode }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="inspectionData && inspectionData.buttonPermissions.length > 0" class="button-permissions-section">
          <div class="section-sub-title">已授予的关键操作代码 ({{ inspectionData.buttonPermissions.length }} 项)：</div>
          <div class="btn-tags-cloud">
            <span v-for="btn in inspectionData.buttonPermissions" :key="btn" class="act-tag">
              {{ btn }}
            </span>
          </div>
        </div>
      </div>

      <!-- TAB 2: 行级数据边界 -->
      <div v-show="activeInspectorTab === 'dataScope'" class="tab-content-panel">
        <div v-if="!inspectionData || inspectionData.dataScopeRules.length === 0" class="empty-box">
          <div class="scope-all-banner">
            <el-icon class="banner-icon"><Check /></el-icon>
            <div>
              <strong>当前用户处于全域数据模式 (ALL)</strong>
              <p>系统未检索到该用户的显式行级隔离规则，默认拥有租户内全部组织和员工业务数据的查阅权限。</p>
            </div>
          </div>
        </div>
        <div v-else class="scope-rules-list">
          <div v-for="rule in inspectionData.dataScopeRules" :key="rule.id" class="scope-rule-item">
            <div class="rule-top">
              <span class="rule-title">{{ rule.resourceName }} ({{ rule.resourceCode }})</span>
              <el-tag size="small" type="warning">{{ rule.scopeType }}</el-tag>
            </div>
            <div class="rule-desc">
              授权主体：{{ rule.subjectType }}:{{ rule.subjectValue }} · {{ rule.configSummary }}
            </div>
            <div v-if="rule.resolvedDepartmentIds.length > 0" class="depts-resolved-box">
              <span class="dept-box-label">动态穿透生效的部门集合：</span>
              <div class="dept-tags">
                <el-tag
                  v-for="dId in rule.resolvedDepartmentIds"
                  :key="dId"
                  size="small"
                  type="info"
                >
                  {{ departments.find(d => d.id === dId)?.deptName ?? `部门 #${dId}` }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- TAB 3: 实时决策模拟器 -->
      <div v-show="activeInspectorTab === 'simulator'" class="tab-content-panel">
        <div class="simulator-config-box">
          <div class="simulator-form-grid">
            <div class="form-item">
              <label>待测试功能或操作 (Action Code)：</label>
              <el-select
                v-model="testActionCode"
                filterable
                allow-create
                default-first-option
                placeholder="选择或输入操作代码，如 iam_user:create..."
                style="width: 100%"
              >
                <el-option
                  v-for="opt in commonActionOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </div>

            <div class="form-item">
              <label>待访问数据归属部门 (Target Department)：</label>
              <el-select
                v-model="testDepartmentId"
                filterable
                placeholder="选择待查阅数据的所属部门..."
                style="width: 100%"
              >
                <el-option
                  v-for="dept in departments"
                  :key="dept.id"
                  :label="`${dept.deptName} (#${dept.id})`"
                  :value="dept.id"
                />
              </el-select>
            </div>
          </div>

          <div class="form-actions">
            <el-button
              type="primary"
              :icon="Cpu"
              :loading="runningSimulation"
              @click="runSimulator"
            >
              执行实时模拟诊断
            </el-button>
          </div>
        </div>

        <!-- Simulation Result Card -->
        <div v-if="simulationResult" class="simulation-result-card" :class="{ 'pass-border': simulationResult.overallGranted, 'deny-border': !simulationResult.overallGranted }">
          <div class="verdict-banner" :class="{ 'pass-bg': simulationResult.overallGranted, 'deny-bg': !simulationResult.overallGranted }">
            <div class="verdict-status">
              <el-icon :size="24">
                <Check v-if="simulationResult.overallGranted" />
                <Close v-else />
              </el-icon>
              <span class="verdict-text">{{ simulationResult.overallGranted ? "允许通行 (PASS)" : "访问拦截 (DENY)" }}</span>
            </div>
            <div class="verdict-badges">
              <el-tag :type="simulationResult.actionGranted ? 'success' : 'danger'" effect="dark">
                功能操作权限: {{ simulationResult.actionGranted ? "通过" : "拒绝" }}
              </el-tag>
              <el-tag :type="simulationResult.dataScopeGranted ? 'success' : 'danger'" effect="dark">
                行级数据范围: {{ simulationResult.dataScopeGranted ? "通过" : "拒绝" }}
              </el-tag>
            </div>
          </div>

          <!-- Trace log -->
          <div class="trace-section">
            <div class="trace-title">决策追踪日志 (Decision Trace Log)：</div>
            <div class="trace-steps">
              <div v-for="(step, idx) in simulationResult.decisionTrace" :key="idx" class="trace-line">
                {{ step }}
              </div>
            </div>
          </div>

          <!-- SQL Spec -->
          <div class="sql-spec-section">
            <div class="sql-title">底层动态 SQL 改写条件 (MyBatis-Plus AST)：</div>
            <div class="sql-code">{{ simulationResult.simulatedSqlCondition }}</div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="emit('update:visible', false)">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.iam-inspector-dialog {
  :deep(.el-dialog__header) {
    margin-right: 0;
    padding: 20px 24px 16px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  :deep(.el-dialog__body) {
    padding: 20px 24px;
    background: #f8fafc;
  }

  :deep(.el-dialog__footer) {
    padding: 14px 24px;
    border-top: 1px solid var(--el-border-color-lighter);
    background: #ffffff;
  }
}

.inspector-top-bar {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 16px;

  .user-select-row {
    display: flex;
    align-items: center;
    gap: 12px;

    .select-label {
      font-size: 13px;
      font-weight: 600;
      color: #334155;
      white-space: nowrap;
    }

    .user-dropdown {
      flex: 1;
    }
  }

  .user-profile-banner {
    display: flex;
    align-items: center;
    gap: 16px;
    background: #ffffff;
    border: 1px solid #e2e8f0;
    border-radius: 10px;
    padding: 14px 18px;

    .user-avatar-circle {
      width: 44px;
      height: 44px;
      border-radius: 50%;
      background: #eff6ff;
      color: #2563eb;
      font-weight: 700;
      font-size: 16px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .user-main-info {
      flex: 1;

      .name-line {
        display: flex;
        align-items: center;
        gap: 8px;

        .user-name {
          font-size: 15px;
          font-weight: 600;
          color: #0f172a;
        }

        .user-handle {
          font-size: 13px;
          color: #64748b;
        }
      }

      .org-line {
        font-size: 12px;
        color: #64748b;
        margin-top: 4px;
        display: flex;
        gap: 6px;

        strong {
          color: #334155;
        }

        .divider {
          color: #cbd5e1;
        }
      }
    }

    .roles-pill-column {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 4px;

      .pill-label {
        font-size: 11px;
        color: #94a3b8;
      }

      .roles-chips {
        display: flex;
        flex-wrap: wrap;
        gap: 4px;
        justify-content: flex-end;

        .no-role-text {
          font-size: 12px;
          color: #94a3b8;
        }
      }
    }
  }
}

.tabs-control-header {
  margin-bottom: 14px;

  .diagnostic-nav-tabs {
    width: 100%;
    display: flex;

    :deep(.el-radio-button) {
      flex: 1;

      .el-radio-button__inner {
        width: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 6px;
        padding: 9px 16px;
        font-weight: 500;
      }
    }
  }
}

.tab-content-panel {
  min-height: 280px;
}

/* Menu Atlas */
.menu-atlas-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.module-group-box {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 16px;

  .module-group-title {
    font-size: 13px;
    font-weight: 600;
    color: #1e293b;
    margin-bottom: 8px;
  }

  .page-chips-flow {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;

    .page-chip {
      display: inline-flex;
      align-items: baseline;
      gap: 4px;
      padding: 4px 10px;
      border-radius: 6px;
      background: #eff6ff;
      border: 1px solid #bfdbfe;

      .chip-name {
        font-size: 12px;
        font-weight: 500;
        color: #1e40af;
      }

      .chip-code {
        font-size: 10px;
        color: #3b82f6;
        font-family: monospace;
      }
    }
  }
}

.button-permissions-section {
  margin-top: 16px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 14px 16px;

  .section-sub-title {
    font-size: 13px;
    font-weight: 600;
    color: #334155;
    margin-bottom: 10px;
  }

  .btn-tags-cloud {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;

    .act-tag {
      font-size: 11px;
      font-family: monospace;
      padding: 3px 8px;
      border-radius: 4px;
      background: #f1f5f9;
      color: #475569;
      border: 1px solid #e2e8f0;
    }
  }
}

/* Data Scope Tab */
.scope-all-banner {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-radius: 8px;

  .banner-icon {
    font-size: 20px;
    color: #059669;
    margin-top: 2px;
  }

  strong {
    display: block;
    font-size: 14px;
    color: #065f46;
    margin-bottom: 4px;
  }

  p {
    margin: 0;
    font-size: 12px;
    color: #047857;
    line-height: 1.5;
  }
}

.scope-rules-list {
  display: flex;
  flex-direction: column;
  gap: 10px;

  .scope-rule-item {
    background: #ffffff;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    padding: 12px 16px;

    .rule-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 4px;

      .rule-title {
        font-size: 13px;
        font-weight: 600;
        color: #1e293b;
      }
    }

    .rule-desc {
      font-size: 12px;
      color: #64748b;
    }

    .depts-resolved-box {
      margin-top: 8px;
      padding-top: 8px;
      border-top: 1px dashed #f1f5f9;

      .dept-box-label {
        font-size: 11px;
        color: #94a3b8;
        display: block;
        margin-bottom: 4px;
      }

      .dept-tags {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
      }
    }
  }
}

/* Simulator Tab */
.simulator-config-box {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 16px 20px;
  margin-bottom: 16px;

  .simulator-form-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;

    .form-item {
      display: flex;
      flex-direction: column;
      gap: 6px;

      label {
        font-size: 12px;
        font-weight: 600;
        color: #334155;
      }
    }
  }

  .form-actions {
    display: flex;
    justify-content: flex-end;
    margin-top: 14px;
  }
}

.simulation-result-card {
  background: #ffffff;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #e2e8f0;

  &.pass-border {
    border-color: #86efac;
  }

  &.deny-border {
    border-color: #fca5a5;
  }

  .verdict-banner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px 20px;

    &.pass-bg {
      background: #f0fdf4;
      color: #15803d;
    }

    &.deny-bg {
      background: #fef2f2;
      color: #b91c1c;
    }

    .verdict-status {
      display: flex;
      align-items: center;
      gap: 10px;

      .verdict-text {
        font-size: 16px;
        font-weight: 700;
      }
    }

    .verdict-badges {
      display: flex;
      gap: 8px;
    }
  }

  .trace-section {
    padding: 16px 20px;
    border-bottom: 1px solid #f1f5f9;

    .trace-title {
      font-size: 13px;
      font-weight: 600;
      color: #334155;
      margin-bottom: 10px;
    }

    .trace-steps {
      display: flex;
      flex-direction: column;
      gap: 6px;

      .trace-line {
        font-size: 12px;
        color: #475569;
        line-height: 1.5;
        padding-left: 8px;
        border-left: 2px solid #e2e8f0;
      }
    }
  }

  .sql-spec-section {
    padding: 14px 20px;
    background: #f8fafc;

    .sql-title {
      font-size: 12px;
      font-weight: 600;
      color: #64748b;
      margin-bottom: 6px;
    }

    .sql-code {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-size: 12px;
      color: #2563eb;
      background: #ffffff;
      padding: 8px 12px;
      border-radius: 6px;
      border: 1px solid #e2e8f0;
      overflow-x: auto;
    }
  }
}
</style>
