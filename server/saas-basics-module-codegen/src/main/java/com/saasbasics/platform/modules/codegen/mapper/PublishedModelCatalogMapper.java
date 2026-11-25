package com.saasbasics.platform.modules.codegen.mapper;

import com.saasbasics.platform.modules.ai.api.PublishedModelCatalog;
import com.saasbasics.platform.modules.codegen.domain.DynamicSchemaIdentifiers;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PublishedModelCatalogMapper extends PublishedModelCatalog {
    @Override
    default List<Column> columns(long tenantId) {
        var columns = listPublishedColumns(tenantId);
        for (var column : columns) {
            DynamicSchemaIdentifiers.requireTable(tenantId, column.tableName());
            if (column.fieldCode() != null) DynamicSchemaIdentifiers.requireBusinessField(column.fieldCode());
        }
        return columns;
    }

    List<Column> listPublishedColumns(@Param("tenantId") long tenantId);
}
