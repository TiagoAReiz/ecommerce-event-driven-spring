# Spec Delta

## Propósito

Calculadora de frete integrada ao frontend de catálogo. Cliente consulta custo de envio por CEP antes de fazer login ou compra, usando GET /shipping/quote público.

## ADDED Requirements

### Requirement: Widget de cálculo de frete na página de produto

O sistema SHALL prover um widget para consultar frete por CEP na página `/products/:id`.

#### Cenário: Widget de frete visível

- **QUANDO** usuário acessa `/products/118`
- **ENTÃO** seção "Frete para sua região" aparece com:
  - Input de CEP (máscara `#####-###`, placeholder "CEP de entrega")
  - Botão "Calcular" inicialmente desabilitado
  - Ícone de "i" com tooltip: "Frete calculado por distância até o CEP (em linha reta)"

#### Cenário: Input valida CEP antes de requisitar

- **QUANDO** usuário começa a digitar no input de CEP
- **ENTÃO** o button "Calcular" permanece desabilitado enquanto CEP < 8 dígitos
- **E** após digitar 8 dígitos (ex.: "01310100"), botão fica habilitado
- **E** pressionando Enter também chama a requisição (UX mobile)

#### Cenário: Consultar frete para um CEP

- **QUANDO** usuário digita CEP "01310100" e clica "Calcular"
- **ENTÃO** GET /shipping/quote?zipcode=01310100 é chamado
- **E** durante carregamento, spinner ou skeleton aparece no lugar do resultado
- **E** resposta `200` retorna `{zipcode: "01310100", origin: {city: "Guarulhos", state: "SP"}, distanceKm: 25, ratePerKm: "1.00", freightCost: "25.00"}`
- **E** resultado é exibido: "Frete para São Paulo, SP: **R$ 25,00** (25 km de distância)"

#### Cenário: Frete zero para o mesmo CEP da loja

- **QUANDO** usuário digita o CEP da loja (ex.: "07010000")
- **ENTÃO** GET /shipping/quote retorna `{distanceKm: 0, freightCost: "0.00"}`
- **E** mensagem exibe: "Envio grátis! Você está na região de origem."

#### Cenário: Cache de CEP durante a sessão

- **QUANDO** usuário consulta frete para "01310100" e recebe resultado
- **E** mais tarde consulta o mesmo CEP novamente
- **ENTÃO** resposta vem do cache local (TanStack Query, TTL 3600s = 1 hora)
- **E** **nenhuma** nova requisição é feita (query key = `['shipping-quote', '01310100']`)

#### Cenário: CEP inválido ou não geocodificado

- **QUANDO** GET /shipping/quote retorna `422 ZIPCODE_NOT_GEOCODED`
- **ENTÃO** mensagem de erro aparece em vermelho: "CEP não encontrado na base de dados. Verifique e tente novamente."
- **E** input mantém o valor digitado para correção
- **E** botão "Calcular" permanece habilitado

#### Cenário: CEP não existe no Brasil

- **QUANDO** GET /shipping/quote retorna `422 ZIPCODE_NOT_FOUND`
- **ENTÃO** mensagem: "Este CEP não existe. Verifique o número."
- **E** input ganha borda vermelha temporariamente (visual feedback)

#### Cenário: Serviço de CEP indisponível

- **QUANDO** GET /shipping/quote retorna `503 SERVICE_UNAVAILABLE` (BrasilAPI fora)
- **ENTÃO** mensagem: "Não conseguimos calcular o frete agora. Tente novamente em alguns instantes."
- **E** botão "Calcular" permanece habilitado para retry manual
- **E** após 5 segundos, um "Tentar novamente" automático é disparado (max 2 retries)

#### Cenário: Timeout na consulta de CEP

- **QUANDO** GET /shipping/quote não responde após 5 segundos (timeout no backend)
- **ENTÃO** o frontend captura como timeout (504 ou socket timeout)
- **E** mensagem: "Tempo esgotado. Verifique sua conexão de internet e tente novamente."
- **E** botão Retry fica habilitado

#### Cenário: Erro genérico do servidor

- **QUANDO** GET /shipping/quote retorna `500` com `{code: "INTERNAL_SERVER_ERROR"}`
- **ENTÃO** mensagem: "[E500] Erro interno. Código: {requestId}"
- **E** botão Retry oferece tentar novamente
- **E** link "Contatar suporte" com ID de requisição pré-preenchido

### Requirement: Calculadora de frete reutilizável no checkout (futuro)

O sistema SHALL prover `useShippingQuote()` hook para ser usado também na página de checkout (design pattern).

#### Cenário: Hook useShippingQuote para reutilização

- **QUANDO** um componente chama `const {quote, loading, error, refetch} = useShippingQuote(zipcode)`
- **ENTÃO** o hook:
  - Valida CEP localmente (8 dígitos)
  - Chama GET /shipping/quote com caching (TanStack Query)
  - Retorna estado `quote`, `loading`, `error`
  - Oferece função `refetch()` para retry manual
- **E** em caso de erro, `error.code` contém o código RFC 9457 (`ZIPCODE_NOT_GEOCODED`, etc.)

### Requirement: Tratamento de autenticação (público)

O sistema SHALL chamar GET /shipping/quote sem token de usuário (public endpoint).

#### Cenário: Requisição sem autenticação

- **QUANDO** usuário faz consulta de frete deslogado
- **ENTÃO** GET /shipping/quote é chamado **sem** header `Authorization`
- **E** o backend (gateway) emite token interno com `catalog:read` (conforme contrato)
- **E** a resposta é `200` normalmente

#### Cenário: Resultado é cacheado independentemente de login

- **QUANDO** usuário consulta CEP "01310100" deslogado
- **E** faz login e está novamente na página de produto
- **E** consulta o mesmo CEP "01310100"
- **ENTÃO** resultado vem do cache (não discrimina autenticação para frete)
- **E** nenhuma requisição nova é feita

