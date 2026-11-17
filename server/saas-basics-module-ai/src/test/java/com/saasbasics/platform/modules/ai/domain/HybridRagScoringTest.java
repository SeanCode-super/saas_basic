package com.saasbasics.platform.modules.ai.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saasbasics.platform.modules.ai.domain.HybridRagScoring.ChunkCandidate;
import com.saasbasics.platform.modules.ai.domain.HybridRagScoring.RankedChunk;
import com.saasbasics.platform.modules.ai.domain.HybridRagScoring.TextChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class HybridRagScoringTest {

    @Test
    void splitsMarkdownIntoHeadingScopedChunks() {
        String text = """
                # 合同付款条件
                单笔商务合同首付款比例不得低于 30%，尾款账期不超过 45 个工作日。
                # 违约责任条款
                我方承担的累计违约责任赔偿总额不得超过合同已收款总额的 100%。
                """;
        List<TextChunk> chunks = HybridRagScoring.splitIntoChunks(text, 240, 40);
        assertFalse(chunks.isEmpty());
        assertEquals("合同付款条件", chunks.get(0).sectionTitle());
        assertTrue(chunks.stream().anyMatch(c -> c.content().contains("100%")));
    }

    @Test
    void ranksMatchingComplianceClauseFirstViaHybridRrf() {
        List<ChunkCandidate> candidates = List.of(
                new ChunkCandidate(1L, 10L, "员工考勤手册.md", 0, 1, "休假", "年假需提前三个工作日在线提交审批。", null),
                new ChunkCandidate(2L, 11L, "合同红线规范.md", 0, 1, "首付款与账期", "单笔合同首付款不得低于 30%，尾款账期不超过 45 天。", null),
                new ChunkCandidate(3L, 12L, "采购标准.md", 0, 2, "供应商", "超过 10 万元订单需三方比价。", null)
        );
        List<RankedChunk> top = HybridRagScoring.rankHybrid("合同首付款最低比例和尾款账期红线是多少？", candidates, 2);
        assertEquals(2, top.size());
        assertEquals(2L, top.get(0).chunk().chunkId());
        assertTrue(top.get(0).rrfScore() >= top.get(1).rrfScore());
    }
}
