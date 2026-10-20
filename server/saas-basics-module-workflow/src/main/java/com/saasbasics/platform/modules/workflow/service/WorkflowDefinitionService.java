package com.saasbasics.platform.modules.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.workflow.dto.ProcessDefinitionResponse;
import com.saasbasics.platform.modules.workflow.dto.ProcessDefinitionSaveRequest;
import com.saasbasics.platform.modules.workflow.entity.ProcessDefinitionEntity;
import com.saasbasics.platform.modules.workflow.mapper.ProcessDefinitionMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WorkflowDefinitionService {

    private final ProcessDefinitionMapper definitionMapper;

    public WorkflowDefinitionService(ProcessDefinitionMapper definitionMapper) {
        this.definitionMapper = definitionMapper;
    }

    public List<ProcessDefinitionResponse> listDefinitions(String category, String keyword, String status) {
        Long tenantId = getTenantId();
        LambdaQueryWrapper<ProcessDefinitionEntity> wrapper = new LambdaQueryWrapper<ProcessDefinitionEntity>()
                .eq(ProcessDefinitionEntity::getTenantId, tenantId)
                .eq(ProcessDefinitionEntity::getDeleted, 0);

        if (StringUtils.hasText(category)) {
            wrapper.eq(ProcessDefinitionEntity::getCategory, category);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ProcessDefinitionEntity::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ProcessDefinitionEntity::getProcessName, keyword)
                    .or()
                    .like(ProcessDefinitionEntity::getProcessCode, keyword)
                    .or()
                    .like(ProcessDefinitionEntity::getDescription, keyword));
        }

        wrapper.orderByAsc(ProcessDefinitionEntity::getSortNo).orderByDesc(ProcessDefinitionEntity::getId);

        return definitionMapper.selectList(wrapper).stream()
                .map(this::toResponse)
                .toList();
    }

    public ProcessDefinitionResponse getDefinition(Long id) {
        ProcessDefinitionEntity entity = definitionMapper.selectById(id);
        if (entity == null || entity.getDeleted() != 0 || !entity.getTenantId().equals(getTenantId())) {
            throw new BizException("NOT_FOUND", "流程定义不存在或已删除");
        }
        return toResponse(entity);
    }

    public ProcessDefinitionEntity getRequiredEntity(Long id) {
        ProcessDefinitionEntity entity = definitionMapper.selectById(id);
        if (entity == null || entity.getDeleted() != 0 || !entity.getTenantId().equals(getTenantId())) {
            throw new BizException("NOT_FOUND", "流程定义不存在或已删除");
        }
        return entity;
    }

    @Transactional
    public ProcessDefinitionResponse createDefinition(ProcessDefinitionSaveRequest request) {
        Long tenantId = getTenantId();
        Long count = definitionMapper.selectCount(new LambdaQueryWrapper<ProcessDefinitionEntity>()
                .eq(ProcessDefinitionEntity::getTenantId, tenantId)
                .eq(ProcessDefinitionEntity::getProcessCode, request.processCode())
                .eq(ProcessDefinitionEntity::getDeleted, 0));
        if (count > 0) {
            throw new BizException("DUPLICATE_CODE", "流程编码已存在: " + request.processCode());
        }

        ProcessDefinitionEntity entity = new ProcessDefinitionEntity();
        entity.setTenantId(tenantId);
        entity.setProcessCode(request.processCode());
        entity.setProcessName(request.processName());
        entity.setCategory(StringUtils.hasText(request.category()) ? request.category() : "GENERAL");
        entity.setIcon(StringUtils.hasText(request.icon()) ? request.icon() : "Document");
        entity.setColor(StringUtils.hasText(request.color()) ? request.color() : "#2563eb");
        entity.setDescription(request.description());
        entity.setFormSchemaJson(request.formSchemaJson());
        entity.setFlowNodesJson(request.flowNodesJson());
        entity.setVersion(1);
        entity.setStatus(StringUtils.hasText(request.status()) ? request.status() : "PUBLISHED");
        entity.setSortNo(request.sortNo() != null ? request.sortNo() : 10);
        entity.setCreatedBy(getUserId());
        entity.setUpdatedBy(getUserId());

        definitionMapper.insert(entity);
        return toResponse(entity);
    }

    @Transactional
    public ProcessDefinitionResponse updateDefinition(Long id, ProcessDefinitionSaveRequest request) {
        ProcessDefinitionEntity entity = getRequiredEntity(id);

        entity.setProcessName(request.processName());
        if (StringUtils.hasText(request.category())) {
            entity.setCategory(request.category());
        }
        if (StringUtils.hasText(request.icon())) {
            entity.setIcon(request.icon());
        }
        if (StringUtils.hasText(request.color())) {
            entity.setColor(request.color());
        }
        entity.setDescription(request.description());
        if (request.formSchemaJson() != null) {
            entity.setFormSchemaJson(request.formSchemaJson());
        }
        if (request.flowNodesJson() != null) {
            entity.setFlowNodesJson(request.flowNodesJson());
        }
        if (StringUtils.hasText(request.status())) {
            entity.setStatus(request.status());
        }
        if (request.sortNo() != null) {
            entity.setSortNo(request.sortNo());
        }
        entity.setVersion(entity.getVersion() + 1);
        entity.setUpdatedBy(getUserId());

        definitionMapper.updateById(entity);
        return toResponse(entity);
    }

    @Transactional
    public ProcessDefinitionResponse toggleStatus(Long id) {
        ProcessDefinitionEntity entity = getRequiredEntity(id);
        String newStatus = "PUBLISHED".equals(entity.getStatus()) ? "DISABLED" : "PUBLISHED";
        entity.setStatus(newStatus);
        entity.setUpdatedBy(getUserId());
        definitionMapper.updateById(entity);
        return toResponse(entity);
    }

    @Transactional
    public void deleteDefinition(Long id) {
        ProcessDefinitionEntity entity = getRequiredEntity(id);
        definitionMapper.update(null, new LambdaUpdateWrapper<ProcessDefinitionEntity>()
                .eq(ProcessDefinitionEntity::getId, id)
                .set(ProcessDefinitionEntity::getDeletedAt, java.time.LocalDateTime.now())
                .set(ProcessDefinitionEntity::getUpdatedBy, getUserId()));
        definitionMapper.deleteById(id);
    }

    public ProcessDefinitionResponse toResponse(ProcessDefinitionEntity entity) {
        return new ProcessDefinitionResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getProcessCode(),
                entity.getProcessName(),
                entity.getCategory(),
                entity.getIcon(),
                entity.getColor(),
                entity.getDescription(),
                entity.getFormSchemaJson(),
                entity.getFlowNodesJson(),
                entity.getVersion(),
                entity.getStatus(),
                entity.getSortNo(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
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
}
