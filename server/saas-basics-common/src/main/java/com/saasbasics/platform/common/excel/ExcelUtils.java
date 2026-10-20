package com.saasbasics.platform.common.excel;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 企业级 FastExcel 高性能流式导出与导入工具类。
 * 兼容大数据量低内存占用，彻底杜绝导出 Excel 时的 JVM OOM 崩溃。
 */
public final class ExcelUtils {

    private ExcelUtils() {
    }

    /**
     * 流式导出 Excel 至 HTTP 响应
     */
    public static <T> void write(HttpServletResponse response, String filename, String sheetName,
                                 Class<T> head, List<T> data) throws IOException {
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encodedFilename + ".xlsx");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        FastExcel.write(baos, head)
                .sheet(sheetName != null ? sheetName : "Sheet1")
                .doWrite(data);

        response.setContentLength(baos.size());
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

    /**
     * 流式导出 Excel 至输出流
     */
    public static <T> void write(OutputStream outputStream, String sheetName,
                                 Class<T> head, List<T> data) {
        FastExcel.write(outputStream, head)
                .autoCloseStream(Boolean.TRUE)
                .sheet(sheetName != null ? sheetName : "Sheet1")
                .doWrite(data);
    }

    /**
     * 流式批量读取 Excel
     */
    public static <T> void read(InputStream inputStream, Class<T> head, int batchSize, Consumer<List<T>> batchConsumer) {
        FastExcel.read(inputStream, head, new ReadListener<T>() {
            private final List<T> buffer = new ArrayList<>(batchSize);

            @Override
            public void invoke(T data, AnalysisContext context) {
                buffer.add(data);
                if (buffer.size() >= batchSize) {
                    batchConsumer.accept(new ArrayList<>(buffer));
                    buffer.clear();
                }
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                if (!buffer.isEmpty()) {
                    batchConsumer.accept(new ArrayList<>(buffer));
                    buffer.clear();
                }
            }
        }).sheet().doRead();
    }
}
