package com.saasbasics.platform.modules.system.trash;

import java.time.LocalDateTime;

public record TrashItemResponse(
        String id,
        String appCode,
        String appName,
        String tableName,
        String moduleName,
        Long recordId,
        String title,
        LocalDateTime deletedAt,
        String remark,
        boolean hasCascade
) {
}
