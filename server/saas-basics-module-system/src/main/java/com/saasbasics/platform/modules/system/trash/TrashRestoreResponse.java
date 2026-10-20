package com.saasbasics.platform.modules.system.trash;

public record TrashRestoreResponse(
        String tableName,
        Long recordId,
        int restoredCount,
        int cascadeRestoredCount,
        String message
) {
}
