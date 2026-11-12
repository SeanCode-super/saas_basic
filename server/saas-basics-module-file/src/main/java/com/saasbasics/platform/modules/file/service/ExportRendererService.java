package com.saasbasics.platform.modules.file.service;

import com.saasbasics.platform.common.mask.DataMaskUtils;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportFieldSpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Multi-format enterprise export renderer:
 * - XLSX via SXSSFWorkbook(500) sliding window (OOM-safe for large exports)
 *   with security banner, print watermark, OOXML custom trace properties & hidden trace sheet
 * - CSV via UTF-8 BOM + RFC 4180 streaming printer
 * - HTML_REPORT printable A4 AI executive analytics report with 45-degree repeating watermark
 */
@Service
public class ExportRendererService {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record RenderOutput(
            byte[] bytes,
            String fileExtension,
            String contentType,
            String sha256
    ) {}

    public RenderOutput render(
            String title,
            String format,
            List<ExportFieldSpec> columns,
            List<Map<String, Object>> rawRows,
            List<String> insights,
            boolean desensitizeEnabled,
            boolean includeWatermark,
            String watermarkText,
            String traceCode,
            String operatorName,
            Long tenantId
    ) throws IOException {
        List<Map<String, Object>> rows = applyDesensitization(columns, rawRows, desensitizeEnabled);
        String fmt = format == null ? "XLSX" : format.trim().toUpperCase(Locale.ROOT);
        byte[] bytes;
        String ext;
        String contentType;
        switch (fmt) {
            case "CSV" -> {
                bytes = renderCsv(columns, rows, includeWatermark, watermarkText, traceCode);
                ext = "csv";
                contentType = "text/csv;charset=UTF-8";
            }
            case "HTML_REPORT", "REPORT", "PDF" -> {
                bytes = renderHtmlReport(title, columns, rows, insights, includeWatermark, watermarkText, traceCode, operatorName);
                ext = "html";
                contentType = "text/html;charset=UTF-8";
            }
            default -> {
                bytes = renderXlsx(title, columns, rows, includeWatermark, watermarkText, traceCode, operatorName, tenantId);
                ext = "xlsx";
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            }
        }
        return new RenderOutput(bytes, ext, contentType, sha256Hex(bytes));
    }

    private List<Map<String, Object>> applyDesensitization(
            List<ExportFieldSpec> columns,
            List<Map<String, Object>> rows,
            boolean desensitizeEnabled
    ) {
        if (!desensitizeEnabled || rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : rows;
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            for (ExportFieldSpec spec : columns) {
                if (!spec.sensitive() && !ExportDatasetCatalogService.isSensitiveField(spec.fieldCode())) {
                    continue;
                }
                Object val = copy.get(spec.fieldCode());
                if (val instanceof String s && !s.isBlank()) {
                    copy.put(spec.fieldCode(), maskByFieldName(spec.fieldCode(), s));
                }
            }
            out.add(copy);
        }
        return out;
    }

    private static String maskByFieldName(String colName, String value) {
        String lower = colName.toLowerCase(Locale.ROOT);
        if (lower.contains("email")) {
            return DataMaskUtils.maskEmail(value);
        }
        if (lower.contains("id_card")) {
            return DataMaskUtils.maskIdCard(value);
        }
        if (lower.contains("bank_card")) {
            return DataMaskUtils.maskBankCard(value);
        }
        return DataMaskUtils.maskMobile(value);
    }

    private byte[] renderXlsx(
            String title,
            List<ExportFieldSpec> columns,
            List<Map<String, Object>> rows,
            boolean includeWatermark,
            String watermarkText,
            String traceCode,
            String operatorName,
            Long tenantId
    ) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(500);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            wb.setCompressTempFiles(true);

            SXSSFSheet sheet = wb.createSheet(sanitizeSheetName(title));
            sheet.trackAllColumnsForAutoSizing();

            CellStyle bannerStyle = createBannerStyle(wb);
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle bodyStyle = createBodyStyle(wb);

            int rowIdx = 0;
            if (includeWatermark) {
                Row bannerRow = sheet.createRow(rowIdx++);
                bannerRow.setHeightInPoints(22f);
                Cell bannerCell = bannerRow.createCell(0);
                bannerCell.setCellValue("🔒 安全追溯水印：" + watermarkText + " | 内部数据请勿外传");
                bannerCell.setCellStyle(bannerStyle);

                sheet.getHeader().setCenter("&C&9&\"-\",Italic " + watermarkText);
                sheet.getFooter().setRight("Trace: " + traceCode + " | Page &P / &N");
            }

            Row headerRow = sheet.createRow(rowIdx++);
            headerRow.setHeightInPoints(26f);
            for (int c = 0; c < columns.size(); c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(columns.get(c).fieldLabel());
                cell.setCellStyle(headerStyle);
            }

            for (Map<String, Object> record : rows) {
                Row r = sheet.createRow(rowIdx++);
                r.setHeightInPoints(20f);
                for (int c = 0; c < columns.size(); c++) {
                    Cell cell = r.createCell(c);
                    Object v = record.get(columns.get(c).fieldCode());
                    writeTypedCellValue(cell, v);
                    cell.setCellStyle(bodyStyle);
                }
            }

            for (int c = 0; c < columns.size(); c++) {
                sheet.autoSizeColumn(c);
                int curWidth = sheet.getColumnWidth(c);
                sheet.setColumnWidth(c, Math.min(Math.max(curWidth + 1024, 3800), 14000));
            }

            // Embedded steganographic & audit trace sheet (very hidden)
            SXSSFSheet traceSheet = wb.createSheet("_Export_Trace_Audit");
            Row tr0 = traceSheet.createRow(0);
            tr0.createCell(0).setCellValue("TraceCode");
            tr0.createCell(1).setCellValue(traceCode);
            Row tr1 = traceSheet.createRow(1);
            tr1.createCell(0).setCellValue("Operator");
            tr1.createCell(1).setCellValue(operatorName);
            Row tr2 = traceSheet.createRow(2);
            tr2.createCell(0).setCellValue("TenantId");
            tr2.createCell(1).setCellValue(String.valueOf(tenantId));
            Row tr3 = traceSheet.createRow(3);
            tr3.createCell(0).setCellValue("ExportTime");
            tr3.createCell(1).setCellValue(LocalDateTime.now().format(TS_FMT));
            int traceIdx = wb.getSheetIndex(traceSheet);
            wb.setSheetVisibility(traceIdx, SheetVisibility.VERY_HIDDEN);

            try {
                var customProps = wb.getXSSFWorkbook().getProperties().getCustomProperties();
                customProps.addProperty("TianshuTraceCode", traceCode);
                customProps.addProperty("TianshuOperator", operatorName);
                customProps.addProperty("TianshuTenantId", String.valueOf(tenantId));
            } catch (Exception ignored) {
            }

            wb.write(baos);
            wb.dispose();
            return baos.toByteArray();
        }
    }

    private byte[] renderCsv(
            List<ExportFieldSpec> columns,
            List<Map<String, Object>> rows,
            boolean includeWatermark,
            String watermarkText,
            String traceCode
    ) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            baos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // UTF-8 BOM for Excel
            CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setCommentMarker('#').build();
            try (OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
                 CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
                if (includeWatermark) {
                    printer.printComment("Watermark: " + watermarkText + " | TraceCode: " + traceCode);
                }
                String[] headers = columns.stream().map(ExportFieldSpec::fieldLabel).toArray(String[]::new);
                printer.printRecord((Object[]) headers);
                for (Map<String, Object> row : rows) {
                    Object[] vals = new Object[columns.size()];
                    for (int i = 0; i < columns.size(); i++) {
                        Object v = row.get(columns.get(i).fieldCode());
                        vals[i] = v == null ? "" : String.valueOf(v);
                    }
                    printer.printRecord(vals);
                }
                printer.flush();
            }
            return baos.toByteArray();
        }
    }

    private byte[] renderHtmlReport(
            String title,
            List<ExportFieldSpec> columns,
            List<Map<String, Object>> rows,
            List<String> insights,
            boolean includeWatermark,
            String watermarkText,
            String traceCode,
            String operatorName
    ) {
        String svgWatermarkDataUri = buildSvgWatermarkDataUri(includeWatermark ? watermarkText : "");
        String numericSummaryChart = buildInlineBarChart(columns, rows);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
                .append("<title>").append(escHtml(title)).append("</title>")
                .append("<style>")
                .append("@page { size: A4 portrait; margin: 16mm; }")
                .append("body { font-family: -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif; color: #0f172a; margin: 0; padding: 28px; background: #f8fafc; }")
                .append(".report-sheet { position: relative; max-width: 960px; margin: 0 auto; background: #fff; padding: 36px; border-radius: 14px; box-shadow: 0 12px 32px rgba(15,23,42,.08); overflow: hidden; }")
                .append(includeWatermark ? ".report-watermark { position: fixed; inset: 0; pointer-events: none; background-image: url('" + svgWatermarkDataUri + "'); background-repeat: repeat; z-index: 9999; }" : "")
                .append(".header { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 2px solid #1e3a8a; padding-bottom: 16px; margin-bottom: 20px; }")
                .append(".header h1 { margin: 0 0 6px; font-size: 22px; color: #0f172a; }")
                .append(".meta { font-size: 12px; color: #64748b; }")
                .append(".badge { display: inline-block; padding: 4px 10px; border-radius: 999px; background: #eff6ff; color: #1d4ed8; font-size: 12px; font-weight: 600; }")
                .append(".kpi-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 20px; }")
                .append(".kpi { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 14px; }")
                .append(".kpi-label { font-size: 12px; color: #64748b; } .kpi-val { font-size: 20px; font-weight: 700; color: #1e293b; margin-top: 4px; }")
                .append(".insights { background: linear-gradient(135deg,#f0f9ff,#f8fafc); border-left: 4px solid #2563eb; padding: 14px 18px; border-radius: 8px; margin-bottom: 20px; }")
                .append(".insights h3 { margin: 0 0 8px; font-size: 14px; color: #1e3a8a; } .insights ul { margin: 0; padding-left: 18px; font-size: 13px; line-height: 1.7; }")
                .append(".chart-box { border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px; margin-bottom: 20px; }")
                .append("table { width: 100%; border-collapse: collapse; font-size: 12px; }")
                .append("th { background: #1e3a8a; color: #fff; text-align: left; padding: 9px 10px; font-weight: 600; }")
                .append("td { padding: 8px 10px; border-bottom: 1px solid #e2e8f0; }")
                .append("tr:nth-child(even) td { background: #f8fafc; }")
                .append(".footer { margin-top: 24px; padding-top: 12px; border-top: 1px solid #e2e8f0; font-size: 11px; color: #64748b; display: flex; justify-content: space-between; }")
                .append("@media print { body { background: #fff; padding: 0; } .report-sheet { box-shadow: none; padding: 0; } .no-print { display: none; } }")
                .append("</style></head><body>");

        if (includeWatermark) {
            html.append("<div class=\"report-watermark\"></div>");
        }

        html.append("<div class=\"report-sheet\">")
                .append("<div class=\"header\"><div>")
                .append("<div class=\"badge\">天枢云基 · 智能数据洞察报告</div>")
                .append("<h1>").append(escHtml(title)).append("</h1>")
                .append("<div class=\"meta\">生成时间：").append(LocalDateTime.now().format(TS_FMT))
                .append(" · 导出人：").append(escHtml(operatorName))
                .append(" · 追溯编号：<code>").append(escHtml(traceCode)).append("</code></div></div>")
                .append("<button class=\"no-print\" onclick=\"window.print()\" style=\"padding:8px 14px;background:#2563eb;color:#fff;border:none;border-radius:8px;cursor:pointer;\">打印 / 另存为 PDF</button>")
                .append("</div>");

        html.append("<div class=\"kpi-grid\">")
                .append("<div class=\"kpi\"><div class=\"kpi-label\">数据总行数</div><div class=\"kpi-val\">").append(rows.size()).append(" 条</div></div>")
                .append("<div class=\"kpi\"><div class=\"kpi-label\">导出维度字段</div><div class=\"kpi-val\">").append(columns.size()).append(" 列</div></div>")
                .append("<div class=\"kpi\"><div class=\"kpi-label\">数据防泄露保护</div><div class=\"kpi-val\">")
                .append(includeWatermark ? "水印 + 脱敏已激活" : "标准导出").append("</div></div>")
                .append("</div>");

        if (insights != null && !insights.isEmpty()) {
            html.append("<div class=\"insights\"><h3>💡 AI 智能洞察与结论摘要</h3><ul>");
            for (String item : insights) {
                html.append("<li>").append(escHtml(item)).append("</li>");
            }
            html.append("</ul></div>");
        }

        if (!numericSummaryChart.isEmpty()) {
            html.append(numericSummaryChart);
        }

        html.append("<table><thead><tr>");
        for (ExportFieldSpec col : columns) {
            html.append("<th>").append(escHtml(col.fieldLabel())).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (Map<String, Object> r : rows) {
            html.append("<tr>");
            for (ExportFieldSpec col : columns) {
                Object val = r.get(col.fieldCode());
                html.append("<td>").append(escHtml(val == null ? "" : String.valueOf(val))).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table>");

        html.append("<div class=\"footer\">")
                .append("<span>追溯水印：").append(escHtml(watermarkText)).append("</span>")
                .append("<span>由天枢云基 Enterprise Export Engine 自动生成</span>")
                .append("</div></div></body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String buildInlineBarChart(List<ExportFieldSpec> columns, List<Map<String, Object>> rows) {
        if (rows.isEmpty() || columns.size() < 2) {
            return "";
        }
        String labelCol = columns.get(0).fieldCode();
        String numCol = null;
        String numLabel = "";
        for (ExportFieldSpec col : columns) {
            if (!col.fieldCode().equals(labelCol) && rows.get(0).get(col.fieldCode()) instanceof Number) {
                numCol = col.fieldCode();
                numLabel = col.fieldLabel();
                break;
            }
        }
        if (numCol == null) {
            return "";
        }
        double maxVal = 1.0;
        int limit = Math.min(rows.size(), 10);
        for (int i = 0; i < limit; i++) {
            Object v = rows.get(i).get(numCol);
            if (v instanceof Number n) {
                maxVal = Math.max(maxVal, n.doubleValue());
            }
        }
        StringBuilder sb = new StringBuilder("<div class=\"chart-box\"><div style=\"font-size:13px;font-weight:600;margin-bottom:10px;color:#1e3a8a;\">📊 指标可视化分析（")
                .append(escHtml(numLabel)).append(" · Top ").append(limit).append("）</div>");
        for (int i = 0; i < limit; i++) {
            Map<String, Object> r = rows.get(i);
            String dim = String.valueOf(r.getOrDefault(labelCol, "-"));
            double val = r.get(numCol) instanceof Number n ? n.doubleValue() : 0.0;
            int pct = (int) Math.min(100, Math.max(2, Math.round((val / maxVal) * 100)));
            sb.append("<div style=\"display:flex;align-items:center;gap:10px;margin:6px 0;font-size:12px;\">")
                    .append("<div style=\"width:150px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;color:#334155;\">")
                    .append(escHtml(dim)).append("</div>")
                    .append("<div style=\"flex:1;background:#f1f5f9;border-radius:4px;height:14px;overflow:hidden;\">")
                    .append("<div style=\"width:").append(pct).append("%;height:100%;background:linear-gradient(90deg,#2563eb,#0ea5e9);\"></div></div>")
                    .append("<div style=\"width:90px;text-align:right;font-variant-numeric:tabular-nums;color:#0f172a;font-weight:600;\">")
                    .append(BigDecimal.valueOf(val).stripTrailingZeros().toPlainString()).append("</div></div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildSvgWatermarkDataUri(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='320' height='200'>"
                + "<text x='160' y='100' fill='rgba(100,116,139,0.14)' font-size='13' font-family='sans-serif' "
                + "text-anchor='middle' transform='rotate(-28 160 100)'>" + escXml(text) + "</text></svg>";
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeTypedCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (value instanceof Boolean b) {
            cell.setCellValue(b ? "是" : "否");
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private static CellStyle createBannerStyle(SXSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setItalic(true);
        font.setFontHeightInPoints((short) 9);
        font.setColor(IndexedColors.DARK_RED.getIndex());
        style.setFont(font);
        return style;
    }

    private static CellStyle createHeaderStyle(SXSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    private static CellStyle createBodyStyle(SXSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.HAIR);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private static String sanitizeSheetName(String title) {
        if (title == null || title.isBlank()) return "Sheet1";
        String cleaned = title.replaceAll("[\\\\/?*\\[\\]:]", "_");
        return cleaned.length() > 28 ? cleaned.substring(0, 28) : cleaned;
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            return "";
        }
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String escXml(String s) {
        return escHtml(s);
    }
}
