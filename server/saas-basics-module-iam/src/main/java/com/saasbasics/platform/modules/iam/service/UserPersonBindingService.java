package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.saasbasics.platform.common.api.PageResponse;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.id.UuidV7Generator;
import com.saasbasics.platform.modules.audit.api.AuditRecorder;
import com.saasbasics.platform.modules.iam.api.UserPersonDirectory;
import com.saasbasics.platform.modules.iam.api.model.UserPersonBindingModels;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.entity.UserPersonBindingEntity;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import com.saasbasics.platform.modules.iam.mapper.UserPersonBindingMapper;
import com.saasbasics.platform.modules.organization.api.OrganizationDirectory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPersonBindingService implements UserPersonDirectory {

    private final ObjectProvider<UserPersonBindingMapper> bindingMapperProvider;
    private final ObjectProvider<UserMapper> userMapperProvider;
    private final OrganizationDirectory organizationDirectory;
    private final AuditRecorder auditRecorder;
    private final UuidV7Generator uuidGenerator = new UuidV7Generator();
    private final Clock clock = Clock.systemUTC();

    public UserPersonBindingService(ObjectProvider<UserPersonBindingMapper> bindingMapperProvider,
                                    ObjectProvider<UserMapper> userMapperProvider,
                                    OrganizationDirectory organizationDirectory,
                                    AuditRecorder auditRecorder) {
        this.bindingMapperProvider = bindingMapperProvider;
        this.userMapperProvider = userMapperProvider;
        this.organizationDirectory = organizationDirectory;
        this.auditRecorder = auditRecorder;
    }

    public PageResponse<UserPersonBindingModels.Response> list(int page, int size) {
        return list(page, size, null, null, null, null);
    }

    public PageResponse<UserPersonBindingModels.Response> list(int page,
                                                               int size,
                                                               UUID userPublicId,
                                                               UUID personPublicId,
                                                               UserPersonBindingModels.Status status,
                                                               Instant effectiveAt) {
        QueryWrapper<UserPersonBindingEntity> query = new QueryWrapper<UserPersonBindingEntity>().eq("deleted", 0);
        if (userPublicId != null) {
            query.eq("user_id", requireUser(userPublicId).getId());
        }
        if (personPublicId != null) {
            organizationDirectory.getPerson(personPublicId);
            query.eq("person_public_id", personPublicId.toString());
        }
        if (status != null) {
            query.eq("status", status.name());
        }
        if (effectiveAt != null) {
            LocalDateTime at = toLocal(effectiveAt);
            query.le("valid_from", at).and(period -> period.isNull("valid_to").or().gt("valid_to", at));
        }
        query.orderByAsc("user_id", "valid_from", "id");
        Page<UserPersonBindingEntity> result = requiredBindingMapper().selectPage(
                Page.of(page, size),
                query
        );
        return new PageResponse<>(result.getRecords().stream().map(this::response).toList(), result.getTotal());
    }

    public UserPersonBindingModels.Response get(UUID publicId) {
        return response(requireBinding(publicId));
    }

    @Transactional
    public UserPersonBindingModels.Response create(UserPersonBindingModels.CreateRequest request) {
        requirePeriod(request.validFrom(), request.validTo());
        UserEntity user = requireUser(request.userPublicId());
        organizationDirectory.getPerson(request.personPublicId());
        UserPersonBindingEntity entity = new UserPersonBindingEntity();
        entity.setPublicId(uuidGenerator.generate().toString());
        entity.setUserId(user.getId());
        entity.setPersonPublicId(request.personPublicId().toString());
        entity.setStatus(UserPersonBindingModels.Status.DRAFT.name());
        entity.setValidFrom(toLocal(request.validFrom()));
        entity.setValidTo(toLocal(request.validTo()));
        entity.setVersion(0);
        entity.setDeleted(0);
        entity.setRemark(normalize(request.remark()));
        requiredBindingMapper().insert(entity);
        auditRecorder.record("iam", "user_person_binding", entity.getPublicId(), "CREATE", null, "DRAFT", true);
        return response(entity);
    }

    @Transactional
    public UserPersonBindingModels.Response update(UUID publicId,
                                                   UserPersonBindingModels.UpdateRequest request) {
        requirePeriod(request.validFrom(), request.validTo());
        UserPersonBindingEntity entity = requireBinding(publicId);
        requireVersion(entity, request.expectedVersion());
        if (!UserPersonBindingModels.Status.DRAFT.name().equals(entity.getStatus())) {
            throw new BizException(
                    "IAM_USER_PERSON_UPDATE_REQUIRES_DRAFT",
                    "Only draft bindings can change account, person, or effective period"
            );
        }
        UserEntity user = requireUser(request.userPublicId());
        organizationDirectory.getPerson(request.personPublicId());
        entity.setUserId(user.getId());
        entity.setPersonPublicId(request.personPublicId().toString());
        entity.setValidFrom(toLocal(request.validFrom()));
        entity.setValidTo(toLocal(request.validTo()));
        entity.setRemark(normalize(request.remark()));
        if (requiredBindingMapper().updateById(entity) != 1) {
            throw versionConflict();
        }
        auditRecorder.record("iam", "user_person_binding", entity.getPublicId(), "UPDATE",
                "DRAFT", "DRAFT", true);
        return response(entity);
    }

    @Transactional
    public UserPersonBindingModels.Response transition(UUID publicId,
                                                       UserPersonBindingModels.TransitionRequest request) {
        UserPersonBindingEntity entity = requireBinding(publicId);
        requireVersion(entity, request.expectedVersion());
        UserPersonBindingModels.Status current = UserPersonBindingModels.Status.valueOf(entity.getStatus());
        UserPersonBindingModels.Status target = request.targetStatus();
        requireTransition(current, target);
        if (current == target) {
            return response(entity);
        }
        if (target == UserPersonBindingModels.Status.ACTIVE) {
            assertBindingWithinPersonPeriod(entity);
            assertNoOverlappingBinding(entity);
        }
        entity.setStatus(target.name());
        try {
            if (requiredBindingMapper().updateById(entity) != 1) {
                throw versionConflict();
            }
        } catch (DuplicateKeyException exception) {
            throw new BizException("IAM_USER_PERSON_ACTIVE_CONFLICT", "The account already has an active person binding");
        }
        auditRecorder.record("iam", "user_person_binding", entity.getPublicId(), "LIFECYCLE",
                current.name(), target.name(), true);
        return response(entity);
    }

    @Transactional
    public void delete(UUID publicId, int expectedVersion) {
        UserPersonBindingEntity entity = requireBinding(publicId);
        requireVersion(entity, expectedVersion);
        if (!UserPersonBindingModels.Status.ARCHIVED.name().equals(entity.getStatus())) {
            throw new BizException("IAM_USER_PERSON_DELETE_REQUIRES_ARCHIVED", "Only archived bindings can be deleted");
        }
        int updated = requiredBindingMapper().update(null, new UpdateWrapper<UserPersonBindingEntity>()
                .set("deleted", 1)
                .set("deleted_at", LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .setSql("version = version + 1")
                .eq("id", entity.getId()).eq("version", expectedVersion).eq("deleted", 0));
        if (updated != 1) {
            throw versionConflict();
        }
        auditRecorder.record("iam", "user_person_binding", entity.getPublicId(), "DELETE", "ARCHIVED", null, true);
    }

    @Override
    public Optional<BindingSummary> findEffectiveByUser(UUID userPublicId, Instant effectiveAt) {
        requireInstant(effectiveAt);
        UserEntity user = requireUser(userPublicId);
        UserPersonBindingEntity entity = requiredBindingMapper().selectEffectiveByUser(
                user.getTenantId(), user.getId(), toLocal(effectiveAt));
        return Optional.ofNullable(entity).map(item -> summary(item, user));
    }

    @Override
    public List<BindingSummary> findEffectiveByPerson(UUID personPublicId, Instant effectiveAt) {
        requireInstant(effectiveAt);
        organizationDirectory.requireEffectivePerson(personPublicId, effectiveAt);
        return requiredBindingMapper().selectEffectiveByPerson(
                        requiredTenantId(), personPublicId.toString(), toLocal(effectiveAt))
                .stream().map(entity -> summary(entity, requireUser(entity.getUserId()))).toList();
    }

    private void assertNoOverlappingBinding(UserPersonBindingEntity candidate) {
        long count = requiredBindingMapper().selectCount(new QueryWrapper<UserPersonBindingEntity>()
                .eq("user_id", candidate.getUserId()).eq("deleted", 0).eq("status", "ACTIVE")
                .ne(candidate.getId() != null, "id", candidate.getId())
                .lt(candidate.getValidTo() != null, "valid_from", candidate.getValidTo())
                .and(query -> query.isNull("valid_to").or().gt("valid_to", candidate.getValidFrom())));
        if (count > 0) {
            throw new BizException("IAM_USER_PERSON_PERIOD_CONFLICT", "Active person binding periods cannot overlap");
        }
    }

    private void assertBindingWithinPersonPeriod(UserPersonBindingEntity binding) {
        OrganizationDirectory.PersonSummary person = organizationDirectory.getPerson(
                UUID.fromString(binding.getPersonPublicId()));
        Instant bindingFrom = toInstant(binding.getValidFrom());
        Instant bindingTo = toInstant(binding.getValidTo());
        boolean startsWithinPersonPeriod = !bindingFrom.isBefore(person.validFrom());
        boolean endsWithinPersonPeriod = person.validTo() == null
                || bindingTo != null && !bindingTo.isAfter(person.validTo());
        if (!"ACTIVE".equals(person.status()) || !startsWithinPersonPeriod || !endsWithinPersonPeriod) {
            throw new BizException(
                    "IAM_USER_PERSON_PERIOD_OUTSIDE_PERSON",
                    "The binding effective period must be contained within the active person period"
            );
        }
    }

    private UserPersonBindingEntity requireBinding(UUID publicId) {
        if (publicId == null) {
            throw new BizException("IAM_USER_PERSON_BINDING_ID_REQUIRED", "binding publicId is required");
        }
        UserPersonBindingEntity entity = requiredBindingMapper().selectOne(new QueryWrapper<UserPersonBindingEntity>()
                .eq("public_id", publicId.toString()).eq("deleted", 0).last("LIMIT 1"));
        if (entity == null) throw new BizException("IAM_USER_PERSON_BINDING_NOT_FOUND", "The binding was not found");
        return entity;
    }

    private UserEntity requireUser(UUID publicId) {
        if (publicId == null) throw new BizException("IAM_USER_PUBLIC_ID_REQUIRED", "userPublicId is required");
        UserEntity user = requiredUserMapper().selectByTenantAndPublicId(requiredTenantId(), publicId.toString());
        if (user == null) throw new BizException("IAM_USER_NOT_FOUND", "The account was not found");
        return user;
    }

    private UserEntity requireUser(Long id) {
        UserEntity user = requiredUserMapper().selectById(id);
        if (user == null) throw new BizException("IAM_USER_NOT_FOUND", "The account was not found");
        return user;
    }

    private BindingSummary summary(UserPersonBindingEntity entity, UserEntity user) {
        return new BindingSummary(UUID.fromString(entity.getPublicId()), UUID.fromString(user.getPublicId()),
                UUID.fromString(entity.getPersonPublicId()), toInstant(entity.getValidFrom()), toInstant(entity.getValidTo()),
                entity.getVersion());
    }

    private UserPersonBindingModels.Response response(UserPersonBindingEntity entity) {
        UserEntity user = requireUser(entity.getUserId());
        return new UserPersonBindingModels.Response(UUID.fromString(entity.getPublicId()), UUID.fromString(user.getPublicId()),
                UUID.fromString(entity.getPersonPublicId()), UserPersonBindingModels.Status.valueOf(entity.getStatus()),
                toInstant(entity.getValidFrom()), toInstant(entity.getValidTo()), entity.getVersion(), entity.getRemark());
    }

    private void requireTransition(UserPersonBindingModels.Status current, UserPersonBindingModels.Status target) {
        boolean allowed = current == target || switch (current) {
            case DRAFT -> target == UserPersonBindingModels.Status.ACTIVE || target == UserPersonBindingModels.Status.ARCHIVED;
            case ACTIVE -> target == UserPersonBindingModels.Status.INACTIVE;
            case INACTIVE -> target == UserPersonBindingModels.Status.ACTIVE || target == UserPersonBindingModels.Status.ARCHIVED;
            case ARCHIVED -> false;
        };
        if (!allowed) throw new BizException("IAM_USER_PERSON_INVALID_TRANSITION", "The binding lifecycle transition is not allowed");
    }

    private void requirePeriod(Instant from, Instant to) {
        requireInstant(from);
        if (to != null && !to.isAfter(from)) throw new BizException("IAM_USER_PERSON_PERIOD_INVALID", "validTo must be after validFrom");
    }

    private void requireVersion(UserPersonBindingEntity entity, Integer expectedVersion) {
        if (expectedVersion == null || entity.getVersion() == null || !entity.getVersion().equals(expectedVersion)) {
            throw versionConflict();
        }
    }

    private BizException versionConflict() { return new BizException("IAM_USER_PERSON_VERSION_CONFLICT", "The binding changed after it was read"); }
    private void requireInstant(Instant at) { if (at == null) throw new BizException("EFFECTIVE_AT_REQUIRED", "effectiveAt is required"); }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private LocalDateTime toLocal(Instant value) { return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private Instant toInstant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
    private Long requiredTenantId() {
        return com.saasbasics.platform.common.tenant.TenantAccessContextHolder.requiredTenantId();
    }
    private UserPersonBindingMapper requiredBindingMapper() {
        UserPersonBindingMapper mapper = bindingMapperProvider.getIfAvailable();
        if (mapper == null) throw new BizException("DB_PROFILE_REQUIRED", "Binding operations require a database");
        return mapper;
    }
    private UserMapper requiredUserMapper() {
        UserMapper mapper = userMapperProvider.getIfAvailable();
        if (mapper == null) throw new BizException("DB_PROFILE_REQUIRED", "Binding operations require a database");
        return mapper;
    }
}
