package com.saasbasics.platform.modules.codegen.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.codegen.dto.CodegenProjectResponse;
import com.saasbasics.platform.modules.codegen.dto.CodegenProjectSaveRequest;
import com.saasbasics.platform.modules.codegen.entity.CodegenProjectEntity;
import com.saasbasics.platform.modules.codegen.mapper.CodegenProjectMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class CodegenService {

    private final ObjectProvider<CodegenProjectMapper> projectMapperProvider;

    public CodegenService(ObjectProvider<CodegenProjectMapper> projectMapperProvider) {
        this.projectMapperProvider = projectMapperProvider;
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
}
