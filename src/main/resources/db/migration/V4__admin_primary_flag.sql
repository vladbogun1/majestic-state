ALTER TABLE admin_users
    ADD COLUMN IF NOT EXISTS is_primary BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE admin_users
SET is_primary = TRUE
WHERE id = (SELECT id FROM (SELECT MIN(id) AS id FROM admin_users) AS first_admin)
  AND NOT EXISTS (SELECT 1 FROM admin_users WHERE is_primary = TRUE);
