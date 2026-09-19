# Proposal

## Why

O `payment` tem só a tabela e o repositório. Sem cobrança, o pedido nunca sai de `pending`, e a
saga inteira (baixa de estoque, envio, entrega) fica inalcançável.

## What Changes

- Integração com o Mercado Pago: PIX (QR Code), cartão tokenizado no front e Checkout Pro.
- Rotas de `docs/api-contracts.md` §9: `GET /payments/config`, `GET /payments/methods`,
  `GET /payments/{id}`, `GET /payments?orderId=`, `GET /internal/payments?orderId=`,
  `POST /payments` (idempotente), `POST /payments/{id}/refund`, `/sync`, `/cancel`,
  `POST /webhooks/mercadopago`.
- Webhook: deduplicação, releitura da verdade no MP e mapeamento de status (§9.2).
- Eventos pela outbox: `payment.approved`, `payment.failed`, `payment.refunded`.
- Consumidor de `order.refund.requested` com estorno idempotente no MP.
- **Modo fake**: sem `MP_ACCESS_TOKEN`, um cliente falso aprova toda cobrança na hora, para a
  saga rodar localmente sem conta no Mercado Pago.
- Colunas novas no pagamento (método, detalhe, QR, valores estornados).

## Capabilities

### New Capabilities
- `payment-processing`: criação, consulta, cancelamento e sincronização de cobranças no Mercado Pago.
- `payment-refund`: estorno manual pela loja e automático por evento.
- `payment-notifications`: processamento dos webhooks do Mercado Pago e publicação dos eventos de pagamento.

### Modified Capabilities

## Impact

- `micro-services/payment/**` apenas. Migrations `V2__outbox.sql`, `V3__payment_details.sql`.
- Depende de `order GET /internal/orders/{id}` e do repasse de webhook do gateway.
- Dependência externa: API do Mercado Pago (`https://api.mercadopago.com`).
