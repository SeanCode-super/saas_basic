package com.saasbasics.platform.modules.tenant.service;

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
import java.util.List;
import java.util.Map;
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

    public TenantBootstrapService(JdbcTemplate jdbcTemplate, AuditTrailService auditTrailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditTrailService = auditTrailService;
    }

    @Transactional
    public void initializeTenant(Long tenantId, String tenantCode, String tenantName) {
        long loginPolicyId = cloneLoginPolicy(tenantId, tenantCode, tenantName);
        long passwordPolicyId = clonePasswordPolicy(tenantId, tenantCode, tenantName);
        long roleId = createAdminRole(tenantId, tenantCode, tenantName);
        cloneApiResources(tenantId);
        cloneMenus(tenantId);
        assignAllApiResourcesToRole(tenantId, roleId);
        assignAllMenusToRole(tenantId, roleId);
        createDefaultDataPermissionRules(tenantId, roleId);
        long userId = createAdminUser(tenantId, tenantCode, tenantName);
        bindUserRole(tenantId, userId, roleId);
        createPortalClientAndTerminal(tenantId, tenantCode, tenantName, loginPolicyId, passwordPolicyId);
        auditTrailService.record("tenant", "tenant_bootstrap", String.valueOf(tenantId), "BOOTSTRAP", tenantCode, "initialized", true);
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
                  tenant_id, parent_id, menu_type, menu_code, menu_name, route_path, component_path, permission_code, icon,
                  visible, keep_alive, sort_no, status, meta_json, created_by, created_at, updated_by, updated_at, deleted, version, remark
                )
                SELECT ?, 0, menu_type, menu_code, menu_name, route_path, component_path, permission_code, icon,
                       visible, keep_alive, sort_no, status, meta_json, 1, NOW(3), 1, NOW(3), 0, 0, 'tenant bootstrap'
                FROM iam_menu
                WHERE tenant_id = ?
                  AND deleted = 0
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
                SELECT ?, id, menu_code, 'ROLE', CAST(? AS CHAR),
                       CASE menu_code
                         WHEN 'iam_department' THEN JSON_ARRAY('iam_department:create', 'iam_department:edit', 'iam_department:toggle')
                         WHEN 'iam_position' THEN JSON_ARRAY('iam_position:create', 'iam_position:edit', 'iam_position:toggle')
                         WHEN 'iam_employee' THEN JSON_ARRAY('iam_employee:create', 'iam_employee:edit', 'iam_employee:toggle')
                         WHEN 'iam_user' THEN JSON_ARRAY('iam_user:create', 'iam_user:edit', 'iam_user:toggle')
                         WHEN 'iam_role' THEN JSON_ARRAY('iam_role:create', 'iam_role:edit', 'iam_role:toggle')
                         WHEN 'iam_api_resource' THEN JSON_ARRAY('iam_api_resource:create', 'iam_api_resource:edit', 'iam_api_resource:toggle')
                         WHEN 'iam_user_role' THEN JSON_ARRAY('iam_user_role:assign', 'iam_user_role:save')
                         WHEN 'iam_role_api' THEN JSON_ARRAY('iam_role_api:assign', 'iam_role_api:save')
                         WHEN 'iam_menu_center' THEN JSON_ARRAY(
                           'iam_menu:create', 'iam_menu:edit', 'iam_menu:toggle',
                           'iam_menu_permission:create', 'iam_menu_permission:edit', 'iam_menu_permission:toggle', 'iam_menu_permission:save'
                         )
                         WHEN 'iam_data_scope_center' THEN JSON_ARRAY(
                           'iam_data_scope_rule:create', 'iam_data_scope_rule:edit', 'iam_data_scope_rule:toggle', 'iam_data_scope_rule:save',
                           'iam_data_scope_template:create', 'iam_data_scope_template:edit', 'iam_data_scope_template:toggle'
                         )
                         ELSE JSON_ARRAY()
                       END,
                       'ENABLED',
                       1, NOW(3), 1, NOW(3), 0, 0, 'tenant bootstrap'
                FROM iam_menu
                WHERE tenant_id = ?
                  AND deleted = 0
                """, tenantId, roleId, tenantId);
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

    private long createAdminUser(Long tenantId, String tenantCode, String tenantName) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO iam_user (
                      tenant_id, public_id, user_code, username, nickname, employee_id, user_type, status, mobile, email,
                      last_login_at, last_login_ip, password_hash, password_changed_at, need_reset_password,
                      created_by, created_at, updated_by, updated_at, deleted, version, remark
                    ) VALUES (?, ?, 'TENANT_ADMIN', 'admin', ?, 0, 'STAFF', 'ENABLED', NULL, ?,
                              NULL, NULL, ?, ?, 1, 1, ?, 1, ?, 0, 0, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, tenantId);
            statement.setString(2, uuidGenerator.generate().toString());
            statement.setString(3, tenantName + "管理员");
            statement.setString(4, "admin@" + tenantCode + ".local");
            statement.setString(5, hash(DEFAULT_ADMIN_PASSWORD));
            statement.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            statement.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            statement.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(9, "tenant bootstrap");
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "TENANT_BOOTSTRAP_FAILED", "初始化管理员用户失败");
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
