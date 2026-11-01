package com.saasbasics.platform.common.excel;

import java.util.ArrayList;
import java.util.List;

/**
 * 工业级 Excel 导入解析与校验结果集。
 * 遵循企业中台标准规范，提供行级校验失败明细、成功集合与统计指标。
 */
public class ExcelImportResult<T> {

    private int totalRows = 0;
    private int successCount = 0;
    private int failureCount = 0;
    private final List<T> successList = new ArrayList<>();
    private final List<String> errorMessages = new ArrayList<>();

    public void addSuccess(T item) {
        this.totalRows++;
        this.successCount++;
        this.successList.add(item);
    }

    public void addError(int rowNum, String reason) {
        this.totalRows++;
        this.failureCount++;
        this.errorMessages.add("第 " + rowNum + " 行: " + reason);
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public List<T> getSuccessList() {
        return successList;
    }

    public List<String> getErrorMessages() {
        return errorMessages;
    }

    public boolean hasErrors() {
        return failureCount > 0;
    }
}
