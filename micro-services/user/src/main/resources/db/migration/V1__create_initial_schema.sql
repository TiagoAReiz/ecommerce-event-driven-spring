-- =========================================================
-- Marketplace - SERVIÇO USER (users / owners / address)
-- PostgreSQL. Banco proprio: user_db.
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


CREATE TABLE users (
                       id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       name       VARCHAR(150) NOT NULL,
                       email      VARCHAR(255) NOT NULL,
                       google_sub VARCHAR(255) NOT NULL,
                       cpf        CHAR(11),
                       phone      VARCHAR(20),
                       photo_url  TEXT,
                       created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                       updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                       deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX users_email_uk      ON users (lower(email)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX users_google_sub_uk ON users (google_sub)   WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX users_cpf_uk        ON users (cpf)          WHERE deleted_at IS NULL AND cpf IS NOT NULL;

CREATE TRIGGER users_set_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


CREATE TABLE owner (
                       id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       id_user    BIGINT NOT NULL REFERENCES users (id),
                       created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                       updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                       deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX owner_user_uk ON owner (id_user) WHERE deleted_at IS NULL;

CREATE TRIGGER owner_set_updated_at BEFORE UPDATE ON owner
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


CREATE TABLE address (
                         id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                         id_user    BIGINT NOT NULL REFERENCES users (id),
                         name       VARCHAR(60),                       -- apelido: "casa", "trabalho"
                         zipcode    VARCHAR(20) NOT NULL,
                         country    VARCHAR(60) NOT NULL DEFAULT 'BR',
                         state      VARCHAR(60) NOT NULL,
                         city       VARCHAR(120) NOT NULL,
                         street     VARCHAR(200) NOT NULL,
                         number     VARCHAR(20),
                         created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                         updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                         deleted_at TIMESTAMPTZ
);

CREATE INDEX address_user_idx ON address (id_user) WHERE deleted_at IS NULL;

CREATE TRIGGER address_set_updated_at BEFORE UPDATE ON address
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
