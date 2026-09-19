-- =========================================================
-- Loja unica, nao marketplace: so existe um vendedor, a propria loja.
--
-- id_owner e owner_name carregariam sempre o mesmo valor em todo produto,
-- e owner_name ainda ficaria velho no dia em que o nome da loja mudasse.
-- O indice product_owner_idx cai junto com a coluna.
-- =========================================================

ALTER TABLE product
    DROP COLUMN id_owner,
    DROP COLUMN owner_name;
