# Event-Driven E-commerce Store

[![CI](https://github.com/TiagoAReiz/ecommerce-event-driven-spring/actions/workflows/ci.yml/badge.svg)](https://github.com/TiagoAReiz/ecommerce-event-driven-spring/actions/workflows/ci.yml)

A microservices-based online store with event-driven architecture, leveraging Kafka for asynchronous communication and Debezium for transactional outbox pattern to guarantee at-least-once event delivery.

---

## Visão Geral

Loja virtual de varejo com microsserviços Spring Boot 4.1.1 / Java 25, orientada a eventos com Apache Kafka. Cada serviço gerencia seu próprio banco de dados PostgreSQL 17 e comunica-se via eventos através de um pipeline outbox transacional integrado com Debezium. A loja oferece apenas um proprietário e catálogo único (não marketplace), com orquestração de pedidos via saga distribuída que envolve reserva de estoque, processamento de pagamentos, gerenciamento de envios e controle de reviews.

---

## Arquitetura

```mermaid
graph TB
    subgraph "External"
        MP["Mercado Pago"]
        BRAPI["BrasilAPI"]
        GOOGLE["Google OAuth"]
    end
    
    subgraph "Docker Network"
        GW["API Gateway<br/>:8080"]
        
        subgraph "Services"
            USER["User Service<br/>:8081"]
            INV["Inventory Service<br/>:8082"]
            ORDER["Order Service<br/>:8083"]
            PAYMENT["Payment Service<br/>:8084"]
            SHIPMENT["Shipment Service<br/>:8085"]
        end
        
        subgraph "Infrastructure"
            DB["PostgreSQL 17<br/>5 databases"]
            KAFKA["Kafka Broker<br/>KRaft Mode"]
            CONNECT["Kafka Connect<br/>+ Debezium"]
            REDIS["Redis Cache"]
        end
    end
    
    CLIENT["Client<br/>Browser"]
    
    CLIENT -->|HTTP| GW
    GW -->|HTTP| USER
    GW -->|HTTP| INV
    GW -->|HTTP| ORDER
    GW -->|HTTP| PAYMENT
    GW -->|HTTP| SHIPMENT
    
    USER -->|HTTP| INV
    ORDER -->|HTTP| INV
    ORDER -->|HTTP| SHIPMENT
    ORDER -->|HTTP| USER
    PAYMENT -->|HTTP| ORDER
    SHIPMENT -->|HTTP| USER
    
    USER -->|Outbox| CONNECT
    INV -->|Outbox| CONNECT
    ORDER -->|Outbox| CONNECT
    PAYMENT -->|Outbox| CONNECT
    SHIPMENT -->|Outbox| CONNECT
    
    CONNECT -->|Events| KAFKA
    KAFKA -->|Consume| INV
    KAFKA -->|Consume| ORDER
    KAFKA -->|Consume| PAYMENT
    KAFKA -->|Consume| SHIPMENT
    
    USER -->|Read/Write| DB
    INV -->|Read/Write| DB
    ORDER -->|Read/Write| DB
    PAYMENT -->|Read/Write| DB
    SHIPMENT -->|Read/Write| DB
    
    USER -->|Cache| REDIS
    INV -->|Cache| REDIS
    ORDER -->|Cache| REDIS
    PAYMENT -->|Cache| REDIS
    SHIPMENT -->|Cache| REDIS
    
    PAYMENT -->|API| MP
    GW -->|Webhook| PAYMENT
    
    ORDER -->|Geocoding| BRAPI
    GW -->|OAuth| GOOGLE
```

---

## Serviços e Responsabilidades

| Serviço | Porta | Banco | Responsabilidades |
|---------|-------|-------|-------------------|
| **API Gateway** | 8080 | user_db | Autenticacao OAuth2, roteamento de requisicoes, validacao de tokens |
| **User** | 8081 | user_db | Gestao de usuarios, enderecos, autenticacao de servicos internos |
| **Inventory** | 8082 | inventory_db | Catalogo de produtos, reserva e controle de estoque, avaliacoes |
| **Order** | 8083 | order_db | Orquestracao de sagas, gerenciamento de pedidos, carrinho |
| **Payment** | 8084 | payment_db | Integracao Mercado Pago, processamento de pagamentos, estornos |
| **Shipment** | 8085 | shipment_db | Gestao de envios, rastreamento, entrega |
| **Front** | 3000 | — | Vitrine, conta, carrinho, pedidos e area da loja (Next.js) |
| **MinIO** | 9000 / 9001 | — | Fotos de produto (S3). 9000 e a API, 9001 o console |

---

## Saga de Checkout — Caminho Feliz

```mermaid
sequenceDiagram
    participant Client
    participant Gateway
    participant Order
    participant Inventory
    participant Payment
    participant Kafka
    participant Shipment

    Client->>Gateway: POST /orders
    Gateway->>Order: Criar pedido (pending)
    Order->>Kafka: order.created
    Kafka->>Inventory: Consome order.created
    Inventory->>Inventory: Reserva estoque por 30 min
    Inventory->>Kafka: stock.reserved
    Kafka->>Order: Consome stock.reserved

    Client->>Payment: POST /payments
    Payment->>Payment: Integra com Mercado Pago
    Payment->>Kafka: payment.approved
    Kafka->>Order: Consome payment.approved
    Order->>Order: pending to paid
    Order->>Kafka: order.paid
    Kafka->>Inventory: Consome order.paid
    Inventory->>Inventory: held to confirmed, stock -= qty
    Inventory->>Kafka: stock.committed
    Kafka->>Order: Consome stock.committed
    Order->>Order: paid to processing
    Order->>Kafka: order.confirmed
    Kafka->>Shipment: Consome order.confirmed
    Shipment->>Shipment: Cria envio (pending)
    Shipment->>Kafka: shipment.status.changed
    Kafka->>Order: Consome shipment.status.changed
```

---

## Decisoes de Arquitetura

### Loja Unica
Nao ha suporte para marketplace. Um unico proprietario controla catalogo, precos e envios. Categorias sao definidas apenas por migracao (API de leitura).

### Outbox Transacional + Debezium (ADR-001)
Todos os eventos de dominio sao gravados na tabela outbox da mesma transacao que muda o estado. O Debezium le o Write-Ahead Log (WAL) em modo replicacao logica (wal_level=logical) e publica no Kafka, garantindo entrega at-least-once na ordem de commit. Nenhum servico publica com KafkaTemplate: toda publicacao passa pela outbox.

### Orquestracao Centralizada no Order
O servico order e o orquestrador unico da saga. Todo evento de outro servico termina no order, que decide o passo seguinte. Isso centraliza o estado da maquina de pedido e simplifica depuracao.

### Token JWT Interno
O gateway emite dois tipos de JWT:
- Token frontend (aud=front): usado pelo browser, trocado a cada requisicao
- Token interno (aud=internal): repassado aos microsservicos, carrega escopos baseados no papel (customer, owner, svc)

Servicos internos se autenticam com clientId + clientSecret, obtendo tokens de servico via POST /auth/service-token no gateway.

### Retry Bloqueante + Dead Letter Topic
Kafka Consumer com DefaultErrorHandler: retry exponencial (1s, x2, max 10s, total 60s) para erros transientes. Erros nao-retentaveis (desserializacao, integridade) vao direto para <topico>-dlt na mesma particao.

### Redis como Cache Complementar
Cache de leitura para rotas de catalogo e analise de frete. Falha de Redis nao derruba nenhuma rota: loga WARN e segue para o banco.

---

## Stack Tecnologico

- Runtime: Java 25, Spring Boot 4.1.1
- Persistencia: PostgreSQL 17, JPA/Hibernate 7
- Mensageria: Apache Kafka (KRaft), Kafka Connect, Debezium 3.x
- Cache: Redis 7.4-alpine
- Build: Maven
- Migrations: Flyway
- Serializacao: Jackson 3.x (ObjectMapper)
- Autenticacao: Spring Security 7, OAuth2, JWT (RS256)
- Validacao: Jakarta Bean Validation
- Stack de entrada: REST (RestClient), nao WebClient

**Front** (`front/`)

- Next.js 16 (App Router) + React 19 + TypeScript
- TanStack Query
- Tailwind CSS 4, identidade branco e azul com tokens em `src/app/globals.css`
- Vitrine publica renderizada no servidor; o que depende de sessao roda no navegador
- No compose, servidor Next em modo standalone na porta 3000

---

## Como Rodar

### Pre-requisitos

Docker e Docker Compose. Mais nada.

### 1. Subir tudo

```bash
docker compose up -d --build
```

Sobe os seis servicos, Kafka, Postgres, Redis, Debezium e a vitrine. **Nao precisa de preparo
nenhum**: sem `.env`, sem gerar chave, sem criar banco. Se as chaves de assinatura do gateway
nao existirem, o container gera um par descartavel no primeiro boot e avisa no log.

- Loja: http://localhost:3000
- Gateway: http://localhost:8080
- Console do MinIO: http://localhost:9001 (usuario e senha de `S3_ACCESS_KEY`/`S3_SECRET_KEY`)
- Conectores Debezium: chegam a RUNNING sozinhos (o `connect-init` insiste ate la)

### 2. Segredos (opcionais, cada um libera uma coisa)

| Variavel | Onde | Libera |
|---|---|---|
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | `micro-services/api-gateway/.env` ou ambiente | o login. Sem eles a stack sobe com um marcador e o Google recusa a autorizacao |
| `STORE_OWNER_EMAIL` | `.env` na raiz ou ambiente | define quem e o dono da loja (default: `dono@loja.local`) |
| `MP_ACCESS_TOKEN`, `MP_PUBLIC_KEY` | `.env` na raiz ou ambiente | pagamento de verdade. Sem eles o `payment` roda em modo `fake`, com o fluxo inteiro funcionando |

No Google Cloud, o redirect autorizado e `http://localhost:8080/login/oauth2/code/google` — do
**gateway**, nao do front.

Os arquivos `.env` sao opcionais: `.env.example` na raiz e em `api-gateway/` mostram o formato.

### 3. Parar

```bash
docker compose down        # mantem os dados
docker compose down -v     # apaga os volumes tambem
```

### 4. Validar a Subida

```bash
# Verificar que gateway responde
curl -X GET http://localhost:8080/health

# Verificar que broker esta pronto
docker compose exec broker /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server broker:9092

# Verificar que conectores estao RUNNING
curl -s http://localhost:8083/connectors | jq .
```

### 5. Percorrer a Saga Completa

O login e feito pelo Google: nao existe senha no sistema. Abra
`http://localhost:8080/oauth2/authorization/google` no navegador; ao voltar, o token
chega no fragmento da URL (`#token=...`). Todo o resto passa pelo gateway em `/api/v1`.

```bash
TOKEN="<token do fragmento>"

# Catalogo (publico na borda)
curl -s "http://localhost:8080/api/v1/categories?includeEmpty=true"
curl -s http://localhost:8080/api/v1/products

# Carrinho
curl -s -X POST http://localhost:8080/api/v1/cart/items   -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"   -d '{"idProduct":1,"quantity":1}'

# Checkout: o valor e o frete sao calculados no servidor, nunca aceitos do cliente
curl -s -X POST http://localhost:8080/api/v1/orders   -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"   -H "Idempotency-Key: $(uuidgen)"   -d '{"addressId":1}'

# Pagamento PIX (sem MP_ACCESS_TOKEN o servico responde em modo fake)
curl -s -X POST http://localhost:8080/api/v1/payments   -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"   -H "Idempotency-Key: $(uuidgen)"   -d '{"idOrder":1,"method":"pix",
       "payer":{"email":"cliente@exemplo.dev",
                "identification":{"type":"CPF","number":"12345678909"}}}'

# A partir daqui a saga corre sozinha:
# payment.approved -> pedido paid -> stock.committed -> order.confirmed -> envio criado
curl -s http://localhost:8080/api/v1/orders/1 -H "Authorization: Bearer $TOKEN"

# A loja despacha; o comprador confirma a entrega
curl -s -X PATCH http://localhost:8080/api/v1/shipments/1   -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"   -d '{"status":"in_transit","trackingCode":"BR123456789XY"}'
curl -s -X POST http://localhost:8080/api/v1/shipments/1/confirm-delivery   -H "Authorization: Bearer $TOKEN"

# Entrega confirmada da o direito de avaliar
curl -s http://localhost:8080/api/v1/reviews/pending -H "Authorization: Bearer $TOKEN"
curl -s -X POST http://localhost:8080/api/v1/products/1/reviews   -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"   -d '{"idOrder":1,"rate":5,"title":"Muito bom","description":"Chegou rapido."}'
```

#### 5.1 Observar os eventos no Kafka

```bash
docker compose exec broker /opt/kafka/bin/kafka-console-consumer.sh   --bootstrap-server broker:9092   --topic ecommerce.order.created.v1   --from-beginning --property print.key=true --property print.headers=true
```

A key e o `orderId` e o header `__TypeId__` traz o alias do evento (`orderCreated`): os dois
sao postos pelo Outbox Event Router do Debezium a partir das colunas da tabela `outbox`.

---

## Referencias

- Contratos de Eventos (docs/event-contracts.md) - Especificacao de todos os eventos Kafka, topicos, keys, payloads, efeitos e sagas
- Contratos de API (docs/api-contracts.md) - Especificacao de todas as rotas HTTP, corpos, parametros, codigos de resposta
- ADR-001: Outbox Transacional com Debezium (docs/outbox-debezium.md) - Decisao de arquitetura, implementacao e operacao do pipeline de publicacao
- Configuracao Kafka (docs/kafka.md) - Serializers, deserializers, consumidor, produtor, troubleshooting
- Decisoes do Projeto (docs/decisions.md) - Decisoes de produto, arquitetura, processo e as correcoes encontradas na verificacao ponta a ponta
- OpenSpec Changes (openspec/) - Uma proposta por servico/fase, com specs em delta, design e tarefas

---

## Integracao Continua

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) roda um job por microsservico a cada
push e pull request, com `fail-fast: false` — o servico que quebrou aparece sozinho.

Cada job sobe um **Postgres de verdade**: os testes carregam o contexto Spring, que abre o pool
e roda o Flyway, e o schema usa `ENUM` e `jsonb` do Postgres, entao banco em memoria nao serve.
Kafka fica de fora de proposito — o listener nao bloqueia o boot, entra em retry e o contexto
sobe igual, de modo que subir um broker seria custo sem cobertura. O gateway gera um par de
chaves descartavel no job, porque as chaves de assinatura nao sao versionadas.

---

## Estado do Projeto

Backend completo e verificado com a stack de pe, nao apenas compilando.

| Area | Estado |
|---|---|
| Borda: OAuth2 Google, emissao de token, proxy, escopos por papel | Pronto |
| `user`: perfil, enderecos, provisionamento do dono da loja, exclusao de conta (LGPD) | Pronto |
| `inventory`: catalogo, estoque, reserva, baixa, devolucao, avaliacoes | Pronto |
| `order`: carrinho, checkout idempotente, consultas, cancelamento, orquestracao da saga | Pronto |
| `payment`: PIX, cartao tokenizado, Checkout Pro, webhook, estorno | Pronto (Mercado Pago em modo `fake` sem `MP_ACCESS_TOKEN`) |
| `shipment`: cotacao de frete, envio, transicoes, confirmacao de entrega | Pronto |
| Outbox transacional + Debezium nos cinco bancos | Pronto |
| 80 rotas HTTP e 15 eventos Kafka documentados e implementados | Pronto |
| Front: vitrine, conta, carrinho, pagamento, pedidos, avaliações e área da loja | Pronto |

### O que foi percorrido ponta a ponta

Com os dez containers de pe e os cinco conectores Debezium em `RUNNING`:

- **Compra completa**: carrinho -> checkout (frete calculado por CEP real) -> `order.created` ->
  reserva de estoque -> pagamento -> `payment.approved` -> pedido `paid` -> baixa de estoque ->
  `order.confirmed` -> envio criado -> despacho -> pedido `shipped` com codigo de rastreio ->
  confirmacao de entrega -> pedido `delivered` -> elegibilidade -> avaliacao com recalculo do
  rating do produto.
- **Compensacao**: cancelamento de pedido pago -> `order.refund.requested` -> estorno no
  provedor -> `payment.refunded` -> pedido `refunded`, envio `cancelled`, estoque devolvido.
- **Exclusao de conta (LGPD)**: `DELETE /users/me` -> `user.deleted` -> avaliacao anonimizada no
  `inventory` ("Usuario removido", sem foto, texto e nota preservados) e carrinho com soft
  delete no `order`. A conta da loja responde `409`: nao se apaga pela API.
- **Subida do zero**: stack derrubada com volumes e reconstruida, migrations aplicadas,
  conectores registrados e rotas publicas respondendo.

As falhas encontradas nessa verificacao, e o que ficou decidido em cada uma, estao em
[`docs/decisions.md`](docs/decisions.md).

### Limites conhecidos

- O Mercado Pago roda em modo `fake` enquanto nao houver `MP_ACCESS_TOKEN`; o contrato das rotas
  e o mapeamento de status sao os mesmos nos dois modos.
- Os testes automatizados cobrem a carga do contexto de cada servico. A verificacao funcional
  descrita acima foi feita manualmente contra a stack.
- `ETag`/`304` e moderacao de texto de avaliacao ficaram fora do escopo; nenhum fluxo depende
  deles.

---

Privado. Projeto de portfolio.
