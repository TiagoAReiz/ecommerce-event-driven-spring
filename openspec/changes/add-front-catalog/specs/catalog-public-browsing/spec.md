# Spec Delta

## Propósito

Interface pública de navegação e busca de catálogo. Cliente consegue visualizar categorias, buscar produtos por nome, filtrar por categoria/preço/avaliação, ordenar e paginar.

## ADDED Requirements

### Requirement: Página inicial com categorias e destaques

O sistema SHALL exibir na homepage (`/`) uma listagem de categorias com contagem de produtos e uma seção de produtos em destaque.

#### Cenário: Página inicial carrega categorias

- **QUANDO** o usuário acessa `/`
- **ENTÃO** GET /categories é chamado automaticamente
- **E** a resposta exibe cada categoria em card (nome, slug, productCount)
- **E** a Cache-Control pública respeita TTL backend de 1 hora

#### Cenário: Destaques carregam produtos aleatórios

- **QUANDO** a página inicial renderiza
- **ENTÃO** GET /products é chamado com `sort=createdAt,desc&size=8` (últimos 8 criados)
- **E** cada produto é exibido em card (foto, nome, preço, avaliação, botão "Visualizar")

#### Cenário: Erro ao carregar categorias

- **QUANDO** GET /categories retorna `500` ou `503`
- **ENTÃO** uma mensagem de erro é exibida ("Erro ao carregar categorias. Tente novamente.") com botão Retry
- **E** a seção de produtos continua carregando independentemente

### Requirement: Busca de produtos com query string

O sistema SHALL aceitar parâmetro `q` na URL `/products?q=...` e buscar produtos por nome/descrição com filtros adicionais.

#### Cenário: Busca simples por termo

- **QUANDO** o usuário digita "teclado" no buscador da homepage e pressiona Enter
- **ENTÃO** é feito redirect para `/products?q=teclado`
- **E** GET /products?q=teclado&sort=createdAt,desc&page=0&size=20 é chamado
- **E** os resultados são exibidos em grid (mínimo 2 colunas mobile, 3–4 desktop)

#### Cenário: Query vazia é rejeitada

- **QUANDO** o backend retorna `422 INVALID_QUERY` (query com < 2 caracteres)
- **ENTÃO** a UI valida **antes** de enviar (regex `q.length >= 2`)
- **E** o botão buscar fica desabilitado até atender à regra

#### Cenário: Debounce evita requisições em excesso

- **QUANDO** o usuário digita rápido na busca (`q` muda frequentemente)
- **ENTÃO** as requisições GET /products são feitas com debounce de 300ms
- **E** spinner aparece enquanto há busca pendente

#### Cenário: Nenhum resultado para a busca

- **QUANDO** GET /products retorna `200` mas `content: []`
- **ENTÃO** exibe mensagem "Nenhum produto encontrado para 'teclado'"
- **E** oferece link para limpar filtros ou voltar à homepage

### Requirement: Filtros por categoria, preço, avaliação e disponibilidade

O sistema SHALL prover sidebar de filtros (mobile = offcanvas drawer, desktop = sticky) que sincroniza com URL.

#### Cenário: Filtrar por categoria única

- **QUANDO** usuário seleciona uma categoria (ex.: "Eletrônicos")
- **ENTÃO** URL muda para `/products?categorySlug=eletronicos` (ou `categoryId=3`)
- **E** GET /products?categorySlug=eletronicos&... é reenviado imediatamente
- **E** o filtro fica marcado como "ativo" (highlight em azul)

#### Cenário: Filtrar por múltiplas categorias

- **QUANDO** usuário seleciona 2+ categorias
- **ENTÃO** URL tem `categoryId=3&categoryId=5` (OR lógico)
- **E** GET /products é chamado com os dois IDs
- **E** cada categoria selecionada aparece como badge removível ("Eletrônicos ✕ Livros ✕")

#### Cenário: Filtrar por range de preço

- **QUANDO** usuário move slider minPrice ou maxPrice
- **ENTÃO** URL é atualizada (debounce 300ms): `/products?minPrice=100&maxPrice=500`
- **E** GET /products recebe os parâmetros
- **E** a faceta de preço na resposta atualiza intervalo disponível

#### Cenário: Filtrar por avaliação mínima

- **QUANDO** usuário seleciona "4 estrelas ou mais"
- **ENTÃO** URL muda para `/products?minRating=4`
- **E** GET /products?minRating=4&... filtra produtos
- **E** badge "★ 4+" aparece nos filtros ativos

#### Cenário: Filtrar por disponibilidade

- **QUANDO** usuário marca "Apenas disponíveis"
- **ENTÃO** URL muda para `/products?inStock=true`
- **E** GET /products retorna apenas produtos com `available > 0`
- **E** checkbox fica marcado

#### Cenário: Limpar todos os filtros

- **QUANDO** usuário clica botão "Limpar filtros"
- **ENTÃO** URL volta para `/products` (sem params)
- **E** GET /products é chamado com defaults (sort=createdAt,desc, page=0, size=20)
- **E** todos os filtros visuais são desmarcados

### Requirement: Ordenação de produtos

O sistema SHALL prover dropdown de sort com opções: "Mais recentes", "Preço crescente", "Preço decrescente", "Avaliação".

#### Cenário: Ordenar por preço decrescente

- **QUANDO** usuário seleciona "Preço alto → baixo"
- **ENTÃO** URL muda para `/products?sort=price,desc`
- **E** GET /products?sort=price,desc&... é enviado
- **E** produtos reorganizam de maior para menor preço

#### Cenário: Ordenar por rating

- **QUANDO** usuário seleciona "Melhor avaliados"
- **ENTÃO** URL muda para `/products?sort=rating,desc`
- **E** GET /products?sort=rating,desc&... é enviado
- **E** produtos com rating 5.0 aparecem primeiro

#### Cenário: Ordenação persiste com filtros

- **QUANDO** URL é `/products?categoryId=3&sort=price,asc`
- **ENTÃO** ao refinar categoria, o sort é preservado
- **E** novo GET /products envia ambos os parâmetros

### Requirement: Paginação

O sistema SHALL prover paginação com parâmetros `page` (0-based) e `size` (default 20, máx 100).

#### Cenário: Navegar para próxima página

- **QUANDO** usuário clica "Próximo" ou botão de página "2"
- **ENTÃO** URL muda para `/products?page=1` (0-based)
- **E** GET /products?page=1&size=20&... é chamado
- **E** a seção de produtos scroll para o topo (auto-scroll)

#### Cenário: Página vazia após última

- **QUANDO** usuário está na última página (ex.: página 10 de 10) e clica "Próximo"
- **ENTÃO** botão "Próximo" fica desabilitado (não faz nada)
- **E** lista permanece mostrando últimos resultados

#### Cenário: Paginação mobile vs desktop

- **QUANDO** viewport < 768px (mobile)
- **ENTÃO** paginação exibe apenas "Anterior" / "Próximo" e página atual ("1 de 10")
- **QUANDO** viewport ≥ 1024px (desktop)
- **ENTÃO** paginação exibe números (1 2 3 ... 10) e permite clicar em qualquer página

#### Cenário: Tamanho de página ajustável

- **QUANDO** usuário seleciona "Mostrar 50 por página"
- **ENTÃO** URL muda para `/products?size=50&page=0`
- **E** GET /products?size=50&... carrega 50 produtos
- **E** "Página 1 de 2" se houver 100 produtos

### Requirement: Estados de carregamento e erro

O sistema SHALL exibir estados visuais apropriados: carregamento, vazio, erro.

#### Cenário: Spinner enquanto busca está em voo

- **QUANDO** usuário refina filtro e GET /products está pendente
- **ENTÃO** spinner aparece sobre grid de produtos (ou skeleton cards mobile)
- **E** inputs de filtro continuam responsivos
- **E** botão "Limpar filtros" fica habilitado

#### Cenário: Erro de API com RFC 9457

- **QUANDO** GET /products retorna `500` com `{code: "INTERNAL_SERVER_ERROR", detail: "..."}`
- **ENTÃO** mensagem de erro é exibida: "[E001] Erro interno do servidor"
- **E** RequestId do header é mostrado para suporte
- **E** botão Retry refaz a requisição

#### Cenário: Rate limit

- **QUANDO** GET /products retorna `429` com `Retry-After: 60`
- **ENTÃO** mensagem exibe: "Muitas requisições. Aguarde 60 segundos."
- **E** contador regressivo visual mostra tempo restante
- **E** botão Retry fica desabilitado até timeout

#### Cenário: Timeout na requisição

- **QUANDO** GET /products não responde após 5 segundos
- **ENTÃO** erro de timeout é capturado (status 504 ou timeout da fetch)
- **E** mensagem: "Tempo esgotado. Verifique sua conexão."
- **E** botão Retry está disponível

