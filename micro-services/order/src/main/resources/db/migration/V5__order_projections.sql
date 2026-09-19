-- Projeções de pagamento, envio, reserva de estoque e motivo de cancelamento
-- Campos adicionados ao pedido para cache de informacoes de servicos downstream

ALTER TABLE orders
    ADD COLUMN payment_id        BIGINT,
    ADD COLUMN payment_status    VARCHAR(20),
    ADD COLUMN shipment_id       BIGINT,
    ADD COLUMN shipment_status   VARCHAR(30),
    ADD COLUMN tracking_code     VARCHAR(60),
    ADD COLUMN stock_reservation VARCHAR(20) NOT NULL DEFAULT 'pending',
    ADD COLUMN cancel_reason     TEXT,
    ADD COLUMN refunded_amount   NUMERIC(12,2) NOT NULL DEFAULT 0;

-- Index para consultas de status de reserva de estoque
CREATE INDEX orders_stock_reservation_idx ON orders (stock_reservation);
