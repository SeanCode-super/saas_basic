package com.saasbasics.platform.modules.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.*;
import org.flowable.bpmn.model.Process;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工业级 OMG BPMN 2.0 规范模型与 XML 转换服务。
 * 负责将企业前端可视化流程树双向转换为 OMG 国际标准 BPMN 2.0 XML 结构，
 * 并支持自动生成标准 Diagram Interchange (DI) 坐标，使产物兼容 Camunda / bpmn-js / Flowable 官方建模器。
 */
@Service
public class FlowableBpmnModelService {

    private static final Logger log = LoggerFactory.getLogger(FlowableBpmnModelService.class);
    private final ObjectMapper objectMapper;
    private final BpmnXMLConverter bpmnXMLConverter = new BpmnXMLConverter();

    public FlowableBpmnModelService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将前端树状 JSON 转换为 OMG 标准 BpmnModel
     */
    public BpmnModel buildBpmnModel(String processCode, String processName, String flowNodesJson) {
        BpmnModel model = new BpmnModel();
        model.setTargetNamespace("http://www.flowable.org/processdef");

        Process process = new Process();
        process.setId(processCode != null && !processCode.isBlank() ? processCode : "Process_" + System.currentTimeMillis());
        process.setName(processName != null && !processName.isBlank() ? processName : "业务审批流程");
        process.setExecutable(true);

        double startX = 100;
        double startY = 160;

        // 1. 开始事件
        StartEvent startEvent = new StartEvent();
        startEvent.setId("startEvent");
        startEvent.setName("发起流程");
        process.addFlowElement(startEvent);
        model.addGraphicInfo("startEvent", new GraphicInfo(startX, startY + 22, 36, 36));

        double currentX = startX + 120;
        String prevElementId = "startEvent";
        AtomicInteger seqIndex = new AtomicInteger(1);

        try {
            if (flowNodesJson != null && !flowNodesJson.isBlank()) {
                JsonNode root = objectMapper.readTree(flowNodesJson);
                JsonNode cursor = root.get("next");

                while (cursor != null && !cursor.isNull()) {
                    String nodeType = cursor.path("type").asText("APPROVER");
                    String nodeId = cursor.path("id").asText("node_" + seqIndex.get());
                    String nodeName = cursor.path("name").asText("审批环节");

                    if ("CONDITION_BRANCH".equals(nodeType)) {
                        // 分支条件网关 (Exclusive Gateway)
                        ExclusiveGateway gateway = new ExclusiveGateway();
                        gateway.setId(nodeId);
                        gateway.setName(nodeName);
                        process.addFlowElement(gateway);
                        model.addGraphicInfo(nodeId, new GraphicInfo(currentX, startY + 15, 50, 50));

                        // 连线：上一节点 -> 网关
                        connect(process, model, prevElementId, nodeId, "flow_" + seqIndex.getAndIncrement(), null);
                        prevElementId = nodeId;
                        currentX += 130;

                        // 处理分支
                        JsonNode branches = cursor.get("branches");
                        if (branches != null && branches.isArray() && !branches.isEmpty()) {
                            double branchY = startY - 100;
                            for (JsonNode branch : branches) {
                                String branchNextId = "endEvent";
                                JsonNode nextNode = branch.get("next");
                                if (nextNode != null && !nextNode.isNull()) {
                                    String subId = nextNode.path("id").asText("sub_node_" + seqIndex.get());
                                    String subName = nextNode.path("name").asText("审批人");
                                    UserTask subTask = new UserTask();
                                    subTask.setId(subId);
                                    subTask.setName(subName);
                                    configureAssignee(subTask, nextNode);
                                    process.addFlowElement(subTask);
                                    model.addGraphicInfo(subId, new GraphicInfo(currentX, branchY, 80, 100));

                                    // 条件表达式
                                    String conditionExpr = buildConditionExpression(branch.get("condition"));
                                    connect(process, model, nodeId, subId, "flow_" + seqIndex.getAndIncrement(), conditionExpr);
                                    branchNextId = subId;
                                }

                                branchY += 120;
                            }
                        }

                        cursor = cursor.get("next");
                        continue;
                    }

                    if ("APPROVER".equals(nodeType)) {
                        UserTask userTask = new UserTask();
                        userTask.setId(nodeId);
                        userTask.setName(nodeName);
                        configureAssignee(userTask, cursor);
                        process.addFlowElement(userTask);
                        model.addGraphicInfo(nodeId, new GraphicInfo(currentX, startY, 80, 100));

                        connect(process, model, prevElementId, nodeId, "flow_" + seqIndex.getAndIncrement(), null);
                        prevElementId = nodeId;
                        currentX += 160;
                    } else if ("CC".equals(nodeType)) {
                        // 抄送通知节点 (ServiceTask / UserTask 通知)
                        UserTask ccTask = new UserTask();
                        ccTask.setId(nodeId);
                        ccTask.setName("【抄送】" + nodeName);
                        configureAssignee(ccTask, cursor);
                        process.addFlowElement(ccTask);
                        model.addGraphicInfo(nodeId, new GraphicInfo(currentX, startY, 80, 100));

                        connect(process, model, prevElementId, nodeId, "flow_" + seqIndex.getAndIncrement(), null);
                        prevElementId = nodeId;
                        currentX += 160;
                    } else if ("END".equals(nodeType)) {
                        break;
                    }

                    cursor = cursor.get("next");
                }
            }
        } catch (Exception ex) {
            log.error("Failed to parse flow nodes json into BPMN model", ex);
        }

        // 3. 结束事件
        EndEvent endEvent = new EndEvent();
        endEvent.setId("endEvent");
        endEvent.setName("流程办结");
        process.addFlowElement(endEvent);
        model.addGraphicInfo("endEvent", new GraphicInfo(currentX, startY + 22, 36, 36));

        connect(process, model, prevElementId, "endEvent", "flow_" + seqIndex.getAndIncrement(), null);

        model.addProcess(process);
        return model;
    }

    /**
     * 将 BpmnModel 导出为标准 OMG 规范 BPMN 2.0 XML
     */
    public String convertToBpmn20Xml(BpmnModel model) {
        byte[] xmlBytes = bpmnXMLConverter.convertToXML(model, StandardCharsets.UTF_8.name());
        return new String(xmlBytes, StandardCharsets.UTF_8);
    }

    /**
     * 将前端可视化 JSON 直接转为 OMG BPMN 2.0 XML
     */
    public String buildBpmn20XmlFromJson(String processCode, String processName, String flowNodesJson) {
        BpmnModel model = buildBpmnModel(processCode, processName, flowNodesJson);
        return convertToBpmn20Xml(model);
    }

    /**
     * 将 OMG BPMN 2.0 XML 解析为 BpmnModel
     */
    public BpmnModel parseBpmn20Xml(String xml) {
        return bpmnXMLConverter.convertToBpmnModel(
                () -> new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                true,
                true,
                StandardCharsets.UTF_8.name()
        );
    }

    private void configureAssignee(UserTask task, JsonNode node) {
        String assigneeType = node.path("assigneeType").asText("USER");
        String assigneeValue = node.path("assigneeValue").asText("");

        if ("DEPT_LEADER".equals(assigneeType)) {
            task.setAssignee("${initiatorDeptLeader}");
        } else if ("ROLE".equals(assigneeType)) {
            task.setCandidateGroups(List.of(assigneeValue.isBlank() ? "ROLE_APPROVER" : "ROLE_" + assigneeValue));
        } else if ("USER".equals(assigneeType)) {
            task.setAssignee(assigneeValue.isBlank() ? "${initiator}" : assigneeValue);
        } else {
            task.setAssignee("${assignee}");
        }
    }

    private String buildConditionExpression(JsonNode condition) {
        if (condition == null || condition.isNull()) {
            return null;
        }
        String field = condition.path("field").asText("");
        String operator = condition.path("operator").asText("==");
        String val = condition.path("value").asText("");

        if (field.isBlank()) {
            return null;
        }

        // 生成标准 UEL 表达式，如 ${amount > 5000}
        if (">".equals(operator) || ">=".equals(operator) || "<".equals(operator) || "<=".equals(operator)) {
            return String.format("${%s %s %s}", field, operator, val.isBlank() ? "0" : val);
        }
        return String.format("${%s %s '%s'}", field, "==".equals(operator) ? "==" : "!=", val);
    }

    private void connect(Process process, BpmnModel model, String fromId, String toId, String flowId, String condition) {
        SequenceFlow flow = new SequenceFlow(fromId, toId);
        flow.setId(flowId);
        if (condition != null && !condition.isBlank()) {
            flow.setConditionExpression(condition);
        }
        process.addFlowElement(flow);

        // 添加连线路径坐标
        GraphicInfo fromG = model.getGraphicInfo(fromId);
        GraphicInfo toG = model.getGraphicInfo(toId);
        if (fromG != null && toG != null) {
            List<GraphicInfo> waypoints = new ArrayList<>();
            waypoints.add(new GraphicInfo(fromG.getX() + fromG.getWidth(), fromG.getY() + fromG.getHeight() / 2.0));
            waypoints.add(new GraphicInfo(toG.getX(), toG.getY() + toG.getHeight() / 2.0));
            model.addFlowGraphicInfoList(flowId, waypoints);
        }
    }
}
