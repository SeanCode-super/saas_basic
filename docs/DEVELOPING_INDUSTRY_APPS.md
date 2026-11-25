# 行业应用接入

## 从已有项目生成管理应用

在 PC 端进入「代码生成中心 → AI 智能应用工坊 → 从已有项目生成」。拥有代码生成写权限的管理员可以选择本机项目目录。浏览器提取接口、类型、业务方法、文档标题等声明，可先点击「核对发送内容」。模型使用天枢现有 AI 网关和计量配置。索引达到容量上限时，页面提示遗漏数量，可按业务子目录继续梳理。

1. 选择模型并进行分析，或导入之前确认过的 `project-design.json`。蓝图列明角色与数据范围、跨模块流程、页面、接口证据和待确认事项。预览前可修改蓝图。每个可执行路由必须与所选目录的源文件、HTTP 方法、路径一致。
2. 确认后预览或下载源码，包括原生多页清单、PC 和手机 Vue 页面、API 客户端、独立业务适配服务、接口测试和增量更新脚本。矩阵发可使用仓库内的[五页示例蓝图](../examples/matrix-push/project-design.json)验收。
3. 在天枢内交付时，平台管理员点击 **生成到天枢**，选择目标租户，再点 **生成并开通**。平台在同一事务中保存经过源码核对的业务蓝图和页面清单、开通租户、创建原生菜单及 API 权限，并授权该租户的内置管理员。PC 和手机工作台从已开通的修订加载页面。业务调用由天枢主服务执行，使用对应成员绑定访问原项目 API。
4. 需要单独分发源码时可使用工程预览和 ZIP 下载。使用独立签名适配服务的项目可沿用应用登记与行业网关。
5. 项目有变化时，选取更新后的目录、导入旧蓝图，再点击「AI 分析项目差异」。查看候选修订并决定是否采纳。下载新包后，`update_generated.py` 可以预览文件变更，遇到与人工编辑冲突的文件会停止。先部署兼容旧修订的服务，再升级目标租户。

**配置业务连接。** 运维设置 `saas.project-connections-directory`，每个 `<应用编码>/<修订号>/` 保存 `approved-contract.json` 和仅服务账号可读的 `connection.json`。前者是业务系统负责人确认的源码蓝图。后者配置业务 origin、身份接口和明确的双系统成员映射。接口地址、命令参数和允许读取的字段由业务方维护。工坊可以调整页面名称和可见列。首次接入其他项目需先核对其身份协议、数据范围及写入接口。


当前生成器支持可确定路径的 Spring MVC 接口、部分直接声明的 Web 路由和 JSON 格式的 OpenAPI 描述。适配器支持 JSON 列表与常用表单写入、路径和查询参数、整型 ID 数组。源系统的复杂上传、实时通信及设备执行可由已有业务代码继续承接。

## 独立服务与双端入口

已有业务系统接入可参照[矩阵发的双端只读清单与联调方案](../examples/matrix-push/README.md)，其中列出了外部租户与成员绑定、结果字段和验收条件。

「危险作业管理」示例包含[双端清单](../examples/hazard-work/manifest.json)和[可运行的业务服务](../examples/hazard-work/service.py)。平台负责身份、订阅和角色权限。行业服务独立发布，使用自己的 SQLite 数据库记录单据、审批历史和请求回执。开发机使用 Python 3 标准库即可运行。

1. 在项目根目录启动示例。首次创建密钥后保留该文件，重启继续使用原来的密钥和数据库。

   ```sh
   umask 077
   mkdir -p data/hazard-work
   test -s data/hazard-work/service.key || openssl rand -hex 32 > data/hazard-work/service.key
   HAZARD_DATABASE="$PWD/data/hazard-work/permits.sqlite" \
   HAZARD_SECRET_FILE="$PWD/data/hazard-work/service.key" python3 examples/hazard-work/service.py
   ```

2. 平台管理员在 PC 的租户应用目录点「管理行业应用 → 登记清单」，粘贴清单和服务密钥。首次发布为版本 1。也可通过 `POST /api/industry/registry` 发送 `{manifest, secret}`。平台加密存放密钥。
3. 在目标租户的应用目录开通应用。给填报人分配 `biz_hazard_work:query` 和 `biz_hazard_work:create`，给审批人分配查询、批准、驳回和关闭权限。工作人员重新进入应用中心或手机工作台，即可看到获准的入口和操作。
4. 手机提交作业申请，PC 审批人从该行进入批准或驳回，批准后可关闭。服务保存操作人的身份、时间和单据状态。示例列表显示最近 100 条；行业团队负责配置企业实际许可流程及归档规则。
5. 给现有表单加字段时，在「管理行业应用 → 给表单加字段」中选择应用、字段类型和填报表单，登记新版本。也可以在清单的 `columns` 和 `create.fields` 中增加同名字段并递增 `revision`。先升级一个租户，双端刷新后展示新字段。示例保存补充字段，老记录显示为空。新增业务动作需要行业服务实现对应处理。修正已发布的清单使用下一个版本。

**接入协议。** `NATIVE` 在 PC 和 App 的通用页面绘制清单声明的列、表单和操作。支持文本、数字和日期。`rowKey` 指定列表和操作表单共用的主键字段，点击行内动作会自动带入单据编号。两端分别访问 `GET /api/industry/workspace?terminal=pc|mobile`。目录来自本地平台库。

平台向运维配置的 `SAAS_INDUSTRY_GATEWAY` 发送 `POST /apps/{code}/actions/{action}`，默认上游为 `http://127.0.0.1:18086`。多服务部署可以在网关按应用编码路由，也可以把地址一次性配置为 `https://{app}.industry.internal`。后者将 `hazard_work` 解析到 `hazard-work.industry.internal`，新服务只需部署到对应服务名并登记清单。请求体包含应用、版本、租户、用户、操作、时间、请求编号和输入。行业服务以本应用密钥计算原始请求字节的 HMAC-SHA256，与 `X-Tianshu-Signature` 十六进制值进行恒时比较，并检查时间和目标应用。业务写入与幂等回执在同一事务内提交。示例的内置 HTTP 服务器绑定本机，正式部署可由 WSGI 服务管理器加载 `service:configured_app()`。

**第三方嵌入。** 清单使用 `kind: "EMBED"`，为启用的端分别填写独立 HTTPS 地址 `pcUrl`、`mobileUrl`，`columns` 置空，`actions` 声明无字段的 `query`。PC 用 iframe，App 用 web-view。第三方需允许门户嵌入，并独立配置登录或其已有的单点登录。原生 App 构建时将 `VITE_MOBILE_API_ORIGIN` 设为设备可访问的平台 HTTPS 地址。小程序还需设置业务域名。平台不会将用户的登录令牌附加到网址。

独立服务停机时，目录仍能从平台库读取，访问该服务的操作返回 503。业务库由行业服务单独备份；平台的租户快照覆盖平台托管的业务表。停用订阅会撤销行业接口访问，重新启用后可继续查看原有单据。

## JVM 内扩展

以课程应用为例，标准扩展 JAR 提供业务接口、数据库迁移和界面字段。PC 和手机按租户已安装的修订号读取字段并渲染列表和表单。示例使用 Java 27 和 MySQL。新建环境请使用数据库名 `saas_basics`，平台的历史 V1 迁移会显式切换到该库。

### 生成和构建

1. 在 PC 代码生成中心选「独立行业应用（按租户安装）」，填写应用编码、类名和字段，从产物页下载 ZIP。也可以用具有代码生成权限的账号调用下面的接口。仓库内有可用的[课程模型](../examples/education/course.json)。
2. 在平台仓库的 `server` 目录运行 `mvn -pl saas-basics-framework -am install`，把当前版的父 POM 和 SDK 装入本地 Maven 仓库。团队也可以从自己的 Maven 仓库分发相同版本。
3. 解压 ZIP，在解压目录运行 `mvn -f server/pom.xml package`。课程示例会得到 `server/target/education-1.0.0.jar`。
4. 部署经过审核的扩展 JAR。标准模板由双端已有的行业页面渲染。选择自定义 PC 界面的扩展需要将自己的 Vue 组件加入 Web 构建。先把兼容新旧租户修订的服务部署到各实例，再给目标租户升级。

从平台仓库根目录下载示例的命令如下。`API_ORIGIN` 为目标环境的域名或本机服务地址，`ACCESS_TOKEN` 为当前环境的登录令牌。

```sh
curl --fail-with-body -o education.zip \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" -H 'Content-Type: application/json' \
  --data-binary @examples/education/course.json "${API_ORIGIN}/api/codegen/download-zip"
```

```sh
java -Dloader.path=/opt/tianshu/extensions \
  -cp /opt/tianshu/saas-basics-app-0.1.0-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher --spring.profiles.active=db
```

## 安装和使用

进入租户的应用目录即可看到「课程」。开通应用会为当前租户登记菜单和可分配的 API 权限。在 IAM 里给业务角色分配菜单和需要的查询、创建、修改或删除权限，刷新该用户的工作台后即可使用。对需要专用界面的既有扩展，可保留自定义 PC 组件。接入手机通用页面时，通过 `TenantApplication.workspaceFields(revision)` 返回该租户已安装修订支持的字段，并实现标准 CRUD 接口。课程的 PC 路由是 `/apps/education`，业务 API 是 `/api/apps/education`。

| 操作 | HTTP 接口 |
| --- | --- |
| 查询可用版本、已安装版本、应用入口 | `GET /api/tenant/apps/catalog` |
| 为当前租户安装首版 | `POST /api/tenant/apps/subscribe`，请求体为 `{"appCode":"education","revision":1}` |
| 升到第 2 版 | `PUT /api/tenant/apps/extensions/education/revision/2` |
| 停用或重新启用 | `PUT /api/tenant/apps/authorize`，请求体为 `{"appCode":"education","enabled":false}` 或 `true` |

首次安装省略 `revision` 会选用当前可用的最新版。重复开通保留已安装版本。停用和重置套餐会关闭行业应用的访问，保留业务数据和迁移进度。

## 发布包含学分的第二版

扩展自己的 Flyway 目录为 `extensions/education/db/`，历史表为 `ext_education_history`。部署第二版时，先在该目录增加 `V2__course_credits.sql`，增加一个可空列，让仍使用第一版的租户继续写入课程。

```sql
ALTER TABLE biz_education_course ADD COLUMN credits INT NULL;
```

在生成的 `CourseApplication` 中通过构造器注入 `JdbcTemplate jdbc`，将 `latestRevision()` 改为返回 `2`。在 `migrate` 中声明第二版对该租户历史数据的处理。

```java
@Override
public void migrate(long tenantId, int revision) {
    if (revision == 2) {
        jdbc.update("""
                UPDATE biz_education_course SET credits = 3
                 WHERE tenant_id = ? AND credits IS NULL
                """, tenantId);
    }
}
```

使用学分的新接口标注 `@RequireTenantFeature(value = "education", installedRevision = 2)`，并声明自己的 `@RequirePermission`。在扩展的 `endpoints()` 里登记相应权限，在前端按已安装版本显示新操作。

共享表的结构随 JAR 部署而变化。每个租户的数据回填随该租户升级，在一个事务里按版本顺序执行。并发安装或升级会锁定目标租户，回填失败会一起回滚本次数据变更和版本记录。钩子里只做可回滚的数据库写入，并显式限定租户；建表、改表使用扩展自身的 Flyway 脚本。修正失败原因后重试原升级接口即可。已经提交的版本需要用后续迁移纠正。

[设备点检示例](../examples/maintenance/inspection.json)可以用同一接口生成第二个独立 JAR。给点检人员分配 `biz_field_ops:query` 和 `biz_field_ops:create` 后，他们可以登记和查询设备记录。

单表扩展由 PC 和手机的通用行业页面渲染。项目级多页面应用使用本章开头的代码工坊入口。

## 业务数据备份与恢复

租户台账生成的 ZIP 包含基础资料，以及当前租户在 CRM、ERP 和带 `tenant_id` 的 `biz_` 表中的数据。动态模型只读取该租户自己的业务表。基础资料供核对，审批引擎状态、文件内容和应用安装配置不参与业务写回。

“校验归档”只检查文件。“恢复业务数据”使用当前平台已登记的新版 ZIP，将其中业务表的目标租户记录恢复到备份时点。恢复要求表结构兼容和事务支持，有外键引用的表需使用专用恢复方案。任一张表写入失败会回滚整批。ZIP 展开上限为 500 MB，更大数据集应使用数据库备份。
