package com.saasbasics.platform.modules.system.infra;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.*;
import org.springframework.stereotype.Component;

import java.sql.Statement;
import java.util.Properties;

@Component
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, org.apache.ibatis.session.ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update", args = {Statement.class}),
        @Signature(type = StatementHandler.class, method = "batch", args = {Statement.class})
})
public class SlowSqlInterceptor implements Interceptor {

    private final InfraMonitorService infraMonitorService;

    public SlowSqlInterceptor(InfraMonitorService infraMonitorService) {
        this.infraMonitorService = infraMonitorService;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (invocation.getTarget() instanceof StatementHandler statementHandler) {
                try {
                    BoundSql boundSql = statementHandler.getBoundSql();
                    if (boundSql != null && boundSql.getSql() != null) {
                        infraMonitorService.recordSlowSql(boundSql.getSql(), cost);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {}
}
