CREATE TABLE trip_positions (
    trip_id     uuid NOT NULL,
    driver_id   varchar(64) NOT NULL,
    city_id     varchar(64) NOT NULL,
    lat         double precision NOT NULL,
    lng         double precision NOT NULL,
    recorded_at timestamptz NOT NULL,
    PRIMARY KEY (trip_id, recorded_at, driver_id)
) PARTITION BY RANGE (recorded_at);

CREATE TABLE trip_positions_default PARTITION OF trip_positions DEFAULT;

CREATE INDEX idx_trip_positions_trip ON trip_positions (trip_id, recorded_at);
