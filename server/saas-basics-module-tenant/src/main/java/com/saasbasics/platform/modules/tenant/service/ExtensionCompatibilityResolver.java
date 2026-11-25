package com.saasbasics.platform.modules.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantApplication;
import com.saasbasics.platform.modules.tenant.entity.TenantAppEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantAppMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves extension dependency DAGs, detects cycles, checks SemVer host compatibility,
 * and prevents disabling extensions that active tenant subscriptions still depend on.
 */
@Component
public class ExtensionCompatibilityResolver {

    private String hostVersion;

    public ExtensionCompatibilityResolver(
            @Value("${saas-basics.platform.version:0.1.0}") String hostVersion
    ) {
        this.hostVersion = hostVersion;
    }

    public void setHostVersion(String hostVersion) {
        this.hostVersion = hostVersion;
    }

    public void verifyCompatibleAndDependencies(
            long tenantId,
            TenantApplication target,
            Map<String, TenantApplication> catalogByCode,
            TenantAppMapper tenantAppMapper
    ) {
        verifyHostCompatibility(target);
        Map<String, Integer> deps = target.dependencies();
        if (deps == null || deps.isEmpty()) {
            return;
        }
        detectDependencyCycles(target.code(), catalogByCode);
        for (Map.Entry<String, Integer> entry : deps.entrySet()) {
            String depCode = entry.getKey();
            int minRev = entry.getValue() == null ? 1 : entry.getValue();
            TenantApplication depApp = catalogByCode.get(depCode);
            if (depApp == null) {
                throw new BizException("EXTENSION_DEPENDENCY_MISSING",
                        "Extension '" + target.code() + "' requires unavailable extension '" + depCode + "'");
            }
            if (depApp.latestRevision() < minRev) {
                throw new BizException("EXTENSION_DEPENDENCY_VERSION_MISMATCH",
                        "Extension '" + depCode + "' max revision " + depApp.latestRevision() + " < required " + minRev);
            }
            if (tenantAppMapper != null) {
                TenantAppEntity installed = tenantAppMapper.selectOne(new LambdaQueryWrapper<TenantAppEntity>()
                        .eq(TenantAppEntity::getTenantId, tenantId)
                        .eq(TenantAppEntity::getAppCode, depCode)
                        .eq(TenantAppEntity::getStatus, "ENABLED")
                        .eq(TenantAppEntity::getDeleted, 0)
                        .last("LIMIT 1"));
                int rev = installed != null && installed.getInstalledRevision() != null
                        ? installed.getInstalledRevision() : 0;
                if (rev < minRev) {
                    throw new BizException("EXTENSION_DEPENDENCY_NOT_INSTALLED",
                            "Please install and enable '" + depCode + "' (revision >= " + minRev + ") before '" + target.code() + "'");
                }
            }
        }
    }

    public void assertNoActiveDependents(
            long tenantId,
            String targetCode,
            Collection<TenantApplication> catalog,
            TenantAppMapper tenantAppMapper
    ) {
        if (tenantAppMapper == null || catalog == null) {
            return;
        }
        List<TenantAppEntity> enabledApps = tenantAppMapper.selectList(new LambdaQueryWrapper<TenantAppEntity>()
                .eq(TenantAppEntity::getTenantId, tenantId)
                .eq(TenantAppEntity::getStatus, "ENABLED")
                .eq(TenantAppEntity::getDeleted, 0));
        Set<String> enabledCodes = new HashSet<>();
        for (TenantAppEntity row : enabledApps) {
            if (row.getAppCode() != null) {
                enabledCodes.add(row.getAppCode());
            }
        }
        for (TenantApplication app : catalog) {
            if (app.code().equals(targetCode) || !enabledCodes.contains(app.code())) {
                continue;
            }
            Map<String, Integer> deps = app.dependencies();
            if (deps != null && deps.containsKey(targetCode)) {
                throw new BizException("EXTENSION_HAS_ACTIVE_DEPENDENTS",
                        "Cannot disable '" + targetCode + "' while active extension '" + app.code() + "' depends on it");
            }
        }
    }

    public List<String> resolveInstallOrder(Collection<TenantApplication> applications) {
        Map<String, TenantApplication> byCode = new HashMap<>();
        for (TenantApplication app : applications) {
            byCode.put(app.code(), app);
        }
        List<String> order = new ArrayList<>();
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        for (TenantApplication app : applications) {
            dfsOrder(app.code(), byCode, visiting, visited, order);
        }
        return order;
    }

    public void verifyHostCompatibility(TenantApplication app) {
        int[] host = parseSemVer(hostVersion);
        if (app.minHostVersion() != null && !app.minHostVersion().isBlank()) {
            if (compareSemVer(host, parseSemVer(app.minHostVersion())) < 0) {
                throw new BizException("EXTENSION_HOST_INCOMPATIBLE",
                        "Extension '" + app.code() + "' requires host >= " + app.minHostVersion() + " (current " + hostVersion + ")");
            }
        }
        if (app.maxHostVersion() != null && !app.maxHostVersion().isBlank()) {
            if (compareSemVer(host, parseSemVer(app.maxHostVersion())) > 0) {
                throw new BizException("EXTENSION_HOST_INCOMPATIBLE",
                        "Extension '" + app.code() + "' supports host <= " + app.maxHostVersion() + " (current " + hostVersion + ")");
            }
        }
    }

    private void detectDependencyCycles(String rootCode, Map<String, TenantApplication> byCode) {
        dfsOrder(rootCode, byCode, new LinkedHashSet<>(), new HashSet<>(), new ArrayList<>());
    }

    private void dfsOrder(
            String code,
            Map<String, TenantApplication> byCode,
            Set<String> visiting,
            Set<String> visited,
            List<String> order
    ) {
        if (visited.contains(code)) {
            return;
        }
        if (!visiting.add(code)) {
            throw new BizException("EXTENSION_DEPENDENCY_CYCLE",
                    "Circular extension dependency detected involving '" + code + "'");
        }
        TenantApplication app = byCode.get(code);
        if (app != null && app.dependencies() != null) {
            for (String dep : app.dependencies().keySet()) {
                dfsOrder(dep, byCode, visiting, visited, order);
            }
        }
        visiting.remove(code);
        visited.add(code);
        order.add(code);
    }

    static int[] parseSemVer(String raw) {
        if (raw == null || raw.isBlank()) {
            return new int[]{0, 0, 0};
        }
        String clean = raw.trim();
        int dash = clean.indexOf('-');
        if (dash >= 0) {
            clean = clean.substring(0, dash);
        }
        String[] parts = clean.split("\\.");
        int major = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
        int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        return new int[]{major, minor, patch};
    }

    static int compareSemVer(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }
}
