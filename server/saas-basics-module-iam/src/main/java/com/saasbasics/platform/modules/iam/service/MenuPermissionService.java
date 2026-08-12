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
import com.saasbasics.platform.modules.iam.mapper.MenuMapper;
import com.saasbasics.platform.modules.iam.mapper.MenuPermissionMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuPermissionService {

    private final ObjectProvider<MenuPermissionMapper> menuPermissionMapperProvider;
    private final ObjectProvider<MenuMapper> menuMapperProvider;
    private final AccessSubjectProfileService accessSubjectProfileService;
    private final AuditTrailService auditTrailService;
    private final ObjectMapper objectMapper;

    public MenuPermissionService(ObjectProvider<MenuPermissionMapper> menuPermissionMapperProvider,
                                 ObjectProvider<MenuMapper> menuMapperProvider,
                                 AccessSubjectProfileService accessSubjectProfileService,
                                 AuditTrailService auditTrailService,
                                 ObjectMapper objectMapper) {
        this.menuPermissionMapperProvider = menuPermissionMapperProvider;
        this.menuMapperProvider = menuMapperProvider;
        this.accessSubjectProfileService = accessSubjectProfileService;
        this.auditTrailService = auditTrailService;
        this.objectMapper = objectMapper;
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
        for (MenuGrantItemRequest grant : request.grants()) {
            if (!processedMenus.add(grant.menuId())) {
                throw new BizException("IAM_MENU_GRANT_DUPLICATE", "同一菜单不能重复授权");
            }
            MenuResponse menu = menus.get(grant.menuId());
            if (menu == null || !isGrantableMenu(menu)) {
                throw new BizException("IAM_MENU_GRANT_INVALID", "授权菜单不存在或不是可访问页面");
            }

            List<String> actionCodes = normalizeActionCodes(grant.actionCodes());
            validateActionCodes(menus.values(), tenantId, grant.menuId(), actionCodes);

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
        AccessSubjectProfile subjectProfile = accessSubjectProfileService.load(principal.tenantId(), principal.userId());
        List<MenuPermissionEntity> grants = requiredMapper().selectList(new LambdaQueryWrapper<MenuPermissionEntity>()
                .eq(MenuPermissionEntity::getTenantId, principal.tenantId())
                .eq(MenuPermissionEntity::getDeleted, 0)
                .eq(MenuPermissionEntity::getStatus, "ENABLED"));

        Set<Long> grantedMenuIds = grants.stream()
                .filter(grant -> subjectProfile.matches(grant.getSubjectType(), grant.getSubjectValue()))
                .map(MenuPermissionEntity::getMenuId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (grantedMenuIds.isEmpty()) {
            return List.of();
        }

        List<MenuResponse> allMenus = requiredMenuMapper().selectMenuList()
                .stream()
                .filter(menu -> principal.tenantId().equals(menu.tenantId()))
                .filter(menu -> "ENABLED".equalsIgnoreCase(menu.status()))
                .filter(menu -> menu.permissionCode() == null || principal.permissions().contains(menu.permissionCode()))
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

    public List<String> resolveButtonPermissions(AuthPrincipal principal) {
        AccessSubjectProfile subjectProfile = accessSubjectProfileService.load(principal.tenantId(), principal.userId());
        List<MenuPermissionEntity> grants = requiredMapper().selectList(new LambdaQueryWrapper<MenuPermissionEntity>()
                .eq(MenuPermissionEntity::getTenantId, principal.tenantId())
                .eq(MenuPermissionEntity::getDeleted, 0)
                .eq(MenuPermissionEntity::getStatus, "ENABLED"));

        LinkedHashSet<String> buttonPermissions = new LinkedHashSet<>();
        for (MenuPermissionEntity grant : grants) {
            if (!subjectProfile.matches(grant.getSubjectType(), grant.getSubjectValue())) {
                continue;
            }
            buttonPermissions.addAll(parseButtonCodes(grant.getMenuCode(), grant.getButtonCodesJson()));
        }
        return new ArrayList<>(buttonPermissions);
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

    private Long requiredTenantId() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.tenantId() == null) {
            throw new BizException("AUTH_UNAUTHORIZED", "Authentication is required");
        }
        return principal.tenantId();
    }
}
