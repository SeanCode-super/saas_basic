package com.saasbasics.platform.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.exception.BizException;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

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

        List<DictTypeEntity> types = typeMapper.selectList(new LambdaQueryWrapper<DictTypeEntity>()
                .eq(DictTypeEntity::getDeleted, 0)
                .orderByAsc(DictTypeEntity::getDictCode));

        List<DictItemEntity> items = itemMapper.selectList(new LambdaQueryWrapper<DictItemEntity>()
                .eq(DictItemEntity::getDeleted, 0));
        Map<Long, Long> itemCountMap = items.stream()
                .collect(Collectors.groupingBy(DictItemEntity::getDictTypeId, Collectors.counting()));

        return types.stream()
                .map(type -> toTypeResponse(type, itemCountMap.getOrDefault(type.getId(), 0L).intValue()))
                .toList();
    }

    public DictTypeResponse createType(DictTypeSaveRequest request) {
        DictTypeEntity entity = new DictTypeEntity();
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
        entity.setTenantId(request.tenantId());
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
        entity.setTenantId(request.tenantId());
        entity.setStatus(request.status());
        mapper.updateById(entity);
        DictItemResponse response = getItem(id);
        auditTrailService.record("system", "dict_item", String.valueOf(response.id()), "STATUS", request.status(), response.status(), true);
        return response;
    }

    public List<DictOptionResponse> listOptions(String dictCode) {
        Long tenantId = TenantAccessContextHolder.requiredTenantId();
        DictTypeMapper typeMapper = requiredTypeMapper();
        DictTypeEntity type = typeMapper.selectOne(new LambdaQueryWrapper<DictTypeEntity>()
                .eq(DictTypeEntity::getDeleted, 0)
                .eq(DictTypeEntity::getTenantId, tenantId)
                .eq(DictTypeEntity::getDictCode, dictCode)
                .last("LIMIT 1"));
        if (type == null) {
            return List.of();
        }

        return requiredItemMapper().selectList(new LambdaQueryWrapper<DictItemEntity>()
                        .eq(DictItemEntity::getDeleted, 0)
                        .eq(DictItemEntity::getTenantId, tenantId)
                        .eq(DictItemEntity::getDictTypeId, type.getId())
                        .eq(DictItemEntity::getStatus, "ENABLED")
                        .orderByAsc(DictItemEntity::getSortNo, DictItemEntity::getId))
                .stream()
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

    private void apply(DictTypeEntity entity, DictTypeSaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setDictCode(request.dictCode());
        entity.setDictName(request.dictName());
        entity.setDictScope(request.dictScope());
        entity.setStatus(request.status());
        entity.setCacheable(request.cacheable());
        entity.setExtJson(request.extJson());
        entity.setRemark(request.remark());
    }

    private void apply(DictItemEntity entity, Long dictTypeId, DictItemSaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setDictTypeId(dictTypeId);
        entity.setItemValue(request.itemValue());
        entity.setItemLabel(request.itemLabel());
        entity.setItemColor(request.itemColor());
        entity.setItemTag(request.itemTag());
        entity.setParentId(request.parentId() == null ? 0L : request.parentId());
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setStatus(request.status());
        entity.setIsDefault(request.defaultItem());
        entity.setExtJson(request.extJson());
        entity.setRemark(request.remark());
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
