# Spec Delta: Front-end — Gerenciamento de Sessão

## Purpose

Implementa validação de sessão ao iniciar a app, renovação automática antes de expirar e logout seguro, garantindo que o usuário está sempre autenticado e que o token é revogado quando sair.

## ADDED Requirements

### Requirement: Bootstrap de sessão ao iniciar app

O front-end SHALL validar o token armazenado em `sessionStorage` logo ao iniciar, antes de renderizar a app.

#### Scenario: App inicia com token válido em sessionStorage
- **WHEN** o usuário abre a aba e o `sessionStorage` contém um token
- **THEN** durante o carregamento da app, chama `GET /api/v1/auth/session` para validar; se sucesso (200), carrega dados do usuário e renderiza a app normalmente

#### Scenario: Bootstrap bem-sucedido
- **WHEN** `GET /api/v1/auth/session` retorna `{ id, name, email, roles, expiresAt }`
- **THEN** o AuthContext armazena `{ token, roles, expiresAt }` e renderiza a app; logo após, inicia timer de renovação

#### Scenario: Bootstrap com token expirado ou inválido (401)
- **WHEN** `GET /api/v1/auth/session` retorna `401`
- **THEN** token em `sessionStorage` é descartado; AuthContext limpa sessão; app renderiza `/login` normalmente

#### Scenario: Bootstrap com erro de rede
- **WHEN** `GET /api/v1/auth/session` falha com timeout ou `5xx`
- **THEN** app mostra tela de "Carregando..." com aviso "Sincronizando sessão" e retenta com backoff; após 3 falhas, oferece opção "Tentar novamente" manualmente

#### Scenario: App inicia sem token
- **WHEN** `sessionStorage` não contém token (primeiro acesso ou após fechar aba)
- **THEN** AuthContext começa com `token = null`; app renderiza `/login` normalmente

### Requirement: Validação de sessão com retry exponencial

O front-end SHALL tentar validar a sessão com retry automático em caso de falha de rede.

#### Scenario: Primeiro retry rápido (100 ms)
- **WHEN** `GET /api/v1/auth/session` falha com timeout
- **THEN** retenta após 100 ms

#### Scenario: Segundo retry (200 ms)
- **WHEN** primeira retentativa falha
- **THEN** retenta após 200 ms

#### Scenario: Terceiro retry (400 ms)
- **WHEN** segunda retentativa falha
- **THEN** retenta após 400 ms

#### Scenario: Retries subsequentes (5 s cada)
- **WHEN** terceira retentativa falha
- **THEN** continua retentando a cada 5 s indefinidamente (ou até que o usuário clique "Parar de tentar")

#### Scenario: Retry bem-sucedido
- **WHEN** qualquer retry retorna `200`
- **THEN** a app para de retentar e processa a resposta normalmente

### Requirement: Renovação automática de token a cada 30 segundos

O front-end SHALL verificar a cada 30 s se o token expira em menos de 5 min e, se sim, renovar antes que expire.

#### Scenario: Timer de renovação inicia após bootstrap
- **WHEN** a app renderiza com sessão válida
- **THEN** um `setInterval(30000)` é criado no AuthContext; a cada 30 s, verifica `now + 5 min >= expiresAt`

#### Scenario: Renovação antecipada (5 min antes de expirar)
- **WHEN** a verificação detecta que faltam 5 min ou menos para expirar
- **THEN** chama `POST /api/v1/auth/refresh` de forma síncrona (sem UI bloqueante)

#### Scenario: Novo token recebido
- **WHEN** `POST /api/v1/auth/refresh` retorna `200` com `{ accessToken, expiresIn, expiresAt, sessionExpiresAt }`
- **THEN** token em context + `sessionStorage` é atualizado; timer continua rodando

#### Scenario: Renovação com falha de rede (retry silencioso)
- **WHEN** `POST /api/v1/auth/refresh` falha com timeout ou `5xx`
- **THEN** a app mostra toast não-bloqueante "Sincronizando sessão..." e retenta a cada 1 s (até 3 vezes rápidas); depois cada 5 s; o usuário pode continuar usando a app

#### Scenario: Renovação com token inválido (401)
- **WHEN** `POST /api/v1/auth/refresh` retorna `401` (token expirou antes de renovar)
- **THEN** a app **não faz retry**; redireciona para `/login` com mensagem "Sessão expirada. Faça login novamente."

#### Scenario: Renovação com SESSION_EXPIRED (7 dias)
- **WHEN** `POST /api/v1/auth/refresh` retorna `401 SESSION_EXPIRED`
- **THEN** a app redireciona para `/login` com mensagem "Sua sessão expirou (máximo de 7 dias). Faça login novamente."

#### Scenario: Cleanup ao logout
- **WHEN** usuário faz logout ou token é invalidado
- **THEN** o `setInterval` é cancelado via `clearInterval`

### Requirement: Proteção de rotas — redireciona para login se sem token

O front-end SHALL impedir acesso a rotas de conta se o usuário não está autenticado.

#### Scenario: Rota protegida sem token
- **WHEN** usuário sem token tenta acessar `/account`, `/account/profile`, `/account/addresses`, etc.
- **THEN** middleware/componente `ProtectedRoute` detecta `useAuth().token === null` e redireciona para `/login?redirect={encodedPath}`

#### Scenario: Rota protegida com token expirado
- **WHEN** durante a navegação, token expira ou é rejeitado pelo backend
- **THEN** interceptor de erro HTTP trata `401` e redireciona para `/login`

#### Scenario: Rota protegida owner-only sem papel owner
- **WHEN** usuário customer tenta navegar para `/store`
- **THEN** redireciona para `/account` com mensagem "Você não tem permissão para acessar este recurso"

#### Scenario: Rota protegida renderiza normalmente com token válido
- **WHEN** usuário autenticado acessa `/account`
- **THEN** o componente `ProtectedRoute` renderiza `<Outlet>` normalmente

### Requirement: Logout seguro com revogação

O front-end SHALL revogar o token no backend antes de limpar a sessão local.

#### Scenario: Logout iniciado
- **WHEN** usuário clica em botão "Sair" ou link "Logout"
- **THEN** a app chama `POST /api/v1/auth/logout` com `Authorization: Bearer {token}`

#### Scenario: Logout bem-sucedido (204)
- **WHEN** `POST /api/v1/auth/logout` retorna `204`
- **THEN** token é removido de context + `sessionStorage`; timer de renovação é cancelado; redireciona para `/login` com mensagem "Saída realizada com sucesso"

#### Scenario: Erro ao fazer logout (5xx)
- **WHEN** `POST /api/v1/auth/logout` falha com timeout ou `5xx`
- **THEN** mostra toast "Erro ao fazer logout" mas **limpa token local de qualquer forma** (segurança em primeiro lugar); redireciona para `/login` após 2 s

#### Scenario: Token inválido ao tentar logout (401)
- **WHEN** `POST /api/v1/auth/logout` retorna `401`
- **THEN** token já era inválido; limpa local e redireciona para `/login` silenciosamente

### Requirement: Invalidação global de cache ao mudar autenticação

O front-end SHALL descartar todas as queries em cache quando a sessão muda.

#### Scenario: Após login bem-sucedido
- **WHEN** login completa e app redireciona para `/account`
- **THEN** TanStack Query invalida cache `['auth', 'session']` e todas as queries de dados do usuário

#### Scenario: Após logout
- **WHEN** logout completa
- **THEN** TanStack Query limpa **todo** o cache (ou invalida chaves críticas como `['auth', 'session']`, `['addresses']`, etc.)

#### Scenario: Após renovação de token
- **WHEN** `POST /api/v1/auth/refresh` completa com novo token
- **THEN** não invalida cache automaticamente; o novo token é válido para as mesmas queries

### Requirement: Context AuthProvider globaliza sessão

O front-end SHALL prover uma API simples via `useAuth()` hook para acessar sessão em qualquer componente.

#### Scenario: useAuth hook em componente
- **WHEN** componente chama `const { token, roles, expiresAt, logout, isOwner } = useAuth()`
- **THEN** acessa sessão global sem prop drilling

#### Scenario: Verificação de autenticação
- **WHEN** componente precisa saber se usuário está logado
- **THEN** chama `useAuth().token !== null` para decisão condicional de render

#### Scenario: Verificação de papel owner
- **WHEN** componente precisa verificar se é owner
- **THEN** chama `useAuth().isOwner` (derivado de `roles.includes('owner')`)

#### Scenario: Métodos de logout
- **WHEN** componente precisa deslogar
- **THEN** chama `useAuth().logout()` que trata tudo (API call, limpeza local, redirect)
