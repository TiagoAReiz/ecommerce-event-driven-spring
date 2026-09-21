# Tasks

## 1. Configuração e setup

- [ ] 1.1 Criar estrutura de pastas em `front/src/features/checkout/` conforme design D1
- [ ] 1.2 Adicionar dependências ao `front/package.json`: `@mercadopago/sdk-react` (Bricks), tipos do MP
- [ ] 1.3 Criar tipos base em `front/src/features/checkout/types/`: `cart.ts`, `checkout.ts`, `payment.ts`, `errors.ts`
- [ ] 1.4 Criar `front/src/services/api/` com configuração de cliente HTTP (axios ou fetch + interceptor de token Bearer)
- [ ] 1.5 Verificar compile sem erro

## 2. Hooks e integração com API

- [ ] 2.1 Criar `useCart.ts`: GET /cart com hidratação, TanStack Query, tratamento de timeout com fallback
- [ ] 2.2 Criar `useAddCart.ts`: POST /cart/items, invalidação de cache do carrinho
- [ ] 2.3 Criar `useUpdateCartItem.ts`: PUT /cart/items/{idProduct}
- [ ] 2.4 Criar `useRemoveCartItem.ts`: DELETE /cart/items/{idProduct}
- [ ] 2.5 Criar `useEmptyCart.ts`: DELETE /cart com confirmação
- [ ] 2.6 Criar `useShippingQuote.ts`: GET /shipping/quote?zipcode, cache por CEP
- [ ] 2.7 Criar `usePaymentConfig.ts`: GET /payments/config, cache público
- [ ] 2.8 Criar `usePaymentMethods.ts`: GET /payments/methods com query de amount
- [ ] 2.9 Criar `useCreatePayment.ts`: POST /payments com tratamento de idempotência
- [ ] 2.10 Criar `useSyncPayment.ts`: POST /payments/{id}/sync
- [ ] 2.11 Criar `useCheckout.ts`: POST /orders com `Idempotency-Key`, revalidação de preço
- [ ] 2.12 Criar `useIdempotencyKey.ts`: geração, armazenamento, limpeza
- [ ] 2.13 Criar `usePaymentPolling.ts`: polling com backoff exponencial, parada em sucesso/expiração
- [ ] 2.14 Criar `useOrders.ts`: GET /orders com filtros, paginação
- [ ] 2.15 Criar `useOrderDetail.ts`: GET /orders/{id}
- [ ] 2.16 Criar `useOrdersManage.ts`: GET /orders/manage (owner)
- [ ] 2.17 Criar `useCancelOrder.ts`: POST /orders/{id}/cancel
- [ ] 2.18 Configurar retry e invalidação de cache no TanStack Query
- [ ] 2.19 Verificar compile

## 3. Componentes de Carrinho

- [ ] 3.1 Criar `CartEmpty.tsx`: estado vazio com botão "Continuar Comprando"
- [ ] 3.2 Criar `ProductWarnings.tsx`: exibição de avisos PRODUCT_UNAVAILABLE, INSUFFICIENT_STOCK, PRICE_CHANGED, HYDRATION_TIMEOUT
- [ ] 3.3 Criar `CartItem.tsx`: linha com foto, nome, preço, quantidade (com +/- ou campo), total da linha, botão remover
- [ ] 3.4 Criar `CartSummary.tsx`: lista de itens, subtotal, possível exibição de frete (se no checkout), botão "Ir para Checkout"
- [ ] 3.5 Estilizar com Tailwind: cores branco/azul, cards 12px, altura mínima de botões 48px
- [ ] 3.6 Testar carregamento, avisos visuais, operações (add, remove, update, empty)

## 4. Componentes de Checkout

- [ ] 4.1 Criar `CheckoutSteps.tsx`: indicador visual das 3 etapas (Endereço → Frete → Confirmação)
- [ ] 4.2 Criar `AddressForm.tsx`: form com campos CEP, estado, cidade, rua, número; validação com BrasilAPI
- [ ] 4.3 Criar `AddressSelection.tsx`: listagem de endereços existentes, seleção de padrão, botão "Novo Endereço"
- [ ] 4.4 Criar `ShippingReview.tsx`: exibição de itemsCost, freightCost, totalCost; botões "Usar Este Endereço" / "Voltar"
- [ ] 4.5 Criar `OrderReview.tsx`: resumo completo de itens, custos, endereço
- [ ] 4.6 Criar `ConfirmCheckout.tsx`: botão de confirmação POST /orders, tratamento de 409 PRICE_CHANGED, 422 EMPTY_CART, 503 indisponibilidade
- [ ] 4.7 Integrar navegação entre etapas (useState local de etapa ativa)
- [ ] 4.8 Testar fluxo completo com casos de erro

## 5. Componentes de Pagamento

- [ ] 5.1 Criar `PaymentMethodPicker.tsx`: abas para PIX, Cartão, Checkout Pro
- [ ] 5.2 Criar `PIXPayment.tsx`:
  - [ ] 5.2.1 Form com email e CPF
  - [ ] 5.2.2 POST /payments, exibição de QR code (qrCodeBase64)
  - [ ] 5.2.3 Exibição de código copiável (qrCode) com botão "Copiar"
  - [ ] 5.2.4 Link "Abrir no Mercado Pago" (ticketUrl)
  - [ ] 5.2.5 Exibição de expiração (expiresAt)
  - [ ] 5.2.6 usePaymentPolling: GET /payments/{id} a cada 5s; sync no maximo 1x por minuto (429)
  - [ ] 5.2.7 Avisos: "PIX expirou, gere um novo" com botão; "Aguardando confirmação..."
- [ ] 5.3 Criar `CardPayment.tsx`:
  - [ ] 5.3.1 Carregar Bricks do MP dinamicamente
  - [ ] 5.3.2 Form com email, CPF, parcelas (de GET /payments/methods)
  - [ ] 5.3.3 Botão "Pagar com Cartão" → tokenize() → POST /payments
  - [ ] 5.3.4 Tratamento de statusDetail: cc_rejected_call_for_authorize, cc_rejected_insufficient_amount, cc_rejected_bad_filled_security_code
  - [ ] 5.3.5 Tratamento de 410 (token expirado)
  - [ ] 5.3.6 Fallback se Bricks falhar carregar
- [ ] 5.4 Criar `CheckoutProPayment.tsx`:
  - [ ] 5.4.1 Form simples com email
  - [ ] 5.4.2 Botão "Pagar com Checkout Pro" → POST /payments
  - [ ] 5.4.3 Redirecionar para initPoint
  - [ ] 5.4.4 Ao retornar, verificar GET /orders/{id} para payment.status
- [ ] 5.5 Criar `PaymentStatus.tsx`: exibição de status (pending, captured, failed, cancelled)
- [ ] 5.6 Criar `PaymentError.tsx`: componente de erro com code, detail, ações sugeridas
- [ ] 5.7 Testar cada método com dados válidos e inválidos

## 6. Componentes de Confirmação

- [ ] 6.1 Criar `OrderConfirmation.tsx`:
  - [ ] 6.1.1 Exibição de número do pedido, data, status
  - [ ] 6.1.2 Resumo dos itens comprados
  - [ ] 6.1.3 Botão "Ver Pedido Completo" → /orders/{id}
  - [ ] 6.1.4 Botão "Continuar Comprando" → /
  - [ ] 6.1.5 Email de confirmação (sugestão para integração futura)
- [ ] 6.2 Criar `ShipmentTracking.tsx`: exibição de envio quando payment.status === "captured"
- [ ] 6.3 Testar transição após pagamento bem-sucedido

## 7. Painel de Pedidos e Gestão

- [ ] 7.1 Criar `OrderList.tsx`: lista com filtros (status, data), paginação
- [ ] 7.2 Criar `OrderCard.tsx`: card com resumo (número, data, status, primeira foto, totais)
- [ ] 7.3 Criar `OrderDetailPage.tsx`: visualização completa (GET /orders/{id}) com itens, pagamento, envio
- [ ] 7.4 Criar `OrdersManagePage.tsx` (owner): GET /orders/manage com filtros de status e customerId
- [ ] 7.5 Criar `OrderCancelDialog.tsx`: diálogo com input de motivo, POST /orders/{id}/cancel
- [ ] 7.6 Testar navegação e filtros

## 8. Rotas e navegação

- [ ] 8.1 Criar rotas no React Router: `/cart`, `/checkout`, `/checkout/payment`, `/orders`, `/orders/{id}`, `/orders/manage` (protegida por owner)
- [ ] 8.2 Adicionar links no header/navbar
- [ ] 8.3 Implementar proteção de rota (redirect para login se sem token)
- [ ] 8.4 Testar navegação entre todas as páginas

## 9. Tratamento de erros e comportamento fake

- [ ] 9.1 Criar componente global `<ErrorAlert />` com tratamento de RFC 9457 (code, title, detail, errors[])
- [ ] 9.2 Criar toast/notification para feedback de operações (sucesso, erro, aviso)
- [ ] 9.3 Implementar detecção de `environment === "fake"` em `/payments/config` e adaptar UI
- [ ] 9.4 Testar todos os códigos HTTP documentados (400, 401, 403, 404, 409, 422, 429, 500, 502, 503, 504)
- [ ] 9.5 Testar retry automático com exponential backoff

## 10. Integração e testes

- [ ] 10.1 Testar carrinho: adicionar, remover, alterar quantidade, esvaziar
- [ ] 10.2 Testar checkout: seleção de endereço, cálculo de frete, criação de pedido
- [ ] 10.3 Testar PIX: geração de QR, polling de status, expiração
- [ ] 10.4 Testar Cartão: tokenização, rejeição, revalidação de parcelas
- [ ] 10.5 Testar Checkout Pro: redirecionamento e retorno
- [ ] 10.6 Testar confirmação e navegação para visualização de pedido
- [ ] 10.7 Testar painel de pedidos e filtros
- [ ] 10.8 Testar em mobile (responsividade)
- [ ] 10.9 Testar com rede lenta (timeout, retry)

## 11. Performance e otimizações

- [ ] 11.1 Lazy load Bricks do MP (carregar apenas na tela de pagamento)
- [ ] 11.2 Memoizar componentes de lista (CartItem, OrderCard) para evitar re-render
- [ ] 11.3 Usar `staleTime` e `cacheTime` apropriados no TanStack Query
- [ ] 11.4 Implementar debounce na busca de CEP
- [ ] 11.5 Otimizar imagens (foto do produto em CartItem)

## 12. Documentação e polish

- [ ] 12.1 Documentar hooks customizados com exemplos de uso
- [ ] 12.2 Adicionar comentários em pontos complexos (idempotência, polling, tokenização)
- [ ] 12.3 Revisar mensagens de erro para clareza
- [ ] 12.4 Testar acessibilidade (ARIA labels, navegação por teclado)
- [ ] 12.5 Verificar todos os links de documentação (docs/api-contracts.md, docs/event-contracts.md)
