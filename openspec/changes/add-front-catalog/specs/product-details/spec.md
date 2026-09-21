# Spec Delta

## Propósito

Página de detalhe do produto. Cliente visualiza fotos em galeria, disponibilidade em tempo real, resumo e lista de avaliações com filtro, e pode adicionar ao carrinho.

## ADDED Requirements

### Requirement: Página de detalhe do produto

O sistema SHALL exibir em `/products/:id` todas as informações do produto com seções bem definidas.

#### Cenário: Acesso à página de detalhe

- **QUANDO** usuário acessa `/products/118`
- **ENTÃO** GET /products/118, GET /products/118/photos, GET /products/118/reviews são chamados em paralelo
- **E** loader do React Router pré-carrega os dados antes de renderizar a página
- **E** a página exibe: nome, preço, categoria, descrição, galeria, disponibilidade, reviews, frete e botão "Adicionar ao carrinho"

#### Cenário: Produto não encontrado

- **QUANDO** GET /products/999 retorna `404`
- **ENTÃO** a página exibe "Produto não encontrado"
- **E** um link oferece voltar ao catálogo
- **E** React Router error boundary captura e exibe fallback customizado

#### Cenário: Erro ao carregar detalhe

- **QUANDO** GET /products/118 retorna `500` ou timeout
- **ENTÃO** spinner aparece enquanto a revalidação ocorre (máx. 2 tentativas)
- **E** se continuar falhando, exibe erro com botão "Tentar novamente"

### Requirement: Galeria de fotos com zoom

O sistema SHALL exibir fotos do produto em galeria com foto principal e thumbnails.

#### Cenário: Galeria com múltiplas fotos

- **QUANDO** GET /products/118/photos retorna array com 4 fotos
- **ENTÃO** foto 0 aparece grande à esquerda (desktop) ou no topo (mobile)
- **E** thumbnails aparecem em linha abaixo (máx. 4 visíveis)
- **E** seta de scroll aparece se houver mais de 4 fotos

#### Cenário: Zoom em foto desktop

- **QUANDO** usuário faz hover em foto principal (desktop)
- **ENTÃO** cursor muda para "zoom" (🔍)
- **E** movimento do mouse sobre a foto faz pan (zoom + seguir cursor)
- **E** scroll wheel aumenta/diminui zoom (50% a 200%)

#### Cenário: Galeria fullscreen mobile

- **QUANDO** usuário clica em thumbnail no mobile
- **ENTÃO** modal fullscreen abre com a foto selecionada
- **E** setas de navegação (‹ ›) permitem trocar de foto
- **E** swipe esquerda/direita também troca foto
- **E** toque/ESC fecha o modal

#### Cenário: Navegação entre fotos

- **QUANDO** usuário clica em thumbnail 2
- **ENTÃO** foto 2 aparece como principal
- **E** foto anterior fica sem destaque, a nova tem border azul

#### Cenário: Foto indisponível

- **QUANDO** uma URL de foto retorna 404 ou erro de rede
- **ENTÃO** um ícone de "sem foto" é exibido no lugar da imagem
- **E** a galeria continua funcionando (não trava)

#### Cenário: Lazy loading de fotos

- **QUANDO** página carrega com 4 fotos visíveis
- **ENTÃO** apenas as 4 primeiras são carregadas inicialmente (com `loading="lazy"`)
- **E** thumbnails fora de viewport carregam quando scroll chega perto
- **E** fullscreen modal pré-carrega a próxima foto enquanto exibe a atual

### Requirement: Disponibilidade em tempo real

O sistema SHALL exibir disponibilidade **nunca cacheada** (sempre consultada no momento da renderização).

#### Cenário: Produto em estoque

- **QUANDO** GET /products/118/availability retorna `{available: 12, stock: 15, held: 3, asOf: "...Z"}`
- **ENTÃO** badge "✓ Em estoque" aparece em verde
- **E** ao lado mostra "12 unidades disponíveis"
- **E** botão "Adicionar ao carrinho" fica habilitado

#### Cenário: Produto fora de estoque

- **QUANDO** GET /products/118/availability retorna `{available: 0, stock: 0, held: 0}`
- **ENTÃO** badge "✗ Fora de estoque" aparece em vermelho
- **E** botão "Adicionar ao carrinho" fica desabilitado (cinzento com tooltip "Fora de estoque")

#### Cenário: Estoque baixo

- **QUANDO** GET /products/118/availability retorna `{available: 2, stock: 5, held: 3}`
- **ENTÃO** badge "⚠ Últimas 2 unidades" aparece em amarelo
- **E** aviso acima do botão: "Aproveite! Apenas 2 restantes."

#### Cenário: Revalidação periódica

- **QUANDO** usuário está vendo o detalhe há 30 segundos
- **ENTÃO** GET /products/118/availability é refeito automaticamente (sem cache)
- **E** disponibilidade atualiza silenciosamente (sem spinner)
- **E** se mudou para fora de estoque, botão é desabilitado

#### Cenário: Erro ao carregar disponibilidade

- **QUANDO** GET /products/118/availability retorna `503`
- **ENTÃO** badge mostra "⚠ Disponibilidade indisponível"
- **E** botão permanece habilitado (assume que há estoque)
- **E** aviso ao lado: "Não conseguimos verificar estoque. Tente completar a compra para confirmar."

### Requirement: Resumo e lista de avaliações

O sistema SHALL exibir avaliações do produto com resumo (média + distribuição) e lista paginada de comentários.

#### Cenário: Resumo de avaliações

- **QUANDO** GET /products/118/reviews retorna com `summary: {rating: "4.60", ratingCount: 52, distribution: {5: 38, 4: 9, 3: 3, 2: 1, 1: 1}}`
- **ENTÃO** seção "Avaliações" exibe:
  - Estrela grande (⭐ 4.6 de 5)
  - Contagem: "52 avaliações"
  - Gráfico de barras horizontal: linha para cada nota (5★ até 1★) com % e contagem (38, 9, 3, 1, 1)

#### Cenário: Lista de comentários

- **QUANDO** GET /products/118/reviews retorna `content: [{id, rate: 5, title, description, author, createdAt, editedAt}]`
- **ENTÃO** cada comentário é exibido em card com:
  - Estrelas (5 ⭐ de ouro)
  - Título do comentário (negrito)
  - Foto + nome do autor
  - Data de publicação ("há 5 dias")
  - Texto da avaliação
  - Badge "Editado em ..." se `editedAt != null`

#### Cenário: Paginação de comentários

- **QUANDO** há 52 avaliações, exibindo 20 por página
- **ENTÃO** paginação aparece abaixo ("1 de 3")
- **E** usuário pode navegar para próximas páginas

#### Cenário: Filtro por nota de avaliação

- **QUANDO** usuário clica em "5 ⭐" no resumo
- **ENTÃO** lista de comentários é filtrada para mostrar apenas 5 estrelas
- **E** URL muda para `/products/118?rate=5`
- **E** GET /products/118/reviews?rate=5&... é chamado
- **E** contagem de comentários mostra "38 avaliações de 5 estrelas"

#### Cenário: Sem avaliações

- **QUANDO** GET /products/118/reviews retorna `{content: [], page: {..., totalElements: 0}}`
- **ENTÃO** exibe mensagem "Este produto ainda não tem avaliações. Seja o primeiro a avaliar!"

#### Cenário: Carregar mais comentários

- **QUANDO** usuário rola até o final da página de comentários
- **ENTÃO** próxima página é carregada automaticamente (infinite scroll, ou paginação numérica)
- **E** spinner aparece durante carregamento
- **E** novos comentários são adicionados à lista

### Requirement: Botão "Adicionar ao carrinho"

O sistema SHALL permitir adicionar produto ao carrinho (ou redirecionar ao login se deslogado).

#### Cenário: Adicionar ao carrinho autenticado

- **QUANDO** usuário clicado em "Adicionar ao carrinho" sendo autenticado
- **ENTÃO** input de quantidade (default 1, máx. 99) aparece inline ou em modal
- **E** ao confirmar, ação é disparada para o módulo de carrinho (useCart() hook, Redux, context, TBD)
- **E** sucesso: toast exibe "✓ Produto adicionado ao carrinho!"
- **E** link rápido "Ver carrinho" aparece no toast

#### Cenário: Adicionar desautenticado

- **QUANDO** usuário clica em "Adicionar ao carrinho" sem autenticação
- **ENTÃO** redirect para `/login?redirect=/products/118` (volta aqui após login)
- **E** após login bem-sucedido, produto é adicionado automaticamente

#### Cenário: Produto indisponível

- **QUANDO** `available: 0`
- **ENTÃO** botão "Adicionar ao carrinho" é desabilitado (cinzento)
- **E** tooltip exibe "Este produto está fora de estoque"

#### Cenário: Quantidade máxima

- **QUANDO** usuário tenta adicionar 100 unidades
- **ENTÃO** input valida e limita a 99 (máximo conforme contrato)
- **E** mensagem de erro: "Quantidade máxima: 99"

#### Cenário: Falha ao adicionar

- **QUANDO** POST /cart/items retorna `400` ou `413` (carrinho cheio)
- **ENTÃO** toast de erro exibe: "Não conseguimos adicionar. Tente novamente."
- **E** botão permanece habilitado para retry

## Requisito: Calculadora de frete integrada

O sistema SHALL permitir consultar frete antes de comprar (ver `shipping-quote` spec separado).

#### Cenário: Input de CEP na página de detalhe

- **QUANDO** usuário vê seção "Frete" com input "CEP de entrega"
- **ENTÃO** input aceita 8 dígitos (máscara `#####-###`)
- **E** botão "Calcular frete" fica desabilitado até digitar 8 dígitos

#### Cenário: Consultar frete

- **QUANDO** usuário digita CEP válido e clica "Calcular"
- **ENTÃO** GET /shipping/quote?zipcode=01310100 é chamado
- **E** resposta mostra: "Frete para São Paulo, SP: R$ 25,00"
- **E** resultado é cacheado por 1 hora (mesmo CEP não refaz requisição)

#### Cenário: CEP inválido

- **QUANDO** GET /shipping/quote retorna `422 ZIPCODE_NOT_GEOCODED`
- **ENTÃO** mensagem de erro: "CEP não encontrado na base de dados"
- **E** input mantém valor para correção

