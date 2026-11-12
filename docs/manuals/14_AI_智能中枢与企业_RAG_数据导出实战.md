# 14. AI 智能中枢、企业 RAG 与全局数据导出套打实战

> **所属专栏**：天枢云基 PaaS 企业级数字化底座 · 第三卷 (业务功能与智能引擎实战)  
> **文档版本**：v1.0.0 (Release)  
> **阅读对象**：AI 应用架构师、平台管理员、业务分析师、全栈研发工程师

---

## 1. AI 智能中枢全景架构 (`saas-basics-module-ai`)

天枢云基在 `V63`–`V64` 与 `V71`–`V73` 中持续演进，构建了多租户隔离、模型厂商解耦的**企业级 AI 智能中枢**，PC 端对应路由入口涵盖模型网关配置、AI 数据副驾驶、企业知识库、智能体工坊以及微调与评测中心：

| 能力模块 | 前端核心视图 (`web/src/views/`) | 核心后端控制器与服务 | 核心数据表 (`V63`–`V73`) |
| :--- | :--- | :--- | :--- |
| **多云模型与网关配置** | `system/AiModelConfigView.vue` | `AiManageController`, `AiGovernanceController` | `ai_provider`, `ai_model_config`, `ai_credential_ref`, `ai_tenant_quota` |
| **⌘K 指令与 NL2SQL 副驾驶** | `CommandPaletteDialog.vue` / `ai/AiDataCopilotView.vue` | `AiCommandController` (`/api/ai/command/*`, `/api/ai/nl2sql/*`) | `ai_command_history` |
| **企业知识库 (Hybrid RAG)** | `ai/KnowledgeBaseView.vue` | `KnowledgeBaseController` (`/api/ai/knowledge/*`) | `ai_knowledge_base`, `ai_knowledge_doc`, `ai_knowledge_chunk` |
| **可视化智能体工坊 (Agent Studio)** | `ai/AgentStudioView.vue` | `AgentAndFinetuneController` (`/api/ai/agents*`, `/api/ai/copilot/crm-suggestion`) | `ai_agent`, `ai_agent_session` |
| **微调数据集与 LoRA 评测** | `ai/FinetuneStudioView.vue` | `AgentAndFinetuneController` (`/api/ai/finetune/*`) | `ai_finetune_dataset`, `ai_finetune_sample`, `ai_finetune_job` |

> **安全边界**：所有外部模型服务商 API Key 与 MCP Server 凭证均经 `Vault` AES-256-GCM 加密托管，租户配额通过 Redis Lua 滑动窗口按 RPM 实时保护（超限返回 HTTP `429`）。

---

## 2. ⌘K 全局指令与 NL2SQL 数据副驾驶 (`V71`)

### 2.1 全局智能指令面板 (`CommandPaletteDialog.vue`)
* 在控制台任意页面按 `⌘K` / `Ctrl+K` 唤醒全局指令面板；
* 请求发送至 `POST /api/ai/command/interpret`，识别导航意图、快捷操作或自然语言取数请求，并持久化至 `ai_command_history`（可通过 `GET /api/ai/command/history` 检索最近执行轨迹）。

### 2.2 NL2SQL 自然语言取数 (`POST /api/ai/nl2sql/query`)
* 结合 `AiSchemaCatalogService` 自动感知白名单业务表结构；
* 将中文问题转译为受租户隔离约束的只读 `SELECT` 查询，返回结构化结果集与耗时指标；
* 受 `@RateLimit(type = RateLimitType.TENANT, permits = 60, windowSeconds = 60)` 限流保护。

---

## 3. 企业知识库 Hybrid RAG 与文档合规审查 (`V72`)

1. **知识库与文档切片入库**：
   - 通过 `POST /api/ai/knowledge/bases` 创建多租户知识库；
   - 支持文档上传（`POST /api/ai/knowledge/docs/upload`，关联 `AI_KNOWLEDGE` 业务上传路由保存原始文件）或纯文本摄入（`POST /api/ai/knowledge/docs/text`），自动切分为 `ai_knowledge_chunk` 片段。
2. **混合检索与溯源问答**：
   - `POST /api/ai/knowledge/search` 与 `POST /api/ai/knowledge/ask` 返回带原文引用 (`KnowledgeCitationDTO`) 的精准回答，并记录来源文档与分片追踪信息。
3. **文档合规红线审查**：
   - 支持合同与制度文本提交 `POST /api/ai/knowledge/review-document` 进行智能条款核查与风险提示。

---

## 4. 智能体工坊 (Agent Studio) 与微调数据闭环 (`V72`–`V73`)

### 4.1 Visual Agent Studio & CRM Copilot
* 管理员可在 `AgentStudioView.vue` 编排智能体人设、绑定工具目录 (`GET /api/ai/agents/tools`) 并通过 `POST /api/ai/agents/chat` 开展多轮会话调试（会话持久化于 `ai_agent_session`）；
* 在 CRM 客户跟进场景中，集成 `GET /api/ai/copilot/crm-suggestion` 提供上下文感知的商机跟进建议。

### 4.2 微调样本沉淀与 LoRA 任务台账
* **NL2SQL 历史自动采集**：通过 `POST /api/ai/finetune/datasets/{datasetId}/harvest-nl2sql` 一键将高质量历史自然语言取数记录采集为微调候选样本，也可手工增补；
* **审核与多格式导出**：支持 `POST /api/ai/finetune/samples/{id}/review` 核准样本，并导出 `ALPACA` 或 `ShareGPT` 格式的 `.jsonl` 数据集（`GET /api/ai/finetune/datasets/{datasetId}/export-jsonl`）；
* **训练任务与评测记录**：通过 `POST /api/ai/finetune/jobs` 创建与追踪 LoRA 微调作业及评估指标（说明：当前阶段主要完成样本清洗、台账跟踪与基准对比编排，实际外部 GPU 训练器集群接入需按部署环境对接）。

---

## 5. 全局数据导出下载中心与单据套打中心

### 5.1 全局异步导出与下载中心 (`saas-basics-module-file`, `V71`–`V72`)
对应控制台统一弹窗组件 `ExportDialog.vue` 与专属下载中心视图 `export/DownloadCenterView.vue`（后端 `/api/exports/*`）：
* **同步与异步双通道**：支持小数据集直出下载（`POST /api/exports/sync`，返回 `X-Export-Trace-Code` 与 `X-Export-Sha256` 校验头）与大批量后台异步导出队列（`POST /api/exports/async`）；
* **字段自定义、水印与脱敏**：支持保存导出字段模板（`sys_export_template`）、自动嵌入租户/操作人水印文本、敏感字段脱敏标记以及敏感导出审批（`/tasks/{id}/approve`、`/reject`）；
* **定时报表投递 (`ReportScheduleService`)**：通过 `sys_report_schedule`（`/api/exports/schedules` 与 `/schedules/{id}/trigger`）按策略生成周期性统计报表并归档至下载中心。

### 5.2 单据打印与套打中心 (`report/PrintCenterView.vue`)
* 覆盖 **采购入库单 (`PURCHASE_IN`)、费用报销单 (`REIMBURSE`)、物流面单 (`SHIPPING_LABEL`)、增值税发票 (`VAT_INVOICE`)** 四类标准单据样式；
* 支持实时拉取真实 ERP 采购订单与 CRM 合同数据填充套打预览，支持 `A4 / A5` 纸张切换、防伪水印及电子公章显示控制。
