CREATE TABLE analysis_previews (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    original_description TEXT NOT NULL,
    result_json TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    ticket_id BIGINT UNIQUE,
    CONSTRAINT fk_analysis_owner FOREIGN KEY (owner_id) REFERENCES app_users(id),
    CONSTRAINT fk_analysis_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id)
);
