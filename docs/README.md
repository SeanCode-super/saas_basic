# SaaS Basics 文档入口

## 当前执行基线

- [`DEPLOYMENT_AND_ARCHITECTURE_GUIDE.md`](./DEPLOYMENT_AND_ARCHITECTURE_GUIDE.md)：天枢云基 PaaS 生产部署与系统架构全景白皮书（Nginx 泛域名、Docker Compose 编排、Flyway V1~V74 迁移、传输加密与 MFA、AI 智能中枢、DevOps 服务编排中台、小程序发布）。
- [`V0.1_EXECUTION_BLUEPRINT.md`](./V0.1_EXECUTION_BLUEPRINT.md)：产品边界、企业模型、AI 架构、架构决策、迁移顺序、里程碑和验收标准。
- [`ROADMAP.md`](./ROADMAP.md)：公开里程碑、发布门槛、能力范围和后续平台建设方向。
- [`adr/ADR-013_OPEN_SOURCE_LICENSE.md`](./adr/ADR-013_OPEN_SOURCE_LICENSE.md)：Apache-2.0 许可证决策、适用范围和后续约束。

## 生产实战专栏手册 (15 卷独立知识库)

全套文档已完成企业级模块化重构，支持独立导入语雀 (Yuque) 知识库：

- [01. 平台定位与系统架构全景](./manuals/01_平台定位与系统架构全景.md)
- [02. 核心生产端实机视觉呈现 UI Showcase](./manuals/02_核心生产端实机视觉呈现_UI_Showcase.md)
- [03. Docker Compose 一键生产编排部署](./manuals/03_Docker_Compose_一键生产编排部署.md)
- [04. 生产级 Nginx 泛域名网关与 SSL 配置](./manuals/04_生产级_Nginx_泛域名网关与_SSL_配置.md)
- [05. 数据库生命周期与 Flyway 自动化迁移 (V1~V74)](./manuals/05_数据库生命周期与_Flyway_自动化迁移.md)
- [06. 跨端工作台生产构建 PC H5 微信小程序](./manuals/06_跨端工作台生产构建_PC_H5_微信小程序.md)
- [07. 生产安全基线 SecOps 与高可用运维保障](./manuals/07_生产安全基线_SecOps_与高可用运维保障.md)
- [08. 组织架构与企业级 IAM 权限配置指南](./manuals/08_组织架构与企业级_IAM_权限配置指南.md)
- [09. Flowable BPMN 2.0 工业级工作流实战](./manuals/09_Flowable_BPMN_2_0_工业级工作流实战.md)
- [10. CRM 客户全生命周期与商机管理实战](./manuals/10_CRM_客户全生命周期与商机管理实战.md)
- [11. ERP 进销存与云仓物料管理实战](./manuals/11_ERP_进销存与云仓物料管理实战.md)
- [12. 开放平台 Open API 与企业 SSO 对接指南](./manuals/12_开放平台_Open_API_与企业_SSO_对接指南.md)
- [13. 服务编排与发布中台 DevOps CI/CD 运维实战](./manuals/13_服务编排与发布中台_DevOps_CI_CD_运维实战.md)
- [14. AI 智能中枢、企业 RAG 与全局数据导出套打实战](./manuals/14_AI_智能中枢与企业_RAG_数据导出实战.md)
- [15. 文件中心存储路由与个人数字身份工作台](./manuals/15_文件中心存储路由与个人数字身份工作台.md)

## M0 评审基线

- [`reviews/TENANT_ACCESS_PROTOCOL.md`](./reviews/TENANT_ACCESS_PROTOCOL.md)：租户上下文、持久层强制隔离和平台旁路协议。
- [`STANDARDIZATION_POLICY.md`](./STANDARDIZATION_POLICY.md)：全项目标准来源、核心与扩展边界、能力剖面和禁止项。
- [`STANDARDIZATION_REMEDIATION.md`](./STANDARDIZATION_REMEDIATION.md)：现有代码偏差、目标状态、责任里程碑和发布阻断台账。
- [`reviews/ORGANIZATION_STANDARD_MODEL.md`](./reviews/ORGANIZATION_STANDARD_MODEL.md)：组织主体、关系、组织单元、参与关系和任职的标准模型与 V25 设计。
- [`M1_ORGANIZATION_IMPLEMENTATION_STATUS.md`](./M1_ORGANIZATION_IMPLEMENTATION_STATUS.md)：M1 已实现范围、验证证据和未完成发布阻断项。
- [`M1_IDENTITY_ORGANIZATION_BINDING.md`](./M1_IDENTITY_ORGANIZATION_BINDING.md)：User-Person 绑定、显式任职上下文、迁移控制和切换约束。
- [`reviews/SPRING_SECURITY_MIGRATION.md`](./reviews/SPRING_SECURITY_MIGRATION.md)：Spring Security、会话和存量密码升级方案。
- [`reviews/AI_CORE_DESIGN.md`](./reviews/AI_CORE_DESIGN.md)：AI Gateway、数据模型、Provider SPI、MCP、工具和运行审计方案。

## 历史设计输入

以下文档保留用于理解早期设计，不再作为当前实施顺序和完成状态的依据：

- [`enterprise-saas-foundation.md`](./enterprise-saas-foundation.md)
- [`backend-enterprise-architecture.md`](./backend-enterprise-architecture.md)
- [`frontend-vue-architecture.md`](./frontend-vue-architecture.md)
- [`database-module-boundaries.md`](./database-module-boundaries.md)

当历史文档与 v0.1 执行蓝图冲突时，以执行蓝图为准。
