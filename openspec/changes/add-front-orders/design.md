# Design

## Context

Após `add-order-cart-checkout` e `add-order-saga-orchestration`: rotas HTTP de pedidos estabilizadas
em `GET /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancel`; projeções de `payment` e
`shipment` no `order`; rotas de envio `GET /shipments`, `GET /shipments/{id}`,
`POST /shipments/{id}/confirm-delivery`; rotas de avaliação `GET /reviews/pending`,
`GET /reviews/mine`, `POST /products/{id}/reviews`, `PATCH /reviews/{id}`, `DELETE /reviews/{id}`.

Stack React + TS: Vite, React Router v6 com data loader, TanStack Query v5, Tailwind CSS.
Design tokens já definidos: azul `#1d4ed8`, hover `#1e40af`, destaque `#eff6ff`,
superfície branca, texto `#0f172a`/`#475569`, borda `#e2e8f0`, Inter, cantos 12px (cards),
8px (controles), `max-w-6xl`.

Contratos em `docs/api-contracts.md` §8, §10, especialmente §7 (reviews).

## Goals / Non-Goals

**Goals:**
- Telas de pedido, envio e avaliação do comprador, refletindo estados que mudam por eventos.
- Cancelamento de pedido com validação de estado (409) e estorno assíncrono.
- Confirmação de entrega pelo comprador (ação exclusiva, não transportadora).
- Sistema completo de avaliações: pendentes, criar, editar (janela 30 dias), deletar,
  minhas avaliações.
- Repolling automático de status enquanto não-final; para quando `delivered`, `cancelled`,
  `refunded`.

**Non-Goals:**
- Gestão de pedidos pela loja (tela `GET /orders/manage`, que é do `owner`).
- Painel de despacho (`GET /shipments/manage`).

## Decisions

### D1. Repolling automático enquanto status não-final

Estados finais: `delivered`, `cancelled`, `refunded`.
- Enquanto não-final (pending, paid, processing, shipped), polling a cada 30 s via `setInterval` +
  `useQuery`.
- Para quando status muda para final.
- **Justificativa:** eventos chegam assincronamente; sem repolling, tela fica velha até o cliente
  recarregar. 30 s é intervalo balanceado para UX sem congestionar o servidor.

### D2. Modal de confirmação para "Confirmar Entrega"

O `POST /shipments/{id}/confirm-delivery` é ação de grande responsabilidade do comprador:
encerra o pedido e libera avaliação. Modal com: `"Tem certeza que recebeu o pedido? Esta ação é
irreversível."` e dois botões: "Cancelar" e "Confirmar".
- **Justificativa:** evita clicks acidentais e deixa claro o peso da ação.

### D3. Modal de cancelamento de pedido com `reason`

Cancelamento (`POST /orders/{id}/cancel`) pede um campo de motivo (até 500 caracteres).
Modal com textarea, validação de comprimento, botões "Voltar" e "Cancelar Pedido".
- Trata `409`: se status não permite, mensagem específica ("Pedido já foi cancelado" ou
  "Não é possível cancelar pedido que já foi enviado").
- **Justificativa:** motivo ajuda a loja entender padrões de cancelamento.

### D4. Timeline visual de status no detalhe do pedido

Status é uma sequência `pending` → `paid` → `processing` → `shipped` → `delivered`.
Componente de timeline mostra: círculo (feito/em progresso/futuro), label de status,
timestamp de mudança (`updatedAt` do `order` ou `shipment`).
- Cancellations/refunds desviam da linha principal.
- **Justificativa:** cliente entende visualmente onde está o pedido.

### D5. Avaliações de produto, não de vendedor

Tabela `review` tem `id_product`, `id_user`, `id_order`, `rate`, `title`, `description`.
Não existe avaliação de vendedor (loja única).
- Elegibilidade por `review_eligibility`: `id_user`, `id_product`, `id_order`, `granted_at`.
- Preenchida por evento `order.delivered`.
- **Justificativa:** sistema simples, reflete o contrato de `docs/api-contracts.md` §7.

### D6. Janela de edição de 30 dias

`PATCH /reviews/{id}` só é permitido se `createdAt` está dentro de 30 dias.
Após, responde `409` com mensagem "Janela de edição expirada".
- **Justificativa:** avaliação antiga é menos relevante para mudar; contrói-se confiança com
  histórico imutável.

### D7. Listagem de "Avaliações Pendentes" como tile grid

`GET /reviews/pending` retorna lista de produtos que pode avaliar.
Tela mostra cada um como tile: foto do produto, nome, data de compra, botão "Avaliar".
Clica em tile → modal de avaliação (5 stars, title, description).
- **Justificativa:** invite visual forte para deixar reviews.

### D8. Cache no TanStack Query com staleTime balanceado

- `staleTime: 30000` (30 s) para listas de pedidos e envios.
- `staleTime: 60000` (60 s) para detalhe do pedido (muda menos).
- `staleTime: 120000` (2 min) para avaliações (quase imutável).
- Repolling explícito com `refetchInterval` enquanto status não-final.

### D9. Estrutura de pastas em `front/src/features/orders/`

```
orders/
├── pages/
│   ├── OrdersListPage.tsx
│   ├── OrderDetailPage.tsx
│   └── ShipmentsPage.tsx
├── components/
│   ├── OrdersList.tsx
│   ├── OrderDetail.tsx
│   ├── OrderStatusTimeline.tsx
│   ├── OrderCancelModal.tsx
│   ├── ShipmentDetail.tsx
│   └── ConfirmDeliveryModal.tsx
├── queries.ts (TanStack Query hooks)
├── types.ts (TypeScript interfaces)
└── layout.tsx (data router layout)

reviews/
├── pages/
│   ├── ReviewsPendingPage.tsx
│   ├── ReviewsMinePage.tsx
│   └── ReviewDetailPage.tsx
├── components/
│   ├── PendingReviewsTile.tsx
│   ├── ReviewForm.tsx
│   ├── ReviewCard.tsx
│   └── EditReviewModal.tsx
├── queries.ts
├── types.ts
└── layout.tsx
```

## Risks / Trade-offs

- [Repolling sem Server-Sent Events (SSE)] → necessário pois é SPA com fetch. Intervalo de 30 s é
  compromisso entre UX e carga do servidor.
- [Confirmação de entrega é ação manual] → contrato define assim (comprador, não transportadora).
  Se comprador nunca confirma, pedido fica em `shipped` indefinidamente (§13.2 de
  `docs/api-contracts.md`).
- [Edição de avaliação tem janela de 30 dias] → após, é read-only. Se quiser mudar, deleta e cria
  nova.

## Decisoes de implementacao

### D1. Hooks do TanStack Query para cada rota

`queries.ts` exporta hooks como `useOrders()`, `useOrder(id)`, `useShipments()`, etc.
Cada um configura `queryKey`, `queryFn`, `staleTime` e `refetchInterval` (onde aplicável).

### D2. Typings em `types.ts`

Interfaces para `Order`, `OrderItem`, `Payment`, `Shipment`, `Review`, `ReviewEligibility`,
espelhando os payloads de `docs/api-contracts.md`.

### D3. Rotas no React Router (data router)

```typescript
{
  path: '/orders',
  element: <OrdersLayout />,
  children: [
    { path: '', element: <OrdersListPage />, loader: ordersLoader },
    { path: ':id', element: <OrderDetailPage />, loader: orderDetailLoader },
    { path: 'shipments', element: <ShipmentsPage />, loader: shipmentsLoader },
  ]
},
{
  path: '/reviews',
  element: <ReviewsLayout />,
  children: [
    { path: 'pending', element: <ReviewsPendingPage />, loader: pendingReviewsLoader },
    { path: 'mine', element: <ReviewsMinePage />, loader: myReviewsLoader },
  ]
}
```

### D4. Tratamento de erros HTTP

Cada rota é um case:
- `401/403` → redirect para login.
- `404` → componente NotFound.
- `409` (PRICE_CHANGED, cancelamento não permitido, janela de edição expirada) → modal de erro
  específico.
- `500/503` → toast "Erro ao carregar".

### D5. Estados de loading / skeleton

Enquanto `isLoading`, exibe skeleton screens (componentes vazios com animação Tailwind).
Após carregar, swap com conteúdo real.

### D6. Integração com autenticação

Bearer token no header `Authorization` sai automaticamente (interceptor axios ou fetch wrapper).
Se token expirar (401), refresh ou redirect para login.

### D7. Repolling com useEffect

```typescript
useEffect(() => {
  if (!isFinal(order?.status)) {
    const timer = setInterval(() => refetch(), 30000);
    return () => clearInterval(timer);
  }
}, [order?.status, refetch]);
```

Respeita `staleTime` do TanStack Query: não força fetch imediato se está fresco, só limpa após
`staleTime` expirar.
