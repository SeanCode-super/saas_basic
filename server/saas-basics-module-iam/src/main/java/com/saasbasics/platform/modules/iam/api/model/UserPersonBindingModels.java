package com.saasbasics.platform.modules.iam.api.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class UserPersonBindingModels {

    private UserPersonBindingModels() {
    }

    public record CreateRequest(
            @NotNull UUID userPublicId,
            @NotNull UUID personPublicId,
            @NotNull Instant validFrom,
            Instant validTo,
            @Size(max = 500) String remark
    ) {
    }

    public record UpdateRequest(
            @NotNull UUID userPublicId,
            @NotNull UUID personPublicId,
            @NotNull Instant validFrom,
            Instant validTo,
            @Size(max = 500) String remark,
            @NotNull @PositiveOrZero Integer expectedVersion
    ) {
    }

    public record TransitionRequest(
            @NotNull Status targetStatus,
            @NotNull @PositiveOrZero Integer expectedVersion
    ) {
    }

    public record Response(
            UUID publicId,
            UUID userPublicId,
            UUID personPublicId,
            Status status,
            Instant validFrom,
            Instant validTo,
            int version,
            String remark
    ) {
    }

    public enum Status {
        DRAFT,
        ACTIVE,
        INACTIVE,
        ARCHIVED
    }
}
