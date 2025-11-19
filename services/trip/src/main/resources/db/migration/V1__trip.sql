CREATE TABLE trips (
    id                 uuid PRIMARY KEY,
    city_id            varchar(64) NOT NULL,
    rider_id           varchar(64) NOT NULL,
    driver_id          varchar(64),
    state              varchar(32) NOT NULL,
    pickup_lat         double precision NOT NULL,
    pickup_lng         double precision NOT NULL,
    dropoff_lat        double precision NOT NULL,
    dropoff_lng        double precision NOT NULL,
    version            bigint NOT NULL DEFAULT 0,
    requested_at       timestamptz NOT NULL,
    matching_started_at timestamptz,
    accepted_at        timestamptz,
    arrived_at         timestamptz,
    started_at         timestamptz,
    completed_at       timestamptz,
    cancelled_at       timestamptz,
    cancel_actor       varchar(16),
    cancel_reason      varchar(64),
    unmatched_at       timestamptz
);

CREATE TABLE trip_read (
    id          uuid PRIMARY KEY,
    city_id     varchar(64) NOT NULL,
    rider_id    varchar(64) NOT NULL,
    driver_id   varchar(64),
    state       varchar(32) NOT NULL,
    pickup_lat  double precision NOT NULL,
    pickup_lng  double precision NOT NULL,
    dropoff_lat double precision NOT NULL,
    dropoff_lng double precision NOT NULL,
    updated_at  timestamptz NOT NULL
);

CREATE TABLE outbox (
    id          bigserial PRIMARY KEY,
    event_id    uuid NOT NULL UNIQUE,
    topic       varchar(128) NOT NULL,
    event_key   varchar(128) NOT NULL,
    payload     jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    published_at timestamptz,
    attempts    integer NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_unpublished ON outbox (id) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    consumer     varchar(64) NOT NULL,
    event_id     uuid NOT NULL,
    processed_at timestamptz NOT NULL,
    PRIMARY KEY (consumer, event_id)
);
