# SaaS Basics · 企业级多租户数字化中台底座

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Java-17%2B-blue.svg" alt="Java">
  <img src="https://img.shields.io/badge/Vue-3.4%2B-green.svg" alt="Vue">
  <img src="https://img.shields.io/badge/TypeScript-5.x-blue.svg" alt="TypeScript">
  <img src="https://img.shields.io/badge/Vite-6.x-purple.svg" alt="Vite">
  <img src="https://img.shields.io/badge/Element%20Plus-2.8%2B-blue.svg" alt="Element Plus">
  <img src="https://img.shields.io/badge/License-Apache%202.0-orange.svg" alt="License">
</p>

**SaaS Basics** 是一套面向全行业、云原生、现代化的企业级多租户数字化中台底座。

本项目不是单纯的后台管理模板或垂直业务系统，而是致力于为 **CRM、ERP、WMS、MES、OA 协同、政企服务、供应链** 等千行百业的数字化系统，提供开箱即用、权威标准、高内聚低耦合的**平台级公共基础设施能力**。

---

## 🌟 核心架构与功能矩阵

```
┌────────────────────────────────────────────────────────────────────────┐
│                        SaaS Basics 平台架构全景图                       │
├────────────────────────────────────────────────────────────────────────┤
│ 【接入控制面】统一门户 / 移动端 H5 / 小程序 / 第三方 API Webhook / SSO   │
├────────────────────────────────────────────────────────────────────────┤
│ 【业务中台套件】                                                       │
│   ├─ 客户中心 (CRM)   : 线索跟进 · 商机漏斗 · 客户公海 · 合同回款       │
│   ├─ 进销存 (ERP)     : 商品物料 · 采购订单 · 出入库流水 · 动态库存     │
│   └─ 可视化决策        : 企业数据大屏 · 报表打印中心 · 审计合规大屏      │
├────────────────────────────────────────────────────────────────────────┤
│ 【核心中台能力】                                                       │
│   ├─ 多租户中心       : 4类数据隔离 · 租户自助入驻 · 配额套餐 · 账本   │
│   ├─ 组织与身份 (IAM) : 集团-企业-部门架构 · RBAC三位一体 · 行级数据范围│
│   ├─ 工作流引擎       : 串并行审批 · 流程图可视化 · 节点加签 · 审计     │
│   ├─ 分布式调度       : Quartz集群 · 任务管线 · 实时熔断 · 告警基线     │
│   ├─ 文件资产中心     : 多存储引擎 · 分片秒传 · 生命周期归档 · 审计     │
│   ├─ 低代码代码生成   : 元数据逆向 · 前后端一体化模板 · 质量门禁        │
│   └─ 集成消息中心     : 短信邮件企微网关 · 连接池探针 · Webhook/死信补偿│
├────────────────────────────────────────────────────────────────────────┤
│ 【系统底座支撑】全平台在线会话强退 · 软删除自愈回收站 · 慢SQL诊断 · Redis/JVM监控 │
└────────────────────────────────────────────────────────────────────────┘
```

---

### 1. 🏢 多租户中心 (Multi-Tenant Management)
- **权威数据隔离方案**：原生支持字段级共享隔离（`tenant_id`）、独立 Schema、独立数据库以及混合隔离模式，在框架底层利用 AST SQL 拦截器实现无感穿透防护。
- **租户全生命周期管理**：租户开通申请、在线签约审批、套餐升降配、租户白名单/黑名单以及租户注销冻结。
- **多维度配额管控 (Quota & Plan)**：支持用户数、存储容量、API 调用频次、并发连接数的动态配额计算与超额熔断。

### 2. 🛡️ 身份与权限中心 (Enterprise IAM & Data Scope)
- **多企业/集团化多层级组织架构**：标准支持 `集团 ➔ 企业/子公司 ➔ 部门 ➔ 岗位 ➔ 员工` 五级实体，适配大型多元化企业集团与母子公司管控模式。
- **三位一体权限体系**：
  - **功能菜单权限**：动态路由树、徽标、菜单代码与角色深度绑定。
  - **按钮操作权限**：细粒度前端指令（`v-button-permission`）与后端 API 端点鉴权。
  - **行级数据范围 (Data Scope)**：原生支持“全部数据”、“本集团数据”、“本企业数据”、“本部门及下级部门”、“仅本人数据”以及“自定义部门”规则。
- **权限透视诊断器与模拟器**：支持输入任意人员与操作代码，毫秒级穿透输出命中规则链、决策裁定（PASS/DENY）及底层自动改写的 SQL 条件。

### 3. ⚙️ 轻量级工作流引擎 (Workflow Engine)
- **内嵌企业级工作流状态机**：支持请假报销、合同审批、租户入驻等业务的工单发起、自动流转、串并行会签、驳回重审、转办与自愈。
- **全流程审计追踪**：完整的任务执行日志、处理人耗时留痕与审批意见。
- **低门槛可视化流程设计器**：支持图形化节点编排与流程定义在线发布。

### 4. ⏱️ 分布式任务调度中心 (Scheduler Center)
- **分布式集群架构**：基于 Quartz 集群，支持主备选举、节点负载分发与毫秒级心跳检测。
- **链路透视与管线监控**：提供调度任务“解析 ➔ 校验 ➔ 分发 ➔ 隔离执行 ➔ 结果留痕”五步流转管线。
- **全方位告警基线与应急熔断**：任务执行失败、执行超时、漏触发毫秒级捕获，支持 Webhook、邮件、企业微信推送与全平台一键紧急熔断。

### 5. 📁 分布式文件资产中心 (File Center)
- **多存储引擎即插即用**：统一抽象底层存储协议，支持 Local 本地文件、MinIO、阿里云 OSS、腾讯云 COS、华为云 OBS、AWS S3。
- **文件全生命周期治理**：冷热数据分层、自动归档周期设定、物理彻底粉碎、哈希防重校验与下载审计。

### 6. 💻 低代码与代码生成中心 (Codegen Center)
- **元数据逆向工程**：一键读取 MySQL 表结构、字段定义、主键索引与字段注释。
- **前后端一体化脚手架**：生成遵循规范的 Entity、Mapper、Service、Controller、DTO、API 接口定义以及 Vue3 ProTable 页面。
- **质量门禁与即时预览**：内置覆盖率、代码规范、多租户字段合规性静态门禁检查。

### 7. 🔗 系统集成中心 (Integration Center)
- **统一多通道消息网关**：集成短信 SMS（阿里云/腾讯云）、企业邮件、微信工作台通知。
- **连接池健康实时探针**：动态对业务数据源、从库副本进行连接有效性探针探测与网络往返延迟分析。
- **Webhook 事件总线与死信补偿**：事件触发、HMAC-SHA256 请求签名、24 小时幂等去重，重试衰竭后自动进入死信队列（DLQ）并支持一键重投。

### 8. 📊 系统底座与安全审计 (System & Audit)
- **在线会话治理**：实时监控 Redis 与全局访问令牌（AccessToken / RefreshToken），支持多端在线审计与特权一键强退。
- **数据回收站自愈引擎**：全平台业务模型软删除数据统一归集，按应用分面统计，支持级联自愈还原与物理永久粉碎。
- **基础设施实时监控**：Redis 缓存键空间与命中率大屏、慢 SQL 智能拦截诊断、JVM 堆与 GC 运行时健康。
- **等保三级安全合规**：密码复杂度矩阵、历史密码留存、会话无操作自动注销、三员分立审计日志防篡改。

---

## 🛠️ 技术栈清单

### 后端技术栈 (Backend)
| 组件 / 框架 | 版本 | 说明 |
| :--- | :--- | :--- |
| **Java** | 17 LTS | 开发语言基础 |
| **Spring Boot** | 3.3.5 | 核心微服务 / 单体底座应用框架 |
| **MyBatis-Plus** | 3.5.7 | ORM 持久层框架，提供多租户拦截与分页扩展 |
| **Dynamic-Datasource**| 4.3.0 | 动态多数据源切换与读写分离 |
| **Quartz** | 2.3.2 | 分布式高可用任务调度引擎 |
| **Redis & Lettuce** | 6.x+ | 分布式会话缓存、字典缓存与分布式锁 |
| **Caffeine** | 3.1.8 | 高性能本地一级缓存，构成二级混合缓存 |
| **Flyway** | 10.x | 数据库版本控制与结构迁移自动化 |
| **HikariCP** | 5.1.0 | 高性能数据库连接池 |
| **Hutool & JWT** | 5.8+ | 工具库与无状态令牌鉴权 |

### 前端技术栈 (Frontend)
| 组件 / 框架 | 版本 | 说明 |
| :--- | :--- | :--- |
| **Vue** | 3.4+ | 响应式核心框架，采用 `<script setup lang="ts">` 语法 |
| **Vite** | 6.x | 现代极速前端构建与热重载工具 |
| **TypeScript** | 5.x | 全静态类型支持，消除隐式类型错误 |
| **Element Plus** | 2.8+ | 核心 UI 组件库，定制现代化企业级轻量主题 |
| **Pinia** | 2.2+ | 新一代状态管理容器 |
| **Vue Router** | 4.x | 前端动态路由体系，支持异步加载与权限守卫 |
| **ECharts** | 5.x | 数据可视化图表库 |
| **SCSS** | 1.x | 现代 CSS 预处理器 |

---

## 🚀 快速启动指南

### 1. 环境准备
确保本机或服务器已安装以下基础环境：
- **JDK 17+**
- **Node.js 20+** 及 **npm 10+**
- **MySQL 8.0+**
- **Redis 6.0+**

### 2. 数据库配置
1. 创建数据库 `saas_basics`，字符集推荐使用 `utf8mb4`，排序规则 `utf8mb4_general_ci`。
2. 后端采用 **Flyway** 进行数据库自动化迁移，启动时会自动执行 `sql/` 下的迁移脚本，无需手动导入全量 SQL。

### 3. 后端启动 (Backend)
进入后端主模块或配置环境变量：
```bash
# 方式一：直接使用 Maven 构建与运行
cd server
mvn clean package -DskipTests

# 方式二：运行可执行 Jar
java -jar saas-basics-app/target/saas-basics-app-0.1.0-SNAPSHOT.jar \
  --spring.datasource.url=jdbc:mysql://127.0.0.1:3306/saas_basics?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai \
  --spring.datasource.username=root \
  --spring.datasource.password=your_password
```
后端服务默认监听端口：`http://127.0.0.1:8080`

### 4. 前端启动 (Frontend)
进入前端项目目录：
```bash
cd web

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 编译生产包
npm run build
```
前端开发服务默认监听端口：`http://localhost:9527`

### 5. 初始登录凭证
系统预设顶级特权账户与租户开箱演示账户：
- **平台超级管理员**：
  - 账号：`admin`
  - 密码：`Admin@123456`
  - 权限：拥有全平台、全租户控制面管理与底层治理权限
- **租户企业管理员**：
  - 账号：`tenant_admin`
  - 密码：`Tenant@123456`
  - 权限：企业自闭环管理与业务审批

---

## 📂 项目代码目录结构

```text
saas_basics/
├── server/                               # 后端 Spring Boot 模块化单体
│   ├── saas-basics-common/               # 公共常量、上下文、异常、统一响应与工具类
│   ├── saas-basics-app/                  # 系统启动入口、Web 配置与安全拦截器
│   ├── saas-basics-module-iam/           # 身份凭证、组织架构、RBAC+ABAC 权限、会话管理
│   ├── saas-basics-module-tenant/        # 多租户管理、数据隔离、配额与套餐
│   ├── saas-basics-module-workflow/      # 内嵌轻量级工作流状态机引擎
│   ├── saas-basics-module-scheduler/     # Quartz 分布式高可用调度中心
│   ├── saas-basics-module-file/          # 分布式文件中心、多存储适配
│   ├── saas-basics-module-codegen/       # 代码生成中心、元数据逆向解析
│   ├── saas-basics-module-integration/   # 消息通道、数据源连接池探测、Webhook 死信队列
│   ├── saas-basics-module-audit/         # 全链路行为审计、风控识别与黑名单阻断
│   └── saas-basics-module-system/        # 系统配置、数据字典、序列号、回收站、监控
├── web/                                  # 前端 Vue3 + TypeScript 控制台
│   ├── src/
│   │   ├── api/                          # 前端 API 模块化请求层
│   │   ├── assets/                       # 静态资源、登录壁纸、图标
│   │   ├── components/                   # 公共业务组件 (ProTable, SchemaForm, 状态点等)
│   │   ├── router/                       # 路由守卫与动态路由树
│   │   ├── stores/                       # Pinia 全局状态管理 (Auth, Notify, Tags)
│   │   ├── views/                        # 业务中心视图层
│   │   │   ├── tenant/                   # 租户中心
│   │   │   ├── iam/                      # 身份权限中心
│   │   │   ├── workflow/                 # 工作流中心
│   │   │   ├── scheduler/                # 调度中心
│   │   │   ├── file/                     # 文件中心
│   │   │   ├── codegen/                  # 代码生成中心
│   │   │   ├── integration/              # 集成中心
│   │   │   ├── system/                   # 系统底座与监控
│   │   │   ├── audit/                    # 审计中心
│   │   │   ├── crm/                      # 客户管理中心
│   │   │   └── erp/                      # 进销存管理中心
│   │   └── main.ts                       # 前端入口文件
│   ├── package.json
│   └── vite.config.ts
├── sql/                                  # 数据库增量与全量迁移 SQL 脚本
└── README.md                             # 项目工程全景介绍文档
```

---

## 📄 开源许可证

本项目基于 [Apache License 2.0](LICENSE) 协议发布，商业应用友好，可自由扩展与二次定制开发。
