package com.saasbasics.platform.common.auth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
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

    /**
     * 双层 DNF/CNF 规则子句集合：单条子句内部按 AND 结合（范围 ∧ ABAC），多条允许子句之间按 OR 结合，DENY 子句显式否决
     */
    private final List<RuleClause> allowClauses = new ArrayList<>();
    private final List<RuleClause> denyClauses = new ArrayList<>();

    public static final class RuleClause {

        private final Set<Long> userIds = new LinkedHashSet<>();
        private final Set<Long> employeeIds = new LinkedHashSet<>();
        private final Set<Long> organizationIds = new LinkedHashSet<>();
        private final Set<Long> orgUnitIds = new LinkedHashSet<>();
        private final Set<Long> departmentIds = new LinkedHashSet<>();
        private final Set<Long> positionIds = new LinkedHashSet<>();
        private final Set<Long> roleIds = new LinkedHashSet<>();
        private final Set<String> treePaths = new LinkedHashSet<>();
        private final Set<Long> scopedMemberUserIds = new LinkedHashSet<>();
        private final List<AbacCondition> abacConditions = new ArrayList<>();
        private boolean scopeConfigured;

        public Set<Long> getUserIds() {
            return userIds;
        }

        public void addUserId(Long userId) {
            if (userId != null && userId > 0) {
                userIds.add(userId);
                scopeConfigured = true;
            }
        }

        public void addUserIds(Iterable<Long> ids) {
            if (ids != null) {
                ids.forEach(this::addUserId);
            }
        }

        public Set<Long> getEmployeeIds() {
            return employeeIds;
        }

        public void addEmployeeId(Long employeeId) {
            if (employeeId != null && employeeId > 0) {
                employeeIds.add(employeeId);
            }
        }

        public Set<Long> getOrganizationIds() {
            return organizationIds;
        }

        public void addOrganizationId(Long orgId) {
            if (orgId != null && orgId > 0) {
                organizationIds.add(orgId);
                scopeConfigured = true;
            }
        }

        public Set<Long> getOrgUnitIds() {
            return orgUnitIds;
        }

        public void addOrgUnitId(Long unitId) {
            if (unitId != null && unitId > 0) {
                orgUnitIds.add(unitId);
                scopeConfigured = true;
            }
        }

        public void addOrgUnitIds(Iterable<Long> ids) {
            if (ids != null) {
                ids.forEach(this::addOrgUnitId);
            }
        }

        public Set<Long> getDepartmentIds() {
            return departmentIds;
        }

        public void addDepartmentId(Long deptId) {
            if (deptId != null && deptId > 0) {
                departmentIds.add(deptId);
                orgUnitIds.add(deptId);
                scopeConfigured = true;
            }
        }

        public void addDepartmentIds(Iterable<Long> ids) {
            if (ids != null) {
                ids.forEach(this::addDepartmentId);
            }
        }

        public Set<Long> getPositionIds() {
            return positionIds;
        }

        public void addPositionId(Long positionId) {
            if (positionId != null && positionId > 0) {
                positionIds.add(positionId);
                scopeConfigured = true;
            }
        }

        public Set<Long> getRoleIds() {
            return roleIds;
        }

        public void addRoleId(Long roleId) {
            if (roleId != null && roleId > 0) {
                roleIds.add(roleId);
                scopeConfigured = true;
            }
        }

        public void addRoleIds(Iterable<Long> ids) {
            if (ids != null) {
                ids.forEach(this::addRoleId);
            }
        }

        public Set<String> getTreePaths() {
            return treePaths;
        }

        public void addTreePath(String path) {
            if (path != null && !path.isBlank()) {
                treePaths.add(path.trim());
                scopeConfigured = true;
            }
        }

        public Set<Long> getScopedMemberUserIds() {
            return scopedMemberUserIds;
        }

        public void addScopedMemberUserId(Long userId) {
            if (userId != null && userId > 0) {
                scopedMemberUserIds.add(userId);
                scopeConfigured = true;
            }
        }

        public void addScopedMemberUserIds(Iterable<Long> ids) {
            if (ids != null) {
                ids.forEach(this::addScopedMemberUserId);
            }
        }

        public List<AbacCondition> getAbacConditions() {
            return abacConditions;
        }

        public void addAbacCondition(String field, String operator, Object value) {
            if (field != null && !field.isBlank() && operator != null && !operator.isBlank()) {
                abacConditions.add(new AbacCondition(field.trim(), operator.trim().toUpperCase(Locale.ROOT), value));
            }
        }

        public boolean isScopeConfigured() {
            return scopeConfigured;
        }

        public void markScopeConfigured() {
            this.scopeConfigured = true;
        }

        public boolean hasConstraints() {
            return scopeConfigured || !abacConditions.isEmpty() || !employeeIds.isEmpty();
        }
    }

    public List<RuleClause> getAllowClauses() {
        return List.copyOf(allowClauses);
    }

    public List<RuleClause> getDenyClauses() {
        return List.copyOf(denyClauses);
    }

    public void addAllowClause(RuleClause clause) {
        if (clause == null) {
            return;
        }
        allowClauses.add(clause);
        mirrorClauseFields(clause);
    }

    public void addDenyClause(RuleClause clause) {
        if (clause == null) {
            return;
        }
        denyClauses.add(clause);
    }

    private void mirrorClauseFields(RuleClause clause) {
        userIds.addAll(clause.getUserIds());
        employeeIds.addAll(clause.getEmployeeIds());
        organizationIds.addAll(clause.getOrganizationIds());
        orgUnitIds.addAll(clause.getOrgUnitIds());
        departmentIds.addAll(clause.getDepartmentIds());
        positionIds.addAll(clause.getPositionIds());
        roleIds.addAll(clause.getRoleIds());
        treePaths.addAll(clause.getTreePaths());
        abacConditions.addAll(clause.getAbacConditions());
    }

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
        return !allowClauses.isEmpty()
                || !denyClauses.isEmpty()
                || !userIds.isEmpty()
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
     * @param orgCol    组织主体字段名，空字符串表示当前表不含该字段
     * @param unitCol   组织单元字段名，空字符串表示当前表不含该字段
     * @param userCol   数据归属人字段名，空字符串表示当前表不含该字段
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

        if (!allowClauses.isEmpty() || !denyClauses.isEmpty()) {
            return compileClauseDisjunction(table, orgCol, unitCol, userCol);
        }

        return compileFlatLegacy(table, orgCol, unitCol, userCol);
    }

    private Expression compileClauseDisjunction(Table table, String orgCol, String unitCol, String userCol) {
        boolean hasAnyOwnershipColumn = hasColumn(orgCol) || hasColumn(unitCol) || hasColumn(userCol);

        List<Expression> compiledAllows = new ArrayList<>();
        boolean sharedAllowPassThrough = false;
        for (RuleClause clause : allowClauses) {
            Expression clauseExpr = compileSingleClause(clause, table, orgCol, unitCol, userCol);
            if (clauseExpr != null) {
                compiledAllows.add(clauseExpr);
            } else if (!hasAnyOwnershipColumn && clause.getAbacConditions().isEmpty()) {
                sharedAllowPassThrough = true;
            }
        }

        List<Expression> compiledDenies = new ArrayList<>();
        for (RuleClause clause : denyClauses) {
            Expression clauseExpr = compileSingleClause(clause, table, orgCol, unitCol, userCol);
            if (clauseExpr != null) {
                compiledDenies.add(clauseExpr);
            }
        }

        Expression allowExpr = null;
        if (!sharedAllowPassThrough) {
            if (allowClauses.isEmpty() || compiledAllows.isEmpty()) {
                return new EqualsTo(new LongValue(1), new LongValue(0));
            }
            allowExpr = joinWithOr(compiledAllows);
        }

        if (compiledDenies.isEmpty()) {
            return allowExpr;
        }

        Expression denyCombined = joinWithOr(compiledDenies);
        Expression notDeny = new NotExpression(wrapInParenthesis(denyCombined));
        if (allowExpr == null) {
            return notDeny;
        }
        return new AndExpression(wrapInParenthesis(allowExpr), notDeny);
    }

    private Expression compileSingleClause(RuleClause clause, Table table, String orgCol, String unitCol, String userCol) {
        List<Expression> scopeExpressions = new ArrayList<>();
        Set<Long> effectiveUserIds = new LinkedHashSet<>(clause.getUserIds());

        Set<Long> combinedUnits = new LinkedHashSet<>(clause.getOrgUnitIds());
        combinedUnits.addAll(clause.getDepartmentIds());
        if (!combinedUnits.isEmpty()) {
            if (hasColumn(unitCol)) {
                scopeExpressions.add(buildIdPredicate(table, unitCol, combinedUnits));
            } else {
                effectiveUserIds.addAll(clause.getScopedMemberUserIds());
            }
        }

        if (!clause.getOrganizationIds().isEmpty()) {
            if (hasColumn(orgCol)) {
                scopeExpressions.add(buildIdPredicate(table, orgCol, clause.getOrganizationIds()));
            } else {
                effectiveUserIds.addAll(clause.getScopedMemberUserIds());
            }
        }

        if (!clause.getPositionIds().isEmpty() || !clause.getRoleIds().isEmpty()) {
            effectiveUserIds.addAll(clause.getScopedMemberUserIds());
        }

        if (!clause.getTreePaths().isEmpty() && hasColumn(unitCol)) {
            for (String path : clause.getTreePaths()) {
                if (path != null && !path.isBlank()) {
                    Column col = table != null ? new Column(table, "tree_path") : new Column("tree_path");
                    LikeExpression like = new LikeExpression();
                    like.setLeftExpression(col);
                    like.setRightExpression(new StringValue(path.endsWith("%") ? path : path + "%"));
                    scopeExpressions.add(like);
                }
            }
        }

        if (!effectiveUserIds.isEmpty() && hasColumn(userCol)) {
            scopeExpressions.add(buildIdPredicate(table, userCol, effectiveUserIds));
        }

        Expression scopeExpr = null;
        if (!scopeExpressions.isEmpty()) {
            scopeExpr = joinWithOr(scopeExpressions);
        } else if (clause.isScopeConfigured()) {
            if (!hasColumn(orgCol) && !hasColumn(unitCol) && !hasColumn(userCol)) {
                scopeExpr = null;
            } else {
                scopeExpr = new EqualsTo(new LongValue(1), new LongValue(0));
            }
        }

        Expression abacExpr = null;
        if (!clause.getAbacConditions().isEmpty()) {
            List<Expression> abacList = new ArrayList<>();
            for (AbacCondition cond : clause.getAbacConditions()) {
                abacList.add(buildAbacExpression(table, cond));
            }
            abacExpr = joinWithAnd(abacList);
        }

        if (scopeExpr != null && abacExpr != null) {
            return new Parenthesis(new AndExpression(wrapIfCompound(scopeExpr), wrapIfCompound(abacExpr)));
        }
        return scopeExpr != null ? scopeExpr : abacExpr;
    }

    private Expression compileFlatLegacy(Table table, String orgCol, String unitCol, String userCol) {
        List<Expression> expressions = new ArrayList<>();

        // 1. 组织单元 / 部门约束
        Set<Long> combinedUnits = new LinkedHashSet<>(orgUnitIds);
        combinedUnits.addAll(departmentIds);
        if (!combinedUnits.isEmpty()) {
            String colName = hasColumn(unitCol) ? unitCol : "org_unit_id";
            expressions.add(buildIdPredicate(table, colName, combinedUnits));
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
            String colName = hasColumn(orgCol) ? orgCol : "organization_id";
            expressions.add(buildIdPredicate(table, colName, organizationIds));
        }

        // 4. 数据所有者 / 本人经办约束
        if (!userIds.isEmpty()) {
            String colName = hasColumn(userCol) ? userCol : "created_by";
            expressions.add(buildIdPredicate(table, colName, userIds));
        }

        // 5. ABAC 业务属性动态表达式约束 (amount > 5000000, status = 'APPROVED', etc.)
        for (AbacCondition cond : abacConditions) {
            expressions.add(buildAbacExpression(table, cond));
        }

        if (expressions.isEmpty()) {
            return new EqualsTo(new LongValue(1), new LongValue(0));
        }

        return joinWithOr(expressions);
    }

    private static boolean hasColumn(String col) {
        return col != null && !col.isBlank();
    }

    private Expression buildIdPredicate(Table table, String colName, Set<Long> ids) {
        Column col = table != null ? new Column(table, colName) : new Column(colName);
        if (ids.size() == 1) {
            return new EqualsTo(col, new LongValue(ids.iterator().next()));
        }
        List<LongValue> values = ids.stream().map(LongValue::new).toList();
        return new InExpression(col, new ParenthesedExpressionList<>(values));
    }

    private Expression buildAbacExpression(Table table, AbacCondition cond) {
        Column col = table != null ? new Column(table, cond.field()) : new Column(cond.field());
        Expression valExpr = toValueExpression(cond.value());
        return switch (cond.operator()) {
            case "EQ", "=" -> new EqualsTo().withLeftExpression(col).withRightExpression(valExpr);
            case "NEQ", "!=", "<>" -> new NotEqualsTo().withLeftExpression(col).withRightExpression(valExpr);
            case "GT", ">" -> new GreaterThan().withLeftExpression(col).withRightExpression(valExpr);
            case "GTE", ">=" -> new GreaterThanEquals().withLeftExpression(col).withRightExpression(valExpr);
            case "LT", "<" -> new MinorThan().withLeftExpression(col).withRightExpression(valExpr);
            case "LTE", "<=" -> new MinorThanEquals().withLeftExpression(col).withRightExpression(valExpr);
            case "LIKE" -> {
                LikeExpression like = new LikeExpression();
                like.setLeftExpression(col);
                like.setRightExpression(valExpr);
                yield like;
            }
            case "IN" -> {
                if (cond.value() instanceof Iterable<?> list) {
                    List<Expression> items = new ArrayList<>();
                    for (Object o : list) {
                        items.add(toValueExpression(o));
                    }
                    yield new InExpression(col, new ParenthesedExpressionList<>(items));
                }
                yield new EqualsTo(col, valExpr);
            }
            default -> new EqualsTo(col, valExpr);
        };
    }

    private static Expression joinWithOr(List<Expression> expressions) {
        Expression combined = expressions.get(0);
        for (int i = 1; i < expressions.size(); i++) {
            combined = new OrExpression(combined, expressions.get(i));
        }
        return expressions.size() > 1 ? wrapInParenthesis(combined) : combined;
    }

    private static Expression joinWithAnd(List<Expression> expressions) {
        Expression combined = expressions.get(0);
        for (int i = 1; i < expressions.size(); i++) {
            combined = new AndExpression(combined, expressions.get(i));
        }
        return combined;
    }

    private static Expression wrapInParenthesis(Expression expr) {
        return expr instanceof Parenthesis ? expr : new Parenthesis(expr);
    }

    private static Expression wrapIfCompound(Expression expr) {
        return (expr instanceof AndExpression || expr instanceof OrExpression) ? new Parenthesis(expr) : expr;
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
