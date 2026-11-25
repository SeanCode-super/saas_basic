package com.saasbasics.platform.modules.tenant.api;

import java.util.Optional;

public interface PortalTenantDirectory {
    record PortalTenant(Long id, String code, String name, String status) {}

    Optional<PortalTenant> findByCode(String code);

    Optional<PortalTenant> findById(Long id);
}
