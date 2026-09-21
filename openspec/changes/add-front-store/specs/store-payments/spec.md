# Spec Delta: Store Payments

## Purpose

Fornece interface de gestao de pagamentos para o dono da loja: listagem de pagamentos, visualizacao de status e reembolso total ou parcial com motivo.

## ADDED Requirements

### Requirement: Listar pagamentos

O sistema SHALL permitir ao owner listar pagamentos de pedidos da loja com filtro e visualizacao de status.

#### Scenario: Listar pagamentos padrao
- **WHEN** owner com escopo `payments:refund` acessa `/store/payments`
- **THEN** interface exibe tabela de pagamentos com colunas: ID, Pedido, Cliente, Status, Total, Data
- **AND** status usa cores: pending (amarelo), completed (verde), captured (verde), refunded (cinza), failed (vermelho), cancelled (cinza)
- **AND** paginacao padrao (page 0, size 20)
- **AND** HTTP 200 com resposta de `GET /payments` ou filtro equivalente

#### Scenario: Filtro por status
- **WHEN** owner faz GET para pagamentos com filtro ?status=pending
- **THEN** interface mostra apenas pagamentos com status "pending"

#### Scenario: Filtro por pedido
- **WHEN** owner faz GET com ?orderId=3301
- **THEN** interface mostra apenas pagamento desse pedido (deve ser 1)

#### Scenario: Combinacao de filtros
- **WHEN** owner faz GET com ?status=pending&orderId=3301
- **THEN** interface aplica filtros combinados

#### Scenario: Filtros persistem em URL
- **WHEN** owner aplica filtros e faz hard refresh
- **THEN** filtros sao mantidos

#### Scenario: Sem permissao
- **WHEN** usuario sem papel `owner` ou sem escopo `payments:refund` acessa `/store/payments`
- **THEN** interface redireciona para home com toast "Acesso negado"

#### Scenario: Lista vazia
- **WHEN** nao ha pagamentos na loja
- **THEN** interface mostra tabela vazia com mensagem "Nenhum pagamento encontrado"

### Requirement: Visualizar detalhes do pagamento

O sistema SHALL permitir ao owner ver detalhes completos de um pagamento.

#### Scenario: Abrir detalhe
- **WHEN** owner clica em uma linha de pagamento
- **THEN** interface abre modal/drawer com detalhes:
  - ID do pagamento
  - ID do pedido (link para detalhe do pedido)
  - Cliente (idCustomer ou nome)
  - Status atual
  - Valor total
  - Metodo de pagamento (ex: "PIX", "Cartao", "Boleto")
  - Data de criacao
  - Data de captura (se applicable)
  - External ID do Mercado Pago
  - Historico de transicoes (pending → completed, etc)

#### Scenario: Refund visivel
- **WHEN** status permite reembolso (nao eh pending, failed, cancelled, refunded)
- **THEN** modal mostra botao "Reembolsar" habilitado
- **AND** em status nao permitido, botao fica oculto ou desabilitado

#### Scenario: Fechar detalhe
- **WHEN** owner clica "X" ou area fora do modal
- **THEN** modal fecha

### Requirement: Reembolso de pagamento

O sistema SHALL permitir ao owner reembolsar (refund) um pagamento total ou parcialmente.

#### Scenario: Botao de refund
- **WHEN** payment tem status que permite refund (ex: "completed")
- **THEN** interface mostra botao "Reembolsar"
- **AND** em status "failed", "cancelled", "refunded", botao nao aparece

#### Scenario: Modal de refund
- **WHEN** owner clica "Reembolsar"
- **THEN** interface abre modal com:
  - Mensagem: "Reembolsar pagamento #xxxx"
  - Campo "Tipo de Reembolso": radio buttons "Total" (default) e "Parcial"
  - Campo "Valor": pre-preenchido com valor total do pagamento, read-only em modo Total
  - Checkbox "Estorno Parcial": se marcado, abre input de valor customizado
  - Campo "Motivo": textarea obrigatorio (≤ 500 caracteres)
  - Validacao: minimo 5 caracteres, contador ao vivo
  - Botoes: "Cancelar" e "Confirmar Reembolso"

#### Scenario: Reembolso total padrao
- **WHEN** modal de refund abre
- **THEN** "Total" eh selecionado por default
- **AND** campo "Valor" mostra o valor total (ex: "724.80") em cinza/read-only

#### Scenario: Reembolso parcial
- **WHEN** owner marca "Parcial"
- **THEN** campo "Valor" fica editavel
- **AND** owner digita valor menor (ex: "100.00")
- **AND** interface valida: valor > 0 e ≤ valor total

#### Scenario: Valor parcial invalido
- **WHEN** owner digita valor negativo ou zero em reembolso parcial
- **THEN** interface mostra erro "Valor deve ser maior que 0"

#### Scenario: Valor parcial acima do total
- **WHEN** valor total eh 724.80 e owner digita 800.00
- **THEN** interface mostra erro "Valor nao pode ser maior que o total (724.80)"

#### Scenario: Motivo obrigatorio
- **WHEN** owner deixa motivo vazio
- **THEN** interface mostra erro "Motivo eh obrigatorio"
- **AND** botao "Confirmar" desabilitado

#### Scenario: Motivo muito curto
- **WHEN** owner digita motivo com 4 caracteres
- **THEN** interface mostra erro "Minimo 5 caracteres"

#### Scenario: Reembolso com sucesso
- **WHEN** owner preenche valor (ou deixa total) e motivo, clica "Confirmar"
- **THEN** interface faz POST /payments/{id}/refund com:
  ```json
  {
    "amount": "724.80",
    "reason": "Produto indisponivel"
  }
  ```
- **AND** header `Idempotency-Key: <uuid-v4>` eh adicionado automaticamente
- **AND** resposta 200 ou 202 (202 = processamento assincrono)
- **AND** interface mostra toast: "Reembolso processado" (em caso de 202, adiciona "pode levar alguns minutos")
- **AND** modal fecha
- **AND** invalida cache de lista de pagamentos
- **AND** status muda para "refunded" na tabela

#### Scenario: Reembolso duplicado
- **WHEN** owner clica "Confirmar" duas vezes rapidamente
- **THEN** segunda requisicao usa mesma `Idempotency-Key` (deduplicada)
- **AND** server retorna resposta em cache da primeira
- **AND** no frontend parece transparente ao usuario

#### Scenario: Pagamento ja foi reembolsado
- **WHEN** owner tenta refund em pagamento ja reembolsado
- **THEN** interface captura 409 com code `ALREADY_REFUNDED`
- **AND** mostra toast "Pagamento ja foi reembolsado"

#### Scenario: Fora da janela de refund
- **WHEN** pagamento foi feito ha > 180 dias e owner tenta refund
- **THEN** interface captura 422 com code `REFUND_WINDOW_EXPIRED`
- **AND** mostra toast "Reembolso nao eh permitido apos 180 dias da compra"

#### Scenario: Valor reembolsavel esgotado
- **WHEN** pagamento de 100.00 ja teve 80.00 reembolsados e owner tenta reembolsar 50.00 novamente
- **THEN** interface captura 409 com code `INSUFFICIENT_REFUND_BALANCE`
- **AND** mostra toast "Saldo disponivel para reembolso: 20.00"

#### Scenario: Timeout do Mercado Pago
- **WHEN** POST /payments/{id}/refund retorna 504 (Mercado Pago fora)
- **THEN** interface mostra toast "Timeout ao processar reembolso, tente novamente em alguns minutos"
- **AND** nao fecha modal (usuario pode retry)

#### Scenario: Erro de servidor
- **WHEN** POST /payments/{id}/refund retorna 500
- **THEN** interface mostra toast "Erro ao processar reembolso, tente novamente"
- **AND** modal permanece aberto para retry

### Requirement: Protecao de acesso

O sistema SHALL impedir acesso de usuarios sem papel `owner` e escopo `payments:refund`.

#### Scenario: Usuario customer nao vê aba de pagamentos
- **WHEN** usuario sem papel `owner` acessa `/store`
- **THEN** abas nao incluem "Pagamentos"

#### Scenario: Token sem escopo
- **WHEN** GET para pagamentos retorna 403
- **THEN** interface mostra toast "Sem permissao para acessar pagamentos"

### Requirement: Ordenacao

O sistema SHALL ordenar pagamentos por padrao por data descendente (mais recentes primeiro).

#### Scenario: Ordenacao padrao
- **WHEN** `/store/payments` carrega
- **THEN** pagamentos sao ordenados por data de criacao (mais recentes primeiro)

#### Scenario: Reordenar por status
- **WHEN** owner clica header "Status"
- **THEN** interface ordena alfabeticamente por status

#### Scenario: Reordenar por valor
- **WHEN** owner clica header "Total"
- **THEN** interface ordena por valor (maior primeiro)

### Requirement: Paginacao

O sistema SHALL suportar paginacao com size=20.

#### Scenario: Proxima pagina
- **WHEN** owner na pagina 0 clica "Proxima"
- **THEN** interface faz GET com page=1&size=20

#### Scenario: Reset ao filtrar
- **WHEN** owner aplica novo filtro
- **THEN** lista volta para page=0

### Requirement: Feedback visual

O sistema SHALL exibir estados de loading, erro e sucesso.

#### Scenario: Loading em refund
- **WHEN** owner clica "Confirmar Reembolso" e POST esta em voo
- **THEN** botao mostra spinner e fica desabilitado
- **AND** modal nao fecha ate terminar

#### Scenario: Toast de processamento
- **WHEN** resposta 202 (assincrono)
- **THEN** interface mostra toast: "Reembolso aceito. Pode levar alguns minutos para processar."
- **AND** mostra status "refund_pending" ou similar na tabela

#### Scenario: Toast de sucesso imediato
- **WHEN** resposta 200 (síncrono)
- **THEN** interface mostra toast: "Reembolso processado com sucesso"
- **AND** status imediatamente passa para "refunded"

#### Scenario: Historico de refunds
- **WHEN** pagamento teve multiplos refunds (ex: parcial + complementar)
- **THEN** detalhe mostra tabela de refunds com: data, valor, motivo, status

### Requirement: Informacao de Data Limite

O sistema SHALL informar ao owner quando um pagamento esta chegando perto do limite de refund.

#### Scenario: Alerta de data limite
- **WHEN** pagamento foi feito ha > 170 dias (10 dias antes do limite)
- **THEN** interface mostra badge amarela ou alerta perto do status
- **AND** tooltip: "Reembolso disponivel ate XX/XX/2026"

#### Scenario: Bloqueio de refund
- **WHEN** pagamento foi feito ha > 180 dias
- **THEN** botao "Reembolsar" desabilitado com tooltip "Prazo de reembolso expirado"
