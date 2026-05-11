ALTER TABLE winning_numbers ADD COLUMN first_accum_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE winning_numbers ADD COLUMN raw_json LONGTEXT NULL;
ALTER TABLE winning_numbers ADD COLUMN fetched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE winning_numbers ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE winning_numbers ADD CONSTRAINT chk_wn_first_accum_nonneg CHECK (first_accum_amount >= 0);

CREATE TABLE lotto_fetch_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    drw_no        INT          NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    message       VARCHAR(500) NULL,
    response_code INT          NULL,
    raw_response  LONGTEXT     NULL,
    fetched_at    DATETIME     NOT NULL,
    CONSTRAINT pk_lotto_fetch_logs PRIMARY KEY (id),
    CONSTRAINT chk_lfl_drw_no_positive CHECK (drw_no > 0),
    CONSTRAINT chk_lfl_status CHECK (status IN ('SUCCESS', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_lotto_fetch_logs_drw_no ON lotto_fetch_logs (drw_no);
CREATE INDEX idx_lotto_fetch_logs_fetched_at ON lotto_fetch_logs (fetched_at);
