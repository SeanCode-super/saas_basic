package com.saasbasics.platform.modules.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saasbasics.platform.common.api.PageResponse;
import com.saasbasics.platform.modules.tenant.controller.TenantController;
import com.saasbasics.platform.modules.tenant.dto.TenantCreateResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantSaveRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TenantCreateReceiptTest {

    @Test
    void sendsUncachedCredentialsOnlyWithTheCreationResponse() {
        var tenant = new TenantResponse(9L, "sample", "Sample", 1L, "Standard", "ACTIVE", "SHARED_SCHEMA",
                null, null, null, null, null, null);
        var request = new TenantSaveRequest("sample", "Sample", 1L, "ACTIVE", "SHARED_SCHEMA",
                null, null, null, null, null);
        String secret = UUID.randomUUID().toString();
        var admin = new TenantCreateResponse.InitialAdmin("admin", secret);
        var receipt = new TenantCreateResponse(tenant, admin);
        TenantService service = mock(TenantService.class);
        when(service.createTenant(request)).thenReturn(receipt);
        when(service.getTenant(9L)).thenReturn(tenant);
        when(service.pageTenants()).thenReturn(PageResponse.of(List.of(tenant)));
        var controller = new TenantController(service);
        var json = JsonMapper.builder().build();

        var creation = controller.create(request);
        var response = json.valueToTree(creation.getBody()).path("data");
        assertThat(creation.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.get("id").asLong()).isEqualTo(9L);
        assertThat(response.get("tenantCode").asString()).isEqualTo("sample");
        assertThat(response.has("tenant")).isFalse();
        assertThat(response.path("initialAdmin").path("username").asString()).isEqualTo("admin");
        assertThat(secret.equals(response.path("initialAdmin").path("temporaryPassword").asString())).isTrue();
        assertThat(receipt.toString().contains(secret)).isFalse();
        for (Object read : List.of(controller.detail(9L), controller.page())) {
            String data = json.writeValueAsString(read);
            assertThat(data.contains(secret)).isFalse();
            assertThat(data).doesNotContain("initialAdmin", "temporaryPassword");
        }
    }
}
