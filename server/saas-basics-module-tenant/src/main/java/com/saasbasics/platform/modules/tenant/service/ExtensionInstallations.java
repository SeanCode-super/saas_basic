package com.saasbasics.platform.modules.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.common.tenant.TenantAppAccess;
import com.saasbasics.platform.common.tenant.TenantApplication;
import com.saasbasics.platform.modules.tenant.entity.TenantAppEntity;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantAppMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExtensionInstallations implements TenantAppAccess {
    private final Map<String, TenantApplication> applications = new LinkedHashMap<>();
    private final IndustryRegistry registry;
    private final TenantAppMapper tenantAppMapper;
    private final TenantMapper tenantMapper;

    public ExtensionInstallations(List<TenantApplication> modules, IndustryRegistry registry,
                                  TenantAppMapper tenantAppMapper, TenantMapper tenantMapper) {
        this.registry = registry;
        this.tenantAppMapper = tenantAppMapper;
        this.tenantMapper = tenantMapper;
        for (TenantApplication app : modules) {
            if (!app.code().matches("[a-z][a-z0-9_]{0,39}") || app.latestRevision() < 1
                    || app.name().isBlank() || app.name().length() > 128
                    || TenantAppService.SYSTEM_APPS.stream().anyMatch(b -> b.appCode().equals(app.code()))
                    || applications.putIfAbsent(app.code(), app) != null) {
                throw new IllegalArgumentException("Invalid or duplicate tenant application: " + app.code());
            }
        }
    }

    Collection<TenantApplication> catalog() {
        var result = new java.util.ArrayList<TenantApplication>(applications.values());
        result.addAll(registry.catalog());
        return result;
    }

    TenantApplication find(String code) {
        var local = applications.get(code);
        if (local != null || TenantAppService.SYSTEM_APPS.stream().anyMatch(b -> b.appCode().equals(code))) return local;
        return registry.latest(code);
    }

    List<IndustryWorkspace.Entry> workspaceEntries(AuthPrincipal user) {
        if (applications.isEmpty()) return List.of();
        var enabled = tenantAppMapper.selectList(new LambdaQueryWrapper<TenantAppEntity>()
                .select(TenantAppEntity::getAppCode, TenantAppEntity::getInstalledRevision)
                .eq(TenantAppEntity::getTenantId, user.tenantId())
                .in(TenantAppEntity::getAppCode, applications.keySet()).eq(TenantAppEntity::getStatus, "ENABLED")
                .gt(TenantAppEntity::getInstalledRevision, 0)
                .and(w -> w.isNull(TenantAppEntity::getExpiredAt).or().gt(TenantAppEntity::getExpiredAt, LocalDateTime.now())));
        var entries = new java.util.ArrayList<IndustryWorkspace.Entry>();
        for (var row : enabled) {
            var app = applications.get(row.getAppCode());
            int revision = row.getInstalledRevision();
            if (revision > app.latestRevision() || !IndustryWorkspace.permitted(user, app.code(), "query")) continue;
            var fields = app.workspaceFields(revision).stream().map(f ->
                    new IndustryManifest.Field(f.key(), f.label(), f.type(), f.required())).toList();
            if (fields.isEmpty()) continue;
            var actions = app.endpoints().stream().filter(e -> java.util.Set.of("create", "update", "delete").contains(e.action()))
                    .filter(e -> IndustryWorkspace.permitted(user, app.code(), e.action()))
                    .map(e -> new IndustryManifest.Action(e.action(), e.name(), "delete".equals(e.action()) ? List.of() : fields,
                            "create".equals(e.action()) ? null : "id")).toList();
            entries.add(new IndustryWorkspace.Entry(app.code(), app.name(), "MODULE", revision, null, fields, actions));
        }
        return entries;
    }

    boolean supportsPc(IndustryManifest latest, int installedRevision) {
        return (latest.revision() == installedRevision ? latest : registry.revision(latest.code(), installedRevision)).pc();
    }

    @Override
    public boolean isInstalled(long tenantId, String code, int minimumRevision) {
        TenantApplication module = find(code);
        if (module == null || minimumRevision < 1) return false;
        return tenantAppMapper.exists(new LambdaQueryWrapper<TenantAppEntity>()
                .eq(TenantAppEntity::getTenantId, tenantId)
                .eq(TenantAppEntity::getAppCode, code)
                .eq(TenantAppEntity::getStatus, "ENABLED")
                .between(TenantAppEntity::getInstalledRevision, minimumRevision, module.latestRevision())
                .and(w -> w.isNull(TenantAppEntity::getExpiredAt).or().gt(TenantAppEntity::getExpiredAt, LocalDateTime.now())));
    }

    // The tenant row also serializes first installations before a subscription row exists.
    String lockTenant(long tenantId) {
        TenantEntity tenant = tenantMapper.selectOne(new LambdaQueryWrapper<TenantEntity>()
                .select(TenantEntity::getTenantCode)
                .eq(TenantEntity::getId, tenantId)
                .last("FOR UPDATE"));
        if (tenant == null) throw new BizException("TENANT_NOT_FOUND", "目标租户不存在");
        return tenant.getTenantCode();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void migrate(long tenantId, TenantApplication app, Integer requested, boolean requireInstallation) {
        var principal = installationActor(tenantId);
        String tenantCode = lockTenant(tenantId);
        TenantAppEntity existing = tenantAppMapper.selectOne(new LambdaQueryWrapper<TenantAppEntity>()
                .eq(TenantAppEntity::getTenantId, tenantId)
                .eq(TenantAppEntity::getAppCode, app.code())
                .last("FOR UPDATE"));
        int previous = existing == null || existing.getInstalledRevision() == null ? 0 : existing.getInstalledRevision();
        int target = requested != null ? requested : (previous == 0 ? app.latestRevision() : previous);
        if (requireInstallation && previous == 0) {
            throw new BizException("APP_NOT_INSTALLED", "请先安装应用");
        }
        if (target < 1 || target > app.latestRevision() || target < previous) {
            throw new BizException("APP_REVISION_INVALID", "目标版本无效或低于已安装版本");
        }
        if (target == previous) return;

        try (var ignored = TenantAccessContextHolder.openTenant(tenantId, tenantCode, principal.userId())) {
            for (int revision = previous + 1; revision <= target; revision++) {
                app.migrate(tenantId, revision);
            }
        }
        if (existing == null) {
            TenantAppEntity entity = new TenantAppEntity();
            entity.setTenantId(tenantId);
            entity.setAppCode(app.code());
            entity.setAppName(app.name());
            entity.setInstalledRevision(target);
            entity.setStatus("DISABLED");
            tenantAppMapper.insert(entity);
        } else {
            existing.setInstalledRevision(target);
            existing.setAppName(app.name());
            tenantAppMapper.updateById(existing);
        }
    }

    void syncMenu(long tenantId, TenantApplication app, boolean enabled) {
        var principal = installationActor(tenantId);
        // IAM menus and resources use the target tenant's routed data source and tenant interceptor.
        try (var ignored = TenantAccessContextHolder.openTenant(tenantId, lockTenant(tenantId), principal.userId())) {
            if (app instanceof IndustryManifest) {
                tenantAppMapper.disableExtensionResources(tenantId, "biz_" + app.code() + ":");
                if (enabled) {
                    TenantAppEntity row = tenantAppMapper.selectOne(new LambdaQueryWrapper<TenantAppEntity>()
                            .select(TenantAppEntity::getInstalledRevision)
                            .eq(TenantAppEntity::getTenantId, tenantId)
                            .eq(TenantAppEntity::getAppCode, app.code()));
                    if (row != null && row.getInstalledRevision() != null) app = registry.revision(app.code(), row.getInstalledRevision());
                }
            }
            for (var endpoint : app.endpoints()) {
                String permission = "biz_" + app.code() + ":" + endpoint.action();
                if (enabled) tenantAppMapper.enableEndpoint(tenantId, permission, endpoint);
                else tenantAppMapper.disableEndpoint(tenantId, permission);
            }
            String menuCode = "ext_" + app.code();
            if (!enabled || (app instanceof IndustryManifest manifest && !manifest.pc())) {
                tenantAppMapper.disableMenu(tenantId, menuCode);
            } else {
                tenantAppMapper.enableMenu(tenantId, app.code(), menuCode, app.name(), app.componentPath());
            }
        }
    }

    private static AuthPrincipal installationActor(long tenantId) {
        var principal = AuthContext.get();
        if (principal == null || (!Long.valueOf(tenantId).equals(principal.tenantId())
                && !(principal.isPlatformTenant() && principal.isSuperAdmin() && "PLATFORM".equalsIgnoreCase(principal.userType())))) {
            throw new BizException("TENANT_ACCESS_DENIED", "无权管理该租户的应用");
        }
        return principal;
    }
}
