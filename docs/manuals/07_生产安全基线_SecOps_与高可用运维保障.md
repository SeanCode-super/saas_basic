# 07. 生产安全基线 (SecOps) 与高可用运维保障

> **所属专栏**：天枢云基 PaaS 企业级数字化底座 · 第二卷 (生产部署与运维)  
> **文档版本**：v1.0.0 (Release)  
> **阅读对象**：DevOps 工程师、安全合规人员、系统架构师  

---

## 1. 生产安全基线强化清单 (Security Hardening)

1. **核心持久层网络隔离**：
   - MySQL (3306) 与 Redis (6379) 严禁映射绑定至 `0.0.0.0` 公网 IP；
   - 必须通过云服务商 VPC 内网安全组或 Docker 容器内部 Bridge 私网互通。
2. **机密凭证动态注入**：
   - 数据库密码、Redis 密码、JWT Secret 必须通过 CI/CD 流水线（GitLab CI Variables / Jenkins Credentials / K8s Secret）在容器启动时动态下发，禁止代码仓库明文保存。
3. **CORS 跨域白名单收敛**：
   - 生产环境严禁配置 `allowedOrigins("*")`；
   - 仅放行企业合法主域名（`https://*.tianshu.cloud`）与微信小程序白名单域名。
4. **API 防暴力破解与高频限流**：
   - 登录鉴权接口基于 Redis 令牌桶 / Lua 滑动窗口限流算法，防爆破与 AI 接口租户配额按 RPM 动态拦截（返回 HTTP `429`）。
5. **可控全链路接口传输加密 (`V65`–`V66`)**：
   - 支持动态策略配置 (`sys_transport_crypto_policy`，`OFF / OPTIONAL / REQUIRED` 三档热切换与作用域规则)；
   - 基于 `X25519 + HKDF-SHA256 + AES-256-GCM` 对请求体、响应体及 Query 参数加密，结合 `Timestamp + Nonce` 防重放与密钥环轮换 (`sys_transport_crypto_key`)，可通过控制台 `/api/system/security/transport/*` 实时管控。
6. **TOTP 多因子认证与 Vault 凭据托管 (`V67`)**：
   - AI 服务商 Key、MCP 认证 Token、SSO AppSecret 及用户 TOTP MFA 密钥 (`iam_user.mfa_secret_enc`) 统一经 AES-256-GCM Vault 加密落盘并支持轮换；
   - 支持通过 `/api/auth/mfa/setup`、`/enable`、`/disable` 开启或停用双因子校验。
7. **接口遥测、告警规则与变更前后审计 Diff (`V68`–`V70`)**：
   - 持久化接口调用量、加密率、P95/P99 时延 (`sys_api_endpoint_stat`)，并支持阈值告警规则与事件确认 (`sys_alert_rule`, `sys_alert_event`)；
   - 核心写操作记录变更前后完整 JSON 快照 (`audit_operation_log.before_json`, `after_json`)，支持合规审计回溯。

---

## 2. 节点探针与健康检查

* **后端应用健康状态探针**：
  ```bash
  curl -i http://localhost:8080/actuator/health
  # 正常响应：{"status":"UP","components":{"db":{"status":"UP"},"redis":{"status":"UP"}}}
  ```
* **Nginx 网关探活**：
  ```bash
  curl -I http://localhost/healthz
  ```

---

## 3. 生产数据库备份与容灾策略

### 3.1 每日定时全量冷备脚本
建议在 Linux Crontab 中配置每日凌晨 02:00 自动执行：

```bash
#!/bin/bash
# /data/scripts/backup_mysql.sh
BACKUP_DIR="/data/backup/mysql"
DATE_STR=$(date +%Y%m%d_%H%M%S)
mkdir -p ${BACKUP_DIR}

# 导出并 gzip 压缩
docker exec saas-mysql mysqldump -u saas_app -p"${DB_PASSWORD}" saas_basics   --single-transaction --quick --default-character-set=utf8mb4 | gzip > ${BACKUP_DIR}/saas_basics_${DATE_STR}.sql.gz

# 自动清理 30 天前的旧备份
find ${BACKUP_DIR} -name "*.sql.gz" -mtime +30 -exec rm -f {} \;
```

### 3.2 MinIO / OSS 附件异地多副本
在对象存储控制台开启跨区域版本复制 (Cross-Region Replication, CRR)，确保企业电子合同与核心凭证多可用区灾备。
