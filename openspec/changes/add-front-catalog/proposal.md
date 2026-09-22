# Proposta

> **Nota de implementação.** A stack do front mudou depois desta proposta: saiu Vite + React
> Router e entrou Next.js com App Router, com a vitrine pública renderizada no servidor. As
> telas, as rotas de API e o desenho descritos aqui continuam valendo; o motivo da troca está
> em `docs/decisions.md`.


## Por que

A loja não tem interface pública de catálogo. Cliente não consegue navegar produtos, buscar por nome, filtrar por categoria, comparar preços nem ver avaliações. Sem vitrine, não há comércio.

## O que muda

**Telas públicas (sem autenticação):**
- **Home** (`/`): Categorias com contagem de produtos e destaques aleatórios. Acesso a GET /categories.
- **Listagem** (`/products`): Busca, filtro por categoria, ordenação por preço/rating/data, paginação. Acesso a GET /products com query params (q, categoryId, categorySlug, minPrice, maxPrice, minRating, inStock, page, size, sort).
- **Detalhe** (`/products/:id`): Galeria de fotos (GET /products/{id}/photos), disponibilidade calculada (GET /products/{id}/availability), resumo de avaliações com distribuição de notas (GET /products/{id}/reviews), calculadora de frete antes do checkout (GET /shipping/quote), botão "Adicionar ao carrinho" que dispara ação de carrinho (de outro módulo).

**Comportamentos:**
- Estados de carregamento, vazio e erro em todas as telas.
- Mapeamento de códigos HTTP do contrato (RFC 9457 com campo `code`) para mensagens ao usuário.
- Responsivo: celular primeiro, sem rolagem horizontal, breakpoints Tailwind padrão.
- Tratamento de 401 em `/shipping/quote` levando ao login (frete é público mas o token de serviço é interno).

**Estrutura e padrões:**
- React Router v7 data router: loaders para pré-carregar dados, actions para mutações (futuro).
- TanStack Query v5 para caching e sincronização: cache inteligente sem refetch desnecessário.
- Componentes reutilizáveis em `front/src/shared/components/` (Card, Badge, Button, LoadingSpinner, ErrorMessage, Pagination, etc.).
- Hooks customizados em `front/src/shared/hooks/` (useApi, useQueryParams, etc.).
- Módulo `front/src/features/catalog/` com rotas, componentes de telas, serviços de API, tipos TypeScript.

## Capacidades

### Novas
- `catalog-public-browsing`: Catálogo navegável com busca, filtro e paginação.
- `product-details`: Detalhe de produto com galeria, disponibilidade, avaliações e frete.
- `shipping-quote`: Calculadora de frete antes do login.

### Modificadas
- (nenhuma)

## Impacto

- `front/` apenas. Novas rotas, componentes, hooks, tipos em `src/features/catalog/` e `src/shared/`.
- Depende de: GET /categories, GET /products, GET /products/{id}, GET /products/{id}/photos, GET /products/{id}/availability, GET /products/{id}/reviews, GET /shipping/quote — todas públicas na borda, definidas em `docs/api-contracts.md` §7.
- Não acrescenta rota ao backend: consome o que já existe.
- Código React segue stack Vite + TypeScript + Tailwind CSS decidida, porta 3000.
