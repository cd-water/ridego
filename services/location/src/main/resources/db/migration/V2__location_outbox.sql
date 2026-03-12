CREATE TABLE outbox (
    id           bigserial PRIMARY KEY,
    event_id     uuid NOT NULL UNIQUE,
    topic        varchar(128) NOT NULL,
    event_key    varchar(128) NOT NULL,
    payload      jsonb NOT NULL,
    occurred_at  timestamptz NOT NULL,
    published_at timestamptz,
    parked_at    timestamptz,
    attempts     integer NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_unpublished ON outbox (id) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    consumer     varchar(64) NOT NULL,
    event_id     uuid NOT NULL,
    processed_at timestamptz NOT NULL,
    PRIMARY KEY (consumer, event_id)
);
