# Tasks

## 1. Infraestrutura e setup

- [ ] 1.1 Criar `front/src/api/client.ts` com axios/fetch + interceptor de `Authorization: Bearer`; trata `401` com logout global; verificar compile.
- [ ] 1.2 Criar `front/src/features/auth/AuthContext.tsx` com provider de `{ token, roles, expiresAt, setToken, logout }`; armazena em `sessionStorage` sob chave `auth:token`; verificar compile.
- [ ] 1.3 Criar `front/src/features/auth/useAuth.ts` custom hook que lê context e expõe `{ token, roles, expiresAt, logout, isOwner }`; verificar compile.

## 2. Login e sessão

- [ ] 2.1 Criar `front/src/features/auth/LoginPage.tsx` (`/login`): botão "Entrar com Google" que redireciona para `http://localhost:8080/oauth2/authorization/google`; verificar build.
- [ ] 2.2 Criar `front/src/features/auth/LoginCallbackPage.tsx` (`/login/callback`): extrai token do fragmento (`location.hash`), limpa URL com `window.history.replaceState()`, valida com `GET /api/v1/auth/session`, salva em context + `sessionStorage`, redireciona para `/account` ou `?redirect=...`; verificar build.
- [ ] 2.3 Criar `front/src/features/auth/ProtectedRoute.tsx`: redireciona para `/login?redirect={currentPath}` se sem token; caso contrário renderiza `<Outlet>`; verificar build.
- [ ] 2.4 Criar `front/src/features/account/hooks/useSession.ts`: `useQuery(['auth', 'session'])` que chama `GET /api/v1/auth/session`, com retry exponencial; verificar build.
- [ ] 2.5 Criar `front/src/features/account/hooks/useRefreshToken.ts`: `useMutation` que chama `POST /api/v1/auth/refresh` com `{ accessToken, tokenType, expiresIn, expiresAt, sessionExpiresAt }`; atualiza context; verificar build.
- [ ] 2.6 Integrar renovação automática no AuthContext: `useEffect` com `setInterval` que a cada 30 s verifica `expiresAt - now < 5 min`; chama refresh; se falha, redireciona para `/login`; verificar build.
- [ ] 2.7 Criar rota de logout: `POST /api/v1/auth/logout`, limpa token de context + `sessionStorage`, redireciona para `/login`; verificar build.

## 3. Perfil

- [ ] 3.1 Criar `front/src/features/account/ProfilePage.tsx`: `useQuery` para `GET /users/me`; mostra nome, email, CPF, telefone, foto, endereço count, datas; botão "Editar"; verificar build.
- [ ] 3.2 Criar `front/src/features/account/EditProfilePage.tsx`: formulário para `PATCH /users/me` com campos `name`, `cpf`, `phone`, `photoUrl`; validação local (CPF 11 dígitos, telefone E.164); trata erro `409 DUPLICATE_CPF`; após sucesso, volta para ProfilePage; verificar build.
- [ ] 3.3 Criar `front/src/features/account/components/ProfileForm.tsx`: componente reutilizável (view + edit mode); validação de CPF com função utilitária; verificar build.
- [ ] 3.4 Criar `front/src/features/account/hooks/useCPFValidation.ts`: função que valida CPF (11 dígitos, dígitos verificadores com módulo 11); verificar build.

## 4. Endereços

- [ ] 4.1 Criar `front/src/features/account/AddressesPage.tsx`: lista paginada de endereços (`GET /users/me/addresses?page=0&size=20`); botão "Novo Endereço"; botão de editar/deletar por address; verificar build.
- [ ] 4.2 Criar `front/src/features/account/AddressFormPage.tsx`: formulário compartilhado para criar (`POST`) e editar (`PUT/PATCH`); modo "novo" vazio; modo "editar" prefill com `GET /users/me/addresses/{id}` + PUT/PATCH; verificar build.
- [ ] 4.3 Criar `front/src/features/account/components/AddressForm.tsx`: campos `name`, `zipcode` (8 dígitos sem máscara), `country` (default BR), `state` (2 letras), `city`, `street`, `number`; validação local; verificar build.
- [ ] 4.4 Criar `front/src/features/account/hooks/useAddresses.ts`: `useQuery` para lista paginada, `useMutation` para POST, PUT, PATCH, DELETE; trata `409` (endereço em shipment ativo); verificar build.
- [ ] 4.5 Criar `front/src/features/account/components/AddressList.tsx`: card por endereço com botões de editar/deletar; confirma deletar com diálogo; verificar build.

## 5. Exclusão de conta

- [ ] 5.1 Criar `front/src/features/account/components/DeleteAccountDialog.tsx`: modal com aviso em vermelho ("Esta ação é irreversível"); campo de confirmação textual ("Sim, remova minha conta"); botão de deletar (`DELETE /users/me`); trata `409 STORE_OWNER_ACCOUNT` (aviso: é a conta da loja), `409` genérico (pedido aberto), `404` (já deletada); após sucesso, logout e redireciona para `/login` com mensagem de sucesso; verificar build.
- [ ] 5.2 Integrar em AccountLayout: link "Deletar Conta" que abre o dialog; verificar build.

## 6. Roteamento e layout

- [ ] 6.1 Criar `front/src/features/account/AccountLayout.tsx`: layout da área de conta com sidebar/nav links (Perfil, Endereços, Deletar Conta); renderiza `<Outlet>` para sub-rotas; verificar build.
- [ ] 6.2 Registrar rotas no React Router:
  - `GET /login` → `<LoginPage>`
  - `GET /login/callback` → `<LoginCallbackPage>`
  - `GET /account` → `<ProtectedRoute>` → `<AccountLayout>` → `<ProfilePage>`
  - `PATCH /account/profile/edit` → `<ProtectedRoute>` → `<AccountLayout>` → `<EditProfilePage>`
  - `GET /account/addresses` → `<ProtectedRoute>` → `<AccountLayout>` → `<AddressesPage>`
  - `POST /account/addresses/new` → `<ProtectedRoute>` → `<AccountLayout>` → `<AddressFormPage>`
  - `PUT /account/addresses/:id/edit` → `<ProtectedRoute>` → `<AccountLayout>` → `<AddressFormPage>`
  - Verificar build.
- [ ] 6.3 Integrar AuthProvider no root do app (antes de `<Router>`); verificar build.

## 7. Estilo e UX

- [ ] 7.1 Componentes com Tailwind CSS: azul principal `#1d4ed8` (hover `#1e40af`), destaque `#eff6ff`, texto `#0f172a` (escuro) / `#475569` (médio), borda `#e2e8f0`, font Inter; cards com `rounded-[12px]`, inputs com `rounded-[8px]`; container `max-w-6xl`; verificar build.
- [ ] 7.2 Toasts/alerts para erros de rede (`INVALID_CPF`, `DUPLICATE_CPF`, `STORE_OWNER_ACCOUNT`); verificar build.
- [ ] 7.3 Loader/skeleton enquanto `useQuery` está carregando; verificar build.

## 8. Verificação

- [ ] 8.1 Rodar `npm run build` em `front/` sem erro.
- [ ] 8.2 Verificar rotas protegidas redirecionam para `/login` sem token.
- [ ] 8.3 Verificar login captura token no fragmento e limpa URL.
- [ ] 8.4 Verificar validação de CPF (rejeita dígitos verificadores inválidos).
- [ ] 8.5 Verificar CRUD de endereço (criar, listar, editar, deletar).
- [ ] 8.6 Verificar renovação automática de token (a cada 30 s, verifica expiração).
- [ ] 8.7 Validar com `cd "C:\Users\tiago\OneDrive\Área de Trabalho\ecommerce-event-driven-spring" && npx openspec validate add-front-account --strict`.
