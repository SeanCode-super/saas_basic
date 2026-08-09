# Spring Security 与密码升级评审稿

状态：`Proposed`

目标里程碑：`M2`

## 1. 目标状态

使用 Spring Security 6 的单一认证链路替换 `AuthContextFilter` 和 MVC 权限拦截器。v0.1 继续使用数据库持久化的 opaque bearer session，不引入 JWT，以便即时注销、并发会话控制和租户停用立即生效。

## 2. SecurityFilterChain

建议顺序：

1. CORS 和统一异常输出。
2. Bearer token 提取。
3. 跨租户系统查找会话，只定位 `tenantId`。
4. 建立 `TenantAccessContext`。
5. 在当前租户内校验会话、租户、用户和过期时间。
6. 构造 Spring Security `Authentication` 和 authorities。
7. Controller 方法授权。
8. 数据权限在应用服务和仓储查询中继续执行。
9. 请求结束清理租户、数据权限和安全上下文。

公开端点使用精确路径白名单，不使用宽泛前缀匹配。菜单可见性不参与 API 授权判断。

## 3. 密码编码决策

v0.1 默认使用 `DelegatingPasswordEncoder`，新密码格式带算法前缀。首个默认实现使用 BCrypt strength 12；保留切换 Argon2id 的能力。

| 格式 | 处理 |
|---|---|
| `{bcrypt}...` | 正常校验，参数过旧时登录后升级 |
| `{argon2}...` | 安装对应实现后正常校验 |
| 64 位无前缀十六进制 SHA-256 | 仅作为旧格式校验，成功登录后立即升级 |
| 未识别格式 | 拒绝并记录安全事件 |

任何创建、重置、导入和租户初始化都只能写新格式。旧 SHA-256 校验器不能成为默认编码器。

## 4. 渐进升级流程

1. 用户提交密码。
2. 识别存量哈希格式。
3. 使用常量时间比较校验旧 SHA-256。
4. 校验成功后在同一事务内写入 BCrypt 哈希并更新时间。
5. 写入密码历史和 `PASSWORD_HASH_UPGRADED` 安全事件。
6. 升级写入失败则登录失败，避免继续维持弱哈希会话。

平台种子密码和租户初始化默认密码迁移到 BCrypt。默认密码必须首次登录修改，生产环境不得把默认明文写入文档或日志。

## 5. 会话与授权

- 数据库存储 token 哈希，不存明文 token。
- 会话包含固定 `tenant_id`，客户端不能在会话内切换租户。
- 租户、法人、组织和任职切换通过受控接口签发或更新上下文快照。
- 注销、密码重置、用户停用和租户停用使相关会话立即失效。
- 并发会话上限来自登录策略，超限按策略拒绝或淘汰最旧会话。
- 权限码进入 authorities；数据范围不展开为大量 authorities，而由仓储策略执行。
- 平台跨租户授权使用独立权限码、原因字段和操作审计。

## 6. 迁移步骤

1. 引入 Spring Security 和新 `PasswordEncoder`，保留当前 API 响应格式。
2. 添加认证 provider 和 session repository，建立等价集成测试。
3. 将 `@RequirePermission` 映射到方法安全或兼容适配器。
4. 切换 bearer filter，删除旧 `AuthContextFilter`。
5. 切换密码写入，启用登录后升级。
6. 删除旧 SHA-256 新建路径和旧 MVC 权限拦截器。

不允许新旧认证过滤器长期并行，否则过滤顺序和异常语义不可控。

## 7. 必须自动化的负向测试

- 无 token、伪造 token、过期 token、已注销 token。
- 会话 tenant 与用户 tenant 不一致。
- 请求头、查询参数和请求体尝试覆盖 tenant。
- 租户 A 使用租户 B 的对象 ID、导出接口或批量接口。
- 普通平台管理员在无旁路权限时访问业务租户。
- 旧 SHA-256 登录成功后升级，新建密码绝不写 SHA-256。
- 用户、租户或任职停用后现有会话失效。
