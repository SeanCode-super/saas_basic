package com.saasbasics.platform.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.system.entity.ApiEndpointStatEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ApiEndpointStatMapper extends BaseMapper<ApiEndpointStatEntity> {

    @Insert("""
            INSERT INTO sys_api_endpoint_stat (
                endpoint_key, http_method, path_pattern, module_code,
                total_calls, encrypted_calls, plaintext_calls, rejected_calls, error_calls,
                total_duration_ms, p95_latency_ms, p99_latency_ms, last_called_at_ms
            ) VALUES (
                #{endpointKey}, #{httpMethod}, #{pathPattern}, #{moduleCode},
                #{totalDelta}, #{encDelta}, #{plainDelta}, #{rejDelta}, #{errDelta},
                #{durDelta}, #{p95Ms}, #{p99Ms}, #{lastCalledAtMs}
            )
            ON DUPLICATE KEY UPDATE
                total_calls = total_calls + VALUES(total_calls),
                encrypted_calls = encrypted_calls + VALUES(encrypted_calls),
                plaintext_calls = plaintext_calls + VALUES(plaintext_calls),
                rejected_calls = rejected_calls + VALUES(rejected_calls),
                error_calls = error_calls + VALUES(error_calls),
                total_duration_ms = total_duration_ms + VALUES(total_duration_ms),
                p95_latency_ms = IF(VALUES(p95_latency_ms) > 0, VALUES(p95_latency_ms), p95_latency_ms),
                p99_latency_ms = IF(VALUES(p99_latency_ms) > 0, VALUES(p99_latency_ms), p99_latency_ms),
                last_called_at_ms = GREATEST(last_called_at_ms, VALUES(last_called_at_ms))
            """)
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
