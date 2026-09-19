-- =========================================================
-- Endereco de entrega no pedido.
--
-- Sem isto o destino so passa a existir quando o shipment e criado, depois
-- do pagamento: entre 'pending' e 'paid' ninguem sabe para onde o pedido vai,
-- e o frete cobrado no checkout nao tem como ser conferido depois.
--
-- So o id: o snapshot completo do endereco (to_*) continua sendo do shipment,
-- que e quem precisa dele imutavel. O endereco no user e mutavel.
--
-- NOT NULL sem DEFAULT: ainda nao existe caminho que crie pedido, entao a
-- tabela esta vazia. Se houver linha de teste no banco local, esta migration
-- falha de proposito, pedido sem destino nao e um pedido valido.
-- =========================================================

ALTER TABLE orders
    ADD COLUMN id_address BIGINT NOT NULL;           -- servico user, sem FK
