package com.saasbasics.platform.modules.tenant.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record TenantCreateResponse(@JsonUnwrapped TenantResponse tenant, InitialAdmin initialAdmin) {
    public Long id() {
        return tenant.id();
    }

    public record InitialAdmin(String username, String temporaryPassword) {
        @Override
        public String toString() {
            return "InitialAdmin[username=" + username + "]";
        }
    }
}
