package com.saasbasics.platform.modules.dashboard.mapper;

import com.saasbasics.platform.modules.dashboard.dto.DashboardMetric;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DashboardMetricMapper {
    List<DashboardMetric> listMetrics(@Param("tenantId") long tenantId, @Param("platformAdmin") boolean platformAdmin);
}
