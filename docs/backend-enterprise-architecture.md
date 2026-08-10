# SaaS Basics 后端架构规范

> 历史状态说明：本文档包含已经过时的运行方式和落地状态。当前执行基线以 [`V0.1_EXECUTION_BLUEPRINT.md`](./V0.1_EXECUTION_BLUEPRINT.md) 为准。

## 1. 当前落地结果

后端工程已经落在：

- `/Users/shenzhidan/ideaProject/saas_basics/server`

当前采用：

- `Spring Boot 3.3.x`
- `Java 17`
- `Maven 3.9`

说明：

- 文档目标技术线仍建议长期升级到 `Java 21`
- 但当前本机环境只有 `Java 17`
- 因此这一版后端骨架先以 `Java 17` 保证本地可构建、可运行

## 2. 后端设计取向

这套后端不是“把所有系统管理功能堆在 controller 里”，而是企业级 SaaS 平台底座：

- 平台级能力与租户级能力分离
- 先做统一基础设施，再做业务域
- 权限、数据、文件、任务、代码生成都作为平台能力建模

## 3. 当前目录结构

```text
server/
  pom.xml
  saas-basics-app/
  saas-basics-common/
  saas-basics-framework/
  saas-basics-module-dashboard/
  saas-basics-module-tenant/
  saas-basics-module-iam/
  saas-basics-module-system/
  saas-basics-module-integration/
  saas-basics-module-file/
  saas-basics-module-scheduler/
  saas-basics-module-codegen/
  saas-basics-module-audit/
```

当前分层原则：

- `saas-basics-app`：启动与装配
- `saas-basics-common`：统一返回、异常、上下文、基础模型
- `saas-basics-framework`：配置与基础设施
- `saas-basics-module-*`：按业务域拆分的模块

数据库边界说明见：

- `/Users/shenzhidan/ideaProject/saas_basics/docs/database-module-boundaries.md`

## 4. 当前已落的基础设施

### 统一返回

- `ApiResponse<T>`
- `PageResponse<T>`

### 统一异常

- `BizException`
- `GlobalExceptionHandler`

### 租户上下文

- `TenantAccessContext`
- `TenantAccessContextHolder`
- MyBatis-Plus `TenantLineInnerInterceptor`

说明：

- 受保护请求只能从认证会话恢复 `tenantId`，不接受请求头、查询参数或请求体覆盖
- 租户表查询和写入由 MyBatis 拦截器强制追加 `tenant_id`
- 令牌定位、公共门户定位和平台租户初始化使用带操作类型与原因的显式旁路作用域

### Web 配置

- 跨域白名单配置
- OpenAPI 文档入口
- 健康检查端点

### MyBatis-Plus 基础设施

- 统一实体基类 `BaseEntity`
- 租户实体基类 `BaseTenantEntity`
- 乐观锁拦截器
- 审计字段自动填充

## 5. 当前已落的模块接口壳

### dashboard

- `/api/platform/dashboard/metrics`

### tenant

- `/api/tenants`
- `GET /api/tenants/{id}`
- `POST /api/tenants`
- `PUT /api/tenants/{id}`
- `PATCH /api/tenants/{id}/status`

### iam

- `/api/iam/overview`
- `GET /api/iam/users`
- `GET /api/iam/users/{id}`
- `POST /api/iam/users`
- `PUT /api/iam/users/{id}`
- `PATCH /api/iam/users/{id}/status`
- `GET /api/iam/roles`
- `GET /api/iam/roles/{id}`
- `POST /api/iam/roles`
- `PUT /api/iam/roles/{id}`
- `GET /api/iam/api-resources`
- `GET /api/iam/api-resources/{id}`
- `POST /api/iam/api-resources`
- `PUT /api/iam/api-resources/{id}`
- `GET /api/iam/users/{userId}/roles`
- `PUT /api/iam/users/{userId}/roles`
- `GET /api/iam/roles/{roleId}/api-resources`
- `PUT /api/iam/roles/{roleId}/api-resources`

### auth

- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`

认证头约定：

- 受保护接口统一使用 `Authorization: Bearer <accessToken>`
- 登录时提交租户编码；登录后租户由服务端会话绑定
- `POST /api/auth/login` 与 `GET /api/health/ready` 是默认公开接口

### system

- `/api/system/configs`
- `GET /api/system/configs/{id}`
- `POST /api/system/configs`
- `PUT /api/system/configs/{id}`

### integration

- `/api/integrations/datasources`
- `GET /api/integrations/datasources/{id}`
- `POST /api/integrations/datasources`
- `PUT /api/integrations/datasources/{id}`

### file

- `/api/files/capability`
- `GET /api/files/storages`
- `GET /api/files/storages/{id}`
- `POST /api/files/storages`
- `PUT /api/files/storages/{id}`

### scheduler

- `GET /api/scheduler/jobs`
- `GET /api/scheduler/jobs/{id}`
- `POST /api/scheduler/jobs`
- `PUT /api/scheduler/jobs/{id}`

### codegen

- `GET /api/codegen/projects`
- `GET /api/codegen/projects/{id}`
- `POST /api/codegen/projects`
- `PUT /api/codegen/projects/{id}`

### audit

- `/api/audit/overview`

### health

- `/api/health/ready`

当前这些接口在没有数据库 profile 时会使用模块内 mock 回退，不直接连接数据库。启用 `db` profile 后，`tenant / iam / system / integration / file / scheduler / codegen` 会优先走真实 mapper 查询。

现在已经进入第二阶段：

- `tenant / iam / system / integration / file / scheduler / codegen` 已经补了真实实体与 mapper
- 当启用数据库 profile 时，会优先走真实 MySQL 查询
- 未启用数据库时，仍保留 mock 回退，保证前后端开发不中断
- `audit` 的概览统计在数据库可用时会读取真实计数
- `auth` 已经补了登录、会话解析、当前用户、退出登录，以及密码/登录策略执行骨架
- 非公开 `/api/**` 接口默认需要登录态

当前开发态 mock 登录账号：

- `tenantCode`: `platform`
- `username`: `platform.admin`
- `password`: `Admin@123456`

## 6. 为什么保留模块内 mock 回退

因为当前仓库刚完成：

- 企业级 SQL 基线
- Vue 前端控制台骨架
- 后端平台工程骨架

这个阶段最需要的是：

- 把前后端接口边界先立起来
- 把模块边界先固化
- 把租户上下文和统一返回先跑通
- 在没有本地数据库时仍能继续开发

而不是立刻把所有表的 CRUD 一次性硬编码完。

## 7. 下一步后端实现优先级

建议按下面顺序继续：

1. 接入 MySQL 8.3 和 Flyway/Liquibase
2. 落 `tenant / iam / system / integration` 的真实实体与 mapper
3. 落统一审计字段、逻辑删除、版本号、租户注入
4. 落认证鉴权链路
5. 落代码生成 DSL 与模板执行器
6. 落文件中心与调度中心真实执行能力

其中第 1、2 项本轮已经开始落地。

## 10. 数据库运行方式

无数据库快速启动：

```bash
cd /Users/shenzhidan/ideaProject/saas_basics
java -jar server/saas-basics-app/target/saas-basics-app-0.1.0-SNAPSHOT.jar
```

说明：

- 默认 `app` profile 已关闭数据源与 Flyway 自动装配
- 默认模式可直接跑 mock/前后端联调链路
- 适合先验证登录、权限、页面访问和模块骨架

如果你要让后端连真实 MySQL 8.3，请使用 `db` profile：

```bash
cd /Users/shenzhidan/ideaProject/saas_basics/server
DB_HOST=127.0.0.1 \
DB_PORT=3306 \
DB_NAME=saas_basics \
DB_USERNAME=root \
DB_PASSWORD=your_password \
mvn -pl saas-basics-app spring-boot:run -Dspring-boot.run.profiles=db -Dmaven.repo.local=/tmp/saas-basics-m2
```

说明：

- `db` profile 会启用 MySQL 数据源
- `Flyway` 会自动执行 `V1__enterprise_saas_foundation.sql`
- `Flyway` 会继续执行 `V2__module_refinement.sql`
- `Flyway` 会继续执行 `V3__auth_session.sql`
- 迁移文件来源于当前仓库的基础 SQL 基线
- 多模块根工程会统一参与构建

迁移文件位置：

- `/Users/shenzhidan/ideaProject/saas_basics/server/saas-basics-app/src/main/resources/db/migration/V1__enterprise_saas_foundation.sql`
- `/Users/shenzhidan/ideaProject/saas_basics/server/saas-basics-app/src/main/resources/db/migration/V2__module_refinement.sql`
- `/Users/shenzhidan/ideaProject/saas_basics/server/saas-basics-app/src/main/resources/db/migration/V3__auth_session.sql`

## 8. 与前端对接建议

前端现阶段最适合先对接这些接口：

- 租户列表
- 仪表盘指标
- 系统配置列表
- 数据源列表
- 代码生成项目列表

这样可以先跑通：

- 布局与导航
- 租户头透传
- API 封装
- 页面渲染链路

## 9. 后续需要继续补的企业级能力

接下来我建议继续在后端补这些能力，而不是只继续堆零散 CRUD：

- 登录认证与会话
- 菜单/角色/API 权限注册
- 数据权限表达式模型
- 密码策略与登录策略执行链
- 平台级功能开关
- 租户套餐与配额
- 数据源连通性检测
- 文件对象、上传会话与引用关系
- 任务调度执行、告警与日志
- SQL 与代码联动生成

当前认证链路已落：

- opaque token 服务端会话
- 登录失败锁定
- 密码策略校验与过期校验
- 登录日志落库
- 当前用户上下文透传到审计字段填充
- 非公开 `/api/**` 接口默认认证拦截
- `@RequirePermission` + `PermissionGuardInterceptor` 接管接口级权限校验
- 用户权限在数据库可用时优先从 `iam_user_role + iam_role_api + iam_api_resource` 实时计算

当前已落的权限码风格：

- `dashboard:metrics:query`
- `tenant:query` / `tenant:write`
- `iam:overview:query` / `iam:user:query` / `iam:user:write`
- `iam:role:query` / `iam:role:write`
- `iam:api-resource:query` / `iam:api-resource:write`
- `iam:user-role:query` / `iam:user-role:write`
- `iam:role-api:query` / `iam:role-api:write`
- `system:config:query` / `system:config:write`
- `integration:datasource:query` / `integration:datasource:write`
- `file:object:query` / `file:storage:write`
- `scheduler:job:query` / `scheduler:job:write`
- `codegen:project:query` / `codegen:project:write`
- `audit:operation:query`
