package com.saasbasics.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.common.auth.SqlAstDataGuard;
import java.util.List;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SqlAstDataGuardTest {

    @Test
    @DisplayName("单条 RuleClause 内部门/成员与 ABAC 条件按 AND 结合，杜绝跨部门低金额泄露")
    void compilesSingleClauseAsConjunctionOfScopeAndAbac() {
        DataPermissionSqlSpec spec = new DataPermissionSqlSpec();
        spec.setTenantId(1001L);

        DataPermissionSqlSpec.RuleClause clause = new DataPermissionSqlSpec.RuleClause();
        clause.addOrgUnitId(101L);
        clause.addScopedMemberUserIds(List.of(11L, 12L));
        clause.addAbacCondition("total_amount", "LTE", 50000L);
        spec.addAllowClause(clause);

        // 1. 含 org_unit_id 列的表：直接使用 org_unit_id = 101 AND total_amount <= 50000
        Table orderTable = new Table("biz_order");
        Expression orderExpr = spec.toSqlExpression(orderTable, "", "org_unit_id", "created_by");
        assertThat(orderExpr).isNotNull();
        assertThat(orderExpr.toString())
                .isEqualTo("(biz_order.org_unit_id = 101 AND biz_order.total_amount <= 50000)");

        // 2. 不含 org_unit_id 列的 CRM 表：退化映射部门成员 owner_user_id IN (11, 12) AND total_amount <= 50000
        Table customerTable = new Table("crm_customer");
        Expression customerExpr = spec.toSqlExpression(customerTable, "", "", "owner_user_id");
        assertThat(customerExpr).isNotNull();
        assertThat(customerExpr.toString())
                .isEqualTo("(crm_customer.owner_user_id IN (11, 12) AND crm_customer.total_amount <= 50000)");
    }

    @Test
    @DisplayName("多条 ALLOW 子句按 OR 合并，且显式 DENY 子句作为 NOT 阻断叠加")
    void compilesAllowDisjunctionAndDenyExclusion() {
        DataPermissionSqlSpec spec = new DataPermissionSqlSpec();
        spec.setTenantId(1001L);

        DataPermissionSqlSpec.RuleClause deptClause = new DataPermissionSqlSpec.RuleClause();
        deptClause.addOrgUnitId(200L);
        deptClause.addAbacCondition("status", "EQ", "APPROVED");
        spec.addAllowClause(deptClause);

        DataPermissionSqlSpec.RuleClause selfClause = new DataPermissionSqlSpec.RuleClause();
        selfClause.addUserId(88L);
        spec.addAllowClause(selfClause);

        DataPermissionSqlSpec.RuleClause denyHighRisk = new DataPermissionSqlSpec.RuleClause();
        denyHighRisk.addAbacCondition("risk_level", "EQ", "HIGH");
        spec.addDenyClause(denyHighRisk);

        Table table = new Table("crm_contract");
        Expression expr = spec.toSqlExpression(table, "", "org_unit_id", "created_by");
        assertThat(expr).isNotNull();
        assertThat(expr.toString())
                .isEqualTo("((crm_contract.org_unit_id = 200 AND crm_contract.status = 'APPROVED') OR crm_contract.created_by = 88) AND NOT (crm_contract.risk_level = 'HIGH')");
    }

    @Test
    @DisplayName("SqlAstDataGuard 正确包裹既有 WHERE OR 条件并支持表别名")
    void rewritesSelectWithParenthesizedWhereAndAlias() {
        DataPermissionSqlSpec spec = new DataPermissionSqlSpec();
        spec.setTenantId(1001L);
        DataPermissionSqlSpec.RuleClause clause = new DataPermissionSqlSpec.RuleClause();
        clause.addUserId(42L);
        spec.addAllowClause(clause);

        String original = "SELECT c.id, c.name FROM crm_customer c WHERE c.tenant_id = 1001 AND (c.status = 'A' OR c.status = 'B') ORDER BY c.id DESC";
        String rewritten = SqlAstDataGuard.rewriteSelectSql(original, table ->
                "crm_customer".equals(table)
                        ? new SqlAstDataGuard.TableScopeConfig(spec, "", "", "owner_user_id")
                        : null
        );

        assertThat(rewritten)
                .contains("(c.tenant_id = 1001 AND (c.status = 'A' OR c.status = 'B')) AND (c.owner_user_id = 42)");
    }

    @Test
    @DisplayName("SqlAstDataGuard 拒绝非 SELECT 写操作语句")
    void rejectsNonSelectStatement() {
        DataPermissionSqlSpec spec = DataPermissionSqlSpec.denyAll(1001L);
        assertThatThrownBy(() -> SqlAstDataGuard.rewriteSelectSql(
                "DELETE FROM crm_customer WHERE id = 1",
                t -> new SqlAstDataGuard.TableScopeConfig(spec, "", "", "owner_user_id")
        )).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("CRM 客户表配置 unitColumn=dept_id 时按本部门及下级部门 ID 列表改写 SQL AST")
    void rewritesCrmCustomerWithDepartmentUnitColumn() {
        DataPermissionSqlSpec spec = new DataPermissionSqlSpec();
        spec.setTenantId(2L);
        DataPermissionSqlSpec.RuleClause deptAndChildren = new DataPermissionSqlSpec.RuleClause();
        deptAndChildren.addDepartmentIds(List.of(10L, 11L));
        spec.addAllowClause(deptAndChildren);

        String original = "SELECT c.id, c.name FROM crm_customer c WHERE c.tenant_id = 2 AND c.is_pool = 0";
        String rewritten = SqlAstDataGuard.rewriteSelectSql(original, table ->
                "crm_customer".equals(table)
                        ? new SqlAstDataGuard.TableScopeConfig(spec, "", "dept_id", "owner_user_id")
                        : null
        );

        assertThat(rewritten)
                .contains("c.dept_id IN (10, 11)")
                .contains("c.tenant_id = 2");
    }
}
