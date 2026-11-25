package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.auth.DataPermissionContext;
import com.saasbasics.platform.common.auth.DataPermissionProvider;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.common.auth.SqlAstDataGuard;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.iam.dto.AccessSubjectProfile;
import com.saasbasics.platform.modules.iam.dto.DataPermissionRuleResponse;
import com.saasbasics.platform.modules.iam.dto.DataPermissionRuleSaveRequest;
import com.saasbasics.platform.modules.iam.dto.DataPermissionSqlPreviewRequest;
import com.saasbasics.platform.modules.iam.dto.DataPermissionSqlPreviewResponse;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.DataPermissionRuleEntity;
import com.saasbasics.platform.modules.iam.entity.RoleEntity;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.DataPermissionRuleMapper;
import com.saasbasics.platform.modules.iam.mapper.RoleMapper;
import com.saasbasics.platform.modules.organization.api.OrganizationDirectory;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.stereotype.Service;

@Service
public class DataPermissionRuleService implements DataPermissionProvider {

    private final ObjectProvider<DataPermissionRuleMapper> dataPermissionRuleMapperProvider;
    private final AccessSubjectProfileService accessSubjectProfileService;
    private final AuditTrailService auditTrailService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RoleMapper> roleMapperProvider;
    private final ObjectProvider<OrganizationDirectory> organizationDirectoryProvider;

    public DataPermissionRuleService(ObjectProvider<DataPermissionRuleMapper> dataPermissionRuleMapperProvider,
                                     AccessSubjectProfileService accessSubjectProfileService,
                                     AuditTrailService auditTrailService,
                                     ObjectMapper objectMapper) {
        this(
                dataPermissionRuleMapperProvider,
                accessSubjectProfileService,
                auditTrailService,
                objectMapper,
                new StaticListableBeanFactory().getBeanProvider(RoleMapper.class),
                null
        );
    }

    public DataPermissionRuleService(ObjectProvider<DataPermissionRuleMapper> dataPermissionRuleMapperProvider,
                                     AccessSubjectProfileService accessSubjectProfileService,
                                     AuditTrailService auditTrailService,
                                     ObjectMapper objectMapper,
                                     ObjectProvider<RoleMapper> roleMapperProvider) {
        this(dataPermissionRuleMapperProvider, accessSubjectProfileService, auditTrailService,
                objectMapper, roleMapperProvider, null);
    }

    @Autowired
    public DataPermissionRuleService(ObjectProvider<DataPermissionRuleMapper> dataPermissionRuleMapperProvider,
                                     AccessSubjectProfileService accessSubjectProfileService,
                                     AuditTrailService auditTrailService,
                                     ObjectMapper objectMapper,
                                     ObjectProvider<RoleMapper> roleMapperProvider,
                                     ObjectProvider<OrganizationDirectory> organizationDirectoryProvider) {
        this.dataPermissionRuleMapperProvider = dataPermissionRuleMapperProvider;
        this.accessSubjectProfileService = accessSubjectProfileService;
        this.auditTrailService = auditTrailService;
        this.objectMapper = objectMapper;
        this.roleMapperProvider = roleMapperProvider;
        this.organizationDirectoryProvider = organizationDirectoryProvider;
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

    public DataPermissionSqlPreviewResponse previewSqlRewrite(DataPermissionSqlPreviewRequest request) {
        long startNs = System.nanoTime();
        String rawSql = request.sql() != null ? request.sql().trim() : "";
        if (rawSql.endsWith(";")) {
            rawSql = rawSql.substring(0, rawSql.length() - 1).trim();
        }
        AuthPrincipal principal = AuthContext.get();
        Long effectiveTenantId = request.tenantId() != null && request.tenantId() > 0
                ? request.tenantId()
                : (principal != null && principal.tenantId() != null ? principal.tenantId() : 1L);

        Statement parsed;
        try {
            parsed = CCJSqlParserUtil.parse(rawSql);
        } catch (Exception ex) {
            throw new BizException("INVALID_SQL_AST", "SQL 语法解析失败，无法通过安全校验: " + ex.getMessage());
        }
        if (!(parsed instanceof Select select)) {
            throw new BizException("INVALID_SQL_AST", "AST 安全改写探测仅允许 SELECT 查询语句");
        }

        injectTenantPredicate(select, effectiveTenantId);
        String tenantRewritten = select.toString();

        String resourceCode = request.resourceCode() != null && !request.resourceCode().isBlank()
                ? request.resourceCode().trim()
                : "iam:user:list";
        DataPermissionSqlSpec spec = principal != null
                ? resolveSpec(principal, resourceCode)
                : DataPermissionSqlSpec.allowAll(effectiveTenantId);

        String finalSql = tenantRewritten;
        String scopeType = "TENANT_ISOLATED";
        if (spec != null && spec.isDenyAll()) {
            finalSql = SqlAstDataGuard.rewriteSelectSql(
                    tenantRewritten,
                    table -> new SqlAstDataGuard.TableScopeConfig(spec, "organization_id", "org_unit_id", "created_by")
            );
            scopeType = "DENY_ALL";
        } else if (spec != null && !spec.isAllowAll() && spec.hasConstraints()) {
            finalSql = SqlAstDataGuard.rewriteSelectSql(
                    tenantRewritten,
                    table -> new SqlAstDataGuard.TableScopeConfig(spec, "organization_id", "org_unit_id", "created_by")
            );
            scopeType = "TENANT_AND_5D_SCOPE";
        }
        long elapsedMicros = Math.max(1L, (System.nanoTime() - startNs) / 1_000L);
        return new DataPermissionSqlPreviewResponse(rawSql, finalSql, scopeType, effectiveTenantId, elapsedMicros);
    }

    private static void injectTenantPredicate(Select select, Long tenantId) {
        if (select instanceof PlainSelect plainSelect) {
            FromItem fromItem = plainSelect.getFromItem();
            if (fromItem instanceof Table table) {
                Column tenantCol = table.getAlias() != null
                        ? new Column(new Table(table.getAlias().getName()), "tenant_id")
                        : new Column(table, "tenant_id");
                Expression tenantPred = new EqualsTo(tenantCol, new LongValue(tenantId));
                Expression existing = plainSelect.getWhere();
                if (existing == null) {
                    plainSelect.setWhere(tenantPred);
                } else {
                    Expression wrapped = existing instanceof Parenthesis ? existing : new Parenthesis(existing);
                    plainSelect.setWhere(new AndExpression(wrapped, tenantPred));
                }
            } else if (fromItem instanceof ParenthesedSelect subSelect && subSelect.getSelect() != null) {
                injectTenantPredicate(subSelect.getSelect(), tenantId);
            }
        } else if (select instanceof SetOperationList setOpList && setOpList.getSelects() != null) {
            for (Select child : setOpList.getSelects()) {
                injectTenantPredicate(child, tenantId);
            }
        } else if (select instanceof ParenthesedSelect parenthesed && parenthesed.getSelect() != null) {
            injectTenantPredicate(parenthesed.getSelect(), tenantId);
        }
    }

    @Override
    public DataPermissionSqlSpec resolveDataPermission(String resourceCode) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null) {
            return DataPermissionSqlSpec.denyAll(null);
        }
        return resolveSpec(principal, resourceCode);
    }

    @Override
    public DataPermissionSqlSpec resolveDataPermissionForUser(Long tenantId, Long userId, String resourceCode) {
        if (tenantId == null || userId == null) {
            return DataPermissionSqlSpec.denyAll(tenantId);
        }
        return runWithoutDataPermissionContext(() -> {
            UserEntity user = accessSubjectProfileService.findActiveUser(tenantId, userId);
            boolean isSuperAdmin = user != null && AuthPrincipal.hasPlatformAdminRole(tenantId,
                    accessSubjectProfileService.load(tenantId, userId).roleIds());
            return resolveSpecForSubject(tenantId, userId, isSuperAdmin, resourceCode);
        });
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

        // 1. L1 平台管理员或通配符超管放行（并保持租户维度隔离）
        return resolveSpecForSubject(principal.tenantId(), principal.userId(), principal.isSuperAdmin(), resourceCode);
    }

    private DataPermissionSqlSpec resolveSpecForSubject(Long tenantId,
                                                        Long userId,
                                                        boolean isSuperAdmin,
                                                        String resourceCode) {
        if (isSuperAdmin) {
            return DataPermissionSqlSpec.allowAll(tenantId);
        }

        return runWithoutDataPermissionContext(() -> {
            AccessSubjectProfile subjectProfile = accessSubjectProfileService.load(tenantId, userId);
            List<String> candidateCodes = buildResourceCandidates(resourceCode);
            List<DataPermissionRuleEntity> allCandidateRules = requiredMapper().selectList(
                    new LambdaQueryWrapper<DataPermissionRuleEntity>()
                            .eq(DataPermissionRuleEntity::getTenantId, tenantId)
                            .in(DataPermissionRuleEntity::getResourceCode, candidateCodes)
                            .eq(DataPermissionRuleEntity::getDeleted, 0)
                            .eq(DataPermissionRuleEntity::getStatus, "ENABLED")
            );

            List<DataPermissionRuleEntity> prioritizedRules = selectHighestPriorityTier(allCandidateRules, candidateCodes);
            if (prioritizedRules.isEmpty()) {
                return resolveRoleFallbackSpec(tenantId, subjectProfile);
            }

            List<DataPermissionRuleEntity> matchedRules = prioritizedRules.stream()
                    .filter(rule -> subjectProfile.matches(rule.getSubjectType(), rule.getSubjectValue()))
                    .toList();

            if (matchedRules.isEmpty()) {
                return DataPermissionSqlSpec.denyAll(tenantId);
            }

            DataPermissionSqlSpec spec = new DataPermissionSqlSpec();
            spec.setTenantId(tenantId);
            for (DataPermissionRuleEntity rule : matchedRules) {
                if (isAllScopeRule(rule)) {
                    if (isDenyRule(rule)) {
                        return DataPermissionSqlSpec.denyAll(tenantId);
                    }
                    return DataPermissionSqlSpec.allowAll(tenantId);
                }
                DataPermissionSqlSpec.RuleClause clause = buildRuleClause(subjectProfile, rule);
                if (isDenyRule(rule)) {
                    spec.addDenyClause(clause);
                } else {
                    spec.addAllowClause(clause);
                }
            }
            if (spec.getAllowClauses().isEmpty() || !spec.hasConstraints()) {
                spec.setDenyAll(true);
            }
            return spec;
        });
    }

    private static DataPermissionSqlSpec runWithoutDataPermissionContext(java.util.function.Supplier<DataPermissionSqlSpec> action) {
        var previousScope = DataPermissionContext.getDataScope();
        var previousResource = DataPermissionContext.getResourceCode();
        var previousSpec = DataPermissionContext.getSqlSpec();
        DataPermissionContext.clear();
        try {
            return action.get();
        } finally {
            if (previousScope != null) {
                DataPermissionContext.setDataScope(previousScope);
            }
            if (previousResource != null) {
                DataPermissionContext.setResourceCode(previousResource);
            }
            if (previousSpec != null) {
                DataPermissionContext.setSqlSpec(previousSpec);
            }
        }
    }

    private DataPermissionSqlSpec resolveRoleFallbackSpec(Long tenantId, AccessSubjectProfile subjectProfile) {
        RoleMapper roleMapper = roleMapperProvider != null ? roleMapperProvider.getIfAvailable() : null;
        if (roleMapper == null) {
            return DataPermissionSqlSpec.allowAll(tenantId);
        }
        List<Long> roleIds = subjectProfile.roleIds();
        if (roleIds == null || roleIds.isEmpty()) {
            return buildSelfFailClosedSpec(tenantId, subjectProfile);
        }
        List<RoleEntity> roles = roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getTenantId, tenantId)
                .in(RoleEntity::getId, roleIds)
                .eq(RoleEntity::getDeleted, 0)
                .eq(RoleEntity::getStatus, "ENABLED"));
        if (roles == null || roles.isEmpty()) {
            return buildSelfFailClosedSpec(tenantId, subjectProfile);
        }

        DataPermissionSqlSpec spec = new DataPermissionSqlSpec();
        spec.setTenantId(tenantId);
        for (RoleEntity role : roles) {
            String scope = role.getDataScopeType() != null ? role.getDataScopeType().trim().toUpperCase(Locale.ROOT) : "SELF";
            if ("ALL".equals(scope) || "TENANT".equals(scope)) {
                return DataPermissionSqlSpec.allowAll(tenantId);
            }
            DataPermissionSqlSpec.RuleClause clause = new DataPermissionSqlSpec.RuleClause();
            applyNamedScope(clause, subjectProfile, scope);
            spec.addAllowClause(clause);
        }
        if (!spec.hasConstraints()) {
            return buildSelfFailClosedSpec(tenantId, subjectProfile);
        }
        return spec;
    }

    private DataPermissionSqlSpec buildSelfFailClosedSpec(Long tenantId, AccessSubjectProfile subjectProfile) {
        DataPermissionSqlSpec spec = new DataPermissionSqlSpec();
        spec.setTenantId(tenantId);
        DataPermissionSqlSpec.RuleClause selfClause = new DataPermissionSqlSpec.RuleClause();
        selfClause.addUserId(subjectProfile.userId());
        selfClause.addEmployeeId(subjectProfile.employeeId());
        spec.addAllowClause(selfClause);
        return spec;
    }

    private static List<String> buildResourceCandidates(String rawResourceCode) {
        if (rawResourceCode == null || rawResourceCode.isBlank()) {
            return List.of("*");
        }
        String trimmed = rawResourceCode.trim();
        Set<String> ordered = new LinkedHashSet<>();
        ordered.add(trimmed);

        String canonical = stripActionSuffix(trimmed);
        ordered.add(canonical);

        int colonIdx = canonical.indexOf(':');
        if (colonIdx > 0) {
            ordered.add(canonical.substring(0, colonIdx) + ":*");
        }
        ordered.add("*");
        return List.copyOf(ordered);
    }

    private static String stripActionSuffix(String code) {
        for (String suffix : List.of(":list", ":query", ":view", ":export", ":page")) {
            if (code.endsWith(suffix) && code.length() > suffix.length()) {
                return code.substring(0, code.length() - suffix.length());
            }
        }
        return code;
    }

    private static List<DataPermissionRuleEntity> selectHighestPriorityTier(List<DataPermissionRuleEntity> rules,
                                                                            List<String> candidateCodes) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        for (String candidate : candidateCodes) {
            List<DataPermissionRuleEntity> tier = rules.stream()
                    .filter(r -> candidate.equalsIgnoreCase(r.getResourceCode()))
                    .toList();
            if (!tier.isEmpty()) {
                return tier;
            }
        }
        return List.of();
    }

    private boolean isAllScopeRule(DataPermissionRuleEntity rule) {
        String scopeType = rule.getScopeType() != null ? rule.getScopeType().toUpperCase(Locale.ROOT) : "";
        if ("ALL".equals(scopeType) || "TENANT".equals(scopeType)) {
            return true;
        }
        if ("CUSTOM".equals(scopeType) && rule.getConfigJson() != null && !rule.getConfigJson().isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(rule.getConfigJson());
                String customScope = root.path("scopeType").asText("");
                return "ALL".equalsIgnoreCase(customScope) || "TENANT".equalsIgnoreCase(customScope);
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private boolean isDenyRule(DataPermissionRuleEntity rule) {
        if (rule.getConfigJson() == null || rule.getConfigJson().isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(rule.getConfigJson());
            return "DENY".equalsIgnoreCase(root.path("effect").asText("ALLOW"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private DataPermissionSqlSpec.RuleClause buildRuleClause(AccessSubjectProfile subjectProfile, DataPermissionRuleEntity rule) {
        DataPermissionSqlSpec.RuleClause clause = new DataPermissionSqlSpec.RuleClause();
        String scopeType = rule.getScopeType() != null ? rule.getScopeType().toUpperCase(Locale.ROOT) : "";
        if ("CUSTOM".equals(scopeType)) {
            mergeCustomClause(clause, subjectProfile, rule.getConfigJson());
        } else {
            applyNamedScope(clause, subjectProfile, scopeType);
            appendCustomAbacOnly(clause, rule.getConfigJson());
        }
        return clause;
    }

    private void applyNamedScope(DataPermissionSqlSpec.RuleClause clause, AccessSubjectProfile subjectProfile, String scopeType) {
        switch (scopeType) {
            case "ORG_AND_CHILDREN" -> {
                Set<Long> expandedDepts = new LinkedHashSet<>();
                if (subjectProfile.companyDepartmentId() != null) {
                    List<Long> orgIds = accessSubjectProfileService.expandDepartmentTree(
                            subjectProfile.tenantId(),
                            List.of(subjectProfile.companyDepartmentId())
                    );
                    clause.addDepartmentIds(orgIds);
                    clause.addOrgUnitIds(orgIds);
                    expandedDepts.addAll(orgIds);
                }
                if (subjectProfile.departmentId() != null) {
                    List<Long> unitIds = accessSubjectProfileService.expandDepartmentTree(
                            subjectProfile.tenantId(),
                            List.of(subjectProfile.departmentId())
                    );
                    clause.addDepartmentIds(unitIds);
                    clause.addOrgUnitIds(unitIds);
                    expandedDepts.addAll(unitIds);
                }
                clause.addScopedMemberUserIds(
                        accessSubjectProfileService.resolveUserIdsByDepartments(subjectProfile.tenantId(), expandedDepts)
                );
                clause.markScopeConfigured();
            }
            case "ORG_ONLY", "COMPANY" -> {
                if (subjectProfile.companyDepartmentId() != null) {
                    clause.addDepartmentId(subjectProfile.companyDepartmentId());
                    clause.addOrganizationId(subjectProfile.companyDepartmentId());
                    clause.addScopedMemberUserIds(
                            accessSubjectProfileService.resolveUserIdsByDepartments(
                                    subjectProfile.tenantId(),
                                    List.of(subjectProfile.companyDepartmentId())
                            )
                    );
                }
                clause.markScopeConfigured();
            }
            case "DEPT_AND_CHILDREN", "DEPARTMENT" -> {
                if (subjectProfile.departmentId() != null) {
                    List<Long> deptIds = accessSubjectProfileService.expandDepartmentTree(
                            subjectProfile.tenantId(),
                            List.of(subjectProfile.departmentId())
                    );
                    clause.addDepartmentIds(deptIds);
                    clause.addOrgUnitIds(deptIds);
                    clause.addScopedMemberUserIds(
                            accessSubjectProfileService.resolveUserIdsByDepartments(subjectProfile.tenantId(), deptIds)
                    );
                }
                clause.markScopeConfigured();
            }
            case "DEPT_ONLY" -> {
                if (subjectProfile.departmentId() != null) {
                    clause.addDepartmentId(subjectProfile.departmentId());
                    clause.addOrgUnitId(subjectProfile.departmentId());
                    clause.addScopedMemberUserIds(
                            accessSubjectProfileService.resolveUserIdsByDepartments(
                                    subjectProfile.tenantId(),
                                    List.of(subjectProfile.departmentId())
                            )
                    );
                }
                clause.markScopeConfigured();
            }
            case "SELF_AND_SUBORDINATES" -> {
                clause.addUserId(subjectProfile.userId());
                clause.addEmployeeId(subjectProfile.employeeId());
                clause.addUserIds(
                        accessSubjectProfileService.resolveSubordinateUserIds(subjectProfile.tenantId(), subjectProfile.userId())
                );
                clause.markScopeConfigured();
            }
            case "SELF", "PERSONAL" -> {
                clause.addUserId(subjectProfile.userId());
                clause.addEmployeeId(subjectProfile.employeeId());
                clause.markScopeConfigured();
            }
            case "POSITION" -> {
                if (subjectProfile.positionId() != null) {
                    clause.addPositionId(subjectProfile.positionId());
                    clause.addScopedMemberUserIds(
                            accessSubjectProfileService.resolveUserIdsByPositions(
                                    subjectProfile.tenantId(),
                                    List.of(subjectProfile.positionId())
                            )
                    );
                }
                clause.markScopeConfigured();
            }
            case "ORGANIZATION" -> {
                if (subjectProfile.organizationPublicId() != null) {
                    clause.addOrganizationPublicId(subjectProfile.organizationPublicId().toString());
                }
                if (subjectProfile.companyDepartmentId() != null) {
                    clause.addOrganizationId(subjectProfile.companyDepartmentId());
                }
                clause.markScopeConfigured();
            }
            case "ORG_UNIT" -> {
                if (subjectProfile.orgUnitPublicId() != null) {
                    clause.addOrgUnitPublicId(subjectProfile.orgUnitPublicId().toString());
                }
                if (subjectProfile.departmentId() != null) {
                    clause.addDepartmentId(subjectProfile.departmentId());
                    clause.addOrgUnitId(subjectProfile.departmentId());
                }
                clause.markScopeConfigured();
            }
            case "ORG_UNIT_AND_SUB" -> {
                if (subjectProfile.orgUnitPublicId() != null) {
                    OrganizationDirectory orgDir = organizationDirectoryProvider != null
                            ? organizationDirectoryProvider.getIfAvailable() : null;
                    if (orgDir != null) {
                        for (UUID unitUuid : orgDir.expandOrgUnitSubtree(subjectProfile.orgUnitPublicId())) {
                            clause.addOrgUnitPublicId(unitUuid.toString());
                        }
                    } else {
                        clause.addOrgUnitPublicId(subjectProfile.orgUnitPublicId().toString());
                    }
                }
                if (subjectProfile.departmentId() != null) {
                    List<Long> deptIds = accessSubjectProfileService.expandDepartmentTree(
                            subjectProfile.tenantId(),
                            List.of(subjectProfile.departmentId())
                    );
                    clause.addDepartmentIds(deptIds);
                    clause.addOrgUnitIds(deptIds);
                }
                clause.markScopeConfigured();
            }
            case "ORG_SELF", "ASSIGNMENT" -> {
                clause.addUserId(subjectProfile.userId());
                if (subjectProfile.personPublicId() != null) {
                    clause.addPersonPublicId(subjectProfile.personPublicId().toString());
                }
                if (subjectProfile.assignmentPublicId() != null) {
                    clause.addAssignmentPublicId(subjectProfile.assignmentPublicId().toString());
                }
                clause.markScopeConfigured();
            }
            case "ROLE" -> {
                if (subjectProfile.roleIds() != null && !subjectProfile.roleIds().isEmpty()) {
                    clause.addRoleIds(subjectProfile.roleIds());
                    clause.addScopedMemberUserIds(
                            accessSubjectProfileService.resolveUserIdsByRoles(subjectProfile.tenantId(), subjectProfile.roleIds())
                    );
                }
                clause.markScopeConfigured();
            }
            default -> clause.markScopeConfigured();
        }
    }

    private void mergeCustomClause(DataPermissionSqlSpec.RuleClause clause, AccessSubjectProfile subjectProfile, String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(configJson);
            String customScope = root.path("scopeType").asText("");
            if (!customScope.isBlank() && !"CUSTOM".equalsIgnoreCase(customScope)) {
                applyNamedScope(clause, subjectProfile, customScope.toUpperCase(Locale.ROOT));
            }
            List<Long> explicitUsers = collectLongs(root.path("userIds"));
            clause.addUserIds(explicitUsers);
            appendLongArray(clause::addEmployeeId, root.path("employeeIds"));

            List<Long> explicitDepts = new ArrayList<>();
            explicitDepts.addAll(collectLongs(root.path("departmentIds")));
            explicitDepts.addAll(collectLongs(root.path("orgUnitIds")));
            if (!explicitDepts.isEmpty()) {
                clause.addDepartmentIds(collectLongs(root.path("departmentIds")));
                clause.addOrgUnitIds(collectLongs(root.path("orgUnitIds")));
                clause.addScopedMemberUserIds(
                        accessSubjectProfileService.resolveUserIdsByDepartments(subjectProfile.tenantId(), explicitDepts)
                );
            }

            List<Long> explicitOrgs = collectLongs(root.path("organizationIds"));
            for (Long orgId : explicitOrgs) {
                clause.addOrganizationId(orgId);
            }
            if (!explicitOrgs.isEmpty()) {
                clause.addScopedMemberUserIds(
                        accessSubjectProfileService.resolveUserIdsByDepartments(subjectProfile.tenantId(), explicitOrgs)
                );
            }

            List<Long> explicitPositions = collectLongs(root.path("positionIds"));
            for (Long posId : explicitPositions) {
                clause.addPositionId(posId);
            }
            if (!explicitPositions.isEmpty()) {
                clause.addScopedMemberUserIds(
                        accessSubjectProfileService.resolveUserIdsByPositions(subjectProfile.tenantId(), explicitPositions)
                );
            }

            List<Long> explicitRoles = collectLongs(root.path("roleIds"));
            clause.addRoleIds(explicitRoles);
            if (!explicitRoles.isEmpty()) {
                clause.addScopedMemberUserIds(
                        accessSubjectProfileService.resolveUserIdsByRoles(subjectProfile.tenantId(), explicitRoles)
                );
            }

            if (root.path("treePaths").isArray()) {
                root.path("treePaths").forEach(n -> clause.addTreePath(n.asText()));
            }
            appendAbacConditions(clause, root.path("conditions"));
        } catch (Exception ignored) {
            clause.markScopeConfigured();
        }
    }

    private void appendCustomAbacOnly(DataPermissionSqlSpec.RuleClause clause, String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(configJson);
            appendAbacConditions(clause, root.path("conditions"));
        } catch (Exception ignored) {
        }
    }

    private void appendAbacConditions(DataPermissionSqlSpec.RuleClause clause, JsonNode conditionsNode) {
        if (conditionsNode == null || !conditionsNode.isArray()) {
            return;
        }
        conditionsNode.forEach(condNode -> {
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
                List<Object> list = new ArrayList<>();
                valNode.forEach(n -> list.add(n.isNumber() ? n.numberValue() : n.asText()));
                val = list;
            } else if (!valNode.isNull()) {
                val = valNode.asText();
            }
            if (field != null && !field.isBlank()) {
                clause.addAbacCondition(field, op, val);
            }
        });
    }

    private List<Long> collectLongs(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        appendLongArray(ids::add, array);
        return ids;
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
