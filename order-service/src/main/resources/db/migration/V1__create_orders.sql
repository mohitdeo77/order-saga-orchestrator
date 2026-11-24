CREATE TABLE orders (
    id                UUID PRIMARY KEY,
    saga_id           UUID                     NOT NULL,
    customer_id       VARCHAR(100),
    total_amount      NUMERIC(12, 2),
    status            VARCHAR(20)              NOT NULL,
    rejection_reason  VARCHAR(500),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    version           BIGINT                   NOT NULL,
    CONSTRAINT uq_orders_saga UNIQUE (saga_id)
);

CREATE TABLE order_lines (
    order_id    UUID           NOT NULL REFERENCES orders (id),
    sku         VARCHAR(50)    NOT NULL,
    quantity    INT            NOT NULL,
    unit_price  NUMERIC(12, 2) NOT NULL
);

CREATE INDEX idx_order_lines_order ON order_lines (order_id);
