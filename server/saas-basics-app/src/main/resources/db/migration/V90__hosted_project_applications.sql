ALTER TABLE plat_industry_service ADD COLUMN project_design_json JSON NULL;

CREATE TABLE cg_project_action_receipt (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    app_code VARCHAR(40) NOT NULL,
    request_id CHAR(36) NOT NULL,
    input_hash CHAR(64) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3) NULL,
    UNIQUE KEY uk_project_request (tenant_id,user_id,app_code,request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
