ALTER TABLE payment
    ADD COLUMN method           VARCHAR(20),
    ADD COLUMN status_detail    VARCHAR(120),
    ADD COLUMN qr_code          TEXT,
    ADD COLUMN qr_code_base64   TEXT,
    ADD COLUMN ticket_url       TEXT,
    ADD COLUMN init_point       TEXT,
    ADD COLUMN expires_at       TIMESTAMPTZ,
    ADD COLUMN card_brand       VARCHAR(30),
    ADD COLUMN card_last4       VARCHAR(4),
    ADD COLUMN installments     SMALLINT,
    ADD COLUMN refunded_amount  NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN approved_at      TIMESTAMPTZ;

CREATE INDEX payment_external_idx ON payment (external_id);
