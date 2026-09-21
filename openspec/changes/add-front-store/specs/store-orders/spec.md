# Spec Delta: Store Orders

## Purpose

Fornece interface de gestao de pedidos para o dono da loja: listagem de todos os pedidos, filtros, visualizacao de detalhes e cancelamento com motivo.

## ADDED Requirements

### Requirement: Listar pedidos da loja

O sistema SHALL permitir ao owner listar todos os pedidos feitos na loja com filtros de status, cliente e intervalo de data.

#### Scenario: Listar pedidos padrao
- **WHEN** owner com escopo `sales:read` acessa `/store/orders`
- **THEN** interface exibe tabela de pedidos com colunas: ID, Cliente, Status, Total, Data
- **AND** status usa cores: pending (amarelo), paid (azul), processing (azul claro), shipped (verde claro), delivered (verde), cancelled (cinza), refunded (cinza)
- **AND** paginacao padrao (page 0, size 20) com controles anterior/proxima
- **AND** HTTP 200 com resposta de `GET /orders/manage`

#### Scenario: Filtro por status unico
- **WHEN** owner faz GET /orders/manage?status=paid
- **THEN** interface mostra apenas pedidos com status "paid"

#### Scenario: Filtro por multiplos status
- **WHEN** owner faz GET /orders/manage?status=pending&status=paid
- **THEN** interface mostra pedidos que estao em "pending" OU "paid"

#### Scenario: Filtro por cliente
- **WHEN** owner faz GET /orders/manage?customerId=42
- **THEN** interface mostra apenas pedidos do cliente com id 42

#### Scenario: Filtro por intervalo de data
- **WHEN** owner faz GET /orders/manage?from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z
- **THEN** interface mostra pedidos criados entre essas datas

#### Scenario: Combinacao de filtros
- **WHEN** owner faz GET /orders/manage?status=paid&customerId=42&from=2026-09-01T00:00:00Z
- **THEN** interface aplica todos os filtros combinados (AND)

#### Scenario: Filtros persistem em URL
- **WHEN** owner aplica filtros e faz hard refresh (F5)
- **THEN** filtros sao mantidos via query params
- **AND** URL mostra `?status=paid&customerId=42&from=...`

#### Scenario: Limpar filtros
- **WHEN** owner clica botao "Limpar Filtros" ou "Reset"
- **THEN** interface reseta todos os filtros para defaults
- **AND** lista volta a mostrar todos os pedidos (page 0)

#### Scenario: Sem permissao
- **WHEN** usuario sem papel `owner` tenta acessar `/store/orders`
- **THEN** interface redireciona para home com toast "Acesso negado"

#### Scenario: Token expirado
- **WHEN** owner com token expirado faz GET /orders/manage
- **THEN** interface captura 401 e redireciona para login

#### Scenario: Lista vazia
- **WHEN** owner nao tem nenhum pedido (nova loja)
- **THEN** interface mostra tabela vazia com mensagem "Nenhum pedido encontrado"

### Requirement: Exibir detalhe do pedido

O sistema SHALL permitir ao owner visualizar todos os detalhes de um pedido: cliente, items, endereco, pagamento, status.

#### Scenario: Abrir detalhe
- **WHEN** owner clica em uma linha de pedido da tabela
- **THEN** interface abre modal com informacoes completas do pedido
- **AND** modal mostra:
  - ID e Status do pedido
  - Dados do cliente (pode ser idCustomer breve ou nome se cached)
  - Tabela de items: Nome, Foto, Quantidade, Preco unitario, Subtotal
  - Endereco de entrega: rua, numero, cidade, estado, CEP
  - Resumo financeiro: subtotal, frete, total
  - Status do pagamento (com external ID se disponivel)
  - Status do envio (com tracking code se disponivel)

#### Scenario: Cliente identificado
- **WHEN** detalhe do pedido carrega
- **THEN** cliente eh identificado por `idCustomer` (numero)
- **AND** se interface cacheou perfil do usuario, mostra nome; senao, mostra apenas ID
- **AND** opcional: link para "Ver perfil do cliente"

#### Scenario: Items com fotos
- **WHEN** detalhe mostra items do pedido
- **THEN** cada item exibe thumbnail da foto do produto
- **AND** foto nao carrega: mostra placeholder

#### Scenario: Fechar detalhe
- **WHEN** owner clica "X" ou area fora do modal
- **THEN** modal fecha
- **AND** tabela de pedidos permanece visible

#### Scenario: Pedido nao encontrado
- **WHEN** owner tenta abrir pedido com ID inexistente
- **THEN** interface captura 404
- **AND** mostra toast "Pedido nao encontrado"
- **AND** modal nao abre

### Requirement: Cancelar pedido

O sistema SHALL permitir ao owner cancelar pedidos nos status `pending`, `paid` ou `processing`, com motivo obrigatorio.

#### Scenario: Botao cancelar disponivel
- **WHEN** detalhe de pedido em status `pending` eh aberto
- **THEN** modal mostra botao "Cancelar Pedido" habilitado
- **AND** em pedidos `paid` e `processing`, botao tambem aparece
- **AND** em pedidos `shipped`, `delivered`, `cancelled`, `refunded`, botao eh oculto

#### Scenario: Modal de cancelamento
- **WHEN** owner clica "Cancelar Pedido"
- **THEN** interface abre modal confirmacao com:
  - Mensagem: "Tem certeza que deseja cancelar esse pedido?"
  - Textarea para "Motivo do cancelamento" (≤ 500 caracteres)
  - Validacao: minimo 5 caracteres, contador ao vivo
  - Botoes: "Cancelar" (fecha modal) e "Confirmar Cancelamento"

#### Scenario: Motivo obrigatorio
- **WHEN** owner deixa motivo vazio e clica "Confirmar Cancelamento"
- **THEN** interface mostra erro "Motivo eh obrigatorio"
- **AND** botao "Confirmar" fica desabilitado

#### Scenario: Motivo muito curto
- **WHEN** owner digita motivo com 4 caracteres
- **THEN** interface mostra erro "Minimo 5 caracteres"

#### Scenario: Motivo muito longo
- **WHEN** owner digita motivo acima de 500 caracteres
- **THEN** interface corta o input (nao deixa digitar mais)
- **AND** mostra contador: "234/500"

#### Scenario: Cancelar com sucesso
- **WHEN** owner digita motivo valido e clica "Confirmar"
- **THEN** interface faz POST /orders/{id}/cancel com body `{ "reason": "..." }`
- **AND** resposta 200 retorna pedido com status "cancelled"
- **AND** interface fecha modal de cancelamento e modal de detalhe
- **AND** invalida cache de lista de pedidos
- **AND** mostra toast "Pedido cancelado com sucesso"
- **AND** tabela recarrega automaticamente

#### Scenario: Cancelamento ja estava feito
- **WHEN** owner clica "Cancelar" em pedido ja cancelado
- **THEN** interface captura 409 com code `ALREADY_CANCELLED`
- **AND** mostra toast "Pedido ja estava cancelado"

#### Scenario: Status nao permite cancelamento
- **WHEN** owner clica "Cancelar" em pedido com status `shipped` ou `delivered`
- **THEN** interface captura 409 com code `TRANSITION_INVALID`
- **AND** mostra toast "Nao eh possivel cancelar um pedido ja enviado"

#### Scenario: Timeout no servidor
- **WHEN** POST /orders/{id}/cancel demora > 30s
- **THEN** interface mostra toast "Timeout ao cancelar pedido, por favor tente novamente"
- **AND** modal de cancelamento fecha
- **AND** interface recarrega a lista para sincronizar

#### Scenario: Erro de servidor
- **WHEN** POST /orders/{id}/cancel retorna 500
- **THEN** interface mostra toast "Erro ao cancelar pedido, tente novamente"
- **AND** modal permanece aberto para retry

### Requirement: Protecao de acesso

O sistema SHALL impedir acesso de usuarios sem papel `owner` e sem escopo `sales:read`.

#### Scenario: Usuario customer nao vê aba de pedidos
- **WHEN** usuario sem papel `owner` acessa `/store`
- **THEN** abas nao incluem "Pedidos"
- **AND** tentativa de acessar `/store/orders` redireciona para home

#### Scenario: Token sem escopo
- **WHEN** GET /orders/manage retorna 403 (sem escopo `sales:read`)
- **THEN** interface mostra toast "Sem permissao para acessar pedidos"
- **AND** lista permanece vazia ou redireciona

### Requirement: Ordenacao de lista

O sistema SHALL permitir ordenacao por data (default desc), status, total.

#### Scenario: Ordenacao padrao
- **WHEN** `/store/orders` carrega
- **THEN** pedidos sao ordenados por data de criacao (mais recente primeiro)

#### Scenario: Ordenar por status
- **WHEN** owner clica header "Status" da tabela
- **THEN** interface faz GET /orders/manage?sort=status,asc
- **AND** lista reordena por status alfabeticamente
- **AND** proxima click ordena desc

#### Scenario: Ordenar por total
- **WHEN** owner clica header "Total"
- **THEN** interface faz GET /orders/manage?sort=totalCost,asc
- **AND** lista ordena por valor total (menor primeiro)
- **AND** icon de sort (seta) aparece no header

### Requirement: Paginacao

O sistema SHALL suportar paginacao com size=20 e navegacao entre paginas.

#### Scenario: Proxima pagina
- **WHEN** owner esta na pagina 0 de 3 totais e clica "Proxima"
- **THEN** interface faz GET /orders/manage?page=1&size=20
- **AND** mostra pedidos de 20-39

#### Scenario: Pagina anterior
- **WHEN** owner esta na pagina 1 e clica "Anterior"
- **THEN** interface faz GET /orders/manage?page=0&size=20
- **AND** mostra pedidos de 0-19

#### Scenario: Salto para pagina especifica
- **WHEN** owner digita numero de pagina em input e pressiona Enter
- **THEN** interface faz GET /orders/manage?page=2&size=20

#### Scenario: Desabilitar navegacao em extremos
- **WHEN** owner esta na pagina 0
- **THEN** botao "Anterior" eh desabilitado
- **AND** em ultima pagina, botao "Proxima" eh desabilitado

#### Scenario: Paginacao se reseta ao filtrar
- **WHEN** owner aplica novo filtro
- **THEN** lista volta para page=0
