package com.saasbasics.platform.modules.system.trash;

public record TrashAppSummaryResponse(
        String appCode,
        String appName,
        String icon,
        long count
) {
}
