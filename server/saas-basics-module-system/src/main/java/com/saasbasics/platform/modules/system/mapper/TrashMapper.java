package com.saasbasics.platform.modules.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.saasbasics.platform.modules.system.trash.TrashTarget;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TrashMapper {
    record SchemaColumn(String tableName, String columnName) {}
    record RegisteredModel(String tableName, String modelName, String modelCode) {}
    record DeletedRow(long id, String title, LocalDateTime deletedAt) {}

    List<RegisteredModel> registeredModels(@Param("tenantId") long tenantId);
    @InterceptorIgnore(tenantLine = "1")
    List<SchemaColumn> resourceColumns(@Param("names") Set<String> names);
    long countDeleted(@Param("target") TrashTarget target, @Param("tenantId") long tenantId);
    List<DeletedRow> listDeleted(@Param("target") TrashTarget target, @Param("tenantId") long tenantId);
    DeletedRow lockDeleted(@Param("target") TrashTarget target, @Param("tenantId") long tenantId, @Param("id") long id);
    int restore(@Param("target") TrashTarget target, @Param("tenantId") long tenantId, @Param("id") long id,
                @Param("actor") long actor, @Param("deletedAt") LocalDateTime deletedAt);
    int purge(@Param("target") TrashTarget target, @Param("tenantId") long tenantId, @Param("id") long id,
              @Param("deletedAt") LocalDateTime deletedAt);
}
