package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.auth.DataPermissionContext;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.iam.dto.AccessSubjectProfile;
import com.saasbasics.platform.modules.iam.dto.DataPermissionRuleResponse;
import com.saasbasics.platform.modules.iam.dto.DataPermissionRuleSaveRequest;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.DataPermissionRuleEntity;
import com.saasbasics.platform.modules.iam.mapper.DataPermissionRuleMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class DataPermissionRuleService {

    private final ObjectProvider<DataPermissionRuleMapper> dataPermissionRuleMapperProvider;
    private final AccessSubjectProfileService accessSubjectProfileService;
    private final AuditTrailService auditTrailService;
    private final ObjectMapper objectMapper;

    public DataPermissionRuleService(ObjectProvider<DataPermissionRuleMapper> dataPermissionRuleMapperProvider,
                                     AccessSubjectProfileService accessSubjectProfileService,
                                     AuditTrailService auditTrailService,
                                     ObjectMapper objectMapper) {
        this.dataPermissionRuleMapperProvider = dataPermissionRuleMapperProvider;
        this.accessSubjectProfileService = accessSubjectProfileService;
        this.auditTrailService = auditTrailService;
        this.objectMapper = objectMapper;
    }

    public List<DataPermissionRuleResponse> listRules() {
        Long tenantId = requiredTenantId();
        return requiredMapper().selectRuleList()
                .stream()
                .filter(rule -> tenantId.equals(rule.tenantId()))
                .toList();
    }

    public DataPermissionRuleResponse getRule(Long id) {
        DataPermissionRuleResponse response = requiredMapper().selectRuleById(id);
        if (response == null) {
            throw new BizException("IAM_DATA_PERMISSION_RULE_NOT_FOUND", "数据权限规则不存在");
        }
        return response;
    }

    public DataPermissionRuleResponse createRule(DataPermissionRuleSaveRequest request) {
        DataPermissionRuleEntity entity = new DataPermissionRuleEntity();
        apply(entity, request);
        requiredMapper().insert(entity);
        auditTrailService.record("iam", "data_permission_rule", String.valueOf(entity.getId()), "CREATE", null, request.scopeType(), true);
        return getRule(entity.getId());
    }

    public DataPermissionRuleResponse updateRule(Long id, DataPermissionRuleSaveRequest request) {
        DataPermissionRuleEntity entity = requiredEntity(id);
        apply(entity, request);
        requiredMapper().updateById(entity);
        auditTrailService.record("iam", "data_permission_rule", String.valueOf(id), "UPDATE", null, request.scopeType(), true);
        return getRule(id);
    }

    public DataPermissionRuleResponse updateStatus(Long id, StatusUpdateRequest request) {
        DataPermissionRuleEntity entity = requiredEntity(id);
        entity.setStatus(request.status());
        requiredMapper().updateById(entity);
        auditTrailService.record("iam", "data_permission_rule", String.valueOf(id), "STATUS", null, request.status(), true);
        return getRule(id);
    }

    public DataPermissionSqlSpec resolveCurrentSpec() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null) {
            return DataPermissionSqlSpec.denyAll(null);
        }
        String resourceCode = DataPermissionContext.getResourceCode();
        if (resourceCode == null || resourceCode.isBlank()) {
            return DataPermissionSqlSpec.allowAll(principal.tenantId());
        }
        return resolveSpec(principal, resourceCode);
    }

    public DataPermissionSqlSpec resolveSpec(AuthPrincipal principal, String resourceCode) {
        if (principal == null) {
            return DataPermissionSqlSpec.denyAll(null);
        }

        AccessSubjectProfile subjectProfile = accessSubjectProfileService.load(principal.tenantId(), principal.userId());
        List<DataPermissionRuleEntity> rules = requiredMapper().selectList(new LambdaQueryWrapper<DataPermissionRuleEntity>()
                .eq(DataPermissionRuleEntity::getTenantId, principal.tenantId())
                .eq(DataPermissionRuleEntity::getResourceCode, resourceCode)
                .eq(DataPermissionRuleEntity::getDeleted, 0)
                .eq(DataPermissionRuleEntity::getStatus, "ENABLED"));

        List<DataPermissionRuleEntity> matchedRules = rules.stream()
                .filter(rule -> subjectProfile.matches(rule.getSubjectType(), rule.getSubjectValue()))
                .toList();

        if (matchedRules.isEmpty()) {
            return DataPermissionSqlSpec.denyAll(principal.tenantId());
        }

        DataPermissionSqlSpec spec = new DataPermissionSqlSpec();
        spec.setTenantId(principal.tenantId());
        for (DataPermissionRuleEntity rule : matchedRules) {
            mergeRule(spec, subjectProfile, rule);
            if (spec.isAllowAll()) {
                return spec;
            }
        }
        if (!spec.hasConstraints()) {
            spec.setDenyAll(true);
        }
        return spec;
    }

    private void mergeRule(DataPermissionSqlSpec spec, AccessSubjectProfile subjectProfile, DataPermissionRuleEntity rule) {
        switch (rule.getScopeType()) {
            case "TENANT" -> spec.setAllowAll(true);
            case "PERSONAL", "SELF" -> {
                spec.addUserId(subjectProfile.userId());
                spec.addEmployeeId(subjectProfile.employeeId());
            }
            case "DEPARTMENT" -> {
                if (subjectProfile.departmentId() != null) {
                    spec.addDepartmentIds(accessSubjectProfileService.expandDepartmentTree(
                            subjectProfile.tenantId(),
                            List.of(subjectProfile.departmentId())
                    ));
                }
            }
            case "POSITION" -> spec.addPositionId(subjectProfile.positionId());
            case "COMPANY" -> {
                if (subjectProfile.companyDepartmentId() != null) {
                    spec.addDepartmentIds(accessSubjectProfileService.expandDepartmentTree(
                            subjectProfile.tenantId(),
                            List.of(subjectProfile.companyDepartmentId())
                    ));
                }
            }
            case "ROLE" -> spec.addRoleIds(subjectProfile.roleIds());
            case "CUSTOM" -> mergeCustomRule(spec, rule.getConfigJson());
            default -> spec.setDenyAll(true);
        }
    }

    private void mergeCustomRule(DataPermissionSqlSpec spec, String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(configJson);
            appendLongArray(spec::addUserId, root.path("userIds"));
            appendLongArray(spec::addEmployeeId, root.path("employeeIds"));
            appendLongArray(spec::addDepartmentId, root.path("departmentIds"));
            appendLongArray(spec::addPositionId, root.path("positionIds"));
            appendLongArray(spec::addRoleId, root.path("roleIds"));
            if (root.path("scopeType").asText("").equalsIgnoreCase("TENANT")) {
                spec.setAllowAll(true);
            }
        } catch (Exception ignored) {
            spec.setDenyAll(true);
        }
    }

    private void appendLongArray(java.util.function.Consumer<Long> consumer, JsonNode array) {
        if (array == null || !array.isArray()) {
            return;
        }
        array.forEach(node -> {
            if (node.canConvertToLong()) {
                consumer.accept(node.longValue());
            }
        });
    }

    private void apply(DataPermissionRuleEntity entity, DataPermissionRuleSaveRequest request) {
        entity.setTenantId(requiredTenantId());
        entity.setResourceCode(request.resourceCode());
        entity.setResourceName(request.resourceName());
        entity.setSubjectType(request.subjectType());
        entity.setSubjectValue(request.subjectValue());
        entity.setScopeType(request.scopeType());
        entity.setConfigJson(request.configJson());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private DataPermissionRuleEntity requiredEntity(Long id) {
        DataPermissionRuleEntity entity = requiredMapper().selectById(id);
        Long tenantId = requiredTenantId();
        if (entity == null
                || (entity.getDeleted() != null && entity.getDeleted() == 1)
                || !tenantId.equals(entity.getTenantId())) {
            throw new BizException("IAM_DATA_PERMISSION_RULE_NOT_FOUND", "数据权限规则不存在");
        }
        return entity;
    }

    private Long requiredTenantId() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.tenantId() == null) {
            throw new BizException("AUTH_UNAUTHORIZED", "Authentication is required");
        }
        return principal.tenantId();
    }

    private DataPermissionRuleMapper requiredMapper() {
        DataPermissionRuleMapper mapper = dataPermissionRuleMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "数据权限规则需要数据库连接");
        }
        return mapper;
    }
}
