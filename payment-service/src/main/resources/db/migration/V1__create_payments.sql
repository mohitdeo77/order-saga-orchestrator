CREATE TABLE payments (
    saga_id         UUID PRIMARY KEY,
    customer_id     VARCHAR(100),
    amount          NUMERIC(12, 2)           NOT NULL,
    status          VARCHAR(20)              NOT NULL,
    decline_reason  VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT                   NOT NULL
);
