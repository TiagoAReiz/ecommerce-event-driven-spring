# Proposal

> **Nota de implementação.** A stack do front mudou depois desta proposta: saiu Vite + React
> Router e entrou Next.js com App Router, com a vitrine pública renderizada no servidor. As
> telas, as rotas de API e o desenho descritos aqui continuam valendo; o motivo da troca está
> em `docs/decisions.md`.


## Why

O front-end não possui interface de compra. Hoje não há carrinho visual, checkout com validação de endereço, cálculo de frete ou pagamento pelo Mercado Pago. O usuário não consegue comprar e a loja não tem receita.

## What Changes

- **Carrinho**: tela com listagem de itens, quantidade, preço unitário e total; alertas visuais para produto indisponível ou preço alterado; operações de adicionar, remover, alterar quantidade e esvaziar.
- **Checkout em etapas**: seleção de endereço existente ou cadastro de endereço novo, exibição de frete e total calculados pelo servidor, revisão do pedido e confirmação com geração de `Idempotency-Key`.
- **Pagamento com três modalidades**: PIX com QR code e código copiável + verificação periódica do status; cartão tokenizado pelo SDK do Mercado Pago (número nunca vai ao backend); Checkout Pro com redirecionamento.
- **Confirmação do pedido**: exibição de número, data, status e próximos passos (confirmar pagamento, acompanhar envio).
- **Painel de gestão**: listagem de pedidos da loja com filtros de status e cliente (para o owner).
- **Tela de acompanhamento**: visualização de pedidos passados e envios com status.
- **Comportamento em ambiente fake**: funcionamento completo sem credenciais reais, com status simulado.

## Capabilities

### New Capabilities

- `shopping-cart-ui`: interface visual do carrinho com hidratação dinâmica de produtos, avisos de indisponibilidade ou mudança de preço, operações de edição.
- `checkout-flow`: fluxo de checkout em etapas com seleção/cadastro de endereço, cálculo de frete, confirmação idempotente e geração de `Idempotency-Key`.
- `payment-ui`: interface de pagamento com três modalidades integradas ao Mercado Pago (PIX com QR code, cartão tokenizado, Checkout Pro).
- `order-confirmation`: tela de confirmação logo após o pagamento, com o número do pedido e o
  caminho para acompanhar. A lista e o detalhe de pedidos são de `add-front-orders`; o painel
  da loja é de `add-front-store`.

### Modified Capabilities

- `navigation`: adição de rotas para carrinho, checkout, pagamento, confirmação do pedido, histórico de pedidos.
- `auth`: integração com o token Bearer para autorização nas operações de carrinho e pedido.

## Impact

- Nova estrutura `front/src/features/checkout/` com componentes de carrinho, checkout, pagamento e confirmação.
- Integração com TanStack Query para cache e refetch das operações HTTP.
- Integração com SDK Bricks do Mercado Pago para tokenização segura do cartão.
- Adição de rotas no React Router data router para cada etapa do fluxo.
- Componentes Tailwind CSS reutilizáveis para alertas, cards, botões de ação.
- Hooks customizados para lógica de carrinho, checkout, idempotência e polling de pagamento.
