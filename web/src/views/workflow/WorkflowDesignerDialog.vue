<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { fetchIamRoles, fetchIamUsers } from "@/api/modules/iam/service";
import type { IamRoleRow, IamUserRow } from "@/api/modules/iam/types";
import { createWorkflowDefinition, updateWorkflowDefinition } from "@/api/modules/workflow";
import type {
  ConditionBranch,
  FlowNodeConfig,
  FormFieldConfig,
  ProcessDefinitionRow,
  ProcessDefinitionSavePayload
} from "@/api/modules/workflow";

const props = defineProps<{
  visible: boolean;
  definition: ProcessDefinitionRow | null;
}>();

const emit = defineEmits<{
  "update:visible": [boolean];
  saved: [];
}>();

const activeTab = ref<"flow" | "form" | "base">("flow");
const saving = ref(false);

// System roles and users
const systemRoles = ref<IamRoleRow[]>([]);
const systemUsers = ref<IamUserRow[]>([]);

// Base form
const baseForm = reactive({
  id: undefined as number | undefined,
  processCode: "",
  processName: "",
  category: "GENERAL",
  icon: "Document",
  description: "",
  formName: ""
});

// Form fields state
const formFields = ref<FormFieldConfig[]>([]);
const selectedFieldIndex = ref<number>(-1);

// Node tree state
const rootNode = ref<FlowNodeConfig>({
  id: "root",
  name: "发起人",
  type: "START",
  next: {
    id: "end",
    name: "流程办结",
    type: "END"
  }
});

// Node configuration drawer
const drawerVisible = ref(false);
const activeEditingNode = ref<FlowNodeConfig | null>(null);
const activeEditingBranch = ref<{ branch: ConditionBranch; parentNode: FlowNodeConfig } | null>(null);

onMounted(async () => {
  try {
    const [roles, users] = await Promise.all([
      fetchIamRoles().catch(() => []),
      fetchIamUsers().catch(() => [])
    ]);
    systemRoles.value = roles;
    systemUsers.value = users;
  } catch {
    // Graceful fallback
  }
});

watch(
  () => props.definition,
  (def) => {
    if (!def) {
      baseForm.id = undefined;
      baseForm.processCode = `WF_${Date.now().toString(36).toUpperCase()}`;
      baseForm.processName = "新建业务审批流";
      baseForm.category = "GENERAL";
      baseForm.icon = "Document";
      baseForm.description = "适用于常规事项的快速发起与流转审批";
      baseForm.formName = "通用申请单";

      formFields.value = [
        { fieldCode: "title", fieldName: "申请事由", fieldType: "TEXT", required: true, placeholder: "请输入申请事由" },
        { fieldCode: "content", fieldName: "详细说明", fieldType: "TEXTAREA", required: false, placeholder: "补充背景与说明" }
      ];

      rootNode.value = {
        id: "root",
        name: "发起人",
        type: "START",
        next: {
          id: `approver_${Date.now()}`,
          name: "部门主管审批",
          type: "APPROVER",
          assigneeType: "DEPT_LEADER",
          assigneeValue: "",
          approvalMode: "ANY",
          next: {
            id: "end",
            name: "流程办结",
            type: "END"
          }
        }
      };
      return;
    }

    baseForm.id = def.id;
    baseForm.processCode = def.processCode;
    baseForm.processName = def.processName;
    baseForm.category = def.category;
    baseForm.icon = def.icon || "Document";
    baseForm.description = def.description || "";
    baseForm.formName = def.processName;

    // Load form fields
    if (def.formSchemaJson) {
      try {
        const parsed = JSON.parse(def.formSchemaJson);
        formFields.value = Array.isArray(parsed) ? parsed : [];
      } catch {
        formFields.value = [];
      }
    } else {
      formFields.value = [];
    }

    // Load node tree
    if (def.flowNodesJson) {
      try {
        const parsed = JSON.parse(def.flowNodesJson);
        rootNode.value = parsed;
      } catch {
        rootNode.value = {
          id: "root",
          name: "发起人",
          type: "START",
          next: { id: "end", name: "流程办结", type: "END" }
        };
      }
    }
  },
  { immediate: true }
);

// Flatten nodes for linear canvas display
interface FlatStep {
  node: FlowNodeConfig;
  parent: FlowNodeConfig | null;
  branchIndex?: number;
}

const flatFlowList = computed(() => {
  const steps: FlatStep[] = [];
  let curr: FlowNodeConfig | undefined = rootNode.value;
  let prev: FlowNodeConfig | null = null;

  while (curr) {
    steps.push({ node: curr, parent: prev });
    if (curr.type === "CONDITION_BRANCH" || curr.type === "END") {
      break;
    }
    prev = curr;
    curr = curr.next;
  }
  return steps;
});

// Node actions
function openNodeConfig(node: FlowNodeConfig) {
  if (node.type === "START" || node.type === "END") return;
  activeEditingBranch.value = null;
  activeEditingNode.value = node;
  drawerVisible.value = true;
}

function openBranchConfig(branch: ConditionBranch, parentNode: FlowNodeConfig) {
  activeEditingNode.value = null;
  activeEditingBranch.value = { branch, parentNode };
  drawerVisible.value = true;
}

function addNodeAfter(targetNode: FlowNodeConfig, nodeType: "APPROVER" | "CONDITION_BRANCH" | "CC") {
  const oldNext = targetNode.next;
  const newId = `node_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;

  if (nodeType === "APPROVER") {
    targetNode.next = {
      id: newId,
      name: "指定审批人",
      type: "APPROVER",
      assigneeType: "DEPT_LEADER",
      assigneeValue: "",
      approvalMode: "ANY",
      next: oldNext
    };
  } else if (nodeType === "CC") {
    targetNode.next = {
      id: newId,
      name: "抄送节点",
      type: "CC",
      assigneeType: "ROLE",
      assigneeValue: systemRoles.value[0]?.id ? String(systemRoles.value[0].id) : "",
      next: oldNext
    };
  } else if (nodeType === "CONDITION_BRANCH") {
    const branch1Id = `branch_${Date.now()}_1`;

    targetNode.next = {
      id: newId,
      name: "条件分流",
      type: "CONDITION_BRANCH",
      branches: [
        {
          name: "分支条件 1",
          condition: {
            field: formFields.value[0]?.fieldCode || "days",
            operator: ">",
            value: 3
          },
          next: {
            id: branch1Id,
            name: "分支审批",
            type: "APPROVER",
            assigneeType: "ROLE",
            assigneeValue: systemRoles.value[0]?.id ? String(systemRoles.value[0].id) : "",
            next: oldNext
          }
        },
        {
          name: "其他默认情况",
          condition: undefined,
          next: oldNext
        }
      ]
    };
  }
}

function deleteNode(parent: FlowNodeConfig | null, target: FlowNodeConfig) {
  if (!parent || target.type === "START" || target.type === "END") return;
  parent.next = target.next;
}

function addBranchToNode(branchNode: FlowNodeConfig) {
  if (!branchNode.branches) branchNode.branches = [];
  const idx = branchNode.branches.length + 1;
  branchNode.branches.push({
    name: `分支条件 ${idx}`,
    condition: {
      field: formFields.value[0]?.fieldCode || "amount",
      operator: ">=",
      value: 1000
    },
    next: {
      id: `branch_node_${Date.now()}`,
      name: "加签审批人",
      type: "APPROVER",
      assigneeType: "DEPT_LEADER",
      assigneeValue: "",
      next: { id: "end", name: "流程办结", type: "END" }
    }
  });
}

function removeBranch(branchNode: FlowNodeConfig, index: number) {
  if (!branchNode.branches || branchNode.branches.length <= 1) {
    ElMessage.warning("至少保留一条分支");
    return;
  }
  branchNode.branches.splice(index, 1);
}

// Form Field Builder Actions
function addFormField() {
  const count = formFields.value.length + 1;
  formFields.value.push({
    fieldCode: `field_${count}`,
    fieldName: `自定义字段 ${count}`,
    fieldType: "TEXT",
    required: false,
    placeholder: "请输入内容"
  });
  selectedFieldIndex.value = formFields.value.length - 1;
}

function removeFormField(index: number) {
  formFields.value.splice(index, 1);
  if (selectedFieldIndex.value >= formFields.value.length) {
    selectedFieldIndex.value = formFields.value.length - 1;
  }
}

// Helper display names
function getAssigneeDescription(node: FlowNodeConfig) {
  if (node.assigneeType === "DEPT_LEADER") {
    return "发起人直属部门主管";
  }
  if (node.assigneeType === "ROLE") {
    const role = systemRoles.value.find((r) => String(r.id) === String(node.assigneeValue));
    return role ? `角色: ${role.roleName}` : `指定角色 ID: ${node.assigneeValue || "未指定"}`;
  }
  if (node.assigneeType === "USER") {
    const user = systemUsers.value.find((u) => String(u.id) === String(node.assigneeValue));
    return user ? `用户: ${user.nickname}` : `指定成员 ID: ${node.assigneeValue || "未指定"}`;
  }
  return "直属负责人";
}

// Save Process
async function handleSave() {
  if (!baseForm.processName.trim()) {
    ElMessage.warning("请填写流程名称");
    activeTab.value = "base";
    return;
  }
  if (!baseForm.processCode.trim()) {
    ElMessage.warning("请填写流程编码");
    activeTab.value = "base";
    return;
  }

  saving.value = true;
  try {
    const payload: ProcessDefinitionSavePayload = {
      processCode: baseForm.processCode.trim(),
      processName: baseForm.processName.trim(),
      category: baseForm.category,
      icon: baseForm.icon,
      description: baseForm.description,
      formSchemaJson: JSON.stringify(formFields.value),
      flowNodesJson: JSON.stringify(rootNode.value),
      status: "PUBLISHED"
    };

    if (baseForm.id) {
      await updateWorkflowDefinition(baseForm.id, payload);
      ElMessage.success("流程定义已成功更新并发布");
    } else {
      await createWorkflowDefinition(payload);
      ElMessage.success("新建流程定义已成功发布");
    }
    emit("saved");
    emit("update:visible", false);
  } catch (err: any) {
    ElMessage.error(err?.message || "保存流程定义失败");
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    width="1080px"
    top="3vh"
    destroy-on-close
    class="workflow-designer-modal"
    @update:model-value="emit('update:visible', $event)"
  >
    <template #header>
      <div class="designer-modal-header">
        <div class="header-left">
          <div class="flow-badge">
            <el-icon><i class="el-icon-connection"></i></el-icon>
            可视化工作流引擎
          </div>
          <span class="title">{{ baseForm.processName || "设计流程与表单" }}</span>
          <el-tag size="small" type="info">{{ baseForm.processCode }}</el-tag>
        </div>
        <div class="header-center">
          <el-radio-group v-model="activeTab" size="small">
            <el-radio-button value="flow">流程流转画布</el-radio-button>
            <el-radio-button value="form">申请表单配置 ({{ formFields.length }})</el-radio-button>
            <el-radio-button value="base">基础属性</el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </template>

    <div class="designer-container">
      <!-- 1. 流程节点画布 -->
      <div v-show="activeTab === 'flow'" class="flow-canvas-wrapper">
        <div class="canvas-tips">
          <span class="tip-dot"></span>
          支持发起人、多级审批人、条件分支与抄送节点。所有业务流无需代码，点击卡片可即时配置。
        </div>

        <div class="flow-canvas">
          <div v-for="(step, idx) in flatFlowList" :key="step.node.id || idx" class="node-wrapper">
            <!-- 普通节点渲染 (START, APPROVER, CC, END) -->
            <div
              v-if="step.node.type !== 'CONDITION_BRANCH'"
              class="node-card"
              :class="[step.node.type.toLowerCase(), { clickable: step.node.type === 'APPROVER' || step.node.type === 'CC' }]"
              @click="openNodeConfig(step.node)"
            >
              <div class="card-header">
                <span class="node-tag">
                  {{
                    step.node.type === "START"
                      ? "发起人"
                      : step.node.type === "APPROVER"
                        ? "审批人"
                        : step.node.type === "CC"
                          ? "抄送人"
                          : "办结"
                  }}
                </span>
                <span class="node-name">{{ step.node.name }}</span>
                <el-button
                  v-if="step.node.type === 'APPROVER' || step.node.type === 'CC'"
                  type="danger"
                  link
                  size="small"
                  class="del-btn"
                  @click.stop="deleteNode(step.parent, step.node)"
                >
                  删除
                </el-button>
              </div>
              <div class="card-body">
                <template v-if="step.node.type === 'START'">
                  <div class="desc-line">所有已启用员工均可发起申请</div>
                </template>
                <template v-else-if="step.node.type === 'APPROVER'">
                  <div class="desc-line"><strong>审批人：</strong>{{ getAssigneeDescription(step.node) }}</div>
                  <div class="desc-line">
                    <strong>规则：</strong>{{ step.node.approvalMode === "ALL" ? "会签 (需所有人同意)" : "或签 (一人同意即可)" }}
                  </div>
                </template>
                <template v-else-if="step.node.type === 'CC'">
                  <div class="desc-line"><strong>抄送对象：</strong>{{ getAssigneeDescription(step.node) }}</div>
                </template>
                <template v-else-if="step.node.type === 'END'">
                  <div class="desc-line end-text">🎉 流程流转完毕，生成归档台账并发送通知</div>
                </template>
              </div>
            </div>

            <!-- 条件分支容器渲染 -->
            <div v-else class="branch-container">
              <div class="branch-bar">
                <span class="branch-title">⚡ 条件智能分流 ({{ step.node.branches?.length || 0 }} 个分支)</span>
                <el-button size="small" type="primary" plain @click="addBranchToNode(step.node)">
                  + 添加分支
                </el-button>
              </div>

              <div class="branches-track">
                <div
                  v-for="(branch, bIdx) in step.node.branches || []"
                  :key="bIdx"
                  class="branch-column"
                >
                  <div class="branch-header" @click="openBranchConfig(branch, step.node)">
                    <div class="branch-name">{{ branch.name }}</div>
                    <div class="branch-rule">
                      <span v-if="branch.condition">
                        {{ branch.condition.field }} {{ branch.condition.operator }} {{ String(branch.condition.value) }}
                      </span>
                      <span v-else class="default-badge">兜底默认流</span>
                    </div>
                    <el-button
                      type="danger"
                      link
                      size="small"
                      class="branch-del"
                      @click.stop="removeBranch(step.node, bIdx)"
                    >
                      ×
                    </el-button>
                  </div>

                  <!-- 分支内部首个节点 -->
                  <div v-if="branch.next" class="branch-subnode">
                    <div
                      class="node-card branch-card"
                      :class="branch.next.type.toLowerCase()"
                      @click="openNodeConfig(branch.next)"
                    >
                      <div class="card-header">
                        <span class="node-tag">分支审批</span>
                        <span class="node-name">{{ branch.next.name }}</span>
                      </div>
                      <div class="card-body">
                        <div class="desc-line">{{ getAssigneeDescription(branch.next) }}</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 节点连接线与添加按钮 -->
            <div v-if="step.node.type !== 'END'" class="add-node-bar">
              <div class="line"></div>
              <el-popover placement="right" trigger="hover" :width="180">
                <template #reference>
                  <button class="add-btn" type="button">+</button>
                </template>
                <div class="add-menu">
                  <div class="add-item" @click="addNodeAfter(step.node, 'APPROVER')">
                    <span class="icon approver">👤</span> 审批人
                  </div>
                  <div class="add-item" @click="addNodeAfter(step.node, 'CONDITION_BRANCH')">
                    <span class="icon branch">🔀</span> 条件分支
                  </div>
                  <div class="add-item" @click="addNodeAfter(step.node, 'CC')">
                    <span class="icon cc">✉️</span> 抄送人
                  </div>
                </div>
              </el-popover>
              <div class="line"></div>
            </div>
          </div>
        </div>
      </div>

      <!-- 2. 表单字段设计器 -->
      <div v-show="activeTab === 'form'" class="form-designer-wrapper">
        <div class="designer-left-pane">
          <div class="pane-header">
            <span>表单字段列表 ({{ formFields.length }})</span>
            <el-button type="primary" size="small" @click="addFormField">+ 添加字段</el-button>
          </div>
          <div class="field-list">
            <div
              v-for="(f, fIdx) in formFields"
              :key="f.fieldCode || fIdx"
              class="field-item"
              :class="{ active: selectedFieldIndex === fIdx }"
              @click="selectedFieldIndex = fIdx"
            >
              <div class="field-meta">
                <span class="field-label">{{ f.fieldName }}</span>
                <span class="field-type-tag">{{ f.fieldType }}</span>
              </div>
              <el-button type="danger" link size="small" @click.stop="removeFormField(fIdx)">
                删除
              </el-button>
            </div>
          </div>

          <!-- 选中字段属性编辑 -->
          <div v-if="selectedFieldIndex >= 0 && formFields[selectedFieldIndex]" class="field-editor-box">
            <div class="editor-title">⚙️ 字段属性设置</div>
            <el-form label-position="top" size="small">
              <el-form-item label="显示标题 (Label)">
                <el-input v-model="formFields[selectedFieldIndex].fieldName" placeholder="如：请假天数、报销金额" />
              </el-form-item>
              <el-form-item label="字段标识 (Key)">
                <el-input v-model="formFields[selectedFieldIndex].fieldCode" placeholder="如：days, amount" />
              </el-form-item>
              <el-form-item label="控件类型">
                <el-select v-model="formFields[selectedFieldIndex].fieldType" style="width: 100%">
                  <el-option label="单行文本 (TEXT)" value="TEXT" />
                  <el-option label="数字金额/数值 (NUMBER)" value="NUMBER" />
                  <el-option label="多行文本 (TEXTAREA)" value="TEXTAREA" />
                  <el-option label="下拉单选 (SELECT)" value="SELECT" />
                  <el-option label="日期时间 (DATE)" value="DATE" />
                </el-select>
              </el-form-item>
              <el-form-item label="是否必填">
                <el-switch v-model="formFields[selectedFieldIndex].required" />
              </el-form-item>
              <el-form-item label="占位提示 (Placeholder)">
                <el-input v-model="formFields[selectedFieldIndex].placeholder" placeholder="请输入提示信息" />
              </el-form-item>
            </el-form>
          </div>
        </div>

        <div class="designer-right-pane">
          <div class="preview-title">📱 业务端发起表单实时预览</div>
          <div class="form-preview-card">
            <div class="preview-header">
              <h3>{{ baseForm.processName || "申请单" }}</h3>
              <p class="sub-text">{{ baseForm.description || "请按规范填写以下申请项" }}</p>
            </div>
            <el-form label-position="top">
              <el-form-item label="申请单标题 *" required>
                <el-input disabled :placeholder="'申请: ' + baseForm.processName" />
              </el-form-item>
              <el-form-item
                v-for="f in formFields"
                :key="f.fieldCode"
                :label="f.fieldName + (f.required ? ' *' : '')"
                :required="f.required"
              >
                <el-input
                  v-if="f.fieldType === 'TEXT'"
                  disabled
                  :placeholder="f.placeholder || '请输入' + f.fieldName"
                />
                <el-input-number
                  v-else-if="f.fieldType === 'NUMBER'"
                  disabled
                  style="width: 100%"
                  :placeholder="f.placeholder || '0'"
                />
                <el-input
                  v-else-if="f.fieldType === 'TEXTAREA'"
                  disabled
                  type="textarea"
                  :rows="3"
                  :placeholder="f.placeholder || '请输入详细内容'"
                />
                <el-select
                  v-else-if="f.fieldType === 'SELECT'"
                  disabled
                  style="width: 100%"
                  :placeholder="f.placeholder || '请选择'"
                />
                <el-date-picker
                  v-else-if="f.fieldType === 'DATE'"
                  disabled
                  type="date"
                  style="width: 100%"
                  :placeholder="f.placeholder || '请选择日期时间'"
                />
              </el-form-item>
            </el-form>
          </div>
        </div>
      </div>

      <!-- 3. 基础信息配置 -->
      <div v-show="activeTab === 'base'" class="base-config-wrapper">
        <el-form label-position="left" label-width="120px" class="base-form">
          <el-form-item label="流程编码 *" required>
            <el-input v-model="baseForm.processCode" placeholder="如：LEAVE_REQUEST, EXPENSE_REIMBURSE" />
            <div class="help-text">租户内全局唯一标识，用于系统路由与业务单据关联</div>
          </el-form-item>
          <el-form-item label="流程名称 *" required>
            <el-input v-model="baseForm.processName" placeholder="如：员工请假申请、项目经费报销" />
          </el-form-item>
          <el-form-item label="所属业务分类">
            <el-select v-model="baseForm.category" style="width: 100%">
              <el-option label="人力人事 (HR)" value="HR" />
              <el-option label="财务报销 (FINANCE)" value="FINANCE" />
              <el-option label="行政综合 (GENERAL)" value="GENERAL" />
              <el-option label="IT与数据权限 (IT)" value="IT" />
              <el-option label="法务合同 (LEGAL)" value="LEGAL" />
            </el-select>
          </el-form-item>
          <el-form-item label="流程图标">
            <el-select v-model="baseForm.icon" style="width: 100%">
              <el-option label="📅 日历请假" value="Calendar" />
              <el-option label="💰 经费财务" value="Money" />
              <el-option label="📄 综合文件" value="Document" />
              <el-option label="🔑 权限密钥" value="Key" />
            </el-select>
          </el-form-item>
          <el-form-item label="申请单名称">
            <el-input v-model="baseForm.formName" placeholder="前端提交时的表单名" />
          </el-form-item>
          <el-form-item label="流程说明">
            <el-input
              v-model="baseForm.description"
              type="textarea"
              :rows="4"
              placeholder="请输入流程适用范围与发起须知"
            />
          </el-form-item>
        </el-form>
      </div>
    </div>

    <!-- 节点侧边抽屉配置 -->
    <el-drawer
      v-model="drawerVisible"
      :title="activeEditingNode ? '节点属性设置' : '分支判定条件设置'"
      size="380px"
      append-to-body
    >
      <div v-if="activeEditingNode" class="node-drawer-content">
        <el-form label-position="top">
          <el-form-item label="节点名称">
            <el-input v-model="activeEditingNode.name" />
          </el-form-item>

          <template v-if="activeEditingNode.type === 'APPROVER' || activeEditingNode.type === 'CC'">
            <el-form-item label="办理人指派规则">
              <el-radio-group v-model="activeEditingNode.assigneeType">
                <el-radio-button value="DEPT_LEADER">部门主管</el-radio-button>
                <el-radio-button value="ROLE">指定角色</el-radio-button>
                <el-radio-button value="USER">指定成员</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="activeEditingNode.assigneeType === 'ROLE'" label="选择系统角色">
              <el-select v-model="activeEditingNode.assigneeValue" placeholder="请选择角色" style="width: 100%">
                <el-option
                  v-for="r in systemRoles"
                  :key="r.id"
                  :label="r.roleName + ' (' + r.roleCode + ')'"
                  :value="String(r.id)"
                />
              </el-select>
            </el-form-item>

            <el-form-item v-else-if="activeEditingNode.assigneeType === 'USER'" label="选择指定人员">
              <el-select v-model="activeEditingNode.assigneeValue" placeholder="请选择用户" style="width: 100%">
                <el-option
                  v-for="u in systemUsers"
                  :key="u.id"
                  :label="u.nickname + ' (' + u.username + ')'"
                  :value="String(u.id)"
                />
              </el-select>
            </el-form-item>

            <el-form-item v-else label="直属部门主管">
              <div class="note-box">
                运行时根据发起人所属的一级/二级部门，动态定位该部门的 leader 负责人办理。如果部门暂未设主管，系统自动平滑兜底。
              </div>
            </el-form-item>

            <el-form-item v-if="activeEditingNode.type === 'APPROVER'" label="多人审批方式">
              <el-radio-group v-model="activeEditingNode.approvalMode">
                <el-radio value="ANY">或签 (一名成员同意即可流转)</el-radio>
                <el-radio value="ALL">会签 (需所有成员全部同意)</el-radio>
              </el-radio-group>
            </el-form-item>
          </template>
        </el-form>
      </div>

      <div v-else-if="activeEditingBranch" class="node-drawer-content">
        <el-form label-position="top">
          <el-form-item label="分支名称">
            <el-input v-model="activeEditingBranch.branch.name" />
          </el-form-item>

          <el-form-item label="是否为兜底分支">
            <el-switch
              :model-value="!activeEditingBranch.branch.condition"
              @update:model-value="(val: any) => {
                if (val) {
                  activeEditingBranch!.branch.condition = undefined;
                } else {
                  activeEditingBranch!.branch.condition = {
                    field: formFields[0]?.fieldCode || 'days',
                    operator: '>',
                    value: 3
                  };
                }
              }"
            />
            <div class="help-text">当其他分支均不匹配时，自动走此默认分支</div>
          </el-form-item>

          <template v-if="activeEditingBranch.branch.condition">
            <el-form-item label="判定表单字段">
              <el-select v-model="activeEditingBranch.branch.condition.field" style="width: 100%">
                <el-option
                  v-for="f in formFields"
                  :key="f.fieldCode"
                  :label="f.fieldName + ' (' + f.fieldCode + ')'"
                  :value="f.fieldCode"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="运算符">
              <el-select v-model="activeEditingBranch.branch.condition.operator" style="width: 100%">
                <el-option label="大于 (>)" value=">" />
                <el-option label="大于等于 (>=)" value=">=" />
                <el-option label="小于 (<)" value="<" />
                <el-option label="小于等于 (<=)" value="<=" />
                <el-option label="等于 (==)" value="==" />
                <el-option label="不等于 (!=)" value="!=" />
                <el-option label="文本包含 (contains)" value="contains" />
              </el-select>
            </el-form-item>

            <el-form-item label="判定阈值">
              <el-input v-model="activeEditingBranch.branch.condition.value as string" placeholder="如 3, 5000, 紧急" />
            </el-form-item>
          </template>
        </el-form>
      </div>
    </el-drawer>

    <template #footer>
      <div class="designer-modal-footer">
        <el-button @click="emit('update:visible', false)">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          保存并即刻生效发布
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.designer-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 28px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.flow-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #eff6ff;
  color: #2563eb;
  padding: 3px 10px;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 600;
}

.designer-modal-header .title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.designer-container {
  min-height: 520px;
  max-height: 640px;
  overflow-y: auto;
  padding: 8px 12px;
}

/* Canvas Styles */
.flow-canvas-wrapper {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 24px;
}

.canvas-tips {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #64748b;
  margin-bottom: 24px;
  background: #fff;
  padding: 10px 16px;
  border-radius: 6px;
  border: 1px dashed #cbd5e1;
}

.tip-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10b981;
}

.flow-canvas {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.node-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.node-card {
  width: 320px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.05), 0 2px 4px -2px rgb(0 0 0 / 0.05);
  border: 1px solid #e2e8f0;
  overflow: hidden;
  transition: all 0.2s ease;
}

.node-card.clickable {
  cursor: pointer;
}

.node-card.clickable:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 15px -3px rgb(0 0 0 / 0.1);
  border-color: #3b82f6;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #f1f5f9;
  border-bottom: 1px solid #e2e8f0;
}

.node-card.start .card-header {
  background: #dbeafe;
}

.node-card.approver .card-header {
  background: #fef3c7;
}

.node-card.cc .card-header {
  background: #e0e7ff;
}

.node-card.end .card-header {
  background: #d1fae5;
}

.node-tag {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgb(0 0 0 / 0.06);
}

.node-name {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  flex: 1;
  margin-left: 8px;
}

.card-body {
  padding: 12px 14px;
  font-size: 12px;
  color: #475569;
}

.desc-line {
  margin-bottom: 4px;
}

.desc-line:last-child {
  margin-bottom: 0;
}

.end-text {
  color: #059669;
  font-weight: 600;
}

/* Branch styling */
.branch-container {
  width: 100%;
  max-width: 720px;
  background: #f8fafc;
  border: 1px dashed #94a3b8;
  border-radius: 8px;
  padding: 16px;
  margin: 8px 0;
}

.branch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.branch-title {
  font-weight: 600;
  font-size: 13px;
  color: #475569;
}

.branches-track {
  display: flex;
  gap: 16px;
  overflow-x: auto;
  padding-bottom: 8px;
}

.branch-column {
  flex: 1;
  min-width: 200px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.branch-header {
  width: 100%;
  padding: 8px;
  background: #f8fafc;
  border-radius: 4px;
  cursor: pointer;
  position: relative;
  text-align: center;
  margin-bottom: 12px;
}

.branch-header:hover {
  background: #f1f5f9;
}

.branch-name {
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
}

.branch-rule {
  font-size: 11px;
  color: #64748b;
  margin-top: 4px;
}

.default-badge {
  background: #e2e8f0;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
}

.branch-del {
  position: absolute;
  top: 4px;
  right: 4px;
}

.branch-card {
  width: 220px;
}

/* Add Node connector bar */
.add-node-bar {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  margin: 6px 0;
}

.add-node-bar .line {
  width: 2px;
  height: 24px;
  background: #cbd5e1;
}

.add-btn {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 2px solid #3b82f6;
  background: #fff;
  color: #3b82f6;
  font-size: 16px;
  line-height: 1;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
}

.add-btn:hover {
  background: #3b82f6;
  color: #fff;
  transform: scale(1.15);
}

.add-menu {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.add-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: #1e293b;
  transition: background 0.15s ease;
}

.add-item:hover {
  background: #f1f5f9;
}

/* Form Designer Styles */
.form-designer-wrapper {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 20px;
}

.designer-left-pane {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.field-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 240px;
  overflow-y: auto;
}

.field-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 8px 12px;
  cursor: pointer;
}

.field-item.active {
  border-color: #3b82f6;
  background: #eff6ff;
}

.field-label {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.field-type-tag {
  font-size: 11px;
  color: #64748b;
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  margin-left: 8px;
}

.field-editor-box {
  background: #fff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 12px;
}

.editor-title {
  font-size: 12px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 8px;
}

.designer-right-pane {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 20px;
}

.preview-title {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 16px;
}

.form-preview-card {
  max-width: 480px;
  margin: 0 auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 20px;
  background: #fbfcfe;
}

.preview-header h3 {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 4px;
}

.preview-header .sub-text {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 16px;
}

/* Base Form */
.base-config-wrapper {
  max-width: 600px;
  margin: 20px auto;
}

.help-text {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}

.note-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 10px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}
</style>
