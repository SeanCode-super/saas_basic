ALTER TABLE iam_session ADD COLUMN refresh_token_hash VARCHAR(64) NULL AFTER access_token_hash;
ALTER TABLE iam_session ADD COLUMN refresh_expire_at DATETIME(3) NULL AFTER expire_at;
CREATE INDEX idx_iam_session_refresh_token ON iam_session (refresh_token_hash);
