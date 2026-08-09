package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.dto.ApiResourceResponse;
import com.saasbasics.platform.modules.iam.dto.ApiResourceSaveRequest;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.ApiResourceEntity;
import com.saasbasics.platform.modules.iam.mapper.ApiResourceMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ApiResourceService {

    private final ObjectProvider<ApiResourceMapper> apiResourceMapperProvider;

    public ApiResourceService(ObjectProvider<ApiResourceMapper> apiResourceMapperProvider) {
        this.apiResourceMapperProvider = apiResourceMapperProvider;
    }

    public List<ApiResourceResponse> listApiResources() {
        return requiredMapper().selectApiResourceList();
    }

    public ApiResourceResponse getApiResource(Long id) {
        ApiResourceResponse resource = requiredMapper().selectApiResourceById(id);
        if (resource == null) {
            throw new BizException("IAM_API_RESOURCE_NOT_FOUND", "IAM api resource not found");
        }
        return resource;
    }

    public ApiResourceResponse createApiResource(ApiResourceSaveRequest request) {
        ApiResourceEntity entity = new ApiResourceEntity();
        apply(entity, request);
        requiredMapper().insert(entity);
        return getApiResource(entity.getId());
    }

    public ApiResourceResponse updateApiResource(Long id, ApiResourceSaveRequest request) {
        ApiResourceMapper mapper = requiredMapper();
        ApiResourceEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("IAM_API_RESOURCE_NOT_FOUND", "IAM api resource not found");
        }
        apply(entity, request);
        mapper.updateById(entity);
        return getApiResource(id);
    }

    public ApiResourceResponse updateStatus(Long id, StatusUpdateRequest request) {
        ApiResourceMapper mapper = requiredMapper();
        ApiResourceEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("IAM_API_RESOURCE_NOT_FOUND", "IAM api resource not found");
        }
        entity.setStatus(request.status());
        mapper.updateById(entity);
        return getApiResource(id);
    }

    private void apply(ApiResourceEntity entity, ApiResourceSaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setResourceCode(request.resourceCode());
        entity.setResourceName(request.resourceName());
        entity.setHttpMethod(request.httpMethod());
        entity.setUrlPattern(request.urlPattern());
        entity.setAuthRequired(request.authRequired());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private ApiResourceMapper requiredMapper() {
        ApiResourceMapper mapper = apiResourceMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "API resource write operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
