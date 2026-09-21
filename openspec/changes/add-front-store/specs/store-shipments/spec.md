# Spec Delta: Store Shipments

## Purpose

Fornece interface de gestao de envios para o dono da loja: listagem da fila de despacho, transicoes de status, insercao de codigo de rastreio e cancelamento de envios.

## ADDED Requirements

### Requirement: Listar envios da loja

O sistema SHALL permitir ao owner listar todos os envios da loja com foco na fila de despacho (pending e ready_to_ship).

#### Scenario: Listar envios com filtro padrao
- **WHEN** owner com escopo `sales:read` acessa `/store/shipments`
- **THEN** interface exibe tabela de envios com colunas: ID, Pedido, Status, Destino, Rastreio
- **AND** status usa cores: pending (vermelho), ready_to_ship (amarelo), in_transit (azul), out_for_delivery (azul claro), delivered (verde), cancelled (cinza), returned (cinza)
- **AND** paginacao padrao (page 0, size 20)
- **AND** HTTP 200 com resposta de `GET /shipments/manage`

#### Scenario: Fila de despacho em destaque
- **WHEN** owner carrega a pagina de envios
- **THEN** interface ordena por padrao status `pending` e `ready_to_ship` primeiro (createdAt,asc)
- **AND** avisos/badges indicam envios prontos para despacho
- **AND** opcional: counter "3 envios pendentes de despacho"

#### Scenario: Filtro por status
- **WHEN** owner faz GET /shipments/manage?status=pending
- **THEN** interface mostra apenas envios em status "pending"

#### Scenario: Filtro por multiplos status
- **WHEN** owner faz GET /shipments/manage?status=in_transit&status=out_for_delivery
- **THEN** interface mostra envios em "in_transit" OU "out_for_delivery"

#### Scenario: Filtro por pedido
- **WHEN** owner faz GET /shipments/manage?orderId=3301
- **THEN** interface mostra apenas envios do pedido 3301 (deve ser 1)

#### Scenario: Combinacao de filtros
- **WHEN** owner faz GET /shipments/manage?status=pending&status=ready_to_ship&orderId=3301
- **THEN** interface aplica todos os filtros

#### Scenario: Filtros persistem em URL
- **WHEN** owner aplica filtros e faz hard refresh
- **THEN** filtros sao mantidos

#### Scenario: Limpar filtros
- **WHEN** owner clica "Limpar Filtros"
- **THEN** interface reseta para defaults (mostra todos os status)

#### Scenario: Sem permissao
- **WHEN** usuario sem papel `owner` tenta acessar `/store/shipments`
- **THEN** interface redireciona para home com toast "Acesso negado"

#### Scenario: Lista vazia
- **WHEN** nao ha envios na loja
- **THEN** interface mostra tabela vazia com mensagem "Nenhum envio encontrado"

### Requirement: Transicoes de status de envio

O sistema SHALL permitir ao owner avancar o status do envio conforme as transicoes permitidas pelo backend.

#### Scenario: Transicao pending -> ready_to_ship
- **WHEN** owner em `/store/shipments` vê envio com status "pending"
- **THEN** interface mostra botao "Marcar como Pronto para Despacho"
- **AND** owner clica, interface faz PATCH /shipments/{id} com `{ "status": "ready_to_ship" }`
- **AND** resposta 200 atualiza status
- **AND** status muda para "ready_to_ship" (amarelo) otimista na UI
- **AND** mostra toast "Envio marcado como pronto para despacho"

#### Scenario: Transicao ready_to_ship -> in_transit com rastreio
- **WHEN** envio tem status "ready_to_ship"
- **THEN** interface mostra botao "Despachar" + campo de texto "Codigo de Rastreio"
- **AND** campo eh obrigatorio (validacao: minimo 3 caracteres, maximo 60)
- **AND** owner preenche rastreio (ex: "AA123456789BR") e clica "Despachar"
- **THEN** interface faz PATCH /shipments/{id} com `{ "status": "in_transit", "trackingCode": "AA123456789BR" }`
- **AND** resposta 200, status muda para "in_transit" (azul)
- **AND** mostra toast "Envio despachado com rastreio adicionado"
- **AND** rastreio aparece na tabela

#### Scenario: Codigo de rastreio obrigatorio no despacho
- **WHEN** owner tenta despachar sem preencher rastreio
- **THEN** interface mostra erro "Codigo de rastreio eh obrigatorio"
- **AND** botao "Despachar" fica desabilitado

#### Scenario: Codigo de rastreio muito curto
- **WHEN** owner digita rastreio com 2 caracteres
- **THEN** interface mostra erro "Minimo 3 caracteres"

#### Scenario: Transicao in_transit -> out_for_delivery (opcional)
- **WHEN** envio tem status "in_transit"
- **THEN** interface mostra botao opcional "Marcar como Saido para Entrega"
- **AND** owner clica, interface faz PATCH /shipments/{id} com `{ "status": "out_for_delivery" }`
- **AND** resposta 200, status muda para "out_for_delivery" (azul claro)
- **AND** mostra toast "Envio marcado como saido para entrega"

#### Scenario: Transicao para returned
- **WHEN** envio tem status "in_transit" ou "out_for_delivery"
- **THEN** interface mostra botao "Marcar como Devolvido"
- **AND** owner clica, abre modal com campo de motivo (textarea)
- **AND** owner preenche motivo e clica "Confirmar"
- **THEN** interface faz PATCH /shipments/{id} com `{ "status": "returned" }`
- **AND** resposta 200, status muda para "returned" (cinza)
- **AND** mostra toast "Envio marcado como devolvido"

#### Scenario: Botao "Entregar" sempre desabilitado
- **WHEN** envio tem qualquer status
- **THEN** interface nunca oferece botao "Entregar" ou "Confirmar Entrega"
- **AND** UI mostra tooltip: "Apenas o comprador pode confirmar a entrega"
- **AND** Isso previne que a loja feche pedidos de clientes que nao receberam

#### Scenario: Transicao invalida
- **WHEN** owner tenta fazer transicao nao permitida (ex: in_transit -> pending)
- **THEN** interface captura 409 com code `TRANSITION_INVALID`
- **AND** mostra toast "Transicao nao permitida para esse envio"
- **AND** status permanece inalterado

#### Scenario: Envio ja foi entregue
- **WHEN** envio tem status "delivered"
- **THEN** interface nao mostra nenhum botao de acao
- **AND** status exibe "Entregue" com verde

#### Scenario: Otimismo em atualizacao
- **WHEN** owner clica botao de transicao
- **THEN** status atualiza localmente imediatamente (otimista)
- **AND** botao fica desabilitado enquanto request voa
- **AND** se resposta for erro 409, UI desfaz mudanca localmente

### Requirement: Codigo de rastreio

O sistema SHALL permitir adicionar, visualizar e editar codigo de rastreio durante transicoes.

#### Scenario: Rastreio obrigatorio no despacho
- **WHEN** owner esta prestes a marcar "in_transit"
- **THEN** interface exige campo "Codigo de Rastreio" pre-focado
- **AND** placeholder: "Ex: AA123456789BR"

#### Scenario: Rastreio persiste na tabela
- **WHEN** envio tem rastreio adicionado
- **THEN** coluna "Rastreio" mostra o codigo
- **AND** cliente tambem vê esse codigo em seu perfil

#### Scenario: Rastreio pode ser editado
- **WHEN** owner com envio em "in_transit" clica em botao "Editar Rastreio"
- **THEN** interface abre campo editavel
- **AND** owner altera e clica "Salvar"
- **THEN** interface faz PATCH /shipments/{id} com novo trackingCode
- **AND** mostra toast "Rastreio atualizado"

### Requirement: Cancelar envio

O sistema SHALL permitir ao owner cancelar envios nos status `pending` ou `ready_to_ship`.

#### Scenario: Botao cancelar em status permitidos
- **WHEN** envio tem status "pending" ou "ready_to_ship"
- **THEN** interface mostra botao "Cancelar Envio"
- **AND** em status "in_transit", "out_for_delivery", "delivered", "returned", botao nao aparece

#### Scenario: Modal de cancelamento
- **WHEN** owner clica "Cancelar Envio"
- **THEN** interface abre modal com:
  - Mensagem: "Tem certeza que deseja cancelar esse envio?"
  - Textarea para "Motivo do cancelamento" (≤ 500 caracteres)
  - Validacao: minimo 5 caracteres, contador ao vivo
  - Botoes: "Voltar" e "Confirmar Cancelamento"

#### Scenario: Motivo obrigatorio
- **WHEN** owner deixa motivo vazio
- **THEN** interface mostra erro "Motivo eh obrigatorio"
- **AND** botao "Confirmar" desabilitado

#### Scenario: Cancelar com sucesso
- **WHEN** owner digita motivo valido e clica "Confirmar"
- **THEN** interface faz POST /shipments/{id}/cancel com `{ "reason": "..." }`
- **AND** resposta 200 atualiza status para "cancelled"
- **AND** interface fecha modal
- **AND** mostra toast "Envio cancelado"
- **AND** invalida cache de lista de envios

#### Scenario: Cancelamento ja foi feito
- **WHEN** owner tenta cancelar envio ja cancelado
- **THEN** interface captura 422 com code `ALREADY_CANCELLED`
- **AND** mostra toast "Envio ja estava cancelado"

#### Scenario: Nao eh possivel cancelar em transit
- **WHEN** owner clica "Cancelar" em envio "in_transit"
- **THEN** interface captura 409 com code `TRANSITION_INVALID`
- **AND** mostra toast "Nao eh possivel cancelar um envio em transito"
- **AND** botao "Cancelar" fica oculto em status avancados

### Requirement: Protecao de acesso

O sistema SHALL impedir acesso de usuarios sem papel `owner` e escopo `sales:read`.

#### Scenario: Usuario customer nao vê aba de envios
- **WHEN** usuario sem papel `owner` acessa `/store`
- **THEN** abas nao incluem "Envios"

#### Scenario: Acesso negado a rota
- **WHEN** GET /shipments/manage retorna 403
- **THEN** interface mostra toast "Sem permissao para acessar envios"

### Requirement: Ordenacao

O sistema SHALL ordenar envios por padrao por createdAt asc (mais antigos primeiro) para priorizar despacho.

#### Scenario: Ordenacao padrao por idade
- **WHEN** `/store/shipments` carrega
- **THEN** envios sao ordenados por data de criacao (mais antigos primeiro)
- **AND** isso coloca a fila de despacho (pending) em destaque

#### Scenario: Reordenar por status
- **WHEN** owner clica header "Status"
- **THEN** interface faz GET /shipments/manage?sort=status,asc
- **AND** lista ordena alfabeticamente por status

### Requirement: Paginacao

O sistema SHALL suportar paginacao com size=20.

#### Scenario: Proxima pagina
- **WHEN** owner na pagina 0 de envios clica "Proxima"
- **THEN** interface faz GET /shipments/manage?page=1&size=20

#### Scenario: Reset ao filtrar
- **WHEN** owner aplica novo filtro
- **THEN** lista volta para page=0

### Requirement: Feedback visual

O sistema SHALL exibir estados de loading, erro e sucesso.

#### Scenario: Loading em transicao
- **WHEN** owner clica botao de transicao e PATCH esta em voo
- **THEN** botao mostra spinner e fica desabilitado
- **AND** tabela pode manter opacidade reduzida

#### Scenario: Erro com retry
- **WHEN** PATCH retorna erro
- **THEN** interface mostra toast com mensagem
- **AND** botao volta a ficar habilitado para retry

#### Scenario: Toast de sucesso
- **WHEN** operacao sucede
- **THEN** toast mostra mensagem contextualizada (ex: "Envio despachado com rastreio adicionado")
- **AND** desaparece automaticamente apos 4s
