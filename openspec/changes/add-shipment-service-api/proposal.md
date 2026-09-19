# Proposal

## Why

O `shipment` tem só a tabela e o repositório. Sem frete o checkout não fecha, e sem envio o
pedido pago nunca chega a `delivered` nem libera a avaliação.

## What Changes

- Frete `GET /shipping/quote?zipcode=`: `ceil(km) × R$ 1,00` a partir do endereço da loja em
  configuração; destino geocodificado na BrasilAPI v2, com **fallback na Nominatim
  (OpenStreetMap)** por cidade/UF quando o CEP não tem coordenada; cache Redis de 30 dias.
- Envios: `GET /shipments`, `GET /shipments/{id}`, `GET /shipments/manage`,
  `PATCH /shipments/{id}` (loja despacha), `POST /shipments/{id}/confirm-delivery` (comprador),
  `POST /shipments/{id}/cancel` (loja), `POST /internal/shipments` (contingência).
- Consumidores: `order.confirmed` cria o envio (snapshot do destino via `user`, origem da
  configuração); `order.cancelled` cancela envio não despachado.
- Evento `shipment.status.changed` em toda transição, pela outbox.
- **Confirmação automática** de entrega 15 dias depois de `in_transit`.

## Capabilities

### New Capabilities
- `shipping-quote`: cálculo de frete por distância a partir da loja.
- `shipment-lifecycle`: criação, despacho, entrega, cancelamento e consulta de envios, e o evento de mudança de status.

### Modified Capabilities

## Impact

- `micro-services/shipment/**` apenas. Migrations `V3__outbox.sql`, `V4__shipment_details.sql`.
- Dependências externas: BrasilAPI e Nominatim (HTTP público, sem chave).
- Consumidores do frete: `order` (checkout) e o front (vitrine).
