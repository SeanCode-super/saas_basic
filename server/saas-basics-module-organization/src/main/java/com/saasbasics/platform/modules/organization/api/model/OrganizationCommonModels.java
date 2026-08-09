package com.saasbasics.platform.modules.organization.api.model;

import com.saasbasics.platform.modules.organization.domain.model.LifecycleStatus;
import com.saasbasics.platform.modules.organization.domain.model.TypeDescriptor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public final class OrganizationCommonModels {

    private OrganizationCommonModels() {
    }

    public record TypeDescriptorInput(
            @NotBlank String namespace,
            @NotBlank String code,
            @NotBlank String version
    ) {
        public TypeDescriptor toDomain() {
            return new TypeDescriptor(namespace, code, version);
        }
    }

    public record LifecycleTransitionRequest(
            @NotNull LifecycleStatus targetStatus,
            @NotNull @PositiveOrZero Integer expectedVersion
    ) {
    }

    public record TerminateRequest(
            @NotNull Instant effectiveAt,
            @NotNull @PositiveOrZero Integer expectedVersion
    ) {
    }
}
