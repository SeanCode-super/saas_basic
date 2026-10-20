package com.saasbasics.platform.workflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.saasbasics.platform.common",
        "com.saasbasics.platform.config",
        "com.saasbasics.platform.security",
        "com.saasbasics.platform.mask",
        "com.saasbasics.platform.modules.workflow",
        "com.saasbasics.platform.workflow"
})
@MapperScan({
        "com.saasbasics.platform.modules.workflow.mapper"
})
public class WorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowApplication.class, args);
    }
}
