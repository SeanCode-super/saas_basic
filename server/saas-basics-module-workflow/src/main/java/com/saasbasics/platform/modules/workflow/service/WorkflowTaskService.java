package com.saasbasics.platform.modules.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.idempotent.Idempotent;
import com.saasbasics.platform.modules.workflow.dto.TaskHandleRequest;
import com.saasbasics.platform.modules.workflow.dto.WorkflowTaskResponse;
import com.saasbasics.platform.modules.workflow.entity.ProcessDefinitionEntity;
import com.saasbasics.platform.modules.workflow.entity.ProcessInstanceEntity;
import com.saasbasics.platform.modules.workflow.entity.WorkflowTaskEntity;
import com.saasbasics.platform.modules.workflow.entity.WorkflowTaskLogEntity;
import com.saasbasics.platform.modules.workflow.mapper.ProcessDefinitionMapper;
import com.saasbasics.platform.modules.workflow.mapper.ProcessInstanceMapper;
import com.saasbasics.platform.modules.workflow.mapper.WorkflowTaskLogMapper;
import com.saasbasics.platform.modules.workflow.mapper.WorkflowTaskMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WorkflowTaskService {

    private final WorkflowTaskMapper taskMapper;
    private final ProcessInstanceMapper instanceMapper;
    private final ProcessDefinitionMapper definitionMapper;
    private final WorkflowTaskLogMapper taskLogMapper;
    private final WorkflowEngine workflowEngine;

    public WorkflowTaskService(
            WorkflowTaskMapper taskMapper,
            ProcessInstanceMapper instanceMapper,
            ProcessDefinitionMapper definitionMapper,
            WorkflowTaskLogMapper taskLogMapper,
            WorkflowEngine workflowEngine
    ) {
        this.taskMapper = taskMapper;
        this.instanceMapper = instanceMapper;
        this.definitionMapper = definitionMapper;
        this.taskLogMapper = taskLogMapper;
        this.workflowEngine = workflowEngine;
    }

    public List<WorkflowTaskResponse> listMyPendingTasks() {
        Long tenantId = getTenantId();
        Long userId = getUserId();

        List<WorkflowTaskEntity> tasks = taskMapper.selectList(new LambdaQueryWrapper<WorkflowTaskEntity>()
                .eq(WorkflowTaskEntity::getTenantId, tenantId)
                .eq(WorkflowTaskEntity::getAssigneeId, userId)
                .eq(WorkflowTaskEntity::getStatus, "PENDING")
                .eq(WorkflowTaskEntity::getNodeType, "APPROVER")
                .eq(WorkflowTaskEntity::getDeleted, 0)
                .orderByDesc(WorkflowTaskEntity::getId));

        return enrichTasks(tasks);
    }

    public List<WorkflowTaskResponse> listMyCompletedTasks() {
        Long tenantId = getTenantId();
        Long userId = getUserId();

        List<WorkflowTaskEntity> tasks = taskMapper.selectList(new LambdaQueryWrapper<WorkflowTaskEntity>()
                .eq(WorkflowTaskEntity::getTenantId, tenantId)
                .eq(WorkflowTaskEntity::getAssigneeId, userId)
                .in(WorkflowTaskEntity::getStatus, "COMPLETED", "REJECTED", "TRANSFERRED")
                .eq(WorkflowTaskEntity::getNodeType, "APPROVER")
                .eq(WorkflowTaskEntity::getDeleted, 0)
                .orderByDesc(WorkflowTaskEntity::getFinishTime));

        return enrichTasks(tasks);
    }

    public List<WorkflowTaskResponse> listCcTasks() {
        Long tenantId = getTenantId();
        Long userId = getUserId();

        List<WorkflowTaskEntity> tasks = taskMapper.selectList(new LambdaQueryWrapper<WorkflowTaskEntity>()
                .eq(WorkflowTaskEntity::getTenantId, tenantId)
                .eq(WorkflowTaskEntity::getAssigneeId, userId)
                .eq(WorkflowTaskEntity::getNodeType, "CC")
                .eq(WorkflowTaskEntity::getDeleted, 0)
                .orderByDesc(WorkflowTaskEntity::getId));

        return enrichTasks(tasks);
    }

    @Idempotent(message = "审批单正在处理中，请勿重复操作", expireMillis = 3000)
    @Transactional
    public void handleTask(Long taskId, TaskHandleRequest request) {
        Long tenantId = getTenantId();
        Long userId = getUserId();
        String userName = getUserName();

        WorkflowTaskEntity task = taskMapper.selectById(taskId);
        if (task == null || task.getDeleted() != 0 || !task.getTenantId().equals(tenantId)) {
            throw new BizException("NOT_FOUND", "任务不存在或已删除");
        }
        if (!task.getAssigneeId().equals(userId)) {
            throw new BizException("PERMISSION_DENIED", "只有当前审批人本人可以处理该任务");
        }
        if (!"PENDING".equals(task.getStatus())) {
            throw new BizException("TASK_ALREADY_HANDLED", "该任务已处理，无法重复办理");
        }

        ProcessInstanceEntity instance = instanceMapper.selectById(task.getInstanceId());
        if (instance == null || !"RUNNING".equals(instance.getStatus())) {
            throw new BizException("INSTANCE_NOT_RUNNING", "审批单已不在进行中，无法操作");
        }

        String action = request.action() != null ? request.action().toUpperCase() : "AGREE";

        if ("REJECT".equals(action)) {
            // 1. 驳回审批
            task.setStatus("REJECTED");
            task.setAction("REJECT");
            task.setComment(request.comment());
            task.setFinishTime(LocalDateTime.now());
            task.setUpdatedBy(userId);
            taskMapper.updateById(task);

            instance.setStatus("REJECTED");
            instance.setEndTime(LocalDateTime.now());
            instance.setCurrentNodeName("已驳回");
            instance.setUpdatedBy(userId);
            instanceMapper.updateById(instance);

            // 写入日志
            logAction(tenantId, instance.getId(), taskId, task.getNodeName(), userId, userName, "REJECT", "驳回申请",
                    StringUtils.hasText(request.comment()) ? request.comment() : "审批人驳回了该申请。");

        } else if ("TRANSFER".equals(action)) {
            // 2. 转交他人办理
            if (request.transferToUserId() == null || request.transferToUserId() <= 0) {
                throw new BizException("INVALID_PARAM", "转交操作必须指定转交对象用户ID");
            }

            task.setStatus("TRANSFERRED");
            task.setAction("TRANSFER");
            task.setComment(request.comment());
            task.setFinishTime(LocalDateTime.now());
            task.setUpdatedBy(userId);
            taskMapper.updateById(task);

            String targetName = StringUtils.hasText(request.transferToUserName()) ? request.transferToUserName() : "转办专员";

            // 创建新的待办
            WorkflowTaskEntity newTask = new WorkflowTaskEntity();
            newTask.setTenantId(tenantId);
            newTask.setInstanceId(instance.getId());
            newTask.setNodeId(task.getNodeId());
            newTask.setNodeName(task.getNodeName());
            newTask.setNodeType("APPROVER");
            newTask.setAssigneeId(request.transferToUserId());
            newTask.setAssigneeName(targetName);
            newTask.setStatus("PENDING");
            newTask.setCreatedBy(userId);
            newTask.setUpdatedBy(userId);
            taskMapper.insert(newTask);

            logAction(tenantId, instance.getId(), taskId, task.getNodeName(), userId, userName, "TRANSFER", "转交任务",
                    "转交给 " + targetName + " 办理。附言: " + (request.comment() != null ? request.comment() : "无"));

        } else if ("READ".equals(action)) {
            // 3. 抄送已读
            task.setStatus("COMPLETED");
            task.setAction("READ");
            task.setFinishTime(LocalDateTime.now());
            task.setUpdatedBy(userId);
            taskMapper.updateById(task);

            logAction(tenantId, instance.getId(), taskId, task.getNodeName(), userId, userName, "READ", "查阅抄送", "抄送知会人已查阅。");

        } else if ("ADD_SIGN".equals(action)) {
            // 4. 加签操作 (动态邀请协助审批)
            if (request.addSignUserId() == null || request.addSignUserId() <= 0) {
                throw new BizException("INVALID_PARAM", "加签操作必须指定被加签人用户ID");
            }
            String targetName = StringUtils.hasText(request.addSignUserName()) ? request.addSignUserName() : "加签协同人";

            WorkflowTaskEntity addSignTask = new WorkflowTaskEntity();
            addSignTask.setTenantId(tenantId);
            addSignTask.setInstanceId(instance.getId());
            addSignTask.setNodeId(task.getNodeId() + "_sign_" + System.currentTimeMillis());
            addSignTask.setNodeName(task.getNodeName() + " (加签协同)");
            addSignTask.setNodeType("APPROVER");
            addSignTask.setAssigneeId(request.addSignUserId());
            addSignTask.setAssigneeName(targetName);
            addSignTask.setStatus("PENDING");
            addSignTask.setCreatedBy(userId);
            addSignTask.setUpdatedBy(userId);
            taskMapper.insert(addSignTask);

            logAction(tenantId, instance.getId(), taskId, task.getNodeName(), userId, userName, "ADD_SIGN", "加签协同",
                    "邀请 " + targetName + " 协助审批。附言: " + (request.comment() != null ? request.comment() : "无"));

        } else if ("ROLLBACK_PREVIOUS".equals(action)) {
            // 5. 退回上一步
            task.setStatus("ROLLBACK");
            task.setAction("ROLLBACK_PREVIOUS");
            task.setComment(request.comment());
            task.setFinishTime(LocalDateTime.now());
            task.setUpdatedBy(userId);
            taskMapper.updateById(task);

            WorkflowTaskEntity lastTask = taskMapper.selectOne(new LambdaQueryWrapper<WorkflowTaskEntity>()
                    .eq(WorkflowTaskEntity::getInstanceId, instance.getId())
                    .eq(WorkflowTaskEntity::getStatus, "COMPLETED")
                    .eq(WorkflowTaskEntity::getNodeType, "APPROVER")
                    .orderByDesc(WorkflowTaskEntity::getFinishTime)
                    .last("LIMIT 1"));

            Long prevUserId = (lastTask != null) ? lastTask.getAssigneeId() : instance.getInitiatorId();
            String prevUserName = (lastTask != null) ? lastTask.getAssigneeName() : instance.getInitiatorName();
            String prevNodeName = (lastTask != null) ? lastTask.getNodeName() : "申请人重新修改";

            WorkflowTaskEntity rollbackTask = new WorkflowTaskEntity();
            rollbackTask.setTenantId(tenantId);
            rollbackTask.setInstanceId(instance.getId());
            rollbackTask.setNodeId("rollback_" + System.currentTimeMillis());
            rollbackTask.setNodeName(prevNodeName);
            rollbackTask.setNodeType("APPROVER");
            rollbackTask.setAssigneeId(prevUserId);
            rollbackTask.setAssigneeName(prevUserName);
            rollbackTask.setStatus("PENDING");
            rollbackTask.setCreatedBy(userId);
            rollbackTask.setUpdatedBy(userId);
            taskMapper.insert(rollbackTask);

            instance.setCurrentNodeName("退回: " + prevNodeName);
            instance.setUpdatedBy(userId);
            instanceMapper.updateById(instance);

            logAction(tenantId, instance.getId(), taskId, task.getNodeName(), userId, userName, "ROLLBACK_PREVIOUS", "退回上一环节",
                    "退回至 " + prevUserName + "。退回原因: " + (request.comment() != null ? request.comment() : "无"));

        } else if ("ROLLBACK_INITIATOR".equals(action)) {
            // 6. 退回发起人
            task.setStatus("ROLLBACK");
            task.setAction("ROLLBACK_INITIATOR");
            task.setComment(request.comment());
            task.setFinishTime(LocalDateTime.now());
            task.setUpdatedBy(userId);
            taskMapper.updateById(task);

            WorkflowTaskEntity initTask = new WorkflowTaskEntity();
            initTask.setTenantId(tenantId);
            initTask.setInstanceId(instance.getId());
            initTask.setNodeId("rollback_starter");
            initTask.setNodeName("申请人重新修改");
            initTask.setNodeType("APPROVER");
            initTask.setAssigneeId(instance.getInitiatorId());
            initTask.setAssigneeName(instance.getInitiatorName());
            initTask.setStatus("PENDING");
            initTask.setCreatedBy(userId);
            initTask.setUpdatedBy(userId);
            taskMapper.insert(initTask);

            instance.setCurrentNodeName("退回申请人");
            instance.setUpdatedBy(userId);
            instanceMapper.updateById(instance);

            logAction(tenantId, instance.getId(), taskId, task.getNodeName(), userId, userName, "ROLLBACK_INITIATOR", "退回发起人",
                    "单据退回给申请人重新核准。原因: " + (request.comment() != null ? request.comment() : "无"));

        } else {
            // 4. 同意审批 (默认)
            task.setStatus("COMPLETED");
            task.setAction("AGREE");
            task.setComment(request.comment());
            task.setFinishTime(LocalDateTime.now());
            task.setUpdatedBy(userId);
            taskMapper.updateById(task);

            logAction(tenantId, instance.getId(), taskId, task.getNodeName(), userId, userName, "AGREE", "同意审批",
                    StringUtils.hasText(request.comment()) ? request.comment() : "审核通过。");

            // 获取流程定义，推进下一步
            ProcessDefinitionEntity definition = definitionMapper.selectById(instance.getDefinitionId());
            if (definition != null) {
                WorkflowEngine.ExecutionStep nextStep = workflowEngine.advanceFlow(
                        definition.getFlowNodesJson(),
                        instance.getFormDataJson(),
                        task.getNodeId(),
                        tenantId,
                        instance.getInitiatorId(),
                        instance.getInitiatorDeptId()
                );

                if (nextStep.isEnd()) {
                    instance.setStatus("APPROVED");
                    instance.setEndTime(LocalDateTime.now());
                    instance.setCurrentNodeId("end");
                    instance.setCurrentNodeName("流程办结");
                    instance.setUpdatedBy(userId);
                    instanceMapper.updateById(instance);

                    logAction(tenantId, instance.getId(), null, "流程办结", userId, userName, "END", "办结归档", "所有审批环节均已顺利完成，单据生效。");
                } else {
                    instance.setCurrentNodeId(nextStep.nodeId());
                    instance.setCurrentNodeName(nextStep.nodeName());
                    instance.setUpdatedBy(userId);
                    instanceMapper.updateById(instance);

                    // 创建新审批任务
                    for (WorkflowEngine.AssigneeInfo assignee : nextStep.assignees()) {
                        WorkflowTaskEntity nextTask = new WorkflowTaskEntity();
                        nextTask.setTenantId(tenantId);
                        nextTask.setInstanceId(instance.getId());
                        nextTask.setNodeId(nextStep.nodeId());
                        nextTask.setNodeName(nextStep.nodeName());
                        nextTask.setNodeType("APPROVER");
                        nextTask.setAssigneeId(assignee.userId());
                        nextTask.setAssigneeName(assignee.userName());
                        nextTask.setStatus("PENDING");
                        nextTask.setCreatedBy(userId);
                        nextTask.setUpdatedBy(userId);
                        taskMapper.insert(nextTask);
                    }

                    // 创建可能伴随的抄送人
                    for (WorkflowEngine.AssigneeInfo cc : nextStep.ccList()) {
                        WorkflowTaskEntity ccTask = new WorkflowTaskEntity();
                        ccTask.setTenantId(tenantId);
                        ccTask.setInstanceId(instance.getId());
                        ccTask.setNodeId("cc_" + cc.userId());
                        ccTask.setNodeName("抄送知会");
                        ccTask.setNodeType("CC");
                        ccTask.setAssigneeId(cc.userId());
                        ccTask.setAssigneeName(cc.userName());
                        ccTask.setStatus("PENDING");
                        ccTask.setCreatedBy(userId);
                        ccTask.setUpdatedBy(userId);
                        taskMapper.insert(ccTask);
                    }
                }
            }
        }
    }

    private void logAction(Long tenantId, Long instanceId, Long taskId, String nodeName, Long operatorId,
                           String operatorName, String action, String actionLabel, String comment) {
        WorkflowTaskLogEntity logEntity = new WorkflowTaskLogEntity();
        logEntity.setTenantId(tenantId);
        logEntity.setInstanceId(instanceId);
        logEntity.setTaskId(taskId);
        logEntity.setNodeName(nodeName);
        logEntity.setOperatorId(operatorId);
        logEntity.setOperatorName(operatorName);
        logEntity.setAction(action);
        logEntity.setActionLabel(actionLabel);
        logEntity.setComment(comment);
        logEntity.setCreatedAt(LocalDateTime.now());
        taskLogMapper.insert(logEntity);
    }

    private List<WorkflowTaskResponse> enrichTasks(List<WorkflowTaskEntity> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }

        return tasks.stream().map(task -> {
            ProcessInstanceEntity inst = instanceMapper.selectById(task.getInstanceId());
            return new WorkflowTaskResponse(
                    task.getId(),
                    task.getTenantId(),
                    task.getInstanceId(),
                    inst != null ? inst.getInstanceNo() : "--",
                    inst != null ? inst.getProcessCode() : "--",
                    inst != null ? inst.getProcessName() : "--",
                    inst != null ? inst.getTitle() : "--",
                    inst != null ? inst.getCategory() : "--",
                    inst != null ? inst.getInitiatorId() : 0L,
                    inst != null ? inst.getInitiatorName() : "--",
                    task.getNodeId(),
                    task.getNodeName(),
                    task.getNodeType(),
                    task.getAssigneeId(),
                    task.getAssigneeName(),
                    task.getStatus(),
                    task.getAction(),
                    task.getComment(),
                    task.getClaimTime(),
                    task.getFinishTime(),
                    task.getCreatedAt()
            );
        }).toList();
    }

    private Long getTenantId() {
        AuthPrincipal principal = AuthContext.get();
        return principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
    }

    private Long getUserId() {
        AuthPrincipal principal = AuthContext.get();
        return principal != null && principal.userId() != null ? principal.userId() : 0L;
    }

    private String getUserName() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null) {
            return "系统审批员";
        }
        return StringUtils.hasText(principal.nickname()) ? principal.nickname() : principal.username();
    }
}
