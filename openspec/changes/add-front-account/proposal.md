# Proposal

> **Nota de implementação.** A stack do front mudou depois desta proposta: saiu Vite + React
> Router e entrou Next.js com App Router, com a vitrine pública renderizada no servidor. As
> telas, as rotas de API e o desenho descritos aqui continuam valendo; o motivo da troca está
> em `docs/decisions.md`.


## Why

O frontend precisa de interface completa para autenticação pelo Google, gerenciamento de sessão (renovação deslizante antes de expirar, logout) e acesso à conta pessoal do usuário. Sem essas telas, o usuário não consegue fazer login, manter a sessão viva nem acessar/editar o perfil e endereços para o checkout.

## What Changes

- Fluxo de login: redirecionamento para Google, captura do token no fragmento da URL, limpeza imediata da URL para não expor o token no histórico.
- Armazenamento do token e papéis em memória (React context). Persistência em `sessionStorage` apenas para sobreviver a refresh da página, nunca em `localStorage` (XSS mitigation).
- Bootstrap de sessão ao abrir a app: validação do token com o gateway (`GET /api/v1/auth/session`), resgate dos papéis do usuário.
- Renovação automática antes da expiração (5 min antes do `expiresAt`), com retry exponencial em caso de falha.
- Logout com revogação no gateway (`POST /api/v1/auth/logout`), limpeza do token local.
- Rota protegida que redireciona para login se sem token; após o login, volta para a página original (deep linking).
- Perfil de leitura: `GET /users/me` mostrando dados completos e contagem de endereços.
- Edição de perfil: `PATCH /users/me` com validação de CPF (11 dígitos + verificadores) e telefone (E.164 `^\+[1-9]\d{7,14}$`).
- Exclusão de conta: `DELETE /users/me` com diálogo de confirmação explícita, tratamento de `409` (conta da loja ou pedido em aberto).
- CRUD de endereços: listar (paginado), ver, criar, editar (`PUT` substitui tudo, `PATCH` só o que mudou, mesmo `id`), deletar.
  - Validação de CEP: 8 dígitos **sem máscara** (contrato exige isso).
  - UF: 2 letras maiúsculas quando `country=BR`.
- Papel `owner`: quando presente no token, libera acesso à área de gestão da loja (rotas sob `/api/v1/products/manage`, `/api/v1/orders/manage`, `/api/v1/shipments/manage`, redirecionadas internamente para componentes da área "Store").

## Capabilities

### New Capabilities

- `front-auth-login`: fluxo de login Google com captura de token no fragmento.
- `front-account-session`: gerenciamento de sessão (validação, renovação automática, logout).
- `front-account-profile`: visualização e edição de perfil pessoal.
- `front-account-addresses`: CRUD de endereços de entrega com validação de CEP e UF.

### Modified Capabilities

Nenhuma (este é o primeiro frontend).

## Impact

- Novo diretório `front/src/features/account/` com componentes, hooks e context de autenticação.
- Novo middleware/hook de proteção de rotas em `front/src/features/auth/ProtectedRoute`.
- Integração com React Router (data router) para carregamento de dados antes de renderizar rota.
- Consumo de `TanStack Query` (React Query) para queries HTTP (`useQuery`, `useMutation`).
- Nenhum impacto no backend: tudo é consumo de APIs já presentes ou planejadas.
