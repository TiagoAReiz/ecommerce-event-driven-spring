-- =========================================================
-- Marketplace - SERVIÇO INVENTORY (products / reviews)
-- PostgreSQL. Banco proprio: inventory_db.
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


CREATE TABLE category (
                          id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          name       VARCHAR(120) NOT NULL,
                          slug       VARCHAR(120) NOT NULL,
                          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                          updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                          deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX category_slug_uk ON category (slug) WHERE deleted_at IS NULL;

CREATE TRIGGER category_set_updated_at BEFORE UPDATE ON category
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


CREATE TABLE product (
                         id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                         id_owner     BIGINT NOT NULL,                 -- serviço user, sem FK
                         owner_name   VARCHAR(150),                    -- [snapshot] evita chamada por card na vitrine
                         id_category  BIGINT REFERENCES category (id),
                         name         VARCHAR(200) NOT NULL,
                         description  TEXT,
                         price        NUMERIC(12,2) NOT NULL CHECK (price >= 0),
                         stock        INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
                         rating       NUMERIC(3,2) NOT NULL DEFAULT 0 CHECK (rating BETWEEN 0 AND 5),
                         rating_count INTEGER NOT NULL DEFAULT 0 CHECK (rating_count >= 0),
                         created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                         updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                         deleted_at   TIMESTAMPTZ
);

CREATE INDEX product_owner_idx    ON product (id_owner)    WHERE deleted_at IS NULL;
CREATE INDEX product_category_idx ON product (id_category) WHERE deleted_at IS NULL;
CREATE INDEX product_price_idx    ON product (price)       WHERE deleted_at IS NULL;
CREATE INDEX product_name_idx     ON product (lower(name));

CREATE TRIGGER product_set_updated_at BEFORE UPDATE ON product
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


CREATE TABLE product_photos (
                                id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                id_product BIGINT NOT NULL REFERENCES product (id) ON DELETE CASCADE,
                                photo_url  TEXT NOT NULL,
                                position   SMALLINT NOT NULL DEFAULT 0,       -- ordem de exibição
                                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                deleted_at TIMESTAMPTZ
);

CREATE INDEX product_photos_product_idx ON product_photos (id_product) WHERE deleted_at IS NULL;

CREATE TRIGGER product_photos_set_updated_at BEFORE UPDATE ON product_photos
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Preenchida por evento OrderDelivered vindo do order.
-- Sem isso o inventory não tem como provar que a compra existiu.
CREATE TABLE review_eligibility (
                                    id_user     BIGINT NOT NULL,                  -- serviço user, sem FK
                                    id_product  BIGINT NOT NULL,                  -- local, mas mantido sem FK p/ ser preenchido por evento
                                    id_order    BIGINT NOT NULL,                  -- serviço order, sem FK
                                    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                                    PRIMARY KEY (id_user, id_product, id_order)
);


CREATE TABLE review (
                        id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        id_user         BIGINT NOT NULL,              -- serviço user, sem FK
                        user_name       VARCHAR(150),                 -- [snapshot]
                        user_photo_url  TEXT,                         -- [snapshot]
                        id_product      BIGINT NOT NULL REFERENCES product (id),
                        id_order        BIGINT NOT NULL,              -- serviço order, sem FK
                        rate            SMALLINT NOT NULL CHECK (rate BETWEEN 1 AND 5),
                        title           VARCHAR(150),
                        description     TEXT,
                        created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                        updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                        deleted_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX review_user_product_order_uk
    ON review (id_user, id_product, id_order) WHERE deleted_at IS NULL;
CREATE INDEX review_product_idx ON review (id_product) WHERE deleted_at IS NULL;

CREATE TRIGGER review_set_updated_at BEFORE UPDATE ON review
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
