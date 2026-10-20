package com.saasbasics.platform.modules.codegen.engine;

import com.saasbasics.platform.modules.codegen.dto.CodegenPreviewFileResponse;
import com.saasbasics.platform.modules.codegen.dto.CodegenTableDefinitionRequest;
import com.saasbasics.platform.modules.codegen.dto.CodegenTableDefinitionRequest.CodegenColumnDefinition;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class CodegenEngineService {

    public List<CodegenPreviewFileResponse> generateFiles(CodegenTableDefinitionRequest request) {
        String basePkg = (request.basePackage() != null && !request.basePackage().isBlank())
                ? request.basePackage()
                : "com.saasbasics.platform.modules";
        String module = (request.moduleName() != null && !request.moduleName().isBlank())
                ? request.moduleName().toLowerCase()
                : extractModule(request.tableName());
        String className = (request.className() != null && !request.className().isBlank())
                ? request.className()
                : toCamelCase(cleanTableName(request.tableName()), true);
        String varName = toCamelCase(className, false);
        String bizName = (request.businessName() != null && !request.businessName().isBlank())
                ? request.businessName()
                : (request.tableComment() != null ? request.tableComment() : className);

        List<CodegenColumnDefinition> cols = request.columns() != null ? request.columns() : List.of();

        List<CodegenPreviewFileResponse> files = new ArrayList<>();

        // 1. Entity
        files.add(new CodegenPreviewFileResponse(
                String.format("server/src/main/java/%s/%s/entity/%sEntity.java", basePkg.replace('.', '/'), module, className),
                className + "Entity.java",
                "Entity",
                "java",
                generateEntity(basePkg, module, className, request.tableName(), cols)
        ));

        // 2. Mapper
        files.add(new CodegenPreviewFileResponse(
                String.format("server/src/main/java/%s/%s/mapper/%sMapper.java", basePkg.replace('.', '/'), module, className),
                className + "Mapper.java",
                "Mapper",
                "java",
                generateMapper(basePkg, module, className)
        ));

        // 3. Response DTO
        files.add(new CodegenPreviewFileResponse(
                String.format("server/src/main/java/%s/%s/dto/%sResponse.java", basePkg.replace('.', '/'), module, className),
                className + "Response.java",
                "DTO",
                "java",
                generateResponseDto(basePkg, module, className, cols)
        ));

        // 4. Save Request DTO
        files.add(new CodegenPreviewFileResponse(
                String.format("server/src/main/java/%s/%s/dto/%sSaveRequest.java", basePkg.replace('.', '/'), module, className),
                className + "SaveRequest.java",
                "DTO",
                "java",
                generateSaveRequestDto(basePkg, module, className, cols)
        ));

        // 5. Query Request DTO
        files.add(new CodegenPreviewFileResponse(
                String.format("server/src/main/java/%s/%s/dto/%sQueryRequest.java", basePkg.replace('.', '/'), module, className),
                className + "QueryRequest.java",
                "DTO",
                "java",
                generateQueryRequestDto(basePkg, module, className, cols)
        ));

        // 6. Service
        files.add(new CodegenPreviewFileResponse(
                String.format("server/src/main/java/%s/%s/service/%sService.java", basePkg.replace('.', '/'), module, className),
                className + "Service.java",
                "Service",
                "java",
                generateService(basePkg, module, className, varName, cols)
        ));

        // 7. Controller
        files.add(new CodegenPreviewFileResponse(
                String.format("server/src/main/java/%s/%s/controller/%sController.java", basePkg.replace('.', '/'), module, className),
                className + "Controller.java",
                "Controller",
                "java",
                generateController(basePkg, module, className, varName, bizName)
        ));

        // 8. Frontend TS API
        files.add(new CodegenPreviewFileResponse(
                String.format("web/src/api/modules/%s.ts", module),
                module + ".ts",
                "API (TS)",
                "typescript",
                generateFrontendApi(module, className)
        ));

        // 9. Frontend Vue 3 ProTable & Drawer View
        files.add(new CodegenPreviewFileResponse(
                String.format("web/src/views/%s/%sView.vue", module, className),
                className + "View.vue",
                "View (Vue3)",
                "vue",
                generateFrontendView(module, className, bizName, cols)
        ));

        // 10. Database Migration SQL
        files.add(new CodegenPreviewFileResponse(
                String.format("sql/V1.0.0__%s_init.sql", module),
                "V1.0.0__" + module + "_init.sql",
                "SQL",
                "sql",
                generateSqlMigration(module, className, bizName, request.tableName(), cols)
        ));

        // 11. Optional Master-Slave sub-table artifacts
        if ("MASTER_SLAVE".equalsIgnoreCase(request.templateType())) {
            String subClass = request.subClassName() != null && !request.subClassName().isBlank()
                    ? request.subClassName() : className + "Item";
            String subTable = request.subTableName() != null && !request.subTableName().isBlank()
                    ? request.subTableName() : request.tableName() + "_item";
            String subFk = request.subTableFk() != null && !request.subTableFk().isBlank()
                    ? request.subTableFk() : varName + "_id";

            files.add(new CodegenPreviewFileResponse(
                    String.format("server/src/main/java/%s/%s/entity/%sEntity.java", basePkg.replace('.', '/'), module, subClass),
                    subClass + "Entity.java",
                    "Sub-Entity",
                    "java",
                    generateSubEntity(basePkg, module, subClass, subTable, subFk)
            ));

            files.add(new CodegenPreviewFileResponse(
                    String.format("server/src/main/java/%s/%s/mapper/%sMapper.java", basePkg.replace('.', '/'), module, subClass),
                    subClass + "Mapper.java",
                    "Sub-Mapper",
                    "java",
                    generateMapper(basePkg, module, subClass)
            ));
        }

        return files;
    }

    public byte[] generateZipArchive(CodegenTableDefinitionRequest request) throws IOException {
        List<CodegenPreviewFileResponse> files = generateFiles(request);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (CodegenPreviewFileResponse file : files) {
                ZipEntry entry = new ZipEntry(file.path());
                zos.putNextEntry(entry);
                zos.write(file.code().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private String generateEntity(String basePkg, String module, String className, String tableName, List<CodegenColumnDefinition> cols) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("package %s.%s.entity;\n\n", basePkg, module));
        sb.append("import com.baomidou.mybatisplus.annotation.*;\n");

        boolean hasTenantId = cols.stream().anyMatch(c -> "tenant_id".equalsIgnoreCase(c.columnName()));
        boolean hasBaseAudit = cols.stream().anyMatch(c -> "created_at".equalsIgnoreCase(c.columnName()));

        java.util.Set<String> baseColNames = java.util.Set.of(
                "id", "tenant_id", "created_by", "created_at", "updated_by", "updated_at",
                "deleted", "deleted_at", "version", "remark"
        );

        if (hasTenantId) {
            sb.append("import com.saasbasics.platform.common.model.BaseTenantEntity;\n");
        } else if (hasBaseAudit) {
            sb.append("import com.saasbasics.platform.common.model.BaseEntity;\n");
        } else {
            sb.append("import java.io.Serializable;\n");
        }

        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("import java.time.LocalDateTime;\n\n");
        sb.append("/**\n");
        sb.append(String.format(" * %s 实体持久化对象\n", className));
        sb.append(" */\n");
        sb.append(String.format("@TableName(\"%s\")\n", tableName));

        if (hasTenantId) {
            sb.append(String.format("public class %sEntity extends BaseTenantEntity {\n\n", className));
        } else if (hasBaseAudit) {
            sb.append(String.format("public class %sEntity extends BaseEntity {\n\n", className));
        } else {
            sb.append(String.format("public class %sEntity implements Serializable {\n\n", className));
        }

        List<CodegenColumnDefinition> bizCols = (hasTenantId || hasBaseAudit)
                ? cols.stream().filter(c -> !baseColNames.contains(c.columnName().toLowerCase())).toList()
                : cols;

        for (CodegenColumnDefinition col : bizCols) {
            String javaType = resolveJavaType(col.javaType(), col.columnType());
            String fieldName = col.fieldName() != null && !col.fieldName().isBlank() ? col.fieldName() : toCamelCase(col.columnName(), false);

            if (Boolean.TRUE.equals(col.isPk())) {
                sb.append("    @TableId(type = IdType.AUTO)\n");
            }
            if (col.comment() != null && !col.comment().isBlank()) {
                sb.append(String.format("    /** %s */\n", col.comment()));
            }
            sb.append(String.format("    private %s %s;\n\n", javaType, fieldName));
        }

        // Getters and Setters
        for (CodegenColumnDefinition col : bizCols) {
            String javaType = resolveJavaType(col.javaType(), col.columnType());
            String fieldName = col.fieldName() != null && !col.fieldName().isBlank() ? col.fieldName() : toCamelCase(col.columnName(), false);
            String cap = toCamelCase(fieldName, true);

            sb.append(String.format("    public %s get%s() {\n        return %s;\n    }\n\n", javaType, cap, fieldName));
            sb.append(String.format("    public void set%s(%s %s) {\n        this.%s = %s;\n    }\n\n", cap, javaType, fieldName, fieldName, fieldName));
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String generateMapper(String basePkg, String module, String className) {
        return String.format("""
                package %s.%s.mapper;

                import com.baomidou.mybatisplus.core.mapper.BaseMapper;
                import %s.%s.entity.%sEntity;
                import org.apache.ibatis.annotations.Mapper;

                /**
                 * %s 数据访问层 Mapper
                 */
                @Mapper
                public interface %sMapper extends BaseMapper<%sEntity> {
                }
                """, basePkg, module, basePkg, module, className, className, className, className);
    }

    private String generateResponseDto(String basePkg, String module, String className, List<CodegenColumnDefinition> cols) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("package %s.%s.dto;\n\n", basePkg, module));
        sb.append("import com.saasbasics.platform.common.desensitize.DataMask;\n");
        sb.append("import com.saasbasics.platform.common.desensitize.DataMaskType;\n");
        sb.append("import java.io.Serializable;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("import java.time.LocalDateTime;\n\n");
        sb.append(String.format("/**\n * %s 详情响应 DTO\n */\n", className));
        sb.append(String.format("public record %sResponse(\n", className));

        for (int i = 0; i < cols.size(); i++) {
            CodegenColumnDefinition col = cols.get(i);
            String javaType = resolveJavaType(col.javaType(), col.columnType());
            String fieldName = col.fieldName() != null && !col.fieldName().isBlank() ? col.fieldName() : toCamelCase(col.columnName(), false);

            if (fieldName.toLowerCase().contains("mobile") || fieldName.toLowerCase().contains("phone")) {
                sb.append("        @DataMask(DataMaskType.MOBILE)\n");
            } else if (fieldName.toLowerCase().contains("email")) {
                sb.append("        @DataMask(DataMaskType.EMAIL)\n");
            } else if (fieldName.toLowerCase().contains("idcard")) {
                sb.append("        @DataMask(DataMaskType.ID_CARD)\n");
            }

            sb.append(String.format("        %s %s%s\n", javaType, fieldName, (i < cols.size() - 1 ? "," : "")));
        }
        sb.append(") implements Serializable {}\n");
        return sb.toString();
    }

    private String generateSaveRequestDto(String basePkg, String module, String className, List<CodegenColumnDefinition> cols) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("package %s.%s.dto;\n\n", basePkg, module));
        sb.append("import jakarta.validation.constraints.*;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("import java.time.LocalDateTime;\n\n");
        sb.append(String.format("/**\n * %s 创建/更新请求 DTO\n */\n", className));
        sb.append(String.format("public record %sSaveRequest(\n", className));

        List<CodegenColumnDefinition> saveCols = cols.stream()
                .filter(c -> !Boolean.TRUE.equals(c.isPk())
                        && !"created_at".equalsIgnoreCase(c.columnName())
                        && !"updated_at".equalsIgnoreCase(c.columnName())
                        && !"created_by".equalsIgnoreCase(c.columnName())
                        && !"updated_by".equalsIgnoreCase(c.columnName())
                        && !"deleted".equalsIgnoreCase(c.columnName())
                        && !"version".equalsIgnoreCase(c.columnName()))
                .toList();

        for (int i = 0; i < saveCols.size(); i++) {
            CodegenColumnDefinition col = saveCols.get(i);
            String javaType = resolveJavaType(col.javaType(), col.columnType());
            String fieldName = col.fieldName() != null && !col.fieldName().isBlank() ? col.fieldName() : toCamelCase(col.columnName(), false);

            if (Boolean.TRUE.equals(col.isRequired())) {
                if ("String".equals(javaType)) {
                    sb.append(String.format("        @NotBlank(message = \"%s不能为空\")\n", col.comment() != null ? col.comment() : fieldName));
                } else {
                    sb.append(String.format("        @NotNull(message = \"%s不能为空\")\n", col.comment() != null ? col.comment() : fieldName));
                }
            }

            sb.append(String.format("        %s %s%s\n", javaType, fieldName, (i < saveCols.size() - 1 ? "," : "")));
        }
        sb.append(") {}\n");
        return sb.toString();
    }

    private String generateQueryRequestDto(String basePkg, String module, String className, List<CodegenColumnDefinition> cols) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("package %s.%s.dto;\n\n", basePkg, module));
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("import java.time.LocalDateTime;\n\n");
        sb.append(String.format("/**\n * %s 分页查询请求 DTO\n */\n", className));
        sb.append(String.format("public record %sQueryRequest(\n", className));
        sb.append("        Integer pageNo,\n");
        sb.append("        Integer pageSize,\n");

        List<CodegenColumnDefinition> queryCols = cols.stream()
                .filter(c -> Boolean.TRUE.equals(c.isQuery()))
                .toList();

        for (int i = 0; i < queryCols.size(); i++) {
            CodegenColumnDefinition col = queryCols.get(i);
            String javaType = resolveJavaType(col.javaType(), col.columnType());
            String fieldName = col.fieldName() != null && !col.fieldName().isBlank() ? col.fieldName() : toCamelCase(col.columnName(), false);
            sb.append(String.format("        %s %s%s\n", javaType, fieldName, (i < queryCols.size() - 1 ? "," : "")));
        }
        sb.append(") {\n");
        sb.append("    public int getPageNo() { return pageNo != null && pageNo > 0 ? pageNo : 1; }\n");
        sb.append("    public int getPageSize() { return pageSize != null && pageSize > 0 ? pageSize : 10; }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String generateService(String basePkg, String module, String className, String varName, List<CodegenColumnDefinition> cols) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("package %s.%s.service;\n\n", basePkg, module));
        sb.append("import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;\n");
        sb.append("import com.baomidou.mybatisplus.extension.plugins.pagination.Page;\n");
        sb.append("import com.saasbasics.platform.common.exception.BizException;\n");
        sb.append(String.format("import %s.%s.dto.*;\n", basePkg, module));
        sb.append(String.format("import %s.%s.entity.%sEntity;\n", basePkg, module, className));
        sb.append(String.format("import %s.%s.mapper.%sMapper;\n", basePkg, module, className));
        sb.append("import org.springframework.stereotype.Service;\n");
        sb.append("import org.springframework.transaction.annotation.Transactional;\n");
        sb.append("import java.util.List;\n\n");
        sb.append("/**\n");
        sb.append(String.format(" * %s 业务逻辑服务层\n", className));
        sb.append(" */\n");
        sb.append("@Service\n");
        sb.append(String.format("public class %sService {\n\n", className));
        sb.append(String.format("    private final %sMapper %sMapper;\n\n", className, varName));
        sb.append(String.format("    public %sService(%sMapper %sMapper) {\n", className, className, varName));
        sb.append(String.format("        this.%sMapper = %sMapper;\n    }\n\n", varName, varName));

        // listPage
        sb.append(String.format("    public Page<%sResponse> listPage(%sQueryRequest request) {\n", className, className));
        sb.append(String.format("        Page<%sEntity> page = new Page<>(request.getPageNo(), request.getPageSize());\n", className));
        sb.append(String.format("        LambdaQueryWrapper<%sEntity> wrapper = new LambdaQueryWrapper<>();\n", className));

        for (CodegenColumnDefinition col : cols) {
            if (Boolean.TRUE.equals(col.isQuery())) {
                String fieldName = col.fieldName() != null && !col.fieldName().isBlank() ? col.fieldName() : toCamelCase(col.columnName(), false);
                String cap = toCamelCase(fieldName, true);
                String qType = col.queryType() != null ? col.queryType().toUpperCase() : "EQ";

                if ("LIKE".equals(qType)) {
                    sb.append(String.format("        if (request.%s() != null && !request.%s().isBlank()) {\n", fieldName, fieldName));
                    sb.append(String.format("            wrapper.like(%sEntity::get%s, request.%s().trim());\n        }\n", className, cap, fieldName));
                } else if ("EQ".equals(qType)) {
                    sb.append(String.format("        if (request.%s() != null) {\n", fieldName));
                    sb.append(String.format("            wrapper.eq(%sEntity::get%s, request.%s());\n        }\n", className, cap, fieldName));
                }
            }
        }
        sb.append(String.format("        wrapper.orderByDesc(%sEntity::getId);\n", className));
        sb.append(String.format("        Page<%sEntity> result = %sMapper.selectPage(page, wrapper);\n", className, varName));
        sb.append(String.format("        return (Page<%sResponse>) result.convert(this::toResponse);\n    }\n\n", className));

        // getById
        sb.append(String.format("    public %sResponse getById(Long id) {\n", className));
        sb.append(String.format("        %sEntity entity = %sMapper.selectById(id);\n", className, varName));
        sb.append("        if (entity == null) {\n");
        sb.append("            throw new BizException(\"DATA_NOT_FOUND\", \"未找到指定业务记录\");\n        }\n");
        sb.append("        return toResponse(entity);\n    }\n\n");

        // create
        sb.append("    @Transactional(rollbackFor = Exception.class)\n");
        sb.append(String.format("    public %sResponse create(%sSaveRequest request) {\n", className, className));
        sb.append(String.format("        %sEntity entity = new %sEntity();\n", className, className));
        sb.append("        applySaveRequest(entity, request);\n");
        sb.append(String.format("        %sMapper.insert(entity);\n", varName));
        sb.append("        return getById(entity.getId());\n    }\n\n");

        // update
        sb.append("    @Transactional(rollbackFor = Exception.class)\n");
        sb.append(String.format("    public %sResponse update(Long id, %sSaveRequest request) {\n", className, className));
        sb.append(String.format("        %sEntity entity = %sMapper.selectById(id);\n", className, varName));
        sb.append("        if (entity == null) {\n");
        sb.append("            throw new BizException(\"DATA_NOT_FOUND\", \"待更新数据不存在\");\n        }\n");
        sb.append("        applySaveRequest(entity, request);\n");
        sb.append(String.format("        %sMapper.updateById(entity);\n", varName));
        sb.append("        return getById(id);\n    }\n\n");

        // delete
        sb.append("    @Transactional(rollbackFor = Exception.class)\n");
        sb.append("    public void delete(Long id) {\n");
        sb.append(String.format("        %sMapper.deleteById(id);\n    }\n\n", varName));

        // converters
        sb.append(String.format("    private %sResponse toResponse(%sEntity e) {\n", className, className));
        sb.append(String.format("        return new %sResponse(\n", className));
        for (int i = 0; i < cols.size(); i++) {
            CodegenColumnDefinition col = cols.get(i);
            String fieldName = col.fieldName() != null && !col.fieldName().isBlank() ? col.fieldName() : toCamelCase(col.columnName(), false);
            String cap = toCamelCase(fieldName, true);
            sb.append(String.format("                e.get%s()%s\n", cap, (i < cols.size() - 1 ? "," : "")));
        }
        sb.append("        );\n    }\n\n");

        sb.append(String.format("    private void applySaveRequest(%sEntity entity, %sSaveRequest req) {\n", className, className));
        List<CodegenColumnDefinition> saveCols = cols.stream()
                .filter(c -> !Boolean.TRUE.equals(c.isPk())
                        && !"created_at".equalsIgnoreCase(c.columnName())
                        && !"updated_at".equalsIgnoreCase(c.columnName())
                        && !"created_by".equalsIgnoreCase(c.columnName())
                        && !"updated_by".equalsIgnoreCase(c.columnName())
                        && !"deleted".equalsIgnoreCase(c.columnName())
                        && !"version".equalsIgnoreCase(c.columnName()))
                .toList();
        for (CodegenColumnDefinition c : saveCols) {
            String fieldName = c.fieldName() != null && !c.fieldName().isBlank() ? c.fieldName() : toCamelCase(c.columnName(), false);
            String cap = toCamelCase(fieldName, true);
            sb.append(String.format("        entity.set%s(req.%s());\n", cap, fieldName));
        }
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String generateController(String basePkg, String module, String className, String varName, String bizName) {
        return String.format("""
                package %s.%s.controller;

                import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
                import com.saasbasics.platform.common.api.ApiResponse;
                import com.saasbasics.platform.common.auth.DataScope;
                import com.saasbasics.platform.common.auth.RequirePermission;
                import %s.%s.dto.*;
                import %s.%s.service.%sService;
                import jakarta.validation.Valid;
                import org.springframework.web.bind.annotation.*;

                /**
                 * %s (%s) 控制器 API
                 */
                @RestController
                @RequestMapping("/api/%ss")
                public class %sController {

                    private final %sService %sService;

                    public %sController(%sService %sService) {
                        this.%sService = %sService;
                    }

                    @GetMapping
                    @RequirePermission("biz_%s:query")
                    @DataScope(resource = "%s")
                    public ApiResponse<Page<%sResponse>> list(%sQueryRequest request) {
                        return ApiResponse.success(%sService.listPage(request));
                    }

                    @GetMapping("/{id}")
                    @RequirePermission("biz_%s:query")
                    @DataScope(resource = "%s")
                    public ApiResponse<%sResponse> detail(@PathVariable Long id) {
                        return ApiResponse.success(%sService.getById(id));
                    }

                    @PostMapping
                    @RequirePermission("biz_%s:create")
                    public ApiResponse<%sResponse> create(@Valid @RequestBody %sSaveRequest request) {
                        return ApiResponse.success(%sService.create(request));
                    }

                    @PutMapping("/{id}")
                    @RequirePermission("biz_%s:update")
                    public ApiResponse<%sResponse> update(@PathVariable Long id, @Valid @RequestBody %sSaveRequest request) {
                        return ApiResponse.success(%sService.update(id, request));
                    }

                    @DeleteMapping("/{id}")
                    @RequirePermission("biz_%s:delete")
                    public ApiResponse<Void> delete(@PathVariable Long id) {
                        %sService.delete(id);
                        return ApiResponse.success(null);
                    }
                }
                """,
                basePkg, module,
                basePkg, module,
                basePkg, module, className,
                bizName, className,
                module,
                className,
                className, varName,
                className, className, varName,
                varName, varName,
                module, module, className, className, varName,
                module, module, className, varName,
                module, className, className, varName,
                module, className, className, varName,
                module, varName
        );
    }

    private String generateFrontendApi(String module, String className) {
        return String.format("""
                import request from "@/api/request";

                export interface %sRecord {
                  id: number;
                  tenantId?: number;
                  [key: string]: any;
                }

                export interface %sQueryParams {
                  pageNo?: number;
                  pageSize?: number;
                  [key: string]: any;
                }

                export interface %sSaveForm {
                  [key: string]: any;
                }

                export function list%ss(params: %sQueryParams) {
                  return request.get("/api/%ss", { params });
                }

                export function get%s(id: number) {
                  return request.get(`/api/%ss/${id}`);
                }

                export function create%s(data: %sSaveForm) {
                  return request.post("/api/%ss", data);
                }

                export function update%s(id: number, data: %sSaveForm) {
                  return request.put(`/api/%ss/${id}`, data);
                }

                export function delete%s(id: number) {
                  return request.delete(`/api/%ss/${id}`);
                }
                """,
                className, className, className,
                className, className, module,
                className, module,
                className, className, module,
                className, className, module,
                className, module
        );
    }

    private String generateFrontendView(String module, String className, String bizName, List<CodegenColumnDefinition> cols) {
        StringBuilder sb = new StringBuilder();
        sb.append("<script setup lang=\"ts\">\n");
        sb.append("import { ref, reactive, onMounted } from \"vue\";\n");
        sb.append("import { ElMessage, ElMessageBox } from \"element-plus\";\n");
        sb.append("import { Search, Refresh, Plus, Edit, Delete, Download } from \"@element-plus/icons-vue\";\n");
        sb.append(String.format("import { list%ss, get%s, create%s, update%s, delete%s } from \"@/api/modules/%s\";\n\n",
                className, className, className, className, className, module));

        sb.append("const loading = ref(false);\n");
        sb.append("const tableData = ref<any[]>([]);\n");
        sb.append("const total = ref(0);\n");
        sb.append("const queryParams = reactive({\n");
        sb.append("  pageNo: 1,\n");
        sb.append("  pageSize: 10,\n");
        for (CodegenColumnDefinition col : cols) {
            if (Boolean.TRUE.equals(col.isQuery())) {
                String f = col.fieldName() != null && !col.fieldName().isBlank() ? col.fieldName() : toCamelCase(col.columnName(), false);
                sb.append(String.format("  %s: \"\",\n", f));
            }
        }
        sb.append("});\n\n");

        sb.append("const drawerVisible = ref(false);\n");
        sb.append("const drawerTitle = ref(\"\");\n");
        sb.append("const currentId = ref<number | null>(null);\n");
        sb.append("const formRef = ref<any>(null);\n");
        sb.append("const form = reactive<Record<string, any>>({});\n\n");

        sb.append("async function loadData() {\n");
        sb.append("  loading.value = true;\n");
        sb.append("  try {\n");
        sb.append(String.format("    const res: any = await list%ss(queryParams);\n", className));
        sb.append("    tableData.value = res?.data?.records || res?.records || [];\n");
        sb.append("    total.value = res?.data?.total || res?.total || 0;\n");
        sb.append("  } finally {\n");
        sb.append("    loading.value = false;\n");
        sb.append("  }\n");
        sb.append("}\n\n");

        sb.append("function handleQuery() {\n");
        sb.append("  queryParams.pageNo = 1;\n");
        sb.append("  loadData();\n");
        sb.append("}\n\n");

        sb.append("function handleReset() {\n");
        for (CodegenColumnDefinition col : cols) {
            if (Boolean.TRUE.equals(col.isQuery())) {
                String f = col.fieldName() != null && !col.fieldName().isBlank() ? col.fieldName() : toCamelCase(col.columnName(), false);
                sb.append(String.format("  queryParams.%s = \"\";\n", f));
            }
        }
        sb.append("  handleQuery();\n");
        sb.append("}\n\n");

        sb.append("function handleCreate() {\n");
        sb.append("  currentId.value = null;\n");
        sb.append(String.format("  drawerTitle.value = \"新建%s\";\n", bizName));
        sb.append("  Object.keys(form).forEach((k) => delete form[k]);\n");
        sb.append("  drawerVisible.value = true;\n");
        sb.append("}\n\n");

        sb.append("async function handleEdit(row: any) {\n");
        sb.append("  currentId.value = row.id;\n");
        sb.append(String.format("  drawerTitle.value = \"编辑%s\";\n", bizName));
        sb.append("  try {\n");
        sb.append(String.format("    const res: any = await get%s(row.id);\n", className));
        sb.append("    Object.assign(form, res?.data || res || row);\n");
        sb.append("    drawerVisible.value = true;\n");
        sb.append("  } catch (err: any) {\n");
        sb.append("    ElMessage.error(err?.message || \"获取详情失败\");\n");
        sb.append("  }\n");
        sb.append("}\n\n");

        sb.append("async function handleDelete(row: any) {\n");
        sb.append("  try {\n");
        sb.append(String.format("    await ElMessageBox.confirm(`确认删除该%s记录吗？`, \"警告\", { type: \"warning\" });\n", bizName));
        sb.append(String.format("    await delete%s(row.id);\n", className));
        sb.append("    ElMessage.success(\"删除成功\");\n");
        sb.append("    loadData();\n");
        sb.append("  } catch {}\n");
        sb.append("}\n\n");

        sb.append("async function submitForm() {\n");
        sb.append("  if (currentId.value) {\n");
        sb.append(String.format("    await update%s(currentId.value, form);\n", className));
        sb.append("    ElMessage.success(\"修改成功\");\n");
        sb.append("  } else {\n");
        sb.append(String.format("    await create%s(form);\n", className));
        sb.append("    ElMessage.success(\"创建成功\");\n");
        sb.append("  }\n");
        sb.append("  drawerVisible.value = false;\n");
        sb.append("  loadData();\n");
        sb.append("}\n\n");

        sb.append("onMounted(() => {\n  loadData();\n});\n");
        sb.append("</script>\n\n");

        // Template
        sb.append("<template>\n");
        sb.append(String.format("  <div class=\"%s-view\">\n", module));
        sb.append("    <!-- 检索过滤区 -->\n");
        sb.append("    <el-card shadow=\"never\" class=\"filter-card\">\n");
        sb.append("      <el-form :model=\"queryParams\" inline>\n");

        for (CodegenColumnDefinition col : cols) {
            if (Boolean.TRUE.equals(col.isQuery())) {
                String f = col.fieldName() != null && !col.fieldName().isBlank() ? col.fieldName() : toCamelCase(col.columnName(), false);
                String label = col.comment() != null ? col.comment() : f;
                sb.append(String.format("        <el-form-item label=\"%s\">\n", label));
                sb.append(String.format("          <el-input v-model=\"queryParams.%s\" placeholder=\"请输入%s\" clearable />\n", f, label));
                sb.append("        </el-form-item>\n");
            }
        }

        sb.append("        <el-form-item>\n");
        sb.append("          <el-button type=\"primary\" :icon=\"Search\" @click=\"handleQuery\">查询</el-button>\n");
        sb.append("          <el-button :icon=\"Refresh\" @click=\"handleReset\">重置</el-button>\n");
        sb.append("        </el-form-item>\n");
        sb.append("      </el-form>\n");
        sb.append("    </el-card>\n\n");

        sb.append("    <!-- 操作与表格主体 -->\n");
        sb.append("    <el-card shadow=\"never\" class=\"table-card\">\n");
        sb.append("      <div class=\"toolbar-bar\">\n");
        sb.append(String.format("        <el-button type=\"primary\" :icon=\"Plus\" @click=\"handleCreate\">新增%s</el-button>\n", bizName));
        sb.append("      </div>\n\n");

        sb.append("      <el-table v-loading=\"loading\" :data=\"tableData\" stripe border style=\"width: 100%; margin-top: 12px;\">\n");

        List<CodegenColumnDefinition> displayCols = cols.stream()
                .filter(c -> !"deleted".equalsIgnoreCase(c.columnName()) && !"version".equalsIgnoreCase(c.columnName()))
                .limit(8)
                .toList();

        for (CodegenColumnDefinition col : displayCols) {
            String f = col.fieldName() != null && !col.fieldName().isBlank() ? col.fieldName() : toCamelCase(col.columnName(), false);
            String label = col.comment() != null ? col.comment() : f;
            sb.append(String.format("        <el-table-column prop=\"%s\" label=\"%s\" min-width=\"120\" />\n", f, label));
        }

        sb.append("        <el-table-column label=\"操作\" width=\"150\" fixed=\"right\">\n");
        sb.append("          <template #default=\"{ row }\">\n");
        sb.append("            <el-button link type=\"primary\" :icon=\"Edit\" @click=\"handleEdit(row)\">编辑</el-button>\n");
        sb.append("            <el-button link type=\"danger\" :icon=\"Delete\" @click=\"handleDelete(row)\">删除</el-button>\n");
        sb.append("          </template>\n");
        sb.append("        </el-table-column>\n");
        sb.append("      </el-table>\n\n");

        sb.append("      <div class=\"pagination-bar\">\n");
        sb.append("        <el-pagination\n");
        sb.append("          v-model:current-page=\"queryParams.pageNo\"\n");
        sb.append("          v-model:page-size=\"queryParams.pageSize\"\n");
        sb.append("          :total=\"total\"\n");
        sb.append("          :page-sizes=\"[10, 20, 50, 100]\"\n");
        sb.append("          layout=\"total, sizes, prev, pager, next, jumper\"\n");
        sb.append("          @size-change=\"handleQuery\"\n");
        sb.append("          @current-change=\"loadData\"\n");
        sb.append("        />\n");
        sb.append("      </div>\n");
        sb.append("    </el-card>\n\n");

        // Drawer
        sb.append("    <!-- 抽屉表单 -->\n");
        sb.append("    <el-drawer v-model=\"drawerVisible\" :title=\"drawerTitle\" size=\"520px\">\n");
        sb.append("      <el-form ref=\"formRef\" :model=\"form\" label-width=\"100px\">\n");
        List<CodegenColumnDefinition> formCols = cols.stream()
                .filter(c -> !Boolean.TRUE.equals(c.isPk())
                        && !"created_at".equalsIgnoreCase(c.columnName())
                        && !"updated_at".equalsIgnoreCase(c.columnName())
                        && !"created_by".equalsIgnoreCase(c.columnName())
                        && !"updated_by".equalsIgnoreCase(c.columnName())
                        && !"deleted".equalsIgnoreCase(c.columnName())
                        && !"version".equalsIgnoreCase(c.columnName()))
                .toList();

        for (CodegenColumnDefinition col : formCols) {
            String f = col.fieldName() != null && !col.fieldName().isBlank() ? col.fieldName() : toCamelCase(col.columnName(), false);
            String label = col.comment() != null ? col.comment() : f;
            sb.append(String.format("        <el-form-item label=\"%s\" prop=\"%s\">\n", label, f));
            sb.append(String.format("          <el-input v-model=\"form.%s\" placeholder=\"请输入%s\" />\n", f, label));
            sb.append("        </el-form-item>\n");
        }

        sb.append("      </el-form>\n");
        sb.append("      <template #footer>\n");
        sb.append("        <el-button @click=\"drawerVisible = false\">取消</el-button>\n");
        sb.append("        <el-button type=\"primary\" @click=\"submitForm\">保存</el-button>\n");
        sb.append("      </template>\n");
        sb.append("    </el-drawer>\n");
        sb.append("  </div>\n");
        sb.append("</template>\n\n");

        sb.append("<style scoped>\n");
        sb.append(String.format(".%s-view { display: flex; flex-direction: column; gap: 16px; }\n", module));
        sb.append(".toolbar-bar { display: flex; justify-content: flex-start; }\n");
        sb.append(".pagination-bar { display: flex; justify-content: flex-end; margin-top: 16px; }\n");
        sb.append("</style>\n");

        return sb.toString();
    }

    private String generateSqlMigration(String module, String className, String bizName, String tableName, List<CodegenColumnDefinition> cols) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- -----------------------------------------------------\n");
        sb.append(String.format("-- %s (%s) 数据库 DDL 与权限菜单种子数据\n", bizName, className));
        sb.append("-- -----------------------------------------------------\n\n");

        sb.append(String.format("CREATE TABLE IF NOT EXISTS `%s` (\n", tableName));
        for (int i = 0; i < cols.size(); i++) {
            CodegenColumnDefinition c = cols.get(i);
            String colName = c.columnName();
            String colType = c.columnType() != null ? c.columnType() : "VARCHAR(128)";
            String isNull = Boolean.TRUE.equals(c.isRequired()) ? "NOT NULL" : "DEFAULT NULL";
            String autoInc = Boolean.TRUE.equals(c.isPk()) ? "AUTO_INCREMENT" : "";
            String comment = c.comment() != null ? String.format("COMMENT '%s'", c.comment()) : "";

            sb.append(String.format("  `%s` %s %s %s %s,\n",
                    colName, colType, isNull, autoInc, comment));
        }

        if (cols.stream().noneMatch(c -> "created_at".equalsIgnoreCase(c.columnName()))) {
            sb.append("  `created_by` bigint unsigned NOT NULL DEFAULT 0 COMMENT '创建人ID',\n");
            sb.append("  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n");
            sb.append("  `updated_by` bigint unsigned NOT NULL DEFAULT 0 COMMENT '最后修改人ID',\n");
            sb.append("  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',\n");
            sb.append("  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0-未删除,1-已删除)',\n");
            sb.append("  `deleted_at` datetime(3) DEFAULT NULL COMMENT '删除时间',\n");
            sb.append("  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',\n");
            sb.append("  `remark` varchar(500) DEFAULT NULL COMMENT '备注',\n");
        }

        cols.stream().filter(c -> Boolean.TRUE.equals(c.isPk())).findFirst().ifPresent(pk ->
                sb.append(String.format("  PRIMARY KEY (`%s`)\n", pk.columnName()))
        );
        sb.append(String.format(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='%s';\n\n", bizName));

        sb.append("-- 权限与菜单注册\n");
        sb.append(String.format("INSERT INTO system_menu (parent_id, menu_name, router_path, component_path, permission_code, menu_type, sort_no, visible, status, icon)\n"));
        sb.append(String.format("VALUES (1, '%s', '/%s', '@/views/%s/%sView.vue', 'biz_%s:query', 'MENU', 10, 1, 1, 'Document');\n",
                bizName, module, module, className, module));

        return sb.toString();
    }

    private String cleanTableName(String tableName) {
        if (tableName == null) return "demo";
        if (tableName.startsWith("biz_")) return tableName.substring(4);
        if (tableName.startsWith("sys_")) return tableName.substring(4);
        if (tableName.startsWith("gen_")) return tableName.substring(4);
        return tableName;
    }

    private String extractModule(String tableName) {
        String clean = cleanTableName(tableName);
        int idx = clean.indexOf('_');
        return idx > 0 ? clean.substring(0, idx) : clean;
    }

    private String toCamelCase(String s, boolean capitalizeFirst) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = capitalizeFirst;
        for (char c : s.toCharArray()) {
            if (c == '_' || c == '-') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    sb.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    sb.append(Character.toLowerCase(c));
                }
            }
        }
        return sb.toString();
    }

    private String resolveJavaType(String specifiedType, String colType) {
        if (specifiedType != null && !specifiedType.isBlank()) {
            return specifiedType;
        }
        if (colType == null) return "String";
        String upper = colType.toUpperCase();
        if (upper.contains("BIGINT")) return "Long";
        if (upper.contains("TINYINT(1)") || upper.contains("BOOLEAN") || upper.contains("BIT")) return "Boolean";
        if (upper.contains("INT") || upper.contains("TINYINT") || upper.contains("SMALLINT")) return "Integer";
        if (upper.contains("DECIMAL") || upper.contains("NUMERIC")) return "BigDecimal";
        if (upper.contains("DATETIME") || upper.contains("TIMESTAMP")) return "LocalDateTime";
        if (upper.contains("DATE")) return "LocalDate";
        return "String";
    }

    private String generateSubEntity(String basePkg, String module, String subClass, String subTable, String subFk) {
        return "package " + basePkg + "." + module + ".entity;\n\n" +
                "import com.baomidou.mybatisplus.annotation.*;\n" +
                "import java.math.BigDecimal;\n" +
                "import java.time.LocalDateTime;\n\n" +
                "@TableName(\"" + subTable + "\")\n" +
                "public class " + subClass + "Entity {\n\n" +
                "    @TableId(type = IdType.AUTO)\n" +
                "    private Long id;\n" +
                "    private Long tenantId;\n" +
                "    private Long " + toCamelCase(subFk, false) + ";\n" +
                "    private String itemName;\n" +
                "    private Integer quantity;\n" +
                "    private BigDecimal unitPrice;\n" +
                "    private BigDecimal subtotal;\n" +
                "    private String remark;\n" +
                "    private LocalDateTime createdAt;\n\n" +
                "    // Getters and Setters\n" +
                "    public Long getId() { return id; }\n" +
                "    public void setId(Long id) { this.id = id; }\n" +
                "    public Long getTenantId() { return tenantId; }\n" +
                "    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }\n" +
                "    public Long get" + toCamelCase(subFk, true) + "() { return " + toCamelCase(subFk, false) + "; }\n" +
                "    public void set" + toCamelCase(subFk, true) + "(Long val) { this." + toCamelCase(subFk, false) + " = val; }\n" +
                "    public String getItemName() { return itemName; }\n" +
                "    public void setItemName(String itemName) { this.itemName = itemName; }\n" +
                "    public Integer getQuantity() { return quantity; }\n" +
                "    public void setQuantity(Integer quantity) { this.quantity = quantity; }\n" +
                "    public BigDecimal getUnitPrice() { return unitPrice; }\n" +
                "    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }\n" +
                "    public BigDecimal getSubtotal() { return subtotal; }\n" +
                "    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }\n" +
                "    public String getRemark() { return remark; }\n" +
                "    public void setRemark(String remark) { this.remark = remark; }\n" +
                "    public LocalDateTime getCreatedAt() { return createdAt; }\n" +
                "    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }\n" +
                "}\n";
    }
}
