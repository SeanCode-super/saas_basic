package com.saasbasics.platform.modules.audit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasbasics.platform.modules.audit.dto.OperationLogDiffResponse.FieldDiffItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditDiffServiceTest {

    @Test
    void computesFieldLevelAddedRemovedAndModifiedDiffs() {
        String before = "{\"minLength\":8,\"forceMfa\":false,\"legacyFlag\":\"ON\",\"nested\":{\"timeout\":30}}";
        String after = "{\"minLength\":12,\"forceMfa\":true,\"nested\":{\"timeout\":30},\"newRule\":\"STRICT\"}";

        List<FieldDiffItem> diffs = AuditService.computeJsonDiff(before, after);

        assertThat(diffs).extracting(FieldDiffItem::fieldPath)
                .containsExactlyInAnyOrder("minLength", "forceMfa", "legacyFlag", "newRule");

        assertThat(diffs.stream().filter(d -> "minLength".equals(d.fieldPath())).findFirst().orElseThrow().changeType())
                .isEqualTo("MODIFIED");
        assertThat(diffs.stream().filter(d -> "legacyFlag".equals(d.fieldPath())).findFirst().orElseThrow().changeType())
                .isEqualTo("REMOVED");
        assertThat(diffs.stream().filter(d -> "newRule".equals(d.fieldPath())).findFirst().orElseThrow().changeType())
                .isEqualTo("ADDED");
    }
}
