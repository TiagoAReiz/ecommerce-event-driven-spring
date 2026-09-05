-- =========================================================
-- Marketplace - SERVIÇO ORDER (orders / cart)
-- PostgreSQL. Banco proprio: order_db.
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


CREATE TYPE order_status AS ENUM (
    'pending', 'paid', 'processing', 'shipped', 'delivered', 'cancelled', 'refunded'
    );

CREATE TABLE orders (
                        id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        id_customer  BIGINT NOT NULL,                 -- serviço user, sem FK
                        status       order_status NOT NULL DEFAULT 'pending',
                        items_cost   NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (items_cost >= 0),
                        freight_cost NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (freight_cost >= 0),
                        total_cost   NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (total_cost >= 0),
                        created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                        updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX orders_customer_idx ON orders (id_customer);
CREATE INDEX orders_status_idx   ON orders (status);

CREATE TRIGGER orders_set_updated_at BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


CREATE TABLE order_item (
                            id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                            id_order          BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
                            id_product        BIGINT NOT NULL,            -- serviço inventory, sem FK
                            product_name      VARCHAR(200) NOT NULL,      -- [snapshot]
                            product_photo_url TEXT,                       -- [snapshot]
                            id_owner          BIGINT NOT NULL,            -- [snapshot] quem vende, p/ o shipment
                            price_at_time     NUMERIC(12,2) NOT NULL CHECK (price_at_time >= 0),
                            quantity          INTEGER NOT NULL CHECK (quantity > 0),
                            created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
                            updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
                            deleted_at        TIMESTAMPTZ
);

CREATE INDEX order_item_order_idx   ON order_item (id_order)   WHERE deleted_at IS NULL;
CREATE INDEX order_item_product_idx ON order_item (id_product) WHERE deleted_at IS NULL;

CREATE TRIGGER order_item_set_updated_at BEFORE UPDATE ON order_item
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


CREATE TABLE cart (
                      id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                      id_user    BIGINT NOT NULL,                   -- serviço user, sem FK
                      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                      updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                      deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX cart_user_uk ON cart (id_user) WHERE deleted_at IS NULL;

CREATE TRIGGER cart_set_updated_at BEFORE UPDATE ON cart
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Sem preço de propósito: carrinho hidrata do inventory na leitura
-- e o preço é revalidado no checkout.
CREATE TABLE cart_items (
                            id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                            id_cart    BIGINT NOT NULL REFERENCES cart (id) ON DELETE CASCADE,
                            id_product BIGINT NOT NULL,                   -- serviço inventory, sem FK
                            quantity   INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
                            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                            updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                            deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX cart_items_cart_product_uk
    ON cart_items (id_cart, id_product) WHERE deleted_at IS NULL;

CREATE TRIGGER cart_items_set_updated_at BEFORE UPDATE ON cart_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
