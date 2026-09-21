# Spec Delta: Shipment Tracking

## Purpose

Permite que o comprador acompanhe seus envios em tempo real, visualizando origem, destino, código
de rastreamento e status. O comprador confirma a entrega manualmente (ação exclusiva do
comprador, não transportadora), com modal de confirmação para evitar clicks acidentais.

## ADDED Requirements

### Requirement: Listar envios do comprador com filtros e paginação

O sistema SHALL exibir lista paginada de envios do usuário autenticado, permitindo filtro por
status.

#### Scenario: Carregamento bem-sucedido de envios
- **WHEN** usuário autentico navega para `/orders/shipments`
- **THEN** tela exibe lista com até 20 envios (default), ordenados por `updatedAt` descendente
- **AND** cada item mostra: id, id do pedido associado, status (badge), código de rastreamento,
  cidade/estado destino, data da última atualização

#### Scenario: Filtro por status único
- **WHEN** usuário seleciona status `in_transit` no dropdown de filtros
- **THEN** requisição envia `status=in_transit` em query string
- **AND** lista é recarregada mostrando apenas envios com esse status
- **AND** filtro permanece selecionado

#### Scenario: Filtro por orderId
- **WHEN** usuário filtra por `orderId=3301` (ex: vindo da página de detalhe do pedido)
- **THEN** requisição envia `orderId=3301`
- **AND** lista mostra apenas o envio desse pedido (max 1 por pedido)

#### Scenario: Paginação para próxima página
- **WHEN** usuário clica "Próxima" na paginação
- **THEN** requisição envia `page=1&size=20`
- **AND** lista carrega novos envios da página 2

#### Scenario: Erro ao carregar lista
- **WHEN** servidor retorna `500` ou `503`
- **THEN** exibe toast "Erro ao carregar envios. Tente novamente."
- **AND** skeleton loader é exibido enquanto carrega

#### Scenario: Lista vazia
- **WHEN** usuário não tem nenhum envio
- **THEN** exibe mensagem: "Você não tem envios registrados ainda."

#### Scenario: Token expirado
- **WHEN** requisição retorna `401`
- **THEN** usuário é redirecionado para login

### Requirement: Exibir detalhe de envio com origem, destino e código

O sistema SHALL mostrar todas as informações de um envio, incluindo origem (endereço da loja),
destino (endereço do comprador), status atual, código de rastreamento e timeline de transições.

#### Scenario: Carregar detalhe bem-sucedido
- **WHEN** usuário clica em um envio da lista ou acessa `/orders/shipments/{id}`
- **THEN** página mostra:
  - ID do envio e ID do pedido associado
  - Status (badge com cor indicativa)
  - Seção "Origem": endereço da loja (completo: rua, número, cidade, estado, CEP)
  - Seção "Destino": endereço de entrega (completo)
  - Código de rastreamento (se disponível, caso contrário: "Aguardando código")
  - Data de atualização do status
  - Frete cobrado

#### Scenario: Código de rastreamento nulo (envio pendente)
- **WHEN** envio está em `pending` ou `ready_to_ship` e `trackingCode` é `null`
- **THEN** campo de rastreamento mostra placeholder: "Código será gerado no despacho"
- **AND** botão de copiar código fica desabilitado

#### Scenario: Código de rastreamento preenchido (enviado)
- **WHEN** envio está em `in_transit` e `trackingCode: "AA123456789BR"`
- **THEN** código é exibido em campo copiável
- **AND** botão "Copiar código" copia para clipboard e exibe toast "Código copiado"

#### Scenario: Timeline de status do envio
- **WHEN** detalhe é exibido
- **THEN** componente de timeline mostra:
  - `pending` → `ready_to_ship` → `in_transit` → `out_for_delivery` → `delivered`
  - Ou variações: `pending` → `cancelled`
  - Cada transição tem timestamp (ex: "Despachado em 2026-09-19 08:11:00")
  - Status completados têm círculo preenchido, futuro-s vazios

#### Scenario: Erro ao carregar detalhe
- **WHEN** servidor retorna `500`
- **THEN** exibe mensagem de erro e botão retry

#### Scenario: Envio não existe
- **WHEN** usuario acessa `/orders/shipments/999999` (id inexistente)
- **THEN** servidor retorna `404`
- **AND** página exibe: "Envio não encontrado" com link de volta para lista

#### Scenario: Envio é de outro usuário (sem acesso)
- **WHEN** usuario A tenta acessar `/orders/shipments/{id_usuario_b}`
- **THEN** servidor retorna `404`
- **AND** página exibe: "Envio não encontrado"

### Requirement: Botão "Confirmar Entrega" com modal de confirmação

O sistema SHALL exibir botão "Confirmar Entrega" apenas quando o envio está em `in_transit` ou
`out_for_delivery`, com modal de confirmação para evitar ações acidentais.

#### Scenario: Botão visível em in_transit
- **WHEN** envio tem `status: "in_transit"`
- **THEN** botão "Confirmar Entrega" é exibido e habilitado (não-disabled)
- **AND** posicionado em seção de ações

#### Scenario: Botão visível em out_for_delivery
- **WHEN** envio tem `status: "out_for_delivery"`
- **THEN** botão "Confirmar Entrega" é exibido e habilitado

#### Scenario: Botão não visível para outros status
- **WHEN** envio está em `pending`, `ready_to_ship`, `delivered`, `cancelled`, `returned`
- **THEN** botão não é exibido (ou exibido desabilitado com tooltip)

#### Scenario: Modal de confirmação abre ao clicar
- **WHEN** usuário clica "Confirmar Entrega"
- **THEN** modal overlay aparece com:
  - Título: "Confirmar Recebimento"
  - Mensagem: "Você declara que recebeu este pedido? Esta ação é irreversível."
  - Botões: "Cancelar" e "Confirmar Recebimento"
  - Ícone de alerta ou informação

#### Scenario: Botão "Cancelar" no modal
- **WHEN** usuário clica "Cancelar"
- **THEN** modal fecha
- **AND** nenhuma ação é executada

#### Scenario: Botão "Confirmar Recebimento" no modal
- **WHEN** usuário clica "Confirmar Recebimento"
- **THEN** requisição `POST /shipments/{id}/confirm-delivery` é enviada
- **AND** spinner de loading é exibido no modal durante requisição

### Requirement: Envio de confirmação de entrega com tratamento de 409

O sistema SHALL executar `POST /shipments/{id}/confirm-delivery`, capturar resposta `200`
(sucesso, incluindo idempotência) e `409` (estado não permite), exibindo mensagens específicas.

#### Scenario: Confirmação bem-sucedida
- **WHEN** requisição retorna `200`
- **THEN** modal fecha
- **AND** página refetch o detalhe do envio
- **AND** status é atualizado para `delivered`
- **AND** timeline mostra "Entregue em [data]"
- **AND** botão "Confirmar Entrega" desaparece
- **AND** toast verde: "Entrega confirmada com sucesso"
- **AND** notificação de que pedido agora pode ser avaliado

#### Scenario: Idempotência — já estava entregue
- **WHEN** envio já tem `status: "delivered"`
- **AND** usuário clica "Confirmar Entrega" novamente
- **AND** servidor retorna `200` (idempotente)
- **THEN** modal fecha
- **AND** toast: "Entrega já estava confirmada"
- **AND** nenhuma mudança visual ocorre

#### Scenario: Erro 409 — status ainda não despachado (pending/ready_to_ship)
- **WHEN** envio está em `pending` (não foi enviado ainda)
- **AND** usuario clica "Confirmar Entrega"
- **AND** servidor retorna `409` com razão `NOT_YET_SHIPPED`
- **THEN** modal mostra mensagem: "Envio ainda não foi despachado. Aguarde o despacho."
- **AND** botão é "Entendi" em vez de "Confirmar"

#### Scenario: Erro 409 — status inválido (cancelled/returned)
- **WHEN** envio foi cancelado ou devolvido
- **AND** servidor retorna `409`
- **THEN** modal mostra: "Este envio não pode ser confirmado como entregue (foi cancelado/devolvido)"
- **AND** botão é "Entendi"

#### Scenario: Erro 500 / 503 — indisponibilidade
- **WHEN** servidor retorna erro de infraestrutura
- **THEN** modal mostra: "Erro ao confirmar entrega. Tente novamente."
- **AND** botão de retry está disponível
- **AND** spinner enquanto requisição em flight

#### Scenario: Token expirado durante confirmação
- **WHEN** requisição retorna `401`
- **THEN** modal fecha
- **AND** usuário é redirecionado para login

#### Scenario: Sem permissão (não é o comprador do envio)
- **WHEN** requisição retorna `403`
- **THEN** modal mostra: "Você não tem permissão para confirmar este envio"
- **AND** botão de fechar

### Requirement: Revalidação periódica enquanto status não-final

O sistema SHALL repolling automático do detalhe de envio a cada 30 segundos enquanto status não
é `delivered`, parando quando entra em estado final.

#### Scenario: Repolling inicia ao abrir detalhe
- **WHEN** usuário abre `/orders/shipments/{id}` e status não é `delivered`
- **THEN** `useEffect` configura `setInterval(() => refetch(), 30000)`
- **AND** primeira requisição já carregou via data loader

#### Scenario: Repolling para quando entregue
- **WHEN** envio muda para `delivered`
- **THEN** repolling é cancelado (clearInterval)
- **AND** nenhuma requisição adicional é feita

#### Scenario: Erro em repolling não interrompe intervalo
- **WHEN** refetch falha (ex: timeout)
- **THEN** toast de erro é exibido brevemente
- **AND** repolling continua

### Requirement: Integração com autenticação

O sistema SHALL validar que toda requisição a `/shipments/**` carrega token Bearer válido com
escopo `shipments:read` (leitura) ou `shipments:write` (confirmação de entrega).

#### Scenario: Token válido com escopo shipments:read
- **WHEN** requisição `GET /shipments` envia token com `scope: "shipments:read"`
- **THEN** requisição é aceita

#### Scenario: Token válido com escopo shipments:write
- **WHEN** requisição `POST /shipments/{id}/confirm-delivery` envia token com `scope: "shipments:write"`
- **THEN** requisição é aceita

#### Scenario: Token com escopo insuficiente
- **WHEN** requisição envia token com escopo diferente (ex: `orders:read`)
- **THEN** servidor retorna `403`
- **AND** mensagem: "Sem permissão para acessar envios"

#### Scenario: Header Authorization ausente
- **WHEN** requisição não inclui header
- **THEN** servidor retorna `401`
- **AND** front redireciona para login

### Requirement: Link de navegação entre pedido e envio

O sistema SHALL permitir navegação bidirecional entre detalhe do pedido e detalhe do envio.

#### Scenario: Link do pedido para envio
- **WHEN** detalhe do pedido mostra projeção de `shipment` com `id`
- **THEN** `id` é um link para `/orders/shipments/{shipment.id}`

#### Scenario: Link do envio para pedido
- **WHEN** detalhe do envio mostra `idOrder`
- **THEN** `idOrder` é um link para `/orders/{idOrder}`
