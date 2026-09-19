# Spec Delta

## Purpose

Carrinho do cliente com hidratação dinâmica de produtos, identificação de problemas e operações de modificação.

## ADDED Requirements

### Requirement: Visualizar carrinho hidratado
O sistema SHALL retornar o carrinho do usuário autenticado com todos os itens, cada um hidratado com informações do produto do `inventory`, sinalização de problemas de disponibilidade, e totalizadores de custo.

#### Scenario: Carrinho com itens válidos
- **WHEN** usuário com escopo `cart:read` executa `GET /cart`
- **THEN** sistema retorna status `200` com array `items[]` onde cada item contém `id`, `idProduct`, `quantity`, `product` (incluindo `name`, `price`, `photoUrl`, `available`, `active`), `lineTotal`, `issues[]` (vazio se sem problemas), `itemsCost` somado de todos os itens, e `updatedAt`

#### Scenario: Carrinho vazio retorna sucesso
- **WHEN** usuário executa `GET /cart` sem itens no carrinho
- **THEN** sistema retorna status `200` com `items: []`, `itemsCost: "0.00"`, `issues: []`

#### Scenario: Timeout na hidratação do inventory
- **WHEN** `inventory` leva mais de 3 segundos para responder `GET /internal/products`
- **THEN** sistema retorna status `504` sem criar pedido; cliente recebe erro de timeout

#### Scenario: Hidrataçao com problemas detectados
- **WHEN** algum produto tem `active: false` ou `available < quantity` no carrinho
- **THEN** sistema retorna status `200` onde cada item com problema contém `issues[]` com `PRODUCT_UNAVAILABLE` (se removido) ou `INSUFFICIENT_STOCK` (se quantidade indisponível)

#### Scenario: Sem autenticação
- **WHEN** `GET /cart` é executado sem token ou com token inválido
- **THEN** sistema retorna status `401`

#### Scenario: Escopo insuficiente
- **WHEN** token não contém escopo `cart:read`
- **THEN** sistema retorna status `403`

---

### Requirement: Adicionar ou somar item ao carrinho
O sistema SHALL criar o carrinho no primeiro item e, para itens subsequentes, somar a quantidade ao item existente (garantindo uma linha por produto). Valida a quantidade máxima por item e o limite total de linhas distintas.

#### Scenario: Adicionar primeiro item
- **WHEN** usuário com escopo `cart:write` executa `POST /cart/items` com `{idProduct: 118, quantity: 2}`
- **THEN** sistema cria o carrinho, insere o item, retorna status `201` e devolve o carrinho completo com hidratação

#### Scenario: Somar à quantidade existente
- **WHEN** item do produto 118 já existe no carrinho com quantity 2, e usuário faz `POST /cart/items` com `{idProduct: 118, quantity: 3}`
- **THEN** sistema soma as quantidades para 5, retorna status `201` e devolve carrinho atualizado

#### Scenario: Quantidade por item acima do limite
- **WHEN** usuário tenta `POST /cart/items` com `quantity: 100` (limite é 99)
- **THEN** sistema retorna status `422` com erro `QUANTITY_EXCEEDS_LIMIT`

#### Scenario: Carrinho com 50 linhas distintas
- **WHEN** carrinho já tem 50 itens distintos e usuário tenta `POST /cart/items`
- **THEN** sistema retorna status `413` com erro `CART_LINE_LIMIT_EXCEEDED`

#### Scenario: Produto não existe ou foi removido
- **WHEN** `POST /cart/items` referencia `idProduct` que não existe no `inventory`
- **THEN** sistema retorna status `404`

#### Scenario: Quantidade resultante acima do disponível
- **WHEN** produto tem 10 unidades disponíveis e carrinho já tem 8 unidades; usuário tenta adicionar 3
- **THEN** sistema retorna status `409` com erro `INSUFFICIENT_STOCK`

#### Scenario: Quantidade inválida
- **WHEN** `POST /cart/items` envia `quantity: 0` ou `quantity: -1`
- **THEN** sistema retorna status `400`

#### Scenario: Campo obrigatório faltando
- **WHEN** `POST /cart/items` não inclui `idProduct` ou inclui com valor `null`
- **THEN** sistema retorna status `400`

---

### Requirement: Definir quantidade de item
O sistema SHALL permitir definir (não somar) a quantidade de um item existente, validando limites e disponibilidade. Operação é idempotente.

#### Scenario: Definir quantidade válida
- **WHEN** usuário com escopo `cart:write` executa `PUT /cart/items/118` com `{quantity: 5}`
- **THEN** sistema define quantidade para 5 (não soma), retorna status `200` e devolve carrinho

#### Scenario: Quantidade acima de 99
- **WHEN** `PUT /cart/items/118` envia `quantity: 150`
- **THEN** sistema retorna status `422` com erro `QUANTITY_EXCEEDS_LIMIT`

#### Scenario: Item não está no carrinho
- **WHEN** `PUT /cart/items/999` para produto que não está no carrinho
- **THEN** sistema retorna status `404`

#### Scenario: Quantidade acima do disponível
- **WHEN** produto tem 10 unidades disponíveis e usuário tenta `PUT /cart/items/118` com `quantity: 20`
- **THEN** sistema retorna status `409` com erro `INSUFFICIENT_STOCK`

#### Scenario: Quantidade inválida (zero ou negativa)
- **WHEN** `PUT /cart/items/118` envia `quantity: 0`
- **THEN** sistema retorna status `400`

---

### Requirement: Remover item do carrinho
O sistema SHALL remover um item do carrinho do usuário, retornando o carrinho atualizado ou `404` se item não existe.

#### Scenario: Remover item existente
- **WHEN** usuário com escopo `cart:write` executa `DELETE /cart/items/118` para item que existe
- **THEN** sistema remove o item, retorna status `200` e devolve carrinho sem aquele item

#### Scenario: Item não está no carrinho
- **WHEN** `DELETE /cart/items/999` para produto não presente
- **THEN** sistema retorna status `404`

#### Scenario: Sem escopo de escrita
- **WHEN** token não contém escopo `cart:write`
- **THEN** sistema retorna status `403`

---

### Requirement: Esvaziar carrinho
O sistema SHALL remover todos os itens do carrinho do usuário. Operação é idempotente: esvaziar um carrinho já vazio retorna sucesso.

#### Scenario: Esvaziar carrinho com itens
- **WHEN** usuário com escopo `cart:write` executa `DELETE /cart` em carrinho com itens
- **THEN** sistema remove todos os itens, retorna status `204`

#### Scenario: Esvaziar carrinho vazio
- **WHEN** `DELETE /cart` em carrinho sem itens
- **THEN** sistema retorna status `204` (idempotente)

#### Scenario: Sem token autenticado
- **WHEN** `DELETE /cart` sem autenticação
- **THEN** sistema retorna status `401`
