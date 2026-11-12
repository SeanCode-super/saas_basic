package com.saasbasics.platform.modules.ai.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class Nl2SqlSecurityValidatorTest {

    private static final Set<String> ALLOWED = Set.of("crm_customer", "crm_contract", "erp_product");

    @Test
    void acceptsValidTenantScopedSelectAndCapsLimit() {
        String raw = "SELECT name, total_price FROM crm_contract WHERE tenant_id = 1 AND deleted = 0 LIMIT 999";
        Nl2SqlSecurityValidator.ValidatedSql res = Nl2SqlSecurityValidator.validateAndNormalize(raw, ALLOWED, 1L);

        assertEquals(200, res.appliedLimit());
        assertTrue(res.sanitizedSql().endsWith("LIMIT 200"));
        assertEquals(Set.of("crm_contract"), Set.copyOf(res.referencedTables()));
    }

    @Test
    void rejectsForbiddenKeywordsCrossTenantAndUnknownTables() {
        assertThrows(SecurityException.class, () ->
                Nl2SqlSecurityValidator.validateAndNormalize(
                        "DELETE FROM crm_customer WHERE tenant_id = 1", ALLOWED, 1L));

        assertThrows(SecurityException.class, () ->
                Nl2SqlSecurityValidator.validateAndNormalize(
                        "SELECT * FROM crm_customer WHERE tenant_id = 2", ALLOWED, 1L));

        assertThrows(SecurityException.class, () ->
                Nl2SqlSecurityValidator.validateAndNormalize(
                        "SELECT * FROM iam_user WHERE tenant_id = 1", ALLOWED, 1L));

        assertThrows(SecurityException.class, () ->
                Nl2SqlSecurityValidator.validateAndNormalize(
                        "SELECT * FROM crm_customer WHERE tenant_id = 1; DROP TABLE crm_customer", ALLOWED, 1L));

        assertThrows(SecurityException.class, () ->
                Nl2SqlSecurityValidator.validateAndNormalize(
                        "SELECT * FROM crm_customer WHERE tenant_id = 1 /* comment */", ALLOWED, 1L));
    }

    @Test
    void injectsRowLevelOwnerScopeForNonAdminQueries() {
        String raw = "SELECT name, level FROM crm_customer WHERE tenant_id = 1 AND deleted = 0 LIMIT 20";
        Nl2SqlSecurityValidator.ValidatedSql res = Nl2SqlSecurityValidator.validateAndNormalize(raw, ALLOWED, 1L);
        assertTrue(Nl2SqlSecurityValidator.containsOwnerScopedTable(res.referencedTables()));

        String scoped = Nl2SqlSecurityValidator.applyOwnerScopePredicate(res.sanitizedSql(), 1L, Set.of(42L));
        assertTrue(scoped.contains("owner_user_id IN (42)"));
        assertTrue(scoped.contains("FROM (SELECT * FROM `crm_customer` WHERE tenant_id = 1 AND deleted = 0 AND owner_user_id IN (42)) crm_customer"));
    }
}
