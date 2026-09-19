-- =========================================================
-- Loja unica, nao marketplace: o vendedor de todo item e sempre a loja.
--
-- A coluna existia para o shipment descobrir de quem era cada item e montar
-- uma remessa por vendedor. Com um vendedor so, e um valor constante.
-- =========================================================

ALTER TABLE order_item
    DROP COLUMN id_owner;
