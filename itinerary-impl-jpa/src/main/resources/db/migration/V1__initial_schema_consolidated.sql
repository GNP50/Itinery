-- =============================================================================
-- V1__initial_schema_consolidated.sql
-- Consolidated Flyway migration for the Travel Itinerary Platform
--
-- Database:  itinerary_db (PostgreSQL 16 + PostGIS 3.4)
-- Author:    Travel Itinerary Platform
--
-- This migration consolidates all previous migrations (V1-V10) into a single
-- initial schema creation script.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 0. Prerequisites (PostGIS extensions - should be installed via init script)
-- ---------------------------------------------------------------------------
-- CREATE EXTENSION IF NOT EXISTS postgis;
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- 1. users – application users (REGISTERED, ANONYMOUS, ADMIN)
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id            UUID         NOT NULL DEFAULT uuid_generate_v4(),
    sub           VARCHAR(255) NOT NULL,
    email         VARCHAR(255),
    name          VARCHAR(255),
    password_hash VARCHAR(255),
    user_type     VARCHAR(20)  NOT NULL DEFAULT 'ANONYMOUS',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_users     PRIMARY KEY (id),
    CONSTRAINT uq_users_sub UNIQUE (sub)
);

CREATE INDEX idx_users_email ON users (email) WHERE email IS NOT NULL;
CREATE INDEX idx_users_sub   ON users (sub);

-- Seed system anonymous user
INSERT INTO users (id, sub, user_type)
VALUES ('00000000-0000-0000-0000-000000000001', 'system_anonymous', 'ANONYMOUS')
ON CONFLICT (sub) DO NOTHING;

-- Seed admin user: admin@admin.com / adminadmin
INSERT INTO users (id, sub, email, name, password_hash, user_type, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    'auth0|admin',
    'admin@admin.com',
    'System Admin',
    '$2y$12$.7JcNf3FyS3zs42fnOTNmuJplyh7U7n1QzvSjvH7n/mE0cFXsUG3O',  -- "adminadmin"
    'ADMIN',
    NOW()
)
ON CONFLICT (sub) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. itineraries – aggregate root
-- ---------------------------------------------------------------------------

CREATE TABLE itineraries (
    id                  UUID        NOT NULL DEFAULT uuid_generate_v4(),
    title               VARCHAR(255) NOT NULL,
    description         TEXT,

    -- User tracking (replaces V1 owner_user_id)
    user_id             UUID        NOT NULL REFERENCES users (id) ON DELETE SET NULL,

    -- Legacy V1 column (nullable, not mapped by entity)
    owner_user_id       UUID,
    organisation_id     UUID,

    -- Status lifecycle (VARCHAR not ENUM for JPA compatibility)
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    -- Token-based sharing
    access_token        VARCHAR(64),

    -- Queue management
    queue_position      INT,
    estimated_completion TIMESTAMPTZ,

    -- Travel preferences
    travel_mode         VARCHAR(20),
    preferences         JSONB,

    -- AI enrichment
    ai_suggestions      JSONB,

    -- Computed metrics
    total_distance_km      NUMERIC(12, 3),
    total_duration_minutes INT,
    current_step_index     INT NOT NULL DEFAULT 0,

    -- Travel window (nullable - not always populated by entity)
    planned_start_date  DATE,
    planned_end_date    DATE,
    actual_start_date   DATE,
    actual_end_date     DATE,

    -- Destination
    destination_country VARCHAR(2),
    destination_city    VARCHAR(100),
    destination_point   GEOMETRY(Point, 4326),

    -- Budget
    estimated_budget_amount   BIGINT,
    estimated_budget_currency VARCHAR(3),
    actual_cost_amount        BIGINT,
    actual_cost_currency      VARCHAR(3),

    -- Participants
    traveller_count     SMALLINT    NOT NULL DEFAULT 1,

    -- Tags
    tags                TEXT[]      NOT NULL DEFAULT '{}',

    -- Optimistic locking (INT not BIGINT for JPA compatibility)
    version             INT         NOT NULL DEFAULT 0,

    -- Audit
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),

    -- Soft delete
    deleted_at          TIMESTAMPTZ,

    CONSTRAINT pk_itineraries PRIMARY KEY (id),
    CONSTRAINT chk_itineraries_travellers CHECK (traveller_count > 0)
);

COMMENT ON TABLE  itineraries IS 'Itinerary aggregate roots';
COMMENT ON COLUMN itineraries.destination_point IS 'PostGIS Point (SRID 4326) for the primary destination';

-- Indexes on itineraries
CREATE INDEX idx_itineraries_user_id       ON itineraries (user_id);
CREATE INDEX idx_itineraries_owner         ON itineraries (owner_user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_itineraries_status        ON itineraries (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_itineraries_dates         ON itineraries (planned_start_date, planned_end_date);
CREATE INDEX idx_itineraries_organisation  ON itineraries (organisation_id) WHERE organisation_id IS NOT NULL;
CREATE INDEX idx_itineraries_tags          ON itineraries USING GIN (tags);
CREATE INDEX idx_itineraries_destination   ON itineraries USING GIST (destination_point) WHERE destination_point IS NOT NULL;
CREATE INDEX idx_itineraries_updated_at    ON itineraries (updated_at DESC);
CREATE UNIQUE INDEX idx_itineraries_access_token ON itineraries (access_token) WHERE access_token IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 3. itinerary_steps – ordered steps within an itinerary
-- ---------------------------------------------------------------------------

CREATE TABLE itinerary_steps (
    id              UUID        NOT NULL DEFAULT uuid_generate_v4(),
    itinerary_id    UUID        NOT NULL,

    -- Step ordering (nullable - entity uses step_order instead)
    sequence        SMALLINT,
    step_order      INT,

    -- Legacy V1 columns (nullable - entity uses place_name instead)
    title           VARCHAR(255),
    description     TEXT,

    -- Entity-mapped location fields
    place_name      VARCHAR(255),
    city            VARCHAR(255),
    province        VARCHAR(255),
    region          VARCHAR(255),
    country         VARCHAR(255),
    country_code    VARCHAR(2),
    latitude        NUMERIC(10, 7),
    longitude       NUMERIC(10, 7),
    osm_id          BIGINT,

    -- Status (VARCHAR not ENUM for JPA compatibility)
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- Legacy V1 location fields
    location_name   VARCHAR(255),
    location_address TEXT,
    location_geom   GEOMETRY(Geometry, 4326),
    altitude_meters DOUBLE PRECISION,

    -- AI enrichment
    ai_description  TEXT,
    ai_tips         TEXT,

    -- Route metrics
    distance_from_prev_km    NUMERIC(10, 3),
    duration_from_prev_min   INT,
    route_geometry_from_prev JSONB,
    poi_nearby               JSONB,

    -- Temporal
    arrival_date    DATE,
    scheduled_start TIMESTAMPTZ,
    scheduled_end   TIMESTAMPTZ,
    actual_start    TIMESTAMPTZ,
    actual_end      TIMESTAMPTZ,
    delay_minutes   INT         NOT NULL DEFAULT 0,

    -- Cost
    cost_amount     BIGINT,
    cost_currency   VARCHAR(3),

    -- External references
    external_ref    VARCHAR(255),
    provider        VARCHAR(100),

    -- JSON payload
    details_json    JSONB       NOT NULL DEFAULT '{}',

    -- Notes and media
    notes           TEXT,
    media_urls      TEXT[]      NOT NULL DEFAULT '{}',

    -- Optimistic locking (INT not BIGINT for JPA compatibility)
    version         INT         NOT NULL DEFAULT 0,

    -- Audit
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_itinerary_steps PRIMARY KEY (id),
    CONSTRAINT fk_steps_itinerary FOREIGN KEY (itinerary_id)
        REFERENCES itineraries (id) ON DELETE CASCADE
);

COMMENT ON TABLE  itinerary_steps IS 'Individual steps (legs) of an itinerary';
COMMENT ON COLUMN itinerary_steps.location_geom IS 'PostGIS Geometry (SRID 4326): Point for a single location, LineString for a route segment';

-- Indexes on itinerary_steps
CREATE INDEX idx_steps_itinerary       ON itinerary_steps (itinerary_id, sequence);
CREATE INDEX idx_steps_status          ON itinerary_steps (status);
CREATE INDEX idx_steps_scheduled       ON itinerary_steps (scheduled_start, scheduled_end);
CREATE INDEX idx_steps_geom            ON itinerary_steps USING GIST (location_geom) WHERE location_geom IS NOT NULL;
CREATE INDEX idx_steps_details_gin     ON itinerary_steps USING GIN (details_json);
CREATE INDEX idx_steps_media_gin       ON itinerary_steps USING GIN (media_urls);
CREATE INDEX idx_steps_external_ref    ON itinerary_steps (external_ref) WHERE external_ref IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 4. itinerary_events – immutable domain event log
-- ---------------------------------------------------------------------------

CREATE TABLE itinerary_events (
    id              UUID        NOT NULL DEFAULT uuid_generate_v4(),
    itinerary_id    UUID        NOT NULL,

    -- Event identity & ordering
    event_type      VARCHAR(100) NOT NULL,
    event_version   VARCHAR(10)  NOT NULL DEFAULT '1.0.0',
    sequence_number BIGINT,  -- nullable for entity-driven inserts

    -- Correlation / causation
    correlation_id  UUID,
    causation_id    UUID,

    -- Payload (V1 used 'payload', entity maps 'details')
    payload         JSONB,
    details         JSONB,

    -- Metadata
    source_service  VARCHAR(100),
    actor_id        UUID,

    -- Timestamps (V1 used 'occurred_at', entity maps 'created_at')
    occurred_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT pk_itinerary_events PRIMARY KEY (id),
    CONSTRAINT fk_events_itinerary FOREIGN KEY (itinerary_id)
        REFERENCES itineraries (id) ON DELETE CASCADE
);

COMMENT ON TABLE itinerary_events IS 'Immutable domain event log for itinerary aggregates';

-- Indexes on itinerary_events
CREATE INDEX idx_events_itinerary      ON itinerary_events (itinerary_id, sequence_number);
CREATE INDEX idx_events_type           ON itinerary_events (event_type);
CREATE INDEX idx_events_occurred_at    ON itinerary_events (occurred_at DESC);
CREATE INDEX idx_events_correlation    ON itinerary_events (correlation_id) WHERE correlation_id IS NOT NULL;
CREATE INDEX idx_events_payload_gin    ON itinerary_events USING GIN (payload);

-- ---------------------------------------------------------------------------
-- 5. outbox_events – transactional outbox for Kafka publishing
-- ---------------------------------------------------------------------------

CREATE TABLE outbox_events (
    id              UUID        NOT NULL DEFAULT uuid_generate_v4(),

    -- Kafka routing (topic nullable - derived at publish time)
    topic           VARCHAR(255),
    partition_key   VARCHAR(255),

    -- Event classification
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID        NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    event_version   VARCHAR(10)  NOT NULL DEFAULT '1.0.0',

    -- Serialised payload (TEXT not JSONB for JPA compatibility)
    payload         TEXT        NOT NULL,

    -- Kafka headers
    headers         JSONB       NOT NULL DEFAULT '{}',

    -- Delivery lifecycle
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    published       BOOLEAN     NOT NULL DEFAULT false,
    retry_count     SMALLINT    NOT NULL DEFAULT 0,
    max_retries     SMALLINT    NOT NULL DEFAULT 5,
    last_error      TEXT,

    -- Correlation ID for tracing
    correlation_id  VARCHAR(64),

    -- Timestamps
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    scheduled_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    failed_at       TIMESTAMPTZ,

    CONSTRAINT pk_outbox_events   PRIMARY KEY (id),
    CONSTRAINT chk_outbox_retries CHECK (retry_count >= 0 AND max_retries >= 0)
);

COMMENT ON TABLE outbox_events IS 'Transactional outbox table for reliable Kafka event publishing';

-- Indexes on outbox_events
CREATE INDEX idx_outbox_pending       ON outbox_events (scheduled_at ASC) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_unpublished   ON outbox_events (published, created_at) WHERE published = false;
CREATE INDEX idx_outbox_aggregate     ON outbox_events (aggregate_type, aggregate_id);
CREATE INDEX idx_outbox_topic         ON outbox_events (topic);
CREATE INDEX idx_outbox_status        ON outbox_events (status);
CREATE INDEX idx_outbox_created_at    ON outbox_events (created_at DESC);

-- ---------------------------------------------------------------------------
-- 6. Triggers – maintain updated_at automatically
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql AS
$$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_itineraries_updated_at
    BEFORE UPDATE ON itineraries
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_itinerary_steps_updated_at
    BEFORE UPDATE ON itinerary_steps
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- ---------------------------------------------------------------------------
-- Done
-- ---------------------------------------------------------------------------
