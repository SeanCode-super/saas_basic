package com.saasbasics.platform.modules.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.integration.dto.DatasourceProbeResultResponse;
import com.saasbasics.platform.modules.integration.dto.DatasourceResponse;
import com.saasbasics.platform.modules.integration.dto.DatasourceSaveRequest;
import com.saasbasics.platform.modules.integration.entity.DatasourceEntity;
import com.saasbasics.platform.modules.integration.mapper.DatasourceMapper;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class IntegrationService {

    private static final int PROBE_TIMEOUT_MS = 2500;

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

    public DatasourceProbeResultResponse testDatasource(Long id) {
        DatasourceMapper mapper = requiredMapper();
        DatasourceEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("DATASOURCE_NOT_FOUND", "Datasource not found");
        }
        return probeAndPersist(mapper, entity);
    }

    public List<DatasourceProbeResultResponse> testAllDatasources() {
        DatasourceMapper mapper = requiredMapper();
        List<DatasourceEntity> entities = mapper.selectList(
                new LambdaQueryWrapper<DatasourceEntity>()
                        .eq(DatasourceEntity::getDeleted, 0)
                        .orderByAsc(DatasourceEntity::getDatasourceCode)
        );
        return entities.stream()
                .map(entity -> probeAndPersist(mapper, entity))
                .toList();
    }

    private DatasourceProbeResultResponse probeAndPersist(DatasourceMapper mapper, DatasourceEntity entity) {
        String host = entity.getHost() != null ? entity.getHost().trim() : "";
        int port = entity.getPort() != null && entity.getPort() > 0
                ? entity.getPort()
                : defaultPortForType(entity.getDatasourceType());
        LocalDateTime testedAt = LocalDateTime.now();

        if (host.isEmpty()) {
            entity.setTestStatus("FAILED");
            entity.setLastTestedAt(testedAt);
            mapper.updateById(entity);
            return new DatasourceProbeResultResponse(
                    entity.getId(), entity.getDatasourceCode(), entity.getDatasourceName(),
                    "FAILED", 0L, "未配置目标主机地址", testedAt
            );
        }

        long startNs = System.nanoTime();
        String status;
        String message;
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLinkLocalAddress()) {
                status = "FAILED";
                message = "禁止探测链路本地元数据地址: " + host;
            } else {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(address, port), PROBE_TIMEOUT_MS);
                    status = "PASSED";
                    message = String.format(Locale.ROOT, "TCP %s:%d 握手连通成功", host, port);
                }
            }
        } catch (Exception ex) {
            status = "FAILED";
            message = String.format(Locale.ROOT, "连接 %s:%d 失败: %s", host, port, ex.getMessage());
        }
        long latencyMs = Math.max(1L, Math.round((System.nanoTime() - startNs) / 1_000_000.0));
        entity.setTestStatus(status);
        entity.setLastTestedAt(testedAt);
        mapper.updateById(entity);
        return new DatasourceProbeResultResponse(
                entity.getId(), entity.getDatasourceCode(), entity.getDatasourceName(),
                status, latencyMs, message, testedAt
        );
    }

    private int defaultPortForType(String datasourceType) {
        if (datasourceType == null) {
            return 3306;
        }
        return switch (datasourceType.toUpperCase(Locale.ROOT)) {
            case "POSTGRESQL" -> 5432;
            case "REDIS" -> 6379;
            case "HTTP", "WEBHOOK" -> 80;
            case "HTTPS" -> 443;
            default -> 3306;
        };
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
