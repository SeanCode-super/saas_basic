package com.saasbasics.platform.modules.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.ai.api.AiToolExecutionResult;
import com.saasbasics.platform.modules.ai.api.AiToolHandler;
import com.saasbasics.platform.modules.ai.api.ToolRiskLevel;
import com.saasbasics.platform.modules.ai.application.dto.AiMcpProbeResultDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiRunCreateRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiRunDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiToolWhitelistRequest;
import com.saasbasics.platform.modules.ai.application.service.AiMcpGovernanceService;
import com.saasbasics.platform.modules.ai.application.service.AiMeteringQuotaService;
import com.saasbasics.platform.modules.ai.application.service.AiPromptService;
import com.saasbasics.platform.modules.ai.application.service.AiRunOrchestratorService;
import com.saasbasics.platform.modules.ai.domain.CredentialResolver;
import com.saasbasics.platform.modules.ai.infrastructure.adapter.VaultCredentialResolver;
import com.saasbasics.platform.modules.ai.infrastructure.mcp.McpClientAdapter;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiMcpServerEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiRunEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiToolCallEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiToolEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiMcpServerMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiModelConfigMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiProviderMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiRunMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiRunStepMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiToolCallMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiToolMapper;
import com.saasbasics.platform.security.vault.CredentialVaultService;
import com.saasbasics.platform.security.vault.VaultCryptoProperties;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiMcpAndApprovalGovernanceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CredentialResolver credentialResolver;
    private AiMcpServerMapper mcpServerMapper;
    private AiToolMapper toolMapper;
    private AiRunMapper runMapper;
    private AiRunStepMapper stepMapper;
    private AiToolCallMapper toolCallMapper;
    private AiMeteringQuotaService meteringQuotaService;
    private AtomicInteger highRiskExecCount;
    private AiMcpGovernanceService mcpGovernanceService;
    private AiRunOrchestratorService orchestratorService;

    @BeforeEach
    void setUp() {
        VaultCryptoProperties props = new VaultCryptoProperties();
        props.setMasterKey("test-master-key-for-ai-governance");
        credentialResolver = new VaultCredentialResolver(new CredentialVaultService(props));

        mcpServerMapper = mock(AiMcpServerMapper.class);
        toolMapper = mock(AiToolMapper.class);
        runMapper = mock(AiRunMapper.class);
        stepMapper = mock(AiRunStepMapper.class);
        toolCallMapper = mock(AiToolCallMapper.class);
        meteringQuotaService = mock(AiMeteringQuotaService.class);
        highRiskExecCount = new AtomicInteger(0);

        AiToolHandler readOnlyTool = new AiToolHandler() {
            @Override public String toolCode() { return "platform.metrics.summary"; }
            @Override public String toolName() { return "Metrics"; }
            @Override public String description() { return "Read metrics"; }
            @Override public ToolRiskLevel riskLevel() { return ToolRiskLevel.READ_ONLY; }
            @Override public AiToolExecutionResult execute(Long tenantId, Long userId, Map<String, Object> arguments) {
                return AiToolExecutionResult.ok("metrics ok", "{\"ok\":true}");
            }
        };

        AiToolHandler highRiskTool = new AiToolHandler() {
            @Override public String toolCode() { return "platform.cache.evict"; }
            @Override public String toolName() { return "Evict Cache"; }
            @Override public String description() { return "Evict tenant cache"; }
            @Override public ToolRiskLevel riskLevel() { return ToolRiskLevel.HIGH; }
            @Override public AiToolExecutionResult execute(Long tenantId, Long userId, Map<String, Object> arguments) {
                highRiskExecCount.incrementAndGet();
                return AiToolExecutionResult.ok("cache cleared", "{\"evicted\":true}");
            }
        };

        McpClientAdapter mcpClient = new McpClientAdapter(objectMapper, credentialResolver, List.of(readOnlyTool, highRiskTool));
        mcpGovernanceService = new AiMcpGovernanceService(
                mcpServerMapper, toolMapper, mcpClient, credentialResolver, objectMapper, List.of(readOnlyTool, highRiskTool)
        );
        orchestratorService = new AiRunOrchestratorService(
                runMapper, stepMapper, toolCallMapper,
                mock(AiProviderMapper.class), mock(AiModelConfigMapper.class),
                meteringQuotaService, mock(AiPromptService.class),
                mcpGovernanceService, List.of(), credentialResolver, objectMapper
        );
    }

    @AfterEach
    void cleanup() {
        AuthContext.clear();
    }

    @Test
    void mcpClientEnforcesDefaultDenyAllWhitelistPolicy() {
        McpClientAdapter adapter = new McpClientAdapter(objectMapper, credentialResolver, List.of(
                new AiToolHandler() {
                    @Override public String toolCode() { return "platform.metrics.summary"; }
                    @Override public String toolName() { return "Metrics"; }
                    @Override public String description() { return "Read"; }
                    @Override public AiToolExecutionResult execute(Long tenantId, Long userId, Map<String, Object> arguments) {
                        return AiToolExecutionResult.ok("allowed", "{}");
                    }
                }
        ));

        AiToolExecutionResult denied = adapter.invokeTool(
                "BUILTIN_LOOPBACK", "loopback://local", "Authorization", null,
                Set.of("other.tool"), "platform.metrics.summary", 1L, 1L, Map.of()
        );
        assertThat(denied.success()).isFalse();
        assertThat(denied.summary()).contains("默认拒绝");

        AiToolExecutionResult allowed = adapter.invokeTool(
                "BUILTIN_LOOPBACK", "loopback://local", "Authorization", null,
                Set.of("platform.metrics.summary"), "platform.metrics.summary", 1L, 1L, Map.of()
        );
        assertThat(allowed.success()).isTrue();
    }

    @Test
    void mcpProbeDiscoversBuiltinToolsWithoutOverwritingPlatformRiskLevel() {
        AiMcpServerEntity server = new AiMcpServerEntity();
        server.setId(10L);
        server.setTenantId(1L);
        server.setServerCode("builtin-platform-mcp");
        server.setTransportType("BUILTIN_LOOPBACK");
        server.setEndpointUrl("loopback://local");
        server.setAllowedToolsJson("[\"platform.metrics.summary\"]");

        AiToolEntity existingHigh = new AiToolEntity();
        existingHigh.setId(99L);
        existingHigh.setToolCode("platform.cache.evict");
        existingHigh.setRiskLevel("HIGH");
        existingHigh.setPermissionCode("admin:cache:write");
        existingHigh.setWhitelisted(1);

        when(mcpServerMapper.selectById(10L)).thenReturn(server);
        when(toolMapper.selectOne(any())).thenReturn(existingHigh);

        AiMcpProbeResultDTO probe = mcpGovernanceService.probeAndSyncTools(10L);

        assertThat(probe.success()).isTrue();
        assertThat(probe.discoveredTools()).hasSize(2);
        // Platform risk level and permission code cannot be downgraded during sync
        assertThat(existingHigh.getRiskLevel()).isEqualTo("HIGH");
        assertThat(existingHigh.getPermissionCode()).isEqualTo("admin:cache:write");
    }

    @Test
    void highRiskToolEntersWaitingApprovalAndRespectsApprovalIdempotencyAndReject() {
        AuthContext.set(principal(1L, 50L, List.of("system:config:query")));

        AiToolEntity highTool = new AiToolEntity();
        highTool.setId(5L);
        highTool.setToolCode("platform.cache.evict");
        highTool.setAdapterType("INTERNAL_FACADE");
        highTool.setRiskLevel("HIGH");
        highTool.setPermissionCode("system:config:query");
        highTool.setWhitelisted(1);
        when(toolMapper.selectOne(any())).thenReturn(highTool);

        when(runMapper.insert(any(AiRunEntity.class))).thenAnswer(inv -> {
            AiRunEntity r = inv.getArgument(0);
            r.setId(1001L);
            return 1;
        });
        when(toolCallMapper.insert(any(AiToolCallEntity.class))).thenAnswer(inv -> {
            AiToolCallEntity c = inv.getArgument(0);
            c.setId(2001L);
            return 1;
        });
        when(stepMapper.selectList(any())).thenReturn(List.of());

        AiRunDTO waitingRun = orchestratorService.executeRun(new AiRunCreateRequest(
                "deepseek-chat", null, "请清理租户缓存", "platform.cache.evict", Map.of("scope", "rate_limit")
        ));

        assertThat(waitingRun.status()).isEqualTo("WAITING_APPROVAL");
        assertThat(highRiskExecCount.get()).isZero();

        // Prepare stored entities for approval
        AiRunEntity storedRun = new AiRunEntity();
        storedRun.setId(1001L);
        storedRun.setTenantId(1L);
        storedRun.setRunNo(waitingRun.runNo());
        storedRun.setModelId("deepseek-chat");
        storedRun.setStatus("WAITING_APPROVAL");
        storedRun.setPromptTokens(0);
        storedRun.setCompletionTokens(0);

        AiToolCallEntity storedCall = new AiToolCallEntity();
        storedCall.setId(2001L);
        storedCall.setTenantId(1L);
        storedCall.setRunId(1001L);
        storedCall.setCallNo("tc_test");
        storedCall.setToolCode("platform.cache.evict");
        storedCall.setRiskLevel("HIGH");
        storedCall.setPermissionCode("system:config:query");
        storedCall.setApprovalStatus("PENDING_APPROVAL");
        storedCall.setExecutionStatus("PENDING");
        storedCall.setArgumentsSummary("{\"scope\":\"rate_limit\"}");

        when(toolCallMapper.selectById(2001L)).thenReturn(storedCall);
        when(runMapper.selectById(1001L)).thenReturn(storedRun);

        // Cross-tenant approver must be blocked
        AuthContext.set(principal(999L, 88L, List.of("system:config:query")));
        assertThatThrownBy(() -> orchestratorService.approveToolCall(2001L, "cross-tenant"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo("AUTH_FORBIDDEN");
        assertThat(highRiskExecCount.get()).isZero();

        // Same-tenant operator without permission is blocked
        AuthContext.set(principal(1L, 88L, List.of("other:perm")));
        assertThatThrownBy(() -> orchestratorService.approveToolCall(2001L, "no-perm"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo("AUTH_FORBIDDEN");

        // Authorized same-tenant approval executes tool once
        AuthContext.set(principal(1L, 88L, List.of("system:config:query")));
        AiRunDTO approvedRun = orchestratorService.approveToolCall(2001L, "审核通过");
        assertThat(approvedRun.status()).isEqualTo("SUCCEEDED");
        assertThat(highRiskExecCount.get()).isEqualTo(1);
        assertThat(storedCall.getExecutionStatus()).isEqualTo("SUCCEEDED");

        // Replay / double-approve blocked by idempotency protection
        assertThatThrownBy(() -> orchestratorService.approveToolCall(2001L, "replay"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo("AI_TOOL_CALL_ALREADY_EXECUTED");
        assertThat(highRiskExecCount.get()).isEqualTo(1);
        verify(meteringQuotaService, times(1)).recordUsage(any(), any(), any(), any(), any(int.class), any(int.class), any(long.class));
    }

    @Test
    void canUpdateToolWhitelistAndRejectPendingToolCall() {
        AiToolEntity tool = new AiToolEntity();
        tool.setId(7L);
        tool.setToolCode("platform.audit.recent");
        tool.setWhitelisted(0);
        tool.setRiskLevel("LOW");
        when(toolMapper.selectById(7L)).thenReturn(tool);

        var updated = mcpGovernanceService.updateToolGovernance(7L, new AiToolWhitelistRequest(true, "READ_ONLY"));
        assertThat(updated.whitelisted()).isTrue();
        assertThat(updated.riskLevel()).isEqualTo("READ_ONLY");

        AiRunEntity run = new AiRunEntity();
        run.setId(3001L);
        run.setTenantId(1L);
        run.setStatus("WAITING_APPROVAL");
        AiToolCallEntity call = new AiToolCallEntity();
        call.setId(4001L);
        call.setRunId(3001L);
        call.setApprovalStatus("PENDING_APPROVAL");
        when(toolCallMapper.selectById(4001L)).thenReturn(call);
        when(runMapper.selectById(3001L)).thenReturn(run);
        when(stepMapper.selectList(any())).thenReturn(List.of());
        when(toolCallMapper.selectList(any())).thenReturn(List.of(call));

        AiRunDTO rejected = orchestratorService.rejectToolCall(4001L, "风险操作不予批准");
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(call.getExecutionStatus()).isEqualTo("SKIPPED");
    }

    private AuthPrincipal principal(Long tenantId, Long userId, List<String> perms) {
        return new AuthPrincipal(
                1L, tenantId, "t" + tenantId, userId, "user" + userId, "User",
                "STAFF", "ONLINE", LocalDateTime.now().plusHours(1),
                perms, List.of(), List.of(), List.of(),
                UUID.randomUUID(), UUID.randomUUID(), null, null, null
        );
    }
}
