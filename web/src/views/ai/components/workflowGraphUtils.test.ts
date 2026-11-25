import { describe, expect, it } from "vitest";
import { autoLayoutGraph, buildBezierEdgePath, hasGraphCycle } from "./workflowGraphUtils";
import type { AgentWorkflowGraph } from "@/api/modules/ai";

describe("workflowGraphUtils", () => {
  const sampleGraph: AgentWorkflowGraph = {
    nodes: [
      { id: "start_1", type: "START", label: "Start", x: 10, y: 10, config: {} },
      { id: "llm_1", type: "LLM", label: "LLM", x: 20, y: 20, config: {} },
      { id: "end_1", type: "END", label: "End", x: 30, y: 30, config: {} }
    ],
    edges: [
      { id: "e1", source: "start_1", target: "llm_1" },
      { id: "e2", source: "llm_1", target: "end_1" }
    ]
  };

  it("detects acyclic vs cyclic graphs accurately", () => {
    expect(hasGraphCycle(sampleGraph)).toBe(false);
    expect(
      hasGraphCycle({
        nodes: sampleGraph.nodes,
        edges: [...sampleGraph.edges, { id: "e3", source: "end_1", target: "start_1" }]
      })
    ).toBe(true);
  });

  it("assigns strictly advancing horizontal coordinates via topological auto-layout", () => {
    const arranged = autoLayoutGraph(sampleGraph);
    const byId = Object.fromEntries(arranged.nodes.map((n) => [n.id, n]));
    expect(byId.start_1.x).toBeLessThan(byId.llm_1.x);
    expect(byId.llm_1.x).toBeLessThan(byId.end_1.x);
    expect(buildBezierEdgePath(byId.start_1, byId.llm_1)).toContain("M ");
  });
});
