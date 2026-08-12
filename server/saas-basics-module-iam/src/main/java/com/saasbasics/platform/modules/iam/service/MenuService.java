package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.dto.MenuResponse;
import com.saasbasics.platform.modules.iam.dto.MenuSaveRequest;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.MenuEntity;
import com.saasbasics.platform.modules.iam.mapper.MenuMapper;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class MenuService {

    private final ObjectProvider<MenuMapper> menuMapperProvider;

    public MenuService(ObjectProvider<MenuMapper> menuMapperProvider) {
        this.menuMapperProvider = menuMapperProvider;
    }

    public List<MenuResponse> listMenus() {
        MenuMapper mapper = menuMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        Long tenantId = requiredTenantId();
        return mapper.selectMenuList().stream()
                .filter(menu -> tenantId.equals(menu.tenantId()))
                .toList();
    }

    public MenuResponse getMenu(Long id) {
        MenuResponse response = requiredMapper().selectMenuById(id);
        if (response == null || !requiredTenantId().equals(response.tenantId())) {
            throw new BizException("IAM_MENU_NOT_FOUND", "IAM menu not found");
        }
        return response;
    }

    public MenuResponse createMenu(MenuSaveRequest request) {
        MenuEntity entity = new MenuEntity();
        apply(entity, request);
        requiredMapper().insert(entity);
        return getMenu(entity.getId());
    }

    public MenuResponse updateMenu(Long id, MenuSaveRequest request) {
        MenuMapper mapper = requiredMapper();
        MenuEntity entity = mapper.selectById(id);
        Long tenantId = requiredTenantId();
        if (entity == null
                || entity.getDeleted() != null && entity.getDeleted() == 1
                || !tenantId.equals(entity.getTenantId())) {
            throw new BizException("IAM_MENU_NOT_FOUND", "IAM menu not found");
        }
        apply(entity, request);
        mapper.updateById(entity);
        return getMenu(id);
    }

    public MenuResponse updateStatus(Long id, StatusUpdateRequest request) {
        MenuMapper mapper = requiredMapper();
        MenuEntity entity = mapper.selectById(id);
        Long tenantId = requiredTenantId();
        if (entity == null
                || entity.getDeleted() != null && entity.getDeleted() == 1
                || !tenantId.equals(entity.getTenantId())) {
            throw new BizException("IAM_MENU_NOT_FOUND", "IAM menu not found");
        }
        entity.setStatus(request.status());
        mapper.updateById(entity);
        return getMenu(id);
    }

    private void apply(MenuEntity entity, MenuSaveRequest request) {
        Long tenantId = requiredTenantId();
        String menuType = request.menuType().trim().toUpperCase();
        if (!List.of("DIRECTORY", "MENU", "BUTTON", "LINK").contains(menuType)) {
            throw new BizException("IAM_MENU_TYPE_INVALID", "Unsupported menu resource type");
        }
        Long parentId = request.parentId() == null ? 0L : request.parentId();
        validateHierarchy(entity, tenantId, parentId, menuType);
        entity.setTenantId(tenantId);
        entity.setParentId(parentId);
        entity.setMenuType(menuType);
        entity.setMenuCode(request.menuCode());
        entity.setMenuName(request.menuName());
        entity.setRoutePath(Set.of("MENU", "LINK").contains(menuType) ? request.routePath() : null);
        entity.setComponentPath("MENU".equals(menuType) ? request.componentPath() : null);
        entity.setPermissionCode(Set.of("MENU", "LINK").contains(menuType) ? request.permissionCode() : null);
        entity.setIcon(request.icon());
        entity.setVisible("BUTTON".equals(menuType) ? false : request.visible());
        entity.setKeepAlive("MENU".equals(menuType) && Boolean.TRUE.equals(request.keepAlive()));
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setStatus(request.status());
        entity.setMetaJson(request.metaJson());
        entity.setRemark(request.remark());
    }

    private void validateHierarchy(MenuEntity entity, Long tenantId, Long parentId, String menuType) {
        MenuMapper mapper = requiredMapper();
        if (entity.getId() != null && entity.getId().equals(parentId)) {
            throw new BizException("IAM_MENU_PARENT_INVALID", "资源不能选择自身作为上级");
        }

        if (parentId <= 0) {
            if ("BUTTON".equals(menuType)) {
                throw new BizException("IAM_MENU_PARENT_REQUIRED", "页面操作必须归属具体页面");
            }
        } else {
            MenuEntity parent = mapper.selectById(parentId);
            if (parent == null
                    || (parent.getDeleted() != null && parent.getDeleted() == 1)
                    || !tenantId.equals(parent.getTenantId())) {
                throw new BizException("IAM_MENU_PARENT_NOT_FOUND", "上级资源不存在");
            }
            String requiredParentType = "BUTTON".equals(menuType) ? "MENU" : "DIRECTORY";
            if (!requiredParentType.equalsIgnoreCase(parent.getMenuType())) {
                throw new BizException(
                        "IAM_MENU_PARENT_TYPE_INVALID",
                        "BUTTON".equals(menuType) ? "页面操作只能挂在页面下" : "目录、页面和链接只能挂在目录下"
                );
            }
            ensureParentIsNotDescendant(mapper, entity.getId(), parent);
        }

        if (entity.getId() == null) {
            return;
        }
        List<MenuEntity> children = mapper.selectList(new LambdaQueryWrapper<MenuEntity>()
                .eq(MenuEntity::getParentId, entity.getId())
                .eq(MenuEntity::getDeleted, 0));
        boolean hasActions = children.stream().anyMatch(child -> "BUTTON".equalsIgnoreCase(child.getMenuType()));
        boolean hasNavigationChildren = children.stream().anyMatch(child -> !"BUTTON".equalsIgnoreCase(child.getMenuType()));
        if (("BUTTON".equals(menuType) || "LINK".equals(menuType)) && !children.isEmpty()) {
            throw new BizException("IAM_MENU_CHILD_INVALID", "操作和外部链接不能包含子资源");
        }
        if ("DIRECTORY".equals(menuType) && hasActions) {
            throw new BizException("IAM_MENU_CHILD_INVALID", "目录不能直接包含页面操作");
        }
        if ("MENU".equals(menuType) && hasNavigationChildren) {
            throw new BizException("IAM_MENU_CHILD_INVALID", "页面只能包含页面操作");
        }
    }

    private void ensureParentIsNotDescendant(MenuMapper mapper, Long resourceId, MenuEntity parent) {
        if (resourceId == null) {
            return;
        }
        MenuEntity current = parent;
        while (current != null) {
            if (resourceId.equals(current.getId())) {
                throw new BizException("IAM_MENU_CYCLE", "上级资源不能选择当前资源的下级");
            }
            if (current.getParentId() == null || current.getParentId() <= 0) {
                break;
            }
            current = mapper.selectById(current.getParentId());
        }
    }

    private MenuMapper requiredMapper() {
        MenuMapper mapper = menuMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Menu write operations require the db profile and MySQL connection");
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
