# 天枢平台重构审查 · 2026-11-19

当前修复状态、实测结果及暂缓事项见[修复复验报告](2026-11-19-remediation.md)。

**首次审查结论（修复前）**　当时整个平台暂未通过。 采购单可以记入用户没有选择的物料；支付页面展示的图案也没有编码支付地址。这两项都会让操作员把未完成的业务当成已完成。

审查对象为本地工作树，包括未提交的 Java、Mapper XML、数据库迁移、PC 和移动端代码。当前树混合了多轮改动，报告按现状归类。数据验证在开发库与隔离副本进行，采购写入实验使用记录型 Mapper。没有发起真实支付。

## 审查依据

| 范围 | 本项目采用的检查标准 |
| --- | --- |
| 模块与交付 | 项目约定要求跨模块通过公开接口调用，租户隔离、负向权限测试、迁移与可回退性一并验收。Java 采用仓库约定的四空格缩进。Google Java Style 的显式导入等规则作为编写参考。[项目规范](../STANDARDIZATION_POLICY.md) · [贡献指南](../../CONTRIBUTING.md) · [EditorConfig](../../.editorconfig) · [Google Java Style](https://google.github.io/styleguide/javaguide.html) |
| 数据访问 | 单表增删改查优先使用 MyBatis-Plus 的通用 Mapper 与 Lambda Wrapper。跨表、聚合、动态权限和批量授权的业务 SQL 集中在 Mapper XML。后一句是天枢的团队约定。查询接口必须能由 Spring 注册并绑定到对应语句。[MyBatis-Plus 数据接口](https://baomidou.com/en/guides/data-interface/) · [MyBatis 映射文件](https://mybatis.org/mybatis-3/sqlmap-xml.html) · [Mapper 注册](https://mybatis.org/spring/mappers.html) |
| 事务、并发与迁移 | 写入要能在失败时整体回滚。库存、流程编辑等先读后写操作还需验证重复请求和并发。已落库的迁移保留校验值，修正采用后续迁移。[Spring 事务回滚](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html) · [InnoDB 锁定读取](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html) · [Flyway 迁移](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations) |
| 前端 | PC 同时检查类型、构建、脚本风格、样式和测试。移动端在 H5 条件编译后检查 Vue 类型，并分别生成 H5、App、微信小程序产物。平台专用页面 API 按实际路由类型使用。[Vue 的 TypeScript 检查](https://vuejs.org/guide/typescript/overview.html) · [uni-app tabBar API](https://uniapp.dcloud.net.cn/api/ui/tabbar.html) |

## 首次审查发现（修复前）

| 优先级 | 发现及证据 | 验收条件 |
| --- | --- | --- |
| 高 | **ERP 采购入库会凭空产生明细。** PC 只传供应商、总额和备注。服务给两张金额分别为 120 和 750 的测试订单选了同一件物料，数量都为 10。另一张单显式传入 3 件、单价 4，明细合计为 12，单头却写入 48,500。已保存的单缺少明细时，入库路径也会构造明细。[采购表单](../../web/src/views/erp/ErpView.vue) · [采购与入库服务](../../server/saas-basics-module-integration/src/main/java/com/saasbasics/platform/modules/erp/service/ErpService.java) | 操作员能选实际物料、数量、单价和仓库。服务端校验归属并据明细计算总额。历史缺明细单必须能识别和处理，在明细补齐前禁止过账。 |
| 高 | **入库过账存在并发风险。** 服务先读订单状态再更新，库存数量也先读后写。库存唯一索引约束一条库存记录，无法替代订单的条件更新。此项来自代码检查，未进行同时入库压测。[入库实现](../../server/saas-basics-module-integration/src/main/java/com/saasbasics/platform/modules/erp/service/ErpService.java) | 同一订单重复提交只产生一次库存变化；两张订单同时入同一物料时，库存和流水的合计一致。 |
| 高 | **PC 支付码无法承载支付地址。** 后端按地址的 SHA-256 摘要绘制方格，前端显示固定 CSS 图案。扩大数据库字段解决了图片字符串的保存长度，实际扫码和支付回调仍缺少验收。[支付服务](../../server/saas-basics-module-integration/src/main/java/com/saasbasics/platform/modules/integration/service/PayCenterService.java) · [支付页面](../../web/src/views/integration/sections/IntegrationPaySection.vue) | 用通用解码器读出提供方返回的支付地址，再在提供方测试环境验证同一订单及幂等回调。 |
| 中 | **配额展示和配额强制执行覆盖不同。** 文件直接上传与分片会话没有租户容量的并发预留；单文件大小和扩展名校验已经存在。席位和部门创建也没有统一覆盖所有写入入口。[直接上传](../../server/saas-basics-module-file/src/main/java/com/saasbasics/platform/modules/file/service/PlatformFileStorageGatewayImpl.java) · [上传会话](../../server/saas-basics-module-file/src/main/java/com/saasbasics/platform/modules/file/service/FileUploadSessionService.java) | 测试两个上传同时占用最后一段可用空间、失败释放、会话重试；数据库、对象存储和配额三者可核对。 |
| 中 | **复杂 SQL 和模块边界仍未统一。** 租户开通服务内含多段多表复制、权限聚合及批量授权 SQL。行业注册还有 Mapper 和供测试调用的 JDBC 两条实现；IAM 的门户服务直接引用租户实体和 Mapper。PC 员工查询已移入 XML。本轮盘点生产 Java 源文件仍有 28 份含 SQL 注解，共 58 处，数量仅用于定位，需按语义逐条归类。[租户开通](../../server/saas-basics-module-tenant/src/main/java/com/saasbasics/platform/modules/tenant/service/TenantBootstrapService.java) · [行业注册](../../server/saas-basics-module-tenant/src/main/java/com/saasbasics/platform/modules/tenant/service/IndustryRegistry.java) · [门户依赖](../../server/saas-basics-module-iam/src/main/java/com/saasbasics/platform/modules/iam/service/PortalEntryService.java) | 单表操作走通用 Mapper；开通流程由事务服务编排，复杂 SQL 由 XML 执行。测试走生产 Mapper 路径；跨模块调用公开契约。 |
| 中 | **权限迁移会物理删除目录授权历史。** 删除条件仅匹配菜单类型，历史软删除记录也符合条件。当前库已经执行了这条迁移。审查确认了 SQL 的影响范围，未据此推断实际损失的行数。[权限迁移](../../server/saas-basics-app/src/main/resources/db/migration/V83__platform_qa_regression_fixes.sql) | 用迁移前备份核对受影响记录。后续修订通过追加迁移交付，并覆盖重复执行和审计保留。 |
| 中 | **开发启动脚本含明文数据库默认凭证。** 仓库贡献指南禁止提交凭证。报告省略具体内容。[启动脚本](../../bin/run-backend.sh) · [贡献指南](../../CONTRIBUTING.md) | 从环境或本地私有配置注入；在同步协调各服务后轮换已暴露的凭证。 |

## 本次兼容修复与复测

- 恢复后端原定运行和构建配置，保留业务改动，更新独立工作流服务的测试注入。旧的正常开发备份可按现有迁移启动，登录、库存、工作流待办接口均成功。
- 当前开发库包含两条备份之后新增的流程定义。使用工作流引擎官方建表逻辑重建规范表结构，迁入当前记录。副本中比对引擎数据、业务表、索引和外键，再关闭自动变更结构，启动真实引擎读取数据。完整证据随本地备份保存，包含业务内容，仅共享此报告。[隔离恢复工具](../../bin/workflow-recovery/rehearse.py)
- 修正数据库模式下 Mapper 扫描。Spring 同时注册基础实体 Mapper 与独立查询 Mapper。审计 XML 成功绑定，缺必需映射时会显式失败。平台共享的行业目录有明确的归属声明和测试。[扫描配置](../../server/saas-basics-app/src/main/java/com/saasbasics/platform/config/DbMapperScanConfig.java) · [映射注册测试](../../server/saas-basics-app/src/test/java/com/saasbasics/platform/config/DbMapperScanConfigTest.java)
- 修正目标租户的用量上下文，文件统计读取实际文件表。隔离测试验证授权平台人员、无权限人员、普通租户、两个数据源、逻辑删除以及存储边界。[配额服务](../../server/saas-basics-module-tenant/src/main/java/com/saasbasics/platform/modules/tenant/service/TenantQuotaService.java)
- 将员工列表、详情和动态数据权限 SQL 迁入同一份 Mapper XML，共用投影并保留参数绑定。[员工查询](../../server/saas-basics-module-iam/src/main/resources/mapper/EmployeeMapper.xml)
- 移动端修正跟进渠道和登录环境变量的类型，移除三个非 tabBar 页面上的隐藏操作及吞异常逻辑。H5 条件编译后的 Vue 类型检查通过，移动端 5 项测试及三个目标端的构建通过。PC 构建、类型检查、ESLint、Stylelint 和 17 项测试通过。App 与微信小程序只验证到构建产物，未做真机操作。
- 按租户分别缓存私有黑名单，平台通用规则独立生效。租户没有本地目录时只读取固定的平台公共目录，避免把另一家租户的目录或封禁带入。双租户用例覆盖交替访问、规则增删、开关和失效时间。[审计隔离测试](../../server/saas-basics-module-audit/src/test/java/com/saasbasics/platform/modules/audit/service/AuditTenantIsolationTest.java)
- 流程编辑器保存时携带打开表单时的修订号。服务在同一事务中锁定当前租户的定义，在部署前检查冲突。实测正常保存返回 200，过期保存返回 409，部署数量和已保存的内容均未改变；旧格式请求仍返回 200。另在 Chromium 中实测列表后台刷新后，保持打开的编辑器仍提交最初读取的修订号，重新打开后才使用新值。仍未传修订号的第三方调用保留原有覆盖语义。[编辑器](../../web/src/views/workflow/WorkflowDesignerDialog.vue) · [并发保存测试](../../server/saas-basics-module-workflow/src/test/java/com/saasbasics/platform/modules/workflow/service/WorkflowDefinitionSaveTest.java)

## 首次审查时的开发环境结果

**主服务 8080 和独立工作流服务 8085 都已切换。** 两个监听进程所加载的文件与本地部署包的 SHA-256 分别一致，健康端点均返回 UP。PC 开发服务和移动开发服务仍从当前源码提供页面。[本地租户总览](http://localhost:9527/tenant/overview)

| 检查 | 结果 |
| --- | --- |
| 数据恢复 | 副本校验 45 张引擎表、162 张业务表及视图对象、14,481 条业务及运行数据、47 个外键。导出后再导入，真实引擎能解析全部 10 个 BPMN 定义。第二次执行恢复，业务数据摘要一致。正式开发库只交换工作流表组，保留原表。切换前再比对工作流所有原始行，切换后核对了 10 个定义、8 个引擎待办、8 个在途实例、8 条历史实例。[本地恢复核验记录](../../data/backups/flowable8-recovery-20261119-verified/validation.json) |
| 平台接口 | 通过 9527 登录后，审计高危目录由空列表恢复为 24 条，与数据源一致；一个获准查看的租户实际有 2 个账号、1 个部门，接口准确返回这两个值。员工列表返回 3 条，详情可读；库存接口返回 10 条。两个后端入口都接受同一平台会话读取待办。 |
| 浏览器 | Chromium 实际登录后进入租户总览，分别往返租户台账、租户应用授权，再刷新总览。页面正常显示，未触发组件错误边界或浏览器运行异常。 |
| 后端测试 | Maven 全模块验证成功。189 项中 174 项通过，15 项因本机缺少 Docker 而跳过。跳过项属于租户隔离、租户应用、迁移、用户人员绑定和组织集成相关测试。本次另外进行了真实 MySQL 的恢复、接口与流程写入验收。 |
| 前端测试 | PC 类型、构建、脚本和样式检查通过，17 项测试通过。移动端 5 项测试通过，H5 类型检查及三端构建通过。 |

切换前的两次全库备份、原运行包和本次验收输出保存在本机。[本地验收输出](../../data/backups/standard-review-20261119-205617/acceptance.txt)

后续已有业务写入时，撤回前需要对账新增数据。[本地切换备份](../../data/backups/standard-review-20261119-205617)
