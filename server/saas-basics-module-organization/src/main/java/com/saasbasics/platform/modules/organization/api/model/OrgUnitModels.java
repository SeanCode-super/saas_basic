package com.saasbasics.platform.modules.organization.api.model;

import com.saasbasics.platform.modules.organization.domain.model.LifecycleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class OrgUnitModels {

    private OrgUnitModels() {
    }

    public record CreateRequest(
            @NotNull UUID organizationPublicId,
            UUID parentUnitPublicId,
            @NotBlank @Size(max = 128) String unitCode,
            @NotBlank @Size(max = 256) String displayName,
            Integer sortOrder,
            @NotNull Instant validFrom,
            Instant validTo,
            @Size(max = 500) String remark
    ) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 128) String unitCode,
            @NotBlank @Size(max = 256) String displayName,
            Integer sortOrder,
            @NotNull Instant validFrom,
            Instant validTo,
            @Size(max = 500) String remark,
            @NotNull @PositiveOrZero Integer expectedVersion
    ) {
    }

    public record MoveRequest(
            UUID parentUnitPublicId,
            @NotNull @PositiveOrZero Integer expectedVersion
    ) {
    }

    public record Response(
            UUID publicId,
            UUID organizationPublicId,
            UUID parentUnitPublicId,
            String unitCode,
            String displayName,
            String treePath,
            int sortOrder,
            LifecycleStatus status,
            Instant validFrom,
            Instant validTo,
            String remark,
            int version
    ) {
    }
}
