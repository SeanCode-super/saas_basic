package com.saasbasics.platform.modules.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasbasics.platform.modules.ai.domain.Nl2SqlSecurityValidator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReportQueryTemplatesTest {
    @Test
    void shippedReportPlansPassThroughTheTenantAwareReadOnlyValidator() {
        var allowed = Set.of("crm_receivable", "crm_contract", "crm_business", "crm_clue", "erp_product",
                "erp_inventory", "erp_purchase_order", "erp_sale_order", "sys_export_task", "audit_operation_log",
                "iam_user", "crm_customer");
        var reports = List.of("receivablesByStatus", "receivables", "contractsByStatus", "contracts",
                "dealsByStage", "deals", "leadsBySource", "leads", "stockByProduct", "products",
                "purchaseOrders", "salesOrders", "exportTasks", "auditByModule", "auditEvents", "users",
                "customersByIndustry");
        for (String report : reports) {
            String sql = ReportQueryTemplates.render(report, 83L, 17);
            assertThat(sql).as(report).contains("83", "LIMIT 17").doesNotContain("{", "}");
            assertThat(Nl2SqlSecurityValidator.validateAndNormalize(sql, allowed, 83L).referencedTables()).isNotEmpty();
        }
        for (boolean pool : List.of(true, false)) {
            for (boolean levelA : List.of(true, false)) {
                String sql = ReportQueryTemplates.customers(83L, 17, pool, levelA);
                assertThat(sql.contains("AND is_pool = 1")).isEqualTo(pool);
                assertThat(sql.contains("AND level = 'A'")).isEqualTo(levelA);
                assertThat(Nl2SqlSecurityValidator.validateAndNormalize(sql, allowed, 83L).referencedTables())
                        .containsExactly("crm_customer");
            }
        }
    }
}
