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
*例如：`V57__flowable_bpmn_integration.sql`、`V58__payment_center.sql`、`V59__pay_order_remark.sql`。*

### 1.2 生产不可篡改原则
* 已发布的迁移脚本在发布后，其文本内容的 **Checksum** 会被 Flyway 强校验并记录在数据库的 `flyway_schema_history` 表中；
* 严禁修改任何已执行的历史脚本内容，否则应用启动时 Flyway 会报 Checksum 校验失败阻断启动；
* 任何新字段或表结构调整必须递增创建新版本脚本（如 `V60__xxx.sql`）。

---

## 2. 历史核心版本线速查表 (V1 ~ V59)

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
