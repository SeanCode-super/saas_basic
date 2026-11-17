# 05. 数据库生命周期与 Flyway 自动化迁移

> **所属专栏**：天枢云基 PaaS 企业级数字化底座 · 第二卷 (生产部署与运维)  
> **文档版本**：v1.0.0 (Release)  
> **阅读对象**：DBA 数据库管理员、后端研发工程师、DevOps 工程师  

---

## 1. 数据库版本演进规范

天枢云基平台严格遵循 **Flyway 10.x** 数据库版本迁移规范。**严禁在生产数据库直接人工手工执行 DDL/DML 变更。**

### 1.1 脚本命名规范
所有数据库增量迁移脚本统一存放在 `server/saas-basics-app/src/main/resources/db/migration/` 目录下，命名格式为：
```
V{版本号}__{英文下划线业务描述}.sql
```
*例如：`V57__flowable_bpmn_integration.sql`、`V62__app_devops_service_center.sql`、`V74__profile_extensions_and_file_upload_routing.sql`。*

### 1.2 生产不可篡改原则
* 已发布的迁移脚本在发布后，其文本内容的 **Checksum** 会被 Flyway 强校验并记录在数据库的 `flyway_schema_history` 表中；
* 严禁修改任何已执行的历史脚本内容，否则应用启动时 Flyway 会报 Checksum 校验失败阻断启动；
* 任何新字段或表结构调整必须递增创建新版本脚本（如 `V75__xxx.sql`）。

---

## 2. 历史核心版本线速查表 (V1 ~ V74)

| 版本跨度 | 核心业务主题 | 涉及的核心数据表与能力 |
| :--- | :--- | :--- |
| **V1 ~ V6** | 平台基础基石与 IAM 底座 | `sys_user`, `sys_role`, `sys_menu`, `sys_dept`, `sys_tenant` 基础架构 |
| **V17** | 多租户动态门户发现 | `sys_portal_entry`，支持租户配置专属域名、Logo 与个性化设置 |
| **V21 ~ V34** | 字典与三位一体权限树 | `sys_dict_type`, `sys_dict_data`，按钮级权限码与功能菜单深度绑定 |
| **V35 ~ V39** | 工作流单据骨架 | 单据草稿、审核状态机与历史审批记录表 |
| **V43 ~ V47** | 增强租户数据隔离 | 逻辑删除唯一索引防冲突方案、租户级系统配置分离 |
| **V48 ~ V50** | ERP 进销存与 CRM 公海 | `crm_customer`, `crm_lead`, `erp_material`, `erp_stock_record` |
| **V51 ~ V56** | 开放平台与企业 SSO | `open_app`, `open_api_token`, SAML 2.0 / OIDC 身份提供商配置表 |
| **V57** | Flowable BPMN 原生集成 | 集成 Flowable 7.x 原生 28 张核心表 (`ACT_RU_*`, `ACT_HI_*`, `ACT_RE_*`) |
| **V58 ~ V59** | 聚合收银台与账单中心 | `pay_channel`, `pay_order` 聚合支付与财务对账支持 |
| **V60** | 企业级开箱数据种子 | 补充企业级预置演示数据、租户套餐与安全合规策略初始基线 |
| **V61** | ERP 进销存唯一索引加固 | 优化多租户模式下物料编号与出入库流水的逻辑删除唯一索引约束 |
| **V62** | 服务编排与发布中台 | `sys_app_service`, `sys_app_build_record`, `sys_app_artifact` 核心纳管与构建表 |
| **V63** | AI 智能模型网关与多服务商编排 | `ai_provider`, `ai_model_config` 多云大模型接入、凭证隔离、动态参数编排与连通性探针 |
| **V64** | M3 AI 治理、凭据库与配额闭环 | `ai_credential_ref`, `ai_prompt`, `ai_prompt_version`, `ai_mcp_server`, `ai_tool`, `ai_run`, `ai_run_step`, `ai_tool_call`, `ai_tenant_quota` |
| **V65 ~ V66** | 可控接口传输加密与密钥环 | `sys_transport_crypto_policy`, `sys_transport_crypto_key`（X25519 + HKDF-SHA256 + AES-256-GCM 动态策略与防重放） |
| **V67** | TOTP 多因子认证与安全基线 | `iam_user` 新增 `mfa_enabled`, `mfa_secret_enc`, `mfa_recovery_codes_enc`（Vault AES-256-GCM 托管） |
| **V68** | 接口指标持久化与告警规则 | `sys_api_endpoint_stat`, `sys_alert_rule`, `sys_alert_event` 接口遥测、阈值告警与事件确认 |
| **V69** | 事务发件箱与租户灾备快照 | `plat_outbox_event`, `plat_tenant_snapshot_record` 异步事件补偿与租户快照打包恢复 |
| **V70** | 审计变更前后快照 Diff | `audit_operation_log` 增补 `before_json`, `after_json` 字段支撑结构化数据对比 |
| **V71** | AI ⌘K 指令、NL2SQL 与导出下载中心 | `ai_command_history`, `sys_export_task`, `sys_export_template` 自然语言取数与带水印异步导出队列 |
| **V72 ~ V73** | AI 知识库 RAG、Agent 工坊、LoRA 微调与定时报表 | `sys_report_schedule`, `ai_knowledge_base`, `ai_knowledge_doc`, `ai_knowledge_chunk`, `ai_agent`, `ai_agent_session`, `ai_finetune_dataset`, `ai_finetune_sample`, `ai_finetune_job`（含 `V73` 增补 `remark` 列） |
| **V74** | 个人资料云端偏好与文件上传路由 | `iam_user`（`avatar_url`, `gender`, `bio`, `work_location`, `ext_contact`, `locale`, `timezone`, `preferences_json`）、`file_storage`（`is_default`, `upload_mode`, `default_folder`, `path_pattern`, `max_file_mb`, `allowed_exts`）、`file_object`（`storage_id`, `upload_mode`, `target_dir`）与 `file_upload_route` |
