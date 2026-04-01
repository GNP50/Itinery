-- =============================================================================
-- Travel Itinerary Platform – Complete itinerary_db schema
-- =============================================================================
-- This script creates the complete schema for itinerary_db in a single pass.
-- It consolidates all Flyway migrations (V1-V9) into one unified DDL script.
--
-- Database:  itinerary_db (PostgreSQL 16 + PostGIS 3.4)
-- =============================================================================

\connect itinerary_db

-- ---------------------------------------------------------------------------
-- 1. Users table (supports both REGISTERED and ANONYMOUS users)
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS users (
    id            UUID         NOT NULL DEFAULT uuid_generate_v4(),
    sub           VARCHAR(255) NOT NULL,
    email         VARCHAR(255),
    name          VARCHAR(255),
    password_hash VARCHAR(255),
    user_type     VARCHAR(20)  NOT NULL DEFAULT 'ANONYMOUS',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_users        PRIMARY KEY (id),
    CONSTRAINT uq_users_sub    UNIQUE (sub)
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users (email) WHERE email IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_sub   ON users (sub);

-- Create system anonymous user for backwards compatibility
INSERT INTO users (id, sub, user_type)
VALUES ('00000000-0000-0000-0000-000000000001', 'system_anonymous', 'ANONYMOUS')
ON CONFLICT (sub) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. Itineraries – aggregate root
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS itineraries (
    id                  UUID        NOT NULL DEFAULT uuid_generate_v4(),
    title               VARCHAR(255) NOT NULL,
    description         TEXT,

    -- Owner / tenancy
    owner_user_id       UUID,
    user_id             UUID        NOT NULL REFERENCES users (id) ON DELETE SET NULL,
    organisation_id     UUID,

    -- Status lifecycle (VARCHAR instead of native ENUM for JPA compatibility)
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    -- Travel window
    planned_start_date  DATE,
    planned_end_date    DATE,
    actual_start_date   DATE,
    actual_end_date     DATE,

    -- Destination
    destination_country VARCHAR(2),   -- ISO 3166-1 alpha-2
    destination_city    VARCHAR(100),

    -- Spatial: primary destination centroid (PostGIS point, SRID 4326 / WGS-84)
    destination_point   GEOMETRY(Point, 4326),

    -- Budget
    estimated_budget_amount  BIGINT,   -- minor currency units (e.g. cents)
    estimated_budget_currency VARCHAR(3), -- ISO 4217 (VARCHAR for consistency)
    actual_cost_amount       BIGINT,
    actual_cost_currency     VARCHAR(3),

    -- Participants
    traveller_count     SMALLINT    NOT NULL DEFAULT 1,

    -- Tags (array of free-form strings)
    tags                TEXT[]      NOT NULL DEFAULT '{}',

    -- Token-based anonymous access
    access_token        VARCHAR(64),

    -- Queue and processing
    queue_position      INT,
    estimated_completion TIMESTAMPTZ,

    -- Travel preferences
    travel_mode         VARCHAR(20),
    preferences         JSONB,

    -- AI-generated content
    ai_suggestions      JSONB,

    -- Computed totals
    total_distance_km      NUMERIC(12, 3),
    total_duration_minutes INT,
    current_step_index     INT NOT NULL DEFAULT 0,

    -- Optimistic locking version counter (INT for JPA compatibility)
    version             INT         NOT NULL DEFAULT 0,

    -- Audit
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),

    -- Soft delete
    deleted_at          TIMESTAMPTZ,

    CONSTRAINT pk_itineraries PRIMARY KEY (id),
    CONSTRAINT chk_itineraries_dates
        CHECK (planned_end_date IS NULL OR planned_start_date IS NULL OR planned_end_date >= planned_start_date),
    CONSTRAINT chk_itineraries_travellers
        CHECK (traveller_count > 0)
);

COMMENT ON TABLE  itineraries              IS 'Itinerary aggregate roots';
COMMENT ON COLUMN itineraries.destination_point IS 'PostGIS Point (SRID 4326) for the primary destination';

-- Indexes on itineraries
CREATE INDEX IF NOT EXISTS idx_itineraries_owner         ON itineraries (owner_user_id)    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_itineraries_user_id       ON itineraries (user_id);
CREATE INDEX IF NOT EXISTS idx_itineraries_status        ON itineraries (status)           WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_itineraries_dates         ON itineraries (planned_start_date, planned_end_date);
CREATE INDEX IF NOT EXISTS idx_itineraries_organisation  ON itineraries (organisation_id)  WHERE organisation_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_itineraries_tags          ON itineraries USING GIN (tags);
CREATE INDEX IF NOT EXISTS idx_itineraries_destination   ON itineraries USING GIST (destination_point)
    WHERE destination_point IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_itineraries_updated_at    ON itineraries (updated_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_itineraries_access_token
    ON itineraries (access_token)
    WHERE access_token IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 3. Itinerary steps – ordered steps within an itinerary
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS itinerary_steps (
    id              UUID        NOT NULL DEFAULT uuid_generate_v4(),
    itinerary_id    UUID        NOT NULL,

    -- Step ordering within the itinerary (1-based, may have gaps)
    sequence        SMALLINT    NOT NULL,
    step_order      INT,

    title           VARCHAR(255) NOT NULL,
    description     TEXT,

    -- Step type (VARCHAR for JPA compatibility)
    step_type       VARCHAR(20) NOT NULL DEFAULT 'ACTIVITY',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- Location details (legacy columns from V1)
    location_name   VARCHAR(255),
    location_address TEXT,

    -- PostGIS geometry column – stores the precise step location.
    location_geom   GEOMETRY(Geometry, 4326),

    -- Structured location fields (added in V3)
    place_name      VARCHAR(255),
    city            VARCHAR(255),
    province        VARCHAR(255),
    region          VARCHAR(255),
    country         VARCHAR(255),
    country_code    CHAR(2),
    latitude        NUMERIC(10, 7),
    longitude       NUMERIC(10, 7),
    osm_id          BIGINT,

    -- Altitude in metres (optional, e.g. for mountain hikes)
    altitude_meters DOUBLE PRECISION,

    -- Temporal
    scheduled_start TIMESTAMPTZ,
    scheduled_end   TIMESTAMPTZ,
    actual_start    TIMESTAMPTZ,
    actual_end      TIMESTAMPTZ,
    arrival_date    DATE,

    -- Duration variance in minutes (positive = late)
    delay_minutes   INT         NOT NULL DEFAULT 0,

    -- Cost
    cost_amount     BIGINT,
    cost_currency   VARCHAR(3),

    -- External references (booking IDs, provider-specific)
    external_ref    VARCHAR(255),
    provider        VARCHAR(100),

    -- Arbitrary step-type-specific JSON payload
    details_json    JSONB       NOT NULL DEFAULT '{}',

    -- AI-generated content
    ai_description  TEXT,
    ai_tips         TEXT,

    -- Route and POI data
    distance_from_prev_km    NUMERIC(10, 3),
    duration_from_prev_min   INT,
    route_geometry_from_prev JSONB,
    poi_nearby               JSONB,

    -- Notes and media
    notes           TEXT,
    media_urls      TEXT[]      NOT NULL DEFAULT '{}',

    -- Optimistic locking
    version         BIGINT      NOT NULL DEFAULT 0,

    -- Audit
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_itinerary_steps      PRIMARY KEY (id),
    CONSTRAINT fk_steps_itinerary      FOREIGN KEY (itinerary_id)
        REFERENCES itineraries (id) ON DELETE CASCADE,
    CONSTRAINT chk_steps_sequence      CHECK (sequence > 0),
    CONSTRAINT chk_steps_times         CHECK (
        scheduled_end IS NULL
        OR scheduled_start IS NULL
        OR scheduled_end >= scheduled_start
    ),
    CONSTRAINT uq_steps_sequence       UNIQUE (itinerary_id, sequence)
);

COMMENT ON TABLE  itinerary_steps            IS 'Individual steps (legs) of an itinerary';
COMMENT ON COLUMN itinerary_steps.location_geom IS
    'PostGIS Geometry (SRID 4326): Point for a single location, '
    'LineString for a route segment (e.g. a flight path)';
COMMENT ON COLUMN itinerary_steps.details_json IS
    'Step-type-specific payload (flight details, hotel info, activity metadata, etc.)';

-- Indexes on itinerary_steps
CREATE INDEX IF NOT EXISTS idx_steps_itinerary       ON itinerary_steps (itinerary_id, sequence);
CREATE INDEX IF NOT EXISTS idx_steps_status          ON itinerary_steps (status);
CREATE INDEX IF NOT EXISTS idx_steps_type            ON itinerary_steps (step_type);
CREATE INDEX IF NOT EXISTS idx_steps_scheduled       ON itinerary_steps (scheduled_start, scheduled_end);
CREATE INDEX IF NOT EXISTS idx_steps_geom            ON itinerary_steps USING GIST (location_geom)
    WHERE location_geom IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_steps_details_gin     ON itinerary_steps USING GIN (details_json);
CREATE INDEX IF NOT EXISTS idx_steps_media_gin       ON itinerary_steps USING GIN (media_urls);
CREATE INDEX IF NOT EXISTS idx_steps_external_ref    ON itinerary_steps (external_ref)
    WHERE external_ref IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 4. Itinerary events – immutable domain event log
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS itinerary_events (
    id              UUID        NOT NULL DEFAULT uuid_generate_v4(),
    itinerary_id    UUID        NOT NULL,

    -- Event identity & ordering
    event_type      VARCHAR(100) NOT NULL,
    event_version   VARCHAR(10)  NOT NULL DEFAULT '1.0.0',
    sequence_number BIGINT,    -- nullable for entity-driven inserts

    -- Correlation / causation
    correlation_id  UUID,
    causation_id    UUID,

    -- Payload serialised as JSON (V1 used 'payload', entity uses 'details')
    payload         JSONB,
    details         JSONB,

    -- Metadata (actor, service, etc.)
    source_service  VARCHAR(100),
    actor_id        UUID,

    -- Timestamps (V1 used 'occurred_at', entity uses 'created_at')
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT pk_itinerary_events     PRIMARY KEY (id),
    CONSTRAINT fk_events_itinerary     FOREIGN KEY (itinerary_id)
        REFERENCES itineraries (id) ON DELETE CASCADE
);

COMMENT ON TABLE itinerary_events IS
    'Immutable domain event log for itinerary aggregates (event sourcing / audit trail)';

-- Indexes on itinerary_events
CREATE INDEX IF NOT EXISTS idx_events_itinerary      ON itinerary_events (itinerary_id, sequence_number);
CREATE INDEX IF NOT EXISTS idx_events_type           ON itinerary_events (event_type);
CREATE INDEX IF NOT EXISTS idx_events_occurred_at    ON itinerary_events (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_events_correlation    ON itinerary_events (correlation_id)
    WHERE correlation_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_events_payload_gin    ON itinerary_events USING GIN (payload);

-- ---------------------------------------------------------------------------
-- 5. Outbox events – transactional outbox for reliable Kafka publishing
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS outbox_events (
    id              UUID        NOT NULL DEFAULT uuid_generate_v4(),

    -- Kafka routing (topic is nullable - derived at publish time)
    topic           VARCHAR(255),
    partition_key   VARCHAR(255),

    -- Event classification
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID        NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    event_version   VARCHAR(10)  NOT NULL DEFAULT '1.0.0',

    -- Serialised payload (TEXT for JPA compatibility instead of JSONB)
    payload         TEXT        NOT NULL,

    -- Optional Kafka headers serialised as a JSON object
    headers         JSONB       NOT NULL DEFAULT '{}',

    -- Delivery lifecycle (VARCHAR for JPA compatibility)
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count     SMALLINT    NOT NULL DEFAULT 0,
    max_retries     SMALLINT    NOT NULL DEFAULT 5,
    last_error      TEXT,

    -- Timestamps
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    scheduled_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    failed_at       TIMESTAMPTZ,

    CONSTRAINT pk_outbox_events     PRIMARY KEY (id),
    CONSTRAINT chk_outbox_retries   CHECK (retry_count >= 0 AND max_retries >= 0)
);

COMMENT ON TABLE outbox_events IS
    'Transactional outbox table for reliable exactly-once Kafka event publishing';

-- Indexes on outbox_events
CREATE INDEX IF NOT EXISTS idx_outbox_pending       ON outbox_events (scheduled_at ASC)
    WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate     ON outbox_events (aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_outbox_topic         ON outbox_events (topic);
CREATE INDEX IF NOT EXISTS idx_outbox_status        ON outbox_events (status);
CREATE INDEX IF NOT EXISTS idx_outbox_created_at    ON outbox_events (created_at DESC);

-- ---------------------------------------------------------------------------
-- 6. Generated documents – AI-generated itinerary summaries
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS generated_documents (
    id              UUID        NOT NULL DEFAULT uuid_generate_v4(),
    itinerary_id    UUID        NOT NULL REFERENCES itineraries (id) ON DELETE CASCADE,
    document_type   VARCHAR(50) NOT NULL DEFAULT 'SUMMARY',
    content_md      TEXT,
    storage_path    VARCHAR(500),
    is_public       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_generated_documents PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_generated_docs_itinerary
    ON generated_documents (itinerary_id);

-- ---------------------------------------------------------------------------
-- 7. Triggers – maintain updated_at automatically
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

DROP TRIGGER IF EXISTS trg_itineraries_updated_at ON itineraries;
CREATE TRIGGER trg_itineraries_updated_at
    BEFORE UPDATE ON itineraries
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

DROP TRIGGER IF EXISTS trg_itinerary_steps_updated_at ON itinerary_steps;
CREATE TRIGGER trg_itinerary_steps_updated_at
    BEFORE UPDATE ON itinerary_steps
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- ---------------------------------------------------------------------------
-- Done
-- ---------------------------------------------------------------------------
