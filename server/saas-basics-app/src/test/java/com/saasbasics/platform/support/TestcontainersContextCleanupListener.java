package com.saasbasics.platform.support;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Closes per-class Spring Boot TestContexts before JUnit stops the class's Testcontainers MySQL instance,
 * preventing orphaned HikariPool threads from polling closed Docker ports or delaying Surefire JVM exit.
 */
public class TestcontainersContextCleanupListener extends AbstractTestExecutionListener {

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public void afterTestClass(TestContext testContext) {
        if (AnnotatedElementUtils.hasAnnotation(testContext.getTestClass(), Testcontainers.class)) {
            testContext.markApplicationContextDirty(DirtiesContext.HierarchyMode.EXHAUSTIVE);
        }
    }
}
