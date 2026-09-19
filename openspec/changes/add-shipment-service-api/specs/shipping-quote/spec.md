# Spec Delta

## Purpose

Permite que clientes consultem o custo de frete antes de finalizar a compra, baseado na distância em linha reta entre a loja e o CEP de destino.

## ADDED Requirements

### Requirement: Calcular frete por distância

O sistema SHALL fornecer uma API de consulta que calcula o frete como a distância em quilômetros (em linha reta, arredondada para cima) multiplicada pela tarifa por quilômetro configurada na loja.

A distância MUST ser calculada usando a fórmula de haversine entre as coordenadas da loja (origem) e as coordenadas do CEP de destino. O frete MUST ser `ceil(distância em km) × taxa por km`, resultando em `0.00` se o destino for o mesmo CEP da loja.

#### Scenario: Cliente consulta frete para um CEP válido com coordenada

- **WHEN** o cliente faz `GET /shipping/quote?zipcode=01310100` e o CEP tem coordenada na BrasilAPI
- **THEN** a resposta `200` retorna `{zipcode: "01310100", origin: {city, state}, distanceKm: 25, ratePerKm: "1.00", freightCost: "25.00"}` com `Cache-Control: public, max-age=3600`

#### Scenario: Cliente consulta frete e a resposta vem do cache de 30 dias

- **WHEN** o mesmo CEP já foi consultado nos últimos 30 dias
- **THEN** a coordenada vem de `geo:cep:{cep}` no Redis, sem chamar BrasilAPI

### Requirement: Fallback de geocodificação com Nominatim

O sistema SHALL tentar geocodificar um CEP primeiro na BrasilAPI; se o CEP não tiver coordenada mas existir (localidade e UF conhecidos), SHALL consultar o Nominatim (OpenStreetMap) passando `city={city}&state={state}&country=Brazil`.

#### Scenario: CEP sem coordenada na BrasilAPI mas localidade conhecida

- **WHEN** a BrasilAPI devolve um CEP válido mas sem `location.coordinates`, e a cidade/UF existem
- **THEN** o sistema consulta Nominatim com `GET https://nominatim.openstreetmap.org/search?city={city}&state={state}&country=Brazil&format=json&limit=1` (com header `User-Agent: ecommerce-event-driven/1.0`)
- **THEN** se Nominatim retorna coordenada, a resposta `200` é calculada normalmente

#### Scenario: CEP sem coordenada e localidade desconhecida

- **WHEN** o CEP não tem coordenada na BrasilAPI e a localidade também não é conhecida
- **THEN** a resposta é `422 ZIPCODE_NOT_GEOCODED` (nenhuma API conseguiu obter coordenada)

### Requirement: Códigos de erro específicos

O sistema SHALL devolver `400 BAD_REQUEST` quando o `zipcode` está ausente, não tem 8 dígitos ou não é numérico.

O sistema SHALL devolver `422 ZIPCODE_NOT_FOUND` quando a BrasilAPI retorna `404` ou o CEP não existe no Brasil.

O sistema SHALL devolver `503 SERVICE_UNAVAILABLE` quando a BrasilAPI está fora e o CEP não está no cache Redis.

O sistema SHALL devolver `504 GATEWAY_TIMEOUT` quando qualquer chamada externa (BrasilAPI ou Nominatim) ultrapassa 5 segundos.

#### Scenario: Requisição sem o parâmetro zipcode

- **WHEN** o cliente faz `GET /shipping/quote` (sem parâmetro)
- **THEN** a resposta é `400` com `code: "BAD_REQUEST"`

#### Scenario: CEP não existe no Brasil

- **WHEN** a BrasilAPI devolve `404` para um CEP (ex.: `99999999`)
- **THEN** a resposta é `422` com `code: "ZIPCODE_NOT_FOUND"`

#### Scenario: BrasilAPI indisponível e cache vazio

- **WHEN** a BrasilAPI está fora do ar e o CEP nunca foi consultado
- **THEN** a resposta é `503` (circuit breaker aberto ou indisponibilidade permanente)

#### Scenario: Timeout na chamada ao Nominatim

- **WHEN** a BrasilAPI não tem coordenada e a chamada ao Nominatim ultrapassa 5 segundos
- **THEN** a resposta é `504`
