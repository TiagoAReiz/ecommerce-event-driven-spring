# Design

## Context

Frontend Vite + React + TypeScript, React Router (data router), TanStack Query, Tailwind CSS na porta 3000. Contratos HTTP em `docs/api-contracts.md` §5 (gateway) e §6 (user). Decisões de segurança do projeto: token Bearer no `Authorization`, sem cookie, renovação deslizante com o próprio token, sem refresh token separado (§2.1). Armazenamento de token: sessão do usuário (memória + `sessionStorage` para recarregar página), nunca `localStorage` (XSS).

Design: branco e azul sóbrio. Azul principal `#1d4ed8`, hover `#1e40af`, destaque `#eff6ff`, superfície branca, texto `#0f172a` (escuro) / `#475569` (médio), borda `#e2e8f0`, font Inter, cantos 12px em cards e 8px em controles, container `max-w-6xl`.

## Goals / Non-Goals

**Goals:**
- Fluxo completo de login, sessão e logout.
- Proteção de rotas com redirect automático para login.
- Edição de perfil com validação de CPF e telefone.
- CRUD de endereços com validação de CEP (8 dígitos sem máscara).
- Exclusão de conta com confirmação explícita e tratamento de erros de negócio (conta da loja, pedido aberto).
- Renovação automática de token antes de expirar.
- Deep linking: após login, volta para a página original.
- Discriminação de papel: `owner` libera acesso à área de gestão da loja.

**Non-Goals:**
- Autenticação por senha ou e-mail/senha.
- Cadastro manual de usuário (só por Google).
- Integração com outro provedor de OAuth.
- Two-factor authentication.

## Decisions

### D1. Armazenamento de token

Token em memória (React context) + `sessionStorage` como fallback. **Jamais em `localStorage`** (risco XSS: roubo persistente). O `sessionStorage` permite sobreviver a refresh da aba, mas é limpo ao fechar a aba (seguro). Implementado via `AuthContext` com custom hook `useAuth()`.

### D2. Captura do token no fragmento

Callback do Google vai para `GET /login/oauth2/code/google`, que devolve `302 Location: {front}/callback#token=…`. O fragmento (`#token=`) **nunca** é enviado a servidor (nem em `Referer`, nem em body). O front JS lê com `location.hash`, extrai o token, limpa a URL com `window.history.replaceState()` para removê-lo do histórico do browser.

### D3. Bootstrap de sessão

Ao montar o app (no componente root ou em um layout guard), se houver token em `sessionStorage`, faz `GET /api/v1/auth/session` para validar e resgatar `name`, `roles`, `expiresAt`. Se falhar (401), descarta o token — o usuário precisa fazer login novamente.

### D4. Renovação automática

Usa `useEffect` com `setInterval` que, a cada 30 s, verifica se o token expira em menos de 5 min. Quando sim, chama `POST /api/v1/auth/refresh` de forma síncrona (se falhar, avisa o usuário — não faz retry silencioso). O novo token sobrescreve o antigo. Se a renovação falha 401 ou 403, redireciona para `/login` de forma síncrona.

### D5. Proteção de rotas

Componente `ProtectedRoute` (ou hook `useProtectedRoute`) que, se sem token, redireciona para `/login?redirect={originalPath}` (com `encodeURIComponent` para deep links). Após o login bem-sucedido, JavaScript lê o parâmetro `redirect` e navega para lá com `navigate(redirect)`.

### D6. Rota de login

`/login` é **pública** (renderiza mesmo sem token). Botão "Entrar com Google" vai para `http://localhost:8080/oauth2/authorization/google` (no host do backend, não do frontend). Página `/login/callback` é privada; redireciona para `/login` se sem token no fragmento. Após extrair o token, redireciona para `/account` (ou para o `redirect` do query param).

### D7. Validações de formulário

**CPF**: 11 dígitos totais + dígitos verificadores (módulo 11, algoritmo padrão da Receita Federal). Rejeita com erro local antes de enviar para o backend. Duplicação é tratada como `409` pelo backend.

**Telefone**: padrão E.164 `^\+[1-9]\d{7,14}$` — código de país obrigatório (+ precedido). Exemplo: `+5511999998888` (Brasil, DDD 11, 9 99998888).

**CEP**: 8 dígitos, **sem máscara** (o contrato rejeita CEP com hífen ou espaço). Input aceita só números; máscara visual opcional no label ("XXXXX-XXX").

**UF**: 2 letras maiúsculas, validação local + backend valida compatibilidade com cidade.

### D8. Edição de endereço no mesmo recurso

`PUT` é substituição completa — campo omitido vira `null`, então o formulário envia todos os
campos. `PATCH` manda só o que mudou. Os dois respondem `200` com o endereço no **mesmo `id`**;
não há criação de recurso novo. Depois de salvar, a lista é invalidada (TanStack Query).

### D9. Exclusão de conta

Diálogo modal com aviso: "Esta ação é irreversível. Todos os seus dados serão removidos. Digite sua senha do Google para confirmar." (na verdade, não pede senha — pede uma confirmação textual como "Sim, remova minha conta"). Backend pode devolver `409 STORE_OWNER_ACCOUNT` ou `409` genérico com pedido aberto; ambos são tratados como erro de negócio com mensagem amigável.

### D10. Papéis

O token do usuario chega com `roles: ["customer"]` ou `roles: ["customer", "owner"]`. O front discrimina:
- Qualquer usuário acessa `/account` (perfil, endereços).
- Só `owner` acessa `/store` (produtos, pedidos, envios).
- Se não-owner tenta acessar `/store`, redireciona para `/account`.

### D11. Invalidação de cache (TanStack Query)

Ao fazer login/logout, invalidar `["auth:session"]`. Ao editar perfil, invalidar `["users", "me"]`. Ao CRUD de endereço, invalidar `["addresses", "list"]` e `["addresses", id]`. Não fazer refetch automático — deixar para o componente decidir se mostra loading.

### D12. Erros de rede

Se `GET /api/v1/auth/session` falha com timeout ou `5xx` no bootstrap, mostra fallback "Conectando..." e retenta com backoff exponencial (100 ms, 200 ms, 400 ms, depois cada 5 s até dar). Depois de 3 tentativas rápidas, avisa "Sem conexão — verifique sua internet".

Erros `4xx` (401, 403, 404) são tratados como sessão inválida → logout e redireciona para `/login`.

## Risks / Trade-offs

- [Token em `sessionStorage`] → Não sobrevive a fechar a aba, exige novo login. Trade-off: segurança > conveniência.
- [Renovação automática síncrona] → Se falha, congela a UX até timeout. Mitigação: timeout curto (3 s), mensagem de aviso.
- [Discriminação de papel em JS] → Navegação escondida, não uma trava de segurança; o backend rejeita rotas `owner` sem escopo.

## Implementation Decisions

- **AuthContext**: globaliza sessão com `const { token, roles, expiresAt, logout, loginGoogle } = useAuth()`.
- **useAuth hook**: custom hook que lê context e tira `useNavigate`, `useLocation` para redirect.
- **useSession query**: TanStack Query (`useQuery`) que faz `GET /api/v1/auth/session` e refetch em erro.
- **useRefreshToken mutation**: `useMutation` que faz `POST /api/v1/auth/refresh` e atualiza context.
- **ProtectedRoute component**: `<Outlet>` se autenticado, `<Navigate to="/login">` caso contrário (React Router v6+).
- **AddressForm component**: reutilizável para POST (criar) e PUT/PATCH (editar), com validações locais.
- **API client**: centralizado em `src/api/client.ts` com interceptor que adiciona `Authorization: Bearer {token}` e trata `401` globalmente (logout).
- **Error boundaries**: captura erros de rede e mostra toast com `code` do problema (ex.: `STORE_OWNER_ACCOUNT`).

## Estrutura de pastas

```
front/src/
├── features/
│   ├── auth/
│   │   ├── AuthContext.tsx        # Context + Provider
│   │   ├── useAuth.ts             # Custom hook
│   │   ├── ProtectedRoute.tsx      # Guard de rota
│   │   ├── LoginPage.tsx           # /login
│   │   └── LoginCallbackPage.tsx   # /login/callback
│   └── account/
│       ├── AccountLayout.tsx       # Layout de /account/**
│       ├── ProfilePage.tsx         # /account/profile
│       ├── EditProfilePage.tsx     # /account/profile/edit
│       ├── AddressesPage.tsx       # /account/addresses
│       ├── AddressFormPage.tsx     # /account/addresses/new, /account/addresses/:id/edit
│       ├── hooks/
│       │   ├── useSession.ts       # useQuery para sessão
│       │   ├── useRefreshToken.ts  # useMutation para renovação
│       │   ├── useAddresses.ts     # useQuery para lista de endereços
│       │   └── useCPFValidation.ts # validação de CPF
│       ├── components/
│       │   ├── ProfileForm.tsx
│       │   ├── AddressForm.tsx
│       │   ├── DeleteAccountDialog.tsx
│       │   └── AddressList.tsx
│       └── api/
│           ├── sessionApi.ts       # GET/POST /api/v1/auth/**
│           └── addressApi.ts       # CRUD /api/v1/users/me/addresses
```
