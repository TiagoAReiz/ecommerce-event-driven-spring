# Spec Delta

## Purpose

Gerencia a sessão do usuário no gateway por meio de login com Google, fornecimento de sessão atual, renovação deslizante de tokens com teto de 7 dias e logout com denylist distribuída.

## ADDED Requirements

### Requirement: Login com Google devolve token em fragmento
O gateway SHALL redirecionar (302) o callback do Google para `{app.front-url}/callback#token=<JWT>`, onde o token SHALL conter claims `roles` (resolvido a partir do cache de perfil) e `auth_time` (instante do login). O fragmento (`#`) SHALL ser usado em vez de query string para evitar que o token seja enviado em headers ou logs.

#### Scenario: Login bem-sucedido redireciona com token em fragmento
- **WHEN** o callback do Google é processado com sucesso
- **THEN** o gateway redireciona `302` para `{front}/callback#token=eyJrb...`

#### Scenario: Token contém roles e auth_time
- **WHEN** o login é bem-sucedido e o token é decodificado
- **THEN** o token contém `roles` (ex: `["customer"]`) e `auth_time` com o timestamp atual

#### Scenario: Usuário nega consentimento no Google
- **WHEN** o usuário rejeita o consentimento
- **THEN** o gateway redireciona `302` para `{front}/login?error=access_denied`

#### Scenario: Código do Google ausente retorna erro
- **WHEN** o callback chega sem parâmetro `code`
- **THEN** o gateway responde `400`

### Requirement: GET /api/v1/auth/session retorna dados da sessão atual
O gateway SHALL fornecer um endpoint `GET /api/v1/auth/session` que retorne o perfil do usuário (id, name, email, photoUrl, roles) e a data de expiração do token, tudo lido do cache `auth:profile:{userId}` com fallback ao serviço `user`. A resposta SHALL conter `expiresAt` calculado a partir do claim `exp`.

#### Scenario: Sessão ativa retorna dados do usuário
- **WHEN** uma requisição autenticada chega em `GET /api/v1/auth/session`
- **THEN** o gateway responde `200` com `{id, name, email, photoUrl, roles, expiresAt}`

#### Scenario: Token ausente retorna 401
- **WHEN** uma requisição chega em `/api/v1/auth/session` sem Authorization header
- **THEN** o gateway responde `401`

#### Scenario: Token expirado retorna 401
- **WHEN** uma requisição chega com token já expirado
- **THEN** o gateway responde `401`

#### Scenario: Token na denylist é rejeitado
- **WHEN** o `jti` do token está em `auth:denylist:{jti}`
- **THEN** o gateway responde `401`

#### Scenario: Usuário deletado retorna 404
- **WHEN** o `sub` do token não existe mais em `users`
- **THEN** o gateway responde `404`

#### Scenario: Serviço user indisponível com cache quente retorna dados
- **WHEN** `user` está indisponível mas o cache tem dados recentes
- **THEN** o gateway responde `200` com dados do cache

#### Scenario: Serviço user indisponível com cache frio retorna 503
- **WHEN** `user` está indisponível e o cache não tem dados
- **THEN** o gateway responde `503`

### Requirement: POST /api/v1/auth/refresh renova token deslizante com teto de 7 dias
O gateway SHALL fornecer um endpoint `POST /api/v1/auth/refresh` que emita um novo token contendo a mesma claim `auth_time` do token anterior. Se a diferença entre `now` e `auth_time` exceder 7 dias, SHALL responder `401` com code `SESSION_EXPIRED`. O `jti` do token antigo SHALL ser adicionado a `auth:denylist:{jti}` até seu `exp` para garantir uso único.

#### Scenario: Renovação bem-sucedida retorna novo token
- **WHEN** uma requisição POST chega em `/api/v1/auth/refresh` com Bearer token válido (não expirado)
- **THEN** o gateway responde `200` com `{accessToken, tokenType:"Bearer", expiresIn:3600, expiresAt, sessionExpiresAt}`

#### Scenario: Novo token herda auth_time do antigo
- **WHEN** uma renovação é bem-sucedida
- **THEN** o novo token contém a mesma `auth_time` do token anterior

#### Scenario: Sessionexpirou após 7 dias retorna 401
- **WHEN** a diferença entre agora e `auth_time` é maior que 7 dias
- **THEN** o gateway responde `401` com code `SESSION_EXPIRED`

#### Scenario: Token expirado não renova
- **WHEN** uma requisição chega com token já fora de seu `exp`
- **THEN** o gateway responde `401`

#### Scenario: Mesmo token renovado duas vezes retorna 401
- **WHEN** um token renovado uma vez é usado novamente em refresh
- **THEN** o gateway responde `401` (o `jti` estava na denylist desde a primeira renovação)

#### Scenario: Redis indisponível causa falha de renovação
- **WHEN** Redis está down e uma requisição de refresh chega
- **THEN** o gateway responde `503` (não consegue manter denylist)

#### Scenario: Rate limit em renovação retorna 429
- **WHEN** o mesmo usuário faz mais de 10 renovações em um minuto
- **THEN** o gateway responde `429` com `Retry-After`

### Requirement: POST /api/v1/auth/logout invalida token com denylist
O gateway SHALL fornecer um endpoint `POST /api/v1/auth/logout` que adicione o `jti` do token bearer à denylist `auth:denylist:{jti}` com TTL até o `exp` do token. O endpoint SHALL responder `204` em sucesso e `401` em falha de autenticação.

#### Scenario: Logout bem-sucedido retorna 204
- **WHEN** uma requisição POST chega em `/api/v1/auth/logout` com Bearer token válido
- **THEN** o gateway responde `204` e o `jti` é adicionado à denylist

#### Scenario: Token invalidado é rejeitado em próximas requisições
- **WHEN** um token foi feito logout e é usado novamente
- **THEN** o gateway responde `401` (o `jti` está na denylist)

#### Scenario: Token ausente em logout retorna 401
- **WHEN** uma requisição chega em `/api/v1/auth/logout` sem Authorization header
- **THEN** o gateway responde `401`

#### Scenario: Token inválido em logout retorna 401
- **WHEN** uma requisição chega em `/api/v1/auth/logout` com token malformado
- **THEN** o gateway responde `401`

#### Scenario: Redis indisponível causa falha de logout
- **WHEN** Redis está down e uma requisição de logout chega
- **THEN** o gateway responde `503` (não consegue gravar na denylist)
