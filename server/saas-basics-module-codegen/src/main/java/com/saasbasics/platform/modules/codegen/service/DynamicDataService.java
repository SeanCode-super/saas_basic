package com.saasbasics.platform.modules.codegen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.api.PageResponse;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.auth.CurrentDepartmentResolver;
import com.saasbasics.platform.common.auth.DataPermissionProvider;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.common.auth.SqlAstDataGuard;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.codegen.dto.DynamicDataPageRequest;
import com.saasbasics.platform.modules.codegen.dto.DynamicFieldDto;
import com.saasbasics.platform.modules.codegen.dto.DynamicModelResponse;
import com.saasbasics.platform.modules.codegen.entity.DynamicFieldEntity;
import com.saasbasics.platform.modules.codegen.entity.DynamicModelEntity;
import com.saasbasics.platform.modules.codegen.mapper.DynamicFieldMapper;
import com.saasbasics.platform.modules.codegen.mapper.DynamicModelMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DynamicDataService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9_]{2,64}$");

    private final ObjectProvider<DynamicModelMapper> modelMapperProvider;
    private final ObjectProvider<DynamicFieldMapper> fieldMapperProvider;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<DataPermissionProvider> dataPermissionProvider;
    private final ObjectProvider<CurrentDepartmentResolver> departmentResolverProvider;
    private final Set<String> deptReadyTables = ConcurrentHashMap.newKeySet();

    public DynamicDataService(
            ObjectProvider<DynamicModelMapper> modelMapperProvider,
            ObjectProvider<DynamicFieldMapper> fieldMapperProvider,
            JdbcTemplate jdbcTemplate
    ) {
        this(modelMapperProvider, fieldMapperProvider, jdbcTemplate, null, null);
    }

    @Autowired
    public DynamicDataService(
            ObjectProvider<DynamicModelMapper> modelMapperProvider,
            ObjectProvider<DynamicFieldMapper> fieldMapperProvider,
            JdbcTemplate jdbcTemplate,
            ObjectProvider<DataPermissionProvider> dataPermissionProvider,
            ObjectProvider<CurrentDepartmentResolver> departmentResolverProvider
    ) {
        this.modelMapperProvider = modelMapperProvider;
        this.fieldMapperProvider = fieldMapperProvider;
        this.jdbcTemplate = jdbcTemplate;
        this.dataPermissionProvider = dataPermissionProvider;
        this.departmentResolverProvider = departmentResolverProvider;
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
        ensureDeptColumn(tableName);

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
        String countSql = applyDynamicDataScope("SELECT COUNT(1) FROM `" + tableName + "`" + whereSql, modelCode, tableName);
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
        if (total == null || total == 0) {
            return new PageResponse<>(List.of(), 0);
        }

        // Query records
        int page = request.getPage();
        int size = request.getSize();
        int offset = (page - 1) * size;

        StringBuilder selectCols = new StringBuilder("id, tenant_id, dept_id, created_by, created_at, updated_by, updated_at");
        for (DynamicFieldEntity f : fields) {
            selectCols.append(", `").append(f.getFieldCode()).append("`");
        }

        String baseDataSql = "SELECT " + selectCols + " FROM `" + tableName + "`" + whereSql;
        String dataSql = applyDynamicDataScope(baseDataSql, modelCode, tableName) + " ORDER BY id DESC LIMIT ? OFFSET ?";

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
        ensureDeptColumn(tableName);

        String sql = applyDynamicDataScope(
                "SELECT * FROM `" + tableName + "` WHERE id = ? AND tenant_id = ? AND deleted = 0",
                modelCode,
                tableName
        );
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, id, tenantId);
        if (list.isEmpty()) {
            throw new BizException("RECORD_NOT_FOUND", "数据记录不存在或无权访问");
        }
        return list.get(0);
    }

    @Transactional
    public Long createRecord(String modelCode, Map<String, Object> data) {
        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        Long deptId = resolveCurrentDeptId(tenantId, userId);
        DynamicModelEntity model = findModel(tenantId, modelCode);
        List<DynamicFieldEntity> fields = findFields(tenantId, model.getId());

        String tableName = model.getTableName();
        validateCode(tableName);
        ensureDeptColumn(tableName);

        Map<String, DynamicFieldEntity> fieldMap = fields.stream()
                .collect(Collectors.toMap(DynamicFieldEntity::getFieldCode, f -> f));

        List<String> insertCols = new ArrayList<>(List.of("tenant_id", "created_by", "dept_id", "updated_by", "created_at", "updated_at", "deleted", "version"));
        List<Object> insertVals = new ArrayList<>(List.of(tenantId, userId, deptId, userId, LocalDateTime.now(), LocalDateTime.now(), 0, 0));

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
        ensureDeptColumn(tableName);
        assertRecordVisible(modelCode, tableName, id, tenantId);

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
    public int seedMockRecords(String modelCode) {
        Long tenantId = currentTenantId();
        DynamicModelEntity model = findModel(tenantId, modelCode);
        List<DynamicFieldEntity> fields = findFields(tenantId, model.getId());
        String tableName = model.getTableName();
        validateCode(tableName);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `" + tableName + "` WHERE tenant_id = ? AND deleted = 0",
                Integer.class,
                tenantId
        );
        if (count != null && count > 0) {
            return count;
        }

        List<Map<String, Object>> samples = generateDefaultSamples(modelCode, fields);
        for (Map<String, Object> sample : samples) {
            createRecord(modelCode, sample);
        }
        return samples.size();
    }

    @Transactional
    public int batchImportRecords(String modelCode, List<Map<String, Object>> records) {
        if (records == null || records.isEmpty()) return 0;
        int count = 0;
        for (Map<String, Object> record : records) {
            createRecord(modelCode, record);
            count++;
        }
        return count;
    }

    private List<Map<String, Object>> generateDefaultSamples(String modelCode, List<DynamicFieldEntity> fields) {
        List<Map<String, Object>> list = new ArrayList<>();
        if ("asset_inventory".equalsIgnoreCase(modelCode)) {
            list.add(Map.of(
                    "asset_code", "AST-2026-001",
                    "asset_name", "MacBook Pro 16寸 M3 Max 工作站",
                    "category", "IT",
                    "specification", "64GB / 2TB SSD / 黑色",
                    "original_value", 24999.00,
                    "purchase_date", "2026-01-15",
                    "holder", "张架构师",
                    "location", "北京研发中心A座502工位",
                    "status", "IN_USE",
                    "remark", "核心系统开发高性能工作机"
            ));
            list.add(Map.of(
                    "asset_code", "AST-2026-002",
                    "asset_name", "戴尔 32寸 4K Type-C 专业显示器",
                    "category", "IT",
                    "specification", "UltraSharp U3223QE",
                    "original_value", 4899.00,
                    "purchase_date", "2026-02-18",
                    "holder", "李设计师",
                    "location", "北京研发中心A座503工位",
                    "status", "IN_USE",
                    "remark", "UI设计部标准双屏设备"
            ));
            list.add(Map.of(
                    "asset_code", "AST-2026-003",
                    "asset_name", "高背人体工学电脑网椅",
                    "category", "FURNITURE",
                    "specification", "全网透气旗舰款带腰托",
                    "original_value", 1899.00,
                    "purchase_date", "2026-03-05",
                    "holder", "物资调配库",
                    "location", "公司1号资产仓库B区04号货架",
                    "status", "IDLE",
                    "remark", "新入职员工备用办公物资"
            ));
            list.add(Map.of(
                    "asset_code", "AST-2026-004",
                    "asset_name", "企业级千兆POE核心交换机",
                    "category", "LAB",
                    "specification", "48口全千兆网管型",
                    "original_value", 6800.00,
                    "purchase_date", "2025-08-20",
                    "holder", "IT运维组",
                    "location", "主栋核心机房Rack-02",
                    "status", "MAINTENANCE",
                    "remark", "主供电模块异常已报修供应商售后"
            ));
            return list;
        }

        // Generic generator
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> row = new HashMap<>();
            for (DynamicFieldEntity f : fields) {
                String cType = f.getComponentType();
                if ("number".equalsIgnoreCase(cType)) {
                    row.put(f.getFieldCode(), i * 100);
                } else if ("date".equalsIgnoreCase(cType)) {
                    row.put(f.getFieldCode(), "2026-10-2" + i);
                } else if ("datetime".equalsIgnoreCase(cType)) {
                    row.put(f.getFieldCode(), "2026-10-2" + i + " 10:00:00");
                } else if ("select".equalsIgnoreCase(cType) && f.getOptionsJson() != null && !f.getOptionsJson().isBlank()) {
                    // Extract first or second option
                    row.put(f.getFieldCode(), i == 1 ? "IN_USE" : (i == 2 ? "IDLE" : "MAINTENANCE"));
                } else {
                    row.put(f.getFieldCode(), "演示" + f.getFieldName() + " #" + i);
                }
            }
            list.add(row);
        }
        return list;
    }

    @Transactional
    public void deleteRecord(String modelCode, Long id) {
        Long tenantId = currentTenantId();
        DynamicModelEntity model = findModel(tenantId, modelCode);
        String tableName = model.getTableName();
        validateCode(tableName);
        ensureDeptColumn(tableName);
        assertRecordVisible(modelCode, tableName, id, tenantId);

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
        ensureDeptColumn(tableName);

        String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
        String visibleQuery = applyDynamicDataScope(
                "SELECT id FROM `" + tableName + "` WHERE tenant_id = ? AND deleted = 0 AND id IN (" + placeholders + ")",
                modelCode,
                tableName
        );
        List<Object> visArgs = new ArrayList<>();
        visArgs.add(tenantId);
        visArgs.addAll(ids);
        List<Long> allowedIds = jdbcTemplate.queryForList(visibleQuery, Long.class, visArgs.toArray());
        if (allowedIds.isEmpty()) {
            throw new BizException("RECORD_NOT_FOUND", "待删除记录不存在或不在当前数据权限范围内");
        }

        String delPlaceholders = allowedIds.stream().map(i -> "?").collect(Collectors.joining(","));
        String sql = "UPDATE `" + tableName + "` SET deleted = 1, deleted_at = ? WHERE tenant_id = ? AND id IN (" + delPlaceholders + ") AND deleted = 0";

        List<Object> args = new ArrayList<>();
        args.add(LocalDateTime.now());
        args.add(tenantId);
        args.addAll(allowedIds);

        jdbcTemplate.update(sql, args.toArray());
    }

    private void assertRecordVisible(String modelCode, String tableName, Long id, Long tenantId) {
        String checkSql = applyDynamicDataScope(
                "SELECT id FROM `" + tableName + "` WHERE id = ? AND tenant_id = ? AND deleted = 0",
                modelCode,
                tableName
        );
        List<Long> found = jdbcTemplate.queryForList(checkSql, Long.class, id, tenantId);
        if (found.isEmpty()) {
            throw new BizException("RECORD_NOT_FOUND", "记录不存在或不在当前部门数据权限范围内");
        }
    }

    private String applyDynamicDataScope(String sql, String modelCode, String tableName) {
        DataPermissionProvider provider = dataPermissionProvider != null ? dataPermissionProvider.getIfAvailable() : null;
        if (provider == null) {
            return sql;
        }
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.isSuperAdmin()) {
            return sql;
        }
        DataPermissionSqlSpec spec = provider.resolveDataPermission("dynamic:" + modelCode);
        if (spec == null || spec.isAllowAll()) {
            return sql;
        }
        if (spec.isDenyAll()) {
            throw new BizException("DATA_PERMISSION_DENIED", "当前账号暂无权访问该动态业务数据");
        }
        if (!spec.hasConstraints()) {
            return sql;
        }
        return SqlAstDataGuard.rewriteSelectSql(sql, tbl ->
                tableName.equalsIgnoreCase(tbl)
                        ? new SqlAstDataGuard.TableScopeConfig(spec, "", "dept_id", "created_by")
                        : null
        );
    }

    private void ensureDeptColumn(String tableName) {
        if (deptReadyTables.contains(tableName)) {
            return;
        }
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'dept_id'",
                    Integer.class,
                    tableName
            );
            if (exists != null && exists == 0) {
                jdbcTemplate.execute("ALTER TABLE `" + tableName + "` ADD COLUMN `dept_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '归属部门ID' AFTER `created_by`, ADD INDEX `idx_tenant_dept` (`tenant_id`, `dept_id`)");
            }
            deptReadyTables.add(tableName);
        } catch (Exception ignored) {
            // 兼容单元测试中的 Mock JdbcTemplate 或非 MySQL 方言
        }
    }

    private Long resolveCurrentDeptId(Long tenantId, Long userId) {
        CurrentDepartmentResolver resolver = departmentResolverProvider != null
                ? departmentResolverProvider.getIfAvailable()
                : null;
        return resolver != null ? resolver.resolveDepartmentId(tenantId, userId) : null;
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
        assertSafeDynamicTable(entity.getTableName());
        return entity;
    }

    private void assertSafeDynamicTable(String tableName) {
        validateCode(tableName);
        if (tableName == null || !tableName.startsWith("biz_dyn_")) {
            throw new BizException("UNSAFE_DYNAMIC_TABLE", "非法动态业务表前缀: " + tableName);
        }
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
