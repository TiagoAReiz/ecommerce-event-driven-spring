# Spec Delta: Front-end — Login Google

## Purpose

Implementa o fluxo completo de autenticação via Google no front-end: redirecionamento para o consent screen, captura do token no fragmento da URL (seguro contra Referer leak), limpeza da URL e validação da sessão antes de permitir acesso à app.

## ADDED Requirements

### Requirement: Botão de login redirecionando para Google

O front-end SHALL render um botão "Entrar com Google" na página de login que redireciona para o servidor de autorização do Google.

#### Scenario: Página de login rendida
- **WHEN** o usuário acessa `/login` sem token
- **THEN** a página mostra título "Bem-vindo", descrição da loja e botão "Entrar com Google"

#### Scenario: Redirecionamento para Google
- **WHEN** o usuário clica em "Entrar com Google"
- **THEN** o browser redireciona para `http://localhost:8080/oauth2/authorization/google`

### Requirement: Captura segura de token no fragmento

O front-end SHALL capturar o token JWT do fragmento da URL (não da query string), validá-lo e remover o fragmento do histórico do browser imediatamente.

#### Scenario: Token capturado do fragmento
- **WHEN** o Google callback redireciona para `http://localhost:3000/login/callback#token=eyJraWQ...`
- **THEN** o front JS lê `window.location.hash`, extrai o token, valida o formato (`Bearer` JWT), e armazena em memória (React context)

#### Scenario: URL limpa do histórico
- **WHEN** o token é extraído com sucesso
- **THEN** a página executa `window.history.replaceState(null, '', '/login/callback')` para remover o fragmento do histórico; o botão voltar não reexibirá a URL com token

#### Scenario: Falha ao extrair token
- **WHEN** o fragmento está vazio, malformado ou contém `error=access_denied` (usuário recusou consentimento)
- **THEN** a página mostra mensagem de erro amigável: "Login cancelado. Tente novamente." ou "Erro ao fazer login. Recarregue a página."

#### Scenario: Validação de sessão após token capturado
- **WHEN** o token é armazenado com sucesso
- **THEN** a página chama `GET /api/v1/auth/session` com `Authorization: Bearer {token}` para validar a sessão

#### Scenario: Sessão válida
- **WHEN** `GET /api/v1/auth/session` retorna `200` com `{ id, name, roles, expiresAt, ... }`
- **THEN** o front armazena `{ token, roles, expiresAt }` em context + `sessionStorage`, limpa o URL fragment e redireciona para `/account` (ou para `?redirect=...` se houver parâmetro)

#### Scenario: Sessão inválida (401)
- **WHEN** `GET /api/v1/auth/session` retorna `401` (token rejeitado)
- **THEN** a página mostra "Sessão expirada" e oferece link "Tente novamente" que leva a `/login`

#### Scenario: Erro de rede no bootstrap
- **WHEN** `GET /api/v1/auth/session` falha com timeout ou `5xx`
- **THEN** a página mostra "Conectando..." e retenta com backoff exponencial (100 ms, 200 ms, 400 ms, depois cada 5 s); após 3 falhas rápidas, mostra "Sem conexão — verifique sua internet"

### Requirement: Deep linking após login

O front-end SHALL redirecionar o usuário para a página original que ele tentou acessar antes do login.

#### Scenario: Rota protegida com redirect
- **WHEN** usuário acessa `/account/addresses` sem token
- **THEN** redireciona para `/login?redirect=%2Faccount%2Faddresses` (URL encoded)

#### Scenario: Redirecionamento após login bem-sucedido
- **WHEN** após validar a sessão, a app detecta parâmetro `redirect` no query
- **THEN** executa `navigate(decodeURIComponent(redirect))` em vez de navegar para `/account`

#### Scenario: Sem parâmetro redirect
- **WHEN** login bem-sucedido e sem `?redirect=...`
- **THEN** redireciona para `/account` (home da sessão)

### Requirement: Token armazenado em sessionStorage, nunca em localStorage

O front-end SHALL armazenar o token de forma que sobreviva a reload da página (dentro da mesma aba), mas seja removido ao fechar a aba.

#### Scenario: Reload da página mantém sessão
- **WHEN** usuário faz login e recebe token, depois faz reload (F5)
- **THEN** o token é lido de `sessionStorage` durante o boot; `GET /api/v1/auth/session` valida; a página não mostra tela de login

#### Scenario: Fechar aba remove token
- **WHEN** usuário fecha a aba/janela
- **THEN** `sessionStorage` é limpo automaticamente pelo browser; próximo login exige autenticação novamente

#### Scenario: Proteção contra XSS
- **WHEN** um ataque XSS tenta ler `window.localStorage['auth:token']`
- **THEN** a chave não existe (token nunca foi armazenado lá); o script malicioso não consegue roubar a sessão persistentemente

### Requirement: Renovação deslizante de token

O front-end SHALL renovar o token automaticamente antes da expiração, sem intervenção do usuário.

#### Scenario: Renovação automática a 5 min de expirar
- **WHEN** a app estiver aberta e faltarem 5 minutos para o `expiresAt` do token
- **THEN** a app chama `POST /api/v1/auth/refresh` e recebe novo token com novo `expiresAt`

#### Scenario: Novo token armazenado
- **WHEN** a renovação retorna `200` com `{ accessToken, tokenType, expiresIn, expiresAt }`
- **THEN** o novo token sobrescreve o antigo em context + `sessionStorage`; timer de renovação é resetado

#### Scenario: Falha na renovação — retry exponencial
- **WHEN** `POST /api/v1/auth/refresh` retorna `5xx` ou timeout
- **THEN** a app retenta a cada 1 s, até 3 vezes; se todas falham, mostra aviso "Sincronizando sessão..." e continua retentando a cada 5 s

#### Scenario: Falha na renovação — token inválido (401)
- **WHEN** `POST /api/v1/auth/refresh` retorna `401` (token expirou antes de renovar)
- **THEN** a app redireciona para `/login` de forma síncrona; limpa token de context + `sessionStorage`

#### Scenario: Teto de 7 dias na renovação
- **WHEN** o gateway retorna `401 SESSION_EXPIRED` (passou 7 dias desde `auth_time`)
- **THEN** a app redireciona para `/login` com mensagem "Sua sessão expirou. Faça login novamente."

### Requirement: Logout revoga sessão

O front-end SHALL chamar o backend para revogar o token e limpar a sessão local.

#### Scenario: Clique em Sair
- **WHEN** usuário clica em botão/link "Sair"
- **THEN** a app chama `POST /api/v1/auth/logout` com `Authorization: Bearer {token}`

#### Scenario: Logout bem-sucedido (204)
- **WHEN** `POST /api/v1/auth/logout` retorna `204`
- **THEN** token é removido de context + `sessionStorage`; redireciona para `/login` com mensagem "Saída realizada com sucesso"

#### Scenario: Erro ao fazer logout
- **WHEN** `POST /api/v1/auth/logout` retorna `5xx` ou timeout
- **THEN** mostra "Erro ao fazer logout" mas limpa o token local de qualquer forma (fallback seguro); redireciona para `/login`

### Requirement: Discriminação de papel — owner libera acesso à loja

O front-end SHALL permitir acesso à área de gestão (`/store`) apenas se o papel `owner` estiver no token.

#### Scenario: Usuário owner vê link "Loja"
- **WHEN** usuário está autenticado e tem `roles: ["customer", "owner"]`
- **THEN** o layout de navegação mostra link/menu "Loja" que leva a `/store`

#### Scenario: Usuário customer não vê link "Loja"
- **WHEN** usuário está autenticado e tem `roles: ["customer"]` apenas
- **THEN** o layout **não mostra** link "Loja"

#### Scenario: Tentativa de acessar /store sem owner
- **WHEN** usuário customer tenta navegar diretamente para `/store` (por URL)
- **THEN** a app redireciona para `/account` com mensagem "Você não tem permissão para acessar a área de gestão"

#### Scenario: Owner acessa /store
- **WHEN** usuário owner navega para `/store`
- **THEN** renderiza o layout de gestão da loja (produtos, pedidos, envios) normalmente
