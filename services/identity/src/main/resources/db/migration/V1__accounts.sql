CREATE TABLE accounts (
    id            uuid PRIMARY KEY,
    username      varchar(64)  NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    role          varchar(16)  NOT NULL,
    driver_id     varchar(64),
    created_at    timestamptz  NOT NULL
);
