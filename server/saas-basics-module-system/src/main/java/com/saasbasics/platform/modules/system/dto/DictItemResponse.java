package com.saasbasics.platform.modules.system.dto;

public record DictItemResponse(
        Long id,
        Long tenantId,
        Long dictTypeId,
        String itemValue,
        String itemLabel,
        String itemColor,
        String itemTag,
        Long parentId,
        Integer sortNo,
        String status,
        boolean defaultItem,
        String extJson,
        String remark
) {
}
