package com.saasbasics.platform.modules.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public WorkflowEngine(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record AssigneeInfo(Long userId, String userName) {}

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
                if (current != null) {
                    targetNode = current.get("next");
                }
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

        } catch (Exception e) {
            log.error("Failed to advance workflow execution", e);
            return new ExecutionStep("end", "自动办结", "END", true, List.of(), List.of());
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

    public List<AssigneeInfo> resolveAssignees(JsonNode node, Long tenantId, Long initiatorId, Long initiatorDeptId) {
        String assigneeType = node.path("assigneeType").asText("USER");
        String assigneeValue = node.path("assigneeValue").asText("");
        List<AssigneeInfo> results = new ArrayList<>();

        if ("DEPT_LEADER".equals(assigneeType)) {
            // 1. 查询发起人归属部门的 leader_user_id
            if (initiatorDeptId != null && initiatorDeptId > 0) {
                try {
                    String sql = "SELECT u.id, u.nickname FROM iam_user u " +
                            "INNER JOIN iam_department d ON d.leader_user_id = u.id " +
                            "WHERE d.id = ? AND d.tenant_id = ? AND d.deleted = 0 AND u.deleted = 0";
                    List<AssigneeInfo> list = jdbcTemplate.query(sql, (rs, rowNum) ->
                            new AssigneeInfo(rs.getLong("id"), rs.getString("nickname")), initiatorDeptId, tenantId);
                    if (!list.isEmpty()) {
                        results.addAll(list);
                    }
                } catch (Exception ex) {
                    log.warn("Failed to query dept leader", ex);
                }
            }
        } else if ("ROLE".equals(assigneeType)) {
            // 2. 根据角色查询绑定的所有有效用户
            if (!assigneeValue.isBlank()) {
                try {
                    Long roleId = Long.parseLong(assigneeValue);
                    String sql = "SELECT u.id, u.nickname FROM iam_user u " +
                            "INNER JOIN iam_user_role ur ON ur.user_id = u.id " +
                            "WHERE ur.role_id = ? AND ur.tenant_id = ? AND u.deleted = 0 AND u.status = 'ENABLED'";
                    List<AssigneeInfo> list = jdbcTemplate.query(sql, (rs, rowNum) ->
                            new AssigneeInfo(rs.getLong("id"), rs.getString("nickname")), roleId, tenantId);
                    results.addAll(list);
                } catch (Exception ex) {
                    log.warn("Failed to query role assignees", ex);
                }
            }
        } else if ("USER".equals(assigneeType)) {
            // 3. 指定用户
            if (!assigneeValue.isBlank()) {
                try {
                    Long userId = Long.parseLong(assigneeValue);
                    String sql = "SELECT id, nickname FROM iam_user WHERE id = ? AND tenant_id = ? AND deleted = 0";
                    List<AssigneeInfo> list = jdbcTemplate.query(sql, (rs, rowNum) ->
                            new AssigneeInfo(rs.getLong("id"), rs.getString("nickname")), userId, tenantId);
                    results.addAll(list);
                } catch (Exception ex) {
                    log.warn("Failed to query user assignee", ex);
                }
            }
        }

        // 防御性保底机制：如果没查到对应的人（例如部门暂未设负责人），自动分配给系统管理员或发起人，确保审批绝不卡死
        if (results.isEmpty()) {
            try {
                String sql = "SELECT id, nickname FROM iam_user WHERE tenant_id = ? AND deleted = 0 AND status = 'ENABLED' ORDER BY id ASC LIMIT 1";
                List<AssigneeInfo> fallback = jdbcTemplate.query(sql, (rs, rowNum) ->
                        new AssigneeInfo(rs.getLong("id"), rs.getString("nickname")), tenantId);
                if (!fallback.isEmpty()) {
                    results.addAll(fallback);
                } else {
                    results.add(new AssigneeInfo(initiatorId, "业务审批员"));
                }
            } catch (Exception ex) {
                results.add(new AssigneeInfo(initiatorId, "业务审批员"));
            }
        }

        return results;
    }
}
