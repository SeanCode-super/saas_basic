<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Check,
  Connection,
  Delete,
  Plus,
  Promotion,
  RefreshRight,
  VideoPlay
} from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";
import {
  deleteAgentWorkflow,
  executeAgentWorkflow,
  fetchAgentWorkflowRuns,
  fetchAgentWorkflows,
  publishAgentWorkflow,
  saveAgentWorkflow,
  type AgentWorkflowEdge,
  type AgentWorkflowGraph,
  type AgentWorkflowNode,
  type AgentWorkflowNodeTrace,
  type AgentWorkflowNodeType,
  type AgentWorkflowRow,
  type AgentWorkflowRunRow,
  type AiAgentRow,
  type AiModelConfigRow,
  type AiToolOption,
  type KnowledgeBaseRow
} from "@/api/modules/ai";
import {
  WORKFLOW_NODE_PALETTE,
  autoLayoutGraph,
  buildBezierEdgePath,
  getNodePaletteMeta,
  hasGraphCycle
} from "./workflowGraphUtils";

const props = defineProps<{
  models: AiModelConfigRow[];
  agents: AiAgentRow[];
  kbs: KnowledgeBaseRow[];
  tools: AiToolOption[];
}>();

const loading = ref(false);
const saving = ref(false);
const executing = ref(false);
const workflows = ref<AgentWorkflowRow[]>([]);
const runs = ref<AgentWorkflowRunRow[]>([]);
const selectedWorkflowId = ref<number | undefined>(undefined);
const inspectorTab = ref<"node" | "run" | "history">("node");

const form = reactive({
  id: undefined as number | undefined,
  workflowCode: "",
  workflowName: "新建多模型编排流程",
  triggerMode: "MANUAL",
  defaultModelId: "deepseek-chat",
  fallbackModelIds: [] as string[],
  status: "DRAFT",
  publishedVersion: 0,
  remark: ""
});

const graph = reactive<AgentWorkflowGraph>({
  nodes: [],
  edges: []
});

const selectedNodeId = ref<string | null>(null);
const connectingFromNodeId = ref<string | null>(null);
const connectingSourceHandle = ref<string | undefined>(undefined);
const zoom = ref(1);
const pan = reactive({ x: 0, y: 0 });
const runInputsJson = ref('{\n  "query": "请分析企业合同付款账期与风控要点",\n  "bizType": "CONTRACT"\n}');
const activeRun = ref<AgentWorkflowRunRow | null>(null);
const selectedTrace = ref<AgentWorkflowNodeTrace | null>(null);

const selectedNode = computed<AgentWorkflowNode | undefined>(() =>
  graph.nodes.find((n) => n.id === selectedNodeId.value)
);

const traceByNodeId = computed<Record<string, AgentWorkflowNodeTrace>>(() => {
  const map: Record<string, AgentWorkflowNodeTrace> = {};
  if (activeRun.value?.nodeTraces) {
    for (const t of activeRun.value.nodeTraces) {
      map[t.nodeId] = t;
    }
  }
  return map;
});

const availableVarChips = computed<string[]>(() => {
  const chips = ["{{input.query}}", "{{input.bizType}}", "{{input.topic}}", "{{input.items}}"];
  for (const n of graph.nodes) {
    if (n.type === "LLM" || n.type === "SUB_AGENT" || n.type === "CODE_TEMPLATE" || n.type === "LOOP") {
      chips.push(`{{${n.id}.text}}`);
    } else if (n.type === "KNOWLEDGE_RAG") {
      chips.push(`{{${n.id}.context}}`);
    } else if (n.type === "TOOL_MCP") {
      chips.push(`{{${n.id}.output}}`);
    } else if (n.type === "HTTP_REQUEST") {
      chips.push(`{{${n.id}.body}}`);
    }
  }
  return chips;
});

function loadWorkflowIntoEditor(wf: AgentWorkflowRow) {
  selectedWorkflowId.value = wf.id;
  form.id = wf.id;
  form.workflowCode = wf.workflowCode;
  form.workflowName = wf.workflowName;
  form.triggerMode = wf.triggerMode || "MANUAL";
  form.defaultModelId = wf.defaultModelId || "deepseek-chat";
  form.fallbackModelIds = [...(wf.fallbackModelIds || [])];
  form.status = wf.status || "DRAFT";
  form.publishedVersion = wf.publishedVersion || 0;
  form.remark = wf.remark || "";

  graph.nodes = (wf.graph?.nodes || []).map((n) => ({ ...n, config: { ...(n.config || {}) } }));
  graph.edges = (wf.graph?.edges || []).map((e) => ({ ...e }));
  selectedNodeId.value = graph.nodes[0]?.id || null;
  connectingFromNodeId.value = null;
  activeRun.value = null;
  selectedTrace.value = null;

  if (wf.variablesSchema?.length) {
    const sampleObj: Record<string, string> = {};
    for (const v of wf.variablesSchema) {
      sampleObj[v.name] = v.defaultValue || "";
    }
    runInputsJson.value = JSON.stringify(sampleObj, null, 2);
  }
  void loadRunsForWorkflow(wf.id);
}

function createNewWorkflow() {
  selectedWorkflowId.value = undefined;
  form.id = undefined;
  form.workflowCode = "";
  form.workflowName = "新多模型协同智能体流程";
  form.triggerMode = "MANUAL";
  form.defaultModelId = props.models[0]?.modelId || "deepseek-chat";
  form.fallbackModelIds = [];
  form.status = "DRAFT";
  form.publishedVersion = 0;
  form.remark = "";
  graph.nodes = [
    { id: "start_1", type: "START", label: "输入接收", x: 60, y: 160, config: { inputs: ["query"] } },
    {
      id: "llm_1",
      type: "LLM",
      label: "多模型推理",
      x: 340,
      y: 160,
      config: {
        modelId: form.defaultModelId,
        fallbackModelIds: [],
        temperature: 0.3,
        systemPrompt: "你是企业多模型 AI 助手。",
        userPromptTemplate: "{{input.query}}"
      }
    },
    { id: "end_1", type: "END", label: "最终输出", x: 630, y: 160, config: { outputTemplate: "{{llm_1.text}}" } }
  ];
  graph.edges = [
    { id: "e1", source: "start_1", target: "llm_1" },
    { id: "e2", source: "llm_1", target: "end_1" }
  ];
  selectedNodeId.value = "llm_1";
  activeRun.value = null;
}

async function loadAll() {
  loading.value = true;
  try {
    const list = await fetchAgentWorkflows();
    workflows.value = list || [];
    if (workflows.value.length > 0 && !selectedWorkflowId.value) {
      loadWorkflowIntoEditor(workflows.value[0]);
    } else if (workflows.value.length === 0 && graph.nodes.length === 0) {
      createNewWorkflow();
    }
  } catch (err: any) {
    ElMessage.error(err?.message || "加载编排工作流失败");
  } finally {
    loading.value = false;
  }
}

async function loadRunsForWorkflow(workflowId?: number) {
  try {
    runs.value = await fetchAgentWorkflowRuns(workflowId);
  } catch {
    runs.value = [];
  }
}

function addNodeToCanvas(type: AgentWorkflowNodeType) {
  const meta = getNodePaletteMeta(type);
  const idx = graph.nodes.filter((n) => n.type === type).length + 1;
  const id = `${type.toLowerCase()}_${idx}_${Date.now().toString().slice(-3)}`;
  const x = 140 + ((graph.nodes.length * 70) % 520);
  const y = 80 + ((graph.nodes.length * 55) % 240);
  const config = JSON.parse(JSON.stringify(meta.defaultConfig));
  if (type === "LLM" && props.models.length > 0) {
    config.modelId = form.defaultModelId || props.models[0].modelId;
  }
  const node: AgentWorkflowNode = { id, type, label: meta.label, x, y, config };
  graph.nodes.push(node);
  selectedNodeId.value = id;
  inspectorTab.value = "node";
}

function removeNode(nodeId: string) {
  graph.nodes = graph.nodes.filter((n) => n.id !== nodeId);
  graph.edges = graph.edges.filter((e) => e.source !== nodeId && e.target !== nodeId);
  if (selectedNodeId.value === nodeId) {
    selectedNodeId.value = graph.nodes[0]?.id || null;
  }
}

function startConnect(nodeId: string, handle?: string) {
  connectingFromNodeId.value = nodeId;
  connectingSourceHandle.value = handle;
}

function completeConnect(targetNodeId: string) {
  const src = connectingFromNodeId.value;
  if (!src || src === targetNodeId) {
    connectingFromNodeId.value = null;
    return;
  }
  const duplicate = graph.edges.some(
    (e) => e.source === src && e.target === targetNodeId && e.sourceHandle === connectingSourceHandle.value
  );
  if (!duplicate) {
    const newEdge: AgentWorkflowEdge = {
      id: `edge_${Date.now().toString(36)}`,
      source: src,
      target: targetNodeId,
      sourceHandle: connectingSourceHandle.value,
      label: connectingSourceHandle.value || undefined
    };
    const nextGraph: AgentWorkflowGraph = {
      nodes: graph.nodes,
      edges: [...graph.edges, newEdge]
    };
    if (hasGraphCycle(nextGraph)) {
      ElMessage.warning("禁止在 DAG 中连成有向环路，请改用 LOOP 节点");
    } else {
      graph.edges.push(newEdge);
    }
  }
  connectingFromNodeId.value = null;
  connectingSourceHandle.value = undefined;
}

function removeEdge(edgeId: string) {
  graph.edges = graph.edges.filter((e) => e.id !== edgeId);
}

function handleAutoLayout() {
  const arranged = autoLayoutGraph(graph);
  graph.nodes = arranged.nodes;
  pan.x = 0;
  pan.y = 0;
}

// Smooth Dragging for Nodes & Panning
let dragState:
  | { kind: "node"; nodeId: string; startX: number; startY: number; origX: number; origY: number }
  | { kind: "pan"; startX: number; startY: number; origPanX: number; origPanY: number }
  | null = null;

function onNodePointerDown(e: MouseEvent, node: AgentWorkflowNode) {
  selectedNodeId.value = node.id;
  dragState = { kind: "node", nodeId: node.id, startX: e.clientX, startY: e.clientY, origX: node.x, origY: node.y };
  window.addEventListener("mousemove", onWindowMouseMove);
  window.addEventListener("mouseup", onWindowMouseUp);
}

function onCanvasMouseDown(e: MouseEvent) {
  if (connectingFromNodeId.value) {
    connectingFromNodeId.value = null;
    return;
  }
  dragState = { kind: "pan", startX: e.clientX, startY: e.clientY, origPanX: pan.x, origPanY: pan.y };
  window.addEventListener("mousemove", onWindowMouseMove);
  window.addEventListener("mouseup", onWindowMouseUp);
}

function onWindowMouseMove(e: MouseEvent) {
  const currentDrag = dragState;
  if (!currentDrag) return;
  if (currentDrag.kind === "node") {
    const node = graph.nodes.find((n) => n.id === currentDrag.nodeId);
    if (node) {
      node.x = Math.max(10, Math.round(currentDrag.origX + (e.clientX - currentDrag.startX) / zoom.value));
      node.y = Math.max(10, Math.round(currentDrag.origY + (e.clientY - currentDrag.startY) / zoom.value));
    }
  } else {
    pan.x = currentDrag.origPanX + (e.clientX - currentDrag.startX);
    pan.y = currentDrag.origPanY + (e.clientY - currentDrag.startY);
  }
}

function onWindowMouseUp() {
  dragState = null;
  window.removeEventListener("mousemove", onWindowMouseMove);
  window.removeEventListener("mouseup", onWindowMouseUp);
}

function getEdgePath(edge: AgentWorkflowEdge): string {
  const s = graph.nodes.find((n) => n.id === edge.source);
  const t = graph.nodes.find((n) => n.id === edge.target);
  if (!s || !t) return "";
  return buildBezierEdgePath(s, t);
}

function getEdgeLabelPos(edge: AgentWorkflowEdge): { x: number; y: number } {
  const s = graph.nodes.find((n) => n.id === edge.source);
  const t = graph.nodes.find((n) => n.id === edge.target);
  if (!s || !t) return { x: 0, y: 0 };
  return { x: (s.x + 216 + t.x) / 2, y: (s.y + 43 + t.y + 43) / 2 - 8 };
}

function appendVariableChip(chip: string) {
  const node = selectedNode.value;
  if (!node) return;
  if (node.type === "LLM") {
    node.config.userPromptTemplate = (node.config.userPromptTemplate || "") + chip;
  } else if (node.type === "KNOWLEDGE_RAG" || node.type === "TOOL_MCP") {
    node.config.queryTemplate = (node.config.queryTemplate || "") + chip;
  } else if (node.type === "END") {
    node.config.outputTemplate = (node.config.outputTemplate || "") + chip;
  } else if (node.type === "CODE_TEMPLATE") {
    node.config.template = (node.config.template || "") + chip;
  }
}

function addConditionBranch() {
  const node = selectedNode.value;
  if (!node) return;
  if (!Array.isArray(node.config.branches)) node.config.branches = [];
  const idx = node.config.branches.length + 1;
  node.config.branches.splice(Math.max(0, node.config.branches.length - 1), 0, {
    id: `branch_${idx}`,
    label: `分支 ${idx}`,
    operator: "EQUALS",
    value: ""
  });
}

async function handleSave() {
  if (!form.workflowName.trim()) {
    ElMessage.warning("请输入编排名称");
    return;
  }
  if (hasGraphCycle(graph)) {
    ElMessage.warning("流程图中存在环路，请修正后再保存");
    return;
  }
  saving.value = true;
  try {
    const saved = await saveAgentWorkflow({
      id: form.id,
      workflowCode: form.workflowCode || undefined,
      workflowName: form.workflowName,
      triggerMode: form.triggerMode,
      graph: { nodes: graph.nodes, edges: graph.edges },
      defaultModelId: form.defaultModelId,
      fallbackModelIds: form.fallbackModelIds,
      status: form.status,
      remark: form.remark
    });
    ElMessage.success("编排工作流保存成功");
    await loadAll();
    loadWorkflowIntoEditor(saved);
  } catch (err: any) {
    ElMessage.error(err?.message || "保存失败");
  } finally {
    saving.value = false;
  }
}

async function handlePublish() {
  if (!form.id) {
    await handleSave();
  }
  if (!form.id) return;
  try {
    const pub = await publishAgentWorkflow(form.id);
    ElMessage.success(`已发布正式版本 v${pub.publishedVersion}`);
    await loadAll();
    loadWorkflowIntoEditor(pub);
  } catch (err: any) {
    ElMessage.error(err?.message || "发布失败");
  }
}

async function handleDelete(wf: AgentWorkflowRow) {
  try {
    await ElMessageBox.confirm(`确定删除编排流程「${wf.workflowName}」吗？`, "删除确认", { type: "warning" });
    await deleteAgentWorkflow(wf.id);
    ElMessage.success("已删除");
    if (selectedWorkflowId.value === wf.id) {
      selectedWorkflowId.value = undefined;
    }
    await loadAll();
  } catch {
    // cancelled
  }
}

async function handleRun(dryRun: boolean) {
  if (!form.id) {
    await handleSave();
  }
  if (!form.id) return;
  let parsedInputs: Record<string, any> = {};
  try {
    parsedInputs = JSON.parse(runInputsJson.value || "{}");
  } catch {
    ElMessage.warning("输入变量 JSON 格式不正确");
    return;
  }
  executing.value = true;
  inspectorTab.value = "run";
  try {
    const res = await executeAgentWorkflow(form.id, {
      inputs: parsedInputs,
      overrideGraph: { nodes: graph.nodes, edges: graph.edges },
      triggerSource: dryRun ? "DRY_RUN" : "STUDIO",
      dryRun
    });
    activeRun.value = res;
    selectedTrace.value = res.nodeTraces[0] || null;
    if (res.status === "SUCCEEDED") {
      ElMessage.success(`${dryRun ? "试跑" : "实跑"}成功 · 执行 ${res.executedNodes} 个节点 · ${res.totalTokens} Tokens`);
    } else {
      ElMessage.error(res.errorMessage || "执行失败");
    }
    await loadRunsForWorkflow(form.id);
  } catch (err: any) {
    ElMessage.error(err?.message || "编排执行异常");
  } finally {
    executing.value = false;
  }
}

onMounted(() => {
  void loadAll();
});
</script>

<template>
  <div class="dag-studio-shell">
    <!-- Top Control Ribbon -->
    <div class="dag-ribbon">
      <div class="ribbon-left">
        <el-input v-model="form.workflowName" placeholder="编排流程名称" style="width: 220px" size="small" />
        <el-tag size="small" :type="form.status === 'PUBLISHED' ? 'success' : 'info'">
          {{ form.status }} · v{{ form.publishedVersion }}
        </el-tag>
        <el-select v-model="form.defaultModelId" placeholder="默认主模型" size="small" filterable style="width: 220px">
          <el-option
            v-for="m in models"
            :key="m.modelId"
            :label="`[${m.providerType || 'OPENAI'}] ${m.modelName} (${m.modelId})`"
            :value="m.modelId"
          />
        </el-select>
        <el-select
          v-model="form.fallbackModelIds"
          multiple
          collapse-tags
          placeholder="全局备选降级模型链"
          size="small"
          style="width: 230px"
        >
          <el-option v-for="m in models" :key="m.modelId" :label="m.modelId" :value="m.modelId" />
        </el-select>
      </div>
      <div class="ribbon-actions">
        <el-button size="small" @click="handleAutoLayout">自动整理画布</el-button>
        <el-button size="small" :icon="RefreshRight" :loading="loading" @click="loadAll">刷新</el-button>
        <el-button size="small" type="info" plain :icon="VideoPlay" :loading="executing" @click="handleRun(true)">
          结构试跑 (Dry-Run)
        </el-button>
        <el-button size="small" type="success" :icon="VideoPlay" :loading="executing" @click="handleRun(false)">
          实跑联调
        </el-button>
        <el-button size="small" type="primary" :icon="Check" :loading="saving" @click="handleSave">保存草稿</el-button>
        <el-button size="small" type="warning" :icon="Promotion" @click="handlePublish">发布版本</el-button>
      </div>
    </div>

    <!-- 3-Column Workbench -->
    <div class="dag-main-grid">
      <!-- Left: Workflows + Node Palette -->
      <BaseCard class="left-panel">
        <div class="panel-section-head">
          <h4>编排工作流 ({{ workflows.length }})</h4>
          <el-button size="small" link type="primary" :icon="Plus" @click="createNewWorkflow">新建</el-button>
        </div>
        <div class="wf-list">
          <div
            v-for="wf in workflows"
            :key="wf.id"
            class="wf-card"
            :class="{ active: wf.id === selectedWorkflowId }"
            @click="loadWorkflowIntoEditor(wf)"
          >
            <div class="wf-card-title">
              <strong>{{ wf.workflowName }}</strong>
              <el-button link type="danger" size="small" :icon="Delete" @click.stop="handleDelete(wf)" />
            </div>
            <div class="wf-card-meta">
              <el-tag size="small" :type="wf.status === 'PUBLISHED' ? 'success' : 'info'">{{ wf.status }}</el-tag>
              <span>{{ wf.graph?.nodes?.length || 0 }} 节点 · {{ wf.runCount }} 次</span>
            </div>
          </div>
        </div>

        <div class="panel-section-head" style="margin-top: 14px">
          <h4>节点组件库（点击加入画布）</h4>
        </div>
        <div class="palette-grid">
          <button
            v-for="item in WORKFLOW_NODE_PALETTE"
            :key="item.type"
            type="button"
            class="palette-item"
            @click="addNodeToCanvas(item.type)"
          >
            <span class="palette-badge" :style="{ background: item.color }">{{ item.badge }}</span>
            <div class="palette-text">
              <strong>{{ item.label }}</strong>
              <span>{{ item.description }}</span>
            </div>
          </button>
        </div>
      </BaseCard>

      <!-- Center: Interactive SVG DAG Canvas -->
      <div class="canvas-wrapper" @mousedown="onCanvasMouseDown">
        <div class="canvas-hud" @mousedown.stop>
          <span v-if="connectingFromNodeId" class="connect-hint">
            <el-icon><Connection /></el-icon> 请点击目标节点左端输入锚点完成连线（点击空白取消）
          </span>
          <span v-else class="canvas-tip">拖拽节点调整布局 · 点击右侧输出圆点连线 · 点击连线可删除</span>
          <div class="zoom-controls">
            <el-button size="small" @click="zoom = Math.max(0.6, +(zoom - 0.1).toFixed(1))">-</el-button>
            <span>{{ Math.round(zoom * 100) }}%</span>
            <el-button size="small" @click="zoom = Math.min(1.4, +(zoom + 0.1).toFixed(1))">+</el-button>
          </div>
        </div>

        <div class="canvas-stage" :style="{ transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})` }">
          <svg class="edges-svg" width="2400" height="1400">
            <defs>
              <marker id="wf-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
                <path d="M0,1 L7,4 L0,7 Z" fill="#64748b" />
              </marker>
            </defs>
            <g v-for="edge in graph.edges" :key="edge.id" class="edge-group" @click.stop="removeEdge(edge.id)">
              <path :d="getEdgePath(edge)" class="edge-hit" />
              <path :d="getEdgePath(edge)" class="edge-line" marker-end="url(#wf-arrow)" />
              <text
                v-if="edge.label"
                :x="getEdgeLabelPos(edge).x"
                :y="getEdgeLabelPos(edge).y"
                class="edge-label"
              >
                {{ edge.label }}
              </text>
            </g>
          </svg>

          <div
            v-for="node in graph.nodes"
            :key="node.id"
            class="dag-node"
            :class="{
              'is-selected': node.id === selectedNodeId,
              'trace-ok': traceByNodeId[node.id]?.status === 'SUCCEEDED',
              'trace-fail': traceByNodeId[node.id]?.status === 'FAILED'
            }"
            :style="{ left: `${node.x}px`, top: `${node.y}px` }"
            @mousedown.stop="onNodePointerDown($event, node)"
          >
            <!-- Left Input Anchor -->
            <button
              v-if="node.type !== 'START'"
              type="button"
              class="port port-in"
              :class="{ 'port-target-ready': Boolean(connectingFromNodeId && connectingFromNodeId !== node.id) }"
              title="连接到此节点输入"
              @mousedown.stop
              @click.stop="completeConnect(node.id)"
            />

            <div class="dag-node-header">
              <span class="node-badge" :style="{ background: getNodePaletteMeta(node.type).color }">
                {{ getNodePaletteMeta(node.type).badge }}
              </span>
              <strong class="node-title">{{ node.label }}</strong>
              <button type="button" class="node-del-btn" title="删除节点" @mousedown.stop @click.stop="removeNode(node.id)">
                ×
              </button>
            </div>
            <div class="dag-node-body">
              <template v-if="node.type === 'LLM'">
                <div class="node-sub-pill">🤖 {{ node.config.modelId || form.defaultModelId }}</div>
                <div v-if="node.config.fallbackModelIds?.length" class="node-sub-meta">
                  备选: {{ node.config.fallbackModelIds.join(" → ") }}
                </div>
              </template>
              <template v-else-if="node.type === 'CONDITION'">
                <div class="node-sub-pill">🔀 {{ node.config.variable || "condition" }}</div>
              </template>
              <template v-else-if="node.type === 'KNOWLEDGE_RAG'">
                <div class="node-sub-pill">📚 知识库 ({{ (node.config.kbIds || []).length || "默认" }})</div>
              </template>
              <template v-else-if="node.type === 'TOOL_MCP'">
                <div class="node-sub-pill">🔌 {{ node.config.toolCode || "MCP Tool" }}</div>
              </template>
              <template v-else>
                <div class="node-sub-meta">{{ getNodePaletteMeta(node.type).description }}</div>
              </template>

              <div v-if="traceByNodeId[node.id]" class="node-trace-pill">
                {{ traceByNodeId[node.id].status }} · {{ traceByNodeId[node.id].latencyMs }}ms
                <span v-if="traceByNodeId[node.id].fallbackUsed">(降级)</span>
              </div>
            </div>

            <!-- Right Output Anchor(s) -->
            <template v-if="node.type === 'CONDITION' && Array.isArray(node.config.branches)">
              <div class="branch-ports" @mousedown.stop>
                <button
                  v-for="br in node.config.branches"
                  :key="br.id"
                  type="button"
                  class="branch-port-btn"
                  @click.stop="startConnect(node.id, br.id)"
                >
                  {{ br.label || br.id }} ●
                </button>
              </div>
            </template>
            <button
              v-else-if="node.type !== 'END'"
              type="button"
              class="port port-out"
              title="拖线连接到下游节点"
              @mousedown.stop
              @click.stop="startConnect(node.id)"
            />
          </div>
        </div>
      </div>

      <!-- Right: Node Inspector & Execution Trace -->
      <BaseCard class="right-panel">
        <div class="inspector-tabs">
          <button
            type="button"
            class="insp-tab"
            :class="{ active: inspectorTab === 'node' }"
            @click="inspectorTab = 'node'"
          >
            节点参数
          </button>
          <button
            type="button"
            class="insp-tab"
            :class="{ active: inspectorTab === 'run' }"
            @click="inspectorTab = 'run'"
          >
            运行调试与链路
          </button>
          <button
            type="button"
            class="insp-tab"
            :class="{ active: inspectorTab === 'history' }"
            @click="inspectorTab = 'history'"
          >
            历史记录 ({{ runs.length }})
          </button>
        </div>

        <!-- Inspector Tab 1: Selected Node Config -->
        <div v-if="inspectorTab === 'node'" class="insp-scroll">
          <template v-if="selectedNode">
            <el-form label-position="top" size="small">
              <el-form-item label="节点 ID / 显示名称">
                <el-input v-model="selectedNode.label" />
              </el-form-item>

              <template v-if="selectedNode.type === 'LLM'">
                <el-form-item label="主模型选择（支持全生态已配置模型）">
                  <el-select v-model="selectedNode.config.modelId" filterable allow-create style="width: 100%">
                    <el-option
                      v-for="m in models"
                      :key="m.modelId"
                      :label="`[${m.providerType || 'OPENAI'}] ${m.modelName} (${m.modelId})`"
                      :value="m.modelId"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="自动降级备选模型链（按顺序容灾切换）">
                  <el-select v-model="selectedNode.config.fallbackModelIds" multiple style="width: 100%">
                    <el-option v-for="m in models" :key="m.modelId" :label="m.modelId" :value="m.modelId" />
                  </el-select>
                </el-form-item>
                <el-form-item label="温度 Temperature">
                  <el-slider v-model="selectedNode.config.temperature" :min="0" :max="1.5" :step="0.1" />
                </el-form-item>
                <el-form-item label="System Prompt 系统指令">
                  <el-input v-model="selectedNode.config.systemPrompt" type="textarea" :rows="3" />
                </el-form-item>
                <el-form-item label="User Prompt 模板（可引用上游变量）">
                  <el-input v-model="selectedNode.config.userPromptTemplate" type="textarea" :rows="4" />
                </el-form-item>
              </template>

              <template v-else-if="selectedNode.type === 'CONDITION'">
                <el-form-item label="判断变量路径 (如 input.bizType)">
                  <el-input v-model="selectedNode.config.variable" />
                </el-form-item>
                <div class="branch-list-edit">
                  <div v-for="(br, idx) in selectedNode.config.branches || []" :key="br.id" class="branch-edit-row">
                    <el-input v-model="br.label" placeholder="分支名" size="small" />
                    <el-select v-model="br.operator" size="small" :disabled="br.operator === 'DEFAULT'">
                      <el-option label="EQUALS" value="EQUALS" />
                      <el-option label="CONTAINS" value="CONTAINS" />
                      <el-option label="NOT_EMPTY" value="NOT_EMPTY" />
                      <el-option label="REGEX" value="REGEX" />
                      <el-option label="GT" value="GT" />
                      <el-option label="LT" value="LT" />
                      <el-option label="DEFAULT" value="DEFAULT" />
                    </el-select>
                    <el-input v-if="br.operator !== 'DEFAULT'" v-model="br.value" placeholder="匹配值" size="small" />
                    <el-button
                      v-if="br.operator !== 'DEFAULT'"
                      link
                      type="danger"
                      @click="selectedNode.config.branches.splice(idx, 1)"
                    >
                      删
                    </el-button>
                  </div>
                  <el-button size="small" plain @click="addConditionBranch">+ 添加条件分支</el-button>
                </div>
              </template>

              <template v-else-if="selectedNode.type === 'KNOWLEDGE_RAG'">
                <el-form-item label="关联企业知识库">
                  <el-select v-model="selectedNode.config.kbIds" multiple style="width: 100%">
                    <el-option v-for="kb in kbs" :key="kb.id" :label="kb.kbName" :value="kb.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="检索查询词模板">
                  <el-input v-model="selectedNode.config.queryTemplate" />
                </el-form-item>
                <el-form-item label="Top-K 切片数">
                  <el-input-number v-model="selectedNode.config.topK" :min="1" :max="10" />
                </el-form-item>
              </template>

              <template v-else-if="selectedNode.type === 'TOOL_MCP'">
                <el-form-item label="选择平台工具 / MCP 工具">
                  <el-select v-model="selectedNode.config.toolCode" filterable allow-create style="width: 100%">
                    <el-option v-for="t in tools" :key="t.toolCode" :label="`${t.toolName} (${t.toolCode})`" :value="t.toolCode" />
                  </el-select>
                </el-form-item>
                <el-form-item label="参数模板">
                  <el-input v-model="selectedNode.config.queryTemplate" type="textarea" :rows="3" />
                </el-form-item>
              </template>

              <template v-else-if="selectedNode.type === 'SUB_AGENT'">
                <el-form-item label="选择子智能体">
                  <el-select v-model="selectedNode.config.agentId" style="width: 100%">
                    <el-option v-for="ag in agents" :key="ag.id" :label="`${ag.avatar} ${ag.agentName}`" :value="ag.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="传入消息模板">
                  <el-input v-model="selectedNode.config.messageTemplate" type="textarea" :rows="3" />
                </el-form-item>
              </template>

              <template v-else-if="selectedNode.type === 'HTTP_REQUEST'">
                <el-form-item label="请求方法">
                  <el-select v-model="selectedNode.config.method" style="width: 100%">
                    <el-option label="GET" value="GET" />
                    <el-option label="POST" value="POST" />
                    <el-option label="PUT" value="PUT" />
                  </el-select>
                </el-form-item>
                <el-form-item label="目标 URL（受 SSRF 防护校验）">
                  <el-input v-model="selectedNode.config.url" />
                </el-form-item>
                <el-form-item v-if="selectedNode.config.method !== 'GET'" label="JSON Body 模板">
                  <el-input v-model="selectedNode.config.bodyTemplate" type="textarea" :rows="3" />
                </el-form-item>
              </template>

              <template v-else-if="selectedNode.type === 'LOOP'">
                <el-form-item label="数组变量路径 (如 input.items)">
                  <el-input v-model="selectedNode.config.listVariable" />
                </el-form-item>
                <el-form-item label="单项迭代模板（可用 {{item}} / {{index}}）">
                  <el-input v-model="selectedNode.config.itemTemplate" type="textarea" :rows="3" />
                </el-form-item>
              </template>

              <template v-else-if="selectedNode.type === 'CODE_TEMPLATE'">
                <el-form-item label="上下文变量聚合模板">
                  <el-input v-model="selectedNode.config.template" type="textarea" :rows="5" />
                </el-form-item>
              </template>

              <template v-else-if="selectedNode.type === 'END'">
                <el-form-item label="最终输出映射模板">
                  <el-input v-model="selectedNode.config.outputTemplate" type="textarea" :rows="4" />
                </el-form-item>
              </template>
            </el-form>

            <div class="var-chips-box">
              <span class="chips-title">点击插入上游变量：</span>
              <div class="chips-wrap">
                <button
                  v-for="chip in availableVarChips"
                  :key="chip"
                  type="button"
                  class="var-chip"
                  @click="appendVariableChip(chip)"
                >
                  {{ chip }}
                </button>
              </div>
            </div>
          </template>
          <el-empty v-else description="请在画布中点击任意节点进行配置" />
        </div>

        <!-- Inspector Tab 2: Execute & Live Trace -->
        <div v-if="inspectorTab === 'run'" class="insp-scroll">
          <div class="run-input-box">
            <label class="box-label">输入变量 (JSON)</label>
            <el-input v-model="runInputsJson" type="textarea" :rows="4" />
          </div>

          <div v-if="activeRun" class="run-result-card">
            <div class="run-summary-head">
              <el-tag :type="activeRun.status === 'SUCCEEDED' ? 'success' : 'danger'">{{ activeRun.status }}</el-tag>
              <code>{{ activeRun.runNo }}</code>
              <span>{{ activeRun.totalTokens }} Tokens · {{ activeRun.totalLatencyMs }}ms</span>
            </div>
            <pre class="output-pre">{{ JSON.stringify(activeRun.outputs, null, 2) }}</pre>

            <h5 class="trace-heading">节点级执行追踪 ({{ activeRun.nodeTraces.length }})</h5>
            <div class="trace-list">
              <div
                v-for="tr in activeRun.nodeTraces"
                :key="tr.nodeId"
                class="trace-row"
                :class="{ active: selectedTrace?.nodeId === tr.nodeId }"
                @click="selectedTrace = tr"
              >
                <div class="trace-row-top">
                  <strong>{{ tr.nodeLabel }}</strong>
                  <el-tag size="small" :type="tr.status === 'SUCCEEDED' ? 'success' : 'danger'">{{ tr.status }}</el-tag>
                </div>
                <div class="trace-row-meta">
                  <span>{{ tr.modelId || tr.nodeType }}</span>
                  <span>{{ tr.latencyMs }}ms · {{ tr.promptTokens + tr.completionTokens }} tok</span>
                </div>
              </div>
            </div>

            <div v-if="selectedTrace" class="trace-detail-box">
              <h6>节点快照 · {{ selectedTrace.nodeId }}</h6>
              <pre class="output-pre">{{ JSON.stringify(selectedTrace.outputSnapshot, null, 2) }}</pre>
            </div>
          </div>
        </div>

        <!-- Inspector Tab 3: History Runs -->
        <div v-if="inspectorTab === 'history'" class="insp-scroll">
          <div v-for="r in runs" :key="r.runNo" class="history-item">
            <div class="trace-row-top">
              <code>{{ r.runNo }}</code>
              <el-tag size="small" :type="r.status === 'SUCCEEDED' ? 'success' : 'danger'">{{ r.status }}</el-tag>
            </div>
            <div class="trace-row-meta">
              <span>{{ r.executedNodes }} 节点 · {{ r.totalTokens }} Tokens</span>
              <span>{{ r.totalLatencyMs }}ms</span>
            </div>
          </div>
          <el-empty v-if="runs.length === 0" description="暂无历史执行记录" />
        </div>
      </BaseCard>
    </div>
  </div>
</template>

<style scoped lang="scss">
.dag-studio-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dag-ribbon {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
}

.ribbon-left,
.ribbon-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.dag-main-grid {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) 330px;
  gap: 12px;
  min-height: 660px;
}

.left-panel,
.right-panel {
  display: flex;
  flex-direction: column;
  max-height: 700px;
  overflow: hidden;
}

.panel-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;

  h4 {
    margin: 0;
    font-size: 13.5px;
    font-weight: 600;
    color: #0f172a;
  }
}

.wf-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 180px;
  overflow-y: auto;
}

.wf-card {
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  cursor: pointer;

  &.active {
    border-color: #2563eb;
    background: #eff6ff;
  }
}

.wf-card-title,
.wf-card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
}

.palette-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-y: auto;
  flex: 1;
}

.palette-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 9px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  text-align: left;
  cursor: pointer;

  &:hover {
    border-color: #3b82f6;
    background: #eff6ff;
  }
}

.palette-badge,
.node-badge {
  color: #ffffff;
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 6px;
  flex-shrink: 0;
}

.palette-text {
  display: flex;
  flex-direction: column;
  min-width: 0;

  strong {
    font-size: 12.5px;
    color: #0f172a;
  }

  span {
    font-size: 11px;
    color: #64748b;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.canvas-wrapper {
  position: relative;
  border-radius: 14px;
  border: 1px solid #cbd5e1;
  background-color: #f8fafc;
  background-image: radial-gradient(#cbd5e1 1px, transparent 1px);
  background-size: 18px 18px;
  overflow: hidden;
  min-height: 660px;
}

.canvas-hud {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.9);
  border-bottom: 1px solid #e2e8f0;
  font-size: 12px;
}

.connect-hint {
  color: #d97706;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.canvas-tip {
  color: #64748b;
}

.zoom-controls {
  display: flex;
  align-items: center;
  gap: 6px;
}

.canvas-stage {
  position: relative;
  width: 2400px;
  height: 1400px;
  transform-origin: 0 0;
}

.edges-svg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.edge-group {
  pointer-events: stroke;
  cursor: pointer;
}

.edge-hit {
  fill: none;
  stroke: transparent;
  stroke-width: 14;
}

.edge-line {
  fill: none;
  stroke: #64748b;
  stroke-width: 2;
}

.edge-group:hover .edge-line {
  stroke: #ef4444;
  stroke-width: 3;
}

.edge-label {
  font-size: 11px;
  fill: #475569;
  font-weight: 600;
}

.dag-node {
  position: absolute;
  width: 216px;
  min-height: 84px;
  border-radius: 12px;
  background: #ffffff;
  border: 1.5px solid #cbd5e1;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
  padding: 8px 10px;
  cursor: grab;
  user-select: none;

  &.is-selected {
    border-color: #2563eb;
    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.18);
  }

  &.trace-ok {
    border-color: #16a34a;
  }

  &.trace-fail {
    border-color: #dc2626;
  }
}

.dag-node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.node-title {
  flex: 1;
  font-size: 12.5px;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-del-btn {
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  font-size: 15px;
  line-height: 1;

  &:hover {
    color: #ef4444;
  }
}

.dag-node-body {
  font-size: 11.5px;
  color: #475569;
}

.node-sub-pill {
  padding: 2px 6px;
  border-radius: 6px;
  background: #f1f5f9;
  color: #1e293b;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-sub-meta {
  font-size: 10.5px;
  color: #64748b;
  margin-top: 3px;
}

.node-trace-pill {
  margin-top: 4px;
  font-size: 10.5px;
  color: #15803d;
  font-weight: 600;
}

.port {
  position: absolute;
  top: 50%;
  width: 12px;
  height: 12px;
  margin-top: -6px;
  border-radius: 50%;
  border: 2px solid #ffffff;
  background: #3b82f6;
  cursor: crosshair;
}

.port-in {
  left: -7px;
  background: #64748b;

  &.port-target-ready {
    background: #16a34a;
    transform: scale(1.3);
  }
}

.port-out {
  right: -7px;
}

.branch-ports {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
  justify-content: flex-end;
}

.branch-port-btn {
  border: 1px solid #f59e0b;
  background: #fffbeb;
  color: #b45309;
  border-radius: 6px;
  font-size: 10px;
  padding: 1px 6px;
  cursor: crosshair;
}

.inspector-tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
}

.insp-tab {
  flex: 1;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #475569;
  padding: 6px 8px;
  border-radius: 8px;
  font-size: 12px;
  cursor: pointer;

  &.active {
    background: #2563eb;
    color: #ffffff;
    border-color: #2563eb;
  }
}

.insp-scroll {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.branch-list-edit {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 10px;
}

.branch-edit-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr auto;
  gap: 4px;
  align-items: center;
}

.var-chips-box {
  margin-top: 8px;
  padding: 8px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
}

.chips-title {
  display: block;
  font-size: 11px;
  color: #64748b;
  margin-bottom: 6px;
}

.chips-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.var-chip {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #1d4ed8;
  border-radius: 6px;
  font-size: 11px;
  padding: 2px 6px;
  cursor: pointer;
}

.run-input-box {
  margin-bottom: 10px;
}

.box-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 4px;
}

.run-summary-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  margin-bottom: 8px;
}

.output-pre {
  background: #0f172a;
  color: #e2e8f0;
  padding: 8px;
  border-radius: 8px;
  font-size: 11.5px;
  max-height: 150px;
  overflow: auto;
  white-space: pre-wrap;
}

.trace-heading {
  margin: 10px 0 6px;
  font-size: 12.5px;
}

.trace-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.trace-row,
.history-item {
  padding: 8px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  cursor: pointer;

  &.active {
    border-color: #2563eb;
    background: #eff6ff;
  }
}

.trace-row-top,
.trace-row-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
}

.trace-row-meta {
  color: #64748b;
  margin-top: 3px;
  font-size: 11px;
}

@media (max-width: 1280px) {
  .dag-main-grid {
    grid-template-columns: 1fr;
  }
}
</style>
