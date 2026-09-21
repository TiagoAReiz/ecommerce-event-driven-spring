# Design

## Context

O front já existe em `front/` com Vite + React + TypeScript, React Router (data router), TanStack Query,
Tailwind CSS e componentes em `front/src/features/`. Stack: Vite, React 18, React Router v7, TypeScript 5,
TanStack Query v5, Tailwind CSS 3.4. Design system: branco, azul `#1d4ed8` (hover `#1e40af`), borda `#e2e8f0`,
Inter font.

Backend: contratos em `docs/api-contracts.md` (§6 user, §7 inventory, §8 order, §9 payment, §10 shipment).
Todas as rotas exigem Bearer token com papel `owner` em campos `roles`.

## Goals / Non-Goals

**Goals:** área completa de gestão para o dono da loja, com proteção de papel e feedback claro de cada operação.

**Non-Goals:** pagina de configuração de dados da loja (separada), upload direto de arquivo de foto (usar URL),
dashboard com metricas, notificacoes push.

## Decisions

### D1. Guarda de papel `owner` na sessao
O hook `useAuth()` decodifica o token Bearer e extrai `roles`. Se `roles` nao contem `owner`, rota `/store`
redireciona para home. O link de loja na nav nao existe se nao for owner.

### D2. Paginacao em listas
`GET /products/manage`, `GET /orders/manage`, `GET /shipments/manage` retornam pagina 0 (size 20) por padrao.
User navega com abas de paginacao ou "load more". Filtros reset para pagina 0.

### D3. Erros HTTP mapeados para UX
- 403 (sem escopo) → redireciona para home com toast "Acesso negado"
- 404 (recurso) → lista vazia ou modal de "nao encontrado"
- 409 (transicao invalida, reserva ativa, etc) → toast com o code do erro (ex: `TRANSITION_INVALID`)
- 422 (validacao) → toast e desabilita botao por 3 s

### D4. Modais vs drawers
Criacao/edicao de produto → drawer lateral (mais espaço para fotos), confirmacao de refund/cancel → modal central.

### D5. Otimismo em atualizacoes
Ao clicar "Despachar" (PATCH /shipments), UI atualiza status localmente na lista enquanto a request voa.
Se falhar (409), desfaz a mudanca e mostra erro. TanStack Query `useMutation` com `onMutate`.

### D6. Fotos em produto
Array editavel no drawer de edicao. Botoes "adicionar foto", reordenacao por drag-and-drop ou arrows, remocao.
Upload via URL (input text de https://); validacao client-side (URL format) + server-side (image dims, size, MIME).

### D7. Refund simples
Owner vê campo "valor do refund" pre-preenchido com o valor total do pagamento (read-only em UI).
Aceita parcial se digitar valor menor. Motivo obrigatorio.

### D8. Cancelamento com motivo
POST /orders/{id}/cancel e POST /shipments/{id}/cancel exigem motivo. Modal com textarea ≤ 500 caracteres,
validacao de minimo 5 caracteres, contador de caracteres.

### D9. Filtros persistem em query string
Status, cliente (por email ou ID), intervalo de data sao URL params. Hard refresh preserva filtros.

### D10. Sem soft-fetch de user details
Pedidos sao lidos com `GET /orders/manage` (sem nome do cliente). Owner clica na linha e ve `idCustomer`;
para saber o nome, teria de chamar `GET /users/{idCustomer}`. Aceita-se isso como trade-off: 1 user
por pedido é custoso em escala, nao vale pena cache/prefetch.

## Architecture

```
front/src/features/store/
├── pages/
│   └── StorePage.tsx           (layout principal, router das abas)
├── pages/
│   ├── ProductsPage.tsx        (lista + modal de criacao/edicao)
│   ├── OrdersPage.tsx          (lista + detalhe + cancel)
│   ├── ShipmentsPage.tsx       (lista + transicoes + cancel)
│   └── PaymentsPage.tsx        (lista + refund)
├── components/
│   ├── ProductForm.tsx         (drawer de edicao, com fotos)
│   ├── PhotoList.tsx           (array editavel de fotos)
│   ├── OrderDetail.tsx         (modal de detalhes)
│   ├── ShipmentRow.tsx         (linha com botoes de transicao)
│   └── RefundForm.tsx          (modal de estorno)
├── hooks/
│   ├── useProducts.ts          (queries: list, detail; mutations: create, update, delete, updateStock, updatePhotos)
│   ├── useOrders.ts            (queries: list, detail; mutations: cancel)
│   ├── useShipments.ts         (queries: list, detail; mutations: updateStatus, cancel)
│   └── usePayments.ts          (queries: list; mutations: refund)
├── types/
│   ├── product.ts
│   ├── order.ts
│   ├── shipment.ts
│   └── payment.ts
└── utils/
    ├── errorHandler.ts         (mapeia code HTTP para mensagem)
    └── validators.ts           (validacao de URL, valor, motivo)
```

## Implementation Notes

### SecurityConfig no front
- Rota `/store/**` protegida por `ProtectedRoute` que valida `useAuth()` e papel `owner`.
- Redirect em 403 nao carrega a pagina.

### TanStack Query cache keys
```typescript
['products', 'manage'] // GET /products/manage
['products', 'manage', { status, q, page, size }] // com filtros
['products', id] // GET /products/{id}
['orders', 'manage'] // GET /orders/manage
['orders', 'manage', { status, customerId, from, to, page }]
['shipments', 'manage'] // GET /shipments/manage
['shipments', 'manage', { status, page }]
['payments', orderId] // lista de pagamentos de um pedido
```

### Error handling
Cada mutation captura `error.response.data.code` (RFC 9457) e mostra toast contextualizado:
- `TRANSITION_INVALID` → "Status nao permite essa operacao"
- `INSUFFICIENT_STOCK` → "Estoque insuficiente"
- `RESERVATION_HELD` → "Nao pode deletar/editar produto com pedido em andamento"
- etc.

### Foto upload
URL input com validacao regex `^https://`. Server responde 422 se nao for imagem valida.
Reordenacao via `PUT /products/{id}/photos/order` com array de ids.

### Relatoria de refund
Owner digita valor de refund (default = totalCost). Motivo obrigatorio (≥ 5 caracteres).
UI desabilita botao enquanto request esta em voo. Apos 200/202, fecha modal e invalida cache.

## Trade-offs

- Sem prefetch de nomes de clientes: economia de requests.
- Drag-and-drop de fotos via arrows/indices em vez de React DnD: menos dependencia.
- Filtros em query string em vez de Redux/Context: mais simples, hard refresh funciona.
- Sem exportacao de CSV: escopo reduzido, add depois se necessario.

## Riscos

- 409 em transicoes de status: pode ser enganador se nao explicar bem no toast (ex: "Envio ja foi despachado").
- Refund que cai em 180 dias: UI mostra "data de corte" se aplicavel.
- User deletado enquanto owner esta editando: 404 em save. Retry avisar ao owner.
