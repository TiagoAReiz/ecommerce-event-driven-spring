# Design

## Contexto

Front-end em Vite + React 19 + TypeScript + Tailwind CSS 4, porta 3000. React Router v7 com data router. TanStack Query v5. Design: branco, azul `#1d4ed8`, Inter font, cards 12px, controles 8px, container `max-w-6xl`. Contratos de API em `docs/api-contracts.md` §7 (inventory: produtos, categorias, avaliações, fotos, disponibilidade) e §10.3 (shipment: frete). Todas as rotas são públicas na borda (autenticação opcional).

## Metas / Fora do escopo

**Metas:** Catálogo navegável com buscas, filtros, paginação; detalhe do produto com galeria, disponibilidade em tempo real, avaliações e calculadora de frete; layout responsivo mobile-first.

**Fora do escopo:** Login (é do módulo `auth`), carrinho (é do módulo `cart`), checkout (é do módulo `order`), avaliação (será do módulo `reviews` futuro), painel administrativo.

## Decisões

### D1. Estrutura de rotas com React Router data router

```
/                           → HomePage (categorias + destaques)
  loader: carregaCategoriasEDestaques()
/products                   → ListingPage (busca, filtro, ordenação, paginação)
  loader: executaPesquisa()
  searchParams: q, categoryId, categorySlug, minPrice, maxPrice, minRating, inStock, page, size, sort
/products/:id               → ProductDetailPage (galeria, disponibilidade, reviews, frete)
  loader: carregaDetalheCompleto()
```

`useLoaderData()` retorna dados pré-carregados. Busca refinada no cliente com `useSearchParams()` sem refetch desnecessário (TanStack Query + ETag).

### D2. Arquitetura por camadas

- **`infra/api/`**: `apiClient` (axios com base URL `/api/v1`), `CatalogService` (chamadas GET /products*, /categories*), `ShippingService` (GET /shipping/quote*).
- **`application/models/`**: `Product`, `Category`, `Review`, `ReviewSummary`, `Availability`, `ShippingQuote` — types DTO do backend com suporte a `decimal` via string.
- **`shared/components/`**: `ProductCard`, `CategoryBadge`, `LoadingSpinner`, `ErrorMessage`, `Pagination`, `PriceDisplay`, `RatingStars`, `GalleryModal`, `FilterSidebar` — todos com Tailwind, acessíveis (ARIA).
- **`shared/hooks/`**: `useApi()` (wrapper TanStack Query), `useQueryParams()` (helper de URL), `usePriceRange()` (minmax em filtro), `useShippingQuote()` (calcula frete pela caixa postal).
- **`features/catalog/pages/`**: `HomePage.tsx`, `ListingPage.tsx`, `ProductDetailPage.tsx`.

### D3. TanStack Query com cache inteligente

Chaves de cache por endpoint:
- `['categories']` — TTL 3600 s (backend é 1 h, lista imutável por migration).
- `['products', queryHash]` — TTL 60 s (busca, cache por sha1 da query).
- `['product', id]` — TTL 600 s (detalhe).
- `['product', id, 'photos']` — TTL 600 s (galeria, muda com produto).
- `['product', id, 'availability']` — **sem cache** (nunca cacheado no frontend, recalculado a cada render; faz fetch sempre).
- `['product', id, 'reviews', rate, page]` — TTL 60 s (reviews).
- `['shipping-quote', zipcode]` — TTL 3600 s (geografia não muda, cache de 1 h).

`staleTime` (quanto tempo antes de refetch): `availability` = 0 (sempre stale), outros = 300 s (5 min).

### D4. Tratamento de erros conforme RFC 9457

Mapeamento de `code` (do campo `code` em `application/problem+json`) para mensagens:

| Código HTTP | RFC 9457 `code` | Ação no front |
|---|---|---|
| 400 | Múltiplos (ex.: `INVALID_PRICE_RANGE`) | Exibir `detail` do problema em tooltip/inline |
| 401 | (não esperado em rota pública) | Redirect a `/login` |
| 403 | (não esperado) | Mensagem "acesso negado" |
| 404 | Múltiplos | Exibir "Produto não encontrado" em detalhe ou "Nenhum resultado" em listagem |
| 422 | `ZIPCODE_NOT_GEOCODED` | "CEP não encontrado na base de dados" |
| 500 / 502 / 503 / 504 | Múltiplos | Exibir "Erro no servidor. Tente novamente." com retry |
| 429 | (rate limit) | "Muitas requisições. Aguarde..." com exponential backoff (max 3 tentativas) |

Mensagens em português, sem jargão técnico. `RequestId` do header `X-Request-Id` aparece em erros persistentes (para suporte).

### D5. Responsividade mobile-first

- **Mobile** (< 768px): Stack vertical, 1 coluna de produtos, busca em modo "acordeão" (expandir/colapsar filtros), paginação com botões "Anterior/Próximo", galeria fullscreen em modal.
- **Tablet** (768–1024px): 2 colunas de produtos, sidebar de filtros em offcanvas (drawer), paginação numérica.
- **Desktop** (≥ 1024px): 3–4 colunas, sidebar de filtros sticky, container `max-w-6xl` centralizado.

Sem rolagem horizontal. Imagens otimizadas (`srcSet` com webp, lazy loading). Fontes carregam via Google Fonts em paralelo.

### D6. Galeria de fotos (detalhe do produto)

- Foto principal com zoom (hover no desktop, swipe no mobile).
- Thumbnails abaixo (máx. 4 visíveis, scroll se mais).
- Modal fullscreen ao clicar em thumbnail (setas de navegação, ESC para fechar).
- Carregamento lazy das fotos fora de viewport.
- Fallback: ícone de "sem foto" se array vazio ou erro de carregamento.

### D7. Calculadora de frete

Integrada no detalhe do produto:
1. Input de CEP (máscara `#####-###`).
2. Botão "Calcular" chamando GET /shipping/quote?zipcode={cep}.
3. Resposta mostra: `distanceKm`, `freightCost`, origem (`from_*`).
4. Cache de 1 h por CEP (mesmo CEP não recalcula).
5. Estados: *entrada vazia* → desabilitado | *carregando* → spinner | *erro* → mensagem (422 = "CEP inválido", 503 = "Indisponível temporariamente") | *sucesso* → exibe resultado com "Frete: R$ 25,00".

Não há validação de CEP no frontend: deixar o backend rejeitar inválidos (422).

### D8. Listagem com busca e filtros

**Query parameters persistidos na URL:**
- `q=` — termo de busca (mín. 2 caracteres; backend retorna 422 se < 2).
- `categoryId=1&categoryId=2` — repetível, OR lógico.
- `categorySlug=eletronicos` — alternativa a categoryId (usar slug se disponível, é mais legível).
- `minPrice=` / `maxPrice=` — range de preço.
- `minRating=4` — mínimo de estrelas (1–5).
- `inStock=true` — filtro "disponível".
- `page=0` — zero-based.
- `size=20` — default.
- `sort=price,asc` ou `price,desc` ou `rating,desc` ou `createdAt,desc` (padrão).

**Comportamento:**
- Busca em tempo real: debounce 300 ms no input `q` antes de refetch.
- Filtros: dropdown de categoria (pré-carregado de GET /categories), slider de preço (min/max do facet), toggle inStock.
- URL syncroniza sempre: `useSearchParams()` → onChange handler → `setSearchParams()` → refetch.
- Filtro "Limpiar" reseta todos os params.

### D9. Ratings e resumo de avaliações

GET /products/{id}/reviews retorna `summary` com `distribution: { "5": 38, "4": 9, "3": 3, "2": 1, "1": 1 }`.

**Exibição no detalhe:**
- Estrela média e contagem: "⭐ 4.6 (52 avaliações)".
- Gráfico de barras horizontal para cada nota (5 → 4 → 3 → 2 → 1) mostrando % e contagem.
- Lista de comentários com autoria (GET /reviews retorna `author.name`, `author.photoUrl`), data, nota e texto.
- Paginação de reviews (default 20 por página).
- Filtro por nota (dropdown: "Mostrar todas" / "5 estrelas" / "4+" / etc.).

### D10. Eventos e rastreamento

Componentes dispõem eventos para análise (futuro):
- `catalog:view_home` — usuário visitou home.
- `catalog:search` — termo buscado, resultados retornados.
- `catalog:filter` — categoria/preço/rating selecionados.
- `catalog:view_product` — produto visitado (id, nome, preço).
- `catalog:shipping_quote` — CEP consultado, resultado (frete).
- `catalog:add_to_cart` — botão "Adicionar ao carrinho" clicado (sem efeito aqui, dispara ação ao carrinho).

Rastreamento via `window.gtag` (Google Analytics, setup futuro).

## Riscos / Trade-offs

- **Latência de buscas**: Query params na URL refazem o fetch; debounce em `q` mitiga o volume de requisições.
- **Sincronização de estado**: `useSearchParams()` + TanStack Query pode gerar race conditions se parâmetros mudam rápido; cache key inclui query hash, evita confusão.
- **Disponibilidade em tempo real**: GET /availability não é cacheado, bate o backend sempre; preço no carrinho pode divergir se usuário demora para finalizar compra. Aceitável: preço revalidado no checkout.
- **Fallback offline**: Sem suporte. Rede indisponível = "Erro ao carregar", sem cache de leitura prévia.
- **Performance de imagem**: Galeria com muitas fotos carrega tudo ao descer scroll; lazy loading via Intersection Observer mitiga.

## Padrões de implementação

- **Componentes funcionais** com hooks (sem class components).
- **Divisão por arquivo**: um componente = um arquivo `.tsx`.
- **Tipos TypeScript** para todo DTO do backend (gerados de `docs/api-contracts.md`).
- **Tailwind utilities** direto em `className` (sem CSS modules), dark mode via `@media (prefers-color-scheme: dark)`.
- **Acessibilidade**: `aria-label`, `role`, `tabindex`, cores com contraste ≥ 4.5:1, formulários com `<label>`.
- **Error boundaries** com fallback UI em caso de crash de componente.
- **Testing**: Vitest + React Testing Library para componentes, MSW para mocks de API.
- **Logging**: `console.warn` para erros de fetch, `console.info` para navegação de rota.
