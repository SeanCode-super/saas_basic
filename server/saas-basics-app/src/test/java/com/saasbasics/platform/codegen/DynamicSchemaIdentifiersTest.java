package com.saasbasics.platform.codegen;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.codegen.domain.DynamicSchemaIdentifiers;
import java.util.List;
import org.junit.jupiter.api.Test;

class DynamicSchemaIdentifiersTest {
    @Test
    void businessColumnsAndPhysicalTablesHaveDistinctBoundaries() {
        assertThatNoException().isThrownBy(() -> DynamicSchemaIdentifiers.requireBusinessField("part_note"));
        for (String owned : List.of("id", "tenant_id", "dept_id", "created_at", "created_by",
                "updated_at", "updated_by", "deleted", "deleted_at", "version")) {
            assertThatThrownBy(() -> DynamicSchemaIdentifiers.requireBusinessField(owned))
                    .isInstanceOf(BizException.class);
        }
        assertThatNoException().isThrownBy(() -> DynamicSchemaIdentifiers.requireTable(16L, "biz_dyn_t16_orders"));
        assertThatNoException().isThrownBy(() -> DynamicSchemaIdentifiers.requireTable(16L, "biz_dyn_shared_orders"));
        assertThatThrownBy(() -> DynamicSchemaIdentifiers.requireTable(16L, "biz_dyn_t17_orders"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> DynamicSchemaIdentifiers.requireTable(16L, "crm_customer"))
                .isInstanceOf(BizException.class);
    }
}
