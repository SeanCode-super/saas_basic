package com.saasbasics.platform.modules.organization.api.model;

import com.saasbasics.platform.modules.organization.domain.model.LifecycleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class PositionModels {

    private PositionModels() {
    }

    public record CreateRequest(
            @NotNull UUID organizationPublicId,
            UUID orgUnitPublicId,
            @NotBlank @Size(max = 128) String positionCode,
            @NotBlank @Size(max = 256) String displayName,
            @NotNull Instant validFrom,
            Instant validTo,
            @Size(max = 500) String remark
    ) {
    }

    public record UpdateRequest(
            UUID orgUnitPublicId,
            @NotBlank @Size(max = 128) String positionCode,
            @NotBlank @Size(max = 256) String displayName,
            @NotNull Instant validFrom,
            Instant validTo,
            @Size(max = 500) String remark,
            @NotNull @PositiveOrZero Integer expectedVersion
    ) {
    }

    public record Response(
            UUID publicId,
            UUID organizationPublicId,
            UUID orgUnitPublicId,
            String positionCode,
            String displayName,
            LifecycleStatus status,
            Instant validFrom,
            Instant validTo,
            String remark,
            int version
    ) {
    }
}
