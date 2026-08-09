# 标准组织模型与 V25 数据库设计

状态：`Accepted`

目标里程碑：`M1`

## 1. 决策

organization 核心只表达跨行业稳定存在的组织语义，不预设集团、公司、法人、医院、学校、政府机构等类型链路。

V25 创建通用组织主体、组织关系、组织单元、人员参与关系和任职模型。法律登记、税务、教育、医疗等属性只能由独立扩展包提供，核心模块不依赖任何扩展包。

标准语义参考 W3C Organization Ontology；身份交换参考 SCIM 2.0。参考标准用于统一概念和外部契约，不要求内部数据库使用 RDF 或复制 SCIM 资源结构。

## 2. 稳定核心

| 表 | 责任 | 关键约束 |
|---|---|---|
| `org_organization` | 通用组织主体 | 租户内编码唯一；不包含行业或地域专有字段 |
| `org_organization_classification` | 主体分类 | 命名空间、编码和版本唯一；支持多分类 |
| `org_organization_capability` | 主体能力 | 表达可雇佣、可核算、可签约等能力，不改变核心表结构 |
| `org_organization_relation` | 主体间关系 | 有方向、类型、有效期和状态；禁止自关联 |
| `org_unit` | 主组织单元树 | 属于一个组织主体；父节点必须属于同一主体 |
| `org_unit_relation` | 辅助组织关系 | 表达矩阵、项目、服务等非主树关系 |
| `org_person` | 租户内人员主体 | 与账号、参与关系和任职分离 |
| `org_engagement` | 人与组织主体的关系 | 类型、有效期和状态均版本化 |
| `org_position` | 岗位或职责位置 | 属于组织主体，可选归属组织单元 |
| `org_assignment` | 有效任职 | 关联参与关系、组织单元和岗位，保留完整历史 |

内部主键可以使用 bigint；所有公开 API 使用不可枚举的 UUIDv7 `public_id`，格式遵循 RFC 9562。

## 3. 分类、能力与关系类型

平台内置的不是行业枚举，而是版本化代码注册机制：

```text
namespace: urn:saas-basics:organization
code: ORGANIZATION
version: 1.0.0
```

扩展包使用自己的反向域名或 URN 命名空间。代码一经发布不可改变语义，破坏性调整必须发布新版本。

`OrganizationClassification` 用于检索和呈现；`OrganizationCapability` 表达可以执行的标准行为。核心业务不能通过 `if classification == COMPANY` 产生结构分支，而应检查能力或调用扩展 API。

## 4. 关系模型

```text
Tenant
  -> Organization <-> OrganizationRelation
      -> OrganizationClassification
      -> OrganizationCapability
      -> OrgUnit -> Position

Person
  -> Engagement -> Organization
  -> Assignment -> Engagement + OrgUnit + Position
  -> User -> Identity
```

- 组织主体形成关系网络，不强制唯一父主体。
- 每个组织主体拥有一棵主组织单元树。
- 辅助组织关系不能改变主树路径和默认数据范围。
- 一个人可以同时与多个组织建立不同类型的参与关系。
- 同一参与关系最多一个当前主任职，可以有多个兼任。
- 有效期使用左闭右开语义：`valid_from <= now < valid_to`；空结束时间表示持续有效。
- 调岗和跨组织变动通过关闭旧任职并创建新任职完成，不覆盖历史。

## 5. 类型化扩展

扩展包可以为 `Organization`、`Person`、`Engagement` 等主体增加一对一或一对多扩展表，但必须满足：

- 独立 Maven 模块、数据库迁移、API 命名空间和语义版本。
- 只依赖 organization 的公开 `api` 包。
- 通过 `organization_public_id` 关联，不读取核心 Mapper。
- 提供安装、升级、卸载前检查和数据保留策略。
- 对应国家或行业标准必须在扩展文档中声明版本。

法律主体档案是这类扩展的示例，不属于 V25 核心迁移。

## 6. 通用格式

- 时间在数据库和事件中存 UTC；API 使用 RFC 3339，并保留 IANA 时区 ID 作为展示配置。
- 语言标签使用 BCP 47，国家或地区使用 ISO 3166-1，货币使用 ISO 4217。
- 电话号码使用 E.164；无法标准化的原始输入保存在受控扩展字段，不作为核心匹配键。
- 地址使用结构化地址契约和国家扩展，不在核心人员或组织表中增加固定行政区字段。
- 敏感身份标识只保存凭证服务引用、密文或不可逆查找摘要，不进入普通日志。

## 7. V25 迁移

V25 只创建第 2 节列出的标准核心表、代码注册表和必要索引，不修改 V1-V24，不创建任何国家或行业扩展字段。

所有表必须：

- 包含非空 `tenant_id`、`public_id` 和统一审计字段。
- 唯一索引以 `tenant_id` 开头，并包含逻辑删除维度。
- 高频索引以 `tenant_id` 开头，并覆盖状态或有效期。
- 不使用 JSON 保存主体关系、任职或分类等核心语义。
- 不创建跨模块物理外键；模块内引用由应用服务和集成测试保证完整性。

## 8. 历史数据迁移

| 旧对象 | 标准目标 |
|---|---|
| `iam_department` | `org_unit` |
| `iam_position` | `org_position` |
| `iam_employee` | `org_person` + `org_engagement` + `org_assignment` |
| `iam_user.employee_id` | 通过公开人员账号绑定契约关联，不继续直接引用旧表 |

旧数据无法确定组织主体时必须进入待映射队列，由迁移配置明确目标组织；迁移脚本不能静默创建带行业含义的默认主体。

迁移分为建表、映射校验、数据回填、双读差异报告、接口切换五个独立版本。不采用长期双写。

## 9. 标准 API

- `/api/organizations`
- `/api/organization-relations`
- `/api/organization-classifications`
- `/api/organization-capabilities`
- `/api/org-units`
- `/api/org-units/{publicId}/move`
- `/api/persons`
- `/api/engagements`
- `/api/positions`
- `/api/assignments`
- `/api/assignments/{publicId}/terminate`
- `/api/organization-context/options`

跨模块公开 Facade 只提供组织摘要、有效参与关系、有效任职、组织范围和上下文校验，不暴露 Entity、Mapper、内部 ID 或树路径实现。

## 10. 验收

- 同一模型能够表达商业组织、公共机构、非营利组织和项目型组织，无需增加核心字段。
- 多组织关系、多组织单元、多参与关系和多任职均有完整生命周期、并发和历史测试。
- 安装或移除地域扩展不改变核心 API、表结构和权限语义。
- 旧 IAM 数据迁移可重复执行，未映射数据明确失败，不产生孤儿记录。
- 核心源码、迁移和种子数据不包含行业实体类型、地域登记字段或供应商名称。
