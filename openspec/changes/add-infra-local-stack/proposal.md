# Proposal

## Why

O compose atual sobe banco, Kafka, Redis e os serviços, mas não sobe o Debezium que publica a
outbox (ADR-001), não passa Kafka/Redis/URLs para metade dos serviços e usa imagens `latest`.
Sem isso nenhuma saga roda ponta a ponta, e o projeto não tem um README que explique a
arquitetura para quem chega pelo portfólio.

## What Changes

- Postgres com `wal_level=logical` e limites de slot para o Debezium.
- Serviços `connect` (Kafka Connect + Debezium) e `connect-init` (registra os conectores e
  insiste até ficarem `RUNNING`), sem porta publicada.
- `micro-services/debezium/`: `register.sh` e um JSON de conector por banco produtor
  (user, inventory, order, payment, shipment), conforme `docs/outbox-debezium.md` §6.5.
- Variáveis de ambiente de todos os serviços no compose: Kafka, Redis, URL do gateway,
  segredo de cliente de serviço, URLs internas, Mercado Pago, e-mail do dono da loja.
- Imagens com versão fixa (Kafka, Debezium, Redis, Postgres).
- `micro-services/.env.example` e `micro-services/api-gateway/.env.example` documentando cada
  variável (os `.env` reais continuam fora do git).
- `README.md` na raiz, em nível de portfólio: visão geral, arquitetura, saga, stack, como rodar,
  onde estão os contratos.

## Capabilities

### New Capabilities
- `local-environment`: ambiente local completo via Docker Compose, incluindo o pipeline outbox → Debezium → Kafka e o isolamento de rede dos serviços internos.

### Modified Capabilities

## Impact

- `micro-services/docker-compose.yaml`, `micro-services/debezium/**`, `micro-services/.env.example`,
  `micro-services/api-gateway/.env.example`, `README.md`.
- Nenhum código Java. Os nomes de variáveis de ambiente definidos aqui são o contrato que os
  `application.properties` de cada serviço leem (ver `design.md`).
