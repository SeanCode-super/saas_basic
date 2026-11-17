package com.saasbasics.platform.modules.workflow.service;

import com.saasbasics.platform.common.tenant.TenantIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.modules.workflow.entity.ProcessDefinitionEntity;
import com.saasbasics.platform.modules.workflow.mapper.ProcessDefinitionMapper;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 工业级 Flowable 7 运行时桥接服务。
 * 封装 Flowable ProcessEngine 核心运行时、任务与审计服务，
 * 提供真正的 OMG BPMN 2.0 部署、执行、审批流转与全链路历史审计能力。
 */
@Service
public class FlowableProcessBridgeService {

    private static final Logger log = LoggerFactory.getLogger(FlowableProcessBridgeService.class);

    private final ObjectProvider<RepositoryService> repositoryServiceProvider;
    private final ObjectProvider<RuntimeService> runtimeServiceProvider;
    private final ObjectProvider<TaskService> taskServiceProvider;
    private final ObjectProvider<HistoryService> historyServiceProvider;
    private final FlowableBpmnModelService bpmnModelService;
    private final ObjectProvider<ProcessDefinitionMapper> definitionMapperProvider;

    public FlowableProcessBridgeService(
            ObjectProvider<RepositoryService> repositoryServiceProvider,
            ObjectProvider<RuntimeService> runtimeServiceProvider,
            ObjectProvider<TaskService> taskServiceProvider,
            ObjectProvider<HistoryService> historyServiceProvider,
            FlowableBpmnModelService bpmnModelService,
            ObjectProvider<ProcessDefinitionMapper> definitionMapperProvider
    ) {
        this.repositoryServiceProvider = repositoryServiceProvider;
        this.runtimeServiceProvider = runtimeServiceProvider;
        this.taskServiceProvider = taskServiceProvider;
        this.historyServiceProvider = historyServiceProvider;
        this.bpmnModelService = bpmnModelService;
        this.definitionMapperProvider = definitionMapperProvider;
    }

    /**
     * 系统启动时自动将平台已有的审批定义热加载并部署至 Flowable 工业引擎
     */
    @TenantIgnore(reason = "Auto deploy process definitions across all tenants on startup")
    @EventListener(ApplicationReadyEvent.class)
    public void autoDeployExistingDefinitions() {
        RepositoryService repo = repositoryServiceProvider.getIfAvailable();
        ProcessDefinitionMapper mapper = definitionMapperProvider.getIfAvailable();
        if (repo == null || mapper == null) {
            return;
        }

        try {
            List<ProcessDefinitionEntity> list = mapper.selectList(
                    new LambdaQueryWrapper<ProcessDefinitionEntity>()
                            .eq(ProcessDefinitionEntity::getDeleted, 0)
                            .eq(ProcessDefinitionEntity::getStatus, "PUBLISHED")
            );

            for (ProcessDefinitionEntity def : list) {
                long count = repo.createProcessDefinitionQuery()
                        .processDefinitionKey(def.getProcessCode())
                        .processDefinitionTenantId(String.valueOf(def.getTenantId()))
                        .count();

                if (count == 0) {
                    String xml = def.getBpmnXml();
                    if (xml == null || xml.isBlank()) {
                        xml = bpmnModelService.buildBpmn20XmlFromJson(def.getProcessCode(), def.getProcessName(), def.getFlowNodesJson());
                        def.setBpmnXml(xml);
                    }

                    String depId = deployBpmnXml(def.getProcessCode(), def.getProcessName(), def.getCategory(), def.getTenantId(), xml);
                    if (depId != null) {
                        def.setFlowableDeploymentId(depId);
                        mapper.updateById(def);
                        log.info("Auto-deployed existing process definition [{}] to Flowable 7, deploymentId: {}", def.getProcessCode(), depId);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Flowable auto-deploy existing definitions warning: {}", ex.getMessage());
        }
    }

    public boolean isFlowableReady() {
        return repositoryServiceProvider.getIfAvailable() != null;
    }

    /**
     * 部署 BPMN 2.0 流程模型至 Flowable 工业级引擎
     */
    public String deployModel(String processCode, String processName, String category, Long tenantId, BpmnModel model) {
        RepositoryService repo = repositoryServiceProvider.getIfAvailable();
        if (repo == null) {
            log.warn("Flowable RepositoryService is not active, skipping repository deploy");
            return "embedded-mock-dep";
        }

        try {
            Deployment deployment = repo.createDeployment()
                    .name(processName)
                    .category(category != null ? category : "GENERAL")
                    .key(processCode)
                    .tenantId(String.valueOf(tenantId))
                    .addBpmnModel(processCode + ".bpmn20.xml", model)
                    .deploy();

            log.info("Successfully deployed BPMN 2.0 process [{}] to Flowable, deploymentId: {}", processCode, deployment.getId());
            return deployment.getId();
        } catch (Exception ex) {
            log.error("Failed to deploy BPMN 2.0 to Flowable: {}", ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * 直接以 BPMN 2.0 XML 字符串部署至 Flowable
     */
    public String deployBpmnXml(String processCode, String processName, String category, Long tenantId, String bpmnXml) {
        RepositoryService repo = repositoryServiceProvider.getIfAvailable();
        if (repo == null) {
            return "embedded-mock-dep";
        }

        try {
            Deployment deployment = repo.createDeployment()
                    .name(processName)
                    .category(category != null ? category : "GENERAL")
                    .key(processCode)
                    .tenantId(String.valueOf(tenantId))
                    .addString(processCode + ".bpmn20.xml", bpmnXml)
                    .deploy();

            log.info("Successfully deployed raw BPMN 2.0 XML [{}] to Flowable, deploymentId: {}", processCode, deployment.getId());
            return deployment.getId();
        } catch (Exception ex) {
            log.error("Failed to deploy BPMN XML: {}", ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * 在 Flowable 工业级引擎中发起流程实例
     */
    public ProcessInstance startProcessInstance(String processCode, String businessKey, Map<String, Object> variables, Long tenantId) {
        RuntimeService runtime = runtimeServiceProvider.getIfAvailable();
        if (runtime == null) {
            log.warn("Flowable RuntimeService not available");
            return null;
        }

        try {
            Map<String, Object> vars = variables != null ? new HashMap<>(variables) : new HashMap<>();
            vars.put("businessKey", businessKey);
            vars.put("tenantId", tenantId);

            ProcessInstance instance = runtime.startProcessInstanceByKeyAndTenantId(
                    processCode,
                    businessKey,
                    vars,
                    String.valueOf(tenantId)
            );
            log.info("Started Flowable ProcessInstance [{}] for businessKey [{}]", instance.getId(), businessKey);
            return instance;
        } catch (Exception ex) {
            log.error("Failed to start Flowable ProcessInstance: {}", ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * 获取 Flowable 当前活动的任务
     */
    public List<Task> getActiveTasks(String procInstId) {
        TaskService taskService = taskServiceProvider.getIfAvailable();
        if (taskService == null || procInstId == null || procInstId.isBlank()) {
            return List.of();
        }
        try {
            return taskService.createTaskQuery().processInstanceId(procInstId).list();
        } catch (Exception ex) {
            log.warn("Failed to query Flowable active tasks: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * 完成 Flowable 任务
     */
    public boolean completeTask(String flowableTaskId, Map<String, Object> variables, String comment, Long userId) {
        TaskService taskService = taskServiceProvider.getIfAvailable();
        if (taskService == null || flowableTaskId == null || flowableTaskId.isBlank()) {
            return false;
        }

        try {
            Task task = taskService.createTaskQuery().taskId(flowableTaskId).singleResult();
            if (task != null) {
                if (comment != null && !comment.isBlank()) {
                    taskService.addComment(flowableTaskId, task.getProcessInstanceId(), "OPINION", comment);
                }
                taskService.complete(flowableTaskId, variables != null ? variables : Map.of());
                log.info("Flowable task [{}] completed successfully", flowableTaskId);
                return true;
            }
        } catch (Exception ex) {
            log.error("Failed to complete Flowable task [{}]: {}", flowableTaskId, ex.getMessage(), ex);
        }
        return false;
    }

    /**
     * 转办 / 委派 Flowable 任务
     */
    public boolean transferTask(String flowableTaskId, Long targetUserId, String targetUserName) {
        TaskService taskService = taskServiceProvider.getIfAvailable();
        if (taskService == null || flowableTaskId == null || flowableTaskId.isBlank()) {
            return false;
        }

        try {
            taskService.setAssignee(flowableTaskId, String.valueOf(targetUserId));
            taskService.addComment(flowableTaskId, null, "TRANSFER", "转办给: " + targetUserName);
            return true;
        } catch (Exception ex) {
            log.error("Failed to transfer Flowable task [{}]: {}", flowableTaskId, ex.getMessage());
            return false;
        }
    }

    /**
     * 终止 / 驳回 Flowable 流程实例
     */
    public boolean terminateProcessInstance(String flowableProcInstId, String reason) {
        RuntimeService runtime = runtimeServiceProvider.getIfAvailable();
        if (runtime == null || flowableProcInstId == null || flowableProcInstId.isBlank()) {
            return false;
        }

        try {
            runtime.deleteProcessInstance(flowableProcInstId, reason != null ? reason : "审批驳回或撤销终止");
            log.info("Terminated Flowable ProcessInstance [{}]", flowableProcInstId);
            return true;
        } catch (Exception ex) {
            log.warn("Could not delete running process instance (might already be completed): {}", ex.getMessage());
            return false;
        }
    }

    /**
     * 查询 Flowable 工业级全链路执行轨迹（Activity 节点、耗时、状态、审批人）
     */
    public List<Map<String, Object>> getHistoricActivityTrace(String flowableProcInstId) {
        HistoryService history = historyServiceProvider.getIfAvailable();
        if (history == null || flowableProcInstId == null || flowableProcInstId.isBlank()) {
            return List.of();
        }

        try {
            List<HistoricActivityInstance> activities = history.createHistoricActivityInstanceQuery()
                    .processInstanceId(flowableProcInstId)
                    .orderByHistoricActivityInstanceStartTime().asc()
                    .list();

            List<Map<String, Object>> trace = new ArrayList<>();
            for (HistoricActivityInstance act : activities) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("activityId", act.getActivityId());
                item.put("activityName", act.getActivityName());
                item.put("activityType", act.getActivityType());
                item.put("assignee", act.getAssignee());
                item.put("startTime", act.getStartTime());
                item.put("endTime", act.getEndTime());
                item.put("durationInMillis", act.getDurationInMillis());
                item.put("completed", act.getEndTime() != null);
                trace.add(item);
            }
            return trace;
        } catch (Exception ex) {
            log.warn("Failed to get Flowable historic activity trace: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * 获取当前活动的 BPMN 节点 ID 集合
     */
    public List<String> getActiveActivityIds(String flowableProcInstId) {
        RuntimeService runtime = runtimeServiceProvider.getIfAvailable();
        if (runtime == null || flowableProcInstId == null || flowableProcInstId.isBlank()) {
            return List.of();
        }

        try {
            return runtime.getActiveActivityIds(flowableProcInstId);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
