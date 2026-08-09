package com.saasbasics.platform.modules.system.dto;

public record DictTypeResponse(
        Long id,
        Long tenantId,
        String dictCode,
        String dictName,
        String dictScope,
        String status,
        boolean cacheable,
        String extJson,
        String remark,
        int itemCount
) {
}
