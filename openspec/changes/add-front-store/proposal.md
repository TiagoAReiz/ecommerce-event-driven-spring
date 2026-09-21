# Proposal

## Why

O front não tem a área de gestão da loja (owner). Qualquer loja event-driven precisa de ferramentas para o dono:
- listar, criar, editar e remover produtos; gerenciar fotos e estoque;
- acompanhar pedidos pagos e decidir quando despachar;
- controlar envios — a loja marca as transições até `in_transit`, o comprador confirma entrega;
- estornar pagamentos se necessário, total ou parcial.

Tudo acontece sob autenticação Bearer com papel `owner`, com fluxos protegidos pela API — nenhuma decisão crítica vem do cliente.

## What Changes

- Nova tela protegida `http://localhost:3000/store` visível só para `owner`, com menu de abas.
- **Produtos:** lista com filtros de status, busca por nome, paginação, estoque bruto e status ativo/zerado/removido;
  criação e edição em modal/drawer; upload de fotos (reordenação e remoção); ajuste de estoque por valor absoluto ou delta.
- **Pedidos:** lista de todos os pedidos da loja com filtro de status, intervalo de data, cliente e paginação;
  detalhe do pedido; cancelamento com motivo (só em status `pending`, `paid`, `processing`).
- **Envios:** fila de despacho (status `pending` e `ready_to_ship`) com destaque; transições (ready → in_transit, in_transit → out_for_delivery, returned);
  cancelamento com motivo (só em `pending` e `ready_to_ship`); código de rastreio; botão de entregar **desabilitado** (reservado ao comprador).
- **Pagamentos:** lista de pagamentos dos pedidos da loja; refund com valor (total ou parcial) e motivo, respeitando a janela de 180 dias do Mercado Pago.

Rotas consumidas: tudo em `GET /api/v1/products/manage`, `POST/PUT/PATCH/DELETE /api/v1/products/**`,
`GET /api/v1/orders/manage`, `POST /api/v1/orders/{id}/cancel`, `GET /api/v1/shipments/manage`,
`PATCH /api/v1/shipments/{id}`, `POST /api/v1/shipments/{id}/cancel`, `POST /api/v1/payments/{id}/refund`.

## Capabilities

### New Capabilities
- `store-entry`: entrada protegida, verificação de papel owner, navegação entre abas da loja.
- `store-products`: listagem, busca, criação, edição, remoção de produtos; gestão de estoque e fotos.
- `store-photos`: upload e reordenação de fotos, integração com produtos.
- `store-orders`: listagem de pedidos da loja, cancelamento.
- `store-shipments`: listagem de envios, transições de status, cancelamento, código de rastreio.
- `store-payments`: listagem de pagamentos, refund total ou parcial.

### Modified Capabilities
- `auth`: rota `/api/v1/auth/session` lida `roles` do token para renderizar link da loja.

## Impact

- `front/src/features/store/` com componentes, hooks, tipos e páginas.
- Integração com TanStack Query para cache de listas.
- Sem alteração no backend: todas as rotas já existem.
- Sem nova autenticação: bearer token existente contém `roles`.

## Design System

- Branco (superfície), azul principal `#1d4ed8`, hover `#1e40af`, destaque `#eff6ff`.
- Texto `#0f172a` (escuro) e `#475569` (médio), borda `#e2e8f0`.
- Inter font, cantos 12px em cards e 8px em controles, container `max-w-6xl`.
- Compatível com Tailwind CSS (já no projeto).
