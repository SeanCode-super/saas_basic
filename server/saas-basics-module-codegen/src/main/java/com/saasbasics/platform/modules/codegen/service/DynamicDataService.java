package com.saasbasics.platform.modules.codegen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.api.PageResponse;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.codegen.dto.DynamicDataPageRequest;
import com.saasbasics.platform.modules.codegen.dto.DynamicFieldDto;
import com.saasbasics.platform.modules.codegen.dto.DynamicModelResponse;
import com.saasbasics.platform.modules.codegen.entity.DynamicFieldEntity;
import com.saasbasics.platform.modules.codegen.entity.DynamicModelEntity;
import com.saasbasics.platform.modules.codegen.mapper.DynamicFieldMapper;
import com.saasbasics.platform.modules.codegen.mapper.DynamicModelMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DynamicDataService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9_]{2,64}$");

    private final ObjectProvider<DynamicModelMapper> modelMapperProvider;
    private final ObjectProvider<DynamicFieldMapper> fieldMapperProvider;
    private final JdbcTemplate jdbcTemplate;

    public DynamicDataService(
            ObjectProvider<DynamicModelMapper> modelMapperProvider,
            ObjectProvider<DynamicFieldMapper> fieldMapperProvider,
            JdbcTemplate jdbcTemplate
    ) {
        this.modelMapperProvider = modelMapperProvider;
        this.fieldMapperProvider = fieldMapperProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    public DynamicModelResponse getModelMeta(String modelCode) {
        Long tenantId = currentTenantId();
        DynamicModelEntity model = findModel(tenantId, modelCode);
        List<DynamicFieldEntity> fields = findFields(tenantId, model.getId());

        List<DynamicFieldDto> fieldDtos = fields.stream()
                .map(this::toFieldDto)
                .toList();

        return new DynamicModelResponse(
                model.getId(),
                model.getTenantId(),
                model.getModelCode(),
                model.getModelName(),
                model.getTableName(),
                model.getCategory(),
                model.getIcon(),
                model.getDescription(),
                model.getStatus(),
                model.getMenuId(),
                model.getConfigJson(),
                fieldDtos,
                model.getCreatedAt()
        );
    }

    public PageResponse<Map<String, Object>> queryPage(String modelCode, DynamicDataPageRequest request) {
        Long tenantId = currentTenantId();
        DynamicModelEntity model = findModel(tenantId, modelCode);
        List<DynamicFieldEntity> fields = findFields(tenantId, model.getId());

        String tableName = model.getTableName();
        validateCode(tableName);

        Set<String> allowedCols = fields.stream()
                .map(DynamicFieldEntity::getFieldCode)
                .collect(Collectors.toSet());

        StringBuilder whereSql = new StringBuilder(" WHERE tenant_id = ? AND deleted = 0 ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);

        // Keyword search across queryable string fields
        if (request.keyword() != null && !request.keyword().isBlank()) {
            String kw = "%" + request.keyword().trim() + "%";
            List<String> kwConds = new ArrayList<>();
            for (DynamicFieldEntity f : fields) {
                if (Boolean.TRUE.equals(f.getIsQuery()) && "String".equals(f.getJavaType())) {
                    kwConds.add("`" + f.getFieldCode() + "` LIKE ?");
                    args.add(kw);
                }
            }
            if (!kwConds.isEmpty()) {
                whereSql.append(" AND (").append(String.join(" OR ", kwConds)).append(") ");
            }
        }

        // Specific field filters
        if (request.filters() != null && !request.filters().isEmpty()) {
            for (Map.Entry<String, Object> entry : request.filters().entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (val == null || String.valueOf(val).isBlank()) continue;

                if (allowedCols.contains(key)) {
                    DynamicFieldEntity f = fields.stream().filter(item -> item.getFieldCode().equals(key)).findFirst().orElse(null);
                    String qType = (f != null && f.getQueryType() != null) ? f.getQueryType() : "EQ";
                    if ("LIKE".equalsIgnoreCase(qType)) {
                        whereSql.append(" AND `").append(key).append("` LIKE ? ");
                        args.add("%" + String.valueOf(val).trim() + "%");
                    } else {
                        whereSql.append(" AND `").append(key).append("` = ? ");
                        args.add(val);
                    }
                }
            }
        }

        // Count total
        String countSql = "SELECT COUNT(1) FROM `" + tableName + "`" + whereSql;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
        if (total == null || total == 0) {
            return new PageResponse<>(List.of(), 0);
        }

        // Query records
        int page = request.getPage();
        int size = request.getSize();
        int offset = (page - 1) * size;

        StringBuilder selectCols = new StringBuilder("id, tenant_id, created_by, created_at, updated_by, updated_at");
        for (DynamicFieldEntity f : fields) {
            selectCols.append(", `").append(f.getFieldCode()).append("`");
        }

        String dataSql = "SELECT " + selectCols + " FROM `" + tableName + "`"
                + whereSql
                + " ORDER BY id DESC LIMIT ? OFFSET ?";

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add(offset);

        List<Map<String, Object>> records = jdbcTemplate.queryForList(dataSql, queryArgs.toArray());
        return new PageResponse<>(records, total);
    }

    public Map<String, Object> getById(String modelCode, Long id) {
        Long tenantId = currentTenantId();
        DynamicModelEntity model = findModel(tenantId, modelCode);
        String tableName = model.getTableName();
        validateCode(tableName);

        String sql = "SELECT * FROM `" + tableName + "` WHERE id = ? AND tenant_id = ? AND deleted = 0";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, id, tenantId);
        if (list.isEmpty()) {
            throw new BizException("RECORD_NOT_FOUND", "数据记录不存在或已删除");
        }
        return list.get(0);
    }

    @Transactional
    public Long createRecord(String modelCode, Map<String, Object> data) {
        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        DynamicModelEntity model = findModel(tenantId, modelCode);
        List<DynamicFieldEntity> fields = findFields(tenantId, model.getId());

        String tableName = model.getTableName();
        validateCode(tableName);

        Map<String, DynamicFieldEntity> fieldMap = fields.stream()
                .collect(Collectors.toMap(DynamicFieldEntity::getFieldCode, f -> f));

        List<String> insertCols = new ArrayList<>(List.of("tenant_id", "created_by", "updated_by", "created_at", "updated_at", "deleted", "version"));
        List<Object> insertVals = new ArrayList<>(List.of(tenantId, userId, userId, LocalDateTime.now(), LocalDateTime.now(), 0, 0));

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String col = entry.getKey();
            if (fieldMap.containsKey(col)) {
                insertCols.add("`" + col + "`");
                insertVals.add(entry.getValue());
            }
        }

        String colStr = String.join(", ", insertCols);
        String placeHolders = insertCols.stream().map(c -> "?").collect(Collectors.joining(", "));
        String sql = "INSERT INTO `" + tableName + "` (" + colStr + ") VALUES (" + placeHolders + ")";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < insertVals.size(); i++) {
                ps.setObject(i + 1, insertVals.get(i));
            }
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0L;
    }

    @Transactional
    public void updateRecord(String modelCode, Long id, Map<String, Object> data) {
        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        DynamicModelEntity model = findModel(tenantId, modelCode);
        List<DynamicFieldEntity> fields = findFields(tenantId, model.getId());

        String tableName = model.getTableName();
        validateCode(tableName);

        Map<String, DynamicFieldEntity> fieldMap = fields.stream()
                .collect(Collectors.toMap(DynamicFieldEntity::getFieldCode, f -> f));

        List<String> setClauses = new ArrayList<>();
        List<Object> setVals = new ArrayList<>();

        setClauses.add("updated_by = ?");
        setVals.add(userId);
        setClauses.add("updated_at = ?");
        setVals.add(LocalDateTime.now());

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String col = entry.getKey();
            if (fieldMap.containsKey(col)) {
                setClauses.add("`" + col + "` = ?");
                setVals.add(entry.getValue());
            }
        }

        setVals.add(id);
        setVals.add(tenantId);

        String sql = "UPDATE `" + tableName + "` SET " + String.join(", ", setClauses)
                + " WHERE id = ? AND tenant_id = ? AND deleted = 0";

        int affected = jdbcTemplate.update(sql, setVals.toArray());
        if (affected == 0) {
            throw new BizException("UPDATE_FAILED", "记录不存在或无权修改");
        }
    }

    @Transactional
    public void deleteRecord(String modelCode, Long id) {
        Long tenantId = currentTenantId();
        DynamicModelEntity model = findModel(tenantId, modelCode);
        String tableName = model.getTableName();
        validateCode(tableName);

        String sql = "UPDATE `" + tableName + "` SET deleted = 1, deleted_at = ? WHERE id = ? AND tenant_id = ? AND deleted = 0";
        jdbcTemplate.update(sql, LocalDateTime.now(), id, tenantId);
    }

    @Transactional
    public void batchDeleteRecords(String modelCode, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        Long tenantId = currentTenantId();
        DynamicModelEntity model = findModel(tenantId, modelCode);
        String tableName = model.getTableName();
        validateCode(tableName);

        String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
        String sql = "UPDATE `" + tableName + "` SET deleted = 1, deleted_at = ? WHERE tenant_id = ? AND id IN (" + placeholders + ") AND deleted = 0";

        List<Object> args = new ArrayList<>();
        args.add(LocalDateTime.now());
        args.add(tenantId);
        args.addAll(ids);

        jdbcTemplate.update(sql, args.toArray());
    }

    private DynamicModelEntity findModel(Long tenantId, String modelCode) {
        validateCode(modelCode);
        DynamicModelMapper mapper = modelMapperProvider.getIfAvailable();
        if (mapper == null) throw new BizException("SYSTEM_ERROR", "DynamicModelMapper unavailable");

        DynamicModelEntity entity = mapper.selectOne(new LambdaQueryWrapper<DynamicModelEntity>()
                .eq(DynamicModelEntity::getTenantId, tenantId)
                .eq(DynamicModelEntity::getModelCode, modelCode)
                .eq(DynamicModelEntity::getDeleted, 0));

        if (entity == null) {
            throw new BizException("MODEL_NOT_FOUND", "应用模型【" + modelCode + "】不存在或尚未发布");
        }
        return entity;
    }

    private List<DynamicFieldEntity> findFields(Long tenantId, Long modelId) {
        DynamicFieldMapper mapper = fieldMapperProvider.getIfAvailable();
        if (mapper == null) throw new BizException("SYSTEM_ERROR", "DynamicFieldMapper unavailable");

        return mapper.selectList(new LambdaQueryWrapper<DynamicFieldEntity>()
                .eq(DynamicFieldEntity::getModelId, modelId)
                .eq(DynamicFieldEntity::getDeleted, 0)
                .orderByAsc(DynamicFieldEntity::getSortNo));
    }

    private DynamicFieldDto toFieldDto(DynamicFieldEntity entity) {
        return new DynamicFieldDto(
                entity.getId(),
                entity.getFieldCode(),
                entity.getFieldName(),
                entity.getColumnType(),
                entity.getJavaType(),
                entity.getComponentType(),
                entity.getIsRequired(),
                entity.getIsQuery(),
                entity.getQueryType(),
                entity.getDictCode(),
                entity.getOptionsJson(),
                entity.getSortNo()
        );
    }

    private void validateCode(String code) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new BizException("INVALID_CODE", "非法标识符: " + code);
        }
    }

    private Long currentTenantId() {
        AuthPrincipal principal = AuthContext.get();
        return principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
    }

    private Long currentUserId() {
        AuthPrincipal principal = AuthContext.get();
        return principal != null && principal.userId() != null ? principal.userId() : 1L;
    }
}
