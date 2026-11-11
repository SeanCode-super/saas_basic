package com.saasbasics.platform.modules.ai.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.ai.api.AiToolExecutionResult;
import com.saasbasics.platform.modules.ai.api.AiToolHandler;
import com.saasbasics.platform.modules.ai.application.dto.AiMcpProbeResultDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiMcpServerDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiMcpServerSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiToolDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiToolWhitelistRequest;
import com.saasbasics.platform.modules.ai.domain.CredentialResolver;
import com.saasbasics.platform.modules.ai.infrastructure.mcp.McpClientAdapter;
import com.saasbasics.platform.modules.ai.infrastructure.mcp.McpDiscoveredTool;
import com.saasbasics.platform.modules.ai.infrastructure.mcp.McpProbeResult;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiMcpServerEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiToolEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiMcpServerMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiToolMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MCP 服务器连接、工具发现同步与默认拒绝白名单风控治理服务。
 * 严格遵循 AI_CORE_DESIGN 安全约束：MCP 服务端返回的描述不能覆盖平台配置的权限码与风险等级。
 */
@Service
public class AiMcpGovernanceService {

    private final AiMcpServerMapper mcpServerMapper;
    private final AiToolMapper toolMapper;
    private final McpClientAdapter mcpClientAdapter;
    private final CredentialResolver credentialResolver;
    private final ObjectMapper objectMapper;
    private final Map<String, AiToolHandler> internalHandlerMap;

    public AiMcpGovernanceService(
            AiMcpServerMapper mcpServerMapper,
            AiToolMapper toolMapper,
            McpClientAdapter mcpClientAdapter,
            CredentialResolver credentialResolver,
            ObjectMapper objectMapper,
            List<AiToolHandler> internalHandlers
    ) {
        this.mcpServerMapper = mcpServerMapper;
        this.toolMapper = toolMapper;
        this.mcpClientAdapter = mcpClientAdapter;
        this.credentialResolver = credentialResolver;
        this.objectMapper = objectMapper;
        this.internalHandlerMap = internalHandlers == null
                ? Map.of()
                : internalHandlers.stream().collect(java.util.stream.Collectors.toMap(
                AiToolHandler::toolCode, h -> h, (a, b) -> a));
    }

    public List<AiMcpServerDTO> listMcpServers() {
        return mcpServerMapper.selectList(
                new LambdaQueryWrapper<AiMcpServerEntity>().orderByAsc(AiMcpServerEntity::getId)
        ).stream().map(this::toServerDto).toList();
    }

    @Transactional
    public Long saveMcpServer(AiMcpServerSaveRequest req) {
        if (req.serverCode() == null || req.serverCode().isBlank()) {
            throw new BizException("VALIDATION_ERROR", "MCP 服务编码不能为空");
        }
        if (req.serverName() == null || req.serverName().isBlank()) {
            throw new BizException("VALIDATION_ERROR", "MCP 服务名称不能为空");
        }
        if (req.endpointUrl() == null || req.endpointUrl().isBlank()) {
            throw new BizException("VALIDATION_ERROR", "MCP 端点 URL 不能为空");
        }

        AiMcpServerEntity entity;
        if (req.id() == null) {
            entity = new AiMcpServerEntity();
            entity.setTenantId(currentTenantId());
            entity.setHealthStatus("UNKNOWN");
        } else {
            entity = mcpServerMapper.selectById(req.id());
            if (entity == null) {
                throw new BizException("AI_MCP_NOT_FOUND", "未找到 MCP 服务配置: " + req.id());
            }
        }

        entity.setServerCode(req.serverCode().trim());
        entity.setServerName(req.serverName().trim());
        entity.setTransportType(req.transportType() != null ? req.transportType() : "STREAMABLE_HTTP");
        entity.setEndpointUrl(req.endpointUrl().trim());
        entity.setAuthHeaderName(req.authHeaderName() != null && !req.authHeaderName().isBlank()
                ? req.authHeaderName().trim() : "Authorization");
        if (req.authToken() != null && !req.authToken().isBlank() && !req.authToken().contains("****")) {
            entity.setEncryptedAuthToken(credentialResolver.sealSecret(req.authToken().trim()));
        }
        if (req.allowedTools() != null) {
            try {
                entity.setAllowedToolsJson(objectMapper.writeValueAsString(req.allowedTools()));
            } catch (Exception e) {
                entity.setAllowedToolsJson("[]");
            }
        } else if (entity.getAllowedToolsJson() == null) {
            // Default deny-all policy when no tools explicitly whitelisted
            entity.setAllowedToolsJson("[]");
        }
        entity.setStatus(req.status() != null ? req.status() : "ENABLED");
        entity.setRemark(req.remark());

        if (entity.getId() == null) {
            mcpServerMapper.insert(entity);
        } else {
            mcpServerMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Transactional
    public void deleteMcpServer(Long id) {
        mcpServerMapper.deleteById(id);
    }

    @Transactional
    public AiMcpProbeResultDTO probeAndSyncTools(Long serverId) {
        AiMcpServerEntity server = mcpServerMapper.selectById(serverId);
        if (server == null) {
            throw new BizException("AI_MCP_NOT_FOUND", "未找到 MCP 服务器: " + serverId);
        }

        Set<String> whitelist = mcpClientAdapter.parseWhitelist(server.getAllowedToolsJson());
        McpProbeResult probe = mcpClientAdapter.probeAndDiscover(
                server.getTransportType(),
                server.getEndpointUrl(),
                server.getAuthHeaderName(),
                server.getEncryptedAuthToken(),
                whitelist
        );

        server.setHealthStatus(probe.success() ? "ONLINE" : "OFFLINE");
        server.setLastCheckedAt(LocalDateTime.now());
        mcpServerMapper.updateById(server);

        if (probe.success() && probe.discoveredTools() != null) {
            syncDiscoveredToolsWithoutElevatingPrivilege(server, probe.discoveredTools(), whitelist);
        }

        return new AiMcpProbeResultDTO(
                probe.success(),
                probe.protocolVersion(),
                probe.serverTitle(),
                probe.latencyMs(),
                probe.message(),
                probe.discoveredTools()
        );
    }

    public List<AiToolDTO> listTools() {
        return toolMapper.selectList(
                new LambdaQueryWrapper<AiToolEntity>().orderByAsc(AiToolEntity::getId)
        ).stream().map(this::toToolDto).toList();
    }

    @Transactional
    public AiToolDTO updateToolGovernance(Long toolId, AiToolWhitelistRequest req) {
        AiToolEntity tool = toolMapper.selectById(toolId);
        if (tool == null) {
            throw new BizException("AI_TOOL_NOT_FOUND", "未找到工具配置: " + toolId);
        }
        if (req.whitelisted() != null) {
            tool.setWhitelisted(req.whitelisted() ? 1 : 0);
            if (tool.getMcpServerId() != null) {
                syncServerWhitelistArray(tool.getMcpServerId(), tool.getToolCode(), req.whitelisted());
            }
        }
        if (req.riskLevel() != null && !req.riskLevel().isBlank()) {
            tool.setRiskLevel(req.riskLevel().toUpperCase());
        }
        toolMapper.updateById(tool);
        return toToolDto(tool);
    }

    public AiToolEntity findToolByCode(String toolCode) {
        return toolMapper.selectOne(
                new LambdaQueryWrapper<AiToolEntity>()
                        .eq(AiToolEntity::getToolCode, toolCode)
                        .last("LIMIT 1")
        );
    }

    public AiToolExecutionResult executeAuthorizedTool(
            AiToolEntity tool,
            Long tenantId,
            Long userId,
            Map<String, Object> arguments
    ) {
        if (tool.getWhitelisted() == null || tool.getWhitelisted() != 1) {
            return AiToolExecutionResult.error("安全拒止：工具 [" + tool.getToolCode() + "] 未被平台列入白名单");
        }
        if ("MCP".equalsIgnoreCase(tool.getAdapterType()) && tool.getMcpServerId() != null) {
            AiMcpServerEntity server = mcpServerMapper.selectById(tool.getMcpServerId());
            if (server == null || !"ENABLED".equalsIgnoreCase(server.getStatus())) {
                return AiToolExecutionResult.error("所属 MCP 服务器不存在或已停用");
            }
            Set<String> whitelist = mcpClientAdapter.parseWhitelist(server.getAllowedToolsJson());
            return mcpClientAdapter.invokeTool(
                    server.getTransportType(),
                    server.getEndpointUrl(),
                    server.getAuthHeaderName(),
                    server.getEncryptedAuthToken(),
                    whitelist,
                    tool.getToolCode(),
                    tenantId,
                    userId,
                    arguments
            );
        }

        AiToolHandler handler = internalHandlerMap.get(tool.getToolCode());
        if (handler == null) {
            return AiToolExecutionResult.error("未找到对应的平台内部 Facade 工具实现: " + tool.getToolCode());
        }
        return handler.execute(tenantId, userId, arguments);
    }

    private void syncDiscoveredToolsWithoutElevatingPrivilege(
            AiMcpServerEntity server,
            List<McpDiscoveredTool> discoveredTools,
            Set<String> serverWhitelist
    ) {
        for (McpDiscoveredTool d : discoveredTools) {
            AiToolEntity existing = toolMapper.selectOne(
                    new LambdaQueryWrapper<AiToolEntity>()
                            .eq(AiToolEntity::getToolCode, d.name())
                            .last("LIMIT 1")
            );
            if (existing == null) {
                // New external MCP tools default to whitelisted=false (deny-all) & HIGH risk unless in explicit server whitelist
                AiToolEntity created = new AiToolEntity();
                created.setTenantId(server.getTenantId());
                created.setToolCode(d.name());
                created.setToolName(d.name());
                created.setSemanticVersion("1.0.0");
                created.setDescription(d.description());
                created.setInputSchemaJson(d.inputSchemaJson());
                created.setOutputSchemaJson("{}");
                created.setPermissionCode("system:config:query");
                created.setRiskLevel("HIGH");
                created.setAdapterType("MCP");
                created.setMcpServerId(server.getId());
                created.setWhitelisted(serverWhitelist.contains(d.name()) ? 1 : 0);
                created.setIdempotent(1);
                created.setTimeoutMs(5000);
                created.setStatus("ENABLED");
                toolMapper.insert(created);
            } else {
                // Refresh description/schema ONLY — NEVER allow server metadata to downgrade risk_level or alter permissionCode!
                if (d.description() != null && !d.description().isBlank()) {
                    existing.setDescription(d.description());
                }
                if (d.inputSchemaJson() != null && !d.inputSchemaJson().isBlank()) {
                    existing.setInputSchemaJson(d.inputSchemaJson());
                }
                toolMapper.updateById(existing);
            }
        }
    }

    private void syncServerWhitelistArray(Long mcpServerId, String toolCode, boolean add) {
        AiMcpServerEntity server = mcpServerMapper.selectById(mcpServerId);
        if (server == null) {
            return;
        }
        List<String> list;
        try {
            list = server.getAllowedToolsJson() != null
                    ? new ArrayList<>(objectMapper.readValue(server.getAllowedToolsJson(), new TypeReference<List<String>>() {}))
                    : new ArrayList<>();
        } catch (Exception e) {
            list = new ArrayList<>();
        }
        if (add && !list.contains(toolCode)) {
            list.add(toolCode);
        } else if (!add) {
            list.remove(toolCode);
        }
        try {
            server.setAllowedToolsJson(objectMapper.writeValueAsString(list));
            mcpServerMapper.updateById(server);
        } catch (Exception ignored) {
        }
    }

    private AiMcpServerDTO toServerDto(AiMcpServerEntity e) {
        String token = e.getEncryptedAuthToken();
        boolean hasToken = token != null && !token.isBlank();
        List<String> whitelist = List.copyOf(mcpClientAdapter.parseWhitelist(e.getAllowedToolsJson()));
        return new AiMcpServerDTO(
                e.getId(),
                e.getTenantId(),
                e.getServerCode(),
                e.getServerName(),
                e.getTransportType(),
                e.getEndpointUrl(),
                e.getAuthHeaderName(),
                hasToken,
                hasToken ? credentialResolver.maskSecret(token) : "无认证头",
                whitelist,
                e.getHealthStatus(),
                e.getLastCheckedAt(),
                e.getStatus(),
                e.getRemark()
        );
    }

    private AiToolDTO toToolDto(AiToolEntity t) {
        return new AiToolDTO(
                t.getId(),
                t.getTenantId(),
                t.getToolCode(),
                t.getToolName(),
                t.getSemanticVersion(),
                t.getDescription(),
                t.getInputSchemaJson(),
                t.getPermissionCode(),
                t.getRiskLevel(),
                t.getAdapterType(),
                t.getMcpServerId(),
                t.getWhitelisted() != null && t.getWhitelisted() == 1,
                t.getIdempotent() != null && t.getIdempotent() == 1,
                t.getTimeoutMs(),
                t.getStatus()
        );
    }

    private Long currentTenantId() {
        AuthPrincipal principal = AuthContext.get();
        return principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
    }
}
