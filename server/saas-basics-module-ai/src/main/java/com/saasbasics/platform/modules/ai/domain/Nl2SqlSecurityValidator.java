package com.saasbasics.platform.modules.ai.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure domain NL2SQL security validator enforcing read-only SELECT queries,
 * table whitelist checks, tenant isolation, and row limits.
 * <p>
 * Must remain free of Spring/MyBatis/Infrastructure dependencies per ArchUnit boundary rules.
 */
public final class Nl2SqlSecurityValidator {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private static final Pattern SQL_COMMENT = Pattern.compile("(--[^\\r\\n]*|/\\*[\\s\\S]*?\\*/)");
    private static final Pattern STRING_LITERAL = Pattern.compile("'([^'\\\\]|\\\\.)*'");
    private static final Pattern FORBIDDEN_KEYWORDS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|REPLACE|GRANT|REVOKE|MERGE|CALL|"
                    + "EXEC|EXECUTE|SHOW|DESCRIBE|EXPLAIN|SET|USE|LOCK|UNLOCK|LOAD|INTO\\s+OUTFILE|"
                    + "INTO\\s+DUMPFILE|UNION|SLEEP|BENCHMARK|INFORMATION_SCHEMA|MYSQL|SYS|PERFORMANCE_SCHEMA)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TABLE_REF = Pattern.compile(
            "\\b(?:FROM|JOIN)\\s+`?([a-zA-Z0-9_]+)`?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LIMIT_PATTERN = Pattern.compile(
            "\\bLIMIT\\s+(\\d+)(?:\\s*,\\s*(\\d+))?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TENANT_EQ_PATTERN = Pattern.compile(
            "\\b(?:[a-zA-Z0-9_]+\\.)?tenant_id\\s*=\\s*(\\d+)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TENANT_OR_DISJUNCTION_PATTERN = Pattern.compile(
            "(\\b(?:[a-zA-Z0-9_]+\\.)?tenant_id\\s*=\\s*\\d+\\s*\\)?\\s+OR\\b|\\bOR\\s+\\(?\\s*(?:[a-zA-Z0-9_]+\\.)?tenant_id\\s*=\\s*\\d+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> OWNER_SCOPED_TABLES = Set.of("crm_customer", "crm_clue", "crm_business");
    private static final Pattern OWNER_TABLE_REWRITE = Pattern.compile(
            "\\b(FROM|JOIN)\\s+`?(crm_customer|crm_clue|crm_business)`?(?=(\\s+(?:WHERE|GROUP|ORDER|LIMIT|HAVING|JOIN|LEFT|RIGHT|INNER|CROSS|ON)\\b|\\s*$))",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OWNER_ALIASED_REWRITE = Pattern.compile(
            "\\b(FROM|JOIN)\\s+`?(crm_customer|crm_clue|crm_business)`?\\s+(?!(?:WHERE|GROUP|ORDER|LIMIT|HAVING|JOIN|LEFT|RIGHT|INNER|CROSS|ON)\\b)([a-zA-Z][a-zA-Z0-9_]*)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public record ValidatedSql(
            String sanitizedSql,
            List<String> referencedTables,
            int appliedLimit
    ) {}

    private Nl2SqlSecurityValidator() {
    }

    public static ValidatedSql validateAndNormalize(String rawSql, Set<String> allowedTables, long requiredTenantId) {
        if (rawSql == null || rawSql.isBlank()) {
            throw new IllegalArgumentException("SQL statement must not be empty");
        }
        if (allowedTables == null || allowedTables.isEmpty()) {
            throw new IllegalArgumentException("Schema whitelist must not be empty");
        }

        // Strip trailing semicolons cleanly and reject SQL comments
        String cleaned = rawSql.trim();
        while (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        if (SQL_COMMENT.matcher(cleaned).find()) {
            throw new SecurityException("SQL comments (-- or /* */) are strictly prohibited");
        }

        // Reject string-stripped multi-statements or dangerous statements
        String withoutStrings = STRING_LITERAL.matcher(cleaned).replaceAll("''");
        if (withoutStrings.contains(";")) {
            throw new SecurityException("Multiple SQL statements are prohibited");
        }

        String upper = withoutStrings.toUpperCase(Locale.ROOT).trim();
        if (!upper.startsWith("SELECT ")) {
            throw new SecurityException("Only single SELECT statements are allowed");
        }

        Matcher forbidden = FORBIDDEN_KEYWORDS.matcher(withoutStrings);
        if (forbidden.find()) {
            throw new SecurityException("Forbidden SQL keyword or schema detected: " + forbidden.group(1));
        }

        // Verify all referenced tables belong to the whitelist
        Matcher tableMatcher = TABLE_REF.matcher(withoutStrings);
        Set<String> referencedTables = new LinkedHashSet<>();
        while (tableMatcher.find()) {
            String tbl = tableMatcher.group(1).toLowerCase(Locale.ROOT);
            if (!allowedTables.contains(tbl)) {
                throw new SecurityException("Table is not in allowed query catalog: " + tbl);
            }
            referencedTables.add(tbl);
        }
        if (referencedTables.isEmpty()) {
            throw new SecurityException("SELECT statement must query at least one whitelisted table");
        }

        // Reject OR disjunction directly attached to tenant_id to prevent tenant isolation bypass
        if (TENANT_OR_DISJUNCTION_PATTERN.matcher(withoutStrings).find()) {
            throw new SecurityException("Disjunction (OR) involving tenant_id predicate is strictly forbidden to prevent tenant isolation bypass");
        }

        // Enforce explicit matching tenant_id predicate on every non-empty query
        Matcher tenantMatcher = TENANT_EQ_PATTERN.matcher(withoutStrings);
        boolean foundMatchingTenant = false;
        while (tenantMatcher.find()) {
            long parsedTenant = Long.parseLong(tenantMatcher.group(1));
            if (parsedTenant != requiredTenantId) {
                throw new SecurityException("Cross-tenant access denied: tenant_id mismatch");
            }
            foundMatchingTenant = true;
        }
        if (!foundMatchingTenant) {
            throw new SecurityException("Query must explicitly scope by tenant_id = " + requiredTenantId);
        }

        // Normalize LIMIT <= MAX_LIMIT
        String normalizedSql = cleaned;
        int limitValue = DEFAULT_LIMIT;
        Matcher limitMatcher = LIMIT_PATTERN.matcher(withoutStrings);
        if (limitMatcher.find()) {
            String secondNum = limitMatcher.group(2);
            int parsed = Integer.parseInt(secondNum != null ? secondNum : limitMatcher.group(1));
            limitValue = Math.min(Math.max(1, parsed), MAX_LIMIT);
            if (parsed > MAX_LIMIT) {
                normalizedSql = LIMIT_PATTERN.matcher(normalizedSql).replaceFirst("LIMIT " + MAX_LIMIT);
            }
        } else {
            normalizedSql = normalizedSql + " LIMIT " + DEFAULT_LIMIT;
        }

        return new ValidatedSql(normalizedSql, new ArrayList<>(referencedTables), limitValue);
    }

    /**
     * Wraps owner_user_id-scoped CRM tables with a derived tenant + owner filter subquery so non-admin
     * users cannot read peer owners' records across ANY aggregate, join, or projection.
     */
    public static String applyOwnerScopePredicate(String sanitizedSql, long requiredTenantId, Set<Long> ownerUserIds) {
        if (sanitizedSql == null || sanitizedSql.isBlank() || ownerUserIds == null || ownerUserIds.isEmpty()) {
            return sanitizedSql;
        }
        String rawIds = ownerUserIds.stream()
                .filter(id -> id != null && id > 0)
                .map(String::valueOf)
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
        final String idsCsv = rawIds.isEmpty() ? "-1" : rawIds;
        String out = OWNER_TABLE_REWRITE.matcher(sanitizedSql).replaceAll(m ->
                m.group(1) + " (SELECT * FROM `" + m.group(2).toLowerCase(Locale.ROOT)
                        + "` WHERE tenant_id = " + requiredTenantId + " AND deleted = 0 AND owner_user_id IN ("
                        + idsCsv + ")) " + m.group(2).toLowerCase(Locale.ROOT));
        out = OWNER_ALIASED_REWRITE.matcher(out).replaceAll(m ->
                m.group(1) + " (SELECT * FROM `" + m.group(2).toLowerCase(Locale.ROOT)
                        + "` WHERE tenant_id = " + requiredTenantId + " AND deleted = 0 AND owner_user_id IN ("
                        + idsCsv + ")) " + m.group(3));
        return out;
    }

    public static boolean containsOwnerScopedTable(List<String> tables) {
        return tables != null && tables.stream().anyMatch(OWNER_SCOPED_TABLES::contains);
    }
}
