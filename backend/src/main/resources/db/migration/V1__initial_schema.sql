CREATE TABLE app_users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(32) NOT NULL UNIQUE,
    display_name VARCHAR(40) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE tickets (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    number VARCHAR(32) NOT NULL UNIQUE,
    owner_id BIGINT NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    original_description TEXT NOT NULL,
    summary VARCHAR(120) NOT NULL,
    category VARCHAR(20) NOT NULL,
    priority VARCHAR(10) NOT NULL,
    device_name VARCHAR(100),
    location VARCHAR(100),
    tags_json VARCHAR(1000) NOT NULL,
    analysis_source VARCHAR(16) NOT NULL,
    status VARCHAR(20) NOT NULL,
    assigned_to BIGINT,
    resolution TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    started_at TIMESTAMP(6),
    completed_at TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ticket_owner FOREIGN KEY (owner_id) REFERENCES app_users(id),
    CONSTRAINT fk_ticket_assignee FOREIGN KEY (assigned_to) REFERENCES app_users(id),
    CONSTRAINT uq_ticket_request UNIQUE (owner_id, request_key)
);
CREATE INDEX ix_ticket_owner_created ON tickets(owner_id, created_at);
CREATE INDEX ix_ticket_status_created ON tickets(status, created_at);
CREATE INDEX ix_ticket_category ON tickets(category);
