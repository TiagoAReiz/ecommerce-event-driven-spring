# Spec Delta: Order Cancellation

## Purpose

Permite que o comprador cancele seu pedido com um motivo, desde que o status permita (apenas
`pending` ou `paid`). A cancelação dispara eventos de estorno assíncrono e reflete erros `409`
quando o estado não permite. Interface valida comprador e oferece feedback claro.

## ADDED Requirements

### Requirement: Botão de cancelamento visível apenas em estados permitidos

O sistema SHALL exibir botão "Cancelar Pedido" apenas quando o pedido está em `pending` ou `paid`,
desabilitado ou invisível para outros estados.

#### Scenario: Pedido em pending mostra botão habilitado
- **WHEN** detalhe de pedido mostra `status: "pending"`
- **THEN** botão "Cancelar Pedido" é visível e habilitado (não-disabled)
- **AND** posicionado ao final da timeline ou em seção de ações

#### Scenario: Pedido em paid mostra botão habilitado
- **WHEN** status é `paid` (pagamento aprovado)
- **THEN** botão "Cancelar Pedido" é visível e habilitado
- **AND** label é idêntico ("Cancelar Pedido")

#### Scenario: Pedido em processing ou além não mostra botão
- **WHEN** status é `processing`, `shipped`, `delivered`, `cancelled` ou `refunded`
- **THEN** botão não é exibido (ou exibido disabled com tooltip "Não é possível cancelar este pedido")

#### Scenario: Pedido cancelado mostra estado final
- **WHEN** status é `cancelled`
- **THEN** timeline mostra "Cancelado em [data]"
- **AND** botão de cancelamento desaparece
- **AND** mensagem opcional: "Este pedido foi cancelado" se houver `cancel_reason`

### Requirement: Modal de cancelamento com validação de motivo

O sistema SHALL exibir um modal ao clicar "Cancelar Pedido" com textarea obrigatória para motivo
(até 500 caracteres), validação de comprimento em tempo real e botões de ação.

#### Scenario: Abertura do modal de cancelamento
- **WHEN** usuário clica botão "Cancelar Pedido"
- **THEN** modal overlay aparece com:
  - Título: "Cancelar Pedido"
  - Subtítulo: "Tem certeza? Após cancelar, será necessário fazer um novo pedido."
  - Campo textarea com placeholder: "Motivo do cancelamento (até 500 caracteres)"
  - Badge indicador de caracteres: "0/500"
  - Botões: "Voltar" e "Confirmar Cancelamento"
  - Botão "Confirmar" inicia desabilitado até usuário preencher algo

#### Scenario: Validação de comprimento em tempo real
- **WHEN** usuário digita no textarea
- **THEN** badge de contador atualiza em tempo real: "23/500"
- **AND** quando atinge 500, campo para aceitar input
- **AND** input é vermelho/com aviso se > 500 (CSS)

#### Scenario: Motivo mínimo de 1 caractere
- **WHEN** textarea está vazio
- **THEN** botão "Confirmar Cancelamento" permanece disabled
- **AND** se usuário clica em "Confirmar" sem motivo, nada acontece

#### Scenario: Botão de voltar
- **WHEN** usuário clica "Voltar"
- **THEN** modal fecha
- **AND** estado da textarea é descartado

### Requirement: Envio de cancelamento e tratamento de 409

O sistema SHALL executar `POST /orders/{id}/cancel` com o motivo, capturar resposta `200` (sucesso)
e `409` (estado não permite, já foi cancelado), exibindo mensagens específicas.

#### Scenario: Cancelamento bem-sucedido
- **WHEN** usuário preenche motivo e clica "Confirmar Cancelamento"
- **AND** requisição `POST /orders/{id}/cancel` retorna `200`
- **THEN** modal fecha
- **AND** página refetch o detalhe do pedido
- **AND** status é atualizado para `cancelled`
- **AND** timeline mostra "Cancelado em [data]"
- **AND** toast verde: "Pedido cancelado com sucesso"

#### Scenario: Cancelamento com estorno assíncrono
- **WHEN** pedido estava em `paid` (com `payment_id`)
- **AND** cancelamento é enviado
- **THEN** resposta `200` inclui `status: "cancelled"`
- **AND** backend publica `order.refund.requested` internamente
- **AND** estorno é processado assincronamente (comprador vai de `paid` → `cancelled` → `refunded`
  após evento `payment.refunded` chegar)
- **AND** repolling de 30 s atualiza status conforme eventos chegam

#### Scenario: Erro 409 — pedido já foi cancelado
- **WHEN** usuário tenta cancelar novamente (duplo-click ou aba aberta duplicada)
- **AND** servidor retorna `409` com código `ALREADY_CANCELLED`
- **THEN** modal mostra mensagem de erro: "Este pedido já foi cancelado"
- **AND** botão de fechar ou voltar
- **AND** página não é redirecionada, permite tentar novamente após fechar

#### Scenario: Erro 409 — status não permite (já em trânsito ou entregue)
- **WHEN** pedido está em `shipped` ou `delivered` (estado mudou entre carregamento e clique)
- **AND** servidor retorna `409` com código `INVALID_STATE_TRANSITION`
- **THEN** modal mostra: "Não é possível cancelar pedido que já foi enviado. O caminho é devolução,
  não cancelamento."
- **AND** modal desabilita o campo de motivo (read-only visual)
- **AND** botão é "Entendi" em vez de "Confirmar"

#### Scenario: Erro 409 — motivo acima de 500 caracteres
- **WHEN** textarea tem > 500 caracteres (validação local falhou)
- **AND** de alguma forma requisição é enviada (edge case)
- **THEN** servidor retorna `400`
- **AND** mensagem: "Motivo deve ter até 500 caracteres"

#### Scenario: Erro 400 — motivo ausente
- **WHEN** requisição é enviada sem campo `reason`
- **THEN** servidor retorna `400`
- **AND** modal mostra: "Motivo é obrigatório"

#### Scenario: Erro 500 / 503 — indisponibilidade
- **WHEN** servidor retorna erro de infraestrutura
- **THEN** modal mostra: "Erro ao cancelar pedido. Tente novamente."
- **AND** botão de retry está disponível (resubmete cancelamento)
- **AND** spinner de loading enquanto requisição está em flight

#### Scenario: Token expirado durante cancelamento
- **WHEN** requisição retorna `401`
- **THEN** modal fecha
- **AND** usuário é redirecionado para login

#### Scenario: Sem permissão (nem dono nem owner)
- **WHEN** requisição retorna `403`
- **THEN** modal mostra: "Você não tem permissão para cancelar este pedido"
- **AND** botão de fechar

### Requirement: Validação de estado no cliente

O sistema SHALL desabilitar o botão "Cancelar Pedido" e/ou exibir motivo de bloqueio se o status
não está em `pending` ou `paid`, mesmo que HTML esconda/mostre o botão.

#### Scenario: Tooltip de bloqueio
- **WHEN** status não é cancelável (ex: `shipped`)
- **THEN** botão "Cancelar Pedido" aparece disabled (opacity reduzida, cursor not-allowed)
- **AND** hover mostra tooltip: "Pedidos em trânsito não podem ser cancelados"

#### Scenario: Mudança de estado entre carregamento e clique
- **WHEN** página carrega com `pending`, mas entre esse moment e clique, status vira `processing`
- **THEN** cliente não previne requisição (server valida)
- **AND** servidor retorna `409`
- **AND** front exibe mensagem amigável

### Requirement: Integração com autenticação e autorização

O sistema SHALL validar que cancelamento é feito apenas pelo dono do pedido (usuário) ou pelo
`owner` (loja), usando token Bearer com escopo `orders:write`.

#### Scenario: Token com escopo orders:write
- **WHEN** requisição envia `Authorization: Bearer eyJ...` com `scope: "orders:write"`
- **THEN** requisição é aceita

#### Scenario: Token sem escopo orders:write
- **WHEN** token tem `scope: "products:read"` em vez de `orders:write`
- **THEN** servidor retorna `403`
- **AND** modal: "Sem permissão para cancelar pedidos"

#### Scenario: Tentativa de cancelar pedido alheio (sem papel owner)
- **WHEN** usuário A tenta cancelar `/orders/{id_usuario_b}` e não é owner
- **THEN** servidor retorna `403`
- **AND** mensagem: "Você só pode cancelar seus próprios pedidos"

#### Scenario: Owner (loja) pode cancelar pedido de qualquer cliente
- **WHEN** token tem papel `owner` e escopo `orders:write`
- **AND** usuario abre detalhe de pedido de outro cliente
- **THEN** botão "Cancelar Pedido" permanece visível se status permite
- **AND** cancelamento funciona normalmente
