# SaaS Basics 数据库模块边界设计

> 历史设计输入：现有 V1-V24 迁移继续保留，新的企业组织模型、AI 模块和数据库约束以 [`V0.1_EXECUTION_BLUEPRINT.md`](./V0.1_EXECUTION_BLUEPRINT.md) 为准。

## 1. 结论

后端已经改成多模块，因此数据库表也必须按模块边界重新定义“归属关系”。

但这里说的“重设计”不是一上来拆成多库多实例，而是：

- 逻辑上按模块拥有表
- 命名上按模块前缀分区
- 迁移上按模块顺序演进
- 物理上当前仍可先落在同一个 `saas_basics` 库里

也就是说：

- 现在适合做“单库多模块”
- 不适合现在就做“多库微服务”

## 2. 为什么表也要按模块重设计

如果后端已经是多模块，但数据库还是“一整坨公共表”，很快会出现：

- 模块责任不清
- 改一个表影响多个模块
- mapper 和 service 边界变脏
- Flyway 迁移不可维护
- 后续拆服务几乎无法平滑演进

所以多模块架构必须配套“模块化数据库边界”。

## 3. 当前建议的数据库原则

### 3.1 物理层

- 当前统一使用一个 MySQL 8.3 数据库：`saas_basics`
- 当前阶段不强制拆 schema
- 当前阶段不强制按模块拆库

### 3.2 逻辑层

每张表必须有清晰的模块归属，只允许一个主模块拥有该表。

其它模块如果用到该数据：

- 通过服务接口访问
- 通过只读查询 DTO 访问
- 不要把表随意当成“公共资源”

### 3.3 迁移层

Flyway 后续建议按模块化命名追加迁移：

- `V2_01__tenant_xxx.sql`
- `V2_02__iam_xxx.sql`
- `V2_03__system_xxx.sql`
- `V2_04__integration_xxx.sql`

当前的：

- `V1__enterprise_saas_foundation.sql`

仍作为第一版总基线保留。

## 4. 模块与表归属

## 4.1 平台与租户模块

模块：

- `saas-basics-module-tenant`

主表：

- `plat_tenant`
- `plat_tenant_package`
- `plat_tenant_quota`
- `plat_tenant_app`
- `plat_tenant_setting`
- `plat_feature_flag`

职责：

- 租户生命周期
- 套餐与配额
- 应用开通
- 功能开关

## 4.2 IAM 模块

模块：

- `saas-basics-module-iam`

主表：

- `iam_user`
- `iam_user_identity`
- `iam_employee`
- `iam_department`
- `iam_position`
- `iam_role`
- `iam_role_group`
- `iam_user_group`
- `iam_user_group_member`
- `iam_user_role`
- `iam_role_menu`
- `iam_role_api`
- `iam_data_scope`
- `iam_menu`
- `iam_api_resource`
- `iam_password_policy`
- `iam_login_policy`
- `iam_auth_policy`
- `iam_session`
- `iam_password_history`
- `iam_login_fail_stat`

职责：

- 账号
- 员工
- 组织
- 角色
- API 权限
- 数据权限

## 4.3 系统配置模块

模块：

- `saas-basics-module-system`

主表：

- `sys_dict_type`
- `sys_dict_item`
- `sys_config`
- `sys_i18n_message`
- `sys_sequence_rule`

职责：

- 字典
- 配置
- 国际化
- 编码规则

## 4.4 集成模块

模块：

- `saas-basics-module-integration`

主表：

- `int_datasource`
- `int_datasource_prop`
- `int_api_client`
- `int_webhook_endpoint`
- `int_integration_log`
- `int_datasource_health_log`

职责：

- 数据源
- API 客户端
- Webhook
- 集成日志

## 4.5 文件模块

模块：

- `saas-basics-module-file`

主表：

- `file_storage`
- `file_bucket`
- `file_object`
- `file_relation`
- `file_access_log`
- `file_upload_session`

职责：

- 存储器
- 文件对象
- 文件引用
- 文件审计

## 4.6 调度模块

模块：

- `saas-basics-module-scheduler`

主表：

- `sched_executor`
- `sched_job`
- `sched_job_trigger`
- `sched_job_log`
- `sched_job_alarm`

职责：

- 执行器
- 任务
- 触发器
- 执行日志

## 4.7 代码生成模块

模块：

- `saas-basics-module-codegen`

主表：

- `gen_project`
- `gen_module`
- `gen_table`
- `gen_column`
- `gen_index`
- `gen_template`
- `gen_run_record`
- `gen_sql_artifact`

职责：

- 元数据建模
- 模板定义
- SQL 与代码生成记录

## 4.8 审计模块

模块：

- `saas-basics-module-audit`

主表：

- `audit_login_log`
- `audit_operation_log`
- `audit_event`
- `sec_risk_rule`
- `sec_blacklist`
- `audit_security_incident`

职责：

- 登录审计
- 操作审计
- 风控规则
- 黑名单

## 5. 表设计还要进一步调整的地方

当前 SQL 已经进入第二阶段：`V1` 提供全量基线，`V2` 已经开始按模块补齐增强表。

但如果按多模块长期演进，仍建议继续优化：

### tenant

- 套餐能力建议拆成更细粒度的套餐功能表
- 配额建议增加周期维度和超限策略

### iam

- `V2` 已补齐用户组、密码策略、登录策略、授权策略
- `V3` 已补齐登录会话、密码历史、登录失败统计
- 角色继承、授权模板、会话明细表还可以继续补

### system

- 功能开关未来建议从 `plat_feature_flag` 再加规则明细表
- 字典建议增加覆盖链模型

### integration

- `V2` 已补齐数据源检测记录表
- 凭证轮换历史表还没补

### file

- `V2` 已补齐上传会话表
- 文件版本表建议从 `file_object` 中再抽一个版本明细表

### scheduler

- `V2` 已补齐任务告警规则表
- 任务补偿策略表还没补

### codegen

- `V2` 已补齐 SQL 产物表
- 模板变量、模板组、生成规则表还可以继续细化

### audit

- `V2` 已补齐安全事件表
- 黑名单命中记录和事件处置流转表还可以继续细化

## 6. 推荐执行策略

下一步不是推倒重来，而是这样推进：

1. 保留当前 V1 基线 SQL 不动
2. 后续新表和表结构调整按模块迁移追加
3. 代码层严格按模块拥有表
4. 先把 `tenant / iam / system / integration / file / scheduler / codegen` 的基础 CRUD 做实
5. 之后继续细化认证、会话、安全事件和模板执行链路

## 7. 当前结论

所以答案是：

- 是，表必须按多模块重新定义边界
- 但不是现在就拆成多个数据库
- 当前最优解是“单库、按模块拥有表、按模块演进迁移”
