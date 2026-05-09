-- Create the Keycloak database automatically when the local Postgres data
-- directory is initialized for the first time.
--
-- This runs only on a fresh postgres data directory via
-- /docker-entrypoint-initdb.d.

SELECT 'CREATE DATABASE keycloak OWNER clinic'
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_database
    WHERE datname = 'keycloak'
) \gexec
