# Spec Delta

## Purpose

O gateway SHALL emitir tokens de serviço por credencial de cliente (clientId e clientSecret) usados em todas as comunicações servidor-a-servidor entre os microsserviços.

## ADDED Requirements

### Requirement: POST /auth/service-token valida credencial e emite token interno
O gateway SHALL fornecer um endpoint `POST /auth/service-token` que aceite corpo `{clientId, clientSecret}`, valide o segredo contra `app.service-clients.<clientId>` em tempo constante (usando `MessageDigest.isEqual`), e emita um token interno com `sub=svc:<clientId>`, escopo `internal:hydrate`, TTL de 5 minutos (300 segundos) e `aud=internal`. Cliente desconhecido ou segredo errado SHALL resultar em `401` com a mesma mensagem para não enumerar clientes.

#### Scenario: Credencial válida retorna token
- **WHEN** uma requisição POST chega em `/auth/service-token` com `{clientId:"order", clientSecret:"<válido>"}`
- **THEN** o gateway responde `200` com `{accessToken, tokenType:"Bearer", expiresIn:300}`

#### Scenario: Token de serviço contém claims corretos
- **WHEN** um token é emitido para `clientId="order"`
- **THEN** o token contém `sub="svc:order"`, `scope="internal:hydrate"`, `aud=["internal"]`

#### Scenario: Cliente desconhecido retorna 401
- **WHEN** uma requisição chega com `clientId` não reconhecido
- **THEN** o gateway responde `401` com mensagem genérica (não confirma existência)

#### Scenario: Segredo errado retorna 401
- **WHEN** uma requisição chega com `clientId` válido mas `clientSecret` incorreto
- **THEN** o gateway responde `401` com mensagem genérica

#### Scenario: Corpo ausente retorna 400
- **WHEN** uma requisição chega com corpo vazio ou sem `clientId` / `clientSecret`
- **THEN** o gateway responde `400`

#### Scenario: Comparação de segredo usa tempo constante
- **WHEN** o segredo incorreto é comparado com o correto
- **THEN** a comparação leva tempo constante para evitar ataques de timing

### Requirement: Token de serviço é usado em comunicação servidor-a-servidor
O gateway e todos os microsserviços SHALL usar o token de serviço emitido neste endpoint em toda chamada downstream (serviço para serviço). O token SHALL ser obtido uma única vez e cacheado até 30 segundos antes da expiração.

#### Scenario: Serviço obtém token e cacheia por 30s
- **WHEN** um serviço precisa chamar outro serviço
- **THEN** o serviço obtém um token via `POST /auth/service-token`, cacheia e reutiliza até próxima expiração

#### Scenario: Token expirado causa nova requisição
- **WHEN** um token cacheado atinge 30s antes da expiração
- **THEN** o serviço requisita um novo token

### Requirement: POST /auth/service-token é acessível sem autenticação
O endpoint `POST /auth/service-token` SHALL ser `permitAll` no gateway — não exigir token prévio, pois a autenticação é por credencial de cliente.

#### Scenario: Requisição sem Authorization header é aceita
- **WHEN** uma requisição chega em `POST /auth/service-token` sem header Authorization
- **THEN** o gateway processa normalmente baseado em clientId/clientSecret
