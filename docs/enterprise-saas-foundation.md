# SaaS Basics 企业级底座蓝图

> 历史设计输入：本文档不再作为当前执行基线。当前范围、架构决策和里程碑以 [`V0.1_EXECUTION_BLUEPRINT.md`](./V0.1_EXECUTION_BLUEPRINT.md) 为准。

## 1. 目标定位

`saas_basics` 不是做成“后台管理功能合集”，而是做成一个可持续演进的企业级 SaaS 底座，重点是：

- 多租户隔离能力完整，不只是加一个 `tenant_id`
- 组织、身份、权限、数据权限、应用权限拆分清晰
- 配置、集成、文件、任务、代码生成具备平台化能力
- 支持后续扩展 CRM、ERP、WMS、工单、审批、财务等业务域
- 统一代码风格、元数据建模、SQL 生成、审计留痕、可观测性

这套设计不沿用芋道的模块拼装思路，而是采用“平台底座 + 业务域扩展 + 元数据驱动”的风格。

## 2. 设计原则

### 2.1 架构原则

- 租户优先：所有业务模型默认具备租户上下文
- 平台与租户分层：平台配置、租户配置、应用配置边界清晰
- 元数据驱动：字典、表单、字段、代码生成、数据源、集成统一建模
- 权限四层化：身份认证、功能权限、数据权限、接口权限分开治理
- 可审计：关键配置、权限变更、登录行为、任务执行、文件访问全留痕
- 可灰度：功能开关、租户套餐、应用开通、环境配置可动态控制
- 可集成：消息、Webhook、第三方登录、外部数据源作为标准能力内建

### 2.2 技术取向

建议采用稳定且现代的组合：

- 后端：Java 21 + Spring Boot 3.3.x + Spring Modulith / Spring MVC
- 安全：Spring Security 6 + OAuth2.1 Resource Server + JWT/opaque token 双模式
- 持久层：MyBatis-Plus 3.5.x 或 jOOQ（二选一，优先 MyBatis-Plus + 自研基础层）
- 数据库：MySQL 8.3
- 缓存：Redis 7.x
- 消息：RabbitMQ 或 Kafka，底座先抽象消息端口
- 定时任务：XXL-Job 风格能力自研建模，执行器可本地/远程
- 文件：S3 协议优先，兼容 MinIO、OSS、COS、OBS
- 前端：Vue 3.5 + TypeScript + Vite + Element Plus/Naive UI 深度定制，不走模板站风格
- 代码生成：模板引擎 + 元数据 DSL + SQL/后端/前端联动输出

## 3. 产品风格

我们的风格不是“系统管理中心”，而是“平台运营控制台”：

- 平台中心视角：租户、套餐、应用、环境、策略、集成统一管理
- 业务组织视角：员工、部门、岗位、身份、权限、数据范围、协作关系
- 工程化视角：代码生成、数据源、任务、文件、审计、事件、集成能力内建

## 4. 模块总览

## 4.1 平台核心域

### A. 租户与商业化中心

- 租户管理
- 租户套餐
- 租户配额
- 租户应用开通
- 租户环境参数
- 租户品牌配置
- 租户数据隔离策略
- 租户生命周期管理（试用、正式、冻结、注销）

增强点：

- 支持单租户、多组织、多应用模式
- 支持套餐差异化开通菜单、接口、功能、数据量
- 支持租户级功能开关和容量限制

### B. 身份与访问管理 IAM

- 用户
- 员工档案
- 用户身份（账号密码、手机号、邮箱、OAuth、SSO）
- 角色
- 角色组
- 菜单
- 按钮/操作点
- API 权限
- 数据权限策略
- 部门
- 岗位
- 用户组
- 登录策略
- 密码策略
- 会话管理

增强点：

- “账号”和“员工”解耦
- 一个用户可绑定多个身份凭证
- 角色支持继承、组合、场景化授权
- 数据权限支持部门、本部门及子部门、自定义组织树、本人、动态规则

### C. 平台配置中心

- 系统参数
- 字典类型/字典项
- 国际化文案
- 编码规则/业务序列
- 功能开关
- 页面配置项
- 租户默认参数模板

增强点：

- 字典支持层级、颜色、扩展属性、租户覆盖
- 功能开关支持平台级、租户级、环境级生效

### D. 集成与连接中心

- 数据源配置
- 第三方应用配置
- Webhook 配置
- API 客户端
- OpenAPI 凭证
- 回调签名策略
- 集成日志

增强点：

- 数据源不只是 JDBC，预留 HTTP、MQ、对象存储、FTP 类型
- 密钥统一加密管理
- 支持连通性检测、灰度启用、租户隔离

### E. 文件与资产中心

- 存储器配置
- 文件桶
- 文件对象
- 文件夹
- 文件标签
- 文件授权策略
- 文件引用关系
- 文件版本

增强点：

- 文件对象与业务引用解耦
- 支持去重、秒传、版本化、软删除、审计下载

### F. 调度与自动化中心

- 任务定义
- 执行器节点
- 任务分片
- 任务日志
- 任务重试策略
- 任务告警规则

增强点：

- 支持 CRON、固定频率、延时任务
- 支持租户级启停和任务配额
- 支持幂等键和失败补偿

### G. 代码与元数据中心

- 代码生成项目
- 业务模块定义
- 生成表定义
- 字段定义
- 索引定义
- 模板定义
- 输出策略
- SQL 生成记录

增强点：

- 一次建模，同时输出：
  - MySQL DDL
  - 后端实体/DTO/VO/Mapper/Service/Controller
  - 前端 API/页面/表单/字典/路由
- 支持“底座规范模板”统一代码风格
- 支持字段级 UI 元数据，如查询方式、列表显隐、表单组件

### H. 审计与安全中心

- 登录日志
- 操作日志
- 审计事件
- 安全事件
- 敏感访问记录
- 黑名单/风控策略

增强点：

- 配置变更、权限变更、数据导出、文件下载必须可审计
- 风险登录支持 IP、设备、地域、失败次数判定

## 4.2 未来业务扩展预留

底座阶段就要预留的能力：

- 消息中心：站内信、邮件、短信、Webhook、企微/钉钉/飞书
- 审批中心：流程定义、表单元数据、流程实例
- 表单中心：动态表单、布局、校验规则
- 报表中心：指标、数据集、图表、导出任务
- 标签中心：通用对象标签体系
- 主数据中心：客户、供应商、商品、组织主数据

## 5. 比芋道更强的核心差异

### 5.1 权限模型更细

不是只有“菜单 + 角色 + 用户”：

- 用户账号
- 员工主体
- 身份凭证
- 角色
- 角色组
- 操作权限
- API 权限
- 数据权限
- 功能开关

### 5.2 租户能力更完整

不是“表里带 `tenant_id`”就结束：

- 套餐
- 配额
- 应用开通
- 品牌配置
- 功能开关
- 数据源隔离
- 文件隔离
- 调度隔离

### 5.3 代码生成更工程化

不是只生成 CRUD：

- 生成 SQL
- 生成索引
- 生成审计字段
- 生成租户字段
- 生成字典绑定
- 生成前后端校验规则
- 生成权限点和菜单骨架

### 5.4 数据源能力更平台化

不是单纯配置一个数据库连接：

- 连接测试
- 加密存储
- 环境隔离
- 类型扩展
- 租户绑定
- 用途分类（读库、写库、报表库、集成库）

## 6. 核心表设计分层

### 6.1 平台层

- `plat_tenant`
- `plat_tenant_package`
- `plat_tenant_quota`
- `plat_tenant_app`
- `plat_tenant_setting`
- `plat_feature_flag`

### 6.2 组织与身份层

- `iam_user`
- `iam_user_identity`
- `iam_employee`
- `iam_department`
- `iam_position`
- `iam_role`
- `iam_role_group`
- `iam_user_role`
- `iam_role_menu`
- `iam_role_api`
- `iam_data_scope`
- `iam_menu`
- `iam_api_resource`

### 6.3 配置层

- `sys_dict_type`
- `sys_dict_item`
- `sys_config`
- `sys_i18n_message`
- `sys_sequence_rule`

### 6.4 集成层

- `int_datasource`
- `int_datasource_prop`
- `int_api_client`
- `int_webhook_endpoint`
- `int_integration_log`

### 6.5 文件层

- `file_storage`
- `file_bucket`
- `file_object`
- `file_relation`
- `file_access_log`

### 6.6 调度层

- `sched_job`
- `sched_job_trigger`
- `sched_job_log`
- `sched_executor`

### 6.7 元数据与代码生成层

- `gen_project`
- `gen_module`
- `gen_table`
- `gen_column`
- `gen_index`
- `gen_template`
- `gen_run_record`

### 6.8 审计与安全层

- `audit_login_log`
- `audit_operation_log`
- `audit_event`
- `sec_risk_rule`
- `sec_blacklist`

## 7. 通用字段规范

所有业务表建议统一具备：

- `id bigint unsigned`
- `tenant_id bigint unsigned`
- `created_by bigint unsigned`
- `created_at datetime(3)`
- `updated_by bigint unsigned`
- `updated_at datetime(3)`
- `deleted tinyint(1)`
- `deleted_at datetime(3)`
- `version int unsigned`
- `remark varchar(500)`

说明：

- 平台级公共表可允许 `tenant_id = 0`
- 不强依赖外键，优先保证迁移和扩展灵活性
- 热表必须建立 `(tenant_id, deleted, ...)` 复合索引

## 8. 推荐实现顺序

### Phase 1：企业级底座最小闭环

- 租户中心
- 用户/员工/部门/岗位
- 角色/菜单/API/数据权限
- 字典/配置/序列
- 文件中心
- 定时任务
- 数据源配置
- 审计日志
- 代码生成

### Phase 2：平台增强

- 套餐/配额/应用开通
- 功能开关
- Webhook / API Client
- 登录安全策略
- 文件版本与授权策略
- SQL 生成增强

### Phase 3：业务域支撑

- 表单中心
- 流程中心
- 消息中心
- 报表中心
- 主数据中心

## 9. 当前仓库建议先落地的产物

第一批先做三类交付：

1. 企业级底座数据模型 SQL
2. 模块边界与命名规范文档
3. 代码生成元数据模型与模板规范

本次已先产出第 1、2 项基础版本，其中 SQL 文件位于：

- `/Users/shenzhidan/ideaProject/saas_basics/sql/mysql8.3/0001_enterprise_saas_foundation.sql`
- `/Users/shenzhidan/ideaProject/saas_basics/docs/frontend-vue-architecture.md`
- `/Users/shenzhidan/ideaProject/saas_basics/docs/backend-enterprise-architecture.md`

后续如果你继续推进，我建议下一步直接在这个仓库里继续补：

- Java 后端多模块骨架
- 基础领域模型
- 统一审计字段基类
- MyBatis-Plus 基础设施
- 第一版代码生成 DSL

当前后端已经从单模块重构为 Maven 多模块模式，根目录位于：

- `/Users/shenzhidan/ideaProject/saas_basics/server`

## 10. 前端技术定版

前端底座正式定版为：

- `Vue 3.5 + TypeScript + Vite + Pinia + Vue Router`

不采用 React 的原因不是 React 不行，而是这套系统的核心场景是：

- 中后台控制台
- 权限与组织配置
- 大量表单与表格
- 元数据驱动页面
- 代码生成页面

这类系统更强调：

- 统一规范
- 批量生成
- 团队协作效率
- 维护成本可控

因此前端架构默认围绕 Vue 生态展开，详细规范见：

- `/Users/shenzhidan/ideaProject/saas_basics/docs/frontend-vue-architecture.md`
