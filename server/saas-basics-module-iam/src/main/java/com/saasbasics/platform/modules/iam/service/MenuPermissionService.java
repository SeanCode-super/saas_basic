package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.iam.dto.AccessSubjectProfile;
import com.saasbasics.platform.modules.iam.dto.MenuPermissionResponse;
import com.saasbasics.platform.modules.iam.dto.MenuPermissionSaveRequest;
import com.saasbasics.platform.modules.iam.dto.MenuGrantItemRequest;
import com.saasbasics.platform.modules.iam.dto.MenuSubjectGrantSaveRequest;
import com.saasbasics.platform.modules.iam.dto.MenuResponse;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.MenuPermissionEntity;
import com.saasbasics.platform.modules.iam.entity.RoleMenuEntity;
import com.saasbasics.platform.modules.iam.mapper.MenuMapper;
import com.saasbasics.platform.modules.iam.mapper.MenuPermissionMapper;
import com.saasbasics.platform.modules.iam.mapper.RoleMenuMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.saasbasics.platform.common.tenant.TenantFeatureProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuPermissionService {

    private final ObjectProvider<MenuPermissionMapper> menuPermissionMapperProvider;
    private final ObjectProvider<MenuMapper> menuMapperProvider;
    private final ObjectProvider<RoleMenuMapper> roleMenuMapperProvider;
    private final ObjectProvider<TenantFeatureProvider> tenantFeatureProvider;
    private final AccessSubjectProfileService accessSubjectProfileService;
    private final AuditTrailService auditTrailService;
    private final ObjectMapper objectMapper;

    @Autowired
    public MenuPermissionService(ObjectProvider<MenuPermissionMapper> menuPermissionMapperProvider,
                                 ObjectProvider<MenuMapper> menuMapperProvider,
                                 ObjectProvider<RoleMenuMapper> roleMenuMapperProvider,
                                 ObjectProvider<TenantFeatureProvider> tenantFeatureProvider,
                                 AccessSubjectProfileService accessSubjectProfileService,
                                 AuditTrailService auditTrailService,
                                 ObjectMapper objectMapper) {
        this.menuPermissionMapperProvider = menuPermissionMapperProvider;
        this.menuMapperProvider = menuMapperProvider;
        this.roleMenuMapperProvider = roleMenuMapperProvider;
        this.tenantFeatureProvider = tenantFeatureProvider;
        this.accessSubjectProfileService = accessSubjectProfileService;
        this.auditTrailService = auditTrailService;
        this.objectMapper = objectMapper;
    }

    public MenuPermissionService(ObjectProvider<MenuPermissionMapper> menuPermissionMapperProvider,
                                 ObjectProvider<MenuMapper> menuMapperProvider,
                                 ObjectProvider<RoleMenuMapper> roleMenuMapperProvider,
                                 AccessSubjectProfileService accessSubjectProfileService,
                                 AuditTrailService auditTrailService,
                                 ObjectMapper objectMapper) {
        this(menuPermissionMapperProvider, menuMapperProvider, roleMenuMapperProvider, null, accessSubjectProfileService, auditTrailService, objectMapper);
    }

    public MenuPermissionService(ObjectProvider<MenuPermissionMapper> menuPermissionMapperProvider,
                                 ObjectProvider<MenuMapper> menuMapperProvider,
                                 AccessSubjectProfileService accessSubjectProfileService,
                                 AuditTrailService auditTrailService,
                                 ObjectMapper objectMapper) {
        this(menuPermissionMapperProvider, menuMapperProvider, null, null, accessSubjectProfileService, auditTrailService, objectMapper);
    }

    public List<MenuPermissionResponse> listPermissions() {
        Long tenantId = requiredTenantId();
        return requiredMapper().selectPermissionList()
                .stream()
                .filter(permission -> tenantId.equals(permission.tenantId()))
                .toList();
    }

    public MenuPermissionResponse getPermission(Long id) {
        MenuPermissionResponse response = requiredMapper().selectPermissionById(id);
        if (response == null) {
            throw new BizException("IAM_MENU_PERMISSION_NOT_FOUND", "菜单权限不存在");
        }
        return response;
    }

    public MenuPermissionResponse createPermission(MenuPermissionSaveRequest request) {
        MenuPermissionEntity entity = new MenuPermissionEntity();
        apply(entity, request);
        requiredMapper().insert(entity);
        auditTrailService.record("iam", "menu_permission", String.valueOf(entity.getId()), "CREATE", null, request.subjectType() + ":" + request.subjectValue(), true);
        return getPermission(entity.getId());
    }

    public MenuPermissionResponse updatePermission(Long id, MenuPermissionSaveRequest request) {
        MenuPermissionEntity entity = requiredEntity(id);
        apply(entity, request);
        requiredMapper().updateById(entity);
        auditTrailService.record("iam", "menu_permission", String.valueOf(id), "UPDATE", null, request.subjectType() + ":" + request.subjectValue(), true);
        return getPermission(id);
    }

    public MenuPermissionResponse updateStatus(Long id, StatusUpdateRequest request) {
        MenuPermissionEntity entity = requiredEntity(id);
        entity.setStatus(request.status());
        requiredMapper().updateById(entity);
        auditTrailService.record("iam", "menu_permission", String.valueOf(id), "STATUS", null, request.status(), true);
        return getPermission(id);
    }

    public List<Long> getRoleMenuIds(Long roleId) {
        Long tenantId = requiredTenantId();
        RoleMenuMapper roleMenuMapper = roleMenuMapperProvider.getIfAvailable();
        if (roleMenuMapper != null) {
            return roleMenuMapper.selectMenuIdsByRoleId(tenantId, roleId);
        }
        return List.of();
    }

    @Transactional
    public List<Long> replaceRoleMenuGrants(Long roleId, List<Long> menuIds) {
        Long tenantId = requiredTenantId();
        RoleMenuMapper roleMenuMapper = requiredRoleMenuMapper();
        Map<Long, MenuResponse> allMenus = requiredMenuMapper().selectMenuList()
                .stream()
                .filter(menu -> tenantId.equals(menu.tenantId()))
                .collect(Collectors.toMap(MenuResponse::id, item -> item));

        LinkedHashSet<Long> targetIds = new LinkedHashSet<>();
        if (menuIds != null) {
            for (Long menuId : menuIds) {
                if (allMenus.containsKey(menuId)) {
                    targetIds.add(menuId);
                    // 自动关联所有上级祖先目录
                    Long parentId = allMenus.get(menuId).parentId();
                    while (parentId != null && parentId > 0 && allMenus.containsKey(parentId)) {
                        targetIds.add(parentId);
                        parentId = allMenus.get(parentId).parentId();
                    }
                }
            }
        }

        roleMenuMapper.deleteByRoleId(tenantId, roleId);
        for (Long menuId : targetIds) {
            roleMenuMapper.insert(new RoleMenuEntity(tenantId, roleId, menuId));
        }

        // 同步兼容旧的 iam_menu_permission 表
        syncRoleMenuPermissionTable(tenantId, roleId, targetIds, allMenus);

        auditTrailService.record(
                "iam",
                "role_menu",
                String.valueOf(roleId),
                "REPLACE",
                null,
                String.valueOf(targetIds.size()),
                true
        );
        return new ArrayList<>(targetIds);
    }

    @Transactional
    public List<MenuPermissionResponse> replaceSubjectGrants(MenuSubjectGrantSaveRequest request) {
        Long tenantId = requiredTenantId();
        String subjectType = normalizeSubjectType(request.subjectType());
        String subjectValue = request.subjectValue().trim();
        Map<Long, MenuResponse> menus = requiredMenuMapper().selectMenuList()
                .stream()
                .filter(menu -> tenantId.equals(menu.tenantId()))
                .collect(Collectors.toMap(MenuResponse::id, item -> item));

        requiredMapper().deleteSubjectGrants(tenantId, subjectType, subjectValue);
        LinkedHashSet<Long> processedMenus = new LinkedHashSet<>();
        LinkedHashSet<Long> atomicMenuIds = new LinkedHashSet<>();

        for (MenuGrantItemRequest grant : request.grants()) {
            if (!processedMenus.add(grant.menuId())) {
                throw new BizException("IAM_MENU_GRANT_DUPLICATE", "同一菜单不能重复授权");
            }
            MenuResponse menu = menus.get(grant.menuId());
            if (menu == null || !isGrantableMenu(menu)) {
                throw new BizException("IAM_MENU_GRANT_INVALID", "授权菜单不存在或不是可访问页面");
            }

            atomicMenuIds.add(grant.menuId());
            // 补全父级目录
            Long parentId = menu.parentId();
            while (parentId != null && parentId > 0 && menus.containsKey(parentId)) {
                atomicMenuIds.add(parentId);
                parentId = menus.get(parentId).parentId();
            }

            List<String> actionCodes = normalizeActionCodes(grant.actionCodes());
            validateActionCodes(menus.values(), tenantId, grant.menuId(), actionCodes);

            // 查找这些 actionCode 对应的 BUTTON 记录，加入原子权限
            for (MenuResponse candidate : menus.values()) {
                if (grant.menuId().equals(candidate.parentId())
                        && "BUTTON".equalsIgnoreCase(candidate.menuType())
                        && actionCodes.contains(candidate.menuCode())) {
                    atomicMenuIds.add(candidate.id());
                }
            }

            MenuPermissionEntity entity = new MenuPermissionEntity();
            entity.setTenantId(tenantId);
            entity.setMenuId(menu.id());
            entity.setMenuCode(menu.menuCode());
            entity.setSubjectType(subjectType);
            entity.setSubjectValue(subjectValue);
            entity.setButtonCodesJson(writeActionCodes(actionCodes));
            entity.setStatus("ENABLED");
            entity.setRemark("权限配置保存");
            requiredMapper().insert(entity);
        }

        // 如果是角色授权，同步更新原子表 iam_role_menu
        if ("ROLE".equals(subjectType)) {
            try {
                Long roleId = Long.parseLong(subjectValue);
                RoleMenuMapper roleMenuMapper = roleMenuMapperProvider.getIfAvailable();
                if (roleMenuMapper != null) {
                    roleMenuMapper.deleteByRoleId(tenantId, roleId);
                    for (Long menuId : atomicMenuIds) {
                        roleMenuMapper.insert(new RoleMenuEntity(tenantId, roleId, menuId));
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }

        auditTrailService.record(
                "iam",
                "menu_permission",
                subjectType + ":" + subjectValue,
                "REPLACE",
                null,
                String.valueOf(processedMenus.size()),
                true
        );
        return listPermissions().stream()
                .filter(permission -> subjectType.equals(permission.subjectType()))
                .filter(permission -> subjectValue.equals(permission.subjectValue()))
                .toList();
    }

    public List<MenuResponse> listCurrentMenus(AuthPrincipal principal) {
        if (principal == null) {
            return List.of();
        }

        TenantFeatureProvider featureProvider = tenantFeatureProvider != null ? tenantFeatureProvider.getIfAvailable() : null;
        Set<String> effectiveFeatures = (featureProvider != null && principal.tenantId() != null)
                ? featureProvider.getEffectiveFeatures(principal.tenantId())
                : Set.of("*");

        // 1. 系统底层无租户环境 (Tenant 0 或 null) 直通系统所有启用的菜单
        if (principal.isSuperAdmin() && (principal.tenantId() == null || principal.tenantId() <= 0L)) {
            return requiredMenuMapper().selectMenuList()
                    .stream()
                    .filter(menu -> menu.tenantId() == null || Long.valueOf(0L).equals(menu.tenantId()))
                    .filter(menu -> "ENABLED".equalsIgnoreCase(menu.status()))
                    .toList();
        }

        // 2. 租户超级管理员 (Tenant >= 1L)：必须受该租户已获授权应用权限严格过滤！
        if (principal.isSuperAdmin()) {
            return requiredMenuMapper().selectMenuList()
                    .stream()
                    .filter(menu -> menu.tenantId() == null || Long.valueOf(0L).equals(menu.tenantId()) || principal.tenantId().equals(menu.tenantId()))
                    .filter(menu -> "ENABLED".equalsIgnoreCase(menu.status()))
                    .filter(menu -> isMenuAllowedForTenant(menu, effectiveFeatures))
                    .toList();
        }

        AccessSubjectProfile subjectProfile = accessSubjectProfileService.load(principal.tenantId(), principal.userId());
        LinkedHashSet<Long> grantedMenuIds = new LinkedHashSet<>();

        // 1. 从原子化 iam_role_menu 中快速获取角色授权菜单
        RoleMenuMapper roleMenuMapper = roleMenuMapperProvider.getIfAvailable();
        if (roleMenuMapper != null && principal.roleIds() != null && !principal.roleIds().isEmpty()) {
            Set<Long> roleMenuIds = roleMenuMapper.selectMenuIdsByRoleIds(principal.tenantId(), principal.roleIds());
            if (roleMenuIds != null) {
                grantedMenuIds.addAll(roleMenuIds);
            }
        }

        // 2. 从 iam_menu_permission 中获取其他主体例外授权或补充授权
        List<MenuPermissionEntity> grants = requiredMapper().selectList(new LambdaQueryWrapper<MenuPermissionEntity>()
                .eq(MenuPermissionEntity::getTenantId, principal.tenantId())
                .eq(MenuPermissionEntity::getDeleted, 0)
                .eq(MenuPermissionEntity::getStatus, "ENABLED"));

        for (MenuPermissionEntity grant : grants) {
            if (subjectProfile.matches(grant.getSubjectType(), grant.getSubjectValue())) {
                grantedMenuIds.add(grant.getMenuId());
            }
        }

        if (grantedMenuIds.isEmpty()) {
            return List.of();
        }

        List<MenuResponse> allMenus = requiredMenuMapper().selectMenuList()
                .stream()
                .filter(menu -> menu.tenantId() == null || Long.valueOf(0L).equals(menu.tenantId()) || principal.tenantId().equals(menu.tenantId()))
                .filter(menu -> "ENABLED".equalsIgnoreCase(menu.status()))
                .filter(menu -> menu.permissionCode() == null || principal.permissions().contains(menu.permissionCode()))
                .filter(menu -> isMenuAllowedForTenant(menu, effectiveFeatures))
                .toList();

        Map<Long, MenuResponse> menuMap = allMenus.stream().collect(Collectors.toMap(MenuResponse::id, item -> item));
        LinkedHashSet<Long> visibleIds = new LinkedHashSet<>();
        for (Long menuId : grantedMenuIds) {
            Long current = menuId;
            while (current != null && current > 0 && visibleIds.add(current)) {
                MenuResponse parent = menuMap.get(current);
                if (parent == null || parent.parentId() == null || parent.parentId() <= 0) {
                    break;
                }
                current = parent.parentId();
            }
        }

        return allMenus.stream().filter(menu -> visibleIds.contains(menu.id())).toList();
    }

    private boolean isMenuAllowedForTenant(MenuResponse menu, Set<String> effectiveFeatures) {
        if (effectiveFeatures.contains("*")) {
            return true;
        }
        String appCode = menu.appCode();
        if (appCode == null || appCode.isBlank() || "dashboard".equalsIgnoreCase(appCode.trim())) {
            return true;
        }
        String target = appCode.trim().toLowerCase(java.util.Locale.ROOT);
        for (String eff : effectiveFeatures) {
            String low = eff.toLowerCase(java.util.Locale.ROOT);
            if ("*".equals(low)) {
                return true;
            }
            if (low.equals(target)) {
                return true;
            }
            if (low.startsWith("saas." + target + ".") || low.equals("saas." + target)) {
                return true;
            }
            if (target.startsWith("saas." + low + ".") || target.equals("saas." + low)) {
                return true;
            }
        }
        return false;
    }

    public List<String> resolveButtonPermissions(AuthPrincipal principal) {
        if (principal == null) {
            return List.of();
        }
        // 超级管理员 / 平台管理员直通所有按钮操作
        if (principal.isSuperAdmin()) {
            return List.of("*");
        }

        AccessSubjectProfile subjectProfile = accessSubjectProfileService.load(principal.tenantId(), principal.userId());
        LinkedHashSet<String> buttonPermissions = new LinkedHashSet<>();

        // 1. 从原子表 iam_role_menu 结合 iam_menu 高效获取按钮权限
        RoleMenuMapper roleMenuMapper = roleMenuMapperProvider.getIfAvailable();
        if (roleMenuMapper != null && principal.roleIds() != null && !principal.roleIds().isEmpty()) {
            List<String> roleButtons = roleMenuMapper.selectButtonCodesByRoleIds(principal.tenantId(), principal.roleIds());
            if (roleButtons != null) {
                buttonPermissions.addAll(roleButtons);
            }
        }

        // 2. 从 iam_menu_permission 中获取例外主体权限
        List<MenuPermissionEntity> grants = requiredMapper().selectList(new LambdaQueryWrapper<MenuPermissionEntity>()
                .eq(MenuPermissionEntity::getTenantId, principal.tenantId())
                .eq(MenuPermissionEntity::getDeleted, 0)
                .eq(MenuPermissionEntity::getStatus, "ENABLED"));

        for (MenuPermissionEntity grant : grants) {
            if (!subjectProfile.matches(grant.getSubjectType(), grant.getSubjectValue())) {
                continue;
            }
            buttonPermissions.addAll(parseButtonCodes(grant.getMenuCode(), grant.getButtonCodesJson()));
        }
        return new ArrayList<>(buttonPermissions);
    }

    private void syncRoleMenuPermissionTable(Long tenantId, Long roleId, Set<Long> grantedIds, Map<Long, MenuResponse> allMenus) {
        MenuPermissionMapper mapper = requiredMapper();
        mapper.deleteSubjectGrants(tenantId, "ROLE", String.valueOf(roleId));

        // 按页面汇总按钮
        Map<Long, List<String>> pageButtons = new LinkedHashMap<>();
        for (Long id : grantedIds) {
            MenuResponse item = allMenus.get(id);
            if (item != null) {
                if (Set.of("MENU", "LINK").contains(item.menuType().toUpperCase())) {
                    pageButtons.computeIfAbsent(item.id(), k -> new ArrayList<>());
                } else if ("BUTTON".equalsIgnoreCase(item.menuType()) && item.parentId() > 0) {
                    pageButtons.computeIfAbsent(item.parentId(), k -> new ArrayList<>()).add(item.menuCode());
                }
            }
        }

        for (Map.Entry<Long, List<String>> entry : pageButtons.entrySet()) {
            MenuResponse page = allMenus.get(entry.getKey());
            if (page != null) {
                MenuPermissionEntity entity = new MenuPermissionEntity();
                entity.setTenantId(tenantId);
                entity.setMenuId(page.id());
                entity.setMenuCode(page.menuCode());
                entity.setSubjectType("ROLE");
                entity.setSubjectValue(String.valueOf(roleId));
                entity.setButtonCodesJson(writeActionCodes(entry.getValue()));
                entity.setStatus("ENABLED");
                entity.setRemark("角色原子授权同步");
                mapper.insert(entity);
            }
        }
    }

    private List<String> parseButtonCodes(String menuCode, String buttonCodesJson) {
        if (buttonCodesJson == null || buttonCodesJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> codes = objectMapper.readValue(buttonCodesJson, new TypeReference<List<String>>() {
            });
            return codes.stream()
                    .filter(code -> code != null && !code.isBlank())
                    .map(code -> code.contains(":") ? code : menuCode + ":" + code)
                    .toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String normalizeSubjectType(String value) {
        String normalized = value.trim().toUpperCase();
        if (!Set.of("ROLE", "USER", "DEPARTMENT", "POSITION", "COMPANY", "TENANT").contains(normalized)) {
            throw new BizException("IAM_MENU_SUBJECT_INVALID", "不支持的授权对象类型");
        }
        return normalized;
    }

    private List<String> normalizeActionCodes(List<String> source) {
        if (source == null) {
            return List.of();
        }
        return source.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String writeActionCodes(List<String> actionCodes) {
        try {
            return objectMapper.writeValueAsString(actionCodes);
        } catch (Exception exception) {
            throw new BizException("IAM_MENU_ACTION_SERIALIZATION_FAILED", "页面动作保存失败");
        }
    }

    private void apply(MenuPermissionEntity entity, MenuPermissionSaveRequest request) {
        Long tenantId = requiredTenantId();
        MenuResponse menu = requiredGrantableMenu(tenantId, request.menuId());
        List<String> actionCodes = parseRequestedActionCodes(request.buttonCodesJson());
        validateActionCodes(requiredMenuMapper().selectMenuList(), tenantId, menu.id(), actionCodes);
        entity.setTenantId(tenantId);
        entity.setMenuId(menu.id());
        entity.setMenuCode(menu.menuCode());
        entity.setSubjectType(normalizeSubjectType(request.subjectType()));
        entity.setSubjectValue(request.subjectValue().trim());
        entity.setButtonCodesJson(writeActionCodes(actionCodes));
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private MenuPermissionEntity requiredEntity(Long id) {
        MenuPermissionEntity entity = requiredMapper().selectById(id);
        Long tenantId = requiredTenantId();
        if (entity == null
                || (entity.getDeleted() != null && entity.getDeleted() == 1)
                || !tenantId.equals(entity.getTenantId())) {
            throw new BizException("IAM_MENU_PERMISSION_NOT_FOUND", "菜单权限不存在");
        }
        return entity;
    }

    private MenuResponse requiredGrantableMenu(Long tenantId, Long menuId) {
        MenuResponse menu = requiredMenuMapper().selectMenuById(menuId);
        if (menu == null || !tenantId.equals(menu.tenantId()) || !isGrantableMenu(menu)) {
            throw new BizException("IAM_MENU_GRANT_INVALID", "授权菜单不存在或不是可访问页面");
        }
        return menu;
    }

    private boolean isGrantableMenu(MenuResponse menu) {
        return Set.of("MENU", "LINK").contains(menu.menuType().toUpperCase())
                && "ENABLED".equalsIgnoreCase(menu.status());
    }

    private List<String> parseRequestedActionCodes(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(source, new TypeReference<List<String>>() {
            });
            return normalizeActionCodes(values);
        } catch (Exception exception) {
            throw new BizException("IAM_MENU_ACTION_FORMAT_INVALID", "页面操作格式无效");
        }
    }

    private void validateActionCodes(Iterable<MenuResponse> menus,
                                      Long tenantId,
                                      Long menuId,
                                      List<String> actionCodes) {
        Set<String> availableActionCodes = new LinkedHashSet<>();
        for (MenuResponse candidate : menus) {
            if (tenantId.equals(candidate.tenantId())
                    && menuId.equals(candidate.parentId())
                    && "BUTTON".equalsIgnoreCase(candidate.menuType())
                    && "ENABLED".equalsIgnoreCase(candidate.status())) {
                availableActionCodes.add(candidate.menuCode());
            }
        }
        if (!availableActionCodes.containsAll(actionCodes)) {
            throw new BizException("IAM_MENU_ACTION_INVALID", "所选页面动作不属于当前页面");
        }
    }

    private MenuPermissionMapper requiredMapper() {
        MenuPermissionMapper mapper = menuPermissionMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "菜单权限需要数据库连接");
        }
        return mapper;
    }

    private MenuMapper requiredMenuMapper() {
        MenuMapper mapper = menuMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "菜单资源需要数据库连接");
        }
        return mapper;
    }

    private RoleMenuMapper requiredRoleMenuMapper() {
        RoleMenuMapper mapper = roleMenuMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "角色菜单权限需要数据库连接");
        }
        return mapper;
    }

    private Long requiredTenantId() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.tenantId() == null) {
            throw new BizException("AUTH_UNAUTHORIZED", "Authentication is required");
        }
        return principal.tenantId();
    }
}
