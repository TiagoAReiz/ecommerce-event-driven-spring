# Tasks

## 1. Plataforma

- [x] 1.1 Adicionar `spring-boot-starter-validation` e `spring-boot-starter-data-redis` ao `micro-services/payment/pom.xml`; trocar `application.properties` para os nomes de env do config com `server.port=8084`, bloco Kafka (consumer + producer da DLT) e `app.mercadopago.*`; verificar compile
- [x] 1.2 Criar `shared/web/*`, `shared/security/CurrentUser`, `shared/client/ServiceTokenProvider` e `RestClient` para o `order`; verificar compile
- [x] 1.3 Criar `V2__outbox.sql` (publicação `debezium_payment_outbox`) e `shared/outbox/*` com `@EnableScheduling`; verificar compile
- [x] 1.4 Criar `config/KafkaConfig` (error handler + DLT, `InvalidEventException`, `NewTopic` para `ecommerce.payment.{approved,failed,refunded}.v1` e seus `-dlt`, `type.mapping` de consumidor de `orderRefundRequested`); verificar compile

## 2. Modelo

- [x] 2.1 Criar `V3__payment_details.sql` e estender `Payment`, `PaymentEntity`, `PaymentMapper`, repositório (busca por pedido, por chave de idempotência, por external id) (design D1); verificar compile

## 3. Provedor

- [x] 3.1 Criar `PaymentGatewayPort`, `MercadoPagoClient` e `FakePaymentGateway` com a seleção por configuração (design D2); verificar compile
- [x] 3.2 Criar `MercadoPagoStatusMapper` e `PaymentEventOutboxPublisher` (records `PaymentApprovedEvent`, `PaymentFailedEvent`, `PaymentRefundedEvent` de `docs/event-contracts.md` §6) (design D4); verificar compile

## 4. Rotas

- [x] 4.1 `POST /payments` (design D3) com os três corpos de `docs/api-contracts.md` §9.4; verificar compile
- [x] 4.2 `GET /payments/{id}`, `GET /payments?orderId=`, `GET /internal/payments?orderId=`, `GET /payments/config`, `GET /payments/methods` (design D7); verificar compile
- [x] 4.3 `POST /payments/{id}/sync` e `POST /payments/{id}/cancel`; verificar compile
- [x] 4.4 `POST /payments/{id}/refund` (design D6); verificar compile
- [x] 4.5 `POST /webhooks/mercadopago` (design D5); verificar compile

## 5. Consumidor

- [x] 5.1 Criar `OrderRefundRequestedConsumer` (`@KafkaListener` em `ecommerce.order.refund.requested.v1`, grupo `payment`) com os resultados de `docs/event-contracts.md` §4.6; verificar compile

## 6. Segurança

- [x] 6.1 Atualizar `config/SecurityConfig` com as regras do design D8; verificar compile

## 7. Verificação

- [x] 7.1 Rodar `mvn -q -DskipTests compile` em `micro-services/payment` sem erro
