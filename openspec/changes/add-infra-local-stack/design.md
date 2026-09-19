# Design

## Context

O compose já tem `broker` (KRaft, listeners INTERNAL `broker:9092` e HOST `localhost:29092`),
`db` (Postgres 17 com `db/init/01-create-databases.sql`), `redis`, `api-gateway` e os cinco
serviços. Só o gateway publica porta de aplicação (`db` e `broker` publicam portas só para
desenvolvimento local). Referência obrigatória: `docs/outbox-debezium.md` §6.5 e §6.2.

## Goals / Non-Goals

**Goals:**
- `docker compose up --build` sobe tudo, e os conectores ficam `RUNNING` sem passo manual.
- Toda variável que um serviço lê tem valor no compose e está documentada no `.env.example`.

**Non-Goals:**
- Kubernetes, CI, observabilidade (Prometheus/Grafana).

## Decisions

### Contrato de variáveis de ambiente (os serviços leem exatamente estes nomes)

| Variável | Serviços | Valor no compose |
|---|---|---|
| `KAFKA_BOOTSTRAP` | todos exceto gateway | `broker:9092` |
| `REDIS_HOST` | gateway, inventory, order, payment, shipment | `redis` |
| `DB_URL` | user, inventory, order, payment, shipment | `jdbc:postgresql://db:5432/<svc>_db` |
| `JWKS_URI` | user, inventory, order, payment, shipment | `http://api-gateway:8080/.well-known/jwks.json` |
| `GATEWAY_URL` | user, inventory, order, payment, shipment | `http://api-gateway:8080` |
| `SERVICE_CLIENT_SECRET` | user, inventory, order, payment, shipment | `${<SVC>_CLIENT_SECRET:-dev-<svc>-secret}` |
| `USER_SERVICE_URL` | gateway, inventory, order, shipment | `http://user:8081` |
| `INVENTORY_SERVICE_URL` | gateway, order | `http://inventory:8082` |
| `ORDER_SERVICE_URL` | gateway, payment | `http://order:8083` |
| `PAYMENT_SERVICE_URL` | gateway | `http://payment:8084` |
| `SHIPMENT_SERVICE_URL` | gateway, order | `http://shipment:8085` |
| `USER_CLIENT_SECRET` … `SHIPMENT_CLIENT_SECRET` | gateway (valida os clientes) | mesmo default `dev-<svc>-secret` |
| `STORE_OWNER_EMAIL` | user | `${STORE_OWNER_EMAIL}` (obrigatória) |
| `MP_ACCESS_TOKEN`, `MP_PUBLIC_KEY`, `MP_NOTIFICATION_URL` | payment | do `.env` |
| `MP_WEBHOOK_SECRET` | gateway | do `.env` |
| `FRONT_URL` | gateway | `${FRONT_URL:-http://localhost:3000}` |

As variáveis antigas `SPRING_DATASOURCE_URL`, `SPRING_KAFKA_BOOTSTRAP_SERVERS` e
`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` saem do compose: cada serviço passa a
ler os nomes curtos acima com default local.

### Versões fixas
Escolher a tag estável mais recente **que exista** no registry, conferindo por HTTP antes de
fixar: `https://hub.docker.com/v2/repositories/apache/kafka/tags?page_size=25`,
`https://quay.io/api/v1/repository/debezium/connect/tag/?limit=25&onlyActiveTags=true`,
`redis:7.4-alpine`, `postgres:17`. Debezium 3.x.

### Conectores
Um arquivo por banco em `micro-services/debezium/connectors/<svc>-outbox.json`, só o mapa de
config (o `register.sh` faz `PUT /connectors/<nome>/config`). Valores por serviço:
`database.dbname=<svc>_db`, `topic.prefix=<svc>-db`, `slot.name=debezium_<svc>`,
`publication.name=debezium_<svc>_outbox`, predicado `<svc>-db\\.public\\.outbox`. Todo o resto
idêntico ao exemplo de `docs/outbox-debezium.md` §6.5.4.

### Dependências de subida
Serviços dependem de `db` (healthy), `broker` e `redis`. `connect` depende de `db` e `broker`;
`connect-init` de `connect` healthy. Não há dependência dos serviços para o `connect-init`: o
script insiste até o Flyway de cada serviço criar a publicação.

## Risks / Trade-offs

- [Tag inexistente quebra o `compose up`] → conferir a tag no registry antes de fixar.
- [`curl` ausente na imagem do Connect quebra o healthcheck] → conferir; se ausente, usar
  `bash -c 'exec 3<>/dev/tcp/localhost/8083'` como teste.
- [`wal_level` só vale após restart] → o `command:` do serviço `db` aplica em toda subida.
