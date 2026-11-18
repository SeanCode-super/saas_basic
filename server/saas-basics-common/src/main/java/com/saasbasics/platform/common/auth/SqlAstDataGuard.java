package com.saasbasics.platform.common.auth;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;

/**
 * 与 ORM 解耦的通用 SQL AST 数据权限注入网关，统一服务于 JdbcTemplate 流式导出、AI NL2SQL 及直连 SQL 查询。
 */
public final class SqlAstDataGuard {

    public record TableScopeConfig(
            DataPermissionSqlSpec spec,
            String orgColumn,
            String unitColumn,
            String userColumn
    ) {}

    private SqlAstDataGuard() {
    }

    public static String rewriteSelectSql(String sql, Function<String, TableScopeConfig> configResolver) {
        if (sql == null || sql.isBlank() || configResolver == null) {
            return sql;
        }
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select select)) {
                throw new SecurityException("数据权限校验仅允许执行 SELECT 查询语句");
            }
            rewriteSelect(select, configResolver);
            return select.toString();
        } catch (SecurityException se) {
            throw se;
        } catch (Exception ex) {
            throw new SecurityException("SQL 安全语法解析失败，已拒绝执行: " + ex.getMessage(), ex);
        }
    }

    private static void rewriteSelect(Select select, Function<String, TableScopeConfig> configResolver) {
        if (select instanceof PlainSelect plainSelect) {
            rewritePlainSelect(plainSelect, configResolver);
        } else if (select instanceof SetOperationList setOpList && setOpList.getSelects() != null) {
            for (Select child : setOpList.getSelects()) {
                rewriteSelect(child, configResolver);
            }
        } else if (select instanceof ParenthesedSelect parenthesed && parenthesed.getSelect() != null) {
            rewriteSelect(parenthesed.getSelect(), configResolver);
        }
    }

    private static void rewritePlainSelect(PlainSelect plainSelect, Function<String, TableScopeConfig> configResolver) {
        FromItem fromItem = plainSelect.getFromItem();
        applyFromItem(plainSelect, fromItem, configResolver);

        List<Join> joins = plainSelect.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                applyFromItem(plainSelect, join.getRightItem(), configResolver);
            }
        }
    }

    private static void applyFromItem(PlainSelect plainSelect,
                                      FromItem fromItem,
                                      Function<String, TableScopeConfig> configResolver) {
        if (fromItem instanceof Table table) {
            String rawName = table.getName();
            if (rawName == null || rawName.isBlank()) {
                return;
            }
            String normalized = rawName.replace("`", "").trim().toLowerCase(Locale.ROOT);
            TableScopeConfig cfg = configResolver.apply(normalized);
            if (cfg == null || cfg.spec() == null) {
                return;
            }
            Expression guardExpr = cfg.spec().toSqlExpression(table, cfg.orgColumn(), cfg.unitColumn(), cfg.userColumn());
            if (guardExpr == null) {
                return;
            }
            Expression wrappedGuard = guardExpr instanceof Parenthesis ? guardExpr : new Parenthesis(guardExpr);
            Expression existing = plainSelect.getWhere();
            if (existing == null) {
                plainSelect.setWhere(guardExpr);
            } else {
                Expression wrappedExisting = existing instanceof Parenthesis ? existing : new Parenthesis(existing);
                plainSelect.setWhere(new AndExpression(wrappedExisting, wrappedGuard));
            }
        } else if (fromItem instanceof ParenthesedSelect subSelect && subSelect.getSelect() != null) {
            rewriteSelect(subSelect.getSelect(), configResolver);
        }
    }
}
