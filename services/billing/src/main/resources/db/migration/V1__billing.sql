CREATE TABLE invoices (
    id           uuid PRIMARY KEY,
    trip_id      uuid NOT NULL UNIQUE,
    rider_id     varchar(64) NOT NULL,
    driver_id    varchar(64) NOT NULL,
    amount_cents integer NOT NULL,
    state        varchar(16) NOT NULL,
    issued_at    timestamptz NOT NULL,
    paid_at      timestamptz
);

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

CREATE INDEX idx_billing_outbox_unpublished ON outbox (id) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    consumer     varchar(64) NOT NULL,
    event_id     uuid NOT NULL,
    processed_at timestamptz NOT NULL,
    PRIMARY KEY (consumer, event_id)
);
