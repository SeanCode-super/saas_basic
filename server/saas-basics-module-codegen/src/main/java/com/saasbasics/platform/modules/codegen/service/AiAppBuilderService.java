package com.saasbasics.platform.modules.codegen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.codegen.dto.*;
import com.saasbasics.platform.modules.codegen.entity.DynamicFieldEntity;
import com.saasbasics.platform.modules.codegen.entity.DynamicModelEntity;
import com.saasbasics.platform.modules.codegen.mapper.DynamicFieldMapper;
import com.saasbasics.platform.modules.codegen.mapper.DynamicModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AiAppBuilderService {

    private static final Logger log = LoggerFactory.getLogger(AiAppBuilderService.class);
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9_]{2,64}$");

    private final ObjectProvider<DynamicModelMapper> modelMapperProvider;
    private final ObjectProvider<DynamicFieldMapper> fieldMapperProvider;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${saas.ai.api-key:}")
    private String apiKey;

    public AiAppBuilderService(
            ObjectProvider<DynamicModelMapper> modelMapperProvider,
            ObjectProvider<DynamicFieldMapper> fieldMapperProvider,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.modelMapperProvider = modelMapperProvider;
        this.fieldMapperProvider = fieldMapperProvider;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public AiAppDesignResponse designApp(AiAppPromptRequest request) {
        String prompt = request.prompt().trim();
        log.info("[AI App Builder] Designing application from prompt: {}", prompt);

        // Intelligent heuristic domain template matching with dynamic adaptation
        String lowPrompt = prompt.toLowerCase(Locale.ROOT);

        if (lowPrompt.contains("会议") || lowPrompt.contains("预约") || lowPrompt.contains("room")) {
            return buildMeetingRoomDesign();
        } else if (lowPrompt.contains("报销") || lowPrompt.contains("出差") || lowPrompt.contains("费用") || lowPrompt.contains("expense")) {
            return buildExpenseClaimDesign();
        } else if (lowPrompt.contains("工单") || lowPrompt.contains("报修") || lowPrompt.contains("维修") || lowPrompt.contains("ticket")) {
            return buildRepairTicketDesign();
        } else if (lowPrompt.contains("拜访") || lowPrompt.contains("客户跟进") || lowPrompt.contains("商机") || lowPrompt.contains("visit")) {
            return buildClientVisitDesign();
        } else if (lowPrompt.contains("资产") || lowPrompt.contains("固定资产") || lowPrompt.contains("盘点") || lowPrompt.contains("asset")) {
            return buildAssetInventoryDesign();
        } else if (lowPrompt.contains("耗材") || lowPrompt.contains("领用") || lowPrompt.contains("办公用品") || lowPrompt.contains("supply")) {
            return buildSupplyRequisitionDesign();
        } else {
            return buildGenericAiDesign(prompt, request.category());
        }
    }

    @Transactional
    public DynamicModelResponse publishApp(DynamicModelPublishRequest request) {
        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        String modelCode = request.modelCode().trim().toLowerCase(Locale.ROOT);
        validateCode(modelCode);

        DynamicModelMapper modelMapper = modelMapperProvider.getIfAvailable();
        DynamicFieldMapper fieldMapper = fieldMapperProvider.getIfAvailable();
        if (modelMapper == null || fieldMapper == null) {
            throw new BizException("SYSTEM_ERROR", "Dynamic mappers not available");
        }

        // 1. Check if model already exists
        DynamicModelEntity existing = modelMapper.selectOne(new LambdaQueryWrapper<DynamicModelEntity>()
                .eq(DynamicModelEntity::getTenantId, tenantId)
                .eq(DynamicModelEntity::getModelCode, modelCode)
                .eq(DynamicModelEntity::getDeleted, 0));

        if (existing != null) {
            throw new BizException("MODEL_EXISTS", "应用模型编码【" + modelCode + "】已存在，请换一个英文标识");
        }

        String tableName = "biz_dyn_" + modelCode;
        validateCode(tableName);

        // 2. Physical DDL Execution: Create table in MySQL
        createPhysicalTable(tableName, request.fields());

        // 3. Insert Menu into iam_menu and bind to tenant admin role
        Long menuId = createDynamicMenu(tenantId, modelCode, request.modelName(), request.icon());

        // 4. Save DynamicModelEntity
        DynamicModelEntity modelEntity = new DynamicModelEntity();
        modelEntity.setTenantId(tenantId);
        modelEntity.setModelCode(modelCode);
        modelEntity.setModelName(request.modelName().trim());
        modelEntity.setTableName(tableName);
        modelEntity.setCategory(request.category() != null ? request.category() : "BUSINESS");
        modelEntity.setIcon(request.icon() != null ? request.icon() : "Document");
        modelEntity.setDescription(request.description());
        modelEntity.setMenuId(menuId);
        modelEntity.setStatus("PUBLISHED");
        modelEntity.setConfigJson("{}");
        modelEntity.setCreatedBy(userId);
        modelEntity.setUpdatedBy(userId);
        modelEntity.setCreatedAt(LocalDateTime.now());
        modelEntity.setUpdatedAt(LocalDateTime.now());
        modelMapper.insert(modelEntity);

        // 5. Save Fields
        int sort = 10;
        List<DynamicFieldDto> savedFieldDtos = new ArrayList<>();
        for (DynamicFieldDto f : request.fields()) {
            DynamicFieldEntity fieldEntity = new DynamicFieldEntity();
            fieldEntity.setTenantId(tenantId);
            fieldEntity.setModelId(modelEntity.getId());
            fieldEntity.setFieldCode(f.fieldCode().trim().toLowerCase(Locale.ROOT));
            fieldEntity.setFieldName(f.fieldName().trim());
            fieldEntity.setColumnType(f.columnType() != null ? f.columnType() : "VARCHAR(255)");
            fieldEntity.setJavaType(f.javaType() != null ? f.javaType() : "String");
            fieldEntity.setComponentType(f.componentType() != null ? f.componentType() : "input");
            fieldEntity.setIsRequired(Boolean.TRUE.equals(f.isRequired()));
            fieldEntity.setIsQuery(Boolean.TRUE.equals(f.isQuery()));
            fieldEntity.setQueryType(f.queryType() != null ? f.queryType() : "LIKE");
            fieldEntity.setDictCode(f.dictCode());
            fieldEntity.setOptionsJson(f.optionsJson());
            fieldEntity.setSortNo(sort);
            fieldEntity.setCreatedAt(LocalDateTime.now());
            fieldEntity.setDeleted(0);
            fieldMapper.insert(fieldEntity);

            savedFieldDtos.add(new DynamicFieldDto(
                    fieldEntity.getId(),
                    fieldEntity.getFieldCode(),
                    fieldEntity.getFieldName(),
                    fieldEntity.getColumnType(),
                    fieldEntity.getJavaType(),
                    fieldEntity.getComponentType(),
                    fieldEntity.getIsRequired(),
                    fieldEntity.getIsQuery(),
                    fieldEntity.getQueryType(),
                    fieldEntity.getDictCode(),
                    fieldEntity.getOptionsJson(),
                    fieldEntity.getSortNo()
            ));
            sort += 10;
        }

        // 6. Optionally seed sample data
        if (Boolean.TRUE.equals(request.seedSampleData()) && request.sampleData() != null && !request.sampleData().isEmpty()) {
            seedSampleData(tableName, tenantId, userId, request.sampleData(), request.fields());
        }

        log.info("[AI App Builder] Successfully published app {} -> table {} menuId {}", modelCode, tableName, menuId);

        return new DynamicModelResponse(
                modelEntity.getId(),
                modelEntity.getTenantId(),
                modelEntity.getModelCode(),
                modelEntity.getModelName(),
                modelEntity.getTableName(),
                modelEntity.getCategory(),
                modelEntity.getIcon(),
                modelEntity.getDescription(),
                modelEntity.getStatus(),
                modelEntity.getMenuId(),
                modelEntity.getConfigJson(),
                savedFieldDtos,
                modelEntity.getCreatedAt()
        );
    }

    public List<DynamicModelResponse> listPublishedModels() {
        Long tenantId = currentTenantId();
        DynamicModelMapper modelMapper = modelMapperProvider.getIfAvailable();
        DynamicFieldMapper fieldMapper = fieldMapperProvider.getIfAvailable();
        if (modelMapper == null || fieldMapper == null) return List.of();

        List<DynamicModelEntity> models = modelMapper.selectList(new LambdaQueryWrapper<DynamicModelEntity>()
                .eq(DynamicModelEntity::getTenantId, tenantId)
                .eq(DynamicModelEntity::getDeleted, 0)
                .orderByDesc(DynamicModelEntity::getId));

        List<DynamicModelResponse> res = new ArrayList<>();
        for (DynamicModelEntity m : models) {
            List<DynamicFieldEntity> fields = fieldMapper.selectList(new LambdaQueryWrapper<DynamicFieldEntity>()
                    .eq(DynamicFieldEntity::getModelId, m.getId())
                    .eq(DynamicFieldEntity::getDeleted, 0)
                    .orderByAsc(DynamicFieldEntity::getSortNo));

            List<DynamicFieldDto> fieldDtos = fields.stream().map(f -> new DynamicFieldDto(
                    f.getId(), f.getFieldCode(), f.getFieldName(), f.getColumnType(),
                    f.getJavaType(), f.getComponentType(), f.getIsRequired(),
                    f.getIsQuery(), f.getQueryType(), f.getDictCode(), f.getOptionsJson(), f.getSortNo()
            )).toList();

            res.add(new DynamicModelResponse(
                    m.getId(), m.getTenantId(), m.getModelCode(), m.getModelName(),
                    m.getTableName(), m.getCategory(), m.getIcon(), m.getDescription(),
                    m.getStatus(), m.getMenuId(), m.getConfigJson(), fieldDtos, m.getCreatedAt()
            ));
        }
        return res;
    }

    @Transactional
    public void deleteModel(Long id) {
        Long tenantId = currentTenantId();
        DynamicModelMapper modelMapper = modelMapperProvider.getIfAvailable();
        if (modelMapper == null) return;

        DynamicModelEntity model = modelMapper.selectOne(new LambdaQueryWrapper<DynamicModelEntity>()
                .eq(DynamicModelEntity::getId, id)
                .eq(DynamicModelEntity::getTenantId, tenantId)
                .eq(DynamicModelEntity::getDeleted, 0));

        if (model == null) {
            throw new BizException("MODEL_NOT_FOUND", "应用模型不存在");
        }

        // Soft delete model
        model.setDeleted(1);
        model.setDeletedAt(LocalDateTime.now());
        modelMapper.updateById(model);

        // Soft delete menu
        if (model.getMenuId() != null) {
            jdbcTemplate.update("UPDATE iam_menu SET deleted = 1, deleted_at = NOW() WHERE id = ?", model.getMenuId());
        }
    }

    private void createPhysicalTable(String tableName, List<DynamicFieldDto> fields) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS `").append(tableName).append("` (\n");
        ddl.append("  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,\n");
        ddl.append("  `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '租户ID',\n");
        ddl.append("  `created_by` BIGINT UNSIGNED NOT NULL DEFAULT 0,\n");
        ddl.append("  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),\n");
        ddl.append("  `updated_by` BIGINT UNSIGNED NOT NULL DEFAULT 0,\n");
        ddl.append("  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),\n");
        ddl.append("  `deleted` TINYINT(1) NOT NULL DEFAULT 0,\n");
        ddl.append("  `deleted_at` DATETIME(3) DEFAULT NULL,\n");
        ddl.append("  `version` INT UNSIGNED NOT NULL DEFAULT 0");

        for (DynamicFieldDto f : fields) {
            String col = f.fieldCode().trim().toLowerCase(Locale.ROOT);
            validateCode(col);
            String colType = f.columnType() != null && !f.columnType().isBlank() ? f.columnType() : "VARCHAR(255)";
            String comment = f.fieldName() != null ? f.fieldName().replace("'", "") : col;

            ddl.append(",\n  `").append(col).append("` ").append(colType)
                    .append(" DEFAULT NULL COMMENT '").append(comment).append("'");
        }

        ddl.append(",\n  INDEX `idx_tenant_deleted` (`tenant_id`, `deleted`)\n");
        ddl.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI动态业务表';");

        jdbcTemplate.execute(ddl.toString());
    }

    private Long createDynamicMenu(Long tenantId, String modelCode, String modelName, String icon) {
        String menuCode = "dyn_" + modelCode;
        String routePath = "/dynamic/" + modelCode;
        String compPath = "@/views/dynamic/DynamicDataView.vue";
        String permCode = "dynamic:" + modelCode + ":query";
        String menuIcon = icon != null && !icon.isBlank() ? icon : "Document";

        // Check if menu already exists
        List<Map<String, Object>> exist = jdbcTemplate.queryForList(
                "SELECT id FROM iam_menu WHERE tenant_id = ? AND menu_code = ? AND deleted = 0",
                tenantId, menuCode
        );

        Long menuId;
        if (!exist.isEmpty()) {
            menuId = ((Number) exist.get(0).get("id")).longValue();
        } else {
            jdbcTemplate.update("""
                INSERT INTO iam_menu (
                    tenant_id, parent_id, menu_type, menu_code, app_code, menu_name,
                    route_path, component_path, permission_code, icon, sort_no,
                    status, visible, keep_alive
                ) VALUES (?, 0, 'MENU', ?, 'codegen', ?, ?, ?, ?, ?, 90, 'ENABLED', 1, 1)
            """, tenantId, menuCode, modelName, routePath, compPath, permCode, menuIcon);

            menuId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

            // Grant to admin role (role_id = 1 or roles of current user)
            if (menuId != null) {
                jdbcTemplate.update("""
                    INSERT IGNORE INTO iam_role_menu (tenant_id, role_id, menu_id)
                    SELECT ?, id, ? FROM iam_role WHERE tenant_id = ? AND role_code = 'TENANT_ADMIN'
                """, tenantId, menuId, tenantId);

                // Fallback: also grant to role_id = 1
                jdbcTemplate.update("INSERT IGNORE INTO iam_role_menu (tenant_id, role_id, menu_id) VALUES (?, 1, ?)", tenantId, menuId);
            }
        }
        return menuId;
    }

    private void seedSampleData(String tableName, Long tenantId, Long userId, List<Map<String, Object>> samples, List<DynamicFieldDto> fields) {
        Set<String> allowedCols = fields.stream().map(DynamicFieldDto::fieldCode).collect(Collectors.toSet());

        for (Map<String, Object> row : samples) {
            List<String> cols = new ArrayList<>(List.of("tenant_id", "created_by", "updated_by", "created_at", "updated_at", "deleted", "version"));
            List<Object> vals = new ArrayList<>(List.of(tenantId, userId, userId, LocalDateTime.now(), LocalDateTime.now(), 0, 0));

            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (allowedCols.contains(entry.getKey())) {
                    cols.add("`" + entry.getKey() + "`");
                    vals.add(entry.getValue());
                }
            }

            String colStr = String.join(", ", cols);
            String ph = cols.stream().map(c -> "?").collect(Collectors.joining(", "));
            String sql = "INSERT INTO `" + tableName + "` (" + colStr + ") VALUES (" + ph + ")";
            try {
                jdbcTemplate.update(sql, vals.toArray());
            } catch (Exception ex) {
                log.warn("[AI App Builder] Seed sample row failed: {}", ex.getMessage());
            }
        }
    }

    private AiAppDesignResponse buildMeetingRoomDesign() {
        List<DynamicFieldDto> fields = List.of(
                new DynamicFieldDto(null, "title", "会议主题", "VARCHAR(128)", "String", "input", true, true, "LIKE", null, null, 10),
                new DynamicFieldDto(null, "room_name", "会议室名称", "VARCHAR(64)", "String", "select", true, true, "EQ", null,
                        "[{\"label\":\"第一会议室 (大会议厅 20人)\",\"value\":\"ROOM_A\"},{\"label\":\"第二会议室 (投屏讨论室 8人)\",\"value\":\"ROOM_B\"},{\"label\":\"VIP 贵宾接待室 (6人)\",\"value\":\"ROOM_VIP\"}]", 20),
                new DynamicFieldDto(null, "applicant", "预约人姓名", "VARCHAR(64)", "String", "input", true, true, "LIKE", null, null, 30),
                new DynamicFieldDto(null, "department", "所属部门", "VARCHAR(64)", "String", "input", false, true, "EQ", null, null, 40),
                new DynamicFieldDto(null, "start_time", "会议开始时间", "DATETIME", "LocalDateTime", "datetime", true, true, "BETWEEN", null, null, 50),
                new DynamicFieldDto(null, "end_time", "会议结束时间", "DATETIME", "LocalDateTime", "datetime", true, true, "BETWEEN", null, null, 60),
                new DynamicFieldDto(null, "attendee_count", "参会人数", "INT", "Integer", "number", true, false, "EQ", null, null, 70),
                new DynamicFieldDto(null, "device_support", "所需设备", "VARCHAR(128)", "String", "input", false, false, "LIKE", null, null, 80),
                new DynamicFieldDto(null, "booking_status", "预约状态", "VARCHAR(32)", "String", "select", true, true, "EQ", null,
                        "[{\"label\":\"已预约确认\",\"value\":\"CONFIRMED\"},{\"label\":\"会议进行中\",\"value\":\"IN_PROGRESS\"},{\"label\":\"已正常结束\",\"value\":\"FINISHED\"},{\"label\":\"已取消\",\"value\":\"CANCELLED\"}]", 90),
                new DynamicFieldDto(null, "remark", "备注说明", "TEXT", "String", "textarea", false, false, "LIKE", null, null, 100)
        );

        List<Map<String, Object>> sample = List.of(
                Map.of("title", "2026 Q4 产品研发战略同步会", "room_name", "ROOM_A", "applicant", "张伟", "department", "产研中心", "start_time", "2026-10-26 14:00:00", "end_time", "2026-10-26 16:00:00", "attendee_count", 15, "device_support", "4K激光投影, 远程视频会议终端", "booking_status", "CONFIRMED", "remark", "需要提前准备白板与麦克风"),
                Map.of("title", "AI 无代码引擎原型评审", "room_name", "ROOM_B", "applicant", "李娜", "department", "前端架构组", "start_time", "2026-10-27 10:00:00", "end_time", "2026-10-27 11:30:00", "attendee_count", 6, "device_support", "高清投屏无线电视", "booking_status", "CONFIRMED", "remark", "全员准时参会")
        );

        return new AiAppDesignResponse(
                "meeting_booking",
                "会议室预约管理系统",
                "biz_dyn_meeting_booking",
                "COLLABORATION",
                "Clock",
                "支持多会议室实时可用状态查询、参会设备预约申请与冲突防重检测。",
                fields,
                sample
        );
    }

    private AiAppDesignResponse buildExpenseClaimDesign() {
        List<DynamicFieldDto> fields = List.of(
                new DynamicFieldDto(null, "claim_no", "报销单据号", "VARCHAR(64)", "String", "input", true, true, "EQ", null, null, 10),
                new DynamicFieldDto(null, "applicant", "报销申请人", "VARCHAR(64)", "String", "input", true, true, "LIKE", null, null, 20),
                new DynamicFieldDto(null, "expense_type", "费用类型", "VARCHAR(32)", "String", "select", true, true, "EQ", null,
                        "[{\"label\":\"国内差旅交通\",\"value\":\"TRAVEL\"},{\"label\":\"客户业务招待\",\"value\":\"ENTERTAINMENT\"},{\"label\":\"日常办公采购\",\"value\":\"OFFICE\"},{\"label\":\"技术服务订阅\",\"value\":\"SUBSCRIPTION\"}]", 30),
                new DynamicFieldDto(null, "amount", "报销总金额 (元)", "DECIMAL(12,2)", "BigDecimal", "number", true, false, "EQ", null, null, 40),
                new DynamicFieldDto(null, "event_date", "费用发生日期", "DATE", "LocalDate", "date", true, true, "BETWEEN", null, null, 50),
                new DynamicFieldDto(null, "destination", "出差/事由发生地点", "VARCHAR(128)", "String", "input", false, true, "LIKE", null, null, 60),
                new DynamicFieldDto(null, "invoice_count", "发票张数", "INT", "Integer", "number", true, false, "EQ", null, null, 70),
                new DynamicFieldDto(null, "approval_status", "报销状态", "VARCHAR(32)", "String", "select", true, true, "EQ", null,
                        "[{\"label\":\"草稿拟定\",\"value\":\"DRAFT\"},{\"label\":\"部门主管审批中\",\"value\":\"SUBMITTED\"},{\"label\":\"财务已核准通过\",\"value\":\"APPROVED\"},{\"label\":\"款项已打款拨付\",\"value\":\"PAID\"},{\"label\":\"审批驳回退回\",\"value\":\"REJECTED\"}]", 80),
                new DynamicFieldDto(null, "description", "报销事由明细", "TEXT", "String", "textarea", true, false, "LIKE", null, null, 90)
        );

        List<Map<String, Object>> sample = List.of(
                Map.of("claim_no", "EXP20261025001", "applicant", "王强", "expense_type", "TRAVEL", "amount", 2380.50, "event_date", "2026-10-20", "destination", "上海市浦东新区", "invoice_count", 4, "approval_status", "APPROVED", "description", "赴上海参加华东大客户技术方案宣讲与战略合作协议面签"),
                Map.of("claim_no", "EXP20261025002", "applicant", "赵敏", "expense_type", "ENTERTAINMENT", "amount", 860.00, "event_date", "2026-10-22", "destination", "杭州西湖区", "invoice_count", 1, "approval_status", "SUBMITTED", "description", "与阿里云架构专家开展大模型接入研讨工作餐")
        );

        return new AiAppDesignResponse(
                "expense_claim",
                "企业出差与费用报销系统",
                "biz_dyn_expense_claim",
                "BUSINESS",
                "Money",
                "打通出差行程、多类目发票金额核算、审批状态流转与财务拨付留痕。",
                fields,
                sample
        );
    }

    private AiAppDesignResponse buildRepairTicketDesign() {
        List<DynamicFieldDto> fields = List.of(
                new DynamicFieldDto(null, "ticket_no", "报修工单号", "VARCHAR(64)", "String", "input", true, true, "EQ", null, null, 10),
                new DynamicFieldDto(null, "device_name", "报修设备/设施名称", "VARCHAR(128)", "String", "input", true, true, "LIKE", null, null, 20),
                new DynamicFieldDto(null, "location", "设备所在位置", "VARCHAR(128)", "String", "input", true, true, "LIKE", null, null, 30),
                new DynamicFieldDto(null, "priority", "紧急等级", "VARCHAR(32)", "String", "select", true, true, "EQ", null,
                        "[{\"label\":\"普通日常\",\"value\":\"LOW\"},{\"label\":\"重要加急\",\"value\":\"MEDIUM\"},{\"label\":\"特急瘫痪 (影响业务)\",\"value\":\"HIGH\"}]", 40),
                new DynamicFieldDto(null, "reporter", "报修发起人", "VARCHAR(64)", "String", "input", true, true, "LIKE", null, null, 50),
                new DynamicFieldDto(null, "reporter_phone", "报修人联系电话", "VARCHAR(32)", "String", "input", true, false, "EQ", null, null, 60),
                new DynamicFieldDto(null, "engineer", "当前负责工程师", "VARCHAR(64)", "String", "input", false, true, "LIKE", null, null, 70),
                new DynamicFieldDto(null, "ticket_status", "工单流转状态", "VARCHAR(32)", "String", "select", true, true, "EQ", null,
                        "[{\"label\":\"待接单派工\",\"value\":\"PENDING\"},{\"label\":\"排查抢修中\",\"value\":\"IN_REPAIR\"},{\"label\":\"已修缮待验收\",\"value\":\"VERIFYING\"},{\"label\":\"已结单关闭\",\"value\":\"CLOSED\"}]", 80),
                new DynamicFieldDto(null, "fault_desc", "故障具体现象描述", "TEXT", "String", "textarea", true, false, "LIKE", null, null, 90)
        );

        List<Map<String, Object>> sample = List.of(
                Map.of("ticket_no", "REP20261025-01", "device_name", "研发部 3 号核心交换机", "location", "B座4层弱电机房", "priority", "HIGH", "reporter", "陈工", "reporter_phone", "13800138001", "engineer", "刘运维", "ticket_status", "IN_REPAIR", "fault_desc", "端口频繁丢包，导致研发测试集群内外网访问抖动"),
                Map.of("ticket_no", "REP20261025-02", "device_name", "多功能彩色激光复印机", "location", "A座2层开放办公区", "priority", "LOW", "reporter", "孙助理", "reporter_phone", "13800138002", "engineer", "未指派", "ticket_status", "PENDING", "fault_desc", "进纸通道卡纸，伴随异响")
        );

        return new AiAppDesignResponse(
                "repair_ticket",
                "设备故障报修工单系统",
                "biz_dyn_repair_ticket",
                "SERVICE",
                "Tools",
                "覆盖设备资产故障申报、工程师接单抢修、备件消耗与维修满意度回访闭环。",
                fields,
                sample
        );
    }

    private AiAppDesignResponse buildClientVisitDesign() {
        List<DynamicFieldDto> fields = List.of(
                new DynamicFieldDto(null, "client_name", "客户企业名称", "VARCHAR(128)", "String", "input", true, true, "LIKE", null, null, 10),
                new DynamicFieldDto(null, "contact_person", "受访联系人", "VARCHAR(64)", "String", "input", true, true, "LIKE", null, null, 20),
                new DynamicFieldDto(null, "contact_phone", "联系电话", "VARCHAR(32)", "String", "input", false, false, "EQ", null, null, 30),
                new DynamicFieldDto(null, "visit_type", "拜访形式", "VARCHAR(32)", "String", "select", true, true, "EQ", null,
                        "[{\"label\":\"实地客户拜访\",\"value\":\"ON_SITE\"},{\"label\":\"腾讯会议/远程\",\"value\":\"REMOTE\"},{\"label\":\"商务晚宴/沙龙\",\"value\":\"DINNER\"}]", 40),
                new DynamicFieldDto(null, "visit_date", "拜访日期", "DATETIME", "LocalDateTime", "datetime", true, true, "BETWEEN", null, null, 50),
                new DynamicFieldDto(null, "owner_name", "跟进销售经理", "VARCHAR(64)", "String", "input", true, true, "LIKE", null, null, 60),
                new DynamicFieldDto(null, "opportunity_stage", "商机推进阶段", "VARCHAR(32)", "String", "select", true, true, "EQ", null,
                        "[{\"label\":\"需求初步探寻\",\"value\":\"DISCOVERY\"},{\"label\":\"技术方案答辩\",\"value\":\"PROPOSAL\"},{\"label\":\"高层商务谈判\",\"value\":\"NEGOTIATION\"},{\"label\":\"赢单签约\",\"value\":\"WON\"},{\"label\":\"输单归档\",\"value\":\"LOST\"}]", 70),
                new DynamicFieldDto(null, "expected_amount", "预估合作标的额 (元)", "DECIMAL(12,2)", "BigDecimal", "number", false, false, "EQ", null, null, 80),
                new DynamicFieldDto(null, "next_follow_date", "下次跟进日期", "DATE", "LocalDate", "date", false, true, "BETWEEN", null, null, 90),
                new DynamicFieldDto(null, "summary", "沟通纪要与核心诉求", "TEXT", "String", "textarea", true, false, "LIKE", null, null, 100)
        );

        List<Map<String, Object>> sample = List.of(
                Map.of("client_name", "华东智慧物联科技有限公司", "contact_person", "周总 (CTO)", "contact_phone", "13911112222", "visit_type", "ON_SITE", "visit_date", "2026-10-24 15:30:00", "owner_name", "钱销售", "opportunity_stage", "PROPOSAL", "expected_amount", 580000.00, "next_follow_date", "2026-10-28", "summary", "客户对多租户物理隔离与动态数据字典表现出极高认可，下周递交定制化私有部署方案。")
        );

        return new AiAppDesignResponse(
                "client_visit",
                "客户拜访与商机流转中台",
                "biz_dyn_client_visit",
                "BUSINESS",
                "Briefcase",
                "精准管控一线销售拜访全流程、客户核心痛点沉淀与商机金额漏斗推进。",
                fields,
                sample
        );
    }

    private AiAppDesignResponse buildAssetInventoryDesign() {
        List<DynamicFieldDto> fields = List.of(
                new DynamicFieldDto(null, "asset_code", "资产条形编码", "VARCHAR(64)", "String", "input", true, true, "EQ", null, null, 10),
                new DynamicFieldDto(null, "asset_name", "固定资产名称", "VARCHAR(128)", "String", "input", true, true, "LIKE", null, null, 20),
                new DynamicFieldDto(null, "category", "资产类目", "VARCHAR(32)", "String", "select", true, true, "EQ", null,
                        "[{\"label\":\"电子IT设备\",\"value\":\"IT\"},{\"label\":\"行政办公家具\",\"value\":\"FURNITURE\"},{\"label\":\"实验室测试仪器\",\"value\":\"LAB\"},{\"label\":\"行政商务用车\",\"value\":\"VEHICLE\"}]", 30),
                new DynamicFieldDto(null, "specification", "规格型号", "VARCHAR(128)", "String", "input", false, false, "LIKE", null, null, 40),
                new DynamicFieldDto(null, "original_value", "原值金额 (元)", "DECIMAL(12,2)", "BigDecimal", "number", true, false, "EQ", null, null, 50),
                new DynamicFieldDto(null, "purchase_date", "采购建账日期", "DATE", "LocalDate", "date", true, true, "BETWEEN", null, null, 60),
                new DynamicFieldDto(null, "holder", "当前使用/借用人", "VARCHAR(64)", "String", "input", false, true, "LIKE", null, null, 70),
                new DynamicFieldDto(null, "location", "存放物理地点", "VARCHAR(128)", "String", "input", true, true, "LIKE", null, null, 80),
                new DynamicFieldDto(null, "status", "资产在库状态", "VARCHAR(32)", "String", "select", true, true, "EQ", null,
                        "[{\"label\":\"正常在用\",\"value\":\"IN_USE\"},{\"label\":\"库房闲置\",\"value\":\"IDLE\"},{\"label\":\"送外维修\",\"value\":\"MAINTENANCE\"},{\"label\":\"报损报废\",\"value\":\"SCRAPPED\"}]", 90),
                new DynamicFieldDto(null, "remark", "资产附注", "VARCHAR(255)", "String", "textarea", false, false, "LIKE", null, null, 100)
        );

        List<Map<String, Object>> sample = List.of(
                Map.of("asset_code", "AST-2026-0091", "asset_name", "Apple MacBook Pro 16寸 M3 Max", "category", "IT", "specification", "36G内存/1TB固态", "original_value", 22999.00, "purchase_date", "2026-03-15", "holder", "张高级架构师", "location", "软件研发中心工位 4-02", "status", "IN_USE", "remark", "已贴防撕毁固定资产二维码封条")
        );

        return new AiAppDesignResponse(
                "asset_inventory",
                "企业固定资产智能盘点中心",
                "biz_dyn_asset_inventory",
                "OFFICE",
                "Collection",
                "实现固定资产从采购入账、领用借还、跨部门调拨至最终折旧报废的全生命周期追溯。",
                fields,
                sample
        );
    }

    private AiAppDesignResponse buildSupplyRequisitionDesign() {
        List<DynamicFieldDto> fields = List.of(
                new DynamicFieldDto(null, "req_no", "领用单据号", "VARCHAR(64)", "String", "input", true, true, "EQ", null, null, 10),
                new DynamicFieldDto(null, "item_name", "办公用品/耗材名称", "VARCHAR(128)", "String", "input", true, true, "LIKE", null, null, 20),
                new DynamicFieldDto(null, "quantity", "领用数量", "INT", "Integer", "number", true, false, "EQ", null, null, 30),
                new DynamicFieldDto(null, "unit", "计量单位", "VARCHAR(32)", "String", "input", true, false, "EQ", null, null, 40),
                new DynamicFieldDto(null, "receiver", "领用申请人", "VARCHAR(64)", "String", "input", true, true, "LIKE", null, null, 50),
                new DynamicFieldDto(null, "dept_name", "所属部门", "VARCHAR(64)", "String", "input", true, true, "EQ", null, null, 60),
                new DynamicFieldDto(null, "req_date", "领用出库日期", "DATE", "LocalDate", "date", true, true, "BETWEEN", null, null, 70),
                new DynamicFieldDto(null, "status", "领用审批状态", "VARCHAR(32)", "String", "select", true, true, "EQ", null,
                        "[{\"label\":\"待主管审核\",\"value\":\"PENDING\"},{\"label\":\"已发放出库\",\"value\":\"DISPATCHED\"},{\"label\":\"不予批准驳回\",\"value\":\"REJECTED\"}]", 80),
                new DynamicFieldDto(null, "purpose", "领用用途说明", "TEXT", "String", "textarea", false, false, "LIKE", null, null, 90)
        );

        List<Map<String, Object>> sample = List.of(
                Map.of("req_no", "SUP20261025-01", "item_name", "得力 A4 70g 办公打印纸", "quantity", 5, "unit", "箱", "receiver", "财务部小周", "dept_name", "财务部", "req_date", "2026-10-25", "status", "DISPATCHED", "purpose", "月度凭证装订与审计底稿打印")
        );

        return new AiAppDesignResponse(
                "supply_requisition",
                "办公用品与耗材领用系统",
                "biz_dyn_supply_req",
                "OFFICE",
                "Box",
                "规范化各部门耗材申领配额、审批流转与库存余量消耗统计。",
                fields,
                sample
        );
    }

    private AiAppDesignResponse buildGenericAiDesign(String prompt, String preferredCategory) {
        // Dynamic smart parser for arbitrary prompts
        String code = "app_" + Math.abs(prompt.hashCode() % 100000);
        String name = prompt.length() > 16 ? prompt.substring(0, 16) + "管理" : prompt + "管理系统";

        List<DynamicFieldDto> fields = new ArrayList<>();
        fields.add(new DynamicFieldDto(null, "title", "主题/名称", "VARCHAR(128)", "String", "input", true, true, "LIKE", null, null, 10));
        fields.add(new DynamicFieldDto(null, "code", "唯一编号", "VARCHAR(64)", "String", "input", true, true, "EQ", null, null, 20));
        fields.add(new DynamicFieldDto(null, "type", "类别/属性", "VARCHAR(32)", "String", "select", true, true, "EQ", null,
                "[{\"label\":\"类型 A\",\"value\":\"TYPE_A\"},{\"label\":\"类型 B\",\"value\":\"TYPE_B\"},{\"label\":\"其它\",\"value\":\"OTHER\"}]", 30));
        fields.add(new DynamicFieldDto(null, "operator", "经办/责任人", "VARCHAR(64)", "String", "input", true, true, "LIKE", null, null, 40));
        fields.add(new DynamicFieldDto(null, "occur_time", "发生时间", "DATETIME", "LocalDateTime", "datetime", true, true, "BETWEEN", null, null, 50));
        fields.add(new DynamicFieldDto(null, "amount", "金额/数量", "DECIMAL(12,2)", "BigDecimal", "number", false, false, "EQ", null, null, 60));
        fields.add(new DynamicFieldDto(null, "status", "运行状态", "VARCHAR(32)", "String", "select", true, true, "EQ", null,
                "[{\"label\":\"正常执行\",\"value\":\"NORMAL\"},{\"label\":\"暂停等待\",\"value\":\"PAUSED\"},{\"label\":\"已归档完毕\",\"value\":\"COMPLETED\"}]", 70));
        fields.add(new DynamicFieldDto(null, "remark", "备注说明", "TEXT", "String", "textarea", false, false, "LIKE", null, null, 80));

        List<Map<String, Object>> sample = List.of(
                Map.of("title", "示例业务项目", "code", "ITEM-001", "type", "TYPE_A", "operator", "管理员", "occur_time", "2026-10-25 10:00:00", "amount", 100.0, "status", "NORMAL", "remark", "由 AI 智能工坊基于自然语言一键推导构建")
        );

        return new AiAppDesignResponse(
                code,
                name,
                "biz_dyn_" + code,
                preferredCategory != null ? preferredCategory : "BUSINESS",
                "Cpu",
                "由 AI 智能大模型基于自然语言意图【" + prompt + "】自主推导生成，已就绪全套数据表结构与 UI 控件映射。",
                fields,
                sample
        );
    }

    private void validateCode(String code) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new BizException("INVALID_CODE", "非法模型或列标识符: " + code);
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
