package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.dto.DataScopeResponse;
import com.saasbasics.platform.modules.iam.dto.DataScopeSaveRequest;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.DataScopeEntity;
import com.saasbasics.platform.modules.iam.mapper.DataScopeMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class DataScopeService {

    private final ObjectProvider<DataScopeMapper> dataScopeMapperProvider;

    public DataScopeService(ObjectProvider<DataScopeMapper> dataScopeMapperProvider) {
        this.dataScopeMapperProvider = dataScopeMapperProvider;
    }

    public List<DataScopeResponse> listDataScopes() {
        DataScopeMapper mapper = dataScopeMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        return mapper.selectDataScopeList();
    }

    public DataScopeResponse getDataScope(Long id) {
        DataScopeResponse response = requiredMapper().selectDataScopeById(id);
        if (response == null) {
            throw new BizException("IAM_DATA_SCOPE_NOT_FOUND", "IAM data scope not found");
        }
        return response;
    }

    public DataScopeResponse createDataScope(DataScopeSaveRequest request) {
        DataScopeEntity entity = new DataScopeEntity();
        apply(entity, request);
        requiredMapper().insert(entity);
        return getDataScope(entity.getId());
    }

    public DataScopeResponse updateDataScope(Long id, DataScopeSaveRequest request) {
        DataScopeMapper mapper = requiredMapper();
        DataScopeEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("IAM_DATA_SCOPE_NOT_FOUND", "IAM data scope not found");
        }
        apply(entity, request);
        mapper.updateById(entity);
        return getDataScope(id);
    }

    public DataScopeResponse updateStatus(Long id, StatusUpdateRequest request) {
        DataScopeMapper mapper = requiredMapper();
        DataScopeEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("IAM_DATA_SCOPE_NOT_FOUND", "IAM data scope not found");
        }
        entity.setStatus(request.status());
        mapper.updateById(entity);
        return getDataScope(id);
    }

    private void apply(DataScopeEntity entity, DataScopeSaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setScopeCode(request.scopeCode());
        entity.setScopeName(request.scopeName());
        entity.setScopeType(request.scopeType());
        entity.setScopeRuleJson(request.scopeRuleJson());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private DataScopeMapper requiredMapper() {
        DataScopeMapper mapper = dataScopeMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Data scope write operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
