# ADR-001: Outbox transacional com Debezium

**Status:** Aceito — implementação planejada
**Data:** 2026-09-18
**Afeta:** `order`, `inventory`, `payment`, `shipment`, `user` · `docker-compose.yaml` · `docs/event-contracts.md`

> Decisão de arquitetura **e** plano de implementação. As seções 1–5 registram o porquê; as
> seções 6–10 são o como, em ordem de execução.

---

## Sumário

1. [Contexto](#1-contexto)
2. [Decisão](#2-decisão)
3. [Opções consideradas](#3-opções-consideradas)
4. [Trade-offs](#4-trade-offs)
5. [Consequências](#5-consequências)
6. [Desenho](#6-desenho)
7. [Plano de implementação](#7-plano-de-implementação)
8. [Verificação](#8-verificação)
9. [Pontos a confirmar na implementação](#9-pontos-a-confirmar-na-implementação)
10. [Impacto nos outros documentos](#10-impacto-nos-outros-documentos)

---

## 1. Contexto

Hoje `order` e `inventory` publicam eventos com `KafkaTemplate`, **depois** do commit do banco:

```java
Result result = stockHold.hold(idOrder, items);   // commit aqui
publisher.publishStockReserved(idOrder);          // send aqui — fora da transação
```

Publicar depois do commit foi escolha consciente: dentro da transação, os locks de `product`
ficariam presos ao tempo de resposta do broker. O preço é uma **janela de perda**:

| Falha | Resultado hoje |
|---|---|
| O processo cai entre o commit e o `send` | estado gravado, evento nunca sai. Nada tenta de novo |
| O broker recusa ou está fora | `send` devolve um `CompletableFuture` que ninguém olha. O evento some sem log |
| Reentrega do evento de entrada | a guarda de idempotência (`ALREADY_RESERVED`) impede o reprocessamento, e o evento que se perdeu na primeira vez **nunca** é republicado |

Numa saga de pedido e pagamento (`docs/event-contracts.md`), evento perdido é pedido parado:
pago sem baixa de estoque, cancelado sem estorno. Com 11 eventos novos planejados, a janela se
multiplicaria por todos eles.

O que se quer é a garantia clássica: **o evento sai se, e somente se, a mudança de estado foi
commitada.**

---

## 2. Decisão

1. Cada serviço produtor grava seus eventos numa tabela **`outbox`** do próprio banco, **na
   mesma transação** da mudança de estado. Se a transação faz rollback, o evento não existe.
2. O **Debezium** (Kafka Connect + conector PostgreSQL) lê as inserções na `outbox` pelo log de
   replicação (WAL) e publica no Kafka com o **Outbox Event Router**, no tópico, key e header de
   tipo que o contrato já define.
3. Nenhum serviço publica evento de domínio com `KafkaTemplate`. `KafkaTemplate` continua
   existindo só para a DLT (`docs/event-contracts.md` §11).
4. **O contrato de fio não muda:** mesmo tópico, mesma key, mesmo `__TypeId__`, mesmo JSON. Os
   consumidores existentes não sabem que o produtor mudou.

---

## 3. Opções consideradas

### A. Publicar depois do commit (situação atual)

| Dimensão | Avaliação |
|---|---|
| Complexidade | baixa |
| Infra nova | nenhuma |
| Garantia | **nenhuma** — perde evento em crash e em falha do broker |
| Latência | a menor possível |

Descartada: é exatamente o problema.

### B. Outbox + publicador *polling* na aplicação

Cada serviço grava na `outbox` e um `@Scheduled` lê as linhas pendentes, publica e as marca
como enviadas.

| Dimensão | Avaliação |
|---|---|
| Complexidade | média — o publicador é código nosso, cinco vezes |
| Infra nova | nenhuma |
| Garantia | at-least-once |
| Latência | intervalo do polling (centenas de ms a segundos) |
| Carga no banco | um `SELECT … FOR UPDATE SKIP LOCKED` por serviço a cada ciclo, com ou sem evento |

**Prós:** não traz componente novo; fácil de depurar.
**Contras:** ordem de publicação com várias instâncias exige cuidado (`SKIP LOCKED` embaralha a
ordem entre linhas do mesmo pedido); polling constante; mais uma coisa para cada serviço manter.

### C. Outbox + Debezium (CDC) — **escolhida**

| Dimensão | Avaliação |
|---|---|
| Complexidade | média — pouca no código, a complexidade vai para a infra |
| Infra nova | Kafka Connect com Debezium; `wal_level=logical` no Postgres |
| Garantia | at-least-once, **na ordem de commit** |
| Latência | dezenas de ms — lê o WAL em streaming, não faz polling |
| Carga no banco | um slot de replicação por banco; nenhuma consulta periódica |

**Prós:** ordem de commit preservada por construção; zero código de publicação nos serviços;
roteamento e headers por configuração; padrão consolidado.
**Contras:** componente novo para operar; slot de replicação parado retém WAL e pode encher o
disco; `wal_level=logical` exige reiniciar o Postgres.

### D. Transação Kafka sincronizada com a do banco

`KafkaTransactionManager` encadeado com o `JpaTransactionManager`.

Descartada: não existe commit atômico entre dois recursos. A sincronização faz o commit de um e
depois o do outro — a janela muda de lugar, não desaparece.

---

## 4. Trade-offs

| | B — polling | C — Debezium |
|---|---|---|
| Onde mora a complexidade | no código de 5 serviços | num componente de infra |
| Ordem por pedido | precisa de desenho (lock por `aggregateid` ou instância única) | vem do WAL |
| Custo com a loja parada | polling contínuo | quase zero (heartbeat) |
| Modo de falha perigoso | publicador travado → atraso | slot parado → **WAL cresce até encher o disco** |
| Observabilidade | log do serviço | REST do Connect + `pg_replication_slots` |

O modo de falha do Debezium é mais grave, mas é **monitorável e com causa única** (conector
parado). O do polling é mais brando, mas vem de cinco implementações que podem divergir.

---

## 5. Consequências

**Fica mais fácil**
- Todo evento novo nasce com garantia de entrega sem nenhum código a mais: é um `INSERT`.
- A publicação volta para dentro da transação, sem o custo que motivou tirá-la de lá: o `INSERT`
  na `outbox` é local, não espera broker.
- Duas pendências de `docs/event-contracts.md` §13.1 somem (a janela de perda e a republicação
  de `stock.reserved` em `ALREADY_RESERVED`).
- A `outbox` guarda o que foi emitido: dá para responder "esse evento saiu?" com um `SELECT` e
  republicar um intervalo depois de um incidente.

**Fica mais difícil**
- Mais um componente no compose e em produção: Kafka Connect com Debezium.
- O Postgres passa a exigir `wal_level=logical`, e o slot de replicação precisa de monitoramento.
- Os casos de uso que hoje publicam depois do commit precisam ser reestruturados (§6.4).

**Revisitar**
- Failover de Postgres em produção: slot de replicação precisa sobreviver à troca de primário
  (PG 17 tem *failover slots*).
- Se um dia a latência de CDC incomodar, nada muda no contrato — só a forma de ler a `outbox`.

---

## 6. Desenho

### 6.1 Fluxo

```
  caso de uso ── @Transactional ───────────────────────────────┐
     │  UPDATE orders SET status = 'cancelled' …               │
     │  INSERT INTO outbox (… 'ecommerce.order.cancelled.v1' …)│  um único commit
     └─────────────────────────────────────────────────────────┘
                                │
                           WAL (logical)
                                │  slot debezium_order · publicação debezium_order_outbox
                                ▼
            Kafka Connect ─ PostgresConnector ─ EventRouter (SMT)
                                │  tópico = coluna topic
                                │  key    = coluna aggregateid
                                │  header __TypeId__ = coluna type
                                │  valor  = coluna payload
                                ▼
                  ecommerce.order.cancelled.v1  ──►  inventory, shipment
```

### 6.2 Tabela `outbox`

Uma por banco, idêntica nos cinco serviços.

```sql
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
CREATE PUBLICATION debezium_order_outbox FOR TABLE outbox;

-- Heartbeat. Todos os bancos dividem o mesmo WAL do cluster: um banco sem
-- escrita segura o slot parado e o WAL dos outros cresce sem limite. O
-- Debezium atualiza esta linha periodicamente para o slot sempre andar.
CREATE TABLE debezium_heartbeat (
    id      SMALLINT    PRIMARY KEY,
    beat_at TIMESTAMPTZ NOT NULL
);
INSERT INTO debezium_heartbeat (id, beat_at) VALUES (1, now());
```

O nome da publicação muda por serviço (`debezium_<serviço>_outbox`). A migration é a próxima
versão livre de cada um:

| Serviço | Migration | Publicação | Slot |
|---|---|---|---|
| user | `V3__outbox.sql` | `debezium_user_outbox` | `debezium_user` |
| inventory | `V4__outbox.sql` | `debezium_inventory_outbox` | `debezium_inventory` |
| order | `V4__outbox.sql` | `debezium_order_outbox` | `debezium_order` |
| payment | `V2__outbox.sql` | `debezium_payment_outbox` | `debezium_payment` |
| shipment | `V3__outbox.sql` | `debezium_shipment_outbox` | `debezium_shipment` |

Slots são do **cluster**, não do banco: o nome precisa ser único entre os cinco.

### 6.3 Da linha à mensagem

| Coluna | Vira | Configuração do EventRouter |
|---|---|---|
| `topic` | tópico de destino | `route.by.field=topic` · `route.topic.replacement=${routedByValue}` |
| `aggregateid` | key (string) | `table.field.event.key=aggregateid` |
| `payload` | valor (bytes do JSON) | `table.field.event.payload=payload` + `StringConverter` |
| `type` | header `__TypeId__` | `table.fields.additional.placement=type:header:__TypeId__` |
| `id` | header `id` | `table.field.event.id=id` (padrão) |
| `aggregatetype`, `created_at` | nada | ficam só no banco, para consulta |

O header `__TypeId__` com o alias é o que o `JacksonJsonDeserializer` dos consumidores já lê via
`type.mapping` — por isso nenhum consumidor muda.

**Por que a coluna `topic` e não `aggregatetype`:** o roteamento padrão do EventRouter é
`outbox.event.<aggregatetype>`, um tópico por agregado. O contrato é um tópico por fato
(`order.created`, `order.cancelled`…). Guardar o tópico na linha deixa o roteamento explícito e
sem regra de conversão.

### 6.4 Lado da aplicação

#### 6.4.1 `OutboxWriter`

Um componente por serviço, em `ecommerce_event_driven.<serviço>.shared.outbox`. São ~40 linhas
repetidas em cinco serviços — aceito pela mesma razão de não haver módulo de contratos
compartilhado: acoplamento de build custaria mais do que a repetição.

```java
/**
 * Registra um evento para publicacao. Nao publica: grava na outbox, e o
 * Debezium publica depois do commit.
 *
 * <p>MANDATORY de proposito: chamado fora de transacao, estoura. Um evento
 * gravado em transacao propria voltaria a janela de perda que a outbox fecha.
 */
@Component
public class OutboxWriter {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(OutboxMessage message) {
        jdbc.sql("""
                insert into outbox (id, aggregatetype, aggregateid, topic, type, payload)
                values (:id, :aggregateType, :aggregateId, :topic, :type, cast(:payload as jsonb))
                """)
                .param("id", message.eventId())
                .param("aggregateType", message.aggregateType())
                .param("aggregateId", message.aggregateId())
                .param("topic", message.topic())
                .param("type", message.type())
                .param("payload", json.writeValueAsString(message.event()))
                .update();
    }
}

public record OutboxMessage(
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String topic,
        String type,
        Object event) {
}
```

`JdbcClient` e não entidade JPA: é um `INSERT` que nunca é lido pela aplicação. Com o
`JpaTransactionManager`, o `JdbcClient` participa da mesma transação e da mesma conexão.

#### 6.4.2 Adaptadores de saída

As **ports não mudam** — `OrderEventPublisherPort`, `StockEventPublisherPort` continuam sendo o
que o caso de uso enxerga. Muda o adaptador:

| Antes | Depois |
|---|---|
| `OrderEventKafkaPublisher` (`KafkaTemplate.send`) | `OrderEventOutboxPublisher` (`OutboxWriter.write`) |
| `StockEventKafkaPublisher` | `StockEventOutboxPublisher` |

As constantes de tópico e o alias saem da configuração do produtor
(`spring.kafka.producer.properties.spring.json.type.mapping`) e passam para o adaptador, que
preenche `topic` e `type` da linha.

#### 6.4.3 A publicação volta para dentro da transação

Esta é a mudança que exige cuidado. Hoje os casos de uso publicam **depois** do commit; com a
outbox, publicar é gravar, e gravar tem que estar **dentro**.

**`inventory` — reserva de estoque**

```java
// StockHoldTransaction (@Transactional)
Result hold(Long idOrder, List<Item> items) {
    if (reservations.existsByIdOrder(idOrder)) {
        return Result.ALREADY_RESERVED;       // o stock.reserved da 1a vez ja esta na outbox
    }
    // … reserva cada item, como hoje …
    publisher.publishStockReserved(idOrder);  // mesma transacao das reservas
    return Result.RESERVED;
}

// ReserveStockService
public Result execute(Long idOrder, List<Item> items) {
    try {
        return stockHold.hold(idOrder, items);
    } catch (InsufficientStockException ex) {
        // A transacao da reserva ja fez rollback. A recusa vai numa transacao
        // propria: se o processo cair entre as duas, a reentrega do
        // order.created reavalia do zero e recusa de novo.
        stockRejection.record(ex.getIdOrder(), ex.getIdProduct());
        return Result.REJECTED;
    }
}
```

`ALREADY_RESERVED` deixa de precisar republicar: não existe mais reserva commitada sem o
`stock.reserved` junto.

**`order` — cancelamento**

A publicação sai de `CancelOrderService` e entra em `OrderCancellationTransaction`, na mesma
transação do `UPDATE` de status. O `findById` que hoje busca o `idCustomer` depois do commit
passa para dentro também.

**Regra para todo caso de uso novo:** a chamada à port de publicação fica dentro do método
`@Transactional` que muda o estado. O `MANDATORY` do `OutboxWriter` faz o erro aparecer no
primeiro teste, não em produção.

#### 6.4.4 Configuração Kafka dos serviços

| Configuração | Antes | Depois |
|---|---|---|
| `producer.value-serializer` | eventos + DLT | só DLT |
| `producer.properties.spring.json.type.mapping` | uma linha por evento | **sai** — o alias está na coluna `type` |
| `consumer.*` | inalterado | inalterado |

### 6.5 Infraestrutura

#### 6.5.1 Postgres

```yaml
  db:
    image: postgres:17
    # logical: o WAL passa a carregar o suficiente para decodificar linhas.
    # Exige restart; o volume existente continua valendo.
    command: ["postgres", "-c", "wal_level=logical", "-c", "max_replication_slots=10", "-c", "max_wal_senders=10"]
```

Cinco slots (um por banco) cabem no limite de 10 com folga para diagnóstico.

#### 6.5.2 Kafka Connect com Debezium

```yaml
  # ------------------------------------------------------------------
  # Le a tabela outbox de cada banco pelo WAL e publica no Kafka.
  # Sem ports: a API REST do Connect nao tem autenticacao, e quem registra
  # conector por ela controla o que vai para o Kafka.
  # ------------------------------------------------------------------
  connect:
    image: quay.io/debezium/connect:3.x        # fixar a versão estável corrente, nunca latest
    environment:
      BOOTSTRAP_SERVERS: broker:9092
      GROUP_ID: debezium-outbox
      CONFIG_STORAGE_TOPIC: _connect-configs
      OFFSET_STORAGE_TOPIC: _connect-offsets
      STATUS_STORAGE_TOPIC: _connect-status
      CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR: 1
      CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR: 1
      CONNECT_STATUS_STORAGE_REPLICATION_FACTOR: 1
      # Senha fora do JSON do conector: ${env:VAR} e resolvido no worker.
      CONNECT_CONFIG_PROVIDERS: env
      CONNECT_CONFIG_PROVIDERS_ENV_CLASS: org.apache.kafka.common.config.provider.EnvVarConfigProvider
      DEBEZIUM_DB_USER: ${DEBEZIUM_DB_USER:-postgres}
      DEBEZIUM_DB_PASSWORD: ${DEBEZIUM_DB_PASSWORD:-postgres}
    healthcheck:
      test: ["CMD", "curl", "-fs", "http://localhost:8083/connectors"]
      interval: 10s
      timeout: 5s
      retries: 12
    depends_on:
      broker:
        condition: service_started
      db:
        condition: service_healthy

  # Registra os conectores e sai. PUT e idempotente: rodar de novo so atualiza.
  connect-init:
    image: curlimages/curl
    volumes:
      - ./debezium/connectors:/connectors:ro
      - ./debezium/register.sh:/register.sh:ro
    entrypoint: ["sh", "/register.sh"]
    depends_on:
      connect:
        condition: service_healthy
```

#### 6.5.3 Registro dos conectores

A publicação só existe depois que o Flyway do serviço rodou, e o compose não sabe quando isso
acontece (nenhum serviço tem actuator). Então o script registra e **insiste até cada conector
ficar `RUNNING`**, em vez de depender da ordem de subida:

```sh
#!/bin/sh
# Registra cada conector de /connectors e espera ele ficar RUNNING.
# O conector falha enquanto o servico nao rodou o Flyway (publicacao ainda
# nao existe); reiniciar ate dar certo e mais simples que orquestrar a subida.
set -eu
CONNECT=http://connect:8083

for file in /connectors/*.json; do
  name=$(basename "$file" .json)
  curl -fsS -X PUT -H "Content-Type: application/json" \
       --data @"$file" "$CONNECT/connectors/$name/config" > /dev/null

  for attempt in $(seq 1 30); do
    state=$(curl -fsS "$CONNECT/connectors/$name/status" | grep -o '"state":"[A-Z]*"' | tail -1)
    case "$state" in
      *RUNNING*) echo "$name: RUNNING"; break ;;
      *FAILED*)  curl -fsS -X POST "$CONNECT/connectors/$name/restart?includeTasks=true&onlyFailed=true" > /dev/null ;;
    esac
    [ "$attempt" = 30 ] && { echo "$name: nao subiu"; exit 1; }
    sleep 10
  done
done
```

#### 6.5.4 Configuração de um conector

`micro-services/debezium/connectors/order-outbox.json` — o nome do arquivo é o nome do conector.
Os outros quatro mudam só `dbname`, `topic.prefix`, `slot.name`, `publication.name` e o padrão
do predicado.

```json
{
  "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
  "plugin.name": "pgoutput",

  "database.hostname": "db",
  "database.port": "5432",
  "database.user": "${env:DEBEZIUM_DB_USER}",
  "database.password": "${env:DEBEZIUM_DB_PASSWORD}",
  "database.dbname": "order_db",

  "topic.prefix": "order-db",
  "slot.name": "debezium_order",
  "publication.name": "debezium_order_outbox",
  "publication.autocreate.mode": "disabled",
  "table.include.list": "public.outbox",

  "snapshot.mode": "no_data",
  "skipped.operations": "u,d,t",
  "tombstones.on.delete": "false",

  "heartbeat.interval.ms": "10000",
  "heartbeat.action.query": "UPDATE debezium_heartbeat SET beat_at = now() WHERE id = 1",

  "key.converter": "org.apache.kafka.connect.storage.StringConverter",
  "value.converter": "org.apache.kafka.connect.storage.StringConverter",

  "predicates": "isOutbox",
  "predicates.isOutbox.type": "org.apache.kafka.connect.transforms.predicates.TopicNameMatches",
  "predicates.isOutbox.pattern": "order-db\\.public\\.outbox",

  "transforms": "outbox",
  "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
  "transforms.outbox.predicate": "isOutbox",
  "transforms.outbox.table.field.event.id": "id",
  "transforms.outbox.table.field.event.key": "aggregateid",
  "transforms.outbox.table.field.event.payload": "payload",
  "transforms.outbox.route.by.field": "topic",
  "transforms.outbox.route.topic.replacement": "${routedByValue}",
  "transforms.outbox.table.fields.additional.placement": "type:header:__TypeId__"
}
```

| Propriedade | Por quê |
|---|---|
| `publication.autocreate.mode=disabled` | a publicação nasce na migration (§6.2); o usuário do Debezium não precisa ser dono da tabela |
| `snapshot.mode=no_data` | não relê a `outbox` inteira ao subir: começa do ponto em que o slot foi criado. Um snapshot republicaria todo evento ainda guardado |
| `skipped.operations=u,d,t` | só `INSERT` vira mensagem. A limpeza (§6.7) gera `DELETE` no WAL, e ele não pode virar evento |
| `tombstones.on.delete=false` | mesma razão: nenhum tombstone nos tópicos de evento |
| `heartbeat.*` | mantém o slot andando em banco sem escrita (§6.2) |
| `StringConverter` na key e no valor | a key sai como a string `"3301"`, igual à do `KafkaTemplate` de hoje; o valor sai como os bytes do JSON da coluna, que é o que o `JacksonJsonDeserializer` espera |
| predicado `isOutbox` | o EventRouter só se aplica às linhas da `outbox`; as mensagens de heartbeat passam sem ele |

### 6.6 Garantias

| Propriedade | Como fica |
|---|---|
| **Atomicidade** | evento existe ⇔ transação commitou |
| **Ordem** | o WAL entrega na ordem de commit; a key por `aggregateid` mantém a ordem por pedido na partição. Mesma garantia de hoje, sem depender de quem chamou `send` primeiro |
| **Duplicatas** | at-least-once: se o Connect cair antes de gravar o offset, reenvia a partir do último offset. Os consumidores já são idempotentes (`docs/event-contracts.md` §2.6) |
| **Latência** | dezenas de ms entre o commit e o tópico |
| **Kafka fora** | o Connect segura e tenta de novo; o serviço continua funcionando, os eventos só atrasam |
| **Connect fora** | idem: as linhas esperam na `outbox` e o WAL fica retido no slot até ele voltar |

### 6.7 Retenção e limpeza

As linhas **não** são apagadas na mesma transação que as grava (outro padrão comum do
Debezium). Ficam **7 dias**, porque é o que permite responder "esse evento saiu?" e republicar
um intervalo depois de um incidente.

```java
// Um por servico. Roda fora do horario de pico.
@Scheduled(cron = "0 30 3 * * *")
@Transactional
public void purge() {
    jdbc.sql("delete from outbox where created_at < now() - interval '7 days'").update();
}
```

O `DELETE` aparece no WAL, e o `skipped.operations=u,d,t` do conector o descarta.

### 6.8 Operação

**O que monitorar**

| Sinal | Onde | Alerta quando |
|---|---|---|
| Estado do conector | `GET /connectors/{nome}/status` | `FAILED` ou `PAUSED` |
| WAL retido pelo slot | `SELECT slot_name, pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn)) FROM pg_replication_slots;` | acima de 1 GB |
| Atraso do conector | métrica JMX `MilliSecondsBehindSource` | acima de 30 s |
| Tamanho da `outbox` | `SELECT count(*) FROM outbox` | crescendo sem parar (limpeza parada) |

**Modos de falha**

| Falha | Efeito | Recuperação |
|---|---|---|
| Connect parado | eventos atrasam; WAL cresce | subir o Connect: retoma do offset gravado |
| Conector `FAILED` | idem | ler o erro no `status`; `POST /connectors/{nome}/restart?includeTasks=true` |
| Slot removido (restore de backup, recriação do banco) | eventos entre a perda e a recriação **não saem** | recriar o slot e republicar da `outbox` o intervalo afetado — é para isso que as linhas ficam 7 dias |
| Disco do Postgres cheio de WAL | o banco para | é o alerta de WAL retido que evita isto; em último caso, remover o slot e seguir o item acima |
| Mudança de schema da `outbox` | o conector pode falhar ao decodificar | a tabela é estável por desenho; mudança nela é migration coordenada com o conector |

### 6.9 Segurança

- A API REST do Connect (`:8083`) **não é publicada** — mesma regra dos microsserviços.
- Em dev o Debezium usa `postgres`. Em produção, um papel próprio com o mínimo:

```sql
CREATE ROLE debezium WITH LOGIN REPLICATION PASSWORD '…';
GRANT CONNECT ON DATABASE order_db TO debezium;
GRANT SELECT ON outbox TO debezium;
GRANT UPDATE ON debezium_heartbeat TO debezium;
```

- A senha chega ao conector por `${env:…}`, nunca escrita no JSON versionado.

---

## 7. Plano de implementação

Cada fase termina com algo verificável e não deixa o sistema quebrado no meio.

### Fase 1 — Infraestrutura

- [ ] `wal_level=logical` e limites de slot no serviço `db` do compose (§6.5.1)
- [ ] Serviços `connect` e `connect-init` no compose (§6.5.2)
- [ ] `micro-services/debezium/register.sh` e os cinco `connectors/*.json` (§6.5.3, §6.5.4)

**Pronto quando:** `docker compose up` sobe o Connect saudável; os conectores falham por falta
da publicação (esperado até a fase 2) e o `connect-init` insiste.

### Fase 2 — Tabela `outbox` nos cinco bancos

- [ ] Migration de outbox + publicação + heartbeat em cada serviço (§6.2)

**Pronto quando:** os cinco conectores ficam `RUNNING` e `pg_replication_slots` mostra cinco
slots ativos. Um `INSERT` manual numa `outbox` aparece no tópico indicado na coluna `topic`,
com a key e o header `__TypeId__` corretos (§8.2).

### Fase 3 — `OutboxWriter` e limpeza

- [ ] `shared.outbox.OutboxWriter` + `OutboxMessage` nos cinco serviços (§6.4.1)
- [ ] Job de limpeza em cada serviço (§6.7) + `@EnableScheduling`

**Pronto quando:** teste de integração da §8.1 passa em pelo menos um serviço.

### Fase 4 — Migrar os produtores existentes

Troca **inteira por serviço**, num único deploy: o adaptador de Kafka sai e o de outbox entra no
mesmo commit. Nunca os dois ativos ao mesmo tempo — seria publicação dupla.

- [ ] `inventory`: `StockEventOutboxPublisher`; publicação para dentro de `StockHoldTransaction`;
      recusa numa transação própria (§6.4.3)
- [ ] `order`: `OrderEventOutboxPublisher`; publicação para dentro de
      `OrderCancellationTransaction`; `order.created` dentro da transação do checkout
- [ ] Remover `StockEventKafkaPublisher`, `OrderEventKafkaPublisher` e as linhas de
      `producer…type.mapping` dos dois `application.properties`

**Pronto quando:** a saga de reserva (`docs/event-contracts.md` §10.1 e §10.2) roda ponta a
ponta com os consumidores **sem nenhuma alteração** neles.

### Fase 5 — Eventos novos

- [ ] Todo evento de `docs/event-contracts.md` marcado `PLANEJADO` já nasce pela outbox
- [ ] `payment`, `shipment` e `user` ganham o bloco de consumidor Kafka (só consomem pelo Kafka;
      produzem pela outbox)

### Fase 6 — Operação

- [ ] Consulta de WAL retido e estado dos conectores num script de diagnóstico
      (`micro-services/debezium/status.sh`)
- [ ] Alertas da §6.8 quando houver stack de monitoramento

---

## 8. Verificação

### 8.1 Teste de integração por serviço

Testcontainers com Postgres (`wal_level=logical`), Kafka e o container do Debezium
(`io.debezium:debezium-testing-testcontainers`), registrando o mesmo JSON de
`debezium/connectors/`:

| Cenário | Esperado |
|---|---|
| caso de uso commita | mensagem no tópico, com key, `__TypeId__` e payload que o consumidor real desserializa no record |
| caso de uso faz rollback | nenhuma mensagem |
| dois eventos do mesmo pedido na mesma transação | chegam na ordem em que foram gravados |
| `OutboxWriter.write` fora de transação | `IllegalTransactionStateException` |
| linha apagada pela limpeza | nenhuma mensagem, nenhum tombstone |

### 8.2 Verificação manual

```bash
# insere um evento a mao
docker exec -it micro-services-db-1 psql -U postgres -d order_db -c \
 "insert into outbox (id, aggregatetype, aggregateid, topic, type, payload)
  values (gen_random_uuid(), 'order', '3301', 'ecommerce.order.cancelled.v1', 'orderCancelled',
          '{\"eventId\":\"x\",\"producedAt\":\"2026-09-18T12:00:00Z\",\"orderId\":3301,\"customerId\":42,\"reason\":\"teste\"}');"

# le com key e headers
docker exec -it broker /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic ecommerce.order.cancelled.v1 --from-beginning \
  --property print.key=true --property print.headers=true
```

Esperado: key `3301`, header `__TypeId__:orderCancelled`, valor igual ao `payload`.

---

## 9. Pontos a confirmar na implementação

Itens que dependem de comportamento de versão e precisam ser provados no teste da §8.1 antes de
seguir para a fase 4:

1. **Formato do header `__TypeId__`.** O `SimpleHeaderConverter` padrão do Connect deve gravar a
   string sem aspas (`orderCancelled`, não `"orderCancelled"`). Com aspas, o `type.mapping` dos
   consumidores não casa. Se vier com aspas, configurar `header.converter` com
   `StringConverter` no conector.
2. **JSON idêntico ao de hoje.** O `payload` é gerado pelo `JsonMapper` do Spring Boot, e hoje o
   JSON sai do `JacksonJsonSerializer` do spring-kafka. Conferir que `Instant` sai como string
   ISO e `BigDecimal` como número nos dois — são mappers configurados separadamente.
3. **`jsonb` normaliza o texto.** A ordem das chaves e os espaços do payload saem diferentes do
   que foi gravado. Irrelevante para quem desserializa JSON; só importa se alguém comparar
   bytes.
4. **`skipped.operations` com `pgoutput`.** Confirmar que o `DELETE` da limpeza não gera
   mensagem nem tombstone.
5. **Versão do Debezium.** Fixar uma 3.x estável com suporte declarado a Postgres 17 e ao Kafka
   da imagem `apache/kafka` usada no compose (que hoje também está em `latest` e deveria ser
   fixada).

---

## 10. Impacto nos outros documentos

| Documento | Mudança |
|---|---|
| `docs/event-contracts.md` §2.3 | `producedAt` passa a ser o instante da gravação na `outbox`, dentro da transação |
| `docs/event-contracts.md` §2.6 | publicação via outbox + Debezium, na mesma transação |
| `docs/event-contracts.md` §9 | as linhas de `producer…type.mapping` saem: o alias viaja na coluna `type` |
| `docs/event-contracts.md` §13.1 | itens 1 e 2 resolvidos por esta decisão |
| `docs/kafka.md` §3.3 | o adaptador com `KafkaTemplate` deixa de ser o padrão para evento de domínio |
