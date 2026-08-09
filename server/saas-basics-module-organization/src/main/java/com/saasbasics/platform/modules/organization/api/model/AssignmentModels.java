package com.saasbasics.platform.modules.organization.api.model;

import com.saasbasics.platform.modules.organization.domain.model.LifecycleStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AssignmentModels {

    private AssignmentModels() {
    }

    public record CreateRequest(
            @NotNull UUID engagementPublicId,
            @NotNull UUID orgUnitPublicId,
            @NotNull UUID positionPublicId,
            @NotNull Boolean primary,
            @NotNull Instant validFrom,
            Instant validTo,
            @Size(max = 500) String remark
    ) {
    }

    public record UpdateRequest(
            @NotNull Boolean primary,
            @NotNull Instant validFrom,
            Instant validTo,
            @Size(max = 500) String remark,
            @NotNull @PositiveOrZero Integer expectedVersion
    ) {
    }

    public record Response(
            UUID publicId,
            UUID engagementPublicId,
            UUID organizationPublicId,
            UUID orgUnitPublicId,
            UUID positionPublicId,
            boolean primary,
            LifecycleStatus status,
            Instant validFrom,
            Instant validTo,
            String remark,
            int version
    ) {
    }
}
