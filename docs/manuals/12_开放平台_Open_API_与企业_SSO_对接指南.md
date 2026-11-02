# 12. 开放平台 Open API 与企业 SSO 对接指南

> **所属专栏**：天枢云基 PaaS 企业级数字化底座 · 第四卷 (开放集成与二次开发)  
> **文档版本**：v1.0.0 (Release)  
> **阅读对象**：第三方系统对接工程师、企业系统集成架构师、API 开发者  

---

## 1. 开放平台 Open API 鉴权机制

为了保障企业租户与第三方外部系统（如金蝶/用友财务系统、电商中台、自研 OA）的数据互通，天枢云基提供高安全等级的 Open API 开放网关：

### 1.1 凭证分发
在「开放平台」->「应用管理」中为每个外部系统颁发独立凭证：
* `AppKey`：应用唯一公钥标识；
* `AppSecret`：应用私钥（由调用方安全妥善保存，禁止外泄）。

### 1.2 签名算法 (HMAC-SHA256)
每次向开放接口发起请求时，必须在 HTTP Header 中携带签名参数：
1. `X-App-Key`: 颁发的 AppKey；
2. `X-Timestamp`: 当前 Unix 毫秒时间戳（服务器校验时间窗口偏差不得超过 5 分钟，防止时间漂移攻击）；
3. `X-Nonce`: 16 位以上随机字符串（结合 Redis 缓存实现防重放攻击）；
4. `X-Signature`: 签名散列值。

**签名计算示例 (Python 伪代码)**：
```python
import hmac
import hashlib

string_to_sign = f"appKey={app_key}&nonce={nonce}&timestamp={timestamp}&body={request_body}"
signature = hmac.new(app_secret.encode('utf-8'), string_to_sign.encode('utf-8'), hashlib.sha256).hexdigest()
```

---

## 2. Webhook 事件总线订阅

外部系统可在平台中注册 Webhook 回调地址，订阅感兴趣的业务事件：
* `crm.customer.created` (新客户创建)
* `workflow.task.completed` (审批单终审通过)
* `erp.stock.warning` (物料库存触底告警)

> **可靠性保证**：平台内置指数退避重试机制（默认重试 3 次：1s、10s、60s）。若外部服务连续响应超时，自动发出运维告警并暂停该 Webhook 订阅。

---

## 3. 企业 SSO 统一联合认证 (SAML 2.0 / OIDC)

天枢云基支持标准企业身份联合认证体系：
1. **企业微信 / 钉钉 / 飞书扫码登录**：在后台填写企业 Corpid 与 AppSecret，即可一键打通移动端免密授权与组织通讯录同步；
2. **标准 SAML 2.0 / OIDC (OpenID Connect)**：支持与 Okta、Microsoft Azure AD、Keycloak 及自建 CAS 进行联合单点登录 (SSO)。
