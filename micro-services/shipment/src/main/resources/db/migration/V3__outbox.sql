-- =========================================================
-- Outbox transacional. O evento e gravado na MESMA transacao da mudanca
-- de estado: sai se, e somente se, o estado foi commitado.
--
-- Quem publica e o Debezium, lendo o WAL. Esta tabela nao e fila de
-- trabalho: ninguem faz SELECT nela no caminho normal.
-- =========================================================

CREATE TABLE outbox (
    id            UUID         PRIMARY KEY,     -- eventId do record; vira o header "id"
    aggregatetype VARCHAR(60)  NOT NULL,        -- order, stock, payment, shipment, user
    aggregateid   VARCHAR(60)  NOT NULL,        -- key da mensagem: orderId ou userId
    topic         VARCHAR(200) NOT NULL,        -- ecommerce.order.created.v1
    type          VARCHAR(60)  NOT NULL,        -- alias do contrato: orderCreated
    payload       JSONB        NOT NULL,        -- o record serializado, igual ao de hoje
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Limpeza por idade e consulta de incidente ("o que saiu entre X e Y").
CREATE INDEX outbox_created_at_idx ON outbox (created_at);
-- Reprocessamento de um pedido especifico.
CREATE INDEX outbox_aggregate_idx  ON outbox (aggregatetype, aggregateid);

-- A publicacao nasce aqui, e nao pelo Debezium: quem cria publicacao precisa
-- ser dono da tabela, e o usuario do Debezium nao deve ser dono de nada.
CREATE PUBLICATION debezium_shipment_outbox FOR TABLE outbox;

-- Heartbeat. Todos os bancos dividem o mesmo WAL do cluster: um banco sem
-- escrita segura o slot parado e o WAL dos outros cresce sem limite. O
-- Debezium atualiza esta linha periodicamente para o slot sempre andar.
CREATE TABLE debezium_heartbeat (
    id      SMALLINT    PRIMARY KEY,
    beat_at TIMESTAMPTZ NOT NULL
);
INSERT INTO debezium_heartbeat (id, beat_at) VALUES (1, now());
