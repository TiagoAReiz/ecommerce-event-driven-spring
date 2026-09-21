# Spec Delta: Shopping Cart UI

## Propósito

Interface visual do carrinho com listagem de itens, avisos de indisponibilidade ou mudança de preço, operações de edição (adicionar, remover, alterar quantidade, esvaziar).

## ADDED Requirements

### Requirement: Tela do carrinho com listagem de itens

O sistema SHALL exibir tela dedicada (`/cart`) com listagem de itens do carrinho, cada um mostrando foto, nome, preço unitário, quantidade, preço da linha e botão de remover.

#### Cenário: Carrinho com itens carrega

- **QUANDO** usuário acessa `/cart` autenticado
- **ENTÃO** GET /cart é chamado automaticamente via TanStack Query
- **E** resposta traz `items[]` com `product` (name, price, photoUrl, available, active), `quantity`, `lineTotal` e `issues[]`
- **E** cada item é exibido em card com layout responsivo (mobile = coluna única, desktop = colunas lado a lado)

#### Cenário: Carrinho vazio

- **QUANDO** GET /cart retorna `items: []`
- **ENTÃO** mensagem "Seu carrinho está vazio" é exibida
- **E** botão "Continuar Comprando" redireciona para `/products`

#### Cenário: Carrinho com timeout de hidratação

- **QUANDO** GET /cart retorna `504` ou timeout (> 5 segundos)
- **ENTÃO** itens aparecem sem `product` com aviso "Carregando informações do produto..."
- **E** botão de checkout é desabilitado até carrinho carregar completamente
- **E** botão "Tentar Novamente" recarrega GET /cart

### Requirement: Avisos visuais por item

O sistema SHALL exibir avisos específicos para `issues[]`: PRODUCT_UNAVAILABLE, INSUFFICIENT_STOCK, PRICE_CHANGED, HYDRATION_TIMEOUT.

#### Cenário: Produto indisponível

- **QUANDO** item tem `issues: ["PRODUCT_UNAVAILABLE"]` (ativo = false)
- **ENTÃO** linha é exibida com fundo cinzento
- **E** ícone de bloqueio aparece ao lado do nome
- **E** aviso em vermelho: "Este produto não está mais disponível"
- **E** quantidade não pode ser alterada; botão remover é o único disponível

#### Cenário: Estoque insuficiente

- **QUANDO** item tem `issues: ["INSUFFICIENT_STOCK"]` (quantity > available)
- **ENTÃO** aviso em laranja: "Disponível apenas {{ available }} unidades"
- **E** quantidade é reduzida automaticamente no UI para `available`
- **E** mensagem oferece opção: "Reduzir para {{ available }}?" com botões Sim/Não
- **E** se Não, usuário é levado de volta ao carrinho com item inalterado

#### Cenário: Preço alterado

- **QUANDO** item tem `issues: ["PRICE_CHANGED"]` (preço diferente de quando foi adicionado)
- **ENTÃO** aviso em azul: "Preço mudou de R$ {{ oldPrice }} para R$ {{ newPrice }}"
- **E** linha total é recalculada com novo preço
- **E** permitido manter item no carrinho com novo preço

#### Cenário: Timeout de carregamento de produto

- **QUANDO** item tem `issues: ["HYDRATION_TIMEOUT"]`
- **ENTÃO** aviso: "Informações do produto ainda carregando..."
- **E** spinner aparece
- **E** campo de quantidade está desabilitado
- **E** botão remover está habilitado

### Requirement: Operações de edição de quantidade

O sistema SHALL permitir alterar quantidade (incremento/decremento ou input direto) com validação contra disponível e máximo por item (99).

#### Cenário: Incrementar quantidade com botões +/-

- **QUANDO** usuário clica botão "+" ao lado de quantidade
- **ENTÃO** PUT /cart/items/{idProduct} é chamado com `quantity: oldQuantity + 1`
- **E** se sucesso (200), quantidade é atualizada no UI
- **E** se erro `409 INSUFFICIENT_STOCK`, aviso exibe disponível e quantidade volta ao anterior

#### Cenário: Alterar quantidade via input numérico

- **QUANDO** usuário clica no campo de quantidade e digita novo valor
- **ENTÃO** validação local recusa valores ≤ 0 ou > 99
- **E** ao sair do campo (blur), PUT /cart/items/{idProduct} é chamado
- **E** spinner aparece sobre o campo durante requisição

#### Cenário: Quantidade acima do máximo permitido

- **QUANDO** usuário tenta definir quantidade > 99
- **ENTÃO** campo recusa entrada (regex) e mostra aviso "Máximo 99 por item"
- **E** requisição não é enviada

#### Cenário: Quantidade acima do disponível

- **QUANDO** PUT /cart/items/{idProduct} retorna `409 INSUFFICIENT_STOCK`
- **ENTÃO** aviso exibe disponível (ex.: "Apenas 5 disponíveis")
- **E** quantidade é revertida para valor anterior
- **E** usuário pode reduzir manualmente se desejar

### Requirement: Remover item do carrinho

O sistema SHALL permitir remover item com confirmação visual (toast de undo rápido).

#### Cenário: Remover item com undo

- **QUANDO** usuário clica botão "Remover" ou ícone de lixeira em um item
- **ENTÃO** DELETE /cart/items/{idProduct} é chamado imediatamente
- **E** item desaparece do UI com animação slide-out
- **E** toast exibe "Item removido" com botão "Desfazer" (3 segundos)
- **E** se clicado Desfazer em tempo, POST /cart/items é chamado para adicionar novamente

#### Cenário: Remover item e confirmar

- **QUANDO** usuário clica "Desfazer" e item é re-adicionado
- **ENTÃO** item reaparece na lista com animação slide-in
- **E** quantidade e avisos são restaurados

#### Cenário: Toast desaparece após 3 segundos

- **QUANDO** item é removido e nenhuma ação é tomada
- **ENTÃO** toast some automaticamente após 3 segundos
- **E** remoção é permanente

### Requirement: Esvaziar carrinho

O sistema SHALL prover botão "Esvaziar Carrinho" com diálogo de confirmação.

#### Cenário: Esvaziar com confirmação

- **QUANDO** usuário clica botão "Esvaziar Carrinho"
- **ENTÃO** diálogo aparece: "Tem certeza? Todos os itens serão removidos."
- **E** botões "Cancelar" e "Esvaziar"
- **E** se confirmado, DELETE /cart é chamado

#### Cenário: Após esvaziar, UI volta ao estado vazio

- **QUANDO** DELETE /cart retorna `204`
- **ENTÃO** diálogo fecha
- **E** lista de itens desaparece
- **E** mensagem "Seu carrinho está vazio" é exibida
- **E** botão "Continuar Comprando" oferecido

### Requirement: Resumo de totais

O sistema SHALL exibir resumo com `itemsCost`, possível `freightCost` (se no checkout) e `totalCost`.

#### Cenário: Cálculo dinâmico de totais

- **QUANDO** carrinho tem itens
- **ENTÃO** resumo à direita (desktop) ou abaixo (mobile) exibe:
  - Subtotal: R$ 699.80
  - Frete: (calculado no checkout, não aqui)
  - Total: R$ 699.80
- **E** valores são atualizados em tempo real quando quantidade muda

#### Cenário: Botão "Ir para Checkout" no resumo

- **QUANDO** carrinho tem pelo menos um item sem `PRODUCT_UNAVAILABLE`
- **ENTÃO** botão "Ir para Checkout" é exibido e habilitado
- **E** clique redireciona para `/checkout` e inicia fluxo de checkout
- **E** se todos os itens têm `PRODUCT_UNAVAILABLE`, botão fica desabilitado com tooltip "Remova itens indisponíveis"

## RENAMED Requirements

Nenhuma mudança.
