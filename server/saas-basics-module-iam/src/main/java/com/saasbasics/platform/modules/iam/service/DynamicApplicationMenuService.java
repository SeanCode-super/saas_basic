package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.saasbasics.platform.common.tenant.DynamicApplicationMenus;
import com.saasbasics.platform.modules.iam.entity.MenuEntity;
import com.saasbasics.platform.modules.iam.mapper.DynamicMenuGrantMapper;
import com.saasbasics.platform.modules.iam.mapper.MenuMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DynamicApplicationMenuService implements DynamicApplicationMenus {
    private final MenuMapper menus;
    private final DynamicMenuGrantMapper grants;

    private final MenuPermissionService permissions;

    public DynamicApplicationMenuService(MenuMapper menus, DynamicMenuGrantMapper grants, MenuPermissionService permissions) {
        this.permissions = permissions;
        this.menus = menus;
        this.grants = grants;
    }

    @Override
    public boolean canOpen(long menuId) {
        return permissions.listCurrentMenus(com.saasbasics.platform.common.auth.AuthContext.requirePrincipal()).stream()
                .anyMatch(menu -> menu.id() == menuId && Boolean.TRUE.equals(menu.visible()));
    }

    @Override
    @Transactional
    public long register(long tenantId, String modelCode, String name, String icon, long actorId) {
        String menuCode = "dyn_" + modelCode;
        var existing = menus.selectOne(new LambdaQueryWrapper<MenuEntity>()
                .eq(MenuEntity::getTenantId, tenantId).eq(MenuEntity::getMenuCode, menuCode));
        grants.registerActions(tenantId, modelCode, name, actorId);
        if (existing != null) return existing.getId();

        var menu = new MenuEntity();
        menu.setTenantId(tenantId);
        menu.setParentId(0L);
        menu.setMenuType("MENU");
        menu.setMenuCode(menuCode);
        menu.setAppCode("codegen");
        menu.setMenuName(name);
        menu.setRoutePath("/dynamic/" + modelCode);
        menu.setComponentPath("@/views/dynamic/DynamicDataView.vue");
        menu.setPermissionCode("dynamic:" + modelCode + ":query");
        menu.setIcon(icon == null || icon.isBlank() ? "Document" : icon);
        menu.setSortNo(90);
        menu.setStatus("ENABLED");
        menu.setVisible(true);
        menu.setKeepAlive(true);
        menu.setCreatedBy(actorId);
        menu.setUpdatedBy(actorId);
        menus.insert(menu);
        grants.grantToTenantAdministrators(tenantId, menu.getId(), actorId);
        return menu.getId();
    }

    @Override
    @Transactional
    public void retire(long tenantId, long menuId, long actorId) {
        menus.update(new LambdaUpdateWrapper<MenuEntity>()
                .eq(MenuEntity::getTenantId, tenantId).eq(MenuEntity::getId, menuId)
                .eq(MenuEntity::getAppCode, "codegen")
                .set(MenuEntity::getDeleted, 1).set(MenuEntity::getDeletedAt, LocalDateTime.now())
                .set(MenuEntity::getUpdatedBy, actorId));
    }
}
