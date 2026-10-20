package com.saasbasics.platform.modules.system.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.system.infra.InfraMonitorService;
import com.saasbasics.platform.modules.system.infra.InfraMonitorService.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/infra")
public class InfraMonitorController {

    private final InfraMonitorService infraMonitorService;
    private final com.saasbasics.platform.cache.SystemCacheService systemCacheService;

    public InfraMonitorController(InfraMonitorService infraMonitorService,
                                  com.saasbasics.platform.cache.SystemCacheService systemCacheService) {
        this.infraMonitorService = infraMonitorService;
        this.systemCacheService = systemCacheService;
    }

    @GetMapping("/redis/info")
    @RequirePermission("system:config:query")
    public ApiResponse<RedisInfoResponse> getRedisInfo() {
        return ApiResponse.success(infraMonitorService.getRedisInfo());
    }

    @GetMapping("/redis/keys")
    @RequirePermission("system:config:query")
    public ApiResponse<List<RedisKeyItem>> listRedisKeys(@RequestParam(required = false, defaultValue = "*") String pattern) {
        return ApiResponse.success(infraMonitorService.listRedisKeys(pattern));
    }

    @DeleteMapping("/redis/keys")
    @RequirePermission("system:config:write")
    public ApiResponse<Boolean> deleteRedisKey(@RequestParam String key) {
        return ApiResponse.success(infraMonitorService.deleteRedisKey(key));
    }

    @PostMapping("/redis/cache/reload")
    @RequirePermission("system:config:write")
    public ApiResponse<Map<String, Object>> reloadSystemCaches() {
        int clearedCount = systemCacheService.reloadAllSystemCaches();
        return ApiResponse.success(Map.of(
                "success", true,
                "clearedKeys", clearedCount,
                "message", "系统 Redis 缓存已成功清理并触发重载，已与底层数据库强同步"
        ));
    }

    @GetMapping("/slow-sql/report")
    @RequirePermission("system:config:query")
    public ApiResponse<SlowSqlReportResponse> getSlowSqlReport() {
        return ApiResponse.success(infraMonitorService.getSlowSqlReport());
    }

    @DeleteMapping("/slow-sql/clear")
    @RequirePermission("system:config:write")
    public ApiResponse<Void> clearSlowSql() {
        infraMonitorService.clearSlowSqlRecords();
        return ApiResponse.success(null);
    }

    @PutMapping("/slow-sql/threshold")
    @RequirePermission("system:config:write")
    public ApiResponse<Void> setSlowSqlThreshold(@RequestBody Map<String, Long> payload) {
        Long threshold = payload.get("thresholdMillis");
        if (threshold != null) {
            infraMonitorService.setSlowSqlThreshold(threshold);
        }
        return ApiResponse.success(null);
    }

    @GetMapping("/jvm/info")
    @RequirePermission("system:config:query")
    public ApiResponse<JvmInfoResponse> getJvmInfo() {
        return ApiResponse.success(infraMonitorService.getJvmInfo());
    }
}
