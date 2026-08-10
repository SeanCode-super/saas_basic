# SaaS Basics 文档入口

## 当前执行基线

- [`V0.1_EXECUTION_BLUEPRINT.md`](./V0.1_EXECUTION_BLUEPRINT.md)：产品边界、企业模型、AI 架构、架构决策、迁移顺序、里程碑和验收标准。
- [`ROADMAP.md`](./ROADMAP.md)：公开里程碑、发布门槛、能力范围和后续平台建设方向。
- [`adr/ADR-013_OPEN_SOURCE_LICENSE.md`](./adr/ADR-013_OPEN_SOURCE_LICENSE.md)：Apache-2.0 许可证决策、适用范围和后续约束。

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
