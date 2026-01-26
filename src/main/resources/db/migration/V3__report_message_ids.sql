ALTER TABLE report_configs
    ADD COLUMN IF NOT EXISTS last_message_ids TEXT;
