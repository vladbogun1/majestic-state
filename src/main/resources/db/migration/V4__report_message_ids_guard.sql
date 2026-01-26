DELIMITER $$

CREATE PROCEDURE add_report_message_ids_if_missing()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'report_configs'
          AND COLUMN_NAME = 'last_message_ids'
    ) THEN
        ALTER TABLE report_configs ADD COLUMN last_message_ids TEXT;
    END IF;
END$$

CALL add_report_message_ids_if_missing()$$

DROP PROCEDURE add_report_message_ids_if_missing()$$

DELIMITER ;
