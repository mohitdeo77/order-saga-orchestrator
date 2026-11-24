CREATE TABLE products (
    sku                 VARCHAR(50) PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,
    available_quantity  INT          NOT NULL CHECK (available_quantity >= 0),
    version             BIGINT       NOT NULL
);

CREATE TABLE stock_reservations (
    saga_id     UUID PRIMARY KEY,
    status      VARCHAR(20)              NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT                   NOT NULL
);

CREATE TABLE stock_reservation_items (
    saga_id   UUID        NOT NULL REFERENCES stock_reservations (saga_id),
    sku       VARCHAR(50) NOT NULL REFERENCES products (sku),
    quantity  INT         NOT NULL CHECK (quantity > 0)
);

CREATE INDEX idx_stock_reservation_items_saga ON stock_reservation_items (saga_id);
