-- =========================================================
-- Marketplace - SERVIÇO PAYMENT (token m2m)
-- PostgreSQL. Banco proprio: payment_db.
-- Regra: FK só existe dentro do mesmo serviço.
-- IDs de outro serviço são BIGINT puro, sem REFERENCES.
-- =========================================================

CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE TYPE payment_status AS ENUM (
    'pending', 'authorized', 'captured', 'failed', 'refunded', 'cancelled'
    );

CREATE TABLE payment (
                         id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                         id_order         BIGINT NOT NULL,             -- serviço order, sem FK
                         value            NUMERIC(12,2) NOT NULL CHECK (value >= 0),
                         status           payment_status NOT NULL DEFAULT 'pending',
                         provider         VARCHAR(60),                 -- stripe, mercadopago...
                         external_id      VARCHAR(120),                -- id da transação no provider
                         idempotency_key  VARCHAR(120) NOT NULL,       -- sem isso, retentativa = cobrança dupla
                         created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
                         updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
                         deleted_at       TIMESTAMPTZ
);

CREATE UNIQUE INDEX payment_idempotency_uk ON payment (idempotency_key);
CREATE INDEX payment_order_idx             ON payment (id_order);

CREATE TRIGGER payment_set_updated_at BEFORE UPDATE ON payment
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
