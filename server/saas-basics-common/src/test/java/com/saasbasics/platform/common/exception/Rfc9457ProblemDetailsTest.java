package com.saasbasics.platform.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saasbasics.platform.common.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class Rfc9457ProblemDetailsTest {

    @AfterEach
    void cleanup() {
        TraceContext.clear();
    }

    @Test
    void returnsRfc9457ProblemJsonWhenRequestedAndKeepsApiResponseDefault() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SampleController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        TraceContext.setTraceId("trace-9457-test");

        // 1. Default client expects legacy ApiResponse JSON
        mockMvc.perform(get("/probe/denied"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TENANT_ACCESS_DENIED"));

        // 2. Accept: application/problem+json yields RFC 9457 Problem Details
        mockMvc.perform(get("/probe/denied").accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:tianshu:problem:tenant-access-denied"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.code").value("TENANT_ACCESS_DENIED"))
                .andExpect(jsonPath("$.traceId").value("trace-9457-test"))
                .andExpect(jsonPath("$.instance").value("/probe/denied"));
    }

    @Test
    void databaseErrorsKeepStatementDetailsOutOfBothResponseFormats() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SampleController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String message = "本次操作未能完成，请联系平台管理员。";
        mvc.perform(get("/probe/database").header("Accept-Language", "zh-CN"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(message));
        mvc.perform(get("/probe/database").header("Accept-Language", "zh-CN").accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value(message));
    }

    @RestController
    static class SampleController {
        @GetMapping("/probe/database")
        public void databaseError() {
            throw new DataAccessResourceFailureException("statement details for the server log");
        }

        @GetMapping("/probe/denied")
        public void denied() {
            throw new BizException("TENANT_ACCESS_DENIED", "Cross-tenant operation denied");
        }
    }
}
