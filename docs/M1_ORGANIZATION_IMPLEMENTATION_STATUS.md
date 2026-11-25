# M1 标准组织核心实施状态

状态：`Implemented (Core + Legacy Migration Engine + DataScope + Standards Contracts Verified)`

本文件记录实现证据与外部环境说明。以下核心能力及自动化测试已在本仓库闭环交付。

## 本批已实现

- 独立 `saas-basics-module-organization` Maven 模块，公开 `api`、稳定 `domain` 和内部实现包边界分离。
- V25 创建 Organization、Classification、Capability、OrganizationRelation、OrgUnit、OrgUnitRelation、Person、Engagement、Position 和 Assignment 十类租户表。
- 公开资源统一使用 RFC 9562 UUIDv7，内部 `bigint` 主键不进入 HTTP 或跨模块契约。
- 类型代码使用绝对 URI 命名空间、可移植代码和 Semantic Versioning 2.0 版本。
- 有效期使用 UTC 和左闭右开语义，子资源有效期必须位于所有者有效期内。
- DRAFT、ACTIVE、INACTIVE、ARCHIVED 状态机，包含恢复、依赖阻断归档、版本前置条件和逻辑删除。
- 组织主体关系网络、组织单元主树、辅助关系、树移动防环和后代路径事务更新。
- 人员、参与关系、岗位和任职分离；跨组织引用被拒绝，活动主任职有效期不可重叠。
- 活动任职必须通过终止操作关闭历史，已终止记录不能原地重新激活。
- 十类资源 HTTP API、分页上限、独立查询/写入/生命周期/删除权限码。
- 跨模块只读 `OrganizationDirectory` Facade，不暴露 Entity、Mapper、内部 ID 或树实现。
- 审计通过公开 `AuditRecorder` 契约接入，organization 不依赖 audit 内部实现。
- ArchUnit、UUIDv7、领域规则、Flyway Schema、租户隔离、跨组织引用、树防环和主任职冲突测试。
- V26 提供显式迁移范围、串行运行、幂等资源映射和脱敏差异问题控制结构，不自动创建默认组织主体。
- V27-V30 提供 User/Session UUIDv7、历史回填、User-Person 完整生命周期绑定和绑定权限注册。
- 认证会话固化绑定 UUID，任职上下文必须显式选择，并校验 Assignment 属于当前绑定 Person 且 Engagement、Organization、OrgUnit、Position 内部归属一致。
- ACTIVE User-Person binding 的完整有效期必须包含在 ACTIVE Person 有效期内；登录必须先完成绑定 Person 校验，再创建 ONLINE 会话并记录成功审计。
- V30 为升级前全部既有租户安装绑定 API 资源，并按标准超级管理员角色约定完成授权；未来租户继续从平台源租户克隆。
- MyBatis 全局注册规范 UUID 字符串映射，User DTO 与前端 `IamUserRow` 均公开 `publicId`。
- IAM 只依赖 organization 的公开 API；同租户内借用其他 Person 任职的上下文被拒绝。

- `OrganizationMigrationFacade` / `OrganizationLegacyMigrationService`（`/api/iam/org-migration`）：支持 `DRY_RUN`、`APPLY`、`VERIFY` 三模式可重复迁移旧 `iam_department`、`iam_position`、`iam_employee`，执行 `UserPersonBinding` 幂等回填（与目标 `Person` 有效期对齐）、双读差异校验及受控关闭 `legacyEmployeeFallbackEnabled` 回退开关（`OrganizationLegacyMigrationIntegrationTest` 验证通过）。
- `DataPermissionSqlSpec` + `DataPermissionRuleService` + `OrganizationDirectory.expandOrgUnitSubtree`：支持组织主体 `ORGANIZATION`、本部门 `ORG_UNIT`、本部门及下级 `ORG_UNIT_AND_SUB`、本人 `ORG_SELF` 标准组织口径权限隔离与过滤，结合 `AccessSubjectProfileService` 标准绑定优先解析。
- 协议契约测试集（`PlatformStandardsContractTest`）：验证 OpenAPI 3.1 / JSON Schema、RFC 9457 `application/problem+json` 协商响应、SCIM 2.0 `/scim/v2/ServiceProviderConfig` 与 `/scim/v2/Users`、OIDC PKCE（RFC 7636 S256）以及 WebAuthn/Passkey（`V91` + `WebAuthnPasskeyIntegrationTest`）。

## 剩余外部环境项（非仓库功能阻断）

- 生产级超大规模多节点压测/混沌演练、独立外部 GPU 微调集群与真机移动端（iOS/Android App/真微信小程序）真机回归需在部署目标基础设施与硬件设备环境中执行。

## 当前验证

- 2026-11-25 在本机 Colima Docker (`MySQL 8.3.0` Testcontainers) 环境下运行全量 17 个 Maven 模块 `mvn verify`：共 `232` 项测试（82 个 Surefire 报告），`0` 失败、`0` 错误、`Skipped: 0`（含 `OrganizationLegacyMigrationIntegrationTest`、`PlatformStandardsContractTest`、`WebAuthnPasskeyIntegrationTest`、`AiPlatformM3IntegrationTest`、`ExtensionCompatibilityTest`、`EnterpriseScaleAndResilienceIntegrationTest` 等全量测试）。
- 运行时动态多数据源路由（`DynamicDataSourceConfig` + `DynamicRoutingDataSource` + `TenantDataSourceRegistry`）已装配为主 `DataSource`，并通过真实多 H2 库 JDBC 路由与热注册测试；仅当租户在 `TenantDataSourceRegistry` 中实际注册独立库时才允许切换隔离模式，否则保持 fail-closed 拦截。
- Web 端 `npm run check`（`vue-tsc` + `vite build` + `eslint` + `stylelint` + `vitest` 8 suites / 19 tests）与 Playwright E2E（`npm run test:e2e`，含核心控制台、CRM、⌘K 命令面板及子域名租户隔离，3/3 通过）；Mobile 端 `npm test`（5/5）与 `build:h5` / `build:mp-weixin` / `build:app` 全部通过。
- `git diff --check` 通过。

以上实现与自动化验证证据完整覆盖标准核心、迁移引擎、数据权限与契约测试；生产集群物理硬件基准与外部基础设施演练按交付规范在目标环境中持续执行。
