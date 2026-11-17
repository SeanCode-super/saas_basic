package com.saasbasics.platform.outbox;

/**
 * 跨模块本地事务发件箱事件消费扩展点（由业务模块实现，避免模块间循环依赖）。
 */
public interface OutboxEventHandler {

    String supportedEventType();

    void handle(OutboxEventPayload event) throws Exception;
}
