package com.saasbasics.platform.modules.file.dto;

import java.util.List;

public record FileCapabilityResponse(
        List<String> storageAdapters,
        boolean versioningEnabled,
        boolean relationTrackingEnabled,
        boolean accessAuditEnabled
) {
}
