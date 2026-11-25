# 管理应用工程

`project-design.json` 保存已确认的页面、角色建议、业务流程、证据和接口映射。PC 和手机共用 `manifest.json`。平台根据订阅和权限动态渲染，工程内的 Vue 和 TypeScript 源码可在相应天枢前端工程中继续开发。

## 独立运行管理后台

独立模式由同一个进程提供登录页、管理页面和同源 API。用户用原项目的业务账号登录，浏览器只保存本服务的会话 Cookie。停止天枢后仍可访问管理后台；原项目业务服务及其数据库需要运行。

1. 复制 `standalone/config.example.json` 到私有运行目录。填写源业务服务 origin、登录和身份接口映射、角色与操作清单。角色清单初始为空。一个角色需同时获准页面的 `页面编码_list` 和具体写操作，企业级查询须核实原接口的数据范围。
2. 可在角色下设置 `rowScope`，按页面和源字段核对列表行归属。涉及实体 ID 的写操作可在 `inputScopes` 中指定对应页面、ID、名称、租户和用户字段，界面将提供归属已核对的选项。源 API 仍负责业务规则和授权。
3. 执行 `python3 -m unittest discover -s server`，再运行 `python3 server/console.py --config /绝对路径/console.json`，打开配置内的 publicOrigin。本地监听回环地址，部署到服务器时通过 HTTPS 反向代理并保留原 Host。请求回执数据库位于配置文件同目录，重启服务后用户重新登录。

## 从天枢连接已有服务

生成的适配服务使用 Python 3.9+ 标准库，通过现有业务 HTTP API 查询和提交。每个天枢用户需明确绑定到对应业务租户和成员，运行时会通过身份接口逐次核对。给适配器提供该成员自己的短期业务访问令牌，更新由已有系统的授权流程或凭据管理器负责。按源系统的实际响应填写配置，在正式调用前验证权限和数据范围。

将配置存到工程之外的私有文件，设置 PROJECT_BINDINGS_FILE 指向它。下面的名称和 ID 为部署格式示例，actions 取自本工程 manifest 的页面 query 和操作编码，rowScope 可按源数据中的归属字段限制返回行。数据范围还需在原系统的写接口执行。
```json
{
  "origin": "https://business.example.com",
  "identity": {"path": "/api/auth/info", "tenantAt": "data.tenantId", "userAt": "data.userId"},
  "codeAt": "code", "successCode": 0,
  "members": {
    "8:12": {"tenant": 7, "user": 31, "tokenEnv": "BUSINESS_USER_31_TOKEN",
             "actions": ["tasks_list"], "rowScope": {"tasks": {"user": "ownerId"}}}
  }
}
```
codeAt 和 successCode 用于返回业务码的系统；仅用 HTTP 状态码的系统可省略。列表 rowsAt 取自完整响应，列的 source 相对于每行。读接口返回原服务默认页；大文件上传、长连接和嵌套表单可在适配器中扩展。对每个写请求预留请求号以防重复提交。遇到超时或进程中断，先向源系统核对该请求的结果再处理。

设置 TIANSHU_APP_SECRET_FILE 为至少 32 位的应用密钥文件，PROJECT_RECEIPTS_DB 为持久化 SQLite 路径，按配置注入成员令牌。运行 `python3 -m unittest discover -s server`，再运行 `python3 server/service.py`。正式部署可用 WSGI 服务器加载 `service:configured_app()`，保护源服务凭据并持久化回执库。
在天枢行业网关为本应用配置 /apps/{应用编码}/actions/ 路由。登记清单时使用相同密钥。给目标租户开通应用，在 IAM 分配进入应用、可见页面和具体操作权限。部门角色及源业务的数据权限须由两边管理员核准。

## 再次生成

导入旧蓝图，可按新源码证据核对接口，修改后增加修订号。用新版工程中的 `python3 update_generated.py 上一份工程路径` 查看变化，添加 --apply 才写入。与本地修改冲突时会列出文件并停止。先部署含新旧修订的业务服务，再登记新清单并逐个升级租户。
