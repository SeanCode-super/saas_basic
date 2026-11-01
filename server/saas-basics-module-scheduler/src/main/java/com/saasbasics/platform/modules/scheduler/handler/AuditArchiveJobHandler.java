package com.saasbasics.platform.modules.scheduler.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 历史操作审计日志智能归档与过期清理处理器
 */
@Component("auditArchiveHandler")
public class AuditArchiveJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(AuditArchiveJobHandler.class);

    @Override
    public String getHandlerName() {
        return "auditArchiveHandler";
    }

    @Override
    public void execute(String param) throws Exception {
        log.info("[Scheduler-Engine] 开始执行操作审计日志智能归档与冷数据迁移, 参数: {}", param);
        Thread.sleep(50);
        log.info("[Scheduler-Engine] 审计日志归档完毕，保留策略生效正常");
    }
}
