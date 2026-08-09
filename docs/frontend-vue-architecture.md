# SaaS Basics 前端架构规范（Vue 版）

> 历史设计输入：技术选型继续保留，当前产品范围、质量门禁和实施顺序以 [`V0.1_EXECUTION_BLUEPRINT.md`](./V0.1_EXECUTION_BLUEPRINT.md) 为准。

## 1. 定版结论

`saas_basics` 前端底座统一采用：

- `Vue 3.5`
- `TypeScript`
- `Vite 7`
- `Pinia`
- `Vue Router 4`
- `Element Plus` 深度二次封装

这不是“先随便选一个前端框架”，而是明确服务于企业级 SaaS 底座的工程目标：

- 权限系统复杂
- 表单/表格页面密集
- 代码生成页面多
- 需要统一交互语言和视觉风格
- 需要低成本批量扩展业务模块

## 2. 为什么选 Vue，不选 React

### Vue 更适合当前底座目标

- 更适合中后台场景下的模板化开发
- 更利于生成式页面统一结构
- 表单、表格、抽屉、弹窗等页面组织更直接
- 团队新人接手成本更低
- 与代码生成器结合时，生成结果更稳定、更可维护

### React 不作为当前优先方案的原因

- 灵活度高，但后台系统会引入更多工程约束成本
- 复杂页面容易堆积状态与胶水代码
- 元数据驱动页面生成成本更高
- 为了“可生成、可维护、可统一”，Vue 的综合性价比更高

结论不是技术鄙视链，而是项目类型决定技术取舍。

## 3. 前端目标形态

前端不是单纯后台模板，而是平台控制台：

- 平台运营中心
- 租户管理中心
- 权限与身份中心
- 文件与集成中心
- 调度与自动化中心
- 代码生成中心

所以前端必须具备三类能力：

- 业务页面承载能力
- 平台配置与策略编排能力
- 元数据驱动渲染能力

## 4. 技术栈建议

### 核心依赖

- `vue`
- `typescript`
- `vite`
- `pinia`
- `vue-router`
- `axios`
- `element-plus`
- `@vueuse/core`
- `unplugin-auto-import`
- `unplugin-vue-components`
- `sass`
- `echarts`
- `dayjs`
- `nprogress`

### 工程依赖

- `eslint`
- `@typescript-eslint/*`
- `prettier`
- `stylelint`
- `vitest`
- `@vue/test-utils`
- `playwright`

### 不建议一开始就引入的东西

- 过重的低代码引擎
- 过多状态管理库
- 微前端框架
- 全家桶式“后台模板脚手架”

底座第一阶段优先保证统一性，不优先追求花哨架构。

## 5. UI 设计方向

我们不要“千篇一律开源后台模板风格”。

建议视觉方向：

- 基础色偏中性灰 + 深蓝/墨绿其中一种品牌主色
- 信息分区清晰，强调控制台秩序感
- 表格页强调密度和扫描效率
- 详情页强调信息层次，不堆卡片
- 配置页强调步骤感、策略感、状态感

组件层分三层：

### 5.1 基础层 Base

- `BasePage`
- `BaseCard`
- `BaseDrawer`
- `BaseDialog`
- `BaseTable`
- `BaseForm`
- `BaseSearchBar`
- `BaseStatusTag`

### 5.2 业务增强层 Pro

- `ProTable`
- `ProForm`
- `ProDescriptions`
- `ProTreeFilter`
- `ProPermissionButton`
- `ProDictTag`
- `ProTenantSelector`
- `ProFileUploader`

### 5.3 平台能力层 Platform

- `TenantScopeSwitch`
- `PackageQuotaPanel`
- `DataScopeEditor`
- `ApiPermissionMatrix`
- `CodegenPreviewPanel`
- `DatasourceConnectionTester`
- `JobTriggerBuilder`

## 6. 目录结构规范

建议目录：

```text
web/
  src/
    api/
      modules/
    assets/
      icons/
      images/
      styles/
    components/
      base/
      pro/
      platform/
    config/
    constants/
    directives/
    hooks/
    layouts/
    router/
      guards/
      routes/
    stores/
      modules/
    types/
    utils/
    views/
      dashboard/
      tenant/
      iam/
      system/
      integration/
      file/
      scheduler/
      codegen/
      audit/
    App.vue
    main.ts
```

规则：

- `api/modules` 按领域拆分，不允许全项目共用一个超大 API 文件
- `views` 按业务域拆分，不按菜单名字随意堆
- `components/base` 不依赖具体业务
- `components/platform` 允许承载平台级复杂组件
- `stores/modules` 只存全局状态，不把页面局部状态乱塞进去

## 7. 路由体系

路由分三层：

- 固定路由
- 权限动态路由
- 元数据生成路由

### 固定路由

- 登录
- 403
- 404
- 首页
- 个人中心

### 动态路由

根据菜单、权限点、租户套餐、功能开关综合装载：

- 菜单可见不等于按钮可用
- 页面可访问不等于 API 可调用
- 路由装载受租户应用开通控制

### 路由元数据建议字段

- `title`
- `icon`
- `permissionCode`
- `featureFlag`
- `tenantApp`
- `keepAlive`
- `activeMenu`
- `hidden`
- `affix`

## 8. 状态管理规范

Pinia 只管理真正跨页面共享的状态：

- 当前用户
- 当前租户
- 权限快照
- 字典缓存
- 主题配置
- 全局标签页
- 通知消息

不要把这些放进全局状态：

- 普通查询条件
- 单页弹窗开关
- 单页表格选择项
- 临时表单草稿

原则：

- 页面状态优先放页面内
- 可复用页面逻辑抽到 `hooks`
- 跨模块共享才进入 `store`

认证状态落地要求：

- `auth` store 负责登录、退出、当前用户、登录态恢复
- `accessToken` 持久化到本地存储，刷新后自动恢复
- `http` 拦截器只注入 `Authorization`；租户来自服务端会话，不接受客户端请求头覆盖
- 遇到 `401` 或 `AUTH_UNAUTHORIZED` 自动清理本地状态并跳转 `/login`
- 路由 `meta.permissionCode` 必须和后端 `@RequirePermission` 采用同一套权限码

## 9. API 层规范

每个业务域一个 API 文件夹，例如：

```text
api/modules/tenant/
api/modules/iam/
api/modules/system/
api/modules/integration/
api/modules/file/
api/modules/scheduler/
api/modules/codegen/
```

建议结构：

- `index.ts` 负责导出
- `types.ts` 存放请求/响应类型
- `service.ts` 存放请求函数

请求层约束：

- 统一错误码处理
- 统一租户头透传
- 统一幂等请求封装
- 统一导出下载能力
- 统一分页响应结构

## 10. 权限前端建模

前端至少要识别四类控制：

- 菜单权限
- 按钮权限
- API 权限
- 功能开关

推荐封装：

- `v-permission`
- `v-feature`
- `PermissionButton`
- `FeatureSwitch`

不要只做“按钮隐藏”这种弱权限，前端还要配合：

- 路由拦截
- 页面级禁用
- 空态提示
- 功能未开通提示

## 11. 字典与配置体系

字典必须变成前端基础设施，而不是零散接口调用：

- 启动时预热高频字典
- 支持懒加载字典
- 支持租户覆盖字典
- 支持颜色、标签、扩展属性

推荐能力：

- `useDict`
- `DictTag`
- `DictSelect`
- `DictRadioGroup`

## 12. 表单与表格体系

底座项目不能靠手写零散 CRUD 页面撑起来，必须抽象。

### 表格能力

- 查询栏
- 列配置
- 密度切换
- 权限列控制
- 导出
- 批量操作
- 空态
- 行级操作位

### 表单能力

- Schema 驱动字段渲染
- 字段级权限控制
- 字典绑定
- 远程选项加载
- 联动校验
- 审计字段只读展示

### 必须封装的通用能力

- `ProTable`
- `SchemaForm`
- `SearchSchemaBuilder`
- `ColumnSettingPanel`
- `BatchActionBar`

## 13. 代码生成器面向 Vue 的输出规范

代码生成器输出 Vue 页面时，必须统一生成：

- 列表页
- 查询 Schema
- 新增/编辑抽屉
- 详情抽屉
- API 文件
- 类型定义
- 路由定义片段
- 权限码建议值

字段元数据至少支持：

- 是否列表展示
- 是否查询条件
- 查询方式
- 表单组件
- 字典绑定
- 是否只读
- 是否必填
- 默认值
- 占位提示
- 排序顺序

页面生成风格建议：

- 列表页主导
- 表单弹窗/抽屉统一
- 不生成又厚又乱的单文件大页面

## 14. 租户能力前端支持

前端必须原生支持 SaaS 场景：

- 当前租户切换
- 租户套餐能力差异
- 功能开通状态判断
- 平台管理员与租户管理员界面差异
- 平台级数据与租户级数据边界提示

必须有这些组件或能力：

- `TenantSwitcher`
- `TenantBadge`
- `FeaturePlanLimitAlert`
- `PlatformOnlyBlock`

## 15. 文件中心前端能力

文件不是普通上传框，要作为平台能力设计：

- 上传
- 预览
- 引用绑定
- 多版本
- 下载审计
- 存储器切换

推荐组件：

- `FileUploader`
- `FilePicker`
- `FilePreviewDialog`
- `FileVersionList`
- `FileRelationPanel`

## 16. 调度中心前端能力

调度页不能只做一个 CRUD 表单。

要支持：

- CRON 表达式编辑
- 触发策略配置
- 执行日志查看
- 重试与重跑
- 执行器节点状态查看

推荐组件：

- `CronBuilder`
- `JobTriggerEditor`
- `JobLogTimeline`
- `ExecutorStatusPanel`

## 17. 主题与样式规范

建议使用：

- `SCSS + CSS Variables`

主题变量分层：

- 品牌变量
- 功能变量
- 布局变量
- 组件变量

最低要求：

- 支持亮色主题
- 保留暗色扩展位
- 不直接改第三方组件源码
- 所有平台风格调整走自有 token

## 18. 测试与质量要求

前端最低质量要求：

- ESLint
- Prettier
- Stylelint
- Vitest 单测
- Playwright 关键链路测试

优先覆盖的关键链路：

- 登录
- 切租户
- 权限装载
- 菜单渲染
- 字典渲染
- 文件上传
- 任务创建
- 代码生成预览

## 19. 构建与环境

建议环境分层：

- `dev`
- `test`
- `staging`
- `prod`

环境变量规范：

- `VITE_APP_TITLE`
- `VITE_API_BASE_URL`
- `VITE_PUBLIC_PATH`
- `VITE_ENABLE_MOCK`
- `VITE_ENABLE_DEVTOOLS`

不要把租户配置、动态菜单、功能开关写死到构建期。

## 20. 第一阶段页面清单

前端第一阶段建议先落这几组页面：

- 登录页
- 工作台首页
- 租户管理
- 套餐管理
- 用户管理
- 员工管理
- 部门管理
- 岗位管理
- 角色管理
- 菜单管理
- API 资源管理
- 数据权限策略
- 字典管理
- 配置管理
- 数据源管理
- 文件中心
- 任务调度
- 代码生成
- 登录日志
- 操作日志

## 21. 下一步实现建议

如果继续往下做，建议直接在仓库里落：

1. `web` 前端工程骨架
2. 布局系统
3. 登录与权限基础链路
4. 字典与配置基础设施
5. `ProTable` 和 `SchemaForm`
6. 第一批 SaaS 底座页面

这样后面的代码生成器才能准确对接前端页面规范。
