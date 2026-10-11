-- Flyway callback: runs before every migration on the same connection.
-- B6 is a pg_dump file and clears search_path for the session, so later
-- migrations that use unqualified table names (V7, V8) would fail on a new
-- database. Reset it to the application schema before each migration.
SELECT pg_catalog.set_config('search_path', '${flyway:defaultSchema}', false);
