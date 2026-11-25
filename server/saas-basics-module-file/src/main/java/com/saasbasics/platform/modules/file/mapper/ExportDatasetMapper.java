package com.saasbasics.platform.modules.file.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;

@Mapper
public interface ExportDatasetMapper {
    record Model(Long id, String modelCode, String modelName, String tableName) {}
    record Field(String fieldCode, String fieldName, String columnType) {}
    record Filter(String column, Object value) {}
    record Query(Long tenantId, String table, boolean logicalDelete, List<String> columns,
                 List<String> keywordColumns, String keyword, List<Filter> filters) {}

    List<Model> publishedModels(@Param("tenantId") Long tenantId);
    Model publishedModel(@Param("tenantId") Long tenantId, @Param("modelCode") String modelCode);
    List<Field> modelFields(@Param("tenantId") Long tenantId, @Param("modelId") Long modelId);
    long countRows(@Param("query") Query query);
    void streamRows(@Param("query") Query query, @Param("limit") int limit, ResultHandler<Map<String, Object>> handler);
}
