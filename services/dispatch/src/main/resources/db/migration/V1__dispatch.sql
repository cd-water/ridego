CREATE TABLE driver_availability (
    driver_id  varchar(64) PRIMARY KEY,
    city_id    varchar(64) NOT NULL,
    state      varchar(16) NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE offers (
    id           uuid PRIMARY KEY,
    trip_id      uuid NOT NULL,
    driver_id    varchar(64) NOT NULL,
    city_id      varchar(64) NOT NULL,
    state        varchar(16) NOT NULL,
    created_at   timestamptz NOT NULL,
    expires_at   timestamptz NOT NULL,
    responded_at timestamptz
);

CREATE UNIQUE INDEX idx_offers_pending_driver
    ON offers (driver_id) WHERE state = 'PENDING';
CREATE INDEX idx_offers_trip ON offers (trip_id);

CREATE TABLE outbox (
    id           bigserial PRIMARY KEY,
    event_id     uuid NOT NULL UNIQUE,
    topic        varchar(128) NOT NULL,
    event_key    varchar(128) NOT NULL,
    payload      jsonb NOT NULL,
    occurred_at  timestamptz NOT NULL,
    published_at timestamptz,
    attempts     integer NOT NULL DEFAULT 0
);

CREATE INDEX idx_dispatch_outbox_unpublished ON outbox (id) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    consumer     varchar(64) NOT NULL,
    event_id     uuid NOT NULL,
    processed_at timestamptz NOT NULL,
    PRIMARY KEY (consumer, event_id)
);
