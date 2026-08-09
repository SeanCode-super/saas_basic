# MySQL 8.3 SQL 说明

当前基线 SQL：

- `0001_enterprise_saas_foundation.sql`
- `0002_module_refinement.sql`
- `0003_auth_session.sql`

说明：

- `0001_enterprise_saas_foundation.sql` 是第一版全量初始化脚本
- `0002_module_refinement.sql` 是多模块落地后的第二版增强迁移
- `0003_auth_session.sql` 是认证执行链的第三版迁移

适合：

- 初始化空库后继续执行增量演进
- 与 Flyway 的 `V1__enterprise_saas_foundation.sql`、`V2__module_refinement.sql`、`V3__auth_session.sql` 保持一致

从多模块架构开始，后续表结构演进建议遵循：

- 逻辑上按模块拥有表
- 迁移上按模块命名追加脚本
- 物理上当前仍保持单库

数据库模块边界说明见：

- `/Users/shenzhidan/ideaProject/saas_basics/docs/database-module-boundaries.md`
