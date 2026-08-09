# Organization V25 数据库设计评审稿

状态：`Proposed`

目标里程碑：`M1`

## 1. 结论

V25 只追加 organization 模块的新表，不修改 V1-V24，不立即删除 `iam_department`、`iam_position`、`iam_employee`。旧数据回填和接口切换使用后续迁移，确保可以分阶段验证和回滚应用版本。

一个租户可以有多个企业集团；集团包含多个法人主体。自然人、登录账号、雇佣关系和任职必须分离。

## 2. 模块与表

新建 Maven 模块：`saas-basics-module-organization`。

| 表 | 主体 | 关键字段与约束 |
|---|---|---|
| `org_enterprise_group` | 企业集团 | `tenant_id`、`group_code`、名称、状态；租户内编码唯一 |
| `org_legal_entity` | 法人或独立核算主体 | 集团、法人类型、信用代码、地区、有效期；信用代码在租户内唯一 |
| `org_unit` | 主组织树节点 | 法人、父节点、类型、路径、层级、状态、有效期；租户内编码唯一 |
| `org_unit_relation` | 辅助组织关系 | 来源组织、目标组织、关系类型、有效期；禁止重复和自关联 |
| `org_person` | 租户内自然人档案 | 人员编号、姓名、联系方式、证件密文引用、状态 |
| `org_employment` | 人与法人的雇佣关系 | 人、法人、关系类型、员工号、起止日期、状态 |
| `org_position` | 岗位编制 | 法人、组织、岗位编码、名称、职级、编制数、状态 |
| `org_assignment` | 有效任职 | 雇佣关系、组织、岗位、任职类型、主次标记、起止日期、状态 |

所有表使用现有审计字段：`created_by`、`created_at`、`updated_by`、`updated_at`、`deleted`、`deleted_at`、`version`。

## 3. 关键领域规则

### 企业与组织

- `Tenant` 是隔离边界，不等于集团或法人。
- 法人必须属于同租户集团；允许集团为空，以支持单体企业的渐进建模。
- `OrgUnit` 的主树只能在同一法人内形成父子关系。
- 跨法人、项目组、矩阵汇报使用 `org_unit_relation`，不能修改主树语义。
- 组织移动必须同步重算后代 `tree_path` 和 `tree_level`，并使用乐观锁防止并发覆盖。
- 已停用组织不能新增岗位和任职；历史记录不物理删除。

### 人员与任职

- 一个 `Person` 可以有多条 `Employment`，从而跨公司任职。
- `Assignment` 必须引用有效雇佣关系，岗位和组织必须属于该雇佣关系的法人。
- 同一雇佣关系最多一个当前主任职；可以有多个兼职任职。
- 起止日期采用左闭右开语义：`start_at <= now < end_at`，空结束时间表示长期有效。
- 调岗创建新的任职历史或关闭旧任职后新建，不能覆盖历史组织与岗位。
- `Position` 是组织职责，不是权限角色；权限通过后续 `RoleBinding` 引用任职或组织范围。

## 4. V25 表结构约束

- 所有表 `tenant_id bigint unsigned NOT NULL`。
- 所有唯一索引以 `tenant_id` 开头并包含 `deleted`。
- 常用有效记录索引以 `tenant_id` 开头，并包含状态或有效期。
- 模块内引用暂不创建物理外键，与现有迁移策略保持一致；应用服务和集成测试负责引用完整性。
- 法人信用代码、手机号和证件信息不得进入普通日志；证件信息只保存加密值或凭证服务引用。
- 表注释、状态枚举和错误码在执行迁移前冻结。

## 5. 迁移分段

| 迁移 | 内容 | 回滚策略 |
|---|---|---|
| V25 | 创建 organization 新表和最小平台权限资源 | 应用版本不读取新表时可直接回退 |
| V26 | 将部门、岗位、员工映射到新表，写迁移映射表 | 保留旧表，重复执行保持幂等 |
| V27 | 启用双读校验和差异报告，不启用双写 | 关闭新读路径即可回退 |
| V28 | API 切换到 organization，IAM 只保留账号和授权 | 保留旧接口兼容期 |

不采用长期双写。迁移期间以旧表为源执行一次回填，之后在维护窗口切换写入口。

## 6. 旧模型映射

| 旧对象 | 新对象 |
|---|---|
| `iam_department` | `org_unit`，默认归入每租户的迁移法人 |
| `iam_position` | `org_position` |
| `iam_employee` | `org_person` + `org_employment` + 主 `org_assignment` |
| `iam_user.employee_id` | 通过人员账号绑定表或公开 Facade 关联，不继续直接引用员工表 |

无法确定法人归属的旧数据进入迁移法人并产生差异记录，不静默猜测真实公司。

## 7. v0.1 API 草案

- `/api/organization/groups`
- `/api/organization/legal-entities`
- `/api/organization/org-units`
- `/api/organization/org-units/{id}/move`
- `/api/organization/persons`
- `/api/organization/employments`
- `/api/organization/assignments`
- `/api/organization/assignments/{id}/terminate`
- `/api/organization/context/options`

公开给其它模块的 `organization/api` 只提供人员摘要、有效任职、组织范围和上下文校验，不暴露 Entity、Mapper 或内部树路径实现。

## 8. 评审通过条件

- 两个法人、跨公司任职和一人多岗位的样例可以无歧义落表。
- 组织移动、停用、调岗和终止任职都有并发与历史规则。
- V26 回填脚本对现有种子数据可重复执行且无孤儿记录。
- IAM RoleBinding 可以只引用公开组织标识，不依赖 organization Mapper。
