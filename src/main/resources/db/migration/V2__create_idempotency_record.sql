CREATE TABLE idempotency_record (
    -- The key is the primary key: the unique constraint IS the lock that makes
    -- "insert wins" work. Only one concurrent INSERT for a given key can succeed.
    idempotency_key VARCHAR(255) PRIMARY KEY,
    request_hash    VARCHAR(64)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    -- response_status / response_body are NULL while IN_PROGRESS and populated
    -- once the work COMPLETED, so a later retry can replay the exact response.
    response_status INTEGER,
    response_body   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);
