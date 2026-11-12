# 08. 组织架构与企业级 IAM 权限配置指南

> **所属专栏**：天枢云基 PaaS 企业级数字化底座 · 第三卷 (业务功能与系统实战)  
> **文档版本**：v1.0.0 (Release)  
> **阅读对象**：系统管理员、组织实施顾问、企业 HR/IT 负责人  

---

## 1. 五级多组织拓扑架构

天枢云基支持现代大型企业集团与多元化子公司的管理诉求，提供标准**五级组织拓扑模型**：

```
集团总公司 (Group)
   └── 子公司 / 分公司 (Company / Enterprise)
         └── 部门体系 (Department)
               └── 岗位体系 (Post)
                     └── 员工用户 (Employee / User)
```

* **集团视角**：跨企业统筹分配配额、监控全局运营状况与审计合规；
* **企业视角**：独立的法人主体，拥有本公司专属的部门编制、岗位角色与业务单据。

---

## 2. 三位一体权限模型实战

天枢云基提供立体式细粒度鉴权矩阵，涵盖菜单、按钮与行级数据：

### 2.1 动态菜单树权限 (Menu Permission)
* 管理员可在「系统管理」->「菜单管理」中，按树状层级维护目录、菜单与外链；
* 菜单可精确绑定角色，用户登录后系统根据所属角色自动计算并渲染侧边栏菜单树。

### 2.2 按钮级操作权限 (Button Permission)
* **前端精准受控**：通过自定义指令 `v-button-permission="['crm:customer:export']"` 控制按钮显隐；无权限用户页面上完全不渲染操作按钮。
* **后端 API 双重鉴权**：在 Spring Boot Controller 方法上标注 `@PreAuthorize("@ss.hasPermi('crm:customer:export')")`，杜绝接口裸调越权。

### 2.3 行级数据范围 (Data Scope)
在角色配置中，支持针对业务单据配置 5 类标准行级数据范围：
1. **全部数据权限**：拥有该角色的用户可查看平台/租户内所有业务数据；
2. **本企业/公司数据**：跨部门可见，但限定在当前法人企业；
3. **本部门及下级数据**：部门负责人标准视图；
4. **仅本部门数据**：平行部门数据严格隔离；
5. **仅本人数据**：基层销售员/采购员只能查看自己创建或负责的单据。

---

## 3. 权限穿透透视器 (Permission Inspector)

在「权限管理」中内置**权限穿透模拟器**：
* 输入任意用户工号与操作代码（如 `erp:stock:adjust`）；
* 毫秒级输出命中规则链、决策判定结果（`PASS / DENY`）以及底层 MyBatis-Plus 自动拼装的 SQL 过滤条件，大幅降低权限排查成本。

---

## 4. 个人数字身份与云端工作台偏好 (`V74`)

PC 控制台（`ProfileView.vue`）与移动端（`mobile/src/pages/profile/profile.vue`）共同提供了企业级**个人中心与安全工作台**（后端接口前缀 `/api/iam/profile`）：
* **个人数字名片与头像闭环**：支持查看与编辑昵称、姓名、手机号、邮箱、性别 (`UNKNOWN/MALE/FEMALE/OTHER`)、个人简介 (`bio`)、办公地点 (`work_location`)、分机/扩展联系 (`ext_contact`)、语言/时区 (`locale`/`timezone`)，以及调用 `POST /api/iam/profile/avatar` 上传真实头像（按 `USER_AVATAR` 业务路由落盘并即时生效）；
* **组织任职上下文切换**：联动 `saas-basics-module-organization`，展示人员编号、组织主体、部门、岗位与主/辅任职列表，支持用户在授权任职上下文间切换（`/api/auth/context`）；
* **云端工作台偏好同步**：`PUT /api/iam/profile/preferences` 将侧边/顶部导航模式、壁纸、紧凑密度、圆角等前端布局偏好持久化至 `iam_user.preferences_json`，跨设备登录自动恢复且尊重显式本地默认配置；
* **账户安全评分与本人会话自管理**：实时呈现 MFA/手机/邮箱安全分，提供 `GET /api/iam/profile/sessions` 查看当前本人活跃设备会话，以及一键踢除其他设备 (`DELETE /api/iam/profile/sessions/others`) 或单设备下线 (`DELETE /api/iam/profile/sessions/{sessionNo}`)。
