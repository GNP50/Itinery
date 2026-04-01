-- =============================================================================
-- V1__create_config_entries.sql
-- Initial schema for the Config Manager centralised configuration store.
-- =============================================================================

CREATE TABLE config_entries (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id   VARCHAR(100) NOT NULL,
    namespace    VARCHAR(100) NOT NULL DEFAULT 'default',
    config_key   VARCHAR(255) NOT NULL,
    config_value TEXT,
    version      BIGINT       NOT NULL DEFAULT 0,
    encrypted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_config_entry UNIQUE (service_id, namespace, config_key)
);

-- Composite index covering the most common lookup pattern (service + namespace)
CREATE INDEX idx_config_service_ns ON config_entries (service_id, namespace);

-- =============================================================================
-- Trigger: keep updated_at current on every row update
-- =============================================================================

CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER
    LANGUAGE plpgsql AS
$$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_config_entries_updated_at
    BEFORE UPDATE ON config_entries
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
