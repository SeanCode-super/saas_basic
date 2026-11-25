import type { AgentWorkflowEdge, AgentWorkflowGraph, AgentWorkflowNode, AgentWorkflowNodeType } from "@/api/modules/ai";

export interface NodePaletteMeta {
  type: AgentWorkflowNodeType;
  label: string;
  group: "control" | "model" | "tool";
  badge: string;
  color: string;
  description: string;
  defaultConfig: Record<string, any>;
}

export const WORKFLOW_NODE_PALETTE: NodePaletteMeta[] = [
  {
    type: "START",
    label: "开始输入",
    group: "control",
    badge: "START",
    color: "#2563eb",
    description: "接收输入变量并绑定 {{input.*}}",
    defaultConfig: { inputs: ["query"] }
  },
  {
    type: "LLM",
    label: "大模型节点",
    group: "model",
    badge: "LLM",
    color: "#7c3aed",
    description: "单节点可选任意厂商模型与主备降级链",
    defaultConfig: {
      modelId: "deepseek-chat",
      fallbackModelIds: ["gemini-2.5-flash"],
      temperature: 0.3,
      systemPrompt: "你是企业 AI 专家助手。",
      userPromptTemplate: "{{input.query}}"
    }
  },
  {
    type: "CONDITION",
    label: "条件分支",
    group: "control",
    badge: "IF",
    color: "#d97706",
    description: "按变量匹配路由到不同下游分支",
    defaultConfig: {
      variable: "input.bizType",
      branches: [
        { id: "branch_1", label: "命中条件", operator: "EQUALS", value: "CONTRACT" },
        { id: "default", label: "默认分支", operator: "DEFAULT", value: "" }
      ]
    }
  },
  {
    type: "PARALLEL",
    label: "并行分发",
    group: "control",
    badge: "PAR",
    color: "#0ea5e9",
    description: "并发扇出多个模型或工具分支后汇合",
    defaultConfig: {}
  },
  {
    type: "KNOWLEDGE_RAG",
    label: "知识库 RAG",
    group: "tool",
    badge: "RAG",
    color: "#059669",
    description: "BM25 + 向量混合检索企业知识库",
    defaultConfig: { kbIds: [], queryTemplate: "{{input.query}}", topK: 3 }
  },
  {
    type: "TOOL_MCP",
    label: "工具 / MCP",
    group: "tool",
    badge: "MCP",
    color: "#0891b2",
    description: "调用 NL2SQL / CRM / 外部 MCP JSON-RPC 工具",
    defaultConfig: { toolCode: "nl2sql_query", queryTemplate: "{{input.query}}" }
  },
  {
    type: "SUB_AGENT",
    label: "子智能体",
    group: "model",
    badge: "AGENT",
    color: "#4f46e5",
    description: "嵌套调用已发布的数字员工 Agent",
    defaultConfig: { agentId: null, messageTemplate: "{{input.query}}" }
  },
  {
    type: "HTTP_REQUEST",
    label: "HTTP 调用",
    group: "tool",
    badge: "HTTP",
    color: "#ea580c",
    description: "SSRF 防护的外部 Webhook/REST API 调用",
    defaultConfig: { method: "GET", url: "https://api.example.com/status", bodyTemplate: "{}" }
  },
  {
    type: "LOOP",
    label: "循环迭代",
    group: "control",
    badge: "LOOP",
    color: "#db2777",
    description: "对数组变量执行有界 FOR_EACH 模板处理",
    defaultConfig: { listVariable: "input.items", itemTemplate: "- {{item}}" }
  },
  {
    type: "CODE_TEMPLATE",
    label: "模板转换",
    group: "tool",
    badge: "TPL",
    color: "#475569",
    description: "聚合多节点上下文并渲染输出变量",
    defaultConfig: { template: "{{llm_1.text}}" }
  },
  {
    type: "END",
    label: "结束输出",
    group: "control",
    badge: "END",
    color: "#16a34a",
    description: "映射工作流最终输出 result 字段",
    defaultConfig: { outputTemplate: "{{llm_1.text}}" }
  }
];

export function getNodePaletteMeta(type: string): NodePaletteMeta {
  return WORKFLOW_NODE_PALETTE.find((p) => p.type === type) || WORKFLOW_NODE_PALETTE[1];
}

export function buildBezierEdgePath(
  source: AgentWorkflowNode,
  target: AgentWorkflowNode,
  nodeWidth = 216,
  nodeHeight = 86
): string {
  const sx = source.x + nodeWidth;
  const sy = source.y + nodeHeight / 2;
  const tx = target.x;
  const ty = target.y + nodeHeight / 2;
  const dx = Math.max(48, Math.abs(tx - sx) * 0.5);
  return `M ${sx} ${sy} C ${sx + dx} ${sy}, ${tx - dx} ${ty}, ${tx} ${ty}`;
}

export function hasGraphCycle(graph: AgentWorkflowGraph): boolean {
  const inDegree = new Map<string, number>();
  const adj = new Map<string, string[]>();
  for (const n of graph.nodes) {
    inDegree.set(n.id, 0);
    adj.set(n.id, []);
  }
  for (const e of graph.edges) {
    if (!inDegree.has(e.source) || !inDegree.has(e.target)) continue;
    adj.get(e.source)!.push(e.target);
    inDegree.set(e.target, (inDegree.get(e.target) || 0) + 1);
  }
  const queue: string[] = [];
  for (const [id, deg] of inDegree.entries()) {
    if (deg === 0) queue.push(id);
  }
  let visited = 0;
  while (queue.length > 0) {
    const cur = queue.shift()!;
    visited++;
    for (const nxt of adj.get(cur) || []) {
      const nextDeg = (inDegree.get(nxt) || 0) - 1;
      inDegree.set(nxt, nextDeg);
      if (nextDeg === 0) queue.push(nxt);
    }
  }
  return visited !== graph.nodes.length;
}

export function autoLayoutGraph(graph: AgentWorkflowGraph): AgentWorkflowGraph {
  const levelMap = new Map<string, number>();
  const adj = new Map<string, string[]>();
  const inDeg = new Map<string, number>();
  for (const n of graph.nodes) {
    levelMap.set(n.id, 0);
    adj.set(n.id, []);
    inDeg.set(n.id, 0);
  }
  for (const e of graph.edges) {
    if (!adj.has(e.source) || !inDeg.has(e.target)) continue;
    adj.get(e.source)!.push(e.target);
    inDeg.set(e.target, (inDeg.get(e.target) || 0) + 1);
  }
  const q: string[] = [];
  for (const [id, d] of inDeg.entries()) {
    if (d === 0) q.push(id);
  }
  while (q.length > 0) {
    const cur = q.shift()!;
    const curLevel = levelMap.get(cur) || 0;
    for (const nxt of adj.get(cur) || []) {
      levelMap.set(nxt, Math.max(levelMap.get(nxt) || 0, curLevel + 1));
      const d = (inDeg.get(nxt) || 0) - 1;
      inDeg.set(nxt, d);
      if (d === 0) q.push(nxt);
    }
  }
  const byLevel = new Map<number, AgentWorkflowNode[]>();
  for (const n of graph.nodes) {
    const lvl = levelMap.get(n.id) || 0;
    if (!byLevel.has(lvl)) byLevel.set(lvl, []);
    byLevel.get(lvl)!.push(n);
  }
  const laidOutNodes = graph.nodes.map((n) => {
    const lvl = levelMap.get(n.id) || 0;
    const col = byLevel.get(lvl) || [n];
    const rowIdx = col.indexOf(n);
    return {
      ...n,
      x: 50 + lvl * 265,
      y: 70 + rowIdx * 135
    };
  });
  return { nodes: laidOutNodes, edges: [...graph.edges] as AgentWorkflowEdge[] };
}
