# AI 核心模块 API 与 SPI 评审稿

状态：`Proposed`

目标里程碑：`M3`

## 1. 交付边界

首个闭环只包含模型注册、凭证引用、Prompt 版本、工具注册、MCP 连接、Run/Step/ToolCall、用量审计和一个最小知识库。AI 模块不是聊天页面，也不直接读取其它模块的 Mapper。

## 2. 模块结构

```text
saas-basics-module-ai/
  api/              AI Facade、工具协议、事件
  application/      Run 编排、策略和事务
  domain/           Model、Prompt、Agent、Tool、Run 状态机
  infrastructure/   Provider、MCP、Vector Store、凭证适配器
  web/              管理 API 和运行 API
```

行业模块通过 `ai/api` 注册工具和知识权限过滤器。AI 模块通过公开业务 Facade 执行工具，不依赖行业表。

## 3. 核心数据模型

所有业务表必须包含 `tenant_id`。

| 表 | 内容 |
|---|---|
| `ai_provider` | 供应商类型、端点、状态、超时和租户配置 |
| `ai_credential_ref` | 密钥服务引用、掩码、轮换状态，不保存可回显明文 |
| `ai_model` | 模型标识、能力、上下文、价格、限额和路由权重 |
| `ai_prompt` / `ai_prompt_version` | Prompt 元数据、不可变版本、发布指针 |
| `ai_agent` | Agent 定义、模型策略、系统 Prompt 和状态 |
| `ai_tool` | 工具编码、Schema、权限码、风险等级、幂等能力 |
| `ai_agent_tool` | Agent 可用工具白名单 |
| `ai_mcp_server` | MCP 连接、认证引用、允许工具和健康状态 |
| `ai_run` | 请求者、应用、上下文快照、状态、Token、费用和延迟 |
| `ai_run_step` | 模型、检索、工具、审批等步骤 |
| `ai_tool_call` | 输入摘要、输出摘要、权限结果、审批和幂等键 |
| `ai_knowledge_base` | 知识库、嵌入模型、向量命名空间和权限策略 |
| `ai_document` / `ai_chunk` | 文档版本、切片、来源、权限元数据和索引状态 |

模型响应原文、Prompt 和工具参数按数据分类策略决定是否保存；默认脱敏并设置保留期。

## 4. 公开 API 草案

管理面：

- `/api/ai/providers`
- `/api/ai/models`
- `/api/ai/prompts` 与 `/versions`
- `/api/ai/agents`
- `/api/ai/tools`
- `/api/ai/mcp-servers`
- `/api/ai/knowledge-bases`

运行面：

- `POST /api/ai/runs`
- `GET /api/ai/runs/{runId}`
- `POST /api/ai/runs/{runId}/cancel`
- `POST /api/ai/tool-calls/{callId}/approve`
- `POST /api/ai/tool-calls/{callId}/reject`

创建 Run 时不接受 `tenantId`、`userId` 或权限集合，它们来自当前安全上下文并固化为快照。

## 5. Provider SPI

领域层只依赖以下能力接口：

- `ChatModelProvider`：流式或非流式生成、工具调用、结构化输出。
- `EmbeddingProvider`：批量嵌入、维度和最大批量声明。
- `ModelCapability`：上下文、视觉、工具、JSON、流式能力描述。
- `CredentialResolver`：按租户解析短生命周期凭证，禁止返回到 API。
- `VectorStore`：upsert、delete、query，必须接受租户命名空间和权限过滤条件。

供应商 SDK 只能出现在 infrastructure adapter。统一错误分为超时、限流、认证、内容安全、不可重试和供应商异常。

## 6. 工具与 MCP 协议

工具注册必须声明：

- 全局唯一工具编码和语义版本
- JSON Schema 输入输出
- 对应 API 权限码和数据权限资源
- `READ_ONLY`、`LOW`、`HIGH` 风险等级
- 幂等支持、超时、最大输出和脱敏规则
- 执行适配器：平台内部 Facade 或 MCP server/tool

MCP 服务器默认拒绝全部工具，只允许显式白名单。服务端返回的工具描述不能覆盖平台配置的权限码和风险等级。

高风险写操作生成 `PENDING_APPROVAL` ToolCall；批准人不能由模型指定。审批后执行仍需重新校验会话、租户、权限、数据范围和幂等键。

## 7. Run 状态机

```text
CREATED -> RUNNING -> WAITING_APPROVAL -> RUNNING -> SUCCEEDED
                    -> REJECTED
RUNNING -> FAILED | CANCELLED | TIMED_OUT
```

每个步骤记录使用的模型、Prompt 版本、知识库版本、工具版本、Token、费用、延迟、错误分类和重试次数。重试不能重复执行没有幂等保护的写工具。

## 8. 租户与权限

- Run 固化 `tenantId`、`userId`、应用、组织/任职上下文和权限摘要。
- 检索查询在向量存储层先应用租户命名空间和权限过滤，不能检索后再隐藏。
- 工具调用复用普通业务 API 的权限和数据范围，不定义“AI 超级权限”。
- 平台运营人员默认不能查看租户 Prompt、知识内容和 Run 原文。
- 引用返回文档 ID、版本、切片和可验证来源；无权限来源不得出现在引用中。

## 9. 首个垂直闭环

1. 配置一个 OpenAI-compatible 远端端点和一个本地兼容端点。
2. 发布一个版本化 Prompt。
3. 注册一个只读平台工具和一个 MCP 工具。
4. 创建 Run，记录模型调用、工具参数、权限结果、Token、费用和延迟。
5. 建立最小知识库，按租户和权限返回引用。
6. 用一个高风险写工具验证人工确认、幂等和拒绝路径。

## 10. 评审通过条件

- 领域代码不引用任何供应商 SDK。
- Provider、MCP 和 Vector Store 都能用契约测试替换实现。
- 两个租户使用同一模型时，凭证、额度、Run 和向量命名空间完全隔离。
- 工具权限失败、高风险待审批、超时重试和供应商限流都有确定状态与审计记录。
