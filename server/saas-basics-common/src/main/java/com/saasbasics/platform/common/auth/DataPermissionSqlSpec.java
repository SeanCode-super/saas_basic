package com.saasbasics.platform.common.auth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

/**
 * 旗舰级数据权限 SQL 过滤规约
 */
public class DataPermissionSqlSpec {

    private Long tenantId;
    private boolean allowAll;
    private boolean denyAll;

    private final Set<Long> userIds = new LinkedHashSet<>();
    private final Set<Long> organizationIds = new LinkedHashSet<>();
    private final Set<Long> orgUnitIds = new LinkedHashSet<>();
    private final Set<String> treePaths = new LinkedHashSet<>();
    private final Set<Long> roleIds = new LinkedHashSet<>();

    // 历史兼容集合
    private final Set<Long> employeeIds = new LinkedHashSet<>();
    private final Set<Long> departmentIds = new LinkedHashSet<>();
    private final Set<Long> positionIds = new LinkedHashSet<>();

    public static DataPermissionSqlSpec allowAll(Long tenantId) {
        DataPermissionSqlSpec spec = new DataPermissionSqlSpec();
        spec.setTenantId(tenantId);
        spec.setAllowAll(true);
        return spec;
    }

    public static DataPermissionSqlSpec denyAll(Long tenantId) {
        DataPermissionSqlSpec spec = new DataPermissionSqlSpec();
        spec.setTenantId(tenantId);
        spec.setDenyAll(true);
        return spec;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isAllowAll() {
        return allowAll;
    }

    public void setAllowAll(boolean allowAll) {
        this.allowAll = allowAll;
    }

    public boolean isDenyAll() {
        return denyAll;
    }

    public void setDenyAll(boolean denyAll) {
        this.denyAll = denyAll;
    }

    public List<Long> getUserIds() {
        return new ArrayList<>(userIds);
    }

    public void addUserId(Long userId) {
        if (userId != null && userId > 0) {
            userIds.add(userId);
        }
    }

    public void addUserIds(Iterable<Long> userIds) {
        if (userIds == null) {
            return;
        }
        userIds.forEach(this::addUserId);
    }

    public List<Long> getOrganizationIds() {
        return new ArrayList<>(organizationIds);
    }

    public void addOrganizationId(Long organizationId) {
        if (organizationId != null && organizationId > 0) {
            organizationIds.add(organizationId);
        }
    }

    public void addOrganizationIds(Iterable<Long> organizationIds) {
        if (organizationIds == null) {
            return;
        }
        organizationIds.forEach(this::addOrganizationId);
    }

    public List<Long> getOrgUnitIds() {
        return new ArrayList<>(orgUnitIds);
    }

    public void addOrgUnitId(Long orgUnitId) {
        if (orgUnitId != null && orgUnitId > 0) {
            orgUnitIds.add(orgUnitId);
        }
    }

    public void addOrgUnitIds(Iterable<Long> orgUnitIds) {
        if (orgUnitIds == null) {
            return;
        }
        orgUnitIds.forEach(this::addOrgUnitId);
    }

    public List<String> getTreePaths() {
        return new ArrayList<>(treePaths);
    }

    public void addTreePath(String treePath) {
        if (treePath != null && !treePath.isBlank()) {
            treePaths.add(treePath.trim());
        }
    }

    public void addTreePaths(Iterable<String> treePaths) {
        if (treePaths == null) {
            return;
        }
        treePaths.forEach(this::addTreePath);
    }

    public List<Long> getRoleIds() {
        return new ArrayList<>(roleIds);
    }

    public void addRoleId(Long roleId) {
        if (roleId != null && roleId > 0) {
            roleIds.add(roleId);
        }
    }

    public void addRoleIds(Iterable<Long> roleIds) {
        if (roleIds == null) {
            return;
        }
        roleIds.forEach(this::addRoleId);
    }

    // 历史兼容方法
    public List<Long> getEmployeeIds() {
        return new ArrayList<>(employeeIds);
    }

    public void addEmployeeId(Long employeeId) {
        if (employeeId != null && employeeId > 0) {
            employeeIds.add(employeeId);
        }
    }

    public List<Long> getDepartmentIds() {
        return new ArrayList<>(departmentIds);
    }

    public void addDepartmentId(Long departmentId) {
        if (departmentId != null && departmentId > 0) {
            departmentIds.add(departmentId);
            orgUnitIds.add(departmentId);
        }
    }

    public void addDepartmentIds(Iterable<Long> departmentIds) {
        if (departmentIds == null) {
            return;
        }
        departmentIds.forEach(this::addDepartmentId);
    }

    public List<Long> getPositionIds() {
        return new ArrayList<>(positionIds);
    }

    public void addPositionId(Long positionId) {
        if (positionId != null && positionId > 0) {
            positionIds.add(positionId);
        }
    }

    public void addPositionIds(Iterable<Long> positionIds) {
        if (positionIds == null) {
            return;
        }
        positionIds.forEach(this::addPositionId);
    }

    public record AbacCondition(String field, String operator, Object value) {}

    private final List<AbacCondition> abacConditions = new ArrayList<>();

    public List<AbacCondition> getAbacConditions() {
        return new ArrayList<>(abacConditions);
    }

    public void addAbacCondition(String field, String operator, Object value) {
        if (field != null && !field.isBlank() && operator != null && !operator.isBlank()) {
            abacConditions.add(new AbacCondition(field.trim(), operator.trim().toUpperCase(Locale.ROOT), value));
        }
    }

    public boolean hasConstraints() {
        return !userIds.isEmpty()
                || !organizationIds.isEmpty()
                || !orgUnitIds.isEmpty()
                || !treePaths.isEmpty()
                || !departmentIds.isEmpty()
                || !employeeIds.isEmpty()
                || !positionIds.isEmpty()
                || !roleIds.isEmpty()
                || !abacConditions.isEmpty();
    }

    /**
     * 将数据权限规约转化为 JSqlParser 表达式树
     *
     * @param table     当前目标表（支持别名）
     * @param orgCol    组织主体字段名，默认 "organization_id"
     * @param unitCol   组织单元字段名，默认 "org_unit_id"
     * @param userCol   数据归属人字段名，默认 "created_by"
     * @return 构建好的 AST Expression
     */
    public Expression toSqlExpression(Table table, String orgCol, String unitCol, String userCol) {
        if (allowAll) {
            return null;
        }
        if (denyAll) {
            return new EqualsTo(new LongValue(1), new LongValue(0));
        }
        if (!hasConstraints()) {
            return new EqualsTo(new LongValue(1), new LongValue(0));
        }

        List<Expression> expressions = new ArrayList<>();

        // 1. 组织单元 / 部门约束
        Set<Long> combinedUnits = new LinkedHashSet<>(orgUnitIds);
        combinedUnits.addAll(departmentIds);
        if (!combinedUnits.isEmpty()) {
            String colName = (unitCol != null && !unitCol.isBlank()) ? unitCol : "org_unit_id";
            Column col = table != null ? new Column(table, colName) : new Column(colName);
            if (combinedUnits.size() == 1) {
                expressions.add(new EqualsTo(col, new LongValue(combinedUnits.iterator().next())));
            } else {
                List<LongValue> values = combinedUnits.stream().map(LongValue::new).toList();
                expressions.add(new InExpression(col, new ParenthesedExpressionList<>(values)));
            }
        }

        // 2. 组织树路径递归下钻匹配 (tree_path LIKE '/root/%')
        if (!treePaths.isEmpty()) {
            for (String path : treePaths) {
                if (path != null && !path.isBlank()) {
                    Column col = table != null ? new Column(table, "tree_path") : new Column("tree_path");
                    LikeExpression like = new LikeExpression();
                    like.setLeftExpression(col);
                    like.setRightExpression(new StringValue(path.endsWith("%") ? path : path + "%"));
                    expressions.add(like);
                }
            }
        }

        // 3. 组织主体约束
        if (!organizationIds.isEmpty()) {
            String colName = (orgCol != null && !orgCol.isBlank()) ? orgCol : "organization_id";
            Column col = table != null ? new Column(table, colName) : new Column(colName);
            if (organizationIds.size() == 1) {
                expressions.add(new EqualsTo(col, new LongValue(organizationIds.iterator().next())));
            } else {
                List<LongValue> values = organizationIds.stream().map(LongValue::new).toList();
                expressions.add(new InExpression(col, new ParenthesedExpressionList<>(values)));
            }
        }

        // 4. 数据所有者 / 本人经办约束
        if (!userIds.isEmpty()) {
            String colName = (userCol != null && !userCol.isBlank()) ? userCol : "created_by";
            Column col = table != null ? new Column(table, colName) : new Column(colName);
            if (userIds.size() == 1) {
                expressions.add(new EqualsTo(col, new LongValue(userIds.iterator().next())));
            } else {
                List<LongValue> values = userIds.stream().map(LongValue::new).toList();
                expressions.add(new InExpression(col, new ParenthesedExpressionList<>(values)));
            }
        }

        // 5. ABAC 业务属性动态表达式约束 (amount > 5000000, status = 'APPROVED', etc.)
        for (AbacCondition cond : abacConditions) {
            Column col = table != null ? new Column(table, cond.field()) : new Column(cond.field());
            Expression valExpr = toValueExpression(cond.value());
            switch (cond.operator()) {
                case "EQ", "=" -> expressions.add(new EqualsTo().withLeftExpression(col).withRightExpression(valExpr));
                case "NEQ", "!=", "<>" -> expressions.add(new NotEqualsTo().withLeftExpression(col).withRightExpression(valExpr));
                case "GT", ">" -> expressions.add(new GreaterThan().withLeftExpression(col).withRightExpression(valExpr));
                case "GTE", ">=" -> expressions.add(new GreaterThanEquals().withLeftExpression(col).withRightExpression(valExpr));
                case "LT", "<" -> expressions.add(new MinorThan().withLeftExpression(col).withRightExpression(valExpr));
                case "LTE", "<=" -> expressions.add(new MinorThanEquals().withLeftExpression(col).withRightExpression(valExpr));
                case "LIKE" -> {
                    LikeExpression like = new LikeExpression();
                    like.setLeftExpression(col);
                    like.setRightExpression(valExpr);
                    expressions.add(like);
                }
                case "IN" -> {
                    if (cond.value() instanceof Iterable<?> list) {
                        List<Expression> items = new ArrayList<>();
                        for (Object o : list) {
                            items.add(toValueExpression(o));
                        }
                        expressions.add(new InExpression(col, new ParenthesedExpressionList<>(items)));
                    } else {
                        expressions.add(new EqualsTo(col, valExpr));
                    }
                }
                default -> expressions.add(new EqualsTo(col, valExpr));
            }
        }

        if (expressions.isEmpty()) {
            return new EqualsTo(new LongValue(1), new LongValue(0));
        }

        // 多维度条件取并集 (OR)
        Expression combined = expressions.get(0);
        for (int i = 1; i < expressions.size(); i++) {
            combined = new OrExpression(combined, expressions.get(i));
        }

        return expressions.size() > 1 ? new Parenthesis(combined) : combined;
    }

    private Expression toValueExpression(Object val) {
        if (val == null) {
            return new NullValue();
        }
        if (val instanceof Long l) {
            return new LongValue(l);
        }
        if (val instanceof Integer i) {
            return new LongValue(i);
        }
        if (val instanceof Number n) {
            return new DoubleValue(String.valueOf(n));
        }
        return new StringValue(String.valueOf(val));
    }
}
