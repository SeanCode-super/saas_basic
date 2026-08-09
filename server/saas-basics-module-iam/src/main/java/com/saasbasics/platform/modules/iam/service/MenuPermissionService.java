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

    private void apply(MenuPermissionEntity entity, MenuPermissionSaveRequest request) {
        Long tenantId = requiredTenantId();
        entity.setTenantId(tenantId);
        entity.setMenuId(request.menuId());
        entity.setMenuCode(resolveMenuCode(tenantId, request.menuId()));
        entity.setSubjectType(request.subjectType());
        entity.setSubjectValue(request.subjectValue());
        entity.setButtonCodesJson(request.buttonCodesJson());
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

    private String resolveMenuCode(Long tenantId, Long menuId) {
        MenuResponse menu = requiredMenuMapper().selectMenuById(menuId);
        if (menu == null || !tenantId.equals(menu.tenantId())) {
            throw new BizException("IAM_MENU_NOT_FOUND", "菜单不存在");
        }
        return menu.menuCode();
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
