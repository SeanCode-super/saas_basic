package com.saasbasics.platform.modules.file.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportFieldSpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExportRendererServiceTest {

    private final ExportRendererService renderer = new ExportRendererService();

    private List<ExportFieldSpec> sampleColumns() {
        return List.of(
                new ExportFieldSpec("name", "客户名称", false, "VARCHAR"),
                new ExportFieldSpec("mobile", "联系电话", true, "VARCHAR"),
                new ExportFieldSpec("amount", "合同金额", false, "DECIMAL")
        );
    }

    private List<Map<String, Object>> sampleRows() {
        return List.of(
                Map.of("name", "华润数字科技", "mobile", "13811223344", "amount", 180000),
                Map.of("name", "智联物联", "mobile", "13955667788", "amount", 92000)
        );
    }

    @Test
    void rendersWatermarkedAndDesensitizedXlsx() throws Exception {
        ExportRendererService.RenderOutput out = renderer.render(
                "客户导出", "XLSX", sampleColumns(), sampleRows(), List.of(),
                true, true, "admin · 2026-11-12 · EXP-TEST-01", "EXP-TEST-01", "admin", 1L);

        assertEquals("xlsx", out.fileExtension());
        assertEquals(64, out.sha256().length());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.bytes()))) {
            var sheet = wb.getSheetAt(0);
            String banner = sheet.getRow(0).getCell(0).getStringCellValue();
            assertTrue(banner.contains("EXP-TEST-01"));
            // Row 1 is header, Row 2 is first data row -> verify mobile masked
            assertEquals("138****3344", sheet.getRow(2).getCell(1).getStringCellValue());

            int traceIdx = wb.getSheetIndex("_Export_Trace_Audit");
            assertTrue(traceIdx >= 0);
            assertEquals(SheetVisibility.VERY_HIDDEN, wb.getSheetVisibility(traceIdx));
            assertEquals("EXP-TEST-01", wb.getSheetAt(traceIdx).getRow(0).getCell(1).getStringCellValue());
        }
    }

    @Test
    void rendersBomCsvAndWatermarkedHtmlReport() throws Exception {
        ExportRendererService.RenderOutput csv = renderer.render(
                "客户CSV", "CSV", sampleColumns(), sampleRows(), List.of(),
                true, true, "admin · EXP-CSV-01", "EXP-CSV-01", "admin", 1L);
        String csvText = new String(csv.bytes(), StandardCharsets.UTF_8);
        assertTrue(csvText.contains("EXP-CSV-01"));
        assertTrue(csvText.contains("138****3344"));
        assertFalse(csvText.contains("13811223344"));

        ExportRendererService.RenderOutput html = renderer.render(
                "客户智能分析报告", "HTML_REPORT", sampleColumns(), sampleRows(),
                List.of("合同总金额达 272000 元"), true, true, "admin · EXP-HTML-01", "EXP-HTML-01", "admin", 1L);
        String htmlText = new String(html.bytes(), StandardCharsets.UTF_8);
        assertTrue(htmlText.contains("report-watermark"));
        assertTrue(htmlText.contains("EXP-HTML-01"));
        assertTrue(htmlText.contains("合同总金额达 272000 元"));
        assertTrue(htmlText.contains("139****7788"));
    }

    @Test
    void rendersBinaryPdf14Document() throws Exception {
        ExportRendererService.RenderOutput pdf = renderer.render(
                "客户财务分析PDF报告", "PDF", sampleColumns(), sampleRows(),
                List.of("前两大客户贡献总合同额 272000 元"), true, true,
                "admin · EXP-PDF-01", "EXP-PDF-01", "admin", 1L);

        assertEquals("pdf", pdf.fileExtension());
        assertEquals("application/pdf", pdf.contentType());
        assertTrue(pdf.bytes().length > 1000);
        String head = new String(pdf.bytes(), 0, Math.min(16, pdf.bytes().length), StandardCharsets.ISO_8859_1);
        String tail = new String(pdf.bytes(), Math.max(0, pdf.bytes().length - 16),
                Math.min(16, pdf.bytes().length), StandardCharsets.ISO_8859_1);
        assertTrue(head.startsWith("%PDF-1.4"));
        assertTrue(tail.contains("%%EOF"));
    }
}
