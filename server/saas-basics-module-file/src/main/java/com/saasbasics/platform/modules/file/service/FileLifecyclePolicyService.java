package com.saasbasics.platform.modules.file.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.file.dto.FileLifecyclePolicyResponse;
import com.saasbasics.platform.modules.file.dto.FileLifecyclePolicySaveRequest;
import com.saasbasics.platform.modules.file.entity.FileLifecyclePolicyEntity;
import com.saasbasics.platform.modules.file.mapper.FileLifecyclePolicyMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class FileLifecyclePolicyService {

    private final ObjectProvider<FileLifecyclePolicyMapper> mapperProvider;

    public FileLifecyclePolicyService(ObjectProvider<FileLifecyclePolicyMapper> mapperProvider) {
        this.mapperProvider = mapperProvider;
    }

    public List<FileLifecyclePolicyResponse> listPolicies() {
        FileLifecyclePolicyMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        return mapper.selectPolicyList();
    }

    public FileLifecyclePolicyResponse createPolicy(FileLifecyclePolicySaveRequest request) {
        FileLifecyclePolicyEntity entity = new FileLifecyclePolicyEntity();
        apply(entity, request);
        requiredMapper().insert(entity);
        return getPolicy(entity.getId());
    }

    public FileLifecyclePolicyResponse updatePolicy(Long id, FileLifecyclePolicySaveRequest request) {
        FileLifecyclePolicyMapper mapper = requiredMapper();
        FileLifecyclePolicyEntity entity = mapper.selectById(id);
        if (entity == null || Objects.equals(entity.getDeleted(), 1)) {
            throw new BizException("FILE_LIFECYCLE_POLICY_NOT_FOUND", "File lifecycle policy not found");
        }
        apply(entity, request);
        mapper.updateById(entity);
        return getPolicy(id);
    }

    public FileLifecyclePolicyResponse getPolicy(Long id) {
        FileLifecyclePolicyResponse response = requiredMapper().selectPolicyById(id);
        if (response == null) {
            throw new BizException("FILE_LIFECYCLE_POLICY_NOT_FOUND", "File lifecycle policy not found");
        }
        return response;
    }

    private void apply(FileLifecyclePolicyEntity entity, FileLifecyclePolicySaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setPolicyCode(request.policyCode());
        entity.setPolicyName(request.policyName());
        entity.setFileScope(request.fileScope());
        entity.setRetentionDays(request.retentionDays());
        entity.setArchiveAfterDays(request.archiveAfterDays());
        entity.setDeleteAfterDays(request.deleteAfterDays());
        entity.setDeduplicateEnabled(request.deduplicateEnabled());
        entity.setVersionRetentionCount(request.versionRetentionCount());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private FileLifecyclePolicyMapper requiredMapper() {
        FileLifecyclePolicyMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Write operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
