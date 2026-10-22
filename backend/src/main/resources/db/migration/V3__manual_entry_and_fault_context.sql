-- Manual entry has no separate raw narrative. Existing descriptions remain intact.
ALTER TABLE tickets MODIFY COLUMN original_description TEXT NULL;
ALTER TABLE tickets ADD COLUMN impact_scope VARCHAR(100) NULL;
ALTER TABLE tickets ADD COLUMN fault_started_at VARCHAR(100) NULL;
ALTER TABLE tickets ADD COLUMN error_message VARCHAR(500) NULL;
ALTER TABLE tickets ADD COLUMN attempted_actions VARCHAR(1000) NULL;
