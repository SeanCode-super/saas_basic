package com.saasbasics.platform.modules.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.workflow.dto.ProcessInstanceDetailResponse;
import com.saasbasics.platform.modules.workflow.dto.ProcessInstanceResponse;
import com.saasbasics.platform.modules.workflow.dto.ProcessStartRequest;
import com.saasbasics.platform.modules.workflow.dto.TaskLogResponse;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WorkflowInstanceService {

    private final ProcessInstanceMapper instanceMapper;
    private final ProcessDefinitionMapper definitionMapper;
    private final WorkflowTaskMapper taskMapper;
    private final WorkflowTaskLogMapper taskLogMapper;
    private final WorkflowDefinitionService definitionService;
    private final WorkflowEngine workflowEngine;
    private final JdbcTemplate jdbcTemplate;

    public WorkflowInstanceService(
            ProcessInstanceMapper instanceMapper,
            ProcessDefinitionMapper definitionMapper,
            WorkflowTaskMapper taskMapper,
            WorkflowTaskLogMapper taskLogMapper,
            WorkflowDefinitionService definitionService,
            WorkflowEngine workflowEngine,
            JdbcTemplate jdbcTemplate
    ) {
        this.instanceMapper = instanceMapper;
        this.definitionMapper = definitionMapper;
        this.taskMapper = taskMapper;
        this.taskLogMapper = taskLogMapper;
        this.definitionService = definitionService;
        this.workflowEngine = workflowEngine;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ProcessInstanceResponse startInstance(ProcessStartRequest request) {
        Long tenantId = getTenantId();
        Long userId = getUserId();
        String userName = getUserName();

        ProcessDefinitionEntity definition = definitionMapper.selectById(request.definitionId());
        if (definition == null || definition.getDeleted() != 0 || !definition.getTenantId().equals(tenantId)) {
            throw new BizException("NOT_FOUND", "流程定义不存在或已删除");
        }
        if (!"PUBLISHED".equals(definition.getStatus())) {
            throw new BizException("NOT_PUBLISHED", "该流程未处于发布可用状态，无法发起申请");
        }

        // 1. 查询发起人部门归属
        Long initiatorDeptId = null;
        String initiatorDeptName = "默认业务部";
        try {
            String sql = "SELECT d.id, d.dept_name FROM iam_department d " +
                    "INNER JOIN iam_employee e ON e.dept_id = d.id " +
                    "WHERE e.user_id = ? AND d.tenant_id = ? AND d.deleted = 0 LIMIT 1";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId, tenantId);
            if (!rows.isEmpty()) {
                initiatorDeptId = ((Number) rows.get(0).get("id")).longValue();
                initiatorDeptName = String.valueOf(rows.get(0).get("dept_name"));
            }
        } catch (Exception ignored) {
        }

        // 2. 生成单据流水号
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = ThreadLocalRandom.current().nextInt(1000, 9999);
        String instanceNo = "WF" + stamp + rand;

        // 3. 计算第一个可执行审批节点
        WorkflowEngine.ExecutionStep firstStep = workflowEngine.advanceFlow(
                definition.getFlowNodesJson(),
                request.formDataJson(),
                "root",
                tenantId,
                userId,
                initiatorDeptId
        );

        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setTenantId(tenantId);
        instance.setInstanceNo(instanceNo);
        instance.setDefinitionId(definition.getId());
        instance.setProcessCode(definition.getProcessCode());
        instance.setProcessName(definition.getProcessName());
        instance.setTitle(request.title());
        instance.setCategory(definition.getCategory());
        instance.setInitiatorId(userId);
        instance.setInitiatorName(userName);
        instance.setInitiatorDeptId(initiatorDeptId);
        instance.setInitiatorDeptName(initiatorDeptName);
        instance.setFormDataJson(request.formDataJson());
        instance.setStartTime(LocalDateTime.now());
        instance.setCreatedBy(userId);
        instance.setUpdatedBy(userId);

        if (firstStep.isEnd()) {
            instance.setCurrentNodeId("end");
            instance.setCurrentNodeName("流程办结");
            instance.setStatus("APPROVED");
            instance.setEndTime(LocalDateTime.now());
            instanceMapper.insert(instance);
        } else {
            instance.setCurrentNodeId(firstStep.nodeId());
            instance.setCurrentNodeName(firstStep.nodeName());
            instance.setStatus("RUNNING");
            instanceMapper.insert(instance);

            // 创建待办任务
            for (WorkflowEngine.AssigneeInfo assignee : firstStep.assignees()) {
                WorkflowTaskEntity task = new WorkflowTaskEntity();
                task.setTenantId(tenantId);
                task.setInstanceId(instance.getId());
                task.setNodeId(firstStep.nodeId());
                task.setNodeName(firstStep.nodeName());
                task.setNodeType("APPROVER");
                task.setAssigneeId(assignee.userId());
                task.setAssigneeName(assignee.userName());
                task.setStatus("PENDING");
                task.setCreatedBy(userId);
                task.setUpdatedBy(userId);
                taskMapper.insert(task);
            }

            // 处理可能伴随的抄送人
            for (WorkflowEngine.AssigneeInfo cc : firstStep.ccList()) {
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

        // 4. 记录提交日志
        WorkflowTaskLogEntity logEntity = new WorkflowTaskLogEntity();
        logEntity.setTenantId(tenantId);
        logEntity.setInstanceId(instance.getId());
        logEntity.setNodeName("发起申请");
        logEntity.setOperatorId(userId);
        logEntity.setOperatorName(userName);
        logEntity.setAction("SUBMIT");
        logEntity.setActionLabel("提交申请");
        logEntity.setComment("发起了审批申请单，进入流程流转。");
        logEntity.setCreatedAt(LocalDateTime.now());
        taskLogMapper.insert(logEntity);

        return toResponse(instance);
    }

    public List<ProcessInstanceResponse> listInstances(String category, String status, String keyword, Boolean onlyMine) {
        Long tenantId = getTenantId();
        LambdaQueryWrapper<ProcessInstanceEntity> wrapper = new LambdaQueryWrapper<ProcessInstanceEntity>()
                .eq(ProcessInstanceEntity::getTenantId, tenantId)
                .eq(ProcessInstanceEntity::getDeleted, 0);

        if (Boolean.TRUE.equals(onlyMine)) {
            wrapper.eq(ProcessInstanceEntity::getInitiatorId, getUserId());
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(ProcessInstanceEntity::getCategory, category);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ProcessInstanceEntity::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ProcessInstanceEntity::getTitle, keyword)
                    .or()
                    .like(ProcessInstanceEntity::getInstanceNo, keyword)
                    .or()
                    .like(ProcessInstanceEntity::getProcessName, keyword)
                    .or()
                    .like(ProcessInstanceEntity::getInitiatorName, keyword));
        }

        wrapper.orderByDesc(ProcessInstanceEntity::getId);

        return instanceMapper.selectList(wrapper).stream()
                .map(this::toResponse)
                .toList();
    }

    public ProcessInstanceDetailResponse getInstanceDetail(Long id) {
        Long tenantId = getTenantId();
        ProcessInstanceEntity instance = instanceMapper.selectById(id);
        if (instance == null || instance.getDeleted() != 0 || !instance.getTenantId().equals(tenantId)) {
            throw new BizException("NOT_FOUND", "审批实例不存在");
        }

        ProcessDefinitionEntity definition = definitionMapper.selectById(instance.getDefinitionId());

        List<WorkflowTaskEntity> taskEntities = taskMapper.selectList(new LambdaQueryWrapper<WorkflowTaskEntity>()
                .eq(WorkflowTaskEntity::getTenantId, tenantId)
                .eq(WorkflowTaskEntity::getInstanceId, id)
                .eq(WorkflowTaskEntity::getDeleted, 0)
                .orderByAsc(WorkflowTaskEntity::getId));

        List<WorkflowTaskLogEntity> logEntities = taskLogMapper.selectList(new LambdaQueryWrapper<WorkflowTaskLogEntity>()
                .eq(WorkflowTaskLogEntity::getTenantId, tenantId)
                .eq(WorkflowTaskLogEntity::getInstanceId, id)
                .orderByAsc(WorkflowTaskLogEntity::getId));

        List<WorkflowTaskResponse> tasks = taskEntities.stream().map(t -> new WorkflowTaskResponse(
                t.getId(), t.getTenantId(), t.getInstanceId(), instance.getInstanceNo(),
                instance.getProcessCode(), instance.getProcessName(), instance.getTitle(),
                instance.getCategory(), instance.getInitiatorId(), instance.getInitiatorName(),
                t.getNodeId(), t.getNodeName(), t.getNodeType(), t.getAssigneeId(), t.getAssigneeName(),
                t.getStatus(), t.getAction(), t.getComment(), t.getClaimTime(), t.getFinishTime(),
                t.getCreatedAt()
        )).toList();

        List<TaskLogResponse> logs = logEntities.stream().map(l -> new TaskLogResponse(
                l.getId(), l.getTenantId(), l.getInstanceId(), l.getTaskId(), l.getNodeName(),
                l.getOperatorId(), l.getOperatorName(), l.getAction(), l.getActionLabel(),
                l.getComment(), l.getCreatedAt()
        )).toList();

        return new ProcessInstanceDetailResponse(
                toResponse(instance),
                definition != null ? definitionService.toResponse(definition) : null,
                instance.getFormDataJson(),
                tasks,
                logs
        );
    }

    @Transactional
    public void cancelInstance(Long id, String reason) {
        Long tenantId = getTenantId();
        Long userId = getUserId();
        String userName = getUserName();

        ProcessInstanceEntity instance = instanceMapper.selectById(id);
        if (instance == null || instance.getDeleted() != 0 || !instance.getTenantId().equals(tenantId)) {
            throw new BizException("NOT_FOUND", "审批实例不存在");
        }

        if (!instance.getInitiatorId().equals(userId)) {
            throw new BizException("PERMISSION_DENIED", "只有发起人本人可以撤回审批单");
        }
        if (!"RUNNING".equals(instance.getStatus())) {
            throw new BizException("INVALID_STATUS", "当前审批单不在进行中，无法撤回");
        }

        instance.setStatus("CANCELLED");
        instance.setEndTime(LocalDateTime.now());
        instance.setUpdatedBy(userId);
        instanceMapper.updateById(instance);

        // 取消所有待办任务
        List<WorkflowTaskEntity> pendingTasks = taskMapper.selectList(new LambdaQueryWrapper<WorkflowTaskEntity>()
                .eq(WorkflowTaskEntity::getTenantId, tenantId)
                .eq(WorkflowTaskEntity::getInstanceId, id)
                .eq(WorkflowTaskEntity::getStatus, "PENDING"));

        for (WorkflowTaskEntity task : pendingTasks) {
            task.setStatus("CANCELLED");
            task.setUpdatedBy(userId);
            taskMapper.updateById(task);
        }

        // 写入撤回流转日志
        WorkflowTaskLogEntity logEntity = new WorkflowTaskLogEntity();
        logEntity.setTenantId(tenantId);
        logEntity.setInstanceId(id);
        logEntity.setNodeName("撤回申请");
        logEntity.setOperatorId(userId);
        logEntity.setOperatorName(userName);
        logEntity.setAction("CANCEL");
        logEntity.setActionLabel("撤回申请");
        logEntity.setComment(StringUtils.hasText(reason) ? reason : "发起人主动撤回申请");
        logEntity.setCreatedAt(LocalDateTime.now());
        taskLogMapper.insert(logEntity);
    }

    public ProcessInstanceResponse toResponse(ProcessInstanceEntity entity) {
        return new ProcessInstanceResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getInstanceNo(),
                entity.getDefinitionId(),
                entity.getProcessCode(),
                entity.getProcessName(),
                entity.getTitle(),
                entity.getCategory(),
                entity.getInitiatorId(),
                entity.getInitiatorName(),
                entity.getInitiatorDeptId(),
                entity.getInitiatorDeptName(),
                entity.getCurrentNodeId(),
                entity.getCurrentNodeName(),
                entity.getStatus(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getCreatedAt()
        );
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
            return "系统用户";
        }
        return StringUtils.hasText(principal.nickname()) ? principal.nickname() : principal.username();
    }
}
