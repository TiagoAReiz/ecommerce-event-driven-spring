-- =========================================================
-- A origem de todo envio e o endereco da loja, que vive na configuracao
-- (app.store.origin.*), nao numa linha de address no servico user.
--
-- id_address_owner era NOT NULL e nao teria de onde vir: sem esta migration o
-- primeiro envio nao poderia ser gravado. O snapshot from_* continua: e ele que
-- preserva a origem de envios antigos quando a loja mudar de endereco.
-- =========================================================

ALTER TABLE shipment
    DROP COLUMN id_address_owner;
