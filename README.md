# majestic-state

## Flyway migration repair

If you see a Flyway validation error for a failed migration (for example, migration 4 for the admin primary flag), clean up any partial schema changes and run Flyway repair against the database before restarting the app. This clears the failed entry in `flyway_schema_history` so the updated migration can be applied cleanly.
