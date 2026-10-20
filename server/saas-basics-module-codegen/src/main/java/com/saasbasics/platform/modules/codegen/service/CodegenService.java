package com.saasbasics.platform.modules.codegen.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantIgnore;
import com.saasbasics.platform.modules.codegen.dto.*;
import com.saasbasics.platform.modules.codegen.dto.CodegenTableDefinitionRequest.CodegenColumnDefinition;
import com.saasbasics.platform.modules.codegen.engine.CodegenEngineService;
import com.saasbasics.platform.modules.codegen.entity.CodegenProjectEntity;
import com.saasbasics.platform.modules.codegen.mapper.CodegenProjectMapper;
import com.saasbasics.platform.modules.codegen.mapper.CodegenSchemaMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class CodegenService {

    private final ObjectProvider<CodegenProjectMapper> projectMapperProvider;
    private final CodegenEngineService codegenEngineService;

    public CodegenService(
            ObjectProvider<CodegenProjectMapper> projectMapperProvider,
            CodegenEngineService codegenEngineService
    ) {
        this.projectMapperProvider = projectMapperProvider;
        this.codegenEngineService = codegenEngineService;
    }

    @TenantIgnore(reason = "Query physical tables across database schema")
    public List<DbTableResponse> listDbTables() {
        return requiredMapper().selectDbTables();
    }

    @TenantIgnore(reason = "Query physical table columns across database schema")
    public List<CodegenColumnDefinition> getDbTableColumns(String tableName) {
        List<DbColumnResponse> dbCols = requiredMapper().selectDbColumns(tableName);
        List<CodegenColumnDefinition> cols = new ArrayList<>();

        for (DbColumnResponse c : dbCols) {
            String colName = c.columnName();
            String fieldName = toCamelCase(colName, false);
            String javaType = resolveJavaType(c.dataType(), c.columnType());
            boolean isPk = "PRI".equalsIgnoreCase(c.columnKey());
            boolean isReq = "NO".equalsIgnoreCase(c.isNullable());
            boolean isQuery = isCandidateQuery(colName, javaType);
            String qType = "String".equals(javaType) && !colName.endsWith("_status") && !colName.endsWith("_type") ? "LIKE" : "EQ";
            String compType = resolveComponentType(colName, javaType, c.columnType());
            String dictCode = resolveDictCode(colName);

            cols.add(new CodegenColumnDefinition(
                    colName,
                    fieldName,
                    c.columnType(),
                    javaType,
                    (c.columnComment() != null && !c.columnComment().isBlank()) ? c.columnComment() : fieldName,
                    isPk,
                    isReq,
                    isQuery,
                    qType,
                    compType,
                    dictCode
            ));
        }

        return cols;
    }

    public List<CodegenPreviewFileResponse> previewCode(CodegenTableDefinitionRequest request) {
        return codegenEngineService.generateFiles(request);
    }

    public byte[] downloadZip(CodegenTableDefinitionRequest request) {
        try {
            return codegenEngineService.generateZipArchive(request);
        } catch (IOException e) {
            throw new BizException("ZIP_GENERATE_ERROR", "生成代码压缩包失败: " + e.getMessage());
        }
    }

    public java.util.Map<String, Object> writeToDisk(CodegenTableDefinitionRequest request) {
        List<CodegenPreviewFileResponse> files = codegenEngineService.generateFiles(request);
        List<String> writtenPaths = new ArrayList<>();

        java.io.File currentDir = new java.io.File(System.getProperty("user.dir", "."));
        java.io.File projectRoot = currentDir;
        if (new java.io.File(currentDir, "server").exists() && new java.io.File(currentDir, "web").exists()) {
            projectRoot = currentDir;
        } else if (currentDir.getParentFile() != null && new java.io.File(currentDir.getParentFile(), "web").exists()) {
            projectRoot = currentDir.getParentFile();
        }

        for (CodegenPreviewFileResponse f : files) {
            String relPath = f.path();
            java.io.File targetFile = new java.io.File(projectRoot, relPath);
            java.io.File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            try {
                java.nio.file.Files.writeString(targetFile.toPath(), f.code(), java.nio.charset.StandardCharsets.UTF_8);
                writtenPaths.add(relPath);
            } catch (IOException e) {
                throw new BizException("FILE_WRITE_ERROR", "写入文件失败: " + relPath + ", " + e.getMessage());
            }
        }

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("projectRoot", projectRoot.getAbsolutePath());
        result.put("count", writtenPaths.size());
        result.put("files", writtenPaths);
        return result;
    }

    public List<CodegenProjectResponse> listProjects() {
        return requiredMapper().selectProjectList();
    }

    public CodegenProjectResponse getProject(Long id) {
        CodegenProjectResponse project = requiredMapper().selectProjectById(id);
        if (project == null) {
            throw new BizException("CODEGEN_PROJECT_NOT_FOUND", "Codegen project not found");
        }
        return project;
    }

    public CodegenProjectResponse createProject(CodegenProjectSaveRequest request) {
        CodegenProjectEntity entity = new CodegenProjectEntity();
        apply(entity, request);
        requiredMapper().insert(entity);
        return getProject(entity.getId());
    }

    public CodegenProjectResponse updateProject(Long id, CodegenProjectSaveRequest request) {
        CodegenProjectMapper mapper = requiredMapper();
        CodegenProjectEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("CODEGEN_PROJECT_NOT_FOUND", "Codegen project not found");
        }
        apply(entity, request);
        mapper.updateById(entity);
        return getProject(id);
    }

    private void apply(CodegenProjectEntity entity, CodegenProjectSaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setProjectCode(request.code());
        entity.setProjectName(request.name());
        entity.setBasePackage(request.basePackage());
        entity.setModulePrefix(request.modulePrefix());
        entity.setOutputMode(request.outputMode());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private CodegenProjectMapper requiredMapper() {
        CodegenProjectMapper mapper = projectMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Write operations require the db profile and MySQL connection");
        }
        return mapper;
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

    private String resolveJavaType(String dataType, String colType) {
        String dt = dataType != null ? dataType.toLowerCase() : "";
        String ct = colType != null ? colType.toUpperCase() : "";

        if (dt.contains("bigint")) return "Long";
        if (dt.contains("tinyint") && ct.contains("(1)")) return "Boolean";
        if (dt.contains("int")) return "Integer";
        if (dt.contains("decimal") || dt.contains("numeric")) return "BigDecimal";
        if (dt.contains("datetime") || dt.contains("timestamp")) return "LocalDateTime";
        if (dt.contains("date")) return "LocalDate";
        if (dt.contains("char") || dt.contains("text")) return "String";
        return "String";
    }

    private String resolveComponentType(String colName, String javaType, String colType) {
        if ("Boolean".equals(javaType)) return "switch";
        if ("LocalDate".equals(javaType) || "LocalDateTime".equals(javaType)) return "date";
        if ("Integer".equals(javaType) || "Long".equals(javaType) || "BigDecimal".equals(javaType)) {
            if ("id".equalsIgnoreCase(colName) || "tenant_id".equalsIgnoreCase(colName)) return "number";
            return "number";
        }
        if (colName.toLowerCase().endsWith("_status") || colName.toLowerCase().endsWith("_type")) {
            return "select";
        }
        if (colType != null && (colType.toUpperCase().contains("TEXT") || colType.toUpperCase().contains("500"))) {
            return "textarea";
        }
        return "input";
    }

    private boolean isCandidateQuery(String colName, String javaType) {
        String lower = colName.toLowerCase();
        if ("id".equals(lower) || "deleted".equals(lower) || "version".equals(lower) || "tenant_id".equals(lower)) {
            return false;
        }
        if (lower.contains("created_by") || lower.contains("updated_by")) {
            return false;
        }
        if (lower.endsWith("_no") || lower.endsWith("_code") || lower.endsWith("_name") || lower.endsWith("_title")) {
            return true;
        }
        if (lower.endsWith("_status") || lower.endsWith("_type")) {
            return true;
        }
        return false;
    }

    private String resolveDictCode(String colName) {
        String lower = colName.toLowerCase();
        if (lower.endsWith("_status")) {
            return lower + "_enum";
        }
        if (lower.endsWith("_type")) {
            return lower + "_enum";
        }
        return null;
    }
}
