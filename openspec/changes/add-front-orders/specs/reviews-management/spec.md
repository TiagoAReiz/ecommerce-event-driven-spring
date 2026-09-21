# Spec Delta: Reviews Management

## Purpose

Gerencia todo o ciclo de vida de avaliações de produtos pelo comprador: visualizar quais produtos
pode avaliar (elegibilidade por compra e entrega), criar nova avaliação com stars, title e
description, editar dentro de 30 dias, deletar, e visualizar histórico de suas avaliações.

## ADDED Requirements

### Requirement: Listar avaliações pendentes (produtos a avaliar)

O sistema SHALL exibir lista de produtos que o usuário pode avaliar: aqueles em que foi entregue
(`review_eligibility`) mas ainda não avaliou (`review` não existe).

#### Scenario: Carregamento bem-sucedido de pendentes
- **WHEN** usuário autentico navega para `/reviews/pending`
- **THEN** tela exibe tile grid de produtos com até 20 itens (paginável), ordenados por
  `grantedAt` descendente
- **AND** cada tile mostra:
  - Foto do produto
  - Nome do produto
  - Data de compra/entrega (`grantedAt` formatada como "Comprado em 01 de setembro de 2026")
  - Botão "Avaliar"

#### Scenario: Paginação de pendentes
- **WHEN** usuário clica "Próxima" ou página 2
- **THEN** requisição envia `page=1&size=20`
- **AND** novos tiles são carregados

#### Scenario: Clique em tile abre modal de avaliação
- **WHEN** usuário clica "Avaliar" em um tile
- **THEN** modal de criação de review abre (ver requirement: Criar Avaliação)

#### Scenario: Lista vazia de pendentes
- **WHEN** usuário não tem produtos a avaliar (já avaliou todos ou nenhuma compra entregue)
- **THEN** exibe mensagem: "Você não tem avaliações pendentes. Compre e receba produtos para
  poder avaliá-los!"
- **AND** botão de link para catálogo

#### Scenario: Erro ao carregar pendentes
- **WHEN** servidor retorna `500` ou `503`
- **THEN** exibe mensagem de erro e botão retry
- **AND** skeleton tiles enquanto carrega

#### Scenario: Token expirado
- **WHEN** requisição retorna `401`
- **THEN** usuário é redirecionado para login

#### Scenario: Sem escopo reviews:read
- **WHEN** requisição retorna `403`
- **THEN** mensagem: "Sem permissão para acessar suas avaliações"

### Requirement: Criar avaliação com validação de elegibilidade

O sistema SHALL abrir modal de criação de avaliação com campos de rate (1–5 stars), title
(até 150 caracteres) e description, validar elegibilidade (`review_eligibility` deve existir),
e tratar erros `409` (já avaliou, fora de janela) e `403` (sem elegibilidade).

#### Scenario: Modal de criação abre corretamente
- **WHEN** usuário clica "Avaliar" em um tile de pendentes ou acessa criar via rota
- **THEN** modal exibe:
  - Foto e nome do produto no topo
  - "Qual sua nota?"
  - 5 stars (clicáveis, hover mostra label 1–5)
  - Campo de input: "Título da sua avaliação (até 150 caracteres)"
  - Textarea: "Descreva sua experiência com o produto"
  - Badge contador para título: "0/150"
  - Botões: "Cancelar" e "Postar Avaliação"
  - Botão "Postar" inicia desabilitado até selecionar rate ≥ 1

#### Scenario: Seleção de stars
- **WHEN** usuário clica na estrela 4
- **THEN** 4 stars aparecem preenchidas (azul `#1d4ed8`), 1 vazia
- **AND** label mostra "4 de 5 - Bom"
- **AND** botão "Postar" fica habilitado

#### Scenario: Validação de título
- **WHEN** usuário digita "Muito bom!" (9 caracteres)
- **THEN** contador atualiza: "9/150"
- **AND** quando atinge 150, campo para aceitar input

#### Scenario: Envio de avaliação bem-sucedida
- **WHEN** usuário preenche: rate=5, title="Excelente", description="Digitação ótima"
- **AND** clica "Postar Avaliação"
- **THEN** requisição `POST /products/{id}/reviews` com payload:
  ```json
  { "idOrder": 3301, "rate": 5, "title": "Excelente", "description": "Digitação ótima" }
  ```
- **AND** servidor retorna `201`
- **AND** modal fecha
- **AND** tile de pendente desaparece (removido da lista)
- **AND** toast verde: "Avaliação postada com sucesso"

#### Scenario: Erro 409 — já avaliou esse produto nesse pedido
- **WHEN** requisição retorna `409` com código `ALREADY_REVIEWED`
- **THEN** modal mostra mensagem: "Você já avaliou este produto neste pedido"
- **AND** botão é "Entendi" em vez de "Postar"

#### Scenario: Erro 403 — sem elegibilidade
- **WHEN** requisição retorna `403` (não comprou ou pedido não foi entregue)
- **THEN** modal mostra: "Você não pode avaliar este produto (precisa ter comprado e recebido)"
- **AND** botão "Entendi"

#### Scenario: Erro 422 — texto reprovado na moderação
- **WHEN** requisição retorna `422`
- **THEN** modal mostra: "Sua avaliação contém conteúdo inadequado. Revise e tente novamente."
- **AND** campos mantêm conteúdo

#### Scenario: Erro 400 — rate ausente ou inválido
- **WHEN** requisição é enviada sem `rate` (edge case)
- **THEN** servidor retorna `400`
- **AND** modal mostra: "Nota é obrigatória"

#### Scenario: Erro 500 — falha ao gravar
- **WHEN** servidor retorna `500`
- **THEN** modal mostra: "Erro ao postar avaliação. Tente novamente."
- **AND** botão de retry

#### Scenario: Token expirado durante criação
- **WHEN** requisição retorna `401`
- **THEN** modal fecha
- **AND** usuário é redirecionado para login

### Requirement: Editar avaliação com janela de 30 dias

O sistema SHALL permitir editar avaliação apenas se foi criada há menos de 30 dias. Após janela
expirar, responde `409` e exibe mensagem de erro.

#### Scenario: Botão de edição visível dentro de 30 dias
- **WHEN** avaliação foi criada há 15 dias
- **THEN** botão "Editar" é exibido na card de avaliação ("Minhas Avaliações")

#### Scenario: Botão de edição invisível após 30 dias
- **WHEN** avaliação foi criada há 31 dias
- **THEN** botão "Editar" não é exibido (ou desabilitado com tooltip "Não é possível editar
  avaliações após 30 dias")

#### Scenario: Modal de edição abre com dados pré-populados
- **WHEN** usuário clica "Editar" em uma avaliação dentro da janela
- **THEN** modal abre com:
  - Rate pré-selecionado
  - Título pré-preenchido
  - Descrição pré-preenchida
  - Botões: "Cancelar" e "Salvar Alterações"

#### Scenario: Edição bem-sucedida
- **WHEN** usuário altera dados (ex: rate de 4 para 5)
- **AND** clica "Salvar Alterações"
- **THEN** requisição `PATCH /reviews/{id}` com novo payload
- **AND** servidor retorna `200`
- **AND** modal fecha
- **AND** card de avaliação atualiza com novos dados
- **AND** `editedAt` é preenchido
- **AND** toast: "Avaliação atualizada com sucesso"

#### Scenario: Erro 409 — janela de edição expirada
- **WHEN** avaliação foi criada há 31 dias
- **AND** requisição retorna `409` com código `EDIT_WINDOW_EXPIRED`
- **THEN** modal mostra: "Não é possível editar avaliações após 30 dias. Você pode deletar e
  criar uma nova."
- **AND** botão é "Entendi"

#### Scenario: Erro 404 — avaliação removida
- **WHEN** avaliação foi deletada entre abertura do modal e envio
- **AND** requisição retorna `404`
- **THEN** modal mostra: "Avaliação não encontrada"

#### Scenario: Erro 422 — texto reprovado na moderação (edição)
- **WHEN** novo texto contém conteúdo inadequado
- **AND** requisição retorna `422`
- **THEN** modal mostra: "Seu texto foi rejeitado na moderação. Revise e tente novamente."

#### Scenario: Erro 400 — rate fora do range
- **WHEN** rate é enviado como 0 ou 6 (edge case)
- **THEN** servidor retorna `400`
- **AND** modal mostra: "Nota deve estar entre 1 e 5"

#### Scenario: Cancelar edição
- **WHEN** usuário clica "Cancelar"
- **THEN** modal fecha
- **AND** alterações não são gravadas

### Requirement: Deletar avaliação

O sistema SHALL permitir que o autor delete sua avaliação, com confirmação via modal antes de
executar a deleção.

#### Scenario: Botão de deletar visível
- **WHEN** avaliação está na tela "Minhas Avaliações"
- **THEN** botão "Deletar" é exibido (lixeira ou texto)

#### Scenario: Modal de confirmação de deleção
- **WHEN** usuário clica "Deletar"
- **THEN** modal overlay aparece com:
  - Título: "Deletar Avaliação"
  - Mensagem: "Tem certeza que deseja remover esta avaliação? Esta ação é irreversível."
  - Botões: "Cancelar" e "Deletar"

#### Scenario: Deleção bem-sucedida
- **WHEN** usuário confirma com "Deletar"
- **THEN** requisição `DELETE /reviews/{id}` é enviada
- **AND** servidor retorna `204`
- **AND** modal fecha
- **AND** avaliação é removida da lista
- **AND** toast verde: "Avaliação deletada com sucesso"

#### Scenario: Erro 404 — avaliação removida
- **WHEN** avaliação foi já deletada por outro dispositivo
- **AND** requisição retorna `404`
- **THEN** modal mostra: "Avaliação não encontrada"
- **AND** avaliação é removida da lista local

#### Scenario: Erro 403 — não é o autor
- **WHEN** requisição retorna `403` (edge case, não é o dono da avaliação)
- **THEN** modal mostra: "Você não pode deletar esta avaliação"

#### Scenario: Erro 500 — falha ao deletar
- **WHEN** servidor retorna `500`
- **THEN** modal mostra: "Erro ao deletar avaliação. Tente novamente."
- **AND** botão de retry

#### Scenario: Token expirado durante deleção
- **WHEN** requisição retorna `401`
- **THEN** modal fecha
- **AND** usuário é redirecionado para login

#### Scenario: Cancelar deleção
- **WHEN** usuário clica "Cancelar" no modal
- **THEN** modal fecha
- **AND** avaliação não é deletada

### Requirement: Listar minhas avaliações

O sistema SHALL exibir lista paginada de todas as avaliações criadas pelo usuário, com opções
de editar (dentro de 30 dias) e deletar.

#### Scenario: Carregamento bem-sucedido
- **WHEN** usuário acessa `/reviews/mine`
- **THEN** tela exibe lista paginada de avaliações (até 20), ordenadas por `createdAt` descendente
- **AND** cada card mostra:
  - Foto do produto
  - Nome do produto
  - Stars (visualização, não editável aqui)
  - Título da avaliação
  - Descrição (truncada se muito longa, com "Ler mais")
  - Data de criação ("Postada em 15 de agosto de 2026")
  - Data de edição se existir (`editedAt`)
  - Botões: "Editar" (se dentro de 30 dias) e "Deletar"

#### Scenario: Paginação
- **WHEN** usuário clica "Próxima"
- **THEN** requisição envia `page=1&size=20`
- **AND** novos cards carregam

#### Scenario: Lista vazia
- **WHEN** usuário não tem nenhuma avaliação
- **THEN** exibe mensagem: "Você ainda não avaliou nenhum produto. Compre e receba para
  poder avaliar!"
- **AND** botão de link para catálogo

#### Scenario: Clique em "Ler mais" expande descrição
- **WHEN** descrição é truncada (ex: > 200 caracteres visíveis)
- **AND** usuário clica "Ler mais"
- **THEN** card expande e mostra descrição completa

#### Scenario: Erro ao carregar
- **WHEN** servidor retorna `500`
- **THEN** mensagem de erro e botão retry
- **AND** skeleton cards enquanto carrega

#### Scenario: Token expirado
- **WHEN** requisição retorna `401`
- **THEN** usuário é redirecionado para login

### Requirement: Integração com autenticação e autorização

O sistema SHALL validar que requisições a `/reviews/**` carregam token Bearer válido com escopo
`reviews:read` (leitura) ou `reviews:write` (criação/edição/deleção).

#### Scenario: Token com escopo reviews:read
- **WHEN** requisição `GET /reviews/pending` ou `GET /reviews/mine` envia token com
  `scope: "reviews:read"`
- **THEN** requisição é aceita

#### Scenario: Token com escopo reviews:write
- **WHEN** requisição `POST /products/{id}/reviews`, `PATCH /reviews/{id}` ou
  `DELETE /reviews/{id}` envia token com `scope: "reviews:write"`
- **THEN** requisição é aceita

#### Scenario: Token sem escopo suficiente
- **WHEN** token tem `scope: "products:read"` em vez de `reviews:read` ou `reviews:write`
- **THEN** servidor retorna `403`
- **AND** mensagem: "Sem permissão para acessar avaliações"

#### Scenario: Header Authorization ausente
- **WHEN** requisição não inclui header
- **THEN** servidor retorna `401`
- **AND** front redireciona para login

### Requirement: Elegibilidade validada no servidor

O sistema SHALL validar no servidor (nunca aceitar do cliente) que a elegibilidade existe em
`review_eligibility` antes de permitir criar avaliação. Cliente não envia `idProduct` em criar;
é extraído pela rota.

#### Scenario: Elegibilidade verificada
- **WHEN** requisição `POST /products/{id}/reviews` inclui `idOrder`
- **THEN** servidor valida em `review_eligibility` que existe registro para
  `(id_user=sub, id_product={id}, id_order={idOrder})`
- **AND** se não existe, retorna `403`
- **AND** se existe, avaliação é criada

#### Scenario: Elegibilidade removida após compra
- **WHEN** comprador comprou, recebeu, mas loja deletou a linha de `review_eligibility`
  (edge case)
- **AND** usuário tenta postar avaliação
- **THEN** servidor retorna `403`

#### Scenario: Elegibilidade vinculada a pedido específico
- **WHEN** mesmo usuário comprou o mesmo produto em 2 pedidos diferentes
- **AND** cria avaliação com `idOrder=3301`
- **AND** depois tenta criar outra com `idOrder=3302`
- **THEN** ambas são permitidas (um `review` por `(id_user, id_product, id_order)`)
