# 03. Docker Compose 一键生产编排部署

> **所属专栏**：天枢云基 PaaS 企业级数字化底座 · 第二卷 (生产部署与运维)  
> **文档版本**：v1.0.0 (Release)  
> **阅读对象**：DevOps 运维工程师、SRE 站点可靠性工程师、系统部署人员  

---

## 1. 部署环境准备与前置要求

生产环境部署推荐使用 **Docker + Docker Compose** 容器化编排，具备环境一致性、秒级弹性扩容、配置与代码解耦等优势。

### 1.1 硬件与操作系统基准配置
* **推荐系统**：Ubuntu 22.04 LTS / Debian 12 / Rocky Linux 9 / AlmaLinux 9
* **硬件最低规格 (POC 验证环境)**：4 核 CPU / 8 GB 内存 / 50 GB SSD 存储
* **硬件生产推荐 (中小企业中高并发环境)**：8 核 CPU / 16 GB 内存 / 200 GB NVMe 高速磁盘
* **必备组件**：
  * Docker Engine >= 24.0.0
  * Docker Compose Plugin >= v2.20.0

---

## 2. 生产编排文件 (`docker-compose.prod.yml`)

在生产主机部署根目录创建 `docker-compose.prod.yml`：

```yaml
version: '3.8'

services:
  # ==========================================
  # 1. MySQL 8.3 核心生产数据库
  # ==========================================
  saas-mysql:
    image: mysql:8.3.0
    container_name: saas-mysql
    restart: always
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      - --default-time-zone=+08:00
      - --max_connections=1000
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD:-Tianshu@Prod2026!}
      MYSQL_DATABASE: saas_basics
      MYSQL_USER: saas_app
      MYSQL_PASSWORD: ${DB_PASSWORD:-SaaS@SecureApp2026!}
    ports:
      - "127.0.0.1:3306:3306" # 仅内网/本机监听，严禁公网暴露
    volumes:
      - saas-mysql-data:/var/lib/mysql
      - /etc/localtime:/etc/localtime:ro
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "127.0.0.1", "-uroot", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s
    networks:
      - saas-network

  # ==========================================
  # 2. Redis 7.2 生产缓存与会话中枢
  # ==========================================
  saas-redis:
    image: redis:7.2-alpine
    container_name: saas-redis
    restart: always
    command: ["redis-server", "--requirepass", "${REDIS_PASSWORD:-Redis@Tianshu2026!}", "--appendonly", "yes"]
    ports:
      - "127.0.0.1:6379:6379" # 仅内网/本机监听
    volumes:
      - saas-redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD:-Redis@Tianshu2026!}", "ping"]
      interval: 5s
      timeout: 3s
      retries: 10
    networks:
      - saas-network

  # ==========================================
  # 3. 后端核心服务 (Spring Boot 3 + Flowable)
  # ==========================================
  saas-backend:
    build:
      context: ./server
      dockerfile: Dockerfile
    container_name: saas-backend
    restart: always
    depends_on:
      saas-mysql:
        condition: service_healthy
      saas-redis:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: saas-mysql
      DB_PORT: 3306
      DB_NAME: saas_basics
      DB_USERNAME: saas_app
      DB_PASSWORD: ${DB_PASSWORD:-SaaS@SecureApp2026!}
      REDIS_HOST: saas-redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD:-Redis@Tianshu2026!}
      JWT_SECRET: ${JWT_SECRET:-TianshuCloudSecretKeyForTokenSigning2026Minimum256BitsLong!}
      JAVA_OPTS: "-Xms1024m -Xmx2048m -XX:+UseG1GC -Dfile.encoding=UTF-8"
    ports:
      - "127.0.0.1:8080:8080"
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://127.0.0.1:8080/actuator/health"]
      interval: 20s
      timeout: 5s
      retries: 5
      start_period: 40s
    networks:
      - saas-network

  # ==========================================
  # 4. PC 前端静态服务与内置 Nginx
  # ==========================================
  saas-frontend:
    build:
      context: ./web
      dockerfile: Dockerfile
    container_name: saas-frontend
    restart: always
    depends_on:
      - saas-backend
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/ssl:/etc/nginx/ssl:ro
      - ./nginx/nginx.prod.conf:/etc/nginx/conf.d/default.conf:ro
    networks:
      - saas-network

networks:
  saas-network:
    driver: bridge

volumes:
  saas-mysql-data:
    driver: local
  saas-redis-data:
    driver: local
```

---

## 3. 环境变量配置 (`.env.production`)

在编排文件同级目录下创建 `.env` 文件，防止敏感密钥提交至代码仓库：

```ini
# 数据库配置
DB_ROOT_PASSWORD=Tianshu@RootStrongPassword_2026!
DB_PASSWORD=Tianshu@AppSafePassword_2026!

# Redis 配置
REDIS_PASSWORD=Redis@TianshuCluster_2026!

# JWT 令牌签名秘钥 (长度建议 >= 64 位字符)
JWT_SECRET=c2Fhc19iYXNpY3NfZW50ZXJwcmlzZV9zZWNyZXRfa2V5XzIwMjZfcHJvZHVjdGlvbl9zZWN1cmVfc2lnbmluZw==
```

---

## 4. 容器生命周期管理指令

```bash
# 1. 首次拉取与后台构建启动
docker compose -f docker-compose.prod.yml up -d --build

# 2. 查看容器运行状态与探针健康指标
docker compose -f docker-compose.prod.yml ps

# 3. 追踪核心后端启动日志 (观察 Flyway 数据库自动迁移过程)
docker compose -f docker-compose.prod.yml logs -f saas-backend

# 4. 平滑重启服务
docker compose -f docker-compose.prod.yml restart saas-backend

# 5. 安全停止并清理网络
docker compose -f docker-compose.prod.yml down
```
