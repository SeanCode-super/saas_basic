# 04. 生产级 Nginx 泛域名网关与 SSL 配置

> **所属专栏**：天枢云基 PaaS 企业级数字化底座 · 第二卷 (生产部署与运维)  
> **文档版本**：v1.0.0 (Release)  
> **阅读对象**：DevOps 运维工程师、网络工程师、安全合规负责人  

---

## 1. 多租户网络路由策略

在生产环境中，天枢云基支持两种业界主流的多租户接入架构：

### 方案 A：泛域名/子域名模式（推荐，企业独立尊享感最强）
* **平台超管总控台**：`https://admin.tianshu.cloud`
* **企业 A 独立门户**：`https://tenant-a.tianshu.cloud`
* **企业 B 独立门户**：`https://tenant-b.tianshu.cloud`
* **移动端统一入口**：`https://m.tianshu.cloud` 或 `https://*.tianshu.cloud/mobile/`

> **解析逻辑**：配置域名 DNS 通配符解析 `*.tianshu.cloud` CNAME 指向 Nginx 服务器公网 IP。Nginx 通过正则从 HTTP Host 头中提取子域名（如 `tenant-a`），自动在反向代理中添加 `proxy_set_header X-Tenant-Code $tenant_code;` 传递给后端，无需前端拼接任何租户参数。

### 方案 B：统一主域名 + 路径路由模式（适合无泛域名证书的场景）
* **总控台**：`https://console.tianshu.cloud/login`
* **租户门户**：`https://console.tianshu.cloud/tenant-a/login`

---

## 2. 完整生产 Nginx 网关配置 (`nginx.prod.conf`)

```nginx
# =======================================================
# 天枢云基生产级网关配置 (支持泛域名多租户与移动端分流)
# =======================================================

# 1. HTTP 80 端口强制跳转 443 HTTPS
server {
    listen 80;
    server_name tianshu.cloud *.tianshu.cloud;
    return 301 https://$host$request_uri;
}

# 2. 生产核心 HTTPS 虚拟主机 (通配符 SSL 证书)
server {
    listen 443 ssl http2;
    server_name ~^(?<subdomain>[a-zA-Z0-9-]+)\.tianshu\.cloud$;

    # 通配符 SSL 证书 (支持 *.tianshu.cloud)
    ssl_certificate /etc/nginx/ssl/tianshu.cloud.crt;
    ssl_certificate_key /etc/nginx/ssl/tianshu.cloud.key;
    ssl_session_timeout 1d;
    ssl_session_cache shared:SSL:50m;
    ssl_session_tickets off;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384;
    ssl_prefer_server_ciphers on;

    # Gzip 静态资源极限压缩
    gzip on;
    gzip_min_length 1k;
    gzip_buffers 4 16k;
    gzip_http_version 1.1;
    gzip_comp_level 6;
    gzip_types text/plain application/javascript text/css application/xml text/javascript application/json image/svg+xml;
    gzip_vary on;

    # 单文件上传最大限制 (支持大附件/设计图)
    client_max_body_size 500m;

    # ===================================================
    # 动静分流：PC 前端控制台静态文件 (Vue 3 SPA)
    # ===================================================
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;

        # 静态资源长期缓存与防重协商
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2|woff|ttf)$ {
            expires 30d;
            add_header Cache-Control "public, no-transform";
        }
    }

    # ===================================================
    # 移动端 H5 独立路径分流 (/mobile/)
    # ===================================================
    location /mobile/ {
        alias /usr/share/nginx/mobile/;
        index index.html;
        try_files $uri $uri/ /mobile/index.html;
    }

    # ===================================================
    # 后端业务 API 反向代理 (动态注入 X-Tenant-Code)
    # ===================================================
    location /api/ {
        proxy_pass http://saas-backend:8080/api/;
        
        # 核心多租户子域名注入：若子域为 admin 则为 platform；否则注入对应企业代码
        set $tenant_code $subdomain;
        if ($subdomain = "admin") {
            set $tenant_code "platform";
        }
        proxy_set_header X-Tenant-Code $tenant_code;

        # 透传真实客户端 IP 与协议
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 代理超时调优
        proxy_connect_timeout 60s;
        proxy_read_timeout 180s;
        proxy_send_timeout 180s;

        # WebSocket 双工连接支持
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # 开放平台 Open API 路由
    location /open/api/ {
        proxy_pass http://saas-backend:8080/open/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 友好错误页
    error_page 500 502 503 504 /50x.html;
    location = /50x.html {
        root /usr/share/nginx/html;
    }
}
```
