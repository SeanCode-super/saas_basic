package com.saasbasics.platform.modules.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlatformWorkflowMetricsMapper {
    record Counts(long definitions, long instances, long pendingTasks, long totalTasks) {}

    // Platform-only host monitor. All four counters describe the same database snapshot.
    @InterceptorIgnore(tenantLine = "true")
    Counts countWorkflowRecords();
}
