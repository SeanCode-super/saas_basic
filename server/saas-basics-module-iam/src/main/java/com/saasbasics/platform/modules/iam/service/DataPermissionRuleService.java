package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.auth.DataPermissionContext;
import com.saasbasics.platform.common.auth.DataPermissionProvider;
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
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class DataPermissionRuleService implements DataPermissionProvider {

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

    @Override
    public DataPermissionSqlSpec resolveDataPermission(String resourceCode) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null) {
            return DataPermissionSqlSpec.denyAll(null);
        }
        return resolveSpec(principal, resourceCode);
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

        // 1. 平台管理员或通配符权限直接放行
        boolean isSuperAdmin = "PLATFORM".equalsIgnoreCase(principal.userType())
                || (principal.permissions() != null && (principal.permissions().contains("*") || principal.permissions().contains("admin")));
        if (isSuperAdmin) {
            return DataPermissionSqlSpec.allowAll(principal.tenantId());
        }

        AccessSubjectProfile subjectProfile = accessSubjectProfileService.load(principal.tenantId(), principal.userId());
        List<DataPermissionRuleEntity> rules = requiredMapper().selectList(new LambdaQueryWrapper<DataPermissionRuleEntity>()
                .eq(DataPermissionRuleEntity::getTenantId, principal.tenantId())
                .eq(DataPermissionRuleEntity::getResourceCode, resourceCode)
                .eq(DataPermissionRuleEntity::getDeleted, 0)
                .eq(DataPermissionRuleEntity::getStatus, "ENABLED"));

        if (rules.isEmpty()) {
            // 当该资源未配置行级规则时，默认租户内放行（避免误杀无规则配置的基础资源）
            return DataPermissionSqlSpec.allowAll(principal.tenantId());
        }

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
        String scopeType = rule.getScopeType() != null ? rule.getScopeType().toUpperCase(Locale.ROOT) : "";
        switch (scopeType) {
            case "ALL", "TENANT" -> spec.setAllowAll(true);
            case "ORG_AND_CHILDREN" -> {
                if (subjectProfile.companyDepartmentId() != null) {
                    List<Long> orgIds = accessSubjectProfileService.expandDepartmentTree(
                            subjectProfile.tenantId(),
                            List.of(subjectProfile.companyDepartmentId())
                    );
                    spec.addDepartmentIds(orgIds);
                    spec.addOrgUnitIds(orgIds);
                }
                if (subjectProfile.departmentId() != null) {
                    List<Long> unitIds = accessSubjectProfileService.expandDepartmentTree(
                            subjectProfile.tenantId(),
                            List.of(subjectProfile.departmentId())
                    );
                    spec.addDepartmentIds(unitIds);
                    spec.addOrgUnitIds(unitIds);
                }
            }
            case "ORG_ONLY", "COMPANY" -> {
                if (subjectProfile.companyDepartmentId() != null) {
                    spec.addDepartmentId(subjectProfile.companyDepartmentId());
                    spec.addOrganizationId(subjectProfile.companyDepartmentId());
                }
            }
            case "DEPT_AND_CHILDREN", "DEPARTMENT" -> {
                if (subjectProfile.departmentId() != null) {
                    List<Long> deptIds = accessSubjectProfileService.expandDepartmentTree(
                            subjectProfile.tenantId(),
                            List.of(subjectProfile.departmentId())
                    );
                    spec.addDepartmentIds(deptIds);
                    spec.addOrgUnitIds(deptIds);
                }
            }
            case "DEPT_ONLY" -> {
                if (subjectProfile.departmentId() != null) {
                    spec.addDepartmentId(subjectProfile.departmentId());
                    spec.addOrgUnitId(subjectProfile.departmentId());
                }
            }
            case "SELF_AND_SUBORDINATES", "SELF", "PERSONAL" -> {
                spec.addUserId(subjectProfile.userId());
                spec.addEmployeeId(subjectProfile.employeeId());
            }
            case "POSITION" -> spec.addPositionId(subjectProfile.positionId());
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
            appendLongArray(spec::addOrgUnitId, root.path("orgUnitIds"));
            appendLongArray(spec::addOrganizationId, root.path("organizationIds"));
            appendLongArray(spec::addPositionId, root.path("positionIds"));
            appendLongArray(spec::addRoleId, root.path("roleIds"));
            if (root.path("treePaths").isArray()) {
                root.path("treePaths").forEach(n -> spec.addTreePath(n.asText()));
            }
            if (root.path("conditions").isArray()) {
                root.path("conditions").forEach(condNode -> {
                    String field = condNode.path("field").asText(null);
                    String op = condNode.path("operator").asText("EQ");
                    JsonNode valNode = condNode.path("value");
                    Object val = null;
                    if (valNode.isIntegralNumber()) {
                        val = valNode.longValue();
                    } else if (valNode.isFloatingPointNumber()) {
                        val = valNode.doubleValue();
                    } else if (valNode.isBoolean()) {
                        val = valNode.booleanValue();
                    } else if (valNode.isArray()) {
                        List<Object> list = new java.util.ArrayList<>();
                        valNode.forEach(n -> list.add(n.isNumber() ? n.numberValue() : n.asText()));
                        val = list;
                    } else if (!valNode.isNull()) {
                        val = valNode.asText();
                    }
                    if (field != null && !field.isBlank()) {
                        spec.addAbacCondition(field, op, val);
                    }
                });
            }
            String customScope = root.path("scopeType").asText("");
            if ("TENANT".equalsIgnoreCase(customScope) || "ALL".equalsIgnoreCase(customScope)) {
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
