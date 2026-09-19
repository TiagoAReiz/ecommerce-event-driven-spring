# Spec Delta

## Purpose

Avaliações de produto permitem que clientes que receberam seus pedidos comentem sobre a compra e atribuam uma nota, construindo o rating agregado de cada produto e a elegibilidade é controlada por evento de entrega.

## ADDED Requirements

### Requirement: Listar avaliações com distribuição de notas
O sistema SHALL permitir listar avaliações de um produto com paginação e filtro opcional por nota, e retornar um sumário com a distribuição de notas entre 1 e 5 estrelas.

#### Scenario: Listar com sucesso sem filtro
- **WHEN** cliente acessa `GET /products/{id}/reviews` com paginação padrão
- **THEN** retorna status `200` com array de avaliações, informações de paginação e sumário contendo rating agregado (média com 2 casas decimais), contagem de avaliações e distribuição de frequência por nota

#### Scenario: Filtrar por nota específica
- **WHEN** cliente acessa `GET /products/{id}/reviews?rate=5` com nota entre 1 e 5
- **THEN** retorna apenas avaliações com aquela nota

#### Scenario: Produto inexistente
- **WHEN** cliente acessa `GET /products/{id}/reviews` com id de produto que não existe
- **THEN** retorna status `404`

### Requirement: Detalhar uma avaliação individual
O sistema SHALL permitir visualizar os dados completos de uma avaliação pelo seu id, incluindo snapshots do autor e indicação se foi editada.

#### Scenario: Visualizar avaliação existente
- **WHEN** cliente acessa `GET /reviews/{id}`
- **THEN** retorna status `200` com os dados da avaliação, incluindo nota, título, descrição, snapshot do autor (id, nome, foto), datas de criação e edição

#### Scenario: Avaliação removida ou inexistente
- **WHEN** cliente acessa `GET /reviews/{id}` para id que não existe ou foi removido
- **THEN** retorna status `404`

### Requirement: Listar avaliações do usuário atual
O sistema SHALL permitir que um cliente autenticado veja todas as suas avaliações com paginação.

#### Scenario: Usuário com avaliações
- **WHEN** cliente autenticado acessa `GET /reviews/mine` com paginação
- **THEN** retorna status `200` com suas avaliações ordenadas por data de criação decrescente

#### Scenario: Usuário sem avaliações
- **WHEN** cliente autenticado acessa `GET /reviews/mine`
- **THEN** retorna status `200` com `content` vazio

### Requirement: Listar produtos elegíveis para avaliação pendente
O sistema SHALL permitir que um cliente veja produtos que ele pode avaliar, ou seja, que já foram entregues e ainda não foram avaliados.

#### Scenario: Listar pendentes com sucesso
- **WHEN** cliente autenticado acessa `GET /reviews/pending` com paginação
- **THEN** retorna status `200` com lista de produtos elegíveis, incluindo id e nome do produto, foto, id do pedido e data de concessão da elegibilidade

#### Scenario: Sem pendentes
- **WHEN** cliente autenticado acessa `GET /reviews/pending` e não tem nenhuma elegibilidade sem avaliação
- **THEN** retorna status `200` com `content` vazio

### Requirement: Criar avaliação de produto
O sistema SHALL permitir que um cliente elegível crie uma avaliação para um produto que recebeu, armazenando snapshots do nome e foto do usuário na época da avaliação, e recalcular o rating e contagem do produto atomicamente.

#### Scenario: Criar avaliação com sucesso
- **WHEN** cliente elegível faz `POST /products/{id}/reviews` com nota entre 1 e 5, título e descrição
- **THEN** retorna status `201` com a avaliação criada, snapshot do autor (nome e foto do usuário no momento), e o produto tem seu `rating` e `rating_count` recalculados

#### Scenario: Usuário sem elegibilidade
- **WHEN** cliente não elegível (não comprou ou pedido não foi entregue) tenta criar avaliação
- **THEN** retorna status `403` indicando falta de elegibilidade

#### Scenario: Avaliação duplicada no mesmo pedido
- **WHEN** cliente tenta criar segunda avaliação para o mesmo produto no mesmo pedido
- **THEN** retorna status `409` Conflict com indicação de duplicata

#### Scenario: Produto inexistente
- **WHEN** cliente tenta avaliar produto que não existe
- **THEN** retorna status `404`

### Requirement: Editar avaliação dentro de 30 dias
O sistema SHALL permitir que o autor de uma avaliação atualize sua nota, título e descrição apenas nos primeiros 30 dias após criação, e recalcular o rating do produto na mesma transação.

#### Scenario: Editar dentro da janela permitida
- **WHEN** autor faz `PATCH /reviews/{id}` com um ou mais campos (rate, title, description) dentro de 30 dias
- **THEN** retorna status `200` com a avaliação atualizada, campo `editedAt` preenchido com a data da última edição, e rating do produto recalculado

#### Scenario: Janela de edição expirada
- **WHEN** autor tenta editar avaliação com mais de 30 dias após criação
- **THEN** retorna status `409` indicando que a janela de edição expirou

#### Scenario: Usuário não autorizado
- **WHEN** usuário diferente do autor tenta editar
- **THEN** retorna status `404` (a avaliação não existe para esse usuário)

### Requirement: Remover avaliação com soft delete
O sistema SHALL permitir que o autor remova sua avaliação permanentemente (soft delete), e recalcule o rating do produto.

#### Scenario: Remover com sucesso
- **WHEN** autor faz `DELETE /reviews/{id}`
- **THEN** retorna status `204`, a avaliação não aparece mais nas listagens, e o rating do produto é recalculado

#### Scenario: Usuário não autorizado
- **WHEN** usuário diferente do autor tenta remover
- **THEN** retorna status `404`

### Requirement: Recalcular rating agregado do produto
O sistema SHALL recalcular automaticamente o rating médio e contagem de avaliações do produto na mesma transação de criação, edição ou remoção de avaliação, com precisão de 2 casas decimais, e invalidar o cache do produto.

#### Scenario: Rating atualizado na criação
- **WHEN** primeira avaliação é criada com nota 5
- **THEN** product.rating vira "5.00" e product.rating_count vira 1

#### Scenario: Rating atualizado na edição
- **WHEN** avaliação é editada de nota 5 para nota 3
- **THEN** product.rating é recalculado como média das notas atuais

#### Scenario: Rating atualizado na remoção
- **WHEN** única avaliação (nota 5) é removida
- **THEN** product.rating vira "0.00" e product.rating_count vira 0

### Requirement: Concessão de elegibilidade por evento de entrega
O sistema SHALL conceder direito de avaliar um cliente por cada item do pedido quando o pedido é confirmado como entregue, armazenando a tupla (usuário, produto, pedido) em `review_eligibility`.

#### Scenario: Elegibilidade concedida na entrega
- **WHEN** evento `order.delivered` chega com 3 itens
- **THEN** inserem-se 3 linhas em `review_eligibility` (uma por produto), com `id_user`, `id_product`, `id_order` e `granted_at`

#### Scenario: Reentrega não duplica elegibilidade
- **WHEN** evento `order.delivered` é reenviado (idempotência Kafka)
- **THEN** usa-se `INSERT … ON CONFLICT DO NOTHING` na chave composta e nenhuma linha duplica

### Requirement: Anonimização de avaliações em caso de exclusão de conta
O sistema SHALL anonimizar as avaliações de um usuário quando sua conta é removida, preservando a nota e texto da avaliação mas removendo identificação pessoal conforme LGPD.

#### Scenario: Usuário com avaliações é removido
- **WHEN** evento `user.deleted` chega para um usuário que tem avaliações
- **THEN** atualizam-se todos os `review` do usuário: `review.user_name = 'Usuário removido'` e `review.user_photo_url = NULL`

#### Scenario: Usuário sem avaliações é removido
- **WHEN** evento `user.deleted` chega para um usuário sem avaliações
- **THEN** a operação é idempotente (ignorado) e nada muda

### Requirement: Segurança e autorização por escopo
O sistema SHALL exigir escopos apropriados para cada rota de avaliação.

#### Scenario: Leitura sem autenticação
- **WHEN** cliente anônimo acessa `GET /products/{id}/reviews` ou `GET /reviews/{id}`
- **THEN** retorna status `200` (o gateway emite token de serviço com escopo `catalog:read` e `reviews:read`)

#### Scenario: Escrita sem escopo
- **WHEN** usuário autenticado sem escopo `reviews:write` tenta criar avaliação
- **THEN** retorna status `403` Forbidden

#### Scenario: Leitura de pendentes sem escopo
- **WHEN** usuário sem escopo `reviews:read` acessa `GET /reviews/pending` ou `GET /reviews/mine`
- **THEN** retorna status `403` Forbidden
