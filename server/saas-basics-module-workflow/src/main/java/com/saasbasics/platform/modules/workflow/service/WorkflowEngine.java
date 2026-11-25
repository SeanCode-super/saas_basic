package com.saasbasics.platform.modules.workflow.service;

import com.saasbasics.platform.modules.workflow.dto.AssigneeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.workflow.mapper.WorkflowParticipantMapper;
import org.springframework.stereotype.Component;

@Component
public class WorkflowEngine {

    private final ObjectMapper objectMapper;
    private final WorkflowParticipantMapper participants;

    public WorkflowEngine(ObjectMapper objectMapper, WorkflowParticipantMapper participants) {
        this.objectMapper = objectMapper;
        this.participants = participants;
    }

    public record ExecutionStep(
            String nodeId,
            String nodeName,
            String nodeType,
            boolean isEnd,
            List<AssigneeInfo> assignees,
            List<AssigneeInfo> ccList
    ) {}

    /**
     * 根据流程节点树和用户填写的表单，计算从 currentNodeId 开始的下一个可执行节点
     */
    public ExecutionStep advanceFlow(
            String flowNodesJson,
            String formDataJson,
            String currentNodeId,
            Long tenantId,
            Long initiatorId,
            Long initiatorDeptId
    ) {
        if (flowNodesJson == null || flowNodesJson.isBlank()) {
            return new ExecutionStep("end", "流程办结", "END", true, List.of(), List.of());
        }

        try {
            JsonNode rootNode = objectMapper.readTree(flowNodesJson);
            JsonNode formData = formDataJson != null && !formDataJson.isBlank()
                    ? objectMapper.readTree(formDataJson)
                    : objectMapper.createObjectNode();

            // 1. 定位当前节点或从根节点开始
            JsonNode targetNode = null;
            if (currentNodeId == null || currentNodeId.isBlank() || "root".equals(currentNodeId)) {
                targetNode = rootNode.get("next");
            } else {
                JsonNode current = findNodeById(rootNode, currentNodeId);
                if (current == null) throw new BizException("WORKFLOW_NODE_UNKNOWN", "当前审批节点不在原始流程定义中");
                targetNode = current.get("next");
            }

            List<AssigneeInfo> ccList = new ArrayList<>();

            // 2. 循环处理非审批节点（如条件分支、抄送节点），直到遇到待办审批节点或办结节点
            while (targetNode != null && !targetNode.isNull()) {
                String type = targetNode.path("type").asText("END");

                if ("CONDITION_BRANCH".equals(type)) {
                    targetNode = evaluateBranch(targetNode, formData);
                    continue;
                }

                if ("CC".equals(type)) {
                    // 记录抄送人
                    List<AssigneeInfo> resolvedCc = resolveAssignees(targetNode, tenantId, initiatorId, initiatorDeptId);
                    ccList.addAll(resolvedCc);
                    targetNode = targetNode.get("next");
                    continue;
                }

                if ("APPROVER".equals(type)) {
                    String nodeId = targetNode.path("id").asText("node_" + System.currentTimeMillis());
                    String nodeName = targetNode.path("name").asText("审批节点");
                    List<AssigneeInfo> assignees = resolveAssignees(targetNode, tenantId, initiatorId, initiatorDeptId);
                    return new ExecutionStep(nodeId, nodeName, "APPROVER", false, assignees, ccList);
                }

                if ("END".equals(type)) {
                    String nodeId = targetNode.path("id").asText("end");
                    String nodeName = targetNode.path("name").asText("流程办结");
                    return new ExecutionStep(nodeId, nodeName, "END", true, List.of(), ccList);
                }

                targetNode = targetNode.get("next");
            }

            return new ExecutionStep("end", "流程办结", "END", true, List.of(), ccList);

        } catch (JsonProcessingException e) {
            throw new BizException("INVALID_WORKFLOW_JSON", "流程定义或表单 JSON 无法解析");
        }
    }

    private JsonNode evaluateBranch(JsonNode branchNode, JsonNode formData) {
        JsonNode branches = branchNode.get("branches");
        if (branches == null || !branches.isArray() || branches.isEmpty()) {
            return branchNode.get("next");
        }

        for (JsonNode branch : branches) {
            JsonNode condition = branch.get("condition");
            if (condition == null || condition.isNull()) {
                return branch.get("next");
            }

            String field = condition.path("field").asText("");
            String operator = condition.path("operator").asText("==");
            JsonNode expectedValueNode = condition.get("value");

            if (field.isBlank()) {
                return branch.get("next");
            }

            JsonNode actualValueNode = formData.get(field);
            if (matchCondition(actualValueNode, operator, expectedValueNode)) {
                return branch.get("next");
            }
        }

        // 默认取第一个分支的下一跳
        return branches.get(0).get("next");
    }

    private boolean matchCondition(JsonNode actual, String operator, JsonNode expected) {
        if (actual == null || actual.isNull()) {
            return false;
        }

        if (actual.isNumber() && expected != null && expected.isNumber()) {
            BigDecimal actNum = actual.decimalValue();
            BigDecimal expNum = expected.decimalValue();
            int cmp = actNum.compareTo(expNum);
            return switch (operator) {
                case ">" -> cmp > 0;
                case ">=" -> cmp >= 0;
                case "<" -> cmp < 0;
                case "<=" -> cmp <= 0;
                case "!=" -> cmp != 0;
                default -> cmp == 0;
            };
        }

        String actStr = actual.asText("");
        String expStr = expected != null ? expected.asText("") : "";

        return switch (operator) {
            case "!=" -> !actStr.equals(expStr);
            case "contains" -> actStr.contains(expStr);
            default -> actStr.equals(expStr);
        };
    }

    private JsonNode findNodeById(JsonNode node, String id) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (id.equals(node.path("id").asText(""))) {
            return node;
        }

        if (node.has("branches") && node.get("branches").isArray()) {
            for (JsonNode branch : node.get("branches")) {
                JsonNode found = findNodeById(branch.get("next"), id);
                if (found != null) {
                    return found;
                }
            }
        }

        return findNodeById(node.get("next"), id);
    }

    List<AssigneeInfo> approversAt(String snapshot, String nodeId, Long tenant, Long initiator, Long department) {
        try {
            var node = findNodeById(objectMapper.readTree(snapshot), nodeId);
            if (node == null || !"APPROVER".equals(node.path("type").asText())) {
                throw new BizException("WORKFLOW_NODE_UNKNOWN", "原流程定义中找不到该审批节点");
            }
            return resolveAssignees(node, tenant, initiator, department);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BizException("INVALID_WORKFLOW_JSON", "原流程定义无法解析");
        }
    }

    public List<AssigneeInfo> resolveAssignees(JsonNode node, Long tenantId, Long initiatorId, Long initiatorDeptId) {
        String assigneeType = node.path("assigneeType").asText("USER");
        String assigneeValue = node.path("assigneeValue").asText("");
        List<AssigneeInfo> results = switch (assigneeType) {
            case "DEPT_LEADER" -> initiatorDeptId == null || initiatorDeptId <= 0
                    ? List.of() : participants.findDepartmentLeader(tenantId, initiatorDeptId);
            case "ROLE" -> participants.findRoleMembers(tenantId, configuredId(assigneeValue));
            case "USER" -> participants.findUser(tenantId, configuredId(assigneeValue));
            default -> throw new BizException("INVALID_ASSIGNEE_TYPE", "流程节点的办理人类型无效");
        };
        if (results.isEmpty()) {
            throw new BizException("WORKFLOW_ASSIGNEE_MISSING", "流程节点没有可用的办理人，请检查人员、角色或部门负责人配置");
        }
        return results;
    }

    private long configuredId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new BizException("INVALID_ASSIGNEE_ID", "办理人或角色编号必须为正整数");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new BizException("INVALID_ASSIGNEE_ID", "流程节点缺少有效的办理人或角色编号");
        }
    }
}
