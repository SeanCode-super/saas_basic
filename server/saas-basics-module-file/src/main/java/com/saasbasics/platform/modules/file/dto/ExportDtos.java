package com.saasbasics.platform.modules.file.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Enterprise Data Export, Approval, Scheduled Report & Download Center DTOs.
 */
public final class ExportDtos {

    private ExportDtos() {
    }

    public record ExportFieldSpec(
            String fieldCode,
            String fieldLabel,
            boolean sensitive,
            String dataType
    ) {}

    public record ExportDatasetInfoDTO(
            String sourceCode,
            String sourceName,
            String domain,
            List<ExportFieldSpec> fields
    ) {}

    public record ExportCreateRequest(
            String taskName,
            String sourceType,
            String sourceCode,
            String exportFormat,
            List<String> selectedFields,
            Map<String, Object> filters,
            List<String> adHocColumns,
            Map<String, String> adHocColumnLabels,
            List<Map<String, Object>> adHocRows,
            List<String> insights,
            Boolean includeWatermark,
            Boolean desensitizeEnabled,
            Boolean async,
            Boolean requireApproval
    ) {}

    public record ExportTaskDTO(
            Long id,
            String taskNo,
            String taskName,
            String sourceType,
            String sourceCode,
            String exportFormat,
            String traceCode,
            String watermarkText,
            boolean desensitized,
            long totalRows,
            long processedRows,
            int progressPercent,
            long fileSize,
            String fileSha256,
            String status,
            String approvalStatus,
            String approvedByName,
            LocalDateTime approvedAt,
            String approveReason,
            String errorMessage,
            LocalDateTime expireAt,
            String operatorName,
            LocalDateTime createdAt
    ) {}

    public record ExportApprovalRequest(
            String reason
    ) {}

    public record ExportStatsDTO(
            long totalExports,
            long completedExports,
            long pendingApprovals,
            long totalRowsExported,
            long totalFileBytes,
            long desensitizedExports,
            long activeTemplates,
            long activeSchedules
    ) {}

    public record ExportTemplateDTO(
            Long id,
            String templateCode,
            String templateName,
            String sourceCode,
            List<String> fields,
            String defaultFormat,
            boolean includeWatermark,
            boolean desensitizeEnabled,
            String remark
    ) {}

    public record ExportTemplateSaveRequest(
            Long id,
            String templateCode,
            String templateName,
            String sourceCode,
            List<String> fields,
            String defaultFormat,
            Boolean includeWatermark,
            Boolean desensitizeEnabled,
            String remark
    ) {}

    public record ReportScheduleDTO(
            Long id,
            String scheduleCode,
            String scheduleName,
            String sourceCode,
            String exportFormat,
            List<String> fields,
            String frequency,
            String cronExpr,
            int runHour,
            int runMinute,
            boolean includeWatermark,
            boolean desensitizeEnabled,
            String recipientUserIds,
            LocalDateTime lastRunAt,
            LocalDateTime nextRunAt,
            Long lastTaskId,
            String status,
            String remark
    ) {}

    public record ReportScheduleSaveRequest(
            Long id,
            String scheduleCode,
            String scheduleName,
            String sourceCode,
            String exportFormat,
            List<String> fields,
            String frequency,
            Integer runHour,
            Integer runMinute,
            Boolean includeWatermark,
            Boolean desensitizeEnabled,
            String recipientUserIds,
            String status,
            String remark
    ) {}
}
