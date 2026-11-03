-- ===================================================================
-- V62: 天枢云基 · 应用生命周期与 DevOps 服务编排中台
-- 包含：服务资产表、自动化构建记录表、制品版本归档表、预置核心服务、菜单与权限赋权
-- ===================================================================

CREATE TABLE IF NOT EXISTS `sys_app_service` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `service_code` VARCHAR(64) NOT NULL COMMENT '服务唯一标识代码',
  `service_name` VARCHAR(128) NOT NULL COMMENT '服务名称',
  `service_type` VARCHAR(32) NOT NULL DEFAULT 'JAR' COMMENT '服务类型: JAR, NODE, DOCKER, SCRIPT',
  `service_group` VARCHAR(64) NOT NULL DEFAULT 'PLATFORM' COMMENT '服务分组: PLATFORM, BUSINESS, INFRA',
  `driver_type` VARCHAR(32) NOT NULL DEFAULT 'HOST_PROCESS' COMMENT '驱动模式: HOST_PROCESS, DOCKER',
  `work_dir` VARCHAR(512) NOT NULL COMMENT '工作目录绝对路径',
  `artifact_path` VARCHAR(512) NULL COMMENT '当前部署制品相对或绝对路径',
  `start_cmd` TEXT NOT NULL COMMENT '启动命令模板',
  `stop_cmd` TEXT NULL COMMENT '停止命令（为空时按 PID 优雅停机）',
  `port` INT NULL COMMENT '监听端口号',
  `health_url` VARCHAR(256) NULL COMMENT '健康探测地址',
  `log_path` VARCHAR(512) NULL COMMENT '日志输出绝对路径',
  `build_cmd` TEXT NULL COMMENT '自动编译命令',
  `auto_restart` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '崩溃自愈自动重启: 0否, 1是',
  `status` VARCHAR(32) NOT NULL DEFAULT 'STOPPED' COMMENT '运行状态: STOPPED, RUNNING, COMPILING, STARTING, ERROR',
  `current_pid` INT NULL COMMENT '当前操作系统 PID',
  `uptime_seconds` BIGINT NOT NULL DEFAULT 0 COMMENT '本次运行累计时长秒',
  `last_started_at` DATETIME NULL COMMENT '最近启动时间',
  `last_stopped_at` DATETIME NULL COMMENT '最近停止时间',
  `last_exit_code` INT NULL COMMENT '最后退出码',
  `last_error_msg` TEXT NULL COMMENT '最后报错信息',
  `remark` VARCHAR(512) NULL COMMENT '备注说明',
  `sort_no` INT NOT NULL DEFAULT 100 COMMENT '排序号',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_app_service_code` (`service_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='DevOps 服务资产纳管表';

CREATE TABLE IF NOT EXISTS `sys_app_build_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `service_id` BIGINT NOT NULL COMMENT '关联服务ID',
  `service_code` VARCHAR(64) NOT NULL COMMENT '服务标识代码',
  `build_no` VARCHAR(64) NOT NULL COMMENT '构建序号，如 B20261103-001',
  `build_cmd` TEXT NOT NULL COMMENT '实际执行编译命令',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING, RUNNING, SUCCESS, FAILED, TIMEOUT',
  `trigger_type` VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '触发类型: MANUAL, WEBHOOK, SCHEDULE',
  `artifact_generated` VARCHAR(512) NULL COMMENT '产出制品路径',
  `artifact_size_bytes` BIGINT NULL COMMENT '制品字节大小',
  `start_time` DATETIME NULL COMMENT '开始时间',
  `end_time` DATETIME NULL COMMENT '结束时间',
  `duration_ms` BIGINT NULL COMMENT '构建耗时毫秒',
  `log_excerpt` MEDIUMTEXT NULL COMMENT '构建日志摘要输出',
  `error_message` TEXT NULL COMMENT '失败错误信息',
  `created_by` VARCHAR(64) NOT NULL DEFAULT 'admin' COMMENT '触发操作人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_app_build_service_id` (`service_id`),
  KEY `idx_app_build_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='DevOps 自动化构建记录表';

CREATE TABLE IF NOT EXISTS `sys_app_artifact` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `service_id` BIGINT NOT NULL COMMENT '关联服务ID',
  `service_code` VARCHAR(64) NOT NULL COMMENT '服务标识代码',
  `version_tag` VARCHAR(64) NOT NULL COMMENT '版本快照标识，如 v0.1.0-20261103-1430',
  `file_name` VARCHAR(256) NOT NULL COMMENT '制品原始文件名',
  `file_path` VARCHAR(512) NOT NULL COMMENT '备份制品绝对存储路径',
  `file_size_bytes` BIGINT NOT NULL DEFAULT 0 COMMENT '制品大小字节数',
  `file_md5` VARCHAR(64) NOT NULL COMMENT '文件 MD5 校验码',
  `is_current` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为当前版本: 0否, 1是',
  `remark` VARCHAR(256) NULL COMMENT '发布备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_app_artifact_service` (`service_id`, `version_tag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='DevOps 制品版本归档库';

-- 预置开箱即用的 4 大核心服务
INSERT INTO `sys_app_service` (
  `service_code`, `service_name`, `service_type`, `service_group`, `driver_type`,
  `work_dir`, `artifact_path`, `start_cmd`, `stop_cmd`, `port`,
  `health_url`, `log_path`, `build_cmd`, `sort_no`, `remark`
) VALUES
(
  'backend-main',
  '天枢云基 · 核心业务主服务 (saas-basics-app)',
  'JAR',
  'PLATFORM',
  'HOST_PROCESS',
  '/Users/shenzhidan/ideaProject/saas_basics/server',
  'saas-basics-app/target/saas-basics-app-0.1.0-SNAPSHOT.jar',
  'java -Dfile.encoding=UTF-8 -DDB_PASSWORD=19990518suo -jar saas-basics-app/target/saas-basics-app-0.1.0-SNAPSHOT.jar',
  NULL,
  8080,
  'http://127.0.0.1:8080/actuator/health',
  '/Users/shenzhidan/ideaProject/saas_basics/server/logs/saas-basics-app.log',
  'mvn clean package -DskipTests -pl saas-basics-app -am',
  10,
  'Spring Boot 3.3.5 主应用（包含多租户、IAM、CRM、ERP 等核心控制面）'
),
(
  'workflow-app',
  '天枢云基 · Flowable 流程引擎独享服务',
  'JAR',
  'PLATFORM',
  'HOST_PROCESS',
  '/Users/shenzhidan/ideaProject/saas_basics/server',
  'saas-basics-workflow-app/target/saas-basics-workflow-app-0.1.0-SNAPSHOT.jar',
  'java -Dfile.encoding=UTF-8 -DDB_PASSWORD=19990518suo -Dserver.port=8085 -jar saas-basics-workflow-app/target/saas-basics-workflow-app-0.1.0-SNAPSHOT.jar',
  NULL,
  8085,
  'http://127.0.0.1:8085/actuator/health',
  '/Users/shenzhidan/ideaProject/saas_basics/server/logs/saas-basics-workflow-app.log',
  'mvn clean package -DskipTests -pl saas-basics-workflow-app -am',
  20,
  'Flowable 7.0 BPMN 2.0 工业级工作流引擎独享微服务'
),
(
  'web-console',
  '天枢云基 · Web 晶透中台前端 (Vue 3 + Vite)',
  'NODE',
  'PLATFORM',
  'HOST_PROCESS',
  '/Users/shenzhidan/ideaProject/saas_basics/web',
  'dist',
  'npm run dev',
  NULL,
  5173,
  'http://127.0.0.1:5173',
  '/Users/shenzhidan/ideaProject/saas_basics/web/logs/web-dev.log',
  'npm run build',
  30,
  'Apple visionOS 流光毛玻璃 Web 控制台（Vue 3 + TS + Vite + Pinia）'
),
(
  'mobile-h5',
  '天枢云基 · 移动端灵动岛客户端 (UniApp)',
  'NODE',
  'PLATFORM',
  'HOST_PROCESS',
  '/Users/shenzhidan/ideaProject/saas_basics/mobile',
  'dist/dev/h5',
  'npm run dev:h5',
  NULL,
  5174,
  'http://127.0.0.1:5174',
  '/Users/shenzhidan/ideaProject/saas_basics/mobile/logs/mobile-dev.log',
  'npm run build:h5',
  40,
  'iOS 27 动态灵动岛 + 晶透 Dock 沉浸式移动终端（UniApp Vue3 TS）'
)
ON DUPLICATE KEY UPDATE
  `service_name` = VALUES(`service_name`),
  `start_cmd` = VALUES(`start_cmd`),
  `build_cmd` = VALUES(`build_cmd`),
  `port` = VALUES(`port`),
  `health_url` = VALUES(`health_url`),
  `log_path` = VALUES(`log_path`);

-- 插入 DevOps 菜单到「系统中心」(parent_id = 8)
INSERT INTO `iam_menu` (
  `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `menu_name`,
  `route_path`, `component_path`, `permission_code`, `icon`, `sort_no`,
  `status`, `visible`, `keep_alive`
)
SELECT
  1, 8, 'MENU', 'devops_service_center', '服务编排与发布中台',
  '/devops/services', '@/views/devops/AppServiceView.vue', 'system:config:query', 'SetUp', 65,
  'ENABLED', 1, 1
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_menu` WHERE `menu_code` = 'devops_service_center' AND `tenant_id` = 1 AND `deleted` = 0
);

-- 赋权给超级管理员角色 (role_id = 1)
INSERT IGNORE INTO `iam_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT 1, 1, `id` FROM `iam_menu` WHERE `menu_code` = 'devops_service_center' AND `tenant_id` = 1 AND `deleted` = 0;
