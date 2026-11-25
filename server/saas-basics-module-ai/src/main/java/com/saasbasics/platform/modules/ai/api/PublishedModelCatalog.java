package com.saasbasics.platform.modules.ai.api;

import java.util.List;

/** Columns contributed to AI reports by the tenant's published low-code models. */
public interface PublishedModelCatalog {
    record Column(String tableName, String modelName, String fieldCode, String fieldName, String columnType) {}

    List<Column> columns(long tenantId);
}
