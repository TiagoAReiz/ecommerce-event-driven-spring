# Spec Delta: Orders Management

## Purpose

Permite que o comprador visualize sua lista de pedidos com filtros, paginação e estados dinâmicos,
acesse o detalhe de cada pedido com itens, valores, projeções de pagamento e envio, e acompanhe
em tempo real enquanto o status muda de `pending` para `delivered` via repolling automático.

## ADDED Requirements

### Requirement: Listar pedidos do comprador com filtros e paginação

O sistema SHALL exibir lista paginada de pedidos do usuário autenticado, permitindo filtro por
status e período.

#### Scenario: Carregamento bem-sucedido da lista de pedidos
- **WHEN** usuário autentico navega para `/orders`
- **THEN** tela exibe lista com até 20 pedidos (default), ordenados por `createdAt` descendente
- **AND** cada item mostra: id, status (badge), valor total, item count, foto do primeiro produto

#### Scenario: Filtro por status único
- **WHEN** usuário seleciona status `paid` no dropdown de filtros
- **THEN** requisição envia `status=paid` em query string
- **AND** lista é recarregada mostrando apenas pedidos com esse status
- **AND** filtro permanece selecionado enquanto usuário navega

#### Scenario: Filtro por período (from/to)
- **WHEN** usuário escolhe data inicial e final com date pickers
- **AND** clica "Buscar" ou seleção se aplica automaticamente
- **THEN** requisição envia `from=2026-09-01&to=2026-09-21` em ISO 8601
- **AND** lista mostra pedidos apenas nesse período

#### Scenario: Filtros múltiplos de status
- **WHEN** usuário seleciona múltiplos status (`paid` e `processing`)
- **THEN** requisição envia `status=paid&status=processing` (repetível em query)
- **AND** lista mostra pedidos com qualquer desses status

#### Scenario: Paginação para página seguinte
- **WHEN** usuário clica "Próxima" ou página 2 na paginação
- **THEN** requisição envia `page=1&size=20`
- **AND** lista carrega novos 20 pedidos da página 2

#### Scenario: Erro ao carregar lista
- **WHEN** servidor retorna `500` ou `503`
- **THEN** skeleton loader desaparece e exibe toast "Erro ao carregar pedidos. Tente novamente."
- **AND** botão de retry está disponível

#### Scenario: Token expirado ou ausente
- **WHEN** requisição retorna `401`
- **THEN** usuário é redirecionado para página de login

#### Scenario: Sem permissão (falta escopo)
- **WHEN** requisição retorna `403`
- **THEN** exibe mensagem "Você não tem permissão para acessar seus pedidos"

#### Scenario: Lista vazia
- **WHEN** usuário não tem nenhum pedido
- **THEN** exibe mensagem de empty state: "Você não tem pedidos ainda. Acesse o catálogo e
compre agora!"
- **AND** botão de link para catálogo está visível

### Requirement: Exibir detalhe do pedido com timeline de status

O sistema SHALL exibir todas as informações de um pedido específico, incluindo itens, valores,
projeções de payment/shipment e uma timeline visual de transições de status.

#### Scenario: Carregar detalhe bem-sucedido
- **WHEN** usuário clica em um pedido da lista ou acessa `/orders/{id}` diretamente
- **THEN** página carrega com: id, status (badge), `itemsCost`, `freightCost`, `totalCost`,
  lista de itens com foto/nome/preço/qtd/linha total

#### Scenario: Projeção de payment nula até evento chegar
- **WHEN** pedido foi criado mas evento `payment.approved` ainda não chegou
- **THEN** campo `payment` é `null`
- **AND** exibe placeholder: "Aguardando confirmação de pagamento..."

#### Scenario: Projeção de payment após evento
- **WHEN** evento `payment.approved` chega e pedido muda para `paid`
- **THEN** projeção `payment` é preenchida com `id`, `status: "captured"`, `provider: "mercadopago"`
- **AND** tela refetch automático (repolling) mostra atualização

#### Scenario: Projeção de shipment nula até confirmação de pedido
- **WHEN** pedido está em `pending` ou `paid`, sem baixa de estoque ainda
- **THEN** campo `shipment` é `null`
- **AND** exibe placeholder: "Envio será gerado após confirmação de pagamento"

#### Scenario: Projeção de shipment preenchida após ordem confirmada
- **WHEN** evento `order.confirmed` chega (após estoque baixado)
- **THEN** projeção `shipment` mostra: `id`, `status: "pending"`, `trackingCode: null`

#### Scenario: Timeline visual de status
- **WHEN** detalhe é exibido
- **THEN** componente `OrderStatusTimeline` mostra círculos conectados para cada status
- **AND** status completados têm círculo preenchido (azul `#1d4ed8`)
- **AND** status em progresso têm círculo com animação
- **AND** status futuros têm círculo vazio
- **AND** cada status tem label e timestamp (ex: "Pago em 2026-09-17 14:06:31")

#### Scenario: Transição de status visível via repolling
- **WHEN** pedido está em `pending` e payment é processado no Mercado Pago
- **THEN** após até 30 s, repolling refetch `GET /orders/{id}`
- **AND** status muda para `paid`, projeção de payment é preenchida
- **AND** timeline avança visualmente

#### Scenario: Repolling para quando status é final
- **WHEN** status vira `delivered`, `cancelled` ou `refunded`
- **THEN** `setInterval` de repolling é cancelado (clearInterval)
- **AND** nenhuma requisição adicional é feita
- **AND** se usuário volta para lista e entra no detalhe novamente, repolling reinicia
  se status não é final

#### Scenario: Erro ao carregar detalhe
- **WHEN** servidor retorna `500` ou `503`
- **THEN** exibe mensagem de erro e botão de retry

#### Scenario: Pedido não existe
- **WHEN** usuario acessa `/orders/999999` (id inexistente)
- **THEN** servidor retorna `404`
- **AND** página exibe: "Pedido não encontrado" com link de volta para lista

#### Scenario: Pedido é de outro usuário
- **WHEN** usuario A tenta acessar `/orders/{id_de_usuario_b}`
- **THEN** servidor retorna `404` (nunca `403`)
- **AND** página exibe: "Pedido não encontrado"

### Requirement: Revalidação periódica enquanto status não-final

O sistema SHALL executar repolling automático a cada 30 segundos enquanto o pedido está em
estado não-final (`pending`, `paid`, `processing`, `shipped`), parando quando entra em estado
final (`delivered`, `cancelled`, `refunded`).

#### Scenario: Repolling inicia ao abrir detalhe
- **WHEN** usuário abre `/orders/{id}` e status não é final
- **THEN** `useEffect` configura `setInterval(() => refetch(), 30000)`
- **AND** primeira requisição já carregou via data loader, segunda sai após 30 s

#### Scenario: Repolling respeita staleTime do TanStack Query
- **WHEN** repolling dispara e `staleTime: 30000` ainda não expirou (mesmos 30 s)
- **THEN** TanStack Query usa cache em vez de fazer fetch
- **AND** não há requisição desnecessária

#### Scenario: Repolling pula para status final
- **WHEN** pedido estava em `shipping` e evento `shipment.delivered` faz transição para
  `delivered`
- **THEN** refetch mostra status como final
- **AND** `useEffect` detecta mudança de `order.status`, corre lógica `!isFinal(status)`
- **AND** `setInterval` é cancelado via `clearInterval(timer)`
- **AND** nenhuma requisição adicional sai

#### Scenario: User navega para fora e volta
- **WHEN** usuário está em `/orders/3301`, vai para `/orders`, volta para `/orders/3301`
- **THEN** ao voltar, `useEffect` roda novamente
- **AND** se status continua não-final, novo `setInterval` é configurado
- **AND** o antigo `setInterval` foi limpo pela dependency change

#### Scenario: Repolling com conexão lenta
- **WHEN** requisição de repolling leva 15 s, mas intervalo é 30 s
- **THEN** primeira requisição completa em 15 s, segunda dispara em 30 s (15+15=30 total)
- **AND** requisições não se sobrepõem

#### Scenario: Erro em repolling não interrompe o intervalo
- **WHEN** refetch falha (ex: timeout 504)
- **THEN** toast de erro é exibido brevemente
- **AND** repolling continua a cada 30 s
- **AND** usuário vê a última versão bem-sucedida em tela

### Requirement: Acesso via token Bearer e autorização

O sistema SHALL validar que toda requisição a `/orders/**` carrega token Bearer válido no header
`Authorization` com escopo `orders:read`.

#### Scenario: Token válido com escopo correto
- **WHEN** requisição envia `Authorization: Bearer eyJhbGc...` (válido) com `scope: "orders:read"`
- **THEN** requisição é aceita e lista/detalhe é retornado

#### Scenario: Token expirado
- **WHEN** token tem `exp` menor que horário atual
- **THEN** servidor retorna `401`
- **AND** front redireciona para login

#### Scenario: Token com escopo insuficiente
- **WHEN** requisição envia token com `scope: "products:read"` em vez de `orders:read`
- **THEN** servidor retorna `403`
- **AND** mensagem: "Sem permissão para acessar seus pedidos"

#### Scenario: Header `Authorization` ausente
- **WHEN** requisição não inclui header
- **THEN** servidor retorna `401`
- **AND** front redireciona para login
