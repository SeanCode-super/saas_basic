package com.saasbasics.platform.modules.codegen.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.api.PageResponse;
import com.saasbasics.platform.modules.codegen.dto.DynamicDataPageRequest;
import com.saasbasics.platform.modules.codegen.dto.DynamicModelResponse;
import com.saasbasics.platform.modules.codegen.service.DynamicDataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dynamic/{modelCode}")
public class DynamicDataController {

    private final DynamicDataService dynamicDataService;

    public DynamicDataController(DynamicDataService dynamicDataService) {
        this.dynamicDataService = dynamicDataService;
    }

    @GetMapping("/meta")
    public ApiResponse<DynamicModelResponse> getMeta(@PathVariable("modelCode") String modelCode) {
        return ApiResponse.success(dynamicDataService.getModelMeta(modelCode));
    }

    @PostMapping("/page")
    public ApiResponse<PageResponse<Map<String, Object>>> queryPage(
            @PathVariable("modelCode") String modelCode,
            @RequestBody DynamicDataPageRequest request
    ) {
        return ApiResponse.success(dynamicDataService.queryPage(modelCode, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getById(
            @PathVariable("modelCode") String modelCode,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.success(dynamicDataService.getById(modelCode, id));
    }

    @PostMapping
    public ApiResponse<Long> createRecord(
            @PathVariable("modelCode") String modelCode,
            @RequestBody Map<String, Object> data
    ) {
        return ApiResponse.success(dynamicDataService.createRecord(modelCode, data));
    }

    @PutMapping
    public ApiResponse<Void> updateRecordWithoutPathId(
            @PathVariable("modelCode") String modelCode,
            @RequestBody Map<String, Object> data
    ) {
        Object idObj = data.get("id");
        if (idObj == null) {
            throw new IllegalArgumentException("Record ID cannot be null");
        }
        Long id = Long.valueOf(String.valueOf(idObj));
        dynamicDataService.updateRecord(modelCode, id, data);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> updateRecord(
            @PathVariable("modelCode") String modelCode,
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> data
    ) {
        dynamicDataService.updateRecord(modelCode, id, data);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRecord(
            @PathVariable("modelCode") String modelCode,
            @PathVariable("id") Long id
    ) {
        dynamicDataService.deleteRecord(modelCode, id);
        return ApiResponse.success(null);
    }

    @PostMapping("/batch-delete")
    public ApiResponse<Void> batchDelete(
            @PathVariable("modelCode") String modelCode,
            @RequestBody List<Long> ids
    ) {
        dynamicDataService.batchDeleteRecords(modelCode, ids);
        return ApiResponse.success(null);
    }

    @PostMapping("/seed-mock")
    public ApiResponse<Integer> seedMock(@PathVariable("modelCode") String modelCode) {
        return ApiResponse.success(dynamicDataService.seedMockRecords(modelCode));
    }

    @PostMapping("/batch-import")
    public ApiResponse<Integer> batchImport(
            @PathVariable("modelCode") String modelCode,
            @RequestBody List<Map<String, Object>> records
    ) {
        return ApiResponse.success(dynamicDataService.batchImportRecords(modelCode, records));
    }
}
