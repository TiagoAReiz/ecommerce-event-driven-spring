# Spec Delta: Order Confirmation

## Propósito

Fechamento do fluxo de compra: a tela que o comprador vê logo depois de pagar. A lista e o
detalhe de pedidos ficam em `add-front-orders`, e o painel da loja em `add-front-store` — aqui
só existe o ponto de chegada do checkout.

## ADDED Requirements

### Requirement: Confirmação do pedido pago

O front-end SHALL exibir uma confirmação com o número do pedido, o valor total, o método de
pagamento e o caminho para acompanhar o pedido, logo após o pagamento ser aceito.

#### Scenario: Pagamento aceito

- **WHEN** o pagamento volta com `status: "captured"` (ou `authorized`)
- **THEN** o front navega para `/checkout/done/{orderId}` e mostra número do pedido, total,
  método e os botões "Acompanhar pedido" e "Continuar comprando"

#### Scenario: Pagamento ainda pendente

- **WHEN** o pagamento volta com `status: "pending"` (PIX não pago até o momento)
- **THEN** a tela continua no pagamento, mostrando o QR code e o estado atual, sem declarar
  compra concluída

#### Scenario: Pedido pago mas saga ainda correndo

- **WHEN** o comprador chega na confirmação e o pedido ainda está em `paid`
- **THEN** a tela informa que a loja está separando o pedido, sem erro, porque a baixa de
  estoque e a criação do envio acontecem por evento, depois da resposta do pagamento
