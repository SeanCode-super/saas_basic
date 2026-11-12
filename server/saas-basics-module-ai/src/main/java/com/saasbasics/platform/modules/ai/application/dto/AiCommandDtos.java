package com.saasbasics.platform.modules.ai.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI Command Router, NL2SQL & History DTOs.
 */
public final class AiCommandDtos {

    private AiCommandDtos() {
    }

    public record AiCommandRequest(
            String input,
            String currentPath,
            String modelId,
            Boolean executeNow
    ) {}

    public record AiCommandResponse(
            String intentCode,
            String intentLabel,
            double confidence,
            String explanation,
            boolean requiresConfirmation,
            String suggestedRoute,
            Map<String, Object> actionPayload,
            Nl2SqlQueryResponse nl2sqlResult
    ) {}

    public record Nl2SqlSchemaColumnDTO(
            String columnCode,
            String columnLabel,
            String dataType,
            boolean sensitive
    ) {}

    public record Nl2SqlSchemaTableDTO(
            String tableName,
            String tableLabel,
            String domain,
            boolean hasDeletedColumn,
            List<Nl2SqlSchemaColumnDTO> columns
    ) {}

    public record Nl2SqlQueryRequest(
            String question,
            String modelId,
            Integer maxRows
    ) {}

    public record Nl2SqlQueryResponse(
            String question,
            List<String> tableNames,
            String sql,
            List<String> columns,
            Map<String, String> columnLabels,
            List<Map<String, Object>> rows,
            int rowCount,
            String recommendedChartType,
            String chartTitle,
            String chartXAxis,
            String chartSeriesField,
            List<String> insights,
            List<String> followUpSuggestions,
            List<String> maskedFields,
            String executionEngine,
            long latencyMs
    ) {}

    public record AiCommandHistoryDTO(
            Long id,
            String rawCommand,
            String intentCode,
            double confidence,
            String generatedSql,
            String resultSummary,
            int rowCount,
            long latencyMs,
            String status,
            LocalDateTime createdAt
    ) {}
}
