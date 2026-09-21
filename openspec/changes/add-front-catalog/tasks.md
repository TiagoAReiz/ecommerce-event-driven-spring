# Tarefas

## 1. Infraestrutura

- [ ] 1.1 Criar `front/src/shared/hooks/` e `front/src/shared/components/` base.
- [ ] 1.2 Criar `front/src/application/models/` com types `Product`, `Category`, `Review`, `ReviewSummary`, `Availability`, `ShippingQuote`, `ProblemDetail` (RFC 9457).
- [ ] 1.3 Criar `front/src/infra/api/` com `apiClient` (axios configurado para `/api/v1`) e `httpErrorHandler()` que mapeia `code` do RFC 9457.
- [ ] 1.4 Criar `front/src/shared/hooks/useApi.ts` (wrapper TanStack Query com retry logic e logging).
- [ ] 1.5 Instalar/verificar deps: `vite`, `react`, `typescript`, `tailwindcss`, `react-router-dom`, `@tanstack/react-query`, `axios`.

## 2. Serviços de API

- [ ] 2.1 Criar `front/src/infra/api/CatalogService.ts`:
  - `getCategories()` — GET /categories
  - `getProducts(params: SearchParams)` — GET /products com query params (q, categoryId, categorySlug, minPrice, maxPrice, minRating, inStock, page, size, sort)
  - `getProduct(id: number)` — GET /products/{id}
  - `getProductPhotos(id: number)` — GET /products/{id}/photos
  - `getProductAvailability(id: number)` — GET /products/{id}/availability
  - `getProductReviews(id: number, params)` — GET /products/{id}/reviews com rate, page, size
- [ ] 2.2 Criar `front/src/infra/api/ShippingService.ts`:
  - `getShippingQuote(zipcode: string)` — GET /shipping/quote

## 3. Componentes reutilizáveis

- [ ] 3.1 Criar `LoadingSpinner.tsx` (skeleton em Mobile, spinner circular em Desktop).
- [ ] 3.2 Criar `ErrorMessage.tsx` (exibe RFC 9457 `code` e `detail`, botão Retry).
- [ ] 3.3 Criar `ProductCard.tsx` (foto, nome, preço, rating, "Visualizar" link).
- [ ] 3.4 Criar `CategoryBadge.tsx` (para tags de categoria).
- [ ] 3.5 Criar `Pagination.tsx` (anterior/próximo em mobile, numérico em desktop).
- [ ] 3.6 Criar `PriceDisplay.tsx` (formata string decimal para "R$ XX,XX").
- [ ] 3.7 Criar `RatingStars.tsx` (exibe média com estrelas e contagem de reviews).
- [ ] 3.8 Criar `GalleryModal.tsx` (fullscreen, setas, ESC para fechar, zoom).
- [ ] 3.9 Criar `FilterSidebar.tsx` (categoria, preço range, inStock toggle, minRating).

## 4. Hooks customizados

- [ ] 4.1 Criar `useQueryParams.ts` (helper de `useSearchParams()`, sync de URL/estado).
- [ ] 4.2 Criar `usePriceRange.ts` (calcula min/max do facet, controla slider).
- [ ] 4.3 Criar `useShippingQuote.ts` (chamada a ShippingService, cache por CEP, formatação).
- [ ] 4.4 Criar `useProductDetail.ts` (carrega produto + fotos + availability + reviews em paralelo, TanStack Query).

## 5. Páginas

- [ ] 5.1 Criar `HomePage.tsx`:
  - Loader: `getCategories()` + `getProducts({ inStock: true, sort: 'createdAt,desc', size: 8 })` (destaques).
  - Layout: Header com logo, busca rápida; Cards de categorias; "Produtos em destaque" seção.
  - Estados: Loading, erro, vazio.
  - Responsivo: mobile 1 col categoria, desktop 2–3 cols.
- [ ] 5.2 Criar `ListingPage.tsx`:
  - Loader: executa busca com params da URL.
  - Layout: Sidebar filtros (mobile = offcanvas), grid de produtos, pagination.
  - Funcionalidades: debounce no input `q` (300 ms), live filter updates.
  - Estados: Loading, erro, vazio (sem resultados).
  - URL sync: toda mudança de filtro → `setSearchParams()`.
- [ ] 5.3 Criar `ProductDetailPage.tsx`:
  - Loader: `getProduct(id)` + `getProductPhotos(id)` + `getProductReviews(id)`.
  - Seções: Galeria (à esquerda em desktop, topo em mobile), detalhes (direita), reviews abaixo.
  - Detalhes: nome, preço, categoria, descrição, disponibilidade (com badge "Em estoque"/"Fora de estoque"), botão "Adicionar ao carrinho".
  - Galeria: foto principal com zoom, thumbnails scrolláveis.
  - Frete: input CEP + botão "Calcular" → modal com resultado ou erro.
  - Reviews: resumo (média + distribuição), lista paginada, filtro por nota.
  - Estado: Loading, erro, produto não encontrado (404).

## 6. Roteamento

- [ ] 6.1 Criar `front/src/routes/index.ts` com React Router data router:
  ```
  / → HomePage
  /products → ListingPage
  /products/:id → ProductDetailPage
  ```
- [ ] 6.2 Loaders para cada página (pré-carregamento de dados).
- [ ] 6.3 Error boundary por rota (fallback 404 para product not found).

## 7. Layout base

- [ ] 7.1 Criar `Layout.tsx` (Header com logo, nav, busca global; Footer).
- [ ] 7.2 Header: logo esquerda, buscador centro (autocomplete futuro), carrinho icon direita (link a `/cart`, badge com contagem), ícone login.
- [ ] 7.3 Footer: links úteis, contato, copyright.

## 8. Estilos

- [ ] 8.1 Tailwind config: cores do design (azul `#1d4ed8`, hover `#1e40af`, bg `#eff6ff`, border `#e2e8f0`).
- [ ] 8.2 Fonte Inter via Google Fonts.
- [ ] 8.3 Border radius: 12px cards, 8px botões/inputs.
- [ ] 8.4 Breakpoints: mobile, tablet (md), desktop (lg).
- [ ] 8.5 Dark mode setup (media query, nenhuma cor hardcodeada).

## 9. Tipos e validação

- [ ] 9.1 Criar `front/src/shared/types/index.ts` com:
  - `SearchParams` (interface com q, categoryId, categorySlug, minPrice, maxPrice, minRating, inStock, page, size, sort).
  - `Product`, `Category`, `Review`, `ReviewSummary`, `Availability`, `ShippingQuote` (conforme API).
  - `ProblemDetail` (RFC 9457 com `code`, `status`, `detail`, `errors[]`, `instance`, `type`, `title`, `requestId`, `timestamp`).
- [ ] 9.2 Criar validadores: `isValidCEP()`, `isValidPrice()`, `isValidPage()`.

## 10. Testes

- [ ] 10.1 Configurar Vitest + React Testing Library.
- [ ] 10.2 Escrever testes para `CatalogService` (mocks com MSW).
- [ ] 10.3 Escrever testes para componentes: `ProductCard`, `Pagination`, `ErrorMessage`.
- [ ] 10.4 Escrever testes de integração para `HomePage` (renderização, eventos).

## 11. Performance

- [ ] 11.1 Lazy loading de imagens (Intersection Observer).
- [ ] 11.2 Code splitting de rotas (React lazy + Suspense).
- [ ] 11.3 Otimização de bundle (Rollup config, tree-shaking).
- [ ] 11.4 Lighthouse check: target Score ≥ 80 (Performance, Accessibility, Best Practices).

## 12. Verificação final

- [ ] 12.1 Rodar `npm run dev` localmente (porta 3000).
- [ ] 12.2 Testar navegação home → listagem → detalhe → filtros → frete (E2E manual).
- [ ] 12.3 Testar estados de erro: API indisponível, produto não encontrado, frete CEP inválido.
- [ ] 12.4 Testar responsividade em Chrome DevTools (mobile 375px, tablet 768px, desktop 1920px).
- [ ] 12.5 Build de produção: `npm run build` sem erro.
