package com.saasbasics.platform.modules.tenant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.id.UuidV7Generator;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantBootstrapService {

    private static final long SOURCE_TENANT_ID = 1L;
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin@123456";

    private final JdbcTemplate jdbcTemplate;
    private final AuditTrailService auditTrailService;
    private final UuidV7Generator uuidGenerator = new UuidV7Generator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TenantBootstrapService(JdbcTemplate jdbcTemplate, AuditTrailService auditTrailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditTrailService = auditTrailService;
    }

    @Transactional
    public void initializeTenant(Long tenantId, String tenantCode, String tenantName) {
        initializeTenant(tenantId, tenantCode, tenantName, null);
    }

    @Transactional
    public void initializeTenant(Long tenantId, String tenantCode, String tenantName, Long packageId) {
        long loginPolicyId = cloneLoginPolicy(tenantId, tenantCode, tenantName);
        long passwordPolicyId = clonePasswordPolicy(tenantId, tenantCode, tenantName);
        long roleId = createAdminRole(tenantId, tenantCode, tenantName);
        cloneApiResources(tenantId);
        cloneMenus(tenantId);
        assignAllApiResourcesToRole(tenantId, roleId);
        assignAllMenusToRole(tenantId, roleId);
        createDefaultDataPermissionRules(tenantId, roleId);
        long employeeId = initializeDefaultOrgStructure(tenantId, tenantCode, tenantName);
        long userId = createAdminUser(tenantId, tenantCode, tenantName, employeeId);
        bindUserRole(tenantId, userId, roleId);
        createPortalClientAndTerminal(tenantId, tenantCode, tenantName, loginPolicyId, passwordPolicyId);
        initializeTenantApps(tenantId, packageId);
        cloneDefaultProcessDefinitions(tenantId);
        cloneDictionaries(tenantId);
        cloneSystemConfigsAndI18n(tenantId);
        cloneGovernanceBaselines(tenantId);
        initializeFileStorageAndRoutes(tenantId);
        initializeErpAndSchedulerBaselines(tenantId, tenantName);
        cloneAiBaselines(tenantId);
        auditTrailService.record("tenant", "tenant_bootstrap", String.valueOf(tenantId), "BOOTSTRAP", tenantCode, "initialized", true);
    }

    private void initializeTenantApps(Long tenantId, Long packageId) {
        Set<String> allowedAppCodes = resolvePackageAllowedAppCodes(packageId);
        if (allowedAppCodes == null) {
            jdbcTemplate.update("""
                    INSERT INTO plat_tenant_app (tenant_id, app_code, app_name, status, remark, created_by, updated_by)
                    SELECT ?, app_code, app_name, 'ENABLED', 'tenant bootstrap (all)', 1, 1
                    FROM plat_tenant_app
                    WHERE tenant_id = ? AND deleted = 0
                    ON DUPLICATE KEY UPDATE status = 'ENABLED'
                    """, tenantId, SOURCE_TENANT_ID);
            return;
        }

        List<Map<String, Object>> templateApps = jdbcTemplate.queryForList("""
                SELECT app_code, app_name
                FROM plat_tenant_app
                WHERE tenant_id = ? AND deleted = 0
                """, SOURCE_TENANT_ID);
        for (Map<String, Object> row : templateApps) {
            String appCode = String.valueOf(row.get("app_code")).trim().toLowerCase(Locale.ROOT);
            String appName = String.valueOf(row.get("app_name"));
            String status = ("iam".equals(appCode) || allowedAppCodes.contains(appCode)) ? "ENABLED" : "DISABLED";
            jdbcTemplate.update("""
                    INSERT INTO plat_tenant_app (tenant_id, app_code, app_name, status, remark, created_by, updated_by)
                    VALUES (?, ?, ?, ?, 'tenant bootstrap (package)', 1, 1)
                    ON DUPLICATE KEY UPDATE status = VALUES(status)
                    """, tenantId, appCode, appName, status);
        }
    }

    private Set<String> resolvePackageAllowedAppCodes(Long packageId) {
        if (packageId == null || packageId <= 0) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT feature_flags_json
                FROM plat_tenant_package
                WHERE id = ? AND deleted = 0
                LIMIT 1
                """, packageId);
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

    private void cloneDefaultProcessDefinitions(Long tenantId) {
        jdbcTemplate.update("""
                INSERT INTO wf_process_definition (
                    tenant_id, process_code, process_name, category, icon, color, description,
                    form_schema_json, flow_nodes_json, bpmn_xml, flowable_deployment_id, version, status, sort_no, created_by, updated_by, deleted
                )
                SELECT
                    ?, process_code, process_name, category, icon, color, description,
                    form_schema_json, flow_nodes_json, bpmn_xml, flowable_deployment_id, version, status, sort_no, 1, 1, 0
                FROM wf_process_definition
                WHERE tenant_id = ? AND deleted = 0 AND status = 'PUBLISHED'
                """, tenantId, SOURCE_TENANT_ID);
    }

    private long cloneLoginPolicy(Long tenantId, String tenantCode, String tenantName) {
        Map<String, Object> source = queryRequired("""
                SELECT *
                FROM iam_login_policy
                WHERE tenant_id IN (0, ?)
                  AND deleted = 0
                  AND status = 'ENABLED'
                ORDER BY tenant_id DESC, id ASC
                LIMIT 1
                """, "初始化登录策略失败：缺少基础登录策略", SOURCE_TENANT_ID);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO iam_login_policy (
                      tenant_id, policy_code, policy_name, allow_password_login, allow_sms_login, allow_email_login,
                      allow_social_login, force_mfa, session_timeout_minutes, max_failed_count, lock_minutes,
                      ip_allowlist_json, device_trust_days, status, created_by, created_at, updated_by, updated_at,
                      deleted, version, remark
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            int index = 1;
            statement.setLong(index++, tenantId);
            statement.setString(index++, tenantCode + "_default_login");
            statement.setString(index++, tenantName + "默认登录策略");
            statement.setObject(index++, source.get("allow_password_login"));
            statement.setObject(index++, source.get("allow_sms_login"));
            statement.setObject(index++, source.get("allow_email_login"));
            statement.setObject(index++, source.get("allow_social_login"));
            statement.setObject(index++, source.get("force_mfa"));
            statement.setObject(index++, source.get("session_timeout_minutes"));
            statement.setObject(index++, source.get("max_failed_count"));
            statement.setObject(index++, source.get("lock_minutes"));
            statement.setObject(index++, source.get("ip_allowlist_json"));
            statement.setObject(index++, source.get("device_trust_days"));
            statement.setString(index++, "ENABLED");
            statement.setLong(index++, 1L);
            statement.setTimestamp(index++, Timestamp.valueOf(LocalDateTime.now()));
            statement.setLong(index++, 1L);
            statement.setTimestamp(index++, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(index, "tenant bootstrap");
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "TENANT_BOOTSTRAP_FAILED", "初始化登录策略失败");
    }

    private long clonePasswordPolicy(Long tenantId, String tenantCode, String tenantName) {
        Map<String, Object> source = queryRequired("""
                SELECT *
                FROM iam_password_policy
                WHERE tenant_id IN (0, ?)
                  AND deleted = 0
                  AND status = 'ENABLED'
                ORDER BY tenant_id DESC, id ASC
                LIMIT 1
                """, "初始化密码策略失败：缺少基础密码策略", SOURCE_TENANT_ID);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO iam_password_policy (
                      tenant_id, policy_code, policy_name, min_length, max_length, require_uppercase, require_lowercase,
                      require_number, require_special, password_history_limit, password_expire_days, temp_password_expire_hours,
                      status, created_by, created_at, updated_by, updated_at, deleted, version, remark
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            int index = 1;
            statement.setLong(index++, tenantId);
            statement.setString(index++, tenantCode + "_default_password");
            statement.setString(index++, tenantName + "默认密码策略");
            statement.setObject(index++, source.get("min_length"));
            statement.setObject(index++, source.get("max_length"));
            statement.setObject(index++, source.get("require_uppercase"));
            statement.setObject(index++, source.get("require_lowercase"));
            statement.setObject(index++, source.get("require_number"));
            statement.setObject(index++, source.get("require_special"));
            statement.setObject(index++, source.get("password_history_limit"));
            statement.setObject(index++, source.get("password_expire_days"));
            statement.setObject(index++, source.get("temp_password_expire_hours"));
            statement.setString(index++, "ENABLED");
            statement.setLong(index++, 1L);
            statement.setTimestamp(index++, Timestamp.valueOf(LocalDateTime.now()));
            statement.setLong(index++, 1L);
            statement.setTimestamp(index++, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(index, "tenant bootstrap");
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "TENANT_BOOTSTRAP_FAILED", "初始化密码策略失败");
    }

    private long createAdminRole(Long tenantId, String tenantCode, String tenantName) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO iam_role (
                      tenant_id, role_group_id, role_code, role_name, role_type, data_scope_type, status, is_system,
                      sort_no, created_by, created_at, updated_by, updated_at, deleted, version, remark
                    ) VALUES (?, 0, ?, ?, 'SYSTEM', 'ALL', 'ENABLED', 1, 1, 1, ?, 1, ?, 0, 0, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, tenantId);
            statement.setString(2, tenantCode + "_super_admin");
            statement.setString(3, tenantName + "管理员");
            statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(6, "tenant bootstrap");
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "TENANT_BOOTSTRAP_FAILED", "初始化管理员角色失败");
    }

    private void cloneApiResources(Long tenantId) {
        jdbcTemplate.update("""
                INSERT INTO iam_api_resource (
                  tenant_id, resource_code, resource_name, http_method, url_pattern, auth_required, status,
                  created_by, created_at, updated_by, updated_at, deleted, version, remark
                )
                SELECT ?, resource_code, resource_name, http_method, url_pattern, auth_required, status,
                       1, NOW(3), 1, NOW(3), 0, 0, 'tenant bootstrap'
                FROM iam_api_resource
                WHERE tenant_id = ?
                  AND deleted = 0
                """, tenantId, SOURCE_TENANT_ID);
    }

    private void assignAllApiResourcesToRole(Long tenantId, Long roleId) {
        jdbcTemplate.update("""
                INSERT INTO iam_role_api (
                  tenant_id, role_id, api_resource_id, created_by, created_at, updated_by, updated_at, deleted, version, remark
                )
                SELECT ?, ?, id, 1, NOW(3), 1, NOW(3), 0, 0, 'tenant bootstrap'
                FROM iam_api_resource
                WHERE tenant_id = ?
                  AND deleted = 0
                """, tenantId, roleId, tenantId);
    }

    private void cloneMenus(Long tenantId) {
        jdbcTemplate.update("""
                INSERT INTO iam_menu (
                  tenant_id, parent_id, menu_type, menu_code, app_code, menu_name, route_path, component_path, permission_code, icon,
                  visible, keep_alive, sort_no, status, meta_json, created_by, created_at, updated_by, updated_at, deleted, version, remark
                )
                SELECT ?, 0, menu_type, menu_code, app_code, menu_name, route_path, component_path, permission_code, icon,
                       visible, keep_alive, sort_no, status, meta_json, 1, NOW(3), 1, NOW(3), 0, 0, 'tenant bootstrap'
                FROM iam_menu
                WHERE tenant_id = ?
                  AND deleted = 0
                  AND menu_code NOT LIKE 'dyn\\_%'
                """, tenantId, SOURCE_TENANT_ID);

        jdbcTemplate.update("""
                UPDATE iam_menu target
                INNER JOIN iam_menu source
                  ON source.tenant_id = ?
                 AND source.menu_code = target.menu_code
                 AND source.deleted = 0
                LEFT JOIN iam_menu source_parent
                  ON source_parent.id = source.parent_id
                 AND source_parent.tenant_id = ?
                 AND source_parent.deleted = 0
                LEFT JOIN iam_menu target_parent
                  ON target_parent.tenant_id = ?
                 AND target_parent.menu_code = source_parent.menu_code
                 AND target_parent.deleted = 0
                SET target.parent_id = IFNULL(target_parent.id, 0)
                WHERE target.tenant_id = ?
                  AND target.deleted = 0
                """, SOURCE_TENANT_ID, SOURCE_TENANT_ID, tenantId, tenantId);
    }

    private void assignAllMenusToRole(Long tenantId, Long roleId) {
        jdbcTemplate.update("""
                INSERT INTO iam_menu_permission (
                  tenant_id, menu_id, menu_code, subject_type, subject_value, button_codes_json, status,
                  created_by, created_at, updated_by, updated_at, deleted, version, remark
                )
                SELECT ?, target.id, target.menu_code, 'ROLE', CAST(? AS CHAR),
                       IFNULL((
                         SELECT JSON_ARRAYAGG(btn.menu_code)
                         FROM iam_menu btn
                         WHERE btn.parent_id = target.id
                           AND btn.tenant_id = target.tenant_id
                           AND btn.menu_type = 'BUTTON'
                           AND btn.deleted = 0
                       ), JSON_ARRAY()),
                       'ENABLED',
                       1, NOW(3), 1, NOW(3), 0, 0, 'tenant bootstrap'
                FROM iam_menu target
                WHERE target.tenant_id = ?
                  AND target.menu_type NOT IN ('BUTTON', 'DIRECTORY')
                  AND target.deleted = 0
                """, tenantId, roleId, tenantId);

        jdbcTemplate.update("""
                INSERT IGNORE INTO iam_role_menu (
                  tenant_id, role_id, menu_id, created_by, created_at, updated_by, updated_at, deleted, version, remark
                )
                SELECT ?, ?, target.id, 1, NOW(3), 1, NOW(3), 0, 0, 'tenant bootstrap'
                FROM iam_menu target
                WHERE target.tenant_id = ?
                  AND target.deleted = 0
                """, tenantId, roleId, tenantId);
    }

    private long initializeDefaultOrgStructure(Long tenantId, String tenantCode, String tenantName) {
        KeyHolder deptKeyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO iam_department (
                      tenant_id, parent_id, dept_code, dept_name, dept_full_name, tree_path, leader_user_id, status,
                      sort_no, created_by, created_at, updated_by, updated_at, deleted, version, remark
                    ) VALUES (?, 0, ?, ?, ?, NULL, 0, 'ENABLED', 1, 1, ?, 1, ?, 0, 0, 'tenant bootstrap')
                    """, Statement.RETURN_GENERATED_KEYS);
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            statement.setLong(1, tenantId);
            statement.setString(2, tenantCode + "_hq");
            statement.setString(3, tenantName + "总部");
            statement.setString(4, tenantName + "总部");
            statement.setTimestamp(5, now);
            statement.setTimestamp(6, now);
            return statement;
        }, deptKeyHolder);
        long deptId = generatedId(deptKeyHolder, "TENANT_BOOTSTRAP_FAILED", "初始化默认部门失败");
        jdbcTemplate.update("UPDATE iam_department SET tree_path = CONCAT('/', ?, '/') WHERE id = ?", deptId, deptId);

        KeyHolder posKeyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO iam_position (
                      tenant_id, position_code, position_name, position_level, status,
                      sort_no, created_by, created_at, updated_by, updated_at, deleted, version, remark
                    ) VALUES (?, ?, '系统管理员', 'M1', 'ENABLED', 1, 1, ?, 1, ?, 0, 0, 'tenant bootstrap')
                    """, Statement.RETURN_GENERATED_KEYS);
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            statement.setLong(1, tenantId);
            statement.setString(2, tenantCode + "_admin_pos");
            statement.setTimestamp(3, now);
            statement.setTimestamp(4, now);
            return statement;
        }, posKeyHolder);
        long positionId = generatedId(posKeyHolder, "TENANT_BOOTSTRAP_FAILED", "初始化默认岗位失败");

        KeyHolder empKeyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO iam_employee (
                      tenant_id, employee_no, employee_name, dept_id, position_id, employee_status,
                      created_by, created_at, updated_by, updated_at, deleted, version, remark
                    ) VALUES (?, 'EMP-0001', ?, ?, ?, 'ACTIVE', 1, ?, 1, ?, 0, 0, 'tenant bootstrap')
                    """, Statement.RETURN_GENERATED_KEYS);
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            statement.setLong(1, tenantId);
            statement.setString(2, tenantName + "管理员");
            statement.setLong(3, deptId);
            statement.setLong(4, positionId);
            statement.setTimestamp(5, now);
            statement.setTimestamp(6, now);
            return statement;
        }, empKeyHolder);
        return generatedId(empKeyHolder, "TENANT_BOOTSTRAP_FAILED", "初始化默认员工档案失败");
    }

    private void createDefaultDataPermissionRules(Long tenantId, Long roleId) {
        List<Map<String, Object>> resources = List.of(
                Map.of("code", "iam:user:list", "name", "用户数据范围"),
                Map.of("code", "iam:department:list", "name", "部门数据范围"),
                Map.of("code", "iam:employee:list", "name", "员工数据范围"),
                Map.of("code", "iam:position:list", "name", "岗位数据范围")
        );

        for (Map<String, Object> resource : resources) {
            jdbcTemplate.update("""
                    INSERT INTO iam_data_permission_rule (
                      tenant_id, resource_code, resource_name, subject_type, subject_value, scope_type, config_json, status,
                      created_by, created_at, updated_by, updated_at, deleted, version, remark
                    ) VALUES (?, ?, ?, 'ROLE', CAST(? AS CHAR), 'TENANT', JSON_OBJECT(), 'ENABLED',
                              1, NOW(3), 1, NOW(3), 0, 0, 'tenant bootstrap')
                    """,
                    tenantId,
                    resource.get("code"),
                    resource.get("name"),
                    roleId
            );
        }
    }

    private long createAdminUser(Long tenantId, String tenantCode, String tenantName, long employeeId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO iam_user (
                      tenant_id, public_id, user_code, username, nickname, employee_id, user_type, status, mobile, email,
                      last_login_at, last_login_ip, password_hash, password_changed_at, need_reset_password,
                      created_by, created_at, updated_by, updated_at, deleted, version, remark
                    ) VALUES (?, ?, 'TENANT_ADMIN', 'admin', ?, ?, 'STAFF', 'ENABLED', NULL, ?,
                              NULL, NULL, ?, ?, 1, 1, ?, 1, ?, 0, 0, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, tenantId);
            statement.setString(2, uuidGenerator.generate().toString());
            statement.setString(3, tenantName + "管理员");
            statement.setLong(4, employeeId);
            statement.setString(5, "admin@" + tenantCode + ".local");
            statement.setString(6, hash(DEFAULT_ADMIN_PASSWORD));
            statement.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            statement.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            statement.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(10, "tenant bootstrap");
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "TENANT_BOOTSTRAP_FAILED", "初始化管理员用户失败");
    }

    private void cloneDictionaries(Long tenantId) {
        jdbcTemplate.update("""
                INSERT INTO sys_dict_type (
                  tenant_id, dict_code, dict_name, dict_scope, status, cacheable, ext_json, remark,
                  created_by, created_at, updated_by, updated_at, deleted, version
                )
                SELECT ?, s.dict_code, s.dict_name, 'TENANT', s.status, s.cacheable, s.ext_json, s.remark,
                       1, NOW(3), 1, NOW(3), 0, 0
                FROM sys_dict_type s
                WHERE s.tenant_id = ? AND s.deleted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM sys_dict_type t
                    WHERE t.tenant_id = ? AND t.dict_code = s.dict_code AND t.deleted = 0
                  )
                """, tenantId, SOURCE_TENANT_ID, tenantId);

        jdbcTemplate.update("""
                INSERT INTO sys_dict_item (
                  tenant_id, dict_type_id, item_value, item_label, item_color, item_tag, parent_id, sort_no,
                  status, is_default, ext_json, remark, created_by, created_at, updated_by, updated_at, deleted, version
                )
                SELECT ?, tgt_type.id, si.item_value, si.item_label, si.item_color, si.item_tag, 0, si.sort_no,
                       si.status, si.is_default, si.ext_json, si.remark, 1, NOW(3), 1, NOW(3), 0, 0
                FROM sys_dict_item si
                INNER JOIN sys_dict_type src_type ON src_type.id = si.dict_type_id AND src_type.tenant_id = ? AND src_type.deleted = 0
                INNER JOIN sys_dict_type tgt_type ON tgt_type.tenant_id = ? AND tgt_type.dict_code = src_type.dict_code AND tgt_type.deleted = 0
                WHERE si.tenant_id = ? AND si.deleted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM sys_dict_item ti
                    WHERE ti.tenant_id = ? AND ti.dict_type_id = tgt_type.id AND ti.item_value = si.item_value AND ti.deleted = 0
                  )
                """, tenantId, SOURCE_TENANT_ID, tenantId, SOURCE_TENANT_ID, tenantId);
    }

    private void cloneSystemConfigsAndI18n(Long tenantId) {
        jdbcTemplate.update("""
                INSERT INTO sys_config (
                  tenant_id, config_group, config_key, config_name, config_value, value_type, encrypted, status, remark,
                  created_by, created_at, updated_by, updated_at, deleted, version
                )
                SELECT ?, s.config_group, s.config_key, s.config_name, s.config_value, s.value_type, 0, s.status, s.remark,
                       1, NOW(3), 1, NOW(3), 0, 0
                FROM sys_config s
                WHERE s.tenant_id = ? AND s.deleted = 0 AND s.encrypted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM sys_config t
                    WHERE t.tenant_id = ? AND t.config_group = s.config_group AND t.config_key = s.config_key AND t.deleted = 0
                  )
                """, tenantId, SOURCE_TENANT_ID, tenantId);

        jdbcTemplate.update("""
                INSERT INTO sys_i18n_message (
                  tenant_id, locale, message_group, message_key, message_value, remark,
                  created_by, created_at, updated_by, updated_at, deleted, version
                )
                SELECT ?, s.locale, s.message_group, s.message_key, s.message_value, s.remark,
                       1, NOW(3), 1, NOW(3), 0, 0
                FROM sys_i18n_message s
                WHERE s.tenant_id = ? AND s.deleted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM sys_i18n_message t
                    WHERE t.tenant_id = ? AND t.locale = s.locale AND t.message_group = s.message_group
                      AND t.message_key = s.message_key AND t.deleted = 0
                  )
                """, tenantId, SOURCE_TENANT_ID, tenantId);

        jdbcTemplate.update("""
                INSERT INTO iam_data_scope (
                  tenant_id, scope_code, scope_name, scope_type, scope_rule_json, status, remark,
                  created_by, created_at, updated_by, updated_at, deleted, version
                )
                SELECT ?, s.scope_code, s.scope_name, s.scope_type, s.scope_rule_json, s.status, s.remark,
                       1, NOW(3), 1, NOW(3), 0, 0
                FROM iam_data_scope s
                WHERE s.tenant_id = ? AND s.deleted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM iam_data_scope t
                    WHERE t.tenant_id = ? AND t.scope_code = s.scope_code AND t.deleted = 0
                  )
                """, tenantId, SOURCE_TENANT_ID, tenantId);
    }

    private void cloneGovernanceBaselines(Long tenantId) {
        jdbcTemplate.update("""
                INSERT INTO sys_sequence_rule (
                  tenant_id, rule_code, rule_name, prefix, date_pattern, seq_length, step, current_value,
                  reset_cycle, status, remark, created_by, created_at, updated_by, updated_at, deleted, version
                )
                SELECT ?, s.rule_code, s.rule_name, s.prefix, s.date_pattern, s.seq_length, s.step, 0,
                       s.reset_cycle, s.status, s.remark, 1, NOW(3), 1, NOW(3), 0, 0
                FROM sys_sequence_rule s
                WHERE s.tenant_id = ? AND s.deleted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM sys_sequence_rule t
                    WHERE t.tenant_id = ? AND t.rule_code = s.rule_code AND t.deleted = 0
                  )
                """, tenantId, SOURCE_TENANT_ID, tenantId);

        jdbcTemplate.update("""
                INSERT INTO sec_risk_rule (
                  tenant_id, rule_code, rule_name, risk_type, rule_expr, action_json, status, remark,
                  created_by, created_at, updated_by, updated_at, deleted, version
                )
                SELECT ?, s.rule_code, s.rule_name, s.risk_type, s.rule_expr, s.action_json, s.status, s.remark,
                       1, NOW(3), 1, NOW(3), 0, 0
                FROM sec_risk_rule s
                WHERE s.tenant_id = ? AND s.deleted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM sec_risk_rule t
                    WHERE t.tenant_id = ? AND t.rule_code = s.rule_code AND t.deleted = 0
                  )
                """, tenantId, SOURCE_TENANT_ID, tenantId);

        jdbcTemplate.update("""
                INSERT INTO system_notify_template (
                  tenant_id, template_code, template_name, template_type, title_template, content_template, status, remark, deleted
                )
                SELECT ?, s.template_code, s.template_name, s.template_type, s.title_template, s.content_template, s.status, s.remark, 0
                FROM system_notify_template s
                WHERE s.tenant_id = ? AND s.deleted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM system_notify_template t
                    WHERE t.tenant_id = ? AND t.template_code = s.template_code AND t.deleted = 0
                  )
                """, tenantId, SOURCE_TENANT_ID, tenantId);
    }

    private void initializeFileStorageAndRoutes(Long tenantId) {
        jdbcTemplate.update("""
                INSERT INTO file_storage (
                  tenant_id, storage_code, storage_name, storage_type, endpoint, bucket_default, base_path,
                  default_folder, path_pattern, max_file_mb, public_base_url, status, is_default, upload_mode,
                  remark, created_by, updated_by, deleted
                )
                SELECT ?, 'local_default', '本地高可用文件存储', 'LOCAL', './data/storage', 'local-disk', './data/storage',
                       'uploads', 't_{tenantId}/{folder}/{yyyy}/{MM}/{uuid}_{filename}', 100, '/api/file/raw-download',
                       'ENABLED', 1, 'DIRECT_SERVER', '租户默认本地存储源', 1, 1, 0
                FROM dual
                WHERE NOT EXISTS (
                  SELECT 1 FROM file_storage fs WHERE fs.tenant_id = ? AND fs.deleted = 0
                )
                """, tenantId, tenantId);

        List<Long> storageIds = jdbcTemplate.query(
                "SELECT id FROM file_storage WHERE tenant_id = ? AND deleted = 0 ORDER BY is_default DESC, id ASC LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                tenantId
        );
        if (!storageIds.isEmpty()) {
            long storageId = storageIds.get(0);
            jdbcTemplate.update("""
                    INSERT INTO file_bucket (
                      tenant_id, storage_id, bucket_code, bucket_name, bucket_type, bucket_path, status, remark,
                      created_by, updated_by, deleted
                    )
                    SELECT ?, ?, 'default_bucket', '默认文件桶', 'PRIVATE', CONCAT('/t_', ?, '/default'),
                           'ENABLED', '租户默认文件桶', 1, 1, 0
                    FROM dual
                    WHERE NOT EXISTS (
                      SELECT 1 FROM file_bucket fb WHERE fb.tenant_id = ? AND fb.bucket_code = 'default_bucket' AND fb.deleted = 0
                    )
                    """, tenantId, storageId, tenantId, tenantId);
        }

        jdbcTemplate.update("""
                INSERT INTO file_upload_route (
                  tenant_id, storage_id, biz_type, route_name, upload_mode, target_dir, path_pattern,
                  max_file_mb, allowed_exts, priority, status, remark, created_by, updated_by, deleted
                )
                SELECT ?, NULL, s.biz_type, s.route_name, s.upload_mode, s.target_dir,
                       't_{tenantId}/{folder}/{yyyy}/{MM}/{uuid}_{filename}',
                       s.max_file_mb, s.allowed_exts, s.priority, s.status, s.remark, 1, 1, 0
                FROM file_upload_route s
                WHERE s.tenant_id = ? AND s.deleted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM file_upload_route t WHERE t.tenant_id = ? AND t.biz_type = s.biz_type AND t.deleted = 0
                  )
                """, tenantId, SOURCE_TENANT_ID, tenantId);
    }

    private void initializeErpAndSchedulerBaselines(Long tenantId, String tenantName) {
        jdbcTemplate.update("""
                INSERT INTO erp_warehouse (
                  tenant_id, warehouse_code, warehouse_name, status, created_by, updated_by, deleted
                )
                SELECT ?, 'WH-DEFAULT', ?, 'ENABLED', 1, 1, 0
                FROM dual
                WHERE NOT EXISTS (
                  SELECT 1 FROM erp_warehouse w WHERE w.tenant_id = ? AND w.deleted = 0
                )
                """, tenantId, tenantName + "默认中心总仓", tenantId);

        jdbcTemplate.update("""
                INSERT INTO sched_executor (
                  tenant_id, executor_code, executor_name, executor_type, address, status, heartbeat_at,
                  remark, created_by, updated_by, deleted
                )
                SELECT ?, 'local-default', '本地执行器', 'LOCAL', '127.0.0.1', 'ENABLED', NOW(3),
                       '租户默认本地任务执行器', 1, 1, 0
                FROM dual
                WHERE NOT EXISTS (
                  SELECT 1 FROM sched_executor se WHERE se.tenant_id = ? AND se.executor_code = 'local-default' AND se.deleted = 0
                )
                """, tenantId, tenantId);
    }

    private void cloneAiBaselines(Long tenantId) {
        jdbcTemplate.update("""
                INSERT INTO ai_provider (
                  tenant_id, provider_code, provider_name, provider_type, base_url, api_key,
                  status, is_default, sort_no, remark, created_by, updated_by, deleted
                )
                SELECT ?, s.provider_code, s.provider_name, s.provider_type, s.base_url, '',
                       s.status, s.is_default, s.sort_no, s.remark, 1, 1, 0
                FROM ai_provider s
                WHERE s.tenant_id = ? AND s.deleted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM ai_provider t WHERE t.tenant_id = ? AND t.provider_code = s.provider_code AND t.deleted = 0
                  )
                """, tenantId, SOURCE_TENANT_ID, tenantId);

        jdbcTemplate.update("""
                INSERT INTO ai_model_config (
                  tenant_id, provider_id, model_id, model_name, category, context_window, max_tokens,
                  temperature, is_default, status, sort_no, created_by, updated_by, deleted
                )
                SELECT ?, tgt_p.id, s.model_id, s.model_name, s.category, s.context_window, s.max_tokens,
                       s.temperature, s.is_default, s.status, s.sort_no, 1, 1, 0
                FROM ai_model_config s
                INNER JOIN ai_provider src_p ON src_p.id = s.provider_id AND src_p.tenant_id = ? AND src_p.deleted = 0
                INNER JOIN ai_provider tgt_p ON tgt_p.tenant_id = ? AND tgt_p.provider_code = src_p.provider_code AND tgt_p.deleted = 0
                WHERE s.tenant_id = ? AND s.deleted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM ai_model_config t WHERE t.tenant_id = ? AND t.model_id = s.model_id AND t.deleted = 0
                  )
                """, tenantId, SOURCE_TENANT_ID, tenantId, SOURCE_TENANT_ID, tenantId);

        jdbcTemplate.update("""
                INSERT INTO ai_prompt (
                  tenant_id, prompt_code, prompt_name, category, active_version, status, remark,
                  created_by, updated_by, deleted
                )
                SELECT ?, s.prompt_code, s.prompt_name, s.category, s.active_version, s.status, s.remark, 1, 1, 0
                FROM ai_prompt s
                WHERE s.tenant_id = ? AND s.deleted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM ai_prompt t WHERE t.tenant_id = ? AND t.prompt_code = s.prompt_code AND t.deleted = 0
                  )
                """, tenantId, SOURCE_TENANT_ID, tenantId);

        jdbcTemplate.update("""
                INSERT INTO ai_prompt_version (
                  tenant_id, prompt_id, version_no, system_prompt, user_template, variables_json,
                  model_hint, changelog, is_published, remark, created_by, updated_by, deleted
                )
                SELECT ?, tgt_pr.id, s.version_no, s.system_prompt, s.user_template, s.variables_json,
                       s.model_hint, s.changelog, s.is_published, s.remark, 1, 1, 0
                FROM ai_prompt_version s
                INNER JOIN ai_prompt src_pr ON src_pr.id = s.prompt_id AND src_pr.tenant_id = ? AND src_pr.deleted = 0
                INNER JOIN ai_prompt tgt_pr ON tgt_pr.tenant_id = ? AND tgt_pr.prompt_code = src_pr.prompt_code AND tgt_pr.deleted = 0
                WHERE s.tenant_id = ? AND s.deleted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM ai_prompt_version t
                    WHERE t.tenant_id = ? AND t.prompt_id = tgt_pr.id AND t.version_no = s.version_no AND t.deleted = 0
                  )
                """, tenantId, SOURCE_TENANT_ID, tenantId, SOURCE_TENANT_ID, tenantId);

        jdbcTemplate.update("""
                INSERT INTO ai_tool (
                  tenant_id, tool_code, tool_name, semantic_version, description, input_schema_json,
                  output_schema_json, permission_code, risk_level, adapter_type, mcp_server_id,
                  whitelisted, idempotent, timeout_ms, status, remark, created_by, updated_by, deleted
                )
                SELECT ?, s.tool_code, s.tool_name, s.semantic_version, s.description, s.input_schema_json,
                       s.output_schema_json, s.permission_code, s.risk_level, s.adapter_type, NULL,
                       s.whitelisted, s.idempotent, s.timeout_ms, s.status, s.remark, 1, 1, 0
                FROM ai_tool s
                WHERE s.tenant_id = ? AND s.deleted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM ai_tool t WHERE t.tenant_id = ? AND t.tool_code = s.tool_code AND t.deleted = 0
                  )
                """, tenantId, SOURCE_TENANT_ID, tenantId);
    }

    private void bindUserRole(Long tenantId, Long userId, Long roleId) {
        jdbcTemplate.update("""
                INSERT INTO iam_user_role (
                  tenant_id, user_id, role_id, source_type, expire_at, created_by, created_at, updated_by, updated_at, deleted, version, remark
                ) VALUES (?, ?, ?, 'MANUAL', NULL, 1, NOW(3), 1, NOW(3), 0, 0, 'tenant bootstrap')
                """, tenantId, userId, roleId);
    }

    private void createPortalClientAndTerminal(Long tenantId,
                                               String tenantCode,
                                               String tenantName,
                                               long loginPolicyId,
                                               long passwordPolicyId) {
        Map<String, Object> clientSource = queryRequired("""
                SELECT *
                FROM iam_portal_client
                WHERE tenant_id = ?
                  AND deleted = 0
                  AND status = 'ENABLED'
                ORDER BY id ASC
                LIMIT 1
                """, "初始化门户 client 失败：缺少基础入口配置", SOURCE_TENANT_ID);

        KeyHolder clientKeyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO iam_portal_client (
                      tenant_id, client_id, tenant_code, client_name, portal_title, welcome_title, welcome_text,
                      logo_url, theme_code, background_image_url, background_color, filing_info, login_policy_id,
                      password_policy_id, captcha_mode, slider_reserved, status, created_by, created_at, updated_by, updated_at,
                      deleted, version, remark
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ENABLED', 1, ?, 1, ?, 0, 0, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, tenantId);
            statement.setString(2, tenantCode + "-console");
            statement.setString(3, tenantCode);
            statement.setString(4, tenantName + "控制台");
            statement.setString(5, tenantName + "开发平台");
            statement.setString(6, tenantName + "统一入口");
            statement.setString(7, tenantName + "租户控制台登录入口");
            statement.setObject(8, clientSource.get("logo_url"));
            statement.setObject(9, clientSource.get("theme_code"));
            statement.setObject(10, clientSource.get("background_image_url"));
            statement.setObject(11, clientSource.get("background_color"));
            statement.setObject(12, clientSource.get("filing_info"));
            statement.setLong(13, loginPolicyId);
            statement.setLong(14, passwordPolicyId);
            statement.setObject(15, clientSource.get("captcha_mode"));
            statement.setObject(16, clientSource.get("slider_reserved"));
            statement.setTimestamp(17, Timestamp.valueOf(LocalDateTime.now()));
            statement.setTimestamp(18, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(19, "tenant bootstrap");
            return statement;
        }, clientKeyHolder);
        long portalClientId = generatedId(clientKeyHolder, "TENANT_BOOTSTRAP_FAILED", "初始化门户 client 失败");

        Map<String, Object> terminalSource = queryRequired("""
                SELECT *
                FROM iam_portal_terminal
                WHERE tenant_id = ?
                  AND terminal_code = 'web'
                  AND deleted = 0
                  AND status = 'ENABLED'
                ORDER BY id ASC
                LIMIT 1
                """, "初始化门户 terminal 失败：缺少 web 终端配置", SOURCE_TENANT_ID);

        jdbcTemplate.update("""
                INSERT INTO iam_portal_terminal (
                  tenant_id, portal_client_id, terminal_code, terminal_name, terminal_type, portal_title, logo_url,
                  theme_code, background_image_url, background_color, login_policy_id, password_policy_id,
                  captcha_mode, slider_reserved, is_default, status, created_by, created_at, updated_by, updated_at,
                  deleted, version, remark
                ) VALUES (?, ?, 'web', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 'ENABLED', 1, NOW(3), 1, NOW(3), 0, 0, 'tenant bootstrap')
                """,
                tenantId,
                portalClientId,
                tenantName + " Web",
                terminalSource.get("terminal_type"),
                tenantName + "开发平台",
                terminalSource.get("logo_url"),
                terminalSource.get("theme_code"),
                terminalSource.get("background_image_url"),
                terminalSource.get("background_color"),
                loginPolicyId,
                passwordPolicyId,
                terminalSource.get("captcha_mode"),
                terminalSource.get("slider_reserved"));
    }

    private Map<String, Object> queryRequired(String sql, String message, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        if (rows.isEmpty()) {
            throw new BizException("TENANT_BOOTSTRAP_FAILED", message);
        }
        return rows.get(0);
    }

    private long generatedId(KeyHolder keyHolder, String code, String message) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException(code, message);
        }
        return key.longValue();
    }

    private String hash(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
