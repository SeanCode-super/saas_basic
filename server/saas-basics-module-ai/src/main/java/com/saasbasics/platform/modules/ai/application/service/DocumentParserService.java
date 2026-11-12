package com.saasbasics.platform.modules.ai.application.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Extracts normalized UTF-8 text from enterprise document formats (MD, TXT, CSV, JSON, HTML, DOCX, XLSX).
 */
@Service
public class DocumentParserService {

    public record ParsedDocument(
            String fileName,
            String fileType,
            long fileSize,
            String textContent,
            String summary
    ) {}

    public ParsedDocument parseUpload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文档不能为空");
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.txt";
        String ext = extractExt(original);
        byte[] bytes = file.getBytes();

        String text = switch (ext) {
            case "DOCX" -> parseDocx(bytes);
            case "XLSX", "XLS" -> parseSpreadsheet(bytes);
            case "HTML", "HTM" -> stripHtmlTags(new String(bytes, StandardCharsets.UTF_8));
            default -> new String(bytes, StandardCharsets.UTF_8);
        };

        String cleaned = text == null ? "" : text.replace("\u0000", "").trim();
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("未能从文档中解析出有效文本内容");
        }
        String summary = cleaned.replaceAll("\\s+", " ");
        if (summary.length() > 160) {
            summary = summary.substring(0, 160) + "…";
        }
        return new ParsedDocument(original, ext, bytes.length, cleaned, summary);
    }

    private String parseDocx(byte[] bytes) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                String txt = p.getText();
                if (txt != null && !txt.isBlank()) {
                    sb.append(txt.trim()).append("\n");
                }
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder line = new StringBuilder();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        if (!line.isEmpty()) line.append(" | ");
                        line.append(cell.getText() != null ? cell.getText().trim() : "");
                    }
                    if (!line.isEmpty()) {
                        sb.append(line).append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    private String parseSpreadsheet(byte[] bytes) throws IOException {
        StringBuilder sb = new StringBuilder();
        DataFormatter formatter = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            for (int s = 0; s < Math.min(wb.getNumberOfSheets(), 8); s++) {
                Sheet sheet = wb.getSheetAt(s);
                sb.append("# ").append(sheet.getSheetName()).append("\n");
                int rowCount = 0;
                for (Row row : sheet) {
                    if (++rowCount > 400) break;
                    StringBuilder line = new StringBuilder();
                    for (Cell cell : row) {
                        if (!line.isEmpty()) line.append(" | ");
                        line.append(formatter.formatCellValue(cell));
                    }
                    if (!line.isEmpty()) {
                        sb.append(line).append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String stripHtmlTags(String html) {
        return html.replaceAll("(?s)<script.*?</script>", " ")
                .replaceAll("(?s)<style.*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("[ \\t]+", " ");
    }

    private static String extractExt(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "TXT";
        }
        return fileName.substring(idx + 1).trim().toUpperCase(Locale.ROOT);
    }
}
