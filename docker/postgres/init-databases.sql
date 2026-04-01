-- =============================================================================
-- Travel Itinerary Platform – PostgreSQL initialisation script
-- =============================================================================
-- Executed once by the postgres/postgis container on first startup.
-- The primary database (itinerary_db) is already created by the
-- POSTGRES_DB environment variable; this script creates the remaining
-- databases and installs the required extensions in all of them.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Helper: run DDL only when connected to the right database.
-- We connect to each database via \connect so that extensions are installed
-- in the correct catalog.
-- ---------------------------------------------------------------------------

-- ──────────────────────────────────────────────────────────────────────────────
-- 1. itinerary_db  (already created by docker-entrypoint; just add extensions)
-- ──────────────────────────────────────────────────────────────────────────────
\connect itinerary_db

-- Enable PostGIS (geometry types, spatial indexes, functions)
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Full-text search helpers
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Crypto helpers (used for token generation)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Ensure the main user has full privileges on the public schema (PostgreSQL 15+)
GRANT ALL ON SCHEMA public TO travel;

-- Create application role with limited privileges
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'travel_app') THEN
        CREATE ROLE travel_app WITH LOGIN PASSWORD 'travel_app_secret';
    END IF;
END
$$;

GRANT CONNECT ON DATABASE itinerary_db TO travel_app;
GRANT USAGE, CREATE ON SCHEMA public TO travel_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO travel_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO travel_app;

-- ──────────────────────────────────────────────────────────────────────────────
-- 2. config_db  (stores distributed configuration managed by config-manager)
-- ──────────────────────────────────────────────────────────────────────────────
\connect postgres

CREATE DATABASE config_db
    WITH
    OWNER       = travel
    ENCODING    = 'UTF8'
    LC_COLLATE  = 'en_US.utf8'
    LC_CTYPE    = 'en_US.utf8'
    TEMPLATE    = template0;

COMMENT ON DATABASE config_db IS
    'Distributed configuration store used by the config-manager service';

\connect config_db

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS hstore;     -- key-value pairs for flexible config

-- Ensure the owner has full privileges on the public schema (PostgreSQL 15+)
GRANT ALL ON SCHEMA public TO travel;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'travel_app') THEN
        CREATE ROLE travel_app WITH LOGIN PASSWORD 'travel_app_secret';
    END IF;
END
$$;

GRANT CONNECT ON DATABASE config_db TO travel_app;
GRANT USAGE, CREATE ON SCHEMA public TO travel_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO travel_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO travel_app;

-- ──────────────────────────────────────────────────────────────────────────────
-- 3. saga_db  (stores SAGA orchestration state for event-saga-orchestrator)
-- ──────────────────────────────────────────────────────────────────────────────
\connect postgres

CREATE DATABASE saga_db
    WITH
    OWNER       = travel
    ENCODING    = 'UTF8'
    LC_COLLATE  = 'en_US.utf8'
    LC_CTYPE    = 'en_US.utf8'
    TEMPLATE    = template0;

COMMENT ON DATABASE saga_db IS
    'SAGA orchestration state store used by event-saga-orchestrator';

\connect saga_db

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Ensure the owner has full privileges on the public schema (PostgreSQL 15+)
GRANT ALL ON SCHEMA public TO travel;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'travel_app') THEN
        CREATE ROLE travel_app WITH LOGIN PASSWORD 'travel_app_secret';
    END IF;
END
$$;

GRANT CONNECT ON DATABASE saga_db TO travel_app;
GRANT USAGE, CREATE ON SCHEMA public TO travel_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO travel_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO travel_app;

-- ──────────────────────────────────────────────────────────────────────────────
-- Done
-- ──────────────────────────────────────────────────────────────────────────────
-- Note: Schema is created by 02-init-itinerary-schema.sql (executed automatically)
-- ──────────────────────────────────────────────────────────────────────────────
\connect postgres
SELECT datname, pg_encoding_to_char(encoding) AS encoding
FROM   pg_database
WHERE  datname IN ('itinerary_db', 'config_db', 'saga_db')
ORDER  BY datname;
