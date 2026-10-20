package com.saasbasics.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasbasics.platform.common.auth.DataPermissionContext;
import com.saasbasics.platform.common.auth.DataPermissionProvider;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.UUID;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class EnterpriseDataPermissionHandlerTest {

    private EnterpriseDataPermissionHandler handler;
    private StaticListableBeanFactory beanFactory;

    @BeforeEach
    void setUp() {
        beanFactory = new StaticListableBeanFactory();
        handler = new EnterpriseDataPermissionHandler(beanFactory.getBeanProvider(DataPermissionProvider.class));
        TenantAccessContextHolder.openTenant(1001L, "tenant_test", 100L);
    }

    @AfterEach
    void tearDown() {
        TenantAccessContextHolder.clear();
        DataPermissionContext.clear();
    }

    @Test
    @DisplayName("平台特权旁路时，豁免数据权限拦截")
    void bypassesWhenTenantAccessContextBypasses() {
        TenantAccessContextHolder.openSystemBypass(
                TenantAccessContext.BypassOperation.PLATFORM_TENANT_ADMINISTRATION,
                "admin bypass"
        );
        Table table = new Table("biz_order");

        Expression expr = handler.getSqlSegment(table, null, "test.mapper.OrderMapper.selectList");
        assertThat(expr).isNull();
    }

    @Test
    @DisplayName("查询平台级系统字典/配置表时，直接豁免数据权限")
    void bypassesPlatformTables() {
        Table table = new Table("plat_tenant");
        Expression expr = handler.getSqlSegment(table, null, "test.mapper.TenantMapper.selectList");
        assertThat(expr).isNull();
    }

    @Test
    @DisplayName("显式标注 @DataScope(ignore = true) 时豁免数据权限改写")
    void ignoresWhenDataScopeIgnoreIsTrue() {
        DataScope ignoreScope = createDataScope("", "", "organization_id", "org_unit_id", "created_by", true);
        DataPermissionContext.setDataScope(ignoreScope);

        Table table = new Table("biz_contract");
        Expression expr = handler.getSqlSegment(table, null, "test.mapper.ContractMapper.selectList");
        assertThat(expr).isNull();
    }

    @Test
    @DisplayName("规约为 allowAll 时返回 null，不追加条件")
    void returnsNullWhenAllowAll() {
        DataPermissionSqlSpec spec = DataPermissionSqlSpec.allowAll(1001L);
        DataPermissionContext.setSqlSpec(spec);
        DataPermissionContext.setResourceCode("biz:order");

        Table table = new Table("biz_order");
        Expression expr = handler.getSqlSegment(table, null, "test.mapper.OrderMapper.selectList");
        assertThat(expr).isNull();
    }

    @Test
    @DisplayName("规约为 denyAll 时返回 1 = 0 阻断条件")
    void returnsDenyAllExpression() {
        DataPermissionSqlSpec spec = DataPermissionSqlSpec.denyAll(1001L);
        DataPermissionContext.setSqlSpec(spec);
        DataPermissionContext.setResourceCode("biz:order");

        Table table = new Table("biz_order");
        Expression expr = handler.getSqlSegment(table, null, "test.mapper.OrderMapper.selectList");
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).isEqualTo("1 = 0");
    }

    @Test
    @DisplayName("单一组织单元约束生成等于表达式")
    void rewritesSingleOrgUnit() {
        DataPermissionSqlSpec spec = new DataPermissionSqlSpec();
        spec.setTenantId(1001L);
        spec.addOrgUnitId(501L);
        DataPermissionContext.setSqlSpec(spec);
        DataPermissionContext.setResourceCode("biz:order");

        Table table = new Table("biz_order");
        Expression expr = handler.getSqlSegment(table, null, "test.mapper.OrderMapper.selectList");
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).isEqualTo("biz_order.org_unit_id = 501");
    }

    @Test
    @DisplayName("多个组织单元约束生成 IN 表达式")
    void rewritesMultipleOrgUnits() {
        DataPermissionSqlSpec spec = new DataPermissionSqlSpec();
        spec.setTenantId(1001L);
        spec.addOrgUnitIds(List.of(501L, 502L, 503L));
        DataPermissionContext.setSqlSpec(spec);
        DataPermissionContext.setResourceCode("biz:order");

        Table table = new Table("biz_order");
        Expression expr = handler.getSqlSegment(table, null, "test.mapper.OrderMapper.selectList");
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).isEqualTo("biz_order.org_unit_id IN (501, 502, 503)");
    }

    @Test
    @DisplayName("部门与本人复合范围生成正确的括号并集 (OR) 表达式")
    void rewritesUnionOfUnitAndUser() {
        DataPermissionSqlSpec spec = new DataPermissionSqlSpec();
        spec.setTenantId(1001L);
        spec.addOrgUnitIds(List.of(201L, 202L));
        spec.addUserId(88L);
        DataPermissionContext.setSqlSpec(spec);
        DataPermissionContext.setResourceCode("biz:project");

        Table table = new Table("biz_project");
        Expression expr = handler.getSqlSegment(table, null, "test.mapper.ProjectMapper.selectList");
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).isEqualTo("(biz_project.org_unit_id IN (201, 202) OR biz_project.created_by = 88)");
    }

    @Test
    @DisplayName("多表 JOIN 查询中匹配目标 tableAlias")
    void filtersOnlyTargetTableAliasInMultiTableJoin() {
        DataScope dataScope = createDataScope("biz:task", "t", "organization_id", "org_unit_id", "created_by", false);
        DataPermissionContext.setDataScope(dataScope);

        DataPermissionSqlSpec spec = new DataPermissionSqlSpec();
        spec.setTenantId(1001L);
        spec.addOrgUnitId(100L);
        DataPermissionContext.setSqlSpec(spec);

        Table mainTable = new Table("biz_task").withAlias(new net.sf.jsqlparser.expression.Alias("t"));
        Table joinTable = new Table("sys_user").withAlias(new net.sf.jsqlparser.expression.Alias("u"));

        // 目标表命中改写
        Expression mainExpr = handler.getSqlSegment(mainTable, null, "test.mapper.TaskMapper.selectTaskList");
        assertThat(mainExpr).isNotNull();
        assertThat(mainExpr.toString()).isEqualTo("t.org_unit_id = 100");

        // 非目标表豁免改写
        Expression joinExpr = handler.getSqlSegment(joinTable, null, "test.mapper.TaskMapper.selectTaskList");
        assertThat(joinExpr).isNull();
    }

    private DataScope createDataScope(
            String resource,
            String tableAlias,
            String orgColumn,
            String unitColumn,
            String userColumn,
            boolean ignore
    ) {
        return new DataScope() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return DataScope.class;
            }

            @Override
            public String resource() {
                return resource;
            }

            @Override
            public String tableAlias() {
                return tableAlias;
            }

            @Override
            public String orgColumn() {
                return orgColumn;
            }

            @Override
            public String unitColumn() {
                return unitColumn;
            }

            @Override
            public String userColumn() {
                return userColumn;
            }

            @Override
            public boolean ignore() {
                return ignore;
            }
        };
    }
}
