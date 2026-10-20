package com.saasbasics.platform.security;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.saasbasics.platform.common.auth.DataPermissionContext;
import com.saasbasics.platform.common.auth.DataPermissionProvider;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.common.auth.RequireDataPermission;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

/**
 * 旗舰级多租户与组织数据权限 MyBatis-Plus SQL 改写处理器
 * 严格遵循 JSqlParser AST 语法树改写规范，防止 SQL 注入与跨租户/跨部门越权
 */
@Component
public class EnterpriseDataPermissionHandler implements MultiDataPermissionHandler {

    private static final Set<String> PLATFORM_TABLES = Set.of(
            "plat_tenant",
            "plat_tenant_package",
            "plat_tenant_onboarding",
            "plat_tenant_subscription",
            "sys_dict_type",
            "sys_dict_data",
            "sys_config"
    );

    private final ObjectProvider<DataPermissionProvider> dataPermissionProvider;
    private final Map<String, Optional<DataScope>> dataScopeCache = new ConcurrentHashMap<>();

    public EnterpriseDataPermissionHandler(ObjectProvider<DataPermissionProvider> dataPermissionProvider) {
        this.dataPermissionProvider = dataPermissionProvider;
    }

    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        return null;
    }

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        // 1. 检查租户上下文是否旁路隔离（如平台级特权或异步系统迁移）
        TenantAccessContext context = TenantAccessContextHolder.current();
        if (context != null && context.bypassesTenantIsolation()) {
            return null;
        }

        // 2. 平台级系统表直接豁免数据权限
        String tableName = table.getName().toLowerCase(Locale.ROOT);
        if (PLATFORM_TABLES.contains(tableName)) {
            return null;
        }

        // 3. 解析当前查询的 @DataScope 声明
        DataScope dataScope = resolveDataScope(mappedStatementId);
        if (dataScope != null && dataScope.ignore()) {
            return null;
        }

        // 4. 多表查询别名与主表智能推导匹配
        if (dataScope != null && !dataScope.tableAlias().isBlank()) {
            // 用户显式指定了 tableAlias，按用户指定别名严格匹配
            String targetAlias = dataScope.tableAlias();
            if (table.getAlias() != null && !targetAlias.equalsIgnoreCase(table.getAlias().getName())) {
                return null;
            }
            if (table.getAlias() == null && !targetAlias.equalsIgnoreCase(table.getName())) {
                return null;
            }
        } else {
            // 用户未指定 tableAlias 时，自动智能推导当前 Mapper 对应的主实体表
            String mainTableName = resolveMainTableName(mappedStatementId);
            if (mainTableName != null) {
                // 如果当前遍历到的表不是主表（例如 JOIN 的字典表、人员表等从表），直接豁免，防止从表字段冲突
                if (!table.getName().equalsIgnoreCase(mainTableName)) {
                    return null;
                }
            }
        }

        // 5. 获取业务资源标识
        String resourceCode = null;
        if (dataScope != null && !dataScope.resource().isBlank()) {
            resourceCode = dataScope.resource();
        } else if (DataPermissionContext.getResourceCode() != null) {
            resourceCode = DataPermissionContext.getResourceCode();
        }

        // 如果既无 @DataScope 标注，上下文亦无数据权限声明，默认不改写当前 SQL
        if (dataScope == null && resourceCode == null && DataPermissionContext.getSqlSpec() == null) {
            return null;
        }

        // 6. 解析并求解数据权限规约
        DataPermissionSqlSpec spec = DataPermissionContext.getSqlSpec();
        if (spec == null && resourceCode != null) {
            DataPermissionProvider provider = dataPermissionProvider.getIfAvailable();
            if (provider != null) {
                spec = provider.resolveDataPermission(resourceCode);
            }
        }

        if (spec == null) {
            return null;
        }

        // 7. 将规约转换为当前目标表的 AST 表达式
        String orgCol = dataScope != null ? dataScope.orgColumn() : "organization_id";
        String unitCol = dataScope != null ? dataScope.unitColumn() : "org_unit_id";
        String userCol = dataScope != null ? dataScope.userColumn() : "created_by";

        return spec.toSqlExpression(table, orgCol, unitCol, userCol);
    }

    private DataScope resolveDataScope(String mappedStatementId) {
        DataScope threadLocalScope = DataPermissionContext.getDataScope();
        if (threadLocalScope != null) {
            return threadLocalScope;
        }
        if (mappedStatementId == null || !mappedStatementId.contains(".")) {
            return null;
        }

        return dataScopeCache.computeIfAbsent(mappedStatementId, this::findDataScopeFromStatement)
                .orElse(null);
    }

    private Optional<DataScope> findDataScopeFromStatement(String mappedStatementId) {
        int lastDotIndex = mappedStatementId.lastIndexOf('.');
        if (lastDotIndex <= 0) {
            return Optional.empty();
        }

        String className = mappedStatementId.substring(0, lastDotIndex);
        String methodName = mappedStatementId.substring(lastDotIndex + 1);

        try {
            Class<?> clazz = Class.forName(className);

            // 1. 查找 Mapper 方法上的注解
            for (Method method : clazz.getMethods()) {
                if (method.getName().equals(methodName)) {
                    DataScope scope = AnnotatedElementUtils.findMergedAnnotation(method, DataScope.class);
                    if (scope != null) {
                        return Optional.of(scope);
                    }
                    RequireDataPermission legacy = AnnotatedElementUtils.findMergedAnnotation(method, RequireDataPermission.class);
                    if (legacy != null) {
                        return Optional.of(synthesizeFromLegacy(legacy));
                    }
                }
            }

            // 2. 查找 Mapper 接口类上的注解
            DataScope classScope = AnnotatedElementUtils.findMergedAnnotation(clazz, DataScope.class);
            if (classScope != null) {
                return Optional.of(classScope);
            }
            RequireDataPermission legacy = AnnotatedElementUtils.findMergedAnnotation(clazz, RequireDataPermission.class);
            if (legacy != null) {
                return Optional.of(synthesizeFromLegacy(legacy));
            }
        } catch (ClassNotFoundException ignored) {
            // Mapped statement might not map to a standard Java class (e.g. dynamic scripts)
        }

        return Optional.empty();
    }

    private DataScope synthesizeFromLegacy(RequireDataPermission legacy) {
        return new DataScope() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return DataScope.class;
            }

            @Override
            public String resource() {
                return legacy.value();
            }

            @Override
            public String tableAlias() {
                return "";
            }

            @Override
            public String orgColumn() {
                return "organization_id";
            }

            @Override
            public String unitColumn() {
                return "org_unit_id";
            }

            @Override
            public String userColumn() {
                return "created_by";
            }

            @Override
            public boolean ignore() {
                return false;
            }
        };
    }

    private final Map<String, Optional<String>> statementMainTableCache = new ConcurrentHashMap<>();

    private String resolveMainTableName(String mappedStatementId) {
        if (mappedStatementId == null || !mappedStatementId.contains(".")) {
            return null;
        }
        int lastDotIndex = mappedStatementId.lastIndexOf('.');
        String className = mappedStatementId.substring(0, lastDotIndex);
        return statementMainTableCache.computeIfAbsent(className, this::findTableNameFromMapper).orElse(null);
    }

    private Optional<String> findTableNameFromMapper(String mapperClassName) {
        try {
            Class<?> clazz = Class.forName(mapperClassName);
            for (java.lang.reflect.Type genericInterface : clazz.getGenericInterfaces()) {
                if (genericInterface instanceof java.lang.reflect.ParameterizedType pt) {
                    if (com.baomidou.mybatisplus.core.mapper.BaseMapper.class.isAssignableFrom((Class<?>) pt.getRawType())) {
                        java.lang.reflect.Type[] typeArgs = pt.getActualTypeArguments();
                        if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> entityClass) {
                            com.baomidou.mybatisplus.core.metadata.TableInfo tableInfo = com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(entityClass);
                            if (tableInfo != null && tableInfo.getTableName() != null) {
                                return Optional.of(tableInfo.getTableName().toLowerCase(Locale.ROOT));
                            }
                            com.baomidou.mybatisplus.annotation.TableName annotation = entityClass.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class);
                            if (annotation != null && !annotation.value().isBlank()) {
                                return Optional.of(annotation.value().toLowerCase(Locale.ROOT));
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }
}
