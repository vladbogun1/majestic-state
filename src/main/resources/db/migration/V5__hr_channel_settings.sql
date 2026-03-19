CREATE TABLE hr_channel_settings (
    guild_id VARCHAR(32) PRIMARY KEY,
    hire_channel_id VARCHAR(32),
    fire_channel_id VARCHAR(32),
    promote_channel_id VARCHAR(32),
    demote_channel_id VARCHAR(32),
    promotion_request_channel_id VARCHAR(32),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
