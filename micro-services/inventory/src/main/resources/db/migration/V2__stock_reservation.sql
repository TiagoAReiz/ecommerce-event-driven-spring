-- =========================================================
-- Reserva de estoque (saga order -> inventory)
--
-- A reserva nasce quando o pedido entra em 'pending', antes do
-- pagamento, e vence sozinha: nao ha rotina devolvendo estoque.
--
-- Disponivel para venda e calculado na hora:
--   product.stock - SUM(quantity) das reservas 'held' nao vencidas
--
-- Reserva vencida nao precisa ser apagada nem atualizada: ela
-- simplesmente para de entrar nessa soma quando expires_at passa.
-- =========================================================

CREATE TYPE reservation_status AS ENUM ('held', 'confirmed', 'released');

CREATE TABLE stock_reservation (
                                   id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                   id_order   BIGINT NOT NULL,                 -- serviço order, sem FK
                                   id_product BIGINT NOT NULL REFERENCES product (id),
                                   quantity   INTEGER NOT NULL CHECK (quantity > 0),
                                   status     reservation_status NOT NULL DEFAULT 'held',
                                   expires_at TIMESTAMPTZ NOT NULL,
                                   created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                   updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Idempotencia do consumidor: Kafka entrega at-least-once, entao o mesmo
-- OrderCreated chega duas vezes. A segunda viola esta constraint.
CREATE UNIQUE INDEX stock_reservation_order_product_uk
    ON stock_reservation (id_order, id_product);

CREATE INDEX stock_reservation_order_idx ON stock_reservation (id_order);

-- Indice da soma de disponibilidade. Parcial em 'held' para que o indice
-- guarde so as reservas que ainda podem contar, e nao o historico inteiro.
CREATE INDEX stock_reservation_active_idx
    ON stock_reservation (id_product, expires_at) WHERE status = 'held';

CREATE TRIGGER stock_reservation_set_updated_at BEFORE UPDATE ON stock_reservation
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
