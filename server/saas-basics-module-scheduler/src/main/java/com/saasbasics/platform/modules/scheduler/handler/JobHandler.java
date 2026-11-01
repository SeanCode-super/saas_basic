package com.saasbasics.platform.modules.scheduler.handler;

/**
 * 工业级任务处理器标准 SPI 接口。
 * 业务模块实现该接口并交由 Spring 容器托管即可注册为生产级可调度任务。
 */
public interface JobHandler {

    /**
     * 处理器唯一标识（对应 sched_job.handler_name）
     */
    String getHandlerName();

    /**
     * 执行具体业务逻辑
     *
     * @param param 任务配置参数（JSON 字符串或键值对）
     * @throws Exception 业务执行异常
     */
    void execute(String param) throws Exception;
}
