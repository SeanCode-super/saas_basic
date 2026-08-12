package com.saasbasics.platform.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.mybatis.UuidTypeHandler;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    static final Set<String> PLATFORM_TABLES = Set.of(
            "plat_tenant",
            "plat_tenant_package"
    );

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new LongValue(TenantAccessContextHolder.requiredTenantId());
            }

            @Override
            public boolean ignoreTable(String tableName) {
                TenantAccessContext context = TenantAccessContextHolder.current();
                return context != null && context.bypassesTenantIsolation()
                        || PLATFORM_TABLES.contains(tableName.toLowerCase(Locale.ROOT));
            }
        }));
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(200L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    @Bean
    public ConfigurationCustomizer uuidTypeHandlerCustomizer() {
        return configuration -> configuration.getTypeHandlerRegistry().register(UuidTypeHandler.class);
    }

    @Bean
    public MetaObjectHandler auditMetaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                fillTenantId(metaObject);
                LocalDateTime now = LocalDateTime.now();
                Long actorId = AuthContext.getUserId() == null ? 0L : AuthContext.getUserId();
                strictInsertFill(metaObject, "createdBy", Long.class, actorId);
                strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updatedBy", Long.class, actorId);
                strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
                strictInsertFill(metaObject, "deleted", Integer.class, 0);
                strictInsertFill(metaObject, "version", Integer.class, 0);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                fillTenantId(metaObject);
                Long actorId = AuthContext.getUserId() == null ? 0L : AuthContext.getUserId();
                strictUpdateFill(metaObject, "updatedBy", Long.class, actorId);
                strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }

            private void fillTenantId(MetaObject metaObject) {
                if (!metaObject.hasSetter("tenantId")) {
                    return;
                }
                TenantAccessContext context = TenantAccessContextHolder.current();
                if (context == null) {
                    throw new BizException("TENANT_CONTEXT_REQUIRED", "Tenant-owned writes require a tenant context");
                }
                if (!context.bypassesTenantIsolation()) {
                    metaObject.setValue("tenantId", context.tenantId());
                }
            }
        };
    }
}
