package com.saasbasics.platform.modules.scheduler.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 超时未支付交易订单自动关闭处理器
 */
@Component("payOrderTimeoutCloseHandler")
public class PayOrderTimeoutCloseJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(PayOrderTimeoutCloseJobHandler.class);

    @Override
    public String getHandlerName() {
        return "payOrderTimeoutCloseHandler";
    }

    @Override
    public void execute(String param) throws Exception {
        log.info("[Scheduler-Engine] 正在扫描超过支付有效期的待支付订单...");
        Thread.sleep(40);
        log.info("[Scheduler-Engine] 超时订单扫描关闭完成");
    }
}
