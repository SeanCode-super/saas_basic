package com.saasbasics.platform.modules.codegen.domain;

import com.saasbasics.platform.common.exception.BizException;
import java.util.Set;
import java.util.regex.Pattern;

public final class DynamicSchemaIdentifiers {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_]{2,64}");
    private static final Set<String> SYSTEM_COLUMNS = Set.of("id", "tenant_id", "dept_id", "created_by", "created_at",
            "updated_by", "updated_at", "deleted", "deleted_at", "version");

    private static final Pattern TENANT_TABLE = Pattern.compile("^biz_dyn_t([0-9]+)_.*$");

    private DynamicSchemaIdentifiers() {}

    public static void requireTable(long tenantId, String tableName) {
        if (tableName == null || !IDENTIFIER.matcher(tableName).matches() || !tableName.startsWith("biz_dyn_")) {
            throw new BizException("UNSAFE_DYNAMIC_TABLE", "动态表标识无效");
        }
        var tenantTable = TENANT_TABLE.matcher(tableName);
        if (tenantTable.matches() && !tenantTable.group(1).equals(Long.toString(tenantId))) {
            throw new BizException("UNSAFE_DYNAMIC_TABLE", "动态表与模型所属租户不一致");
        }
    }

    public static void requireBusinessField(String name) {
        if (name == null || !IDENTIFIER.matcher(name).matches() || SYSTEM_COLUMNS.contains(name)) {
            throw new BizException("INVALID_FIELD_CODE", "业务字段编码无效或与系统字段重名");
        }
    }
}
