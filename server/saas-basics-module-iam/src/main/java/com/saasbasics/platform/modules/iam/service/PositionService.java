package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.iam.dto.PositionResponse;
import com.saasbasics.platform.modules.iam.dto.PositionSaveRequest;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.PositionEntity;
import com.saasbasics.platform.modules.iam.mapper.PositionMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class PositionService {

    private final ObjectProvider<PositionMapper> positionMapperProvider;
    private final AuditTrailService auditTrailService;

    public PositionService(ObjectProvider<PositionMapper> positionMapperProvider,
                           AuditTrailService auditTrailService) {
        this.positionMapperProvider = positionMapperProvider;
        this.auditTrailService = auditTrailService;
    }

    public PositionResponse getPosition(Long id) {
        PositionResponse response = requiredMapper().selectPositionById(id);
        if (response == null) {
            throw new BizException("IAM_POSITION_NOT_FOUND", "IAM position not found");
        }
        return response;
    }

    public PositionResponse createPosition(PositionSaveRequest request) {
        PositionEntity entity = new PositionEntity();
        apply(entity, request);
        requiredMapper().insert(entity);
        auditTrailService.record("iam", "position", String.valueOf(entity.getId()), "CREATE", null, entity.getPositionCode(), true);
        return getPosition(entity.getId());
    }

    public PositionResponse updatePosition(Long id, PositionSaveRequest request) {
        PositionMapper mapper = requiredMapper();
        PositionEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("IAM_POSITION_NOT_FOUND", "IAM position not found");
        }
        String before = entity.getPositionName();
        apply(entity, request);
        mapper.updateById(entity);
        auditTrailService.record("iam", "position", String.valueOf(id), "UPDATE", before, entity.getPositionName(), true);
        return getPosition(id);
    }

    public PositionResponse updateStatus(Long id, StatusUpdateRequest request) {
        PositionMapper mapper = requiredMapper();
        PositionEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("IAM_POSITION_NOT_FOUND", "IAM position not found");
        }
        entity.setStatus(request.status());
        mapper.updateById(entity);
        auditTrailService.record("iam", "position", String.valueOf(id), "STATUS", null, request.status(), true);
        return getPosition(id);
    }

    private void apply(PositionEntity entity, PositionSaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setPositionCode(request.positionCode());
        entity.setPositionName(request.positionName());
        entity.setPositionLevel(request.positionLevel());
        entity.setStatus(request.status());
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setRemark(request.remark());
    }

    private PositionMapper requiredMapper() {
        PositionMapper mapper = positionMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Position write operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
