-- =========================================================
-- Marketplace - SERVIÇO SHIPMENT
-- PostgreSQL. Banco proprio: shipment_db.
-- Regra: FK só existe dentro do mesmo serviço.
-- IDs de outro serviço são BIGINT puro, sem REFERENCES.
-- Colunas marcadas [snapshot] são cópias imutáveis vindas
-- de outro serviço (histórico não pode mudar retroativamente).
-- =========================================================

CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE TYPE shipment_status AS ENUM (
    'pending', 'ready_to_ship', 'in_transit', 'out_for_delivery', 'delivered', 'returned', 'cancelled'
    );

CREATE TABLE shipment (
                          id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          id_order          BIGINT NOT NULL,            -- serviço order, sem FK
                          id_user           BIGINT NOT NULL,            -- serviço user, sem FK
                          status            shipment_status NOT NULL DEFAULT 'pending',
                          freight_tax       NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (freight_tax >= 0),
                          tracking_code     VARCHAR(60),

    -- destino: [snapshot] completo, endereço no user é mutável
                          id_address_user   BIGINT NOT NULL,
                          to_zipcode        VARCHAR(20)  NOT NULL,
                          to_country        VARCHAR(60)  NOT NULL,
                          to_state          VARCHAR(60)  NOT NULL,
                          to_city           VARCHAR(120) NOT NULL,
                          to_street         VARCHAR(200) NOT NULL,
                          to_number         VARCHAR(20),

    -- origem: [snapshot] completo
                          id_address_owner  BIGINT NOT NULL,
                          from_zipcode      VARCHAR(20)  NOT NULL,
                          from_country      VARCHAR(60)  NOT NULL,
                          from_state        VARCHAR(60)  NOT NULL,
                          from_city         VARCHAR(120) NOT NULL,
                          from_street       VARCHAR(200) NOT NULL,
                          from_number       VARCHAR(20),

                          created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
                          updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
                          deleted_at        TIMESTAMPTZ
);

CREATE UNIQUE INDEX shipment_order_uk ON shipment (id_order) WHERE deleted_at IS NULL;
CREATE INDEX shipment_user_idx        ON shipment (id_user)  WHERE deleted_at IS NULL;

CREATE TRIGGER shipment_set_updated_at BEFORE UPDATE ON shipment
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
