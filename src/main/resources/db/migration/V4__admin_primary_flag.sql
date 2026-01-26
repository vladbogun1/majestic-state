ALTER TABLE admin_users
    ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE admin_users
SET is_primary = TRUE
WHERE id = (SELECT MIN(id) FROM admin_users);
