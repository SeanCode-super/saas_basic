package com.saasbasics.platform.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.system.entity.ApiEndpointStatEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ApiEndpointStatMapper extends BaseMapper<ApiEndpointStatEntity> {

    int accumulateDelta(
            @Param("endpointKey") String endpointKey,
            @Param("httpMethod") String httpMethod,
            @Param("pathPattern") String pathPattern,
            @Param("moduleCode") String moduleCode,
            @Param("totalDelta") long totalDelta,
            @Param("encDelta") long encDelta,
            @Param("plainDelta") long plainDelta,
            @Param("rejDelta") long rejDelta,
            @Param("errDelta") long errDelta,
            @Param("durDelta") long durDelta,
            @Param("p95Ms") double p95Ms,
            @Param("p99Ms") double p99Ms,
            @Param("lastCalledAtMs") long lastCalledAtMs
    );
}
