package com.saasbasics.platform.modules.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.integration.dto.DatasourceResponse;
import com.saasbasics.platform.modules.integration.dto.DatasourceSaveRequest;
import com.saasbasics.platform.modules.integration.entity.DatasourceEntity;
import com.saasbasics.platform.modules.integration.mapper.DatasourceMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class IntegrationService {

    private final ObjectProvider<DatasourceMapper> datasourceMapperProvider;

    public IntegrationService(ObjectProvider<DatasourceMapper> datasourceMapperProvider) {
        this.datasourceMapperProvider = datasourceMapperProvider;
    }

    public List<DatasourceResponse> listDatasources() {
        DatasourceMapper datasourceMapper = requiredMapper();
        LambdaQueryWrapper<DatasourceEntity> wrapper = new LambdaQueryWrapper<DatasourceEntity>()
                .eq(DatasourceEntity::getDeleted, 0)
                .orderByAsc(DatasourceEntity::getDatasourceCode);

        return datasourceMapper.selectList(wrapper)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public DatasourceResponse getDatasource(Long id) {
        DatasourceEntity entity = requiredMapper().selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("DATASOURCE_NOT_FOUND", "Datasource not found");
        }
        return toResponse(entity);
    }

    public DatasourceResponse createDatasource(DatasourceSaveRequest request) {
        DatasourceEntity entity = new DatasourceEntity();
        apply(entity, request);
        requiredMapper().insert(entity);
        return getDatasource(entity.getId());
    }

    public DatasourceResponse updateDatasource(Long id, DatasourceSaveRequest request) {
        DatasourceMapper mapper = requiredMapper();
        DatasourceEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("DATASOURCE_NOT_FOUND", "Datasource not found");
        }
        apply(entity, request);
        mapper.updateById(entity);
        return getDatasource(id);
    }

    private DatasourceResponse toResponse(DatasourceEntity entity) {
        return new DatasourceResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getDatasourceCode(),
                entity.getDatasourceName(),
                entity.getDatasourceType(),
                entity.getUsageType(),
                entity.getHost(),
                entity.getPort(),
                entity.getDatabaseName(),
                entity.getUsername(),
                entity.getTestStatus(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private void apply(DatasourceEntity entity, DatasourceSaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setDatasourceCode(request.code());
        entity.setDatasourceName(request.name());
        entity.setDatasourceType(request.type());
        entity.setUsageType(request.usageType());
        entity.setHost(request.host());
        entity.setPort(request.port());
        entity.setDatabaseName(request.databaseName());
        entity.setUsername(request.username());
        entity.setTestStatus(request.testStatus());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private DatasourceMapper requiredMapper() {
        DatasourceMapper mapper = datasourceMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Datasource operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
