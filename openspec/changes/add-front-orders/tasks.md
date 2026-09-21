# Tasks

## 1. Base: Estrutura de tipos e queries

- [ ] 1.1 Criar `front/src/features/orders/types.ts` com interfaces: `Order`, `OrderItem`,
  `Payment`, `Shipment`, `OrderListResponse`, `ShipmentListResponse` (design D2)
- [ ] 1.2 Criar `front/src/features/reviews/types.ts` com interfaces: `Review`, `ReviewEligibility`,
  `ReviewPendingItem`, `ReviewListResponse`
- [ ] 1.3 Criar `front/src/features/orders/queries.ts` com hooks TanStack Query:
  `useOrders()`, `useOrder(id)`, `useShipments()`, `useShipment(id)`, com `staleTime` e
  `refetchInterval` configurados (design D8)
- [ ] 1.4 Criar `front/src/features/reviews/queries.ts` com hooks: `useReviewsPending()`,
  `useReviewsMine()`, `useReview(id)` com cache apropriado

## 2. Tela de Pedidos

- [ ] 2.1 Criar `front/src/features/orders/pages/OrdersListPage.tsx` com tabela/grid de pedidos,
  filtros (status, período), paginação; usar `useOrders` hook (design D9)
- [ ] 2.2 Criar `front/src/features/orders/components/OrdersList.tsx` com renderização dos itens,
  link para detalhe
- [ ] 2.3 Implementar filtros de `status` (dropdown multi-select), `from`/`to` (date pickers),
  paginação do TanStack Query
- [ ] 2.4 Criar skeleton loader enquanto `isLoading` (design D5)
- [ ] 2.5 Tratar erros: `401/403` → redirect; `500/503` → toast

## 3. Detalhe do Pedido e Timeline

- [ ] 3.1 Criar `front/src/features/orders/pages/OrderDetailPage.tsx` com layout: itens,
  valores (itemsCost + freightCost = totalCost), payment e shipment (design D9)
- [ ] 3.2 Criar `front/src/features/orders/components/OrderStatusTimeline.tsx` com círculos
  e labels de status, timestamp de `updatedAt` (design D4)
- [ ] 3.3 Exibir itens com foto, nome, preço, quantidade, linha total
- [ ] 3.4 Mostrar payment e shipment como projeções (podem ser `null`); atualizar quando eventos
  chegarem
- [ ] 3.5 Implementar repolling: `useEffect` com `setInterval(refetch, 30000)` enquanto
  `!isFinal(status)` (design D1, D7)

## 4. Cancelamento de Pedido

- [ ] 4.1 Adicionar botão "Cancelar Pedido" ao detalhe (habilitado apenas em `pending`/`paid`)
- [ ] 4.2 Criar `front/src/features/orders/components/OrderCancelModal.tsx` com textarea de motivo,
  validação de comprimento, botões (design D3)
- [ ] 4.3 Implementar `POST /orders/{id}/cancel` via hook `useCancelOrder()`
- [ ] 4.4 Tratar `409`: exibir mensagem específica por motivo (status não permite, já cancelado)
- [ ] 4.5 Após sucesso, refetch do pedido e redirect para lista ou manter na tela com status
  atualizado

## 5. Acompanhamento de Envio

- [ ] 5.1 Criar `front/src/features/orders/pages/ShipmentsPage.tsx` com lista de envios,
  filtros (status), paginação
- [ ] 5.2 Criar `front/src/features/orders/components/ShipmentDetail.tsx` com: id, status,
  código de rastreamento, origem (loja), destino (comprador), timeline de status
- [ ] 5.3 Adicionar botão "Confirmar Entrega" quando status for `in_transit` ou `out_for_delivery`
- [ ] 5.4 Criar `front/src/features/orders/components/ConfirmDeliveryModal.tsx` com confirmação
  (design D2)
- [ ] 5.5 Implementar `POST /shipments/{id}/confirm-delivery` via hook `useConfirmDelivery()`
- [ ] 5.6 Tratar `409`: mensagem de erro quando status não permite (ainda não despachado, já
  entregue, cancelado)

## 6. Avaliações Pendentes

- [ ] 6.1 Criar `front/src/features/reviews/pages/ReviewsPendingPage.tsx` com tile grid de
  produtos para avaliar (design D7)
- [ ] 6.2 Criar `front/src/features/reviews/components/PendingReviewsTile.tsx` com: foto do
  produto, nome, data de compra (`grantedAt`), botão "Avaliar"
- [ ] 6.3 Ao clicar "Avaliar", abrir modal de criação de review
- [ ] 6.4 Usar `useReviewsPending()` para carregar lista com paginação

## 7. Criar/Editar Avaliação

- [ ] 7.1 Criar `front/src/features/reviews/components/ReviewForm.tsx` com:
  - Star rating (1–5, clickable)
  - Text input para `title` (até 150 caracteres)
  - Textarea para `description` (sem limite documentado, usar 1000)
  - Botões "Cancelar" e "Postar Avaliação" / "Salvar Alterações"
- [ ] 7.2 Criar hook `useCreateReview()` que faz `POST /products/{id}/reviews` com `idOrder`,
  `rate`, `title`, `description`
- [ ] 7.3 Criar hook `useUpdateReview(id)` que faz `PATCH /reviews/{id}`
- [ ] 7.4 Tratar `409` após janela de 30 dias: mensagem "Não é possível editar avaliação após
  30 dias" (design D6)
- [ ] 7.5 Tratar `409` (já avaliou esse produto nesse pedido): "Você já avaliou este produto neste
  pedido"
- [ ] 7.6 Tratar `403` (sem elegibilidade): "Você não pode avaliar este produto"
- [ ] 7.7 Tratar `422` (texto reprovado na moderação): "Texto contém conteúdo inadequado"

## 8. Minhas Avaliações

- [ ] 8.1 Criar `front/src/features/reviews/pages/ReviewsMinePage.tsx` com lista paginada de
  avaliações do usuário
- [ ] 8.2 Criar `front/src/features/reviews/components/ReviewCard.tsx` com: nome do produto,
  foto, rate (stars), title, description, `createdAt`, `editedAt`, botões "Editar" / "Deletar"
- [ ] 8.3 Implementar `useDeleteReview(id)` que faz `DELETE /reviews/{id}`
- [ ] 8.4 Ao clicar "Editar", abrir modal com `ReviewForm` pré-populado
- [ ] 8.5 Ao clicar "Deletar", confirmação (modal): "Tem certeza que deseja remover esta
  avaliação?"
- [ ] 8.6 Após sucesso, refetch da lista

## 9. Integração de Rotas (React Router)

- [ ] 9.1 Adicionar `orders` e `reviews` ao layout de rotas em `front/src/router.tsx` (design D3)
- [ ] 9.2 Criar loaders para cada página (data router)
- [ ] 9.3 Configurar `refetchInterval` no loader quando status não-final (design D1)
- [ ] 9.4 Integrar com sistema de autenticação: Bearer token no header `Authorization`

## 10. Estilo e Componentes Reutilizáveis

- [ ] 10.1 Criar ou estender componentes Tailwind: Modal, Button, Badge (status), Link
- [ ] 10.2 Aplicar design tokens: azul `#1d4ed8`, hover `#1e40af`, destaque `#eff6ff`,
  texto `#0f172a`/`#475569`, borda `#e2e8f0`, cantos 12px (cards), 8px (controles)
- [ ] 10.3 Implementar animação de skeleton (Tailwind shimmer)
- [ ] 10.4 Criar componente de toast/snackbar para mensagens de erro
- [ ] 10.5 Implementar spinners/loaders para ações assincronamente

## 11. Tratamento de Erros Transversais

- [ ] 11.1 Criar middleware de erro HTTP (401/403/404/409/500/503) com roteamento apropriado
  (design D4)
- [ ] 11.2 Implementar retry automático para erros retentáveis (5xx, timeout)
- [ ] 11.3 Mensagens de erro específicas por código HTTP documentado

## 12. Verificação

- [ ] 12.1 Testar lista de pedidos: filtros, paginação, loading, erro
- [ ] 12.2 Testar detalhe e timeline: carregamento, repolling a cada 30 s até status final
- [ ] 12.3 Testar cancelamento: modal, validação de estado (409), estorno assíncrono
- [ ] 12.4 Testar envios: lista, detalhe, confirmação de entrega
- [ ] 12.5 Testar avaliações: pendentes, criar, editar (dentro e fora de 30 dias),
  deletar, minhas avaliações
- [ ] 12.6 Verificar que as telas refletem projeções de `payment` e `shipment` quando eventos
  chegam
