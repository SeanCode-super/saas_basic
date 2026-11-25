package com.saasbasics.platform.modules.tenant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.InitialPasswordIssuer;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.id.UuidV7Generator;
import com.saasbasics.platform.common.tenant.TenantMembershipCapacity;
import com.saasbasics.platform.modules.tenant.mapper.TenantBootstrapMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantBootstrapMapper.GeneratedId;
import com.saasbasics.platform.modules.tenant.mapper.TenantBootstrapMapper.PasswordTemplate;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantBootstrapService {

    private final InitialPasswordIssuer passwords;

    private final TenantBootstrapMapper bootstrap;
    private final TenantMembershipCapacity membershipCapacity;
    private final AuditTrailService auditTrailService;
    private final UuidV7Generator uuidGenerator = new UuidV7Generator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TenantBootstrapService(TenantBootstrapMapper bootstrap, TenantMembershipCapacity membershipCapacity,
                                  AuditTrailService auditTrailService, InitialPasswordIssuer passwords) {
        this.passwords = passwords;
        this.bootstrap = bootstrap;
        this.membershipCapacity = membershipCapacity;
        this.auditTrailService = auditTrailService;
    }

    @Transactional
    public void initializeTenant(Long tenantId, String tenantCode, String tenantName) {
        initializeTenant(tenantId, tenantCode, tenantName, null);
    }

    @Transactional
    public String initializeTenant(Long tenantId, String tenantCode, String tenantName, Long packageId) {
        PasswordTemplate template = requireTemplate(bootstrap.selectPasswordTemplate(), "初始化密码策略失败：缺少基础密码策略");
        String initialPassword = passwords.generateInitialPassword(new InitialPasswordIssuer.Rules(
                template.minLength(), template.maxLength(), template.requireUppercase(), template.requireLowercase(),
                template.requireNumber(), template.requireSpecial()));
        membershipCapacity.requireDepartment(tenantId);
        membershipCapacity.requireSeat(tenantId);
        long loginPolicyId = cloneLoginPolicy(tenantId, tenantCode, tenantName);
        long passwordPolicyId = clonePasswordPolicy(tenantId, tenantCode, tenantName, template);
        long roleId = createAdminRole(tenantId, tenantCode, tenantName);
        bootstrap.cloneApiResources(tenantId);
        cloneMenus(tenantId);
        bootstrap.assignRoleApis(tenantId, roleId);
        assignAllMenusToRole(tenantId, roleId);
        createDefaultDataPermissionRules(tenantId, roleId);
        long employeeId = initializeDefaultOrgStructure(tenantId, tenantCode, tenantName);
        long userId = createAdminUser(tenantId, tenantCode, tenantName, employeeId, passwords.hash(initialPassword));
        bootstrap.bindUserRole(tenantId, userId, roleId);
        createPortalClientAndTerminal(tenantId, tenantCode, tenantName, loginPolicyId, passwordPolicyId);
        initializeTenantApps(tenantId, packageId);
        bootstrap.cloneProcessDefinitions(tenantId);
        cloneDictionaries(tenantId);
        cloneSystemConfigsAndI18n(tenantId);
        cloneGovernanceBaselines(tenantId);
        initializeFileStorageAndRoutes(tenantId);
        initializeErpAndSchedulerBaselines(tenantId, tenantName);
        cloneAiBaselines(tenantId);
        auditTrailService.record("tenant", "tenant_bootstrap", String.valueOf(tenantId), "BOOTSTRAP", tenantCode, "initialized", true);
        return initialPassword;
    }

    private void initializeTenantApps(Long tenantId, Long packageId) {
        Set<String> allowedAppCodes = resolvePackageAllowedAppCodes(packageId);
        if (allowedAppCodes == null) {
            bootstrap.cloneAllTenantApps(tenantId);
            return;
        }

        List<Map<String, Object>> templateApps = bootstrap.selectTemplateApps();
        for (Map<String, Object> row : templateApps) {
            String appCode = String.valueOf(row.get("app_code")).trim().toLowerCase(Locale.ROOT);
            String appName = String.valueOf(row.get("app_name"));
            String status = ("iam".equals(appCode) || allowedAppCodes.contains(appCode)) ? "ENABLED" : "DISABLED";
            bootstrap.upsertPackageApp(tenantId, appCode, appName, status);
        }
    }

    private Set<String> resolvePackageAllowedAppCodes(Long packageId) {
        if (packageId == null || packageId <= 0) {
            return null;
        }
        List<Map<String, Object>> rows = bootstrap.selectPackageFeatures(packageId);
        if (rows.isEmpty()) {
            return null;
        }
        Object rawObj = rows.get(0).get("feature_flags_json");
        if (rawObj == null) {
            return null;
        }
        String json = String.valueOf(rawObj).trim();
        if (json.isBlank() || json.contains("*")) {
            return null;
        }
        try {
            List<String> flags = objectMapper.readValue(json, new TypeReference<>() {});
            if (flags == null || flags.isEmpty()) {
                return null;
            }
            Set<String> resolved = new LinkedHashSet<>();
            resolved.add("iam");
            for (String flag : flags) {
                String normalized = normalizePackageFeatureToAppCode(flag);
                if (!normalized.isBlank()) {
                    resolved.add(normalized);
                }
            }
            return resolved;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String normalizePackageFeatureToAppCode(String rawFlag) {
        if (rawFlag == null || rawFlag.isBlank()) {
            return "";
        }
        String val = rawFlag.trim().toLowerCase(Locale.ROOT);
        if (val.startsWith("saas.") && val.split("\\.").length >= 2) {
            val = val.split("\\.")[1];
        }
        return switch (val) {
            case "org", "organization" -> "iam";
            case "ai_center" -> "integration";
            case "report_print" -> "report";
            default -> val;
        };
    }

    private long cloneLoginPolicy(Long tenantId, String tenantCode, String tenantName) {
        Map<String, Object> source = requireTemplate(bootstrap.selectLoginTemplate(), "初始化登录策略失败：缺少基础登录策略");

        GeneratedId keyHolder = new GeneratedId();
        bootstrap.insertLoginPolicy(keyHolder, tenantId, tenantCode, tenantName, source);
        return keyHolder.require("初始化登录策略失败");
    }

    private long clonePasswordPolicy(Long tenantId, String tenantCode, String tenantName, PasswordTemplate source) {
        GeneratedId keyHolder = new GeneratedId();
        bootstrap.insertPasswordPolicy(keyHolder, tenantId, tenantCode, tenantName, source);
        return keyHolder.require("初始化密码策略失败");
    }

    private long createAdminRole(Long tenantId, String tenantCode, String tenantName) {
        GeneratedId keyHolder = new GeneratedId();
        bootstrap.insertAdminRole(keyHolder, tenantId, tenantCode, tenantName);
        return keyHolder.require("初始化管理员角色失败");
    }

    private void cloneMenus(Long tenantId) {
        bootstrap.cloneMenus(tenantId);

        bootstrap.restoreMenuParents(tenantId);
    }

    private void assignAllMenusToRole(Long tenantId, Long roleId) {
        long actorId = Objects.requireNonNullElse(AuthContext.getUserId(), 0L);
        bootstrap.assignMenuPermissions(tenantId, roleId, actorId);

        bootstrap.assignRoleMenus(tenantId, roleId, actorId);
    }

    private long initializeDefaultOrgStructure(Long tenantId, String tenantCode, String tenantName) {
        GeneratedId deptKeyHolder = new GeneratedId();
        bootstrap.insertDepartment(deptKeyHolder, tenantId, tenantCode, tenantName);
        long deptId = deptKeyHolder.require("初始化默认部门失败");
        bootstrap.updateDepartmentPath(tenantId, deptId);

        GeneratedId posKeyHolder = new GeneratedId();
        bootstrap.insertPosition(posKeyHolder, tenantId, tenantCode);
        long positionId = posKeyHolder.require("初始化默认岗位失败");

        GeneratedId empKeyHolder = new GeneratedId();
        bootstrap.insertEmployee(empKeyHolder, tenantId, tenantName, deptId, positionId);
        return empKeyHolder.require("初始化默认员工档案失败");
    }

    private void createDefaultDataPermissionRules(Long tenantId, Long roleId) {
        List<Map<String, Object>> resources = List.of(
                Map.of("code", "iam:user:list", "name", "用户数据范围"),
                Map.of("code", "iam:department:list", "name", "部门数据范围"),
                Map.of("code", "iam:employee:list", "name", "员工数据范围"),
                Map.of("code", "iam:position:list", "name", "岗位数据范围")
        );

        for (Map<String, Object> resource : resources) {
            bootstrap.insertDataPermissionRule(tenantId, resource, roleId);
        }
    }

    private long createAdminUser(Long tenantId, String tenantCode, String tenantName, long employeeId, String passwordHash) {
        GeneratedId keyHolder = new GeneratedId();
        bootstrap.insertAdminUser(keyHolder, tenantId, uuidGenerator.generate().toString(), tenantName, employeeId, tenantCode, passwordHash);
        return keyHolder.require("初始化管理员用户失败");
    }

    private void cloneDictionaries(Long tenantId) {
        bootstrap.cloneDictTypes(tenantId);

        bootstrap.cloneDictItems(tenantId);
    }

    private void cloneSystemConfigsAndI18n(Long tenantId) {
        bootstrap.cloneSystemConfigs(tenantId);

        bootstrap.cloneMessages(tenantId);

        bootstrap.cloneDataScopes(tenantId);
    }

    private void cloneGovernanceBaselines(Long tenantId) {
        bootstrap.cloneSequenceRules(tenantId);

        bootstrap.cloneRiskRules(tenantId);

        bootstrap.cloneNotifyTemplates(tenantId);
    }

    private void initializeFileStorageAndRoutes(Long tenantId) {
        bootstrap.insertDefaultStorage(tenantId);

        Long storageId = bootstrap.selectDefaultStorageId(tenantId);
        if (storageId != null) {
            bootstrap.insertDefaultBucket(tenantId, storageId);
        }

        bootstrap.cloneUploadRoutes(tenantId);
    }

    private void initializeErpAndSchedulerBaselines(Long tenantId, String tenantName) {
        bootstrap.insertDefaultWarehouse(tenantId, tenantName);

        bootstrap.insertDefaultExecutor(tenantId);
    }

    private void cloneAiBaselines(Long tenantId) {
        bootstrap.cloneAiProviders(tenantId);

        bootstrap.cloneAiModels(tenantId);

        bootstrap.cloneAiPrompts(tenantId);

        bootstrap.cloneAiPromptVersions(tenantId);

        bootstrap.cloneAiTools(tenantId);
    }

    private void createPortalClientAndTerminal(Long tenantId,
                                               String tenantCode,
                                               String tenantName,
                                               long loginPolicyId,
                                               long passwordPolicyId) {
        Map<String, Object> clientSource = requireTemplate(bootstrap.selectClientTemplate(), "初始化门户 client 失败：缺少基础入口配置");

        GeneratedId clientKeyHolder = new GeneratedId();
        bootstrap.insertPortalClient(clientKeyHolder, tenantId, tenantCode, tenantName, clientSource, loginPolicyId, passwordPolicyId);
        long portalClientId = clientKeyHolder.require("初始化门户 client 失败");

        Map<String, Object> terminalSource = requireTemplate(bootstrap.selectTerminalTemplate(), "初始化门户 terminal 失败：缺少 web 终端配置");

        bootstrap.insertPortalTerminal(tenantId, portalClientId, tenantName, terminalSource, loginPolicyId, passwordPolicyId);
    }

    private <T> T requireTemplate(T row, String message) {
        if (row == null) {
            throw new BizException("TENANT_BOOTSTRAP_FAILED", message);
        }
        return row;
    }

}
