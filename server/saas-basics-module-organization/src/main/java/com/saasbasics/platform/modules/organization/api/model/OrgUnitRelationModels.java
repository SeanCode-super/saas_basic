package com.saasbasics.platform.modules.organization.api.model;

import com.saasbasics.platform.modules.organization.api.model.OrganizationCommonModels.TypeDescriptorInput;
import com.saasbasics.platform.modules.organization.domain.model.LifecycleStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class OrgUnitRelationModels {

    private OrgUnitRelationModels() {
    }

    public record CreateRequest(
            @NotNull UUID sourceUnitPublicId,
            @NotNull UUID targetUnitPublicId,
            @NotNull @Valid TypeDescriptorInput type,
            @NotNull Instant validFrom,
            Instant validTo,
            @Size(max = 500) String remark
    ) {
    }

    public record UpdateRequest(
            @NotNull @Valid TypeDescriptorInput type,
            @NotNull Instant validFrom,
            Instant validTo,
            @Size(max = 500) String remark,
            @NotNull @PositiveOrZero Integer expectedVersion
    ) {
    }

    public record Response(
            UUID publicId,
            UUID sourceUnitPublicId,
            UUID targetUnitPublicId,
            TypeDescriptorInput type,
            LifecycleStatus status,
            Instant validFrom,
            Instant validTo,
            String remark,
            int version
    ) {
    }
}
