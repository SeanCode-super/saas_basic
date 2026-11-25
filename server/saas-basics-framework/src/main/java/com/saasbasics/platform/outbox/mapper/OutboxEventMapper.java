package com.saasbasics.platform.outbox.mapper;

import com.saasbasics.platform.outbox.OutboxEventRecordView;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OutboxEventMapper {
    int enqueue(@Param("eventId") String eventId, @Param("eventType") String eventType,
                @Param("aggregateType") String aggregateType, @Param("aggregateId") String aggregateId,
                @Param("tenantId") Long tenantId, @Param("json") String json);

    List<OutboxEventRecordView> findPending();

    List<OutboxEventRecordView> findRecent(@Param("limit") int limit);

    int markSent(@Param("id") long id);

    int markFailure(@Param("id") long id, @Param("status") String status, @Param("retryCount") int retryCount,
                    @Param("nextRetry") LocalDateTime nextRetry, @Param("error") String error);

    int reschedule(@Param("id") long id);
}
