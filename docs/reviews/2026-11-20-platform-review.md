# 天枢平台整体验收复核

2026 年 11 月 20 日 · 本地开发环境、当前工作树

**当前实现尚未达到跨行业开发平台的整体交付要求。** 租户开户、套餐配额、业务数据读写和按租户安装行业服务已有可复核的实现。危险作业示例也已有手机填报、PC 审批、手机回读的记录。目前影响其他行业复用的主要问题是应用发布路径不同、配置变更会影响在途业务、界面有时把生成的示例结果当作业务事实，以及任务和备份依赖单个进程或节点。

## 检查范围和依据

盘点了后端构建清单的 16 个子模块、75 个以 Controller 命名的类、PC 的 24 个视图分组、手机的 10 个页面分组，以及独立行业服务示例、打包模板、CI、Compose 与 Helm。这些数字用于说明边界。下表中的每个结论均限定到所列路径，不能用入口数量判断业务通过率。[后端清单](../../server/pom.xml#L29) · [项目验收规范](../STANDARDIZATION_POLICY.md) · [持续集成](../../.github/workflows/ci.yml)

| 证据 | 本轮采用的口径 |
| --- | --- |
| 本机运行 | 08:49（北京时间）读取主服务 8080、工作流 8085 的健康端点，均为 HTTP 200、状态 UP。PC 9527 的租户总览与移动 H5 5173 首页均返回 HTML。08:54 经 9527 复查登录页、就绪端点和门户配置均成功，匿名访问租户列表返回 HTTP 401。 |
| 自动化 | 从当前源码复制到独立临时目录进行后端编译。排除支付专项测试类，为获取所有结果允许 Maven 在单项失败后继续，按 Surefire XML 逐条计数。另跑 PC 和移动单测，以及使用私有临时数据库的行业服务示例。 |
| 历史现场 | 11 月 19 日记录覆盖 84 个 PC 地址、15 个注册 H5 页面和 122 个只读接口用例。首轮有失败，后续另有采购、开户、配额等修复复验。地址到达仅证明路由可用。[原始逐项记录](2026-11-19-platform-qa/测试明细.html) · [修复复验](2026-11-19-remediation.md) |
| 源码确认 | 核对用户入口、服务实现、数据写入或外部调用。下文标出的条件风险未通过破坏性故障注入验证，未统计开发库中受影响的历史记录。支付继续暂缓，共享数据库管理员密码轮换由各项目协调。 |

### 本次执行结果

| 检查 | 结果 |
| --- | --- |
| 后端非支付测试，原始启动方式 | 212 项中 193 项通过、1 项失败、18 项因本机缺少 Docker 而跳过。失败在常规租户基础套餐断言。在该用例给定的基础套餐下，代码移除平台租户治理权限，原断言却期望授予。该失败在独立编译和工作区精选测试中均出现。应按租户治理契约校正用例，并保留普通租户不能取得平台治理权限的断言。[用例](../../server/saas-basics-module-tenant/src/test/java/com/saasbasics/platform/modules/tenant/service/TenantFeatureServiceTest.java#L79) · [实际策略](../../server/saas-basics-module-tenant/src/main/java/com/saasbasics/platform/modules/tenant/service/TenantFeatureService.java#L239) |
| 后端非支付测试，隔离补跑 | 将前述跳过的 9 个测试类改为连接其专用的空 MySQL 与 Redis，保留原测试业务断言。18 个方法全部通过，覆盖迁移、组织、用户与人员绑定、租户隔离、应用升级、动态模型、采购入库与回收站。两次合计按唯一用例计为 **211 通过、1 失败**。补跑使用本机数据库发行包，原套件指定的容器镜像本轮未运行。 |
| 登录态业务读取，独立实例 | 09:02:56–09:02:57，当前源码打包后在新建数据库和 Redis 上启动。专用测试管理员通过平台登录，抽查 12 个领域的 15 个 GET，并正常注销。15 项均返回 HTTP 200、业务码 OK 且数组或分页结构有效，11 项非空。该结果只代表隔离库的管理员查询。 |
| PC | 生产构建、Vue 类型检查、ESLint、Stylelint 通过，7 个测试文件的 17 项全部通过。测试阶段有 Sass 旧接口弃用提醒。 |
| 手机 | 5 项单测通过。H5 条件编译后的应用源码类型检查通过。H5、App、微信小程序生产构建均完成。微信构建提示不支持底栏使用的 img 标签选择器，真机图标尺寸与选中效果待核对。[底栏样式](../../mobile/src/App.vue#L296) |
| 独立行业服务 | 危险作业服务 3 项测试通过，分别涉及并发重试与重启、租户和操作人边界与状态流转、签名有效期与应用身份。[测试](../../examples/hazard-work/test_service.py#L36) |

PC 和手机在临时副本中复用了本地已安装依赖。构建与单测没有覆盖所有页面操作、全新依赖安装或 App 真机。

隔离实例中的 15 项查询可按业务复核。括号内是该专用库本次返回的记录数。

| 领域 | 实际读取 |
| --- | --- |
| 租户、组织、IAM | 租户分页（1）、组织分页（0）、用户列表（4） |
| ERP、CRM | 商品（10）、库存（10）、客户（5） |
| 文件与导出、审计 | 文件对象（2）、导出任务（0）、高风险接口目录（27）、风险规则（4） |
| 集成、系统、调度、工作流、行业应用 | 数据源（2）、字典类型（4）、调度任务（2）、当前用户待办（0）、行业登记（0） |

空列表通过的是查询协议。组织设立、导出完成、办理审批和行业应用安装的业务结论，需看各自的写入或专项证据。这个全新实例还复现了开启二进制日志时受限迁移账号创建触发器被 MySQL 拒绝。对本次私有数据库调整测试设置后迁移成功，开发库无此改动。[触发器迁移](../../server/saas-basics-app/src/main/resources/db/migration/V27__iam_public_subject_binding.sql#L12)

## 能用到哪一步

| 领域 | 已有能力与实际边界 |
| --- | --- |
| 租户、组织、权限、审计 | 有租户开户、组织和人员模型、角色权限、配额和审计。此前实测过从 PC 开户到新租户登录，以及并发争用最后一个席位。撤权、独立库和生产配置仍有下节所列缺陷。[开户与配额复验](2026-11-19-remediation.md) · [组织模型](../M1_ORGANIZATION_IMPLEMENTATION_STATUS.md) |
| 原生行业服务清单 | 平台管理员可在 PC 登记服务，按租户发布、升级、停用，PC 与手机按终端和角色显示同一清单。现有表单字段仅支持文本、数字、日期，查询动作不接受配置参数。清单加字段可分租户升级，目前没有普通租户管理员单独覆盖该租户原表的配置入口。新增业务动作由行业服务实现。[清单校验](../../server/saas-basics-module-tenant/src/main/java/com/saasbasics/platform/modules/tenant/service/IndustryManifest.java#L35) · [发布与加字段](../DEVELOPING_INDUSTRY_APPS.md) |
| 低代码与扩展包 | 动态模型在 MySQL 上有发布、增删改查和归档记录。PC 建模器会拒绝已发布的同名模型，只注册 PC 菜单。手机工作台读取另一份行业目录。独立 JAR 可按租户安装和迁移，但生成的 Vue 页面要随 PC 重新构建，手机自动注册尚未交付。[模型发布](../../server/saas-basics-module-codegen/src/main/java/com/saasbasics/platform/modules/codegen/service/AiAppBuilderService.java#L129) · [手机目录](../../mobile/src/pages/index/index.vue#L320) · [扩展交付](../DEVELOPING_INDUSTRY_APPS.md#L94) |
| 第三方嵌入与开放平台 | PC iframe 和手机 web-view 均有实现，可设置不同的 HTTPS 地址。第三方须允许嵌入并支持约定的登录方式。已有 Webhook 测试投递与手动重放，仓库内未找到把普通业务事件投递到订阅并自动重试的消费者。仍缺第三方供应商的双端 SSO 验收。[嵌入说明](../DEVELOPING_INDUSTRY_APPS.md#L26) · [回调服务](../../server/saas-basics-module-integration/src/main/java/com/saasbasics/platform/modules/integration/service/WebhookSubscriptionService.java#L144) |
| 工作流与业务套件 | 引擎能查询真实待办、解析有效办理人。已有 ERP 真实采购与并发入库验收。CRM、库存、会议、耗材、资产都有入口。此前办公三项的逐项结果为“目标页呈现”，教育课程记录到生成预览。这些业务的完整生命周期仍缺操作记录。[业务复验](2026-11-19-remediation.md) · [原始测试明细](2026-11-19-platform-qa/测试明细.html) |
| 文件、导出与灾备 | 有上传配置、容量预留、导出数据权限和按表恢复。历史测试涉及上传失败补偿、并发与跨租户拒绝。租户 ZIP 的既定范围不含账号、审批引擎和文件实体。备份介质持久性、老式动态表和云对象存储仍需单独验收。[存储复验](2026-11-19-remediation.md) · [备份范围](../DEVELOPING_INDUSTRY_APPS.md#L98) |
| AI、报表和监控 | 有模型适配器、配额、运行记录、知识库、受控报表查询和单据打印入口。实际模型调用、样例输出、业务单据和健康检查在部分入口共用相同“成功”状态，详见下表。 |
| 工程与运行 | 已有 Java 模块边界检查、MyBatis-Plus 与复杂 SQL 的 Mapper XML 约束、迁移脚本、PC 类型和 lint 检查。持久化门禁列明支付 Mapper 和特定动态查询引擎的例外。两套本地后端可访问，发行物、生产数据库装配、多副本和外部供应商需要各自的验收。[持久化门禁](../../server/saas-basics-app/src/test/java/com/saasbasics/platform/architecture/PersistenceBoundaryTest.java) · [模块门禁](../../server/saas-basics-app/src/test/java/com/saasbasics/platform/architecture/ModuleBoundaryTest.java) |

## 需要处理的问题

“交付阻断”表示会直接影响权限、业务凭证、流程结论或承诺的隔离与恢复能力。“条件风险”列出触发条件，尚无证据表明开发环境已发生相应事故。

| 范围 | 当前行为、后果及证据 |
| --- | --- |
| 租户隔离 · 交付阻断 | “升级独立数据库”只写台账字段、刷缓存、记迁移成功，没有建库、数据复制、完整性校验或切换数据源。本机已验证的天枢专用数据库账号隔离的是同机不同项目。每个租户的物理库隔离仍未交付。[升级页面](../../web/src/views/tenant/sections/TenantIsolationSection.vue#L72) · [迁移服务](../../server/saas-basics-module-tenant/src/main/java/com/saasbasics/platform/modules/tenant/service/TenantService.java#L172) |
| 身份与双端授权 · 交付阻断 | 公共鉴权把用户名本身作为超级管理员判据之一，撤销角色后，部分接口仍可能放行。手机登录遇到后端的 MFA 挑战后没有输入动态码或恢复码的入口。手机的部分内置图标来自固定清单，停用相应模块仍可能看到入口。没有实测越租户访问。[身份判定](../../server/saas-basics-common/src/main/java/com/saasbasics/platform/common/auth/AuthPrincipal.java#L44) · [方法权限](../../server/saas-basics-framework/src/main/java/com/saasbasics/platform/security/PermissionGuardInterceptor.java#L84) · [手机登录](../../mobile/src/pages/login/login.vue#L394) · [手机入口](../../mobile/src/pages/index/index.vue#L325) |
| 套餐授权 · 条件风险 | 套餐计算会移除列举的平台治理权限，但保留通配授权时，功能判断仍可直接放行。取不到租户 Mapper 时也会生成全功能授权。这个结论限于套餐功能判断，平台接口另有身份检查，未观察到实际跨租户访问。[套餐计算](../../server/saas-basics-module-tenant/src/main/java/com/saasbasics/platform/modules/tenant/service/TenantFeatureService.java#L160) · [功能判定](../../server/saas-basics-module-tenant/src/main/java/com/saasbasics/platform/modules/tenant/service/TenantFeatureService.java#L71) |
| 工作流 · 交付阻断 | “退回发起人”产生临时业务节点，再次同意可能直接写为已通过，原引擎任务仍在。管理员修改流程后，旧单推进时会重读新定义，业务待办可能交给与引擎不同的人。[退回重提](../../server/saas-basics-module-workflow/src/main/java/com/saasbasics/platform/modules/workflow/service/WorkflowTaskService.java#L272) · [读取定义与办结](../../server/saas-basics-module-workflow/src/main/java/com/saasbasics/platform/modules/workflow/service/WorkflowTaskService.java#L428) |
| ERP、CRM 与打印 · 交付阻断 | 绑定真实采购单后，套打把全部物料合成“一批”，并填入固定仓库和验收人。绑定 CRM 合同会按合同 ID 生成发票号、填固定税率并沿用样票抬头中的部分字段。采购入库本身已修复，这里出错的是打印取数。[采购与合同套打](../../web/src/views/report/PrintCenterView.vue#L171) · [ERP 明细来源](../../server/saas-basics-module-integration/src/main/java/com/saasbasics/platform/modules/erp/service/ErpService.java#L310) |
| CRM 与办公业务规则 | 回款单号在同一租户当天仅有 90 种随机后缀，撞号会遇到唯一约束。会议预约模板标示可检测冲突，保存走通用插入，缺少同一会议室相交时段的拒绝规则。两者均影响正常业务操作。[回款编号](../../server/saas-basics-module-integration/src/main/java/com/saasbasics/platform/modules/crm/service/CrmService.java#L704) · [会议模板](../../server/saas-basics-module-codegen/src/main/java/com/saasbasics/platform/modules/codegen/service/AiAppBuilderService.java#L377) · [保存逻辑](../../server/saas-basics-module-codegen/src/main/java/com/saasbasics/platform/modules/codegen/service/DynamicDataService.java#L216) |
| 报表、监控与 AI 结果 · 交付阻断 | BI 取数失败会回空列表或空值，页面仍固定显示“系统全链路健康”。集成健康页用定时器完成体检并导出固定结果。AI 微调直接按公式生成准确率、损失和模型编号并写入已完成。聊天在上游失败后可能将本地模板回复记作完成并估算用量。模型训练效果和供应商用量均不能据此确认。[BI 大屏](../../web/src/views/screen/DataScreenView.vue#L110) · [体检](../../web/src/views/integration/sections/IntegrationHealthSection.vue#L20) · [微调](../../server/saas-basics-module-ai/src/main/java/com/saasbasics/platform/modules/ai/application/service/FinetuneStudioService.java#L295) · [聊天记录](../../server/saas-basics-module-ai/src/main/java/com/saasbasics/platform/modules/ai/application/service/AiChatGatewayService.java#L205) |
| 备份与文件 · 交付阻断 | 租户 ZIP 放 JVM 临时目录，默认 Helm 部署有两个服务副本，跨节点下载或节点重建时会出现“有登记、无文件”。备份对动态表仅接受带租户前缀的新命名，旧式已登记业务表可能被漏备。文件下载找不到原存储源时还会用相同对象键读默认源，有条件读到旧内容或不同对象。[快照介质](../../server/saas-basics-module-tenant/src/main/java/com/saasbasics/platform/modules/tenant/service/TenantMeteringService.java#L83) · [备份筛表](../../server/saas-basics-module-tenant/src/main/java/com/saasbasics/platform/modules/tenant/service/TenantBusinessSnapshot.java#L191) · [下载选源](../../server/saas-basics-module-file/src/main/java/com/saasbasics/platform/modules/file/service/PlatformFileStorageGatewayImpl.java#L226) |
| 调度、事件与导出 · 条件风险 | Redis 锁被其他实例正常占用时会跳过。Redis 调用抛错时各节点却能继续执行，且长任务的锁不续期。Outbox 找不到处理器也可标记已投递。导出提交给进程内线程池，未找到服务重启后重新领取未完成任务的入口。扩副本或重启时可能出现重复执行、状态误报或导出长时间等待。[任务锁](../../server/saas-basics-module-scheduler/src/main/java/com/saasbasics/platform/modules/scheduler/engine/JobExecutionEngine.java#L179) · [事件分发](../../server/saas-basics-framework/src/main/java/com/saasbasics/platform/outbox/PlatformOutboxService.java#L104) · [导出提交](../../server/saas-basics-module-file/src/main/java/com/saasbasics/platform/modules/file/service/EnterpriseExportService.java#L320) |
| 空库安装 · 现场复现 | 在独立 MySQL 中自定义业务库名，给平台账号授予该单库权限后，服务因首个迁移硬编码创建和切换到 saas_basics 而启动失败，MySQL 返回 1044。连接配置允许自定义库名，安装手册已指出首迁限制。从零安装到其他库名目前不能直接交付。[连接配置](../../server/saas-basics-app/src/main/resources/application-db.yml#L7) · [首个迁移](../../server/saas-basics-app/src/main/resources/db/migration/V1__enterprise_saas_foundation.sql#L4) · [安装说明](../DEVELOPING_INDUSTRY_APPS.md#L32) |
| 生产部署 · 条件风险 | 原样使用 Compose 或 Helm 会显式激活 prod，而组织 Mapper 的集中扫描只在 db 激活。Helm 生成的后端服务名还与 PC 镜像中 Nginx 固定的上游名不一致。没有在生产集群实部署，需验证组织 API 能完成读写、前端实例能启动和代理 API。[Compose](../../docker-compose.prod.yml#L52) · [Mapper 注册](../../server/saas-basics-app/src/main/java/com/saasbasics/platform/config/DbMapperScanConfig.java#L10) · [前端代理](../../web/nginx.conf#L24) · [集群服务名](../../deploy/helm/saas-basics/templates/service-server.yaml#L4) |
| 移动工作台 · 故障恢复 | 返回首页会先清空行业应用列表。目录请求失败后没有错误提示，下拉刷新也不重试，员工会以为应用被停用。[目录与刷新](../../mobile/src/pages/index/index.vue#L287) |

另外两类运维数字也需要标明来源。仪表盘 API 即使连上数据库，今日任务数和生成模块数仍使用常量。API 计数持久化会先推进内存游标，数据库写入失败后可能漏记该段增量。这两类数据不宜用于容量决策或服务质量承诺。[仪表盘指标](../../server/saas-basics-module-dashboard/src/main/java/com/saasbasics/platform/modules/dashboard/service/DashboardService.java#L28) · [计数同步](../../server/saas-basics-framework/src/main/java/com/saasbasics/platform/telemetry/PlatformApiCatalogService.java#L900) · [统计落库](../../server/saas-basics-module-system/src/main/java/com/saasbasics/platform/modules/system/service/ApiStatPersistenceService.java#L86)

## 发布前如何验收

1. **先校准业务结果。** 从界面原操作核对数据库或外部执行回执。退回重提后核对两边待办，打印逐行对采购单，模型服务离线时核对用户提示与用量记录，监控断开数据源时显示未知或失败。
2. **确定统一的应用发布契约。** 选一个原生行业应用和一个真实第三方系统，分别走登记、按租户授权、PC 与手机使用、给一个租户升级、停用和恢复。验收加字段后历史记录可读、另一租户用旧配置、在途单据按原流程完成。明确哪些规则由行业服务开发。
3. **在发行配置上验证恢复能力。** 用空库按实际生产清单部署两个实例，验证组织服务、任务唯一执行、导出重启恢复、跨节点取备份和历史业务表还原。对象存储、第三方 SSO、App 与小程序真机分别记录实际环境和结果。
