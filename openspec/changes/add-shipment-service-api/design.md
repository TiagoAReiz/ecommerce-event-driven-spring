# Design

## Context

Existe em `micro-services/shipment`: `Shipment` (com `idAddressUser`, `destination`, `origin`
como `AddressSnapshot` sem id), `ShipmentStatus`, entidade (`to_*`, `from_*`), repositório e
adapter. `shipment_order_uk` garante um envio por pedido. Contratos: `docs/api-contracts.md` §10,
`docs/event-contracts.md` §4.2, §4.4, §7.1.

## Goals / Non-Goals

**Goals:** frete e ciclo de vida do envio completos, orientados por evento.

**Non-Goals:** transportadora, etiqueta, rastreio automático.

## Decisions

### D1. Geocodificação com fallback
`GeocodingPort.locate(cep)` → `{lat, lon, city, state}`. Ordem: cache Redis `geo:cep:{cep}`
(30 d) → BrasilAPI `GET https://brasilapi.com.br/api/cep/v2/{cep}` (`location.coordinates`) →
se sem coordenada, Nominatim `GET https://nominatim.openstreetmap.org/search?city={city}&state={state}&country=Brazil&format=json&limit=1`
com header `User-Agent: ecommerce-event-driven/1.0`. Nenhuma coordenada → 422
`ZIPCODE_NOT_GEOCODED`; CEP inexistente na BrasilAPI (404) → 422 `ZIPCODE_NOT_FOUND`; APIs fora
e cache frio → 503; timeout (5 s) → 504.
Por quê: a BrasilAPI não tem coordenada para muitos CEPs, e sem fallback o checkout travaria.

### D2. Frete
`distanceKm = ceil(haversine(origem, destino))`, `freightCost = distanceKm × app.shipping.rate-per-km`.
Origem: `app.store.origin.{zipcode,country,state,city,street,number,latitude,longitude}` (defaults
de Guarulhos/SP de `docs/api-contracts.md` §10.1), validada no boot (falta → não sobe).

### D3. Ciclo de vida
Transições de `docs/api-contracts.md` §10.2, num `ShipmentStateMachine`. Toda transição (inclusive
a criação, `from=null`) grava `shipment.status.changed` na outbox na mesma transação
(`aggregateType=shipment`, key = orderId). `delivered` só por `confirm-delivery` (comprador) ou pelo
job D5; `PATCH` com `status=delivered` → 403.

### D4. Consumidores
- `order.confirmed` (`OrderConfirmedConsumer`): se não há envio do pedido, busca o destino em
  `user GET /internal/addresses/{addressId}?userId={customerId}` com token de serviço, copia para
  `to_*`, origem da configuração para `from_*`, `freight_tax = freightCost`, grava `pending`.
  Já existe → ignora. `user` 404 → `InvalidEventException` (DLT). 5xx/timeout → retry.
- `order.cancelled` (`OrderCancelledConsumer`): `pending|ready_to_ship` → `cancelled` com o
  motivo; sem envio ou já cancelado → ignora; `in_transit` ou além → log WARN, nada muda.

### D5. Confirmação automática
`DeliveryAutoConfirmJob` (`@Scheduled(cron = "0 0 4 * * *")`): envios em `in_transit` ou
`out_for_delivery` há mais de 15 dias (`updated_at`) vão para `delivered` pelo mesmo caminho de D3.

### D6. Migration `V4__shipment_details.sql`
`ALTER TABLE shipment ADD COLUMN cancel_reason TEXT;`

### D7. Segurança e posse
`GET /shipping/quote` → `shipments:read` ou `internal:hydrate`; `GET /shipments`,
`GET /shipments/{id}` → `shipments:read`; `GET /shipments/manage` → `sales:read`;
`POST /shipments/{id}/confirm-delivery`, `PATCH /shipments/{id}`, `POST /shipments/{id}/cancel` →
`shipments:write`, e as duas últimas conferem `roles` contém `owner` (senão 403);
`/internal/**` → `internal:hydrate`. Leitura de envio de outro comprador (não owner) → 404.

## Risks / Trade-offs

- [Nominatim tem limite de 1 req/s] → cache de 30 dias por CEP; volume de uma loja é baixo.
- [Linha reta subestima a estrada] → decisão de produto já registrada.

## Decisoes de implementacao

- **RestClient para chamadas HTTP**: usamos RestClient (nao RestTemplate) como indicado na stack. Chamadas de serviço (user, gateway) usam ServiceTokenProvider para autenticacao com token interno.
- **SQLException em transaction.propagation MANDATORY**: OutboxWriter relanca JsonProcessingException; os consumidores Kafka enrolam em try-catch nas operacoes de EventPublisher. Nao e esperado erro aqui, mas evita code smell.
- **GET /shipments, GET /shipments/{id}, GET /shipments/manage**: implementados com queries JPA parametrizadas. Filtros por status (repetivel) e orderId opcionais. GET /shipments retorna lista paginada dos envios do usuario autenticado; GET /shipments/{id} retorna detalhe completo com destination e origin; GET /shipments/manage retorna todos os envios com permissao owner. DTOs ShipmentResponse (resumido) e ShipmentDetailedResponse (completo) com @JsonFormat(shape=STRING) para BigDecimal conforme spec.
- **Transicoes PATCH e confirmacoes**: PATCH /shipments/{id} valida transicoes com ShipmentStateMachine; POST /shipments/{id}/confirm-delivery e POST /shipments/{id}/cancel implementadas com lógica completa, verificam autorização (comprador vs owner) e movem status com event publishing.
- **DeliveryAutoConfirmJob**: implementado com findExpiredInTransit() no repositório que busca shipments com status in_transit ou out_for_delivery e updated_at anterior a 15 dias; transição para delivered pelo mesmo caminho das outras, com event publishing via ShipmentEventOutboxPublisher.
- **OrderConfirmedConsumer**: se user service retorna 404 no endereco, InvalidEventException vai pra DLT (esperado, per docs/event-contracts 4.4). Se timeout ou 5xx, o DefaultErrorHandler tenta retry.
- **GeocodingAdapter**: cai para Nominatim sem coordenadas da BrasilAPI. Se ambas falham, lanca UnprocessableException (422). Cache Redis usa StringRedisTemplate com try-catch: falha de Redis nao trava a rota (log WARN, segue).
- **StoreOriginProperties**: todos os campos sao @NotBlank/@NotNull. Se falta no boot, a aplicacao nao sobe (por design, D2).
- **SecurityConfig**: usa @EnableMethodSecurity pra suportar @PreAuthorize nos controllers. Todos os endpoints exigem token valido (audiences=internal); permissoes sao por escopo/papel nos controllers.
