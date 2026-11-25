CREATE TABLE plat_industry_service (
    app_code VARCHAR(40) NOT NULL,
    revision INT NOT NULL,
    manifest_json JSON NOT NULL,
    credential_cipher TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (app_code, revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
