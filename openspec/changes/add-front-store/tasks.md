# Tasks

## 1. Setup

- [ ] 1.1 Criar estrutura de diretorios em `front/src/features/store/` conforme design § Architecture
- [ ] 1.2 Criar `front/src/features/store/types/` com types para Product, Order, Shipment, Payment
- [ ] 1.3 Criar `front/src/utils/api.ts` com client HTTP reutilizavel (AxiosInstance com Bearer token)
- [ ] 1.4 Criar `front/src/hooks/useAuth.ts` que decodifica token e extrai `roles`, `sub`, `aud`

## 2. Protecao de rota

- [ ] 2.1 Criar `ProtectedRoute.tsx` component que valida papel `owner` e redireciona se necessario
- [ ] 2.2 Criar rota `/store` no React Router data router; renderiza `StorePage` se owner
- [ ] 2.3 Adicionar link "Loja" na navegacao principal, visivel so se `useAuth()` retorna `roles.includes('owner')`

## 3. Store entry (StorePage)

- [ ] 3.1 Criar `StorePage.tsx` com abas (Produtos, Pedidos, Envios, Pagamentos) usando Tab component
- [ ] 3.2 Integrar router em cada aba: `<Outlet />` para carrega sub-paginas
- [ ] 3.3 Estilo: container `max-w-6xl`, abas com Tailwind (azul principal em ativa), padding 24px

## 4. Produtos (store-products)

- [ ] 4.1 Criar `useProducts.ts` hook com queries: `getProductsList`, `getProductDetail`; mutations: `createProduct`, `updateProduct`, `deleteProduct`, `updateStock`, `uploadPhoto`
- [ ] 4.2 Criar `ProductsPage.tsx` com tabela: ID, Nome, Categoria, Preco, Estoque, Status (ativo/zerado/removido)
- [ ] 4.3 Integrar filtro de status (`active`, `out_of_stock`, `deleted`, `all`) e busca por nome (`q`)
- [ ] 4.4 Paginacao com controles de pagina anterior/proxima (size=20)
- [ ] 4.5 Botoes: "Novo Produto" (abre drawer), "Editar" (abre drawer), "Remover" (confirmacao)
- [ ] 4.6 Criar `ProductForm.tsx` (drawer lateral) com campos: nome, descricao, categoria, preco, estoque; validacao obrigatoria

## 5. Fotos (store-photos)

- [ ] 5.1 Criar `PhotoList.tsx` component com array editavel de fotos dentro do drawer de produto
- [ ] 5.2 Campos: URL (input https://), Position (reordenacao por arrows), remocao
- [ ] 5.3 Validacao: URL deve iniciar com https://, máximo 10 fotos, mínimo 1 se produto ativo
- [ ] 5.4 Integrar mutations de foto: `addPhoto`, `updatePhotoOrder`, `removePhoto`
- [ ] 5.5 Toast de sucesso ao salvar fotos

## 6. Pedidos (store-orders)

- [ ] 6.1 Criar `useOrders.ts` hook: queries `getOrdersList`, `getOrderDetail`; mutations `cancelOrder`
- [ ] 6.2 Criar `OrdersPage.tsx` com tabela: ID, Cliente (email ou ID breve), Status, Total, Data
- [ ] 6.3 Integrar filtro de status (repetivel), intervalo de data (from/to), cliente (customerId)
- [ ] 6.4 Paginacao (size=20)
- [ ] 6.5 Botao "Detalhes" abre modal com itens, endereco, pagamento e botao "Cancelar"
- [ ] 6.6 Cancelamento: modal com textarea de motivo (≤ 500 char), validacao ≥ 5 char, POST /orders/{id}/cancel
- [ ] 6.7 Resposta 409 mostra toast contextualizado (ex: "Pedido ja esta em status que nao permite cancelar")

## 7. Envios (store-shipments)

- [ ] 7.1 Criar `useShipments.ts` hook: queries `getShipmentsList`, `getShipmentDetail`; mutations `updateShipmentStatus`, `cancelShipment`
- [ ] 7.2 Criar `ShipmentsPage.tsx` com tabela: ID, Pedido, Status, Destino (cidade/estado), Rastreio
- [ ] 7.3 Filtro de status (repetivel), pedido, paginacao (size=20)
- [ ] 7.4 Coluna de Status com badges: pending (vermelho), ready_to_ship (amarelo), in_transit (azul), out_for_delivery (azul claro), delivered (verde), cancelled (cinza), returned (cinza)
- [ ] 7.5 Botoes de transicao em cada linha baseado no status:
  - pending → "Marcar como pronto para despacho" (ready_to_ship)
  - ready_to_ship → "Despachar" (in_transit) + campo obrigatorio de codigo de rastreio
  - in_transit → "Marcar como saido para entrega" (out_for_delivery)
  - in_transit/out_for_delivery → "Marcar como devolvido" (returned) com motivo
  - Botao "Entregar" **sempre desabilitado** com tooltip "Apenas o comprador pode confirmar entrega"
- [ ] 7.6 Cancelamento (pending/ready_to_ship): modal com motivo (≤ 500 char, ≥ 5 char)
- [ ] 7.7 Otimismo: atualiza status na UI antes de receber resposta
- [ ] 7.8 409 mostra toast: "Transicao invalida" + code

## 8. Pagamentos (store-payments)

- [ ] 8.1 Criar `usePayments.ts` hook: queries `getPaymentsList`; mutations `refundPayment`
- [ ] 8.2 Criar `PaymentsPage.tsx` com tabela: ID, Pedido, Cliente, Status, Total, Data
- [ ] 8.3 Status colors: pending (amarelo), completed/captured (verde), refunded (cinza), failed (vermelho), cancelled (cinza)
- [ ] 8.4 Botao "Estornar" visivel so se status permite (nao em failed, cancelled, refunded)
- [ ] 8.5 Modal de refund com campos:
  - Valor (pre-preenchido com total do pagamento, read-only em UI, server valida)
  - Valor parcial: checkbox "Estorno parcial" abre input de valor (validacao: ≤ total)
  - Motivo obrigatorio (≤ 500 char, ≥ 5 char)
- [ ] 8.6 POST /payments/{id}/refund com Idempotency-Key (UUID v4 gerado)
- [ ] 8.7 Resposta 200/202 mostra "Estorno processado" e invalida cache
- [ ] 8.8 422 se fora da janela de 180 dias: mostra "Estorno nao permitido apos 180 dias da compra"

## 9. Tratamento de erros

- [ ] 9.1 Criar `errorHandler.ts` que mapeia `code` HTTP (RFC 9457) para mensagem amigavel
- [ ] 9.2 Cada mutation captura `error.response.data` e passa para `toast.error(errorHandler(code))`
- [ ] 9.3 Erros comuns: `TRANSITION_INVALID`, `RESERVATION_HELD`, `INSUFFICIENT_STOCK`, `IDEMPOTENCY_KEY_REUSED`, etc.
- [ ] 9.4 Validacoes globais: 401 redireciona para login, 403 mostra "Acesso negado"

## 10. Validacao e UX

- [ ] 10.1 Criar `validators.ts` com funcoes: `isValidUrl()`, `isValidPrice()`, `isValidMotivo()`, `isValidPhotoUrl()`
- [ ] 10.2 Campo de preco: input type number, 2 casas decimais, ≥ 0
- [ ] 10.3 Campos de motivo: textarea ≤ 500, contador de caracteres ao vivo
- [ ] 10.4 Botoes de submit desabilitados enquanto mutation esta em voo (`isPending`)
- [ ] 10.5 Toast sucesso/erro padrao (duration 4s)

## 11. Query invalidation

- [ ] 11.1 Apos criar/editar produto: invalidar cache de lista de produtos
- [ ] 11.2 Apos deletar produto: idem
- [ ] 11.3 Apos cancel de pedido: invalidar lista de pedidos
- [ ] 11.4 Apos transicao de envio: invalidar lista de envios
- [ ] 11.5 Apos refund: invalidar lista de pagamentos

## 12. Verificacao

- [ ] 12.1 Rodar `npm run dev` em `front/` e navegar `/store` — pagina carrega com abas
- [ ] 12.2 Cada aba carrega dados da API (sem erro de CORS)
- [ ] 12.3 Papel `owner` verificado: usuário nao-owner vê redirecionar para home
- [ ] 12.4 Criar produto: form valida, POST sucede, lista atualiza
- [ ] 12.5 Editar produto: pre-preenche dados, PUT sucede, lista atualiza
- [ ] 12.6 Cancelar pedido: modal, modal input motivo, POST sucede com 200, status muda
- [ ] 12.7 Despachar envio: input de rastreio obrigatorio, PATCH sucede, status muda otimista
- [ ] 12.8 Refund: modal, valor pre-preenchido, POST sucede com 200 ou 202, mostra "processado"
- [ ] 12.9 Filtros persistem em URL query params
- [ ] 12.10 Hard refresh preserva filtros
