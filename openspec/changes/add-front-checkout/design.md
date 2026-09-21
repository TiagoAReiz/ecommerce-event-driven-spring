# Design

## Context

O backend fornece rotas HTTP completas em `docs/api-contracts.md` §8 (carrinho, checkout e pedidos), §9 (pagamento Mercado Pago) e §10 (frete). O front deve consumir essas rotas com token Bearer, apresentar interface clara e tratável a avisos (indisponibilidade, preço alterado, falta de estoque) e integrar com o SDK Bricks do Mercado Pago para tokenização segura de cartão.

O fluxo ponta a ponta está em `docs/api-contracts.md` §12; regras de idempotência em §3.5; tratamento de erro RFC 9457 em §3.2; mapeamento de status de pagamento em §9.2; ciclo de envio em §10.2.

## Goals / Non-Goals

**Goals:**
- interface visual completa de carrinho, checkout em etapas, pagamento com três métodos e confirmação do pedido;
- integração segura com Mercado Pago (cartão tokenizado no front, número nunca vai ao backend);
- geração e reaproveitamento automático de `Idempotency-Key` para retry transparente;
- polling periódico de pagamento PIX até aprovação ou expiração;
- painel de gestão da loja (owner) com visualização de todos os pedidos;
- funcionamento completo em ambiente `fake` (sem credenciais reais, status simulado);
- avisos visuais claros para produto indisponível, preço alterado ou estoque insuficiente.

**Non-Goals:**
- lógica de carrinho persistente entre abas (assume-se navegação linear);
- integração com other provedores de pagamento (apenas Mercado Pago);
- gestão de devoluções ou trocas (escopo é pedido até confirmação de entrega);
- administração de produtos ou estoque pelo front (owner só vê, não edita).

## Decisions

### D1. Estrutura de features e hooks

```
front/src/features/checkout/
  ├── components/
  │   ├── Cart/
  │   │   ├── CartSummary.tsx         # resumo com linhas, totais, avisos
  │   │   ├── CartItem.tsx            # linha de item com quantidade e botão remover
  │   │   ├── CartEmpty.tsx           # estado vazio
  │   │   └── ProductWarnings.tsx     # avisos por item (PRODUCT_UNAVAILABLE, INSUFFICIENT_STOCK, PRICE_CHANGED)
  │   ├── Checkout/
  │   │   ├── CheckoutSteps.tsx       # indicador de etapa (endereço → frete → confirmação)
  │   │   ├── AddressSelection.tsx    # lista de endereços + botão "Novo endereço"
  │   │   ├── AddressForm.tsx         # formulário de cadastro com validação de CEP
  │   │   ├── ShippingReview.tsx      # exibição de frete calculado e total
  │   │   ├── OrderReview.tsx         # revisão final antes de confirmar
  │   │   └── ConfirmCheckout.tsx     # botão de confirmação com spinner e retry
  │   ├── Payment/
  │   │   ├── PaymentMethodPicker.tsx # abas para PIX, Cartão, Checkout Pro
  │   │   ├── PIXPayment.tsx          # QR code, código copiável, polling status
  │   │   ├── CardPayment.tsx         # form com Bricks do MP para tokenização
  │   │   ├── CheckoutProPayment.tsx  # botão de redirecionamento para init_point
  │   │   ├── PaymentStatus.tsx       # exibição de status (pending, captured, failed)
  │   │   └── PaymentError.tsx        # mensagens de erro específicas por código
  │   └── Confirmation/
  │       ├── OrderConfirmation.tsx   # número, data, status, botões "Ver Pedido" e "Continuar Comprando"
  │       └── ShipmentTracking.tsx    # integração com envio quando disponível
  ├── hooks/
  │   ├── useCart.ts                  # GET /cart, hidratação, avisos
  │   ├── useAddCart.ts               # POST /cart/items
  │   ├── useUpdateCartItem.ts        # PUT /cart/items/{id}
  │   ├── useRemoveCartItem.ts        # DELETE /cart/items/{id}
  │   ├── useEmptyCart.ts             # DELETE /cart
  │   ├── useCheckout.ts              # POST /orders com idempotência
  │   ├── useShippingQuote.ts         # GET /shipping/quote
  │   ├── usePaymentConfig.ts         # GET /payments/config
  │   ├── usePaymentMethods.ts        # GET /payments/methods
  │   ├── useCreatePayment.ts         # POST /payments
  │   ├── useSyncPayment.ts           # POST /payments/{id}/sync
  │   ├── useOrders.ts                # GET /orders com filtros
  │   ├── useOrderDetail.ts           # GET /orders/{id}
  │   ├── useOrdersManage.ts          # GET /orders/manage (owner)
  │   ├── useCancelOrder.ts           # POST /orders/{id}/cancel
  │   ├── useIdempotencyKey.ts        # geração e armazenamento local
  │   └── usePaymentPolling.ts        # polling periódico com backoff
  └── types/
      ├── cart.ts                     # Cart, CartItem, CartWarning
      ├── checkout.ts                 # CheckoutRequest, CheckoutResponse
      ├── payment.ts                  # PaymentConfig, Payment, PaymentMethod
      └── errors.ts                   # ProblemError com code estável

```

### D2. Gerenciamento de estado e cache

- **TanStack Query (React Query)**: cache de `GET /cart`, `GET /orders`, `GET /payments/config`, invalidação automática após `POST`/`DELETE`.
- **localStorage**: armazenamento de `Idempotency-Key` por sessão (key = `cart-idempotency-key`); token Bearer em memória (nunca persiste).
- **useState local**: estado de forma do checkout (endereço selecionado, método de pagamento), etapa atual.
- **Polling com exponential backoff**: status de PIX a cada 2s, máximo 10 tentativas (20s); backoff se receber `429`.

### D3. Tratamento de idempotência

- **useIdempotencyKey hook**: gera UUID v4 no primeiro render, armazena em localStorage sob chave `checkout-idempotency-key-{sessionId}`, reutiliza em retry.
- **Retry automático em 409 `IDEMPOTENCY_IN_FLIGHT`**: aguarda 1s (conforme header `Retry-After`) e repete requisição com mesma chave.
- **Detecção de corpo alterado (422 `IDEMPOTENCY_KEY_REUSED`)**: erro claro ao usuário sugerindo refresh da página.
- **Limpeza após sucesso**: após `201` do POST /orders, chave é removida do localStorage para nova compra.

### D4. Fluxo de carrinho

1. **GET /cart**: hidratação com `product` (nome, preço, foto, disponível, ativo), `issues[]` por item.
2. **Avisos visuais**:
   - `PRODUCT_UNAVAILABLE`: item com linha cinzenta, ícone de bloqueio, não permite quantidade.
   - `INSUFFICIENT_STOCK`: aviso em laranja mostrando disponível vs. solicitado; quantidade é reduzida automaticamente na UI.
   - `PRICE_CHANGED`: aviso mostrando preço anterior vs. novo; permitido mas com destaque.
   - `HYDRATION_TIMEOUT`: item aparece sem dados do produto com aviso de carregamento; permite remover.
3. **POST /cart/items**: adiciona e soma quantidade; mostra toast de sucesso.
4. **PUT /cart/items/{id}**: define quantidade exata; valida contra disponível.
5. **DELETE /cart/items/{id}**: remove linha com confirmação rápida (undo em 3s).
6. **DELETE /cart**: esvazia com diálogo de confirmação.

### D5. Fluxo de checkout em etapas

**Etapa 1: Endereço**
- Listagem de `GET /users/me/addresses` com indicação de padrão.
- Botão "Novo endereço" abre formulário `POST /users/me/addresses`.
- Validação: CEP com 8 dígitos, estado 2 letras; BrasilAPI valida city/state.
- Ao selecionar, chama `GET /shipping/quote?zipcode={cep}` para carregar frete.

**Etapa 2: Revisão de frete e total**
- Exibe `itemsCost`, `freightCost`, `totalCost` (serão revalidados no POST).
- Botão "Usar este endereço" ou "Voltar" para mudar.

**Etapa 3: Confirmação**
- Resumo do pedido (itens, custos, endereço).
- Idempotency-Key gerada neste momento se não existir.
- Botão "Confirmar Pedido" desabilitado enquanto carrega.
- Erro 409 `PRICE_CHANGED` exibe diferença e oferece "Recalcular" (volta para etapa 2).
- Erro 422 `EMPTY_CART` → volta ao carrinho (item foi removido).
- Erro 503 `INVENTORY_UNAVAILABLE` → aviso persistente para tentar depois.

Após `201`, exibe tela de confirmação com número do pedido.

### D6. Fluxo de pagamento

**GET /payments/config** (público):
- Carrega `publicKey`, `environment` (sandbox ou fake), `enabledMethods`.
- Se `environment === "fake"`, todas as APIs de teste funcionam e status é simulado.

**Seleção de método**:
- Abas: PIX, Cartão, Checkout Pro.
- PIX exigido: email e CPF (para pessoa).
- Cartão exigido: email, CPF, parcelamento (cálculo de juros do MP).
- Checkout Pro: apenas e-mail do usuário.

**PIX (POST /payments)**:
1. Form com email (pré-preenchido do perfil) e CPF (máscara, validação de dígito).
2. Botão "Gerar PIX" faz `POST /payments {idOrder, method: "pix", payer: {email, identification}}`.
3. Resposta `201` traz `detail.qrCode` (string), `detail.qrCodeBase64` (imagem), `detail.ticketUrl`, `detail.expiresAt`.
4. QR code exibido grande; código copiável embaixo com botão "Copiar" (toast de sucesso).
5. Ticket URL em link "Abrir no Mercado Pago".
6. Polling: `POST /payments/{id}/sync` a cada 2s por até 20s.
7. Ao receber `status: "captured"` → tela de confirmação.
8. Se expirar (`now > expiresAt`) → aviso "PIX expirou, gere um novo" com botão.

**Cartão (POST /payments)**:
1. Carrega Bricks do MP com `publicKey` e `amount`.
2. Form com fields: número (mascarado, máximo 16-19 dígitos), mês/ano, CVV, nome.
3. Seleção de parcela (cálculo do MP via GET /payments/methods).
4. Botão "Pagar com Cartão" chama `tokenize()` do Bricks.
5. Se tokenização bem-sucedida, envia `POST /payments {idOrder, method: "credit_card", token, paymentMethodId, issuerId, installments, payer}`.
6. Resposta `201` com `status: "captured"` → confirmação; `status: "failed"` → exibe `statusDetail` em linguagem amigável.
   - `cc_rejected_call_for_authorize` → "Ligue para seu banco"
   - `cc_rejected_insufficient_amount` → "Limite insuficiente"
   - `cc_rejected_bad_filled_security_code` → "CVV inválido"
   - Oferece "Tentar outro cartão" ou "Voltar".
7. Erro 410 (token expirado) → "Token expirou, gere um novo".

**Checkout Pro (POST /payments)**:
1. Simples: email pré-preenchido, botão "Pagar com Checkout Pro".
2. `POST /payments {idOrder, method: "checkout_pro", payer: {email}}`.
3. Resposta `201` traz `detail.initPoint` (URL do Mercado Pago).
4. Redireciona para `window.location.href = detail.initPoint`.
5. MP cuida da captura; ao retornar, chama `GET /orders/{id}` para verificar se `payment.status === "captured"`.

### D7. Tratamento de erros e códigos HTTP

| Código | Cenário | Ação no front |
|--------|---------|---------------|
| `200` / `201` | Sucesso em GET/POST | Atualizar UI com dados |
| `304` | Cache válido (ETag) | Manter dados locais |
| `400` | Corpo inválido (JSON malformado, type errado) | Toast "Erro: dados inválidos"; log em dev |
| `401` | Token expirado ou ausente | Redirecionar para login (token Bearer) |
| `403` | Sem escopo (ex.: customer tentando GET /orders/manage) | Toast "Acesso negado"; não mostrar rota no menu |
| `404` | Recurso inexistente ou de outro usuário | Toast "Não encontrado"; voltar para lista anterior |
| `409` | Conflito (ex.: `PRICE_CHANGED`, `INSUFFICIENT_STOCK`, `IDEMPOTENCY_IN_FLIGHT`) | Exibir problema específico e oferecer ação (recalcular, tentar depois) |
| `422` | Validação (ex.: CEP inválido, `IDEMPOTENCY_KEY_REUSED`, `EMPTY_CART`) | Toast com detalhe do campo ou sugestão |
| `429` | Rate limit | Toast "Muitas requisições, tente em 1 min"; usar header `Retry-After` |
| `500` | Erro interno | Toast "Erro no servidor"; log e contato de suporte (se configurado) |
| `502` | Resposta inválida do downstream | Toast "Erro de comunicação"; sugerir retry |
| `503` | Serviço indisponível (inventory, shipment, payment) | Toast com contexto (ex.: "Catálogo indisponível"), oferecer retry |
| `504` | Timeout | Toast "Operação demorou; tente novamente" + retry automático com backoff |

**Sempre extrair `code` estável** do RFC 9457 para lógica (não confiar em `title`).

### D8. Comportamento em ambiente fake

- `GET /payments/config` retorna `environment: "fake"`.
- PIX: o provedor falso já devolve o pagamento aprovado na criação; a tela mostra o QR code
  de exemplo e cai direto na confirmação. Não existe botão de "aprovar" — isso seria uma rota
  que o backend não tem.
- Cartão: qualquer token válido é aceito e retorna `status: "captured"`.
- Checkout Pro: redirect funciona normalmente (caixa de areia do MP).
- Pedidos criados com `status: "pending"` → podem ser pagos e aceitos a qualquer hora.
- Nenhuma chamada real ao Mercado Pago quando fake.

### D9. Responsividade e design

- **Mobile-first**: layout único em Tailwind CSS.
- **Container**: `max-w-6xl mx-auto px-4`.
- **Cards**: `rounded-lg` (12px) com border `border-slate-200`.
- **Botões**: altura mínima 48px, `rounded-lg`, hover `bg-blue-700` (hover do `#1d4ed8`).
- **Inputs**: `rounded-lg` 8px, border `border-slate-300`, focus `ring-blue-600`.
- **Cores**: fundo branco `bg-white`, texto padrão `text-slate-900`, secundário `text-slate-600`, destaque `bg-blue-50` (`#eff6ff`).
- **Avisos**: amarelo para `INSUFFICIENT_STOCK`, vermelho para `PRODUCT_UNAVAILABLE`, verde para sucesso.

### D10. Segurança e data leakage

- **Número de cartão**: gerado no front pelo Bricks; nunca enviado ao backend. Apenas `token` (uso único) vai ao POST /payments.
- **Token Bearer**: armazenado apenas em memória (const/useState), nunca persiste. Nunca é enviado a outro serviço ou logging.
- **CPF e email**: enviados apenas em POST /payments para os métodos PIX/Cartão; nunca armazenados após. Validação básica de formato.
- **Chave de idempotência**: UUID v4, armazenada em localStorage (segura pois é UUID, não credencial), limpa após sucesso.
- **Dados sensíveis**: nenhum é logado, mesmo em dev; usar `console.debug` com flag.

## Risks / Trade-offs

- **Sincronização de carrinho entre abas**: não implementada. Assume-se navegação linear; se usuário abre duas abas, a segunda não sincroniza com a primeira. Mitigação: clear cache ao ganhar foco da aba (`visibilitychange`).
- **Polling bloqueante**: fazer polling periódico no PIX consome recursos; não há WebSocket no escopo. Aceitável para N < 1000 usuários simultâneos pagando.
- **HYDRATION_TIMEOUT no carrinho**: se inventory demora, itens aparecem sem dados; user pode remover acidentalmente. Mitigação: toast avisando que está carregando, desabilitar checkout.
- **Endereço mutável no intervalo checkout-pagamento**: backend copia endereço só no `order.confirmed`. Se usuário edita entre checkout e pagamento, envio sai para endereço novo com frete antigo. Recomendação futura: snapshot de endereço no checkout.

## Decisoes de implementacao

- **React Router data router**: rotas no layout raiz com loader/action functions; transições de etapa por programação (`navigate`), não por URL params.
- **Bricks SDK**: carregado via `<script>` no index.html ou dinâmico ao abrir tela de pagamento; fallback se falhar (user vai direto para Checkout Pro).
- **TanStack Query**: configurado com retry automático (3 vezes com backoff exponencial), invalidação manual após POST/DELETE.
- **localStorage para Idempotency-Key**: seguro pois é UUID; alternativa seria sessionStorage se preferir per-tab.
- **Componentes de erro reutilizáveis**: `<ErrorAlert code={error.code} message={error.detail} />` para cada tipo de falha.
- **Formato de moeda**: sempre string decimal com 2 casas (`"699.80"`); Intl.NumberFormat para exibição.
- **Cores**: usar tokens Tailwind direto (`text-blue-600`, `bg-blue-50`, `hover:bg-blue-700`) em vez de CSS vars.
