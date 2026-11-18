package com.saasbasics.platform.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.system.dto.DictItemResponse;
import com.saasbasics.platform.modules.system.dto.DictItemSaveRequest;
import com.saasbasics.platform.modules.system.dto.DictOptionResponse;
import com.saasbasics.platform.modules.system.dto.DictStatusUpdateRequest;
import com.saasbasics.platform.modules.system.dto.DictTypeResponse;
import com.saasbasics.platform.modules.system.dto.DictTypeSaveRequest;
import com.saasbasics.platform.modules.system.entity.DictItemEntity;
import com.saasbasics.platform.modules.system.entity.DictTypeEntity;
import com.saasbasics.platform.modules.system.mapper.DictItemMapper;
import com.saasbasics.platform.modules.system.mapper.DictTypeMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DictService {

    private final ObjectProvider<DictTypeMapper> dictTypeMapperProvider;
    private final ObjectProvider<DictItemMapper> dictItemMapperProvider;
    private final AuditTrailService auditTrailService;

    public DictService(ObjectProvider<DictTypeMapper> dictTypeMapperProvider,
                       ObjectProvider<DictItemMapper> dictItemMapperProvider,
                       AuditTrailService auditTrailService) {
        this.dictTypeMapperProvider = dictTypeMapperProvider;
        this.dictItemMapperProvider = dictItemMapperProvider;
        this.auditTrailService = auditTrailService;
    }

    public List<DictTypeResponse> listTypes() {
        DictTypeMapper typeMapper = requiredTypeMapper();
        DictItemMapper itemMapper = requiredItemMapper();
        Long tenantId = resolveCurrentTenantId();

        List<DictTypeEntity> types = typeMapper.selectList(new LambdaQueryWrapper<DictTypeEntity>()
                .eq(DictTypeEntity::getTenantId, tenantId)
                .eq(DictTypeEntity::getDeleted, 0)
                .orderByAsc(DictTypeEntity::getDictCode));

        if (types.isEmpty() && tenantId > 1L) {
            seedDictionariesFromPlatform(typeMapper, itemMapper, tenantId);
            types = typeMapper.selectList(new LambdaQueryWrapper<DictTypeEntity>()
                    .eq(DictTypeEntity::getTenantId, tenantId)
                    .eq(DictTypeEntity::getDeleted, 0)
                    .orderByAsc(DictTypeEntity::getDictCode));
        }

        List<DictItemEntity> items = itemMapper.selectList(new LambdaQueryWrapper<DictItemEntity>()
                .eq(DictItemEntity::getTenantId, tenantId)
                .eq(DictItemEntity::getDeleted, 0));
        Map<Long, Long> itemCountMap = items.stream()
                .collect(Collectors.groupingBy(DictItemEntity::getDictTypeId, Collectors.counting()));

        return types.stream()
                .map(type -> toTypeResponse(type, itemCountMap.getOrDefault(type.getId(), 0L).intValue()))
                .toList();
    }

    public DictTypeResponse createType(DictTypeSaveRequest request) {
        DictTypeEntity entity = new DictTypeEntity();
        entity.setTenantId(resolveCurrentTenantId());
        apply(entity, request);
        requiredTypeMapper().insert(entity);
        DictTypeResponse response = getType(entity.getId());
        auditTrailService.record("system", "dict_type", String.valueOf(response.id()), "CREATE", request.dictCode(), response.dictCode(), true);
        return response;
    }

    public DictTypeResponse updateType(Long id, DictTypeSaveRequest request) {
        DictTypeMapper mapper = requiredTypeMapper();
        DictTypeEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("DICT_TYPE_NOT_FOUND", "Dict type not found");
        }
        apply(entity, request);
        mapper.updateById(entity);
        DictTypeResponse response = getType(id);
        auditTrailService.record("system", "dict_type", String.valueOf(response.id()), "UPDATE", request.dictCode(), response.dictCode(), true);
        return response;
    }

    public DictTypeResponse updateTypeStatus(Long id, DictStatusUpdateRequest request) {
        DictTypeMapper mapper = requiredTypeMapper();
        DictTypeEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("DICT_TYPE_NOT_FOUND", "Dict type not found");
        }
        entity.setStatus(request.status());
        mapper.updateById(entity);
        DictTypeResponse response = getType(id);
        auditTrailService.record("system", "dict_type", String.valueOf(response.id()), "STATUS", request.status(), response.status(), true);
        return response;
    }

    public List<DictItemResponse> listItems(Long dictTypeId) {
        DictItemMapper mapper = requiredItemMapper();
        return mapper.selectList(new LambdaQueryWrapper<DictItemEntity>()
                        .eq(DictItemEntity::getDeleted, 0)
                        .eq(DictItemEntity::getDictTypeId, dictTypeId)
                        .orderByAsc(DictItemEntity::getSortNo, DictItemEntity::getId))
                .stream()
                .map(this::toItemResponse)
                .toList();
    }

    public DictItemResponse createItem(Long dictTypeId, DictItemSaveRequest request) {
        ensureTypeExists(dictTypeId);
        DictItemEntity entity = new DictItemEntity();
        entity.setTenantId(resolveCurrentTenantId());
        apply(entity, dictTypeId, request);
        requiredItemMapper().insert(entity);
        DictItemResponse response = getItem(entity.getId());
        auditTrailService.record("system", "dict_item", String.valueOf(response.id()), "CREATE", request.itemValue(), response.itemValue(), true);
        return response;
    }

    public DictItemResponse updateItem(Long id, DictItemSaveRequest request) {
        DictItemMapper mapper = requiredItemMapper();
        DictItemEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("DICT_ITEM_NOT_FOUND", "Dict item not found");
        }
        ensureTypeExists(request.dictTypeId());
        apply(entity, request.dictTypeId(), request);
        mapper.updateById(entity);
        DictItemResponse response = getItem(id);
        auditTrailService.record("system", "dict_item", String.valueOf(response.id()), "UPDATE", request.itemValue(), response.itemValue(), true);
        return response;
    }

    public DictItemResponse updateItemStatus(Long id, DictStatusUpdateRequest request) {
        DictItemMapper mapper = requiredItemMapper();
        DictItemEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("DICT_ITEM_NOT_FOUND", "Dict item not found");
        }
        entity.setStatus(request.status());
        mapper.updateById(entity);
        DictItemResponse response = getItem(id);
        auditTrailService.record("system", "dict_item", String.valueOf(response.id()), "STATUS", request.status(), response.status(), true);
        return response;
    }

    @Transactional
    public void deleteType(Long id) {
        DictTypeMapper mapper = requiredTypeMapper();
        DictTypeEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("DICT_TYPE_NOT_FOUND", "Dict type not found");
        }
        LocalDateTime now = LocalDateTime.now();
        mapper.update(null, new LambdaUpdateWrapper<DictTypeEntity>()
                .eq(DictTypeEntity::getId, id)
                .set(DictTypeEntity::getDeletedAt, now));
        mapper.deleteById(id);

        DictItemMapper itemMapper = requiredItemMapper();
        itemMapper.update(null, new LambdaUpdateWrapper<DictItemEntity>()
                .eq(DictItemEntity::getDictTypeId, id)
                .set(DictItemEntity::getDeletedAt, now));
        itemMapper.delete(new LambdaQueryWrapper<DictItemEntity>()
                .eq(DictItemEntity::getDictTypeId, id));

        auditTrailService.record("system", "dict_type", String.valueOf(id), "DELETE", entity.getDictCode(), null, true);
    }

    @Transactional
    public void deleteItem(Long id) {
        DictItemMapper mapper = requiredItemMapper();
        DictItemEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("DICT_ITEM_NOT_FOUND", "Dict item not found");
        }
        LocalDateTime now = LocalDateTime.now();
        mapper.update(null, new LambdaUpdateWrapper<DictItemEntity>()
                .eq(DictItemEntity::getId, id)
                .set(DictItemEntity::getDeletedAt, now));
        mapper.deleteById(id);
        auditTrailService.record("system", "dict_item", String.valueOf(id), "DELETE", entity.getItemValue(), null, true);
    }

    public List<DictOptionResponse> listOptions(String dictCode) {
        Long tenantId = resolveCurrentTenantId();
        DictTypeMapper typeMapper = requiredTypeMapper();
        DictItemMapper itemMapper = requiredItemMapper();

        DictTypeEntity type = typeMapper.selectOne(new LambdaQueryWrapper<DictTypeEntity>()
                .eq(DictTypeEntity::getDeleted, 0)
                .eq(DictTypeEntity::getTenantId, tenantId)
                .eq(DictTypeEntity::getDictCode, dictCode)
                .eq(DictTypeEntity::getStatus, "ENABLED")
                .last("LIMIT 1"));

        List<DictItemEntity> items;
        if (type != null) {
            items = itemMapper.selectList(new LambdaQueryWrapper<DictItemEntity>()
                    .eq(DictItemEntity::getDeleted, 0)
                    .eq(DictItemEntity::getTenantId, tenantId)
                    .eq(DictItemEntity::getDictTypeId, type.getId())
                    .eq(DictItemEntity::getStatus, "ENABLED")
                    .orderByAsc(DictItemEntity::getSortNo, DictItemEntity::getId));
        } else if (!Objects.equals(1L, tenantId)) {
            try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openSystemBypass(
                    TenantAccessContext.BypassOperation.TENANT_BOOTSTRAP,
                    "Fallback read platform global dictionary options")) {
                type = typeMapper.selectOne(new LambdaQueryWrapper<DictTypeEntity>()
                        .eq(DictTypeEntity::getDeleted, 0)
                        .and(w -> w.eq(DictTypeEntity::getTenantId, 1L).or().eq(DictTypeEntity::getDictScope, "GLOBAL"))
                        .eq(DictTypeEntity::getDictCode, dictCode)
                        .eq(DictTypeEntity::getStatus, "ENABLED")
                        .orderByAsc(DictTypeEntity::getTenantId, DictTypeEntity::getId)
                        .last("LIMIT 1"));
                if (type == null) {
                    return List.of();
                }
                Long fallbackTenantId = type.getTenantId();
                items = itemMapper.selectList(new LambdaQueryWrapper<DictItemEntity>()
                        .eq(DictItemEntity::getDeleted, 0)
                        .eq(DictItemEntity::getTenantId, fallbackTenantId)
                        .eq(DictItemEntity::getDictTypeId, type.getId())
                        .eq(DictItemEntity::getStatus, "ENABLED")
                        .orderByAsc(DictItemEntity::getSortNo, DictItemEntity::getId));
            }
        } else {
            return List.of();
        }

        return items.stream()
                .map(item -> new DictOptionResponse(
                        dictCode,
                        item.getItemLabel(),
                        item.getItemValue(),
                        item.getItemColor(),
                        item.getItemTag(),
                        Boolean.TRUE.equals(item.getIsDefault())
                ))
                .toList();
    }

    @Transactional
    protected void seedDictionariesFromPlatform(DictTypeMapper typeMapper, DictItemMapper itemMapper, Long tenantId) {
        List<DictTypeEntity> sourceTypes;
        List<DictItemEntity> sourceItems;
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openSystemBypass(
                TenantAccessContext.BypassOperation.TENANT_BOOTSTRAP, "Clone baseline dictionaries from platform")) {
            sourceTypes = typeMapper.selectList(new LambdaQueryWrapper<DictTypeEntity>()
                    .eq(DictTypeEntity::getTenantId, 1L)
                    .eq(DictTypeEntity::getDeleted, 0));
            sourceItems = itemMapper.selectList(new LambdaQueryWrapper<DictItemEntity>()
                    .eq(DictItemEntity::getTenantId, 1L)
                    .eq(DictItemEntity::getDeleted, 0));
        }

        Map<Long, List<DictItemEntity>> itemsByTypeId = sourceItems.stream()
                .collect(Collectors.groupingBy(DictItemEntity::getDictTypeId, LinkedHashMap::new, Collectors.toList()));
        for (DictTypeEntity src : sourceTypes) {
            DictTypeEntity copyType = new DictTypeEntity();
            copyType.setTenantId(tenantId);
            copyType.setDictCode(src.getDictCode());
            copyType.setDictName(src.getDictName());
            copyType.setDictScope("TENANT");
            copyType.setStatus(src.getStatus());
            copyType.setCacheable(src.getCacheable());
            copyType.setExtJson(src.getExtJson());
            copyType.setRemark(src.getRemark());
            typeMapper.insert(copyType);

            List<DictItemEntity> srcItems = itemsByTypeId.getOrDefault(src.getId(), List.of());
            for (DictItemEntity srcItem : srcItems) {
                DictItemEntity copyItem = new DictItemEntity();
                copyItem.setTenantId(tenantId);
                copyItem.setDictTypeId(copyType.getId());
                copyItem.setItemValue(srcItem.getItemValue());
                copyItem.setItemLabel(srcItem.getItemLabel());
                copyItem.setItemColor(srcItem.getItemColor());
                copyItem.setItemTag(srcItem.getItemTag());
                copyItem.setParentId(0L);
                copyItem.setSortNo(srcItem.getSortNo());
                copyItem.setStatus(srcItem.getStatus());
                copyItem.setIsDefault(srcItem.getIsDefault());
                copyItem.setExtJson(srcItem.getExtJson());
                copyItem.setRemark(srcItem.getRemark());
                itemMapper.insert(copyItem);
            }
        }
    }

    private DictTypeResponse getType(Long id) {
        DictTypeEntity entity = requiredTypeMapper().selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("DICT_TYPE_NOT_FOUND", "Dict type not found");
        }
        long itemCount = requiredItemMapper().selectCount(new LambdaQueryWrapper<DictItemEntity>()
                .eq(DictItemEntity::getDeleted, 0)
                .eq(DictItemEntity::getDictTypeId, id));
        return toTypeResponse(entity, (int) itemCount);
    }

    private DictItemResponse getItem(Long id) {
        DictItemEntity entity = requiredItemMapper().selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("DICT_ITEM_NOT_FOUND", "Dict item not found");
        }
        return toItemResponse(entity);
    }

    private void ensureTypeExists(Long dictTypeId) {
        DictTypeEntity type = requiredTypeMapper().selectById(dictTypeId);
        if (type == null || type.getDeleted() != null && type.getDeleted() == 1) {
            throw new BizException("DICT_TYPE_NOT_FOUND", "Dict type not found");
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private String normalizeJson(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            OBJECT_MAPPER.readTree(trimmed);
            return trimmed;
        } catch (Exception e) {
            throw new BizException("INVALID_JSON", "附加扩展参数必须是合法的 JSON 格式");
        }
    }

    private void apply(DictTypeEntity entity, DictTypeSaveRequest request) {
        if (entity.getTenantId() == null) {
            entity.setTenantId(resolveCurrentTenantId());
        }
        entity.setDictCode(request.dictCode());
        entity.setDictName(request.dictName());
        entity.setDictScope(request.dictScope());
        entity.setStatus(request.status());
        entity.setCacheable(request.cacheable());
        entity.setExtJson(normalizeJson(request.extJson()));
        entity.setRemark(request.remark());
    }

    private void apply(DictItemEntity entity, Long dictTypeId, DictItemSaveRequest request) {
        if (entity.getTenantId() == null) {
            entity.setTenantId(resolveCurrentTenantId());
        }
        entity.setDictTypeId(dictTypeId);
        entity.setItemValue(request.itemValue());
        entity.setItemLabel(request.itemLabel());
        entity.setItemColor(request.itemColor());
        entity.setItemTag(request.itemTag());
        entity.setParentId(request.parentId() == null ? 0L : request.parentId());
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setStatus(request.status());
        entity.setIsDefault(request.defaultItem());
        entity.setExtJson(normalizeJson(request.extJson()));
        entity.setRemark(request.remark());
    }

    private Long resolveCurrentTenantId() {
        TenantAccessContext ctx = TenantAccessContextHolder.current();
        if (ctx != null && ctx.tenantId() != null && ctx.tenantId() > 0L) {
            return ctx.tenantId();
        }
        AuthPrincipal principal = AuthContext.get();
        if (principal != null && principal.tenantId() != null && principal.tenantId() > 0L) {
            return principal.tenantId();
        }
        return 1L;
    }

    private DictTypeResponse toTypeResponse(DictTypeEntity entity, int itemCount) {
        return new DictTypeResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getDictCode(),
                entity.getDictName(),
                entity.getDictScope(),
                entity.getStatus(),
                Boolean.TRUE.equals(entity.getCacheable()),
                entity.getExtJson(),
                entity.getRemark(),
                itemCount
        );
    }

    private DictItemResponse toItemResponse(DictItemEntity entity) {
        return new DictItemResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getDictTypeId(),
                entity.getItemValue(),
                entity.getItemLabel(),
                entity.getItemColor(),
                entity.getItemTag(),
                entity.getParentId(),
                entity.getSortNo(),
                entity.getStatus(),
                Boolean.TRUE.equals(entity.getIsDefault()),
                entity.getExtJson(),
                entity.getRemark()
        );
    }

    private DictTypeMapper requiredTypeMapper() {
        return required(dictTypeMapperProvider, "Dict type operations require the db profile and MySQL connection");
    }

    private DictItemMapper requiredItemMapper() {
        return required(dictItemMapperProvider, "Dict item operations require the db profile and MySQL connection");
    }

    private <T> T required(ObjectProvider<T> provider, String message) {
        T bean = provider.getIfAvailable();
        if (bean == null) {
            throw new BizException("DB_PROFILE_REQUIRED", message);
        }
        return bean;
    }
}
