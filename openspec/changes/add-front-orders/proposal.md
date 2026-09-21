# Proposal

## Why

O cliente compra, paga, recebe — mas hoje não consegue acompanhar nada pelo front. Sem telas de
pedidos, envios e avaliações, a jornada do comprador termina no checkout. Precisa de lista de
pedidos com status dinâmico, detalhe com linha do tempo e valores, acompanhamento do envio,
confirmação de entrega e sistema completo de avaliações.

## What Changes

- **Tela de pedidos do comprador:** lista com filtros (status, período), paginação e detalhe
  com itens, valores, payment, shipment e timeline de status.
- **Cancelamento de pedido:** modal com motivo, validação de `409` (status não permite),
  transições `pending` → `cancelled`, `paid` → `cancelled` com estorno assíncrono.
- **Acompanhamento de envio:** lista de envios, detalhe com origem/destino, código de
  rastreamento, status; botão "Confirmar entrega" (com confirmação, pois é ação do comprador).
- **Sistema de avaliações:** tela de avaliações pendentes (produtos que pode avaliar),
  criar/editar/deletar avaliação com janela de 30 dias, "minhas avaliações", tratamento de
  `409` após janela expirada.
- **Revalidação de estado:** enquanto o status não for final (`delivered`, `cancelled`,
  `refunded`), repolling automático a cada 30 s; para quando fim de vida.

## Capabilities

### New Capabilities
- `orders-management`: lista de pedidos do comprador com status, filtros e paginação; detalhe
  com itens, valores, projeções de pagamento e envio, timeline de status.
- `order-cancellation`: cancelamento de pedido com motivo, validação de estados e
  tratamento de `409`.
- `shipment-tracking`: lista e detalhe de envios do comprador, código de rastreamento,
  confirmação de entrega pelo comprador.
- `reviews-management`: avaliações pendentes, criar/editar/deletar avaliação com janela de
  30 dias, "minhas avaliações", moderação de `409`.

### Modified Capabilities

## Impact

- `front/src/features/orders/**` e `front/src/features/reviews/**`. Nova estrutura de
  rotas com React Router data router.
- Depende de `add-order-cart-checkout` e `add-order-saga-orchestration` (rotas HTTP
  estabilizadas, eventos de saga).
- Stack: Vite + React + TypeScript, React Router, TanStack Query, Tailwind CSS. Porta 3000.
- Design: branco/azul, azul principal `#1d4ed8`, hover `#1e40af`, destaque `#eff6ff`.
