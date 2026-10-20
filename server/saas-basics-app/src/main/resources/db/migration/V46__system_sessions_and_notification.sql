-- V46__system_sessions_and_notification.sql
-- 1. 注册 "在线会话" 菜单
INSERT IGNORE INTO iam_menu (
    tenant_id, parent_id, menu_type, menu_code, menu_name,
    route_path, component_path, component_name, permission_code,
    icon, visible, sort_no, status, remark
) VALUES (
    0, 8, 'MENU', 'system_sessions', '在线会话',
    '/system/sessions', '@/views/system/SystemView.vue', 'SystemSessionsView', 'system:config:query',
    'Monitor', 1, 6, 'ENABLED', '实时在线会话监控与强退治理'
);

-- 注册强退会话按钮权限
INSERT IGNORE INTO iam_menu (
    tenant_id, parent_id, menu_type, menu_code, menu_name,
    permission_code, visible, sort_no, status, remark
)
SELECT 0, id, 'BUTTON', 'system_sessions:kickout', '强制下线',
       'system:session:kickout', 1, 1, 'ENABLED', '强制下线在线会话凭证'
FROM iam_menu WHERE menu_code = 'system_sessions' LIMIT 1;

-- 授权给平台超级管理员角色 (role_id = 1)
INSERT IGNORE INTO iam_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, id FROM iam_menu WHERE menu_code IN ('system_sessions', 'system_sessions:kickout');

-- 2. 系统消息通知模版表
CREATE TABLE IF NOT EXISTS system_notify_template (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    template_code VARCHAR(64) NOT NULL COMMENT '模版编码',
    template_name VARCHAR(128) NOT NULL COMMENT '模版名称',
    template_type VARCHAR(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '通知分类: SYSTEM/WORKFLOW/AUDIT',
    title_template VARCHAR(255) NOT NULL COMMENT '标题模版',
    content_template TEXT NOT NULL COMMENT '内容模版',
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED/DISABLED',
    remark VARCHAR(500) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_tpl_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统消息通知模版表';

-- 3. 用户站内信与通知消息表
CREATE TABLE IF NOT EXISTS system_notify_message (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '接收用户ID',
    category VARCHAR(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '通知分类: WORKFLOW/AUDIT/SYSTEM',
    title VARCHAR(255) NOT NULL COMMENT '通知标题',
    content TEXT NOT NULL COMMENT '通知正文详情',
    read_status TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读: 0未读, 1已读',
    read_at DATETIME(3) DEFAULT NULL COMMENT '阅读时间',
    biz_type VARCHAR(64) DEFAULT NULL COMMENT '业务类型(如WORKFLOW_TASK/SECURITY_ALERT)',
    biz_id VARCHAR(64) DEFAULT NULL COMMENT '业务唯一标识或单号',
    ext_json JSON DEFAULT NULL COMMENT '扩展透传参数',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_user_read (tenant_id, user_id, read_status),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户站内信与通知消息表';

-- 预置标准消息模版
INSERT IGNORE INTO system_notify_template (template_code, template_name, template_type, title_template, content_template) VALUES
('WORKFLOW_TASK_ASSIGNED', '待办审批提醒', 'WORKFLOW', '【待办审批】您有新的审批任务待处理', '流程单号【{instanceNo}】发起人【{initiator}】提交了【{defName}】，请进入审批工作台处理。'),
('WORKFLOW_TASK_FINISHED', '审批办结通知', 'WORKFLOW', '【办结通知】您的审批申请已完成', '您发起的流程单号【{instanceNo}】已完成流转，最终审批结果为【{result}】。'),
('SECURITY_ALERT', '安全审计告警', 'AUDIT', '【安全告警】检测到安全防御异常事件', '检测到来自 IP【{ip}】的【{action}】异常访问尝试，系统已触发双令牌安全防御。'),
('SYSTEM_BROADCAST', '系统全员广播', 'SYSTEM', '【系统公告】平台自治架构升级发布', 'SaaS基础底座已上线独立子系统与实时消息推送引擎，业务流转更加流畅安全。');

-- 为平台管理员 (user_id = 1, tenant_id = 1) 初始化直观的未读通知消息
INSERT INTO system_notify_message (tenant_id, user_id, category, title, content, read_status, biz_type, biz_id, created_at) VALUES
(1, 1, 'WORKFLOW', '【待办审批】费用报销申请等待您审批', '流程单号【WF-20261018-0001】发起人【张三 (研发部)】提交了【差旅报销申请，金额：¥3,650.00】，请及时审核。', 0, 'WORKFLOW_TASK', '1', DATE_SUB(NOW(), INTERVAL 5 MINUTE)),
(1, 1, 'AUDIT', '【安全告警】管理员特权账号异地登录审计', '检测到来自客户端【macOS / Google Chrome】的特权登录操作，双令牌链路与会话管理已实时生效并受控。', 0, 'SECURITY_ALERT', 'SESS-ONLINE', DATE_SUB(NOW(), INTERVAL 15 MINUTE)),
(1, 1, 'SYSTEM', '【系统公告】SaaS 基础底座独立自治子系统上线', '工作流微引擎已支持独立集群部署与热重启容灾（Fault Isolation），核心平台 100% 不受影响。', 0, 'SYSTEM_BROADCAST', 'NOTICE-01', DATE_SUB(NOW(), INTERVAL 1 HOUR));
