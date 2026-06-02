CREATE TABLE payment (
    id         UUID         PRIMARY KEY,
    amount     BIGINT       NOT NULL,
    currency   VARCHAR(3)   NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);
