# Spec Delta: Store Products

## Purpose

Fornece interface completa de gerenciamento de produtos para o dono da loja: listagem com filtros, criacao, edicao, remocao, controle de estoque e gestao de fotos.

## ADDED Requirements

### Requirement: Listar produtos da loja

O sistema SHALL permitir ao owner listar todos os seus produtos com status bruto (ativo, zerado, removido), filtro de status e busca por nome.

#### Scenario: Listar produtos ativos
- **WHEN** owner com papel `owner` e escopo `catalog:write` faz `GET /products/manage?status=active`
- **THEN** interface exibe tabela de produtos ativos, com colunas ID, Nome, Categoria, Preco, Estoque, Status
- **AND** cada linha mostra botoes "Editar" e "Remover"
- **AND** paginacao padrao (page 0, size 20)
- **AND** HTTP 200

#### Scenario: Filtro de status "zerado"
- **WHEN** owner faz `GET /products/manage?status=out_of_stock`
- **THEN** interface mostra apenas produtos com stock=0 (sem reservas held ou com todas vencidas)

#### Scenario: Filtro de status "removido"
- **WHEN** owner faz `GET /products/manage?status=deleted`
- **THEN** interface mostra apenas produtos com `deleted_at` preenchido

#### Scenario: Filtro de status "todos"
- **WHEN** owner faz `GET /products/manage?status=all`
- **THEN** interface mostra todos os produtos independente de status

#### Scenario: Busca por nome
- **WHEN** owner faz `GET /products/manage?q=teclado`
- **THEN** interface filtra para produtos que contem "teclado" no nome ou descricao

#### Scenario: Combinacao de filtros
- **WHEN** owner faz `GET /products/manage?status=active&q=mouse&page=1`
- **THEN** interface mostra produtos ativos que contem "mouse", pagina 1

#### Scenario: Sem permissao
- **WHEN** usuario sem papel `owner` tenta acessar `/products/manage`
- **THEN** interface mostra toast "Acesso negado" e redireciona para home
- **AND** HTTP 403

#### Scenario: Token expirado
- **WHEN** owner com token expirado faz request
- **THEN** interface captura 401 e redireciona para login

#### Scenario: Lista vazia
- **WHEN** owner nao tem nenhum produto
- **THEN** interface mostra tabela vazia com mensagem "Nenhum produto encontrado"
- **AND** HTTP 200

### Requirement: Criar novo produto

O sistema SHALL permitir ao owner criar um novo produto com nome, descricao, categoria, preco e estoque inicial.

#### Scenario: Criar produto com dados validos
- **WHEN** owner clica botao "Novo Produto" em `/store/products`
- **THEN** interface abre drawer lateral com form em branco
- **AND** campos: nome (obrigatorio, ≤ 200), descricao (opcional), categoria (dropdown), preco (obrigatorio, ≥ 0, 2 casas), estoque (opcional, default 0, ≥ 0)
- **AND** owner preenche dados e clica "Salvar"
- **THEN** interface faz POST /products com payload
- **AND** resposta 201 carrega novo produto com Location
- **AND** interface invalida cache de lista e recarrega
- **AND** mostra toast "Produto criado com sucesso"
- **AND** drawer fecha

#### Scenario: Campo obrigatorio ausente
- **WHEN** owner deixa campo "Nome" em branco e clica "Salvar"
- **THEN** interface mostra erro "Nome é obrigatório" abaixo do campo
- **AND** botao "Salvar" fica desabilitado ate preencher

#### Scenario: Preco invalido
- **WHEN** owner digita preco com mais de 2 casas decimais (ex: "349.899")
- **THEN** interface mostra erro "Maximo 2 casas decimais"

#### Scenario: Preco negativo
- **WHEN** owner digita preco negativo
- **THEN** interface mostra erro "Preco deve ser maior ou igual a 0"

#### Scenario: Categoria inexistente
- **WHEN** owner tenta criar produto com categoria removida (inválida)
- **THEN** interface captura 404 do servidor e mostra toast "Categoria nao encontrada"

#### Scenario: Falha de servidor
- **WHEN** POST /products retorna 500
- **THEN** interface mostra toast "Erro ao criar produto, tente novamente"
- **AND** drawer nao fecha
- **AND** botao "Salvar" volta a ficar habilitado

#### Scenario: Excesso de requisicoes
- **WHEN** owner faz POST /products mais de 100 vezes em 1 hora
- **THEN** interface captura 429 e mostra toast "Limite de criacao atingido, tente mais tarde"

### Requirement: Editar produto existente

O sistema SHALL permitir ao owner atualizar nome, descricao, categoria e preco de um produto.

#### Scenario: Editar produto com sucesso
- **WHEN** owner clica botao "Editar" em uma linha de produto
- **THEN** interface abre drawer com form pre-preenchido com dados atuais do produto
- **AND** owner altera "Nome" e clica "Salvar"
- **THEN** interface faz PUT /products/{id} com payload completo
- **AND** resposta 200 retorna produto atualizado
- **AND** interface invalida cache e recarrega lista
- **AND** mostra toast "Produto atualizado com sucesso"
- **AND** drawer fecha

#### Scenario: Tentar mudar preco de produto com pedido em andamento
- **WHEN** produto tem reserva `held` ativa e owner tenta PUT alterando preco
- **THEN** interface captura 409 com code `TRANSITION_INVALID` ou similar
- **AND** mostra toast "Nao eh possivel mudar o preco de um produto com pedido em andamento"

#### Scenario: Produto removido por outra sessao
- **WHEN** owner estava editando produto e, enquanto escrevia, outro owner removeu o produto
- **THEN** interface captura 404 no PUT
- **AND** mostra toast "Produto nao encontrado ou removido"
- **AND** drawer fecha

### Requirement: Remover produto

O sistema SHALL permitir ao owner remover (soft delete) um produto, deixando-o invisivel na vitrine.

#### Scenario: Remover produto ativo
- **WHEN** owner clica botao "Remover" em uma linha e confirma em modal de confirmacao
- **THEN** interface faz DELETE /products/{id}
- **AND** resposta 204 (sem corpo)
- **AND** interface invalida cache e recarrega lista
- **AND** mostra toast "Produto removido com sucesso"
- **AND** produto desaparece da tab "Ativos" ou aparece em "Removidos"

#### Scenario: Nao remover produto com pedido ativo
- **WHEN** produto tem reserva `held` nao vencida e owner tenta DELETE
- **THEN** interface captura 409 com code `RESERVATION_HELD`
- **AND** mostra toast "Nao eh possivel remover produto com pedido em andamento"
- **AND** permanece na lista

#### Scenario: Remover produto ja removido
- **WHEN** owner tenta remover produto com status "removido" (deleted_at preenchido)
- **THEN** interface captura 404
- **AND** mostra toast "Produto ja estava removido"

### Requirement: Atualizar estoque

O sistema SHALL permitir ao owner ajustar o estoque de um produto por valor absoluto ou incremento.

#### Scenario: Definir estoque absoluto
- **WHEN** owner clica em um produto na lista (aba "Estoque" ou acao rapida)
- **THEN** interface exibe modal/drawer com campo "Estoque" pre-preenchido
- **AND** owner altera para novo valor (ex: 25) e clica "Atualizar"
- **THEN** interface faz PATCH /products/{id}/stock com body `{ "stock": 25 }`
- **AND** resposta 200 retorna `{ idProduct: ..., stock: 25, held: 3, available: 22 }`
- **AND** interface mostra toast "Estoque atualizado para 25 unidades"

#### Scenario: Incrementar estoque
- **WHEN** owner clica opçao "Incrementar" em place de "Estoque"
- **THEN** interface exibe campo de incremento (ex: +5)
- **AND** owner digita +5 e clica "Atualizar"
- **THEN** interface faz PATCH /products/{id}/stock com body `{ "delta": 5 }`
- **AND** resposta 200 mostra novo estoque
- **AND** mostra toast "Estoque incrementado em 5 unidades"

#### Scenario: Tentar deixar estoque negativo
- **WHEN** produto tem estoque 5 e owner tenta PATCH { "stock": -1 }
- **THEN** interface captura 422 com erro "Estoque nao pode ser negativo"

#### Scenario: Tentar deixar estoque abaixo do reservado
- **WHEN** produto tem 20 de estoque e 15 reservados (held), owner tenta PATCH { "stock": 10 }
- **THEN** interface captura 409 com code `INSUFFICIENT_STOCK` ou `RESERVATION_HELD`
- **AND** mostra toast "Estoque nao pode ser menor que o já reservado (15 unidades)"

#### Scenario: Conflito de edicao (If-Match)
- **WHEN** owner estava atualizando estoque, outro owner alterou entre-meio
- **THEN** interface captura 412 (Precondition Failed)
- **AND** mostra toast "Estoque foi alterado por outro usuario, recarregue e tente novamente"

### Requirement: Gerenciar fotos do produto

O sistema SHALL permitir ao owner adicionar, reordenar e remover fotos de um produto.

#### Scenario: Abrir drawer de edicao com fotos
- **WHEN** owner clica "Editar" em um produto
- **THEN** interface abre drawer e exibe secao "Fotos" com lista de URLs
- **AND** cada foto mostra botoes de reordenacao (setas up/down) e "Remover"

#### Scenario: Adicionar foto por URL
- **WHEN** owner clica "Adicionar Foto" em produto
- **THEN** interface exibe campo de input "URL da Foto" (obrigatorio https://)
- **AND** owner cola URL (ex: https://cdn.loja.dev/p/118/3.webp) e clica "Adicionar"
- **THEN** interface faz POST /products/{id}/photos com body `{ "photoUrl": "...", "position": 3 }`
- **AND** resposta 201 retorna nova foto com id
- **AND** interface mostra toast "Foto adicionada" e atualiza lista de fotos

#### Scenario: Validar URL
- **WHEN** owner tenta adicionar foto com URL nao-https (ex: http://...)
- **THEN** interface mostra erro "URL deve iniciar com https://"
- **AND** botao "Adicionar" fica desabilitado

#### Scenario: Limite de 10 fotos
- **WHEN** produto ja tem 10 fotos e owner tenta adicionar a 11a
- **THEN** interface captura 409 com code `PHOTO_LIMIT_EXCEEDED`
- **AND** mostra toast "Limite de 10 fotos por produto atingido"
- **AND** campo "Adicionar Foto" fica desabilitado

#### Scenario: Reordenar fotos
- **WHEN** owner clica seta "up" de uma foto ou arrasta para reordenar
- **THEN** interface atualiza posicoes localmente
- **AND** ao salvar drawer, faz PUT /products/{id}/photos/order com array de `{ id, position }`
- **AND** resposta 200 confirma reordenacao
- **AND** mostra toast "Fotos reordenadas"

#### Scenario: Remover foto
- **WHEN** owner clica botao "Remover" em uma foto
- **THEN** interface pede confirmacao
- **AND** owner confirma, interface faz DELETE /products/{id}/photos/{photoId}
- **AND** resposta 204 remove foto
- **AND** mostra toast "Foto removida"

#### Scenario: Nao remover unica foto de produto ativo
- **WHEN** produto ativo tem 1 foto e owner tenta DELETE
- **THEN** interface captura 409 com code similar
- **AND** mostra toast "Produto ativo precisa de pelo menos 1 foto"

#### Scenario: Foto com URL invalida
- **WHEN** owner adiciona URL que nao eh imagem valida (tamanho, dimensoes, MIME)
- **THEN** interface captura 422 do servidor
- **AND** mostra toast "Imagem invalida: verifique tamanho (max 10MB) e dimensoes (min 400x400)"

### Requirement: Protecao de acesso

O sistema SHALL impedir acesso de usuarios sem papel `owner` e sem escopo `catalog:write`.

#### Scenario: Usuario customer tenta acessar /store/products
- **WHEN** usuario autenticado mas sem papel `owner` acessa `/store/products`
- **THEN** interface redireciona para `/` com toast "Acesso negado, apenas a loja pode gerenciar produtos"

#### Scenario: Token sem escopo
- **WHEN** token do owner tem `aud=front` mas nao tem escopo `catalog:write` (improvavel em pratica)
- **THEN** requests para POST/PUT/PATCH/DELETE retornam 403
- **AND** interface mostra toast "Sem permissao para essa operacao"

#### Scenario: Token expirado
- **WHEN** owner com token expirado faz qualquer requisicao
- **THEN** interface captura 401 e redireciona para login
