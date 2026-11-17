package com.saasbasics.platform.modules.file.service;

import com.saasbasics.platform.common.mask.DataMaskUtils;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportFieldSpec;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
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
            case "PDF" -> {
                bytes = renderPdf(title, columns, rows, insights, includeWatermark, watermarkText, traceCode, operatorName);
                ext = "pdf";
                contentType = "application/pdf";
            }
            case "HTML_REPORT", "REPORT" -> {
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

    /**
     * Pure-JDK binary PDF 1.4 multi-page A4 renderer with crisp typography, KPI bar visualization,
     * zebra-striped data table, and repeating diagonal security watermark.
     */
    private byte[] renderPdf(
            String title,
            List<ExportFieldSpec> columns,
            List<Map<String, Object>> rows,
            List<String> insights,
            boolean includeWatermark,
            String watermarkText,
            String traceCode,
            String operatorName
    ) throws IOException {
        System.setProperty("java.awt.headless", "true");
        int w = 1190;
        int h = 1684;
        String fontFamily = resolveCjkFontFamily();
        List<ExportFieldSpec> visibleCols = columns.size() > 7 ? columns.subList(0, 7) : columns;
        int maxDataRows = Math.min(rows.size(), 200);

        List<BufferedImage> pages = new ArrayList<>();
        int rowCursor = 0;
        int pageNum = 0;

        do {
            pageNum++;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Clean white A4 canvas
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);

            int y = 44;
            int margin = 52;
            int contentW = w - margin * 2;

            // Header banner
            if (pageNum == 1) {
                g.setPaint(new GradientPaint(margin, y, new Color(15, 23, 42), margin + contentW, y + 118, new Color(29, 78, 216)));
                g.fillRoundRect(margin, y, contentW, 118, 16, 16);
                g.setColor(Color.WHITE);
                g.setFont(new java.awt.Font(fontFamily, java.awt.Font.BOLD, 28));
                g.drawString(truncateText(title != null ? title : "企业数据智能分析报告", 36), margin + 28, y + 48);
                g.setColor(new Color(219, 234, 254));
                g.setFont(new java.awt.Font(fontFamily, java.awt.Font.PLAIN, 15));
                String metaLine = "导出人: " + (operatorName != null ? operatorName : "system")
                        + "  |  时间: " + LocalDateTime.now().format(TS_FMT)
                        + "  |  总行数: " + rows.size()
                        + "  |  追溯码: " + traceCode;
                g.drawString(truncateText(metaLine, 84), margin + 28, y + 84);
                y += 140;

                // AI Insights box
                if (insights != null && !insights.isEmpty()) {
                    int insCount = Math.min(insights.size(), 3);
                    int boxH = 42 + insCount * 28;
                    g.setColor(new Color(239, 246, 255));
                    g.fillRoundRect(margin, y, contentW, boxH, 12, 12);
                    g.setColor(new Color(191, 219, 254));
                    g.drawRoundRect(margin, y, contentW, boxH, 12, 12);
                    g.setColor(new Color(30, 58, 138));
                    g.setFont(new java.awt.Font(fontFamily, java.awt.Font.BOLD, 16));
                    g.drawString("AI 数据洞察摘要", margin + 18, y + 26);
                    g.setColor(new Color(51, 65, 85));
                    g.setFont(new java.awt.Font(fontFamily, java.awt.Font.PLAIN, 14));
                    for (int i = 0; i < insCount; i++) {
                        g.drawString("• " + truncateText(insights.get(i), 76), margin + 20, y + 52 + i * 26);
                    }
                    y += boxH + 18;
                }

                // KPI bar visualization (if numeric column found)
                y = drawPdfBarChart(g, fontFamily, margin, y, contentW, visibleCols, rows);
            } else {
                g.setColor(new Color(15, 23, 42));
                g.setFont(new java.awt.Font(fontFamily, java.awt.Font.BOLD, 18));
                g.drawString(truncateText((title != null ? title : "数据报告") + "（续表）", 48), margin, y + 24);
                g.setColor(new Color(148, 163, 184));
                g.drawLine(margin, y + 36, margin + contentW, y + 36);
                y += 56;
            }

            // Data table header
            if (!visibleCols.isEmpty()) {
                int colCount = visibleCols.size();
                int colW = contentW / colCount;
                g.setColor(new Color(241, 245, 249));
                g.fillRect(margin, y, contentW, 38);
                g.setColor(new Color(30, 41, 59));
                g.setFont(new java.awt.Font(fontFamily, java.awt.Font.BOLD, 14));
                for (int c = 0; c < colCount; c++) {
                    String lbl = truncateText(visibleCols.get(c).fieldLabel(), Math.max(8, 56 / colCount));
                    g.drawString(lbl, margin + c * colW + 10, y + 24);
                }
                y += 38;

                g.setFont(new java.awt.Font(fontFamily, java.awt.Font.PLAIN, 13));
                int maxY = h - 96;
                while (rowCursor < maxDataRows && y + 34 <= maxY) {
                    if (rowCursor % 2 == 1) {
                        g.setColor(new Color(248, 250, 252));
                        g.fillRect(margin, y, contentW, 34);
                    }
                    g.setColor(new Color(226, 232, 240));
                    g.drawLine(margin, y + 34, margin + contentW, y + 34);

                    Map<String, Object> row = rows.get(rowCursor);
                    g.setColor(new Color(51, 65, 85));
                    for (int c = 0; c < colCount; c++) {
                        Object raw = row.get(visibleCols.get(c).fieldCode());
                        String txt = raw == null ? "" : String.valueOf(raw);
                        g.drawString(truncateText(txt, Math.max(10, 64 / colCount)), margin + c * colW + 10, y + 22);
                    }
                    y += 34;
                    rowCursor++;
                }
            }

            if (rowCursor >= maxDataRows && rows.size() > maxDataRows && y + 30 < h - 72) {
                g.setColor(new Color(100, 116, 139));
                g.setFont(new java.awt.Font(fontFamily, java.awt.Font.ITALIC, 13));
                g.drawString("提示：PDF 报告仅渲染前 " + maxDataRows + " 条数据，全量 " + rows.size() + " 条明细请导出 XLSX/CSV 格式。", margin, y + 22);
            }

            // Diagonal repeating watermark
            if (includeWatermark && watermarkText != null && !watermarkText.isBlank()) {
                AffineTransform saved = g.getTransform();
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.14f));
                g.setColor(new Color(71, 85, 105));
                g.setFont(new java.awt.Font(fontFamily, java.awt.Font.BOLD, 16));
                for (int wy = 180; wy < h; wy += 340) {
                    for (int wx = 80; wx < w; wx += 420) {
                        g.rotate(-Math.toRadians(28), wx, wy);
                        g.drawString(watermarkText, wx - 110, wy);
                        g.setTransform(saved);
                    }
                }
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            }

            // Footer strip
            g.setColor(new Color(226, 232, 240));
            g.drawLine(margin, h - 62, margin + contentW, h - 62);
            g.setColor(new Color(100, 116, 139));
            g.setFont(new java.awt.Font(fontFamily, java.awt.Font.PLAIN, 12));
            g.drawString("天枢企业级安全导出审计  ·  追溯码: " + traceCode, margin, h - 36);
            String pgText = "第 " + pageNum + " 页";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(pgText, margin + contentW - fm.stringWidth(pgText), h - 36);

            g.dispose();
            pages.add(img);
        } while (rowCursor < maxDataRows && pages.size() < 15);

        return encodeImagesAsPdf14(pages);
    }

    private int drawPdfBarChart(
            Graphics2D g,
            String fontFamily,
            int margin,
            int y,
            int contentW,
            List<ExportFieldSpec> columns,
            List<Map<String, Object>> rows
    ) {
        if (rows == null || rows.isEmpty() || columns.isEmpty()) {
            return y;
        }
        String labelCol = null;
        String numCol = null;
        String numLabel = "";
        Map<String, Object> sample = rows.get(0);
        for (ExportFieldSpec c : columns) {
            Object v = sample.get(c.fieldCode());
            if (numCol == null && v instanceof Number) {
                numCol = c.fieldCode();
                numLabel = c.fieldLabel();
            } else if (labelCol == null) {
                labelCol = c.fieldCode();
            }
        }
        if (numCol == null || labelCol == null) {
            return y;
        }
        int barCount = Math.min(rows.size(), 6);
        double maxVal = 1.0;
        for (int i = 0; i < barCount; i++) {
            Object v = rows.get(i).get(numCol);
            if (v instanceof Number n) {
                maxVal = Math.max(maxVal, n.doubleValue());
            }
        }
        int boxH = 44 + barCount * 30;
        g.setColor(new Color(248, 250, 252));
        g.fillRoundRect(margin, y, contentW, boxH, 12, 12);
        g.setColor(new Color(226, 232, 240));
        g.drawRoundRect(margin, y, contentW, boxH, 12, 12);
        g.setColor(new Color(30, 58, 138));
        g.setFont(new java.awt.Font(fontFamily, java.awt.Font.BOLD, 15));
        g.drawString("指标排行可视化（" + numLabel + " · Top " + barCount + "）", margin + 18, y + 26);

        int barMaxW = contentW - 340;
        g.setFont(new java.awt.Font(fontFamily, java.awt.Font.PLAIN, 13));
        for (int i = 0; i < barCount; i++) {
            int rowY = y + 48 + i * 28;
            String dim = truncateText(String.valueOf(rows.get(i).getOrDefault(labelCol, "-")), 18);
            double val = rows.get(i).get(numCol) instanceof Number n ? n.doubleValue() : 0.0;
            int fillW = (int) Math.max(8, Math.round((val / maxVal) * barMaxW));
            g.setColor(new Color(51, 65, 85));
            g.drawString(dim, margin + 18, rowY + 12);
            g.setColor(new Color(226, 232, 240));
            g.fillRoundRect(margin + 190, rowY, barMaxW, 14, 6, 6);
            g.setColor(new Color(37, 99, 235));
            g.fillRoundRect(margin + 190, rowY, fillW, 14, 6, 6);
            g.setColor(new Color(15, 23, 42));
            g.drawString(BigDecimal.valueOf(val).stripTrailingZeros().toPlainString(), margin + 200 + barMaxW, rowY + 12);
        }
        return y + boxH + 18;
    }

    private static byte[] encodeImagesAsPdf14(List<BufferedImage> pages) throws IOException {
        List<byte[]> jpegs = new ArrayList<>(pages.size());
        for (BufferedImage page : pages) {
            ByteArrayOutputStream jpegOut = new ByteArrayOutputStream();
            ImageIO.write(page, "jpg", jpegOut);
            jpegs.add(jpegOut.toByteArray());
        }

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        List<Long> offsets = new ArrayList<>();
        pdf.write("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n".getBytes(StandardCharsets.ISO_8859_1));

        // 1: Catalog, 2: Pages
        // Per page i (0..n-1): obj 3 + i*3 = Page, 4 + i*3 = Contents, 5 + i*3 = Image XObject
        int n = jpegs.size();
        offsets.add((long) pdf.size());
        pdf.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) kids.append(" ");
            kids.append(3 + i * 3).append(" 0 R");
        }
        offsets.add((long) pdf.size());
        pdf.write(("2 0 obj\n<< /Type /Pages /Kids [" + kids + "] /Count " + n + " >>\nendobj\n")
                .getBytes(StandardCharsets.ISO_8859_1));

        for (int i = 0; i < n; i++) {
            int pageObj = 3 + i * 3;
            int contentObj = 4 + i * 3;
            int imgObj = 5 + i * 3;
            BufferedImage img = pages.get(i);
            byte[] jpeg = jpegs.get(i);
            byte[] contentStream = ("q 595 0 0 842 0 0 cm /Im" + i + " Do Q").getBytes(StandardCharsets.ISO_8859_1);

            offsets.add((long) pdf.size());
            pdf.write((pageObj + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                    + "/Resources << /XObject << /Im" + i + " " + imgObj + " 0 R >> >> "
                    + "/Contents " + contentObj + " 0 R >>\nendobj\n").getBytes(StandardCharsets.ISO_8859_1));

            offsets.add((long) pdf.size());
            pdf.write((contentObj + " 0 obj\n<< /Length " + contentStream.length + " >>\nstream\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            pdf.write(contentStream);
            pdf.write("\nendstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

            offsets.add((long) pdf.size());
            pdf.write((imgObj + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + img.getWidth()
                    + " /Height " + img.getHeight() + " /ColorSpace /DeviceRGB /BitsPerComponent 8 "
                    + "/Filter /DCTDecode /Length " + jpeg.length + " >>\nstream\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            pdf.write(jpeg);
            pdf.write("\nendstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
        }

        long xrefOffset = pdf.size();
        int totalObjs = offsets.size() + 1;
        StringBuilder xref = new StringBuilder();
        xref.append("xref\n0 ").append(totalObjs).append("\n");
        xref.append("0000000000 65535 f \n");
        for (Long off : offsets) {
            xref.append(String.format(Locale.ROOT, "%010d 00000 n \n", off));
        }
        xref.append("trailer\n<< /Size ").append(totalObjs).append(" /Root 1 0 R >>\n");
        xref.append("startxref\n").append(xrefOffset).append("\n%%EOF\n");
        pdf.write(xref.toString().getBytes(StandardCharsets.ISO_8859_1));
        return pdf.toByteArray();
    }

    private static String resolveCjkFontFamily() {
        try {
            List<String> available = Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.SIMPLIFIED_CHINESE)
            );
            for (String candidate : List.of("PingFang SC", "Hiragino Sans GB", "Noto Sans CJK SC", "WenQuanYi Micro Hei", "Microsoft YaHei", "SimHei")) {
                if (available.contains(candidate)) {
                    return candidate;
                }
            }
        } catch (Throwable ignored) {
        }
        return "SansSerif";
    }

    private static String truncateText(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
    }
}
