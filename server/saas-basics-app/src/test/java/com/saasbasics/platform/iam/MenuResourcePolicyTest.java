package com.saasbasics.platform.iam;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.iam.dto.MenuPermissionSaveRequest;
import com.saasbasics.platform.modules.iam.dto.MenuResponse;
import com.saasbasics.platform.modules.iam.dto.MenuSaveRequest;
import com.saasbasics.platform.modules.iam.entity.MenuEntity;
import com.saasbasics.platform.modules.iam.entity.MenuPermissionEntity;
import com.saasbasics.platform.modules.iam.mapper.MenuMapper;
import com.saasbasics.platform.modules.iam.mapper.MenuPermissionMapper;
import com.saasbasics.platform.modules.iam.service.AccessSubjectProfileService;
import com.saasbasics.platform.modules.iam.service.MenuPermissionService;
import com.saasbasics.platform.modules.iam.service.MenuService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MenuResourcePolicyTest {

    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void requiresPageActionsToBelongToAPage() {
        MenuMapper mapper = mock(MenuMapper.class);
        MenuEntity directory = menuEntity(10L, 1L, "DIRECTORY");
        when(mapper.selectById(10L)).thenReturn(directory);
        MenuService service = new MenuService(provider(mapper));
        setPrincipal();

        MenuSaveRequest request = menuRequest(10L, "BUTTON");

        assertThatThrownBy(() -> service.createMenu(request))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getCode())
                .isEqualTo("IAM_MENU_PARENT_TYPE_INVALID");
        verify(mapper, never()).insert(any(MenuEntity.class));
    }

    @Test
    void preventsPagesFromContainingNavigationChildren() {
        MenuMapper mapper = mock(MenuMapper.class);
        MenuEntity directory = menuEntity(20L, 1L, "DIRECTORY");
        MenuEntity childPage = menuEntity(22L, 1L, "MENU");
        when(mapper.selectById(20L)).thenReturn(directory);
        when(mapper.selectList(any())).thenReturn(List.of(childPage));
        MenuService service = new MenuService(provider(mapper));
        setPrincipal();

        assertThatThrownBy(() -> service.updateMenu(20L, menuRequest(0L, "MENU")))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getCode())
                .isEqualTo("IAM_MENU_CHILD_INVALID");
        verify(mapper, never()).updateById(any(MenuEntity.class));
    }

    @Test
    void rejectsDirectDirectoryGrants() {
        MenuMapper menuMapper = mock(MenuMapper.class);
        MenuPermissionMapper permissionMapper = mock(MenuPermissionMapper.class);
        when(menuMapper.selectMenuById(30L)).thenReturn(menuResponse(30L, 0L, "DIRECTORY", "settings"));
        MenuPermissionService service = permissionService(menuMapper, permissionMapper);
        setPrincipal();

        MenuPermissionSaveRequest request = new MenuPermissionSaveRequest(
                1L, 30L, "settings", "ROLE", "1", "[]", "ENABLED", null
        );

        assertThatThrownBy(() -> service.createPermission(request))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getCode())
                .isEqualTo("IAM_MENU_GRANT_INVALID");
        verify(permissionMapper, never()).insert(any(MenuPermissionEntity.class));
    }

    @Test
    void rejectsActionsRegisteredToAnotherPage() {
        MenuMapper menuMapper = mock(MenuMapper.class);
        MenuPermissionMapper permissionMapper = mock(MenuPermissionMapper.class);
        MenuResponse page = menuResponse(40L, 10L, "MENU", "users");
        MenuResponse otherPageAction = menuResponse(42L, 41L, "BUTTON", "roles:create");
        when(menuMapper.selectMenuById(40L)).thenReturn(page);
        when(menuMapper.selectMenuList()).thenReturn(List.of(page, otherPageAction));
        MenuPermissionService service = permissionService(menuMapper, permissionMapper);
        setPrincipal();

        MenuPermissionSaveRequest request = new MenuPermissionSaveRequest(
                1L, 40L, "users", "ROLE", "1", "[\"roles:create\"]", "ENABLED", null
        );

        assertThatThrownBy(() -> service.createPermission(request))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getCode())
                .isEqualTo("IAM_MENU_ACTION_INVALID");
        verify(permissionMapper, never()).insert(any(MenuPermissionEntity.class));
    }

    @Test
    void rejectsActionsRegisteredInAnotherTenant() {
        MenuMapper menuMapper = mock(MenuMapper.class);
        MenuPermissionMapper permissionMapper = mock(MenuPermissionMapper.class);
        MenuResponse page = menuResponse(50L, 10L, "MENU", "users");
        MenuResponse foreignAction = new MenuResponse(
                52L, 2L, 50L, "BUTTON", "users:create", "Create user", null, null,
                null, null, false, false, 10, "ENABLED", null, null
        );
        when(menuMapper.selectMenuById(50L)).thenReturn(page);
        when(menuMapper.selectMenuList()).thenReturn(List.of(page, foreignAction));
        MenuPermissionService service = permissionService(menuMapper, permissionMapper);
        setPrincipal();

        MenuPermissionSaveRequest request = new MenuPermissionSaveRequest(
                1L, 50L, "users", "ROLE", "1", "[\"users:create\"]", "ENABLED", null
        );

        assertThatThrownBy(() -> service.createPermission(request))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getCode())
                .isEqualTo("IAM_MENU_ACTION_INVALID");
        verify(permissionMapper, never()).insert(any(MenuPermissionEntity.class));
    }

    @Test
    void rejectsMenuUpdatesFromAnotherTenant() {
        MenuMapper mapper = mock(MenuMapper.class);
        MenuEntity foreignMenu = menuEntity(60L, 2L, "MENU");
        when(mapper.selectById(60L)).thenReturn(foreignMenu);
        MenuService service = new MenuService(provider(mapper));
        setPrincipal();

        assertThatThrownBy(() -> service.updateMenu(60L, menuRequest(0L, "MENU")))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getCode())
                .isEqualTo("IAM_MENU_NOT_FOUND");
        verify(mapper, never()).updateById(any(MenuEntity.class));
    }

    private MenuPermissionService permissionService(MenuMapper menuMapper, MenuPermissionMapper permissionMapper) {
        return new MenuPermissionService(
                provider(permissionMapper),
                provider(menuMapper),
                mock(AccessSubjectProfileService.class),
                mock(AuditTrailService.class),
                new ObjectMapper()
        );
    }

    private MenuSaveRequest menuRequest(Long parentId, String menuType) {
        return new MenuSaveRequest(
                1L, parentId, menuType, "resource_code", "Resource", null, null,
                null, null, true, false, 10, "ENABLED", null, null
        );
    }

    private MenuEntity menuEntity(Long id, Long tenantId, String menuType) {
        MenuEntity entity = new MenuEntity();
        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setParentId(0L);
        entity.setMenuType(menuType);
        entity.setDeleted(0);
        return entity;
    }

    private MenuResponse menuResponse(Long id, Long parentId, String menuType, String menuCode) {
        return new MenuResponse(
                id, 1L, parentId, menuType, menuCode, menuCode, null, null,
                null, null, true, false, 10, "ENABLED", null, null
        );
    }

    private void setPrincipal() {
        AuthContext.set(new AuthPrincipal(
                1L, 1L, "platform", 1L, "admin", "Platform Administrator", "PLATFORM",
                "ACTIVE", LocalDateTime.now().plusHours(1), List.of(), List.of(), List.of(1L), List.of()
        ));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
