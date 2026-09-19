-- =========================================================
-- Loja unica: no maximo um owner ativo.
--
-- Indice sobre uma expressao constante: toda linha ativa tem a mesma chave
-- (true), entao so cabe uma. O servico ja garante isso ao provisionar o dono,
-- mas duas instancias subindo ao mesmo tempo nao tem como combinar entre si;
-- o banco tem.
-- =========================================================

CREATE UNIQUE INDEX owner_single_uk ON owner ((true)) WHERE deleted_at IS NULL;
