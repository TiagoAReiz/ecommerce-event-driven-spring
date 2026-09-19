# Tasks

## 1. Plataforma

- [x] 1.1 Adicionar `spring-boot-starter-validation` e `spring-boot-starter-data-redis` ao `micro-services/shipment/pom.xml`; trocar `application.properties` para os nomes de env do config com `server.port=8085`, bloco Kafka (consumer + producer da DLT), `app.store.origin.*` e `app.shipping.rate-per-km=1.00`; verificar compile
- [x] 1.2 Criar `shared/web/*`, `shared/security/CurrentUser`, `shared/client/ServiceTokenProvider` e `RestClient` para o `user`; verificar compile
- [x] 1.3 Criar `V3__outbox.sql` (publicação `debezium_shipment_outbox`), `V4__shipment_details.sql` e `shared/outbox/*` com `@EnableScheduling`; atualizar `Shipment`/entidade/mapper com `cancelReason`; verificar compile
- [x] 1.4 Criar `config/KafkaConfig` (error handler + DLT, `InvalidEventException`, `NewTopic` para `ecommerce.shipment.status.changed.v1` e `-dlt`, `type.mapping` de consumidor de `orderConfirmed` e `orderCancelled`); verificar compile

## 2. Frete

- [x] 2.1 Criar `GeocodingPort` com adaptadores BrasilAPI + Nominatim + cache Redis (design D1) e `StoreOriginProperties` validadas no boot (design D2); verificar compile
- [x] 2.2 Criar `QuoteShippingService` e `GET /shipping/quote?zipcode=` com a resposta e os códigos de `docs/api-contracts.md` §10.3; verificar compile

## 3. Ciclo de vida

- [x] 3.1 Criar `ShipmentStateMachine`, `ShipmentEventOutboxPublisher` e o record `ShipmentStatusChangedEvent` (design D3); verificar compile
- [x] 3.2 Rotas `GET /shipments`, `GET /shipments/{id}`, `GET /shipments/manage`, `PATCH /shipments/{id}`, `POST /shipments/{id}/confirm-delivery`, `POST /shipments/{id}/cancel`, `POST /internal/shipments` com os códigos de `docs/api-contracts.md` §10; verificar compile

## 4. Consumidores e job

- [x] 4.1 Criar `OrderConfirmedConsumer` e `OrderCancelledConsumer` (design D4); verificar compile
- [x] 4.2 Criar `DeliveryAutoConfirmJob` (design D5); verificar compile

## 5. Segurança

- [x] 5.1 Atualizar `config/SecurityConfig` com as regras do design D7; verificar compile

## 6. Verificação

- [x] 6.1 Rodar `mvn -q -DskipTests compile` em `micro-services/shipment` sem erro
