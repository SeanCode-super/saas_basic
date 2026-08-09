package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.dto.MenuResponse;
import com.saasbasics.platform.modules.iam.dto.MenuSaveRequest;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.MenuEntity;
import com.saasbasics.platform.modules.iam.mapper.MenuMapper;
import java.util.List;
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
        return mapper.selectMenuList();
    }

    public MenuResponse getMenu(Long id) {
        MenuResponse response = requiredMapper().selectMenuById(id);
        if (response == null) {
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
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("IAM_MENU_NOT_FOUND", "IAM menu not found");
        }
        apply(entity, request);
        mapper.updateById(entity);
        return getMenu(id);
    }

    public MenuResponse updateStatus(Long id, StatusUpdateRequest request) {
        MenuMapper mapper = requiredMapper();
        MenuEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("IAM_MENU_NOT_FOUND", "IAM menu not found");
        }
        entity.setStatus(request.status());
        mapper.updateById(entity);
        return getMenu(id);
    }

    private void apply(MenuEntity entity, MenuSaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setParentId(request.parentId() == null ? 0L : request.parentId());
        entity.setMenuType(request.menuType());
        entity.setMenuCode(request.menuCode());
        entity.setMenuName(request.menuName());
        entity.setRoutePath(request.routePath());
        entity.setComponentPath(request.componentPath());
        entity.setPermissionCode(request.permissionCode());
        entity.setIcon(request.icon());
        entity.setVisible(request.visible());
        entity.setKeepAlive(request.keepAlive());
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setStatus(request.status());
        entity.setMetaJson(request.metaJson());
        entity.setRemark(request.remark());
    }

    private MenuMapper requiredMapper() {
        MenuMapper mapper = menuMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Menu write operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
