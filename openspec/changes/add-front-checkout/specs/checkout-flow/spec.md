# Spec Delta: Checkout Flow

## Propósito

Fluxo de checkout em etapas (seleção/cadastro de endereço, cálculo de frete, revisão e confirmação) com geração de `Idempotency-Key`, revalidação de preço e criação idempotente de pedido.

## ADDED Requirements

### Requirement: Fluxo de checkout em três etapas

O sistema SHALL exibir checkout como processo linear: Endereço → Frete → Confirmação, com indicador visual de etapa ativa.

#### Cenário: Navegação entre etapas

- **QUANDO** usuário inicia checkout em `/checkout`
- **ENTÃO** Etapa 1 (Endereço) é exibida com indicador visual mostrando "1/3"
- **E** botão "Próximo" navega para Etapa 2
- **E** botão "Anterior" volta para Etapa 1 (exceto na primeira etapa)
- **E** todas as etapas são preenchidas sequencialmente; não há jump direto

#### Cenário: Etapa 1 não avança se carrinho vazio

- **QUANDO** usuário tenta prosseguir do checkout com carrinho vazio
- **ENTÃO** POST /orders nunca é chamado (validação local)
- **E** toast exibe "Seu carrinho está vazio"
- **E** botão "Voltar ao Carrinho" redireciona para `/cart`

### Requirement: Seleção ou cadastro de endereço

O sistema SHALL listar endereços do usuário ou prover formulário de cadastro novo.

#### Cenário: Carregar lista de endereços existentes

- **QUANDO** usuário acessa Etapa 1
- **ENTÃO** GET /users/me/addresses é chamado via TanStack Query
- **E** lista exibe cada endereço com: nome, CEP, estado, cidade, rua, número
- **E** um endereço pode ser marcado como padrão (ícone de estrela)
- **E** radio buttons permitem selecionar um para checkout

#### Cenário: Selecionar endereço e calcular frete

- **QUANDO** usuário seleciona um endereço e clica "Próximo"
- **ENTÃO** GET /shipping/quote?zipcode={cep} é chamado imediatamente
- **E** resposta traz `distanceKm`, `ratePerKm`, `freightCost`
- **E** tela muda para Etapa 2 com frete exibido
- **E** se GET falhar com `422 ZIPCODE_NOT_GEOCODED`, aviso exibe e botão "Tentar Outro Endereço" volta à lista

#### Cenário: Cadastrar novo endereço

- **QUANDO** usuário clica botão "Novo Endereço"
- **ENTÃO** formulário aparece com campos: nome (opcional), CEP, estado, cidade, rua, número (opcional)
- **E** ao preencher CEP com 8 dígitos, busca BrasilAPI automaticamente (debounce 500ms)
- **E** cidade e estado são preenchidos automaticamente
- **E** se CEP inválido, aviso em vermelho: "CEP não encontrado"
- **E** botão "Salvar e Usar Este Endereço" faz POST /users/me/addresses e segue para cálculo de frete

#### Cenário: Validação de formulário de endereço

- **QUANDO** usuário tenta salvar endereço incompleto
- **ENTÃO** validação local bloqueia: CEP obrigatório, estado/cidade/rua obrigatórios
- **E** campos inválidos ganham borda vermelha e mensagem de erro
- **E** botão Salvar fica desabilitado até campos corretos

#### Cenário: Erro ao cadastrar novo endereço

- **QUANDO** POST /users/me/addresses retorna `422` (CEP/UF inválidos) ou `429` (rate limit)
- **ENTÃO** aviso específico é exibido
- **E** usuário pode voltar e tentar novamente
- **E** se `500`/`503`, toast "Erro ao salvar endereço; tente novamente"

### Requirement: Exibição de frete e total

O sistema SHALL exibir Etapa 2 com itemsCost, freightCost, totalCost e opções de voltar ou confirmar.

#### Cenário: Etapa 2 exibe custos calculados

- **QUANDO** usuário avança para Etapa 2
- **ENTÃO** resumo mostra:
  - Subtotal de itens: R$ 699.80
  - Frete ({{ distanceKm }} km × R$ 1,00): R$ 25.00
  - Total: R$ 724.80
- **E** botão "Usar Este Endereço" leva para Etapa 3
- **E** botão "Voltar e Mudar Endereço" volta para Etapa 1

#### Cenário: Frete zero para CEP da loja

- **QUANDO** usuário seleciona endereço com mesmo CEP da loja
- **ENTÃO** GET /shipping/quote retorna `freightCost: "0.00"`
- **E** resumo exibe "Frete: R$ 0.00 (Retirada na loja)"

### Requirement: Revisão final e confirmação idempotente

O sistema SHALL exibir Etapa 3 com resumo completo, Idempotency-Key gerada/reutilizada, e botão de confirmação.

#### Cenário: Etapa 3 exibe resumo completo

- **QUANDO** usuário avança para Etapa 3
- **ENTÃO** resumo mostra:
  - Endereço de entrega selecionado
  - Lista de itens (nome, quantidade, preço da linha)
  - Subtotal, frete, total
- **E** botões "Voltar" e "Confirmar Pedido"

#### Cenário: Geração de Idempotency-Key

- **QUANDO** usuário entra em Etapa 3 pela primeira vez
- **ENTÃO** UUID v4 é gerado para `Idempotency-Key`
- **E** armazenado em localStorage sob chave `checkout-idempotency-key-{sessionId}`
- **E** se usuário voltar para Etapa 1 e retornar à Etapa 3 nesta sessão, mesma chave é reutilizada

#### Cenário: Confirmação do pedido com POST idempotente

- **QUANDO** usuário clica "Confirmar Pedido"
- **ENTÃO** POST /orders é chamado com:
  ```json
  {
    "addressId": 15,
    "expectedTotalCost": "724.80"
  }
  ```
- **E** header `Idempotency-Key: {uuid}` é incluído
- **E** spinner "Processando..." aparece sobre botão
- **E** resposta `201` traz `{id, status: "pending", ...}`

#### Cenário: Revalidação de preço no checkout

- **QUANDO** POST /orders é enviado
- **ENTÃO** se resposta é `409 PRICE_CHANGED`, aviso exibe preço antigo vs. novo
- **E** botão "Recalcular" volta para Etapa 2
- **E** frete é recalculado e totais são atualizados

#### Cenário: Carrinho vazio antes de confirmar

- **QUANDO** POST /orders retorna `422 EMPTY_CART` (item removido entre etapas)
- **ENTÃO** toast exibe "Seu carrinho está vazio"
- **E** botão "Voltar ao Carrinho" redireciona para `/cart`

#### Cenário: Estoque insuficiente no checkout

- **QUANDO** POST /orders retorna `409 INSUFFICIENT_STOCK`
- **ENTÃO** aviso exibe: "Produto {{ name }} tem apenas {{ available }} disponíveis"
- **E** botão "Voltar ao Carrinho" permite ajustar quantidade

#### Cenário: Endereço não pertence ao usuário

- **QUANDO** POST /orders retorna `404` (addressId não é do usuário)
- **ENTÃO** raramente ocorre (validação frontend); se ocorre, toast "Endereço inválido"
- **E** botão "Voltar" vai para Etapa 1

#### Cenário: Serviço indisponível no checkout

- **QUANDO** POST /orders retorna `503` (inventory, shipment ou user fora)
- **ENTÃO** aviso persistente: "Catálogo temporariamente indisponível. Tente novamente."
- **E** pedido NÃO é criado
- **E** botão "Tentar Novamente" refaz POST com mesma chave

#### Cenário: Timeout no checkout

- **QUANDO** POST /orders não responde após 15 segundos
- **ENTÃO** erro `504` é tratado
- **E** aviso: "Operação demorou. Tente novamente com a mesma chave."
- **E** retry automático com exponential backoff (1s, 2s, 4s); máximo 3 tentativas
- **E** se todas falham, aviso oferece "Entrar em contato com suporte"

#### Cenário: Retry com Idempotency-Key idêntica

- **QUANDO** usuário refaz POST /orders com mesma chave e corpo idêntico após erro
- **ENTÃO** header `Idempotency-Replayed: true` vem na resposta
- **E** mesmo status (201) é retornado, mesmo ID do pedido é reutilizado
- **E** efeito colateral (limpeza do carrinho) não é repetido

#### Cenário: Retry com corpo diferente (IDEMPOTENCY_KEY_REUSED)

- **QUANDO** usuário muda expectedTotalCost e tenta POST com mesma chave
- **ENTÃO** retorna `422 IDEMPOTENCY_KEY_REUSED`
- **E** aviso: "Chave de requisição reutilizada com dados diferentes. Recarregue a página."
- **E** botão "Recarregar" limpa localStorage e volta ao início do checkout

#### Cenário: Requisição em voo (IDEMPOTENCY_IN_FLIGHT)

- **QUANDO** usuario clica duas vezes em "Confirmar Pedido" rapidamente
- **ENTÃO** primeira requisição é enviada
- **E** segunda clicada retorna `409 IDEMPOTENCY_IN_FLIGHT` com `Retry-After: 1`
- **E** toast exibe "Processando... aguarde 1 segundo"
- **E** botão fica desabilitado por 1 segundo
- **E** após timeout, mesmo ID do pedido é retornado

### Requirement: Tratamento de erros de RFC 9457

O sistema SHALL exibir erro com `code` estável, não confiar apenas em `title`.

#### Cenário: Erro com details em errors[]

- **QUANDO** resposta HTTP tem `errors: [{field: "items[0].quantity", message: "máximo disponível: 2"}]`
- **ENTÃO** aviso exibe campo específico e mensagem
- **E** user pode ir de volta e corrigir

#### Cenário: RequestId no erro

- **QUANDO** erro ocorre, header `X-Request-Id` vem na resposta
- **ENTÃO** aviso de suporte inclui: "RequestId: {{ requestId }}"
- **E** user pode reportar esse ID para diagnosticar

## RENAMED Requirements

Nenhuma mudança.
