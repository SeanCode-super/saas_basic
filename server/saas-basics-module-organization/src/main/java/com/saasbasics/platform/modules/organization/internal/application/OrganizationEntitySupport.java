package com.saasbasics.platform.modules.organization.internal.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.id.UuidV7Generator;
import com.saasbasics.platform.modules.organization.domain.model.EffectivePeriod;
import com.saasbasics.platform.modules.organization.domain.model.LifecycleStatus;
import com.saasbasics.platform.modules.organization.domain.model.OrganizationRules;
import com.saasbasics.platform.modules.organization.domain.model.TypeDescriptor;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.AbstractOrganizationResourceEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.AbstractTypedResourceEntity;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrganizationEntitySupport {

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();
    private final Clock clock = Clock.systemUTC();

    public <E extends AbstractOrganizationResourceEntity> void initialize(E entity,
                                                                           Instant validFrom,
                                                                           Instant validTo,
                                                                           String remark) {
        entity.setPublicId(uuidGenerator.generate().toString());
        entity.setStatus(LifecycleStatus.DRAFT.name());
        applyPeriod(entity, validFrom, validTo);
        entity.setRemark(normalizeOptional(remark));
    }

    public void applyPeriod(AbstractOrganizationResourceEntity entity, Instant validFrom, Instant validTo) {
        EffectivePeriod period = OrganizationRules.requirePeriod(validFrom, validTo);
        entity.setValidFrom(toLocalDateTime(period.validFrom()));
        entity.setValidTo(toLocalDateTime(period.validTo()));
    }

    public void applyType(AbstractTypedResourceEntity entity, TypeDescriptor type) {
        entity.setTypeNamespace(type.namespace());
        entity.setTypeCode(type.code());
        entity.setTypeVersion(type.version());
    }

    public <E extends AbstractOrganizationResourceEntity> E byPublicId(BaseMapper<E> mapper,
                                                                        UUID publicId,
                                                                        String notFoundCode) {
        if (publicId == null) {
            throw new BizException(notFoundCode, "The resource public identifier is required");
        }
        E entity = mapper.selectOne(new QueryWrapper<E>()
                .eq("public_id", publicId.toString())
                .eq("deleted", 0)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new BizException(notFoundCode, "The requested organization resource was not found");
        }
        return entity;
    }

    public <E extends AbstractOrganizationResourceEntity> E byInternalId(BaseMapper<E> mapper,
                                                                          Long id,
                                                                          String notFoundCode) {
        E entity = id == null ? null : mapper.selectById(id);
        if (entity == null) {
            throw new BizException(notFoundCode, "The referenced organization resource was not found");
        }
        return entity;
    }

    public <E extends AbstractOrganizationResourceEntity> void update(BaseMapper<E> mapper,
                                                                       E entity,
                                                                       int expectedVersion) {
        requireVersion(entity, expectedVersion);
        if (mapper.updateById(entity) != 1) {
            throw new BizException("ORG_VERSION_CONFLICT", "The resource changed after it was read");
        }
    }

    public <E extends AbstractOrganizationResourceEntity> boolean transition(BaseMapper<E> mapper,
                                                                              E entity,
                                                                              LifecycleStatus target,
                                                                              int expectedVersion) {
        requireVersion(entity, expectedVersion);
        LifecycleStatus current = LifecycleStatus.valueOf(entity.getStatus());
        current.requireTransitionTo(target);
        if (current == target) {
            return false;
        }
        entity.setStatus(target.name());
        update(mapper, entity, expectedVersion);
        return true;
    }

    public <E extends AbstractOrganizationResourceEntity> void softDelete(BaseMapper<E> mapper,
                                                                           E entity,
                                                                           int expectedVersion) {
        requireVersion(entity, expectedVersion);
        if (LifecycleStatus.valueOf(entity.getStatus()) != LifecycleStatus.ARCHIVED) {
            throw new BizException("ORG_DELETE_REQUIRES_ARCHIVED", "Only an archived resource can be deleted");
        }
        LocalDateTime deletedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        int updated = mapper.update(null, new UpdateWrapper<E>()
                .set("deleted", 1)
                .set("deleted_at", deletedAt)
                .setSql("version = version + 1")
                .eq("id", entity.getId())
                .eq("version", expectedVersion)
                .eq("deleted", 0));
        if (updated != 1) {
            throw new BizException("ORG_VERSION_CONFLICT", "The resource changed after it was read");
        }
        entity.setDeleted(1);
        entity.setDeletedAt(deletedAt);
        entity.setVersion(expectedVersion + 1);
    }

    public void requireDraftForIdentityChange(AbstractOrganizationResourceEntity entity,
                                               boolean identityChanged) {
        if (identityChanged && LifecycleStatus.valueOf(entity.getStatus()) != LifecycleStatus.DRAFT) {
            throw new BizException(
                    "ORG_IDENTITY_IMMUTABLE",
                    "Identity and relationship fields can only change while the resource is in DRAFT status"
            );
        }
    }

    public EffectivePeriod period(AbstractOrganizationResourceEntity entity) {
        return new EffectivePeriod(toInstant(entity.getValidFrom()), toInstant(entity.getValidTo()));
    }

    public Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public UUID toUuid(String value) {
        return UUID.fromString(value);
    }

    public String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LocalDateTime toLocalDateTime(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private void requireVersion(AbstractOrganizationResourceEntity entity, int expectedVersion) {
        if (entity.getVersion() == null || entity.getVersion() != expectedVersion) {
            throw new BizException("ORG_VERSION_CONFLICT", "The resource version does not match expectedVersion");
        }
    }
}
