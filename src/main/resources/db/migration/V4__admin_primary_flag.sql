ALTER TABLE admin_users
    ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE admin_users AS target
JOIN (
    SELECT MIN(id) AS id
    FROM admin_users
    WHERE NOT EXISTS (SELECT 1 FROM admin_users WHERE is_primary = TRUE)
) AS first_admin
    ON target.id = first_admin.id
SET target.is_primary = TRUE;
