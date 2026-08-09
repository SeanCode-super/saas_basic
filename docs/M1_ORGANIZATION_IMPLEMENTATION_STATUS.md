# M1 标准组织核心实施状态

状态：`In Progress`

本文件记录实现证据和发布阻断项。只有全部阻断项关闭并通过 `STANDARDIZATION_POLICY.md` 的完整能力剖面后，M1 才能标记为完成。

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

## M1 发布阻断项

- 设计并执行旧 `iam_department`、`iam_position`、`iam_employee` 到标准模型的可重复映射、回填、差异报告和切换迁移。
- 建立 User、Person、Engagement 和 Assignment 的公开绑定契约，移除新代码对 `iam_user.employee_id` 的依赖。
- 重构 RoleBinding 和数据权限，使查询范围能够引用组织主体、组织单元、任职和本人。
- 将 IAM 权限定义与 HTTP endpoint 定义拆分，注册 organization 权限并提供租户安装、升级和卸载检查。
- 将公开错误响应升级为 RFC 9457 Problem Details，并增加 OpenAPI 3.1 / JSON Schema 2020-12 契约测试。
- 为创建命令补充持久化幂等键，为领域变化补充 CloudEvents 1.0 事件、outbox 和失败重放。
- 补充 OpenTelemetry 指标和追踪、敏感人员字段治理、审计检索和告警。
- 实现组织上下文前端、加载/空数据/无权限/冲突/恢复状态和可访问性测试。
- 完成分类、能力和地域或行业扩展包的 manifest、Schema、版本兼容、安装和卸载契约测试。
- 完成容量、深树移动、并发调岗、备份恢复、上一版本升级和应用回滚验证。

## 当前验证

- `mvn -q verify` 在无 Docker 环境通过；容器测试按 Testcontainers 配置明确跳过。
- GitHub Actions 必须实际运行 MySQL 8.3 Flyway、租户隔离和组织标准集成测试，且 `Skipped: 0`。
- `git diff --check` 通过。

本批代码是 M1 的标准核心实现，不是 M1 完成声明，也不是可替代发布阻断项的最小版本。
