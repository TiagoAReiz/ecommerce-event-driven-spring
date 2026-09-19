# Spec Delta

## Purpose

Ambiente local completo via Docker Compose que executa a loja em microsserviços com pipeline transacional outbox → Debezium → Kafka, permitindo testar sagas ponta a ponta sem etapas manuais.

## ADDED Requirements

### Requirement: Ambiente local sobe com um único comando

O ambiente local SHALL estar definido em um arquivo `docker-compose.yaml` na pasta `micro-services/` que permite iniciar todos os serviços, infraestrutura e dependências com um único comando `docker compose up --build`, deixando o sistema pronto para testes de saga ponta a ponta sem intervenção manual adicional.

#### Scenario: Subida completa do ambiente
- **WHEN** o usuário executa `docker compose up --build` na pasta `micro-services/`
- **THEN** todos os serviços (api-gateway, user, inventory, order, payment, shipment), infraestrutura (Postgres, Kafka, Redis) e componentes de integração (Kafka Connect com Debezium) iniciam com sucesso

#### Scenario: Serviço fica pronto para teste após subida
- **WHEN** o comando `docker compose up --build` termina sem erros
- **THEN** o gateway está acessível em `http://localhost:8080`, aceita requisições HTTP e retorna respostas válidas

### Requirement: Apenas o gateway expõe porta publicada

O ambiente SHALL expor apenas a porta do api-gateway (`8080`) como porta pública; todos os demais serviços (user, inventory, order, payment, shipment, Postgres, Kafka, Redis, Kafka Connect) permanecem isolados em rede interna do Docker, acessíveis apenas entre containers, e MUST estar configurados sem ports publicadas ou com ports acessíveis apenas de dentro da rede.

#### Scenario: Gateway é único ponto de entrada público
- **WHEN** a rede do Docker está ativa com o compose
- **THEN** apenas o gateway em `localhost:8080` está acessível do host; tentativas de conexão direta a `localhost:8081` (user), `localhost:8082` (inventory), etc., falham com erro de recusa de conexão

#### Scenario: Serviços internos se comunicam em rede isolada
- **WHEN** os serviços estão rodando
- **THEN** o gateway consegue chamar user em `http://user:8081`, order consegue chamar inventory em `http://inventory:8082` e assim por diante, sem publicar portas para o host

### Requirement: Postgres configurado com replicação lógica

O serviço Postgres SHALL estar configurado com `wal_level=logical` e limites adequados de slot de replicação (`max_slot_wal_keep_size`) para suportar Debezium, MUST estar rodando a versão 17, e MUST ter criado os bancos de dados separados para cada serviço (user_db, inventory_db, order_db, payment_db, shipment_db) com usuário debezium tendo permissão de replicação.

#### Scenario: Postgres inicia com configuração de replicação lógica
- **WHEN** o serviço `db` no compose inicia
- **THEN** o parâmetro `wal_level` está configurado como `logical` e a conexão do Debezium consegue criar um slot de replicação sem erros de permissão

#### Scenario: Cada serviço tem banco isolado
- **WHEN** o Postgres termina a inicialização
- **THEN** os bancos user_db, inventory_db, order_db, payment_db e shipment_db existem e estão vazios, prontos para as migrations de cada serviço

### Requirement: Conectores Debezium registrados automaticamente e insistindo até RUNNING

O compose SHALL incluir um serviço `connect-init` que registra automaticamente os conectores Debezium para cada banco produtor (user, inventory, order, payment, shipment) através de um script que MUST executar `PUT` na REST API do Kafka Connect para cada arquivo de conector JSON e MUST reintentar até cada conector alcançar o estado `RUNNING`, com timeout e falha clara se um conector não ficar pronto após tentativas.

#### Scenario: Conectores registram automaticamente na subida
- **WHEN** o serviço `connect-init` executa após o `connect` estar saudável
- **THEN** todos os cinco conectores (user-outbox, inventory-outbox, order-outbox, payment-outbox, shipment-outbox) são registrados via PUT em `http://connect:8083/connectors/<nome>/config`

#### Scenario: Script insiste até conectores ficarem RUNNING
- **WHEN** um conector falha inicialmente porque a publicação Debezium não existe ainda (Flyway não rodou)
- **THEN** o script `register.sh` reinicia o conector automaticamente a cada 10 segundos até ficarem no estado `RUNNING`, ou falha após 30 tentativas com mensagem clara

#### Scenario: Ordem de subida garante pré-requisitos
- **WHEN** o compose inicia
- **THEN** o serviço `connect` depende de `db` healthy e `broker` started; `connect-init` depende de `connect` healthy; nenhum serviço de aplicação depende de `connect-init`, mas as publicações são criadas pelo Flyway dos serviços

### Requirement: Mensagem da outbox chega ao tópico com estrutura completa

Quando um evento é inserido na tabela `outbox` de um banco via transação de negócio, o Debezium MUST ler a inserção do WAL logical, rotear para o tópico especificado na coluna `topic`, usar o valor da coluna `aggregateid` como key da mensagem Kafka (como string), incluir o valor da coluna `type` em um header Kafka chamado `__TypeId__`, incluir o `id` do evento como header `id`, e usar o JSON da coluna `payload` como valor da mensagem (bytes brutos).

#### Scenario: Evento outbox vira mensagem Kafka com key e type
- **WHEN** uma aplicação insere uma linha em `outbox` com `aggregateid='pedido-123'`, `type='orderCreated'`, `topic='ecommerce.order.created.v1'` e `payload='{"id":123,...}'` na mesma transação que muda estado
- **THEN** uma mensagem aparece no tópico `ecommerce.order.created.v1` com key `"pedido-123"`, header `__TypeId__: orderCreated`, header `id: <uuid>`, e valor sendo o JSON original

#### Scenario: Heartbeat mantém slot de replicação andando
- **WHEN** um banco não recebe inserts na `outbox` por um tempo
- **THEN** o Debezium atualiza a tabela `debezium_heartbeat` periodicamente via `UPDATE`, mantendo o slot avançando e o WAL não acumulando

### Requirement: Variáveis de ambiente documentadas em .env.example

O arquivo `micro-services/.env.example` SHALL documentar cada variável de ambiente que os serviços leem (KAFKA_BOOTSTRAP, REDIS_HOST, DB_URL, JWKS_URI, GATEWAY_URL, SERVICE_CLIENT_SECRET, URLs dos serviços downstream, STORE_OWNER_EMAIL, credenciais do Mercado Pago, FRONT_URL), indicar se é obrigatória ou opcional, listar quais serviços a usam, e mostrar o valor padrão local ou de exemplo. Um segundo arquivo `micro-services/api-gateway/.env.example` SHALL documentar as variáveis específicas do gateway (secrets dos clientes de serviço, MERCADOPAGO_WEBHOOK_SECRET, FRONT_URL).

#### Scenario: .env.example contém todas as variáveis
- **WHEN** um novo desenvolvedor clona o repositório e lê `micro-services/.env.example`
- **THEN** encontra cada variável lida nos `application.properties` dos serviços, com comentário sobre que serviço a usa, se é obrigatória, e qual é o padrão local

#### Scenario: Valores de exemplo são usáveis no compose local
- **WHEN** o desenvolvedor copia `micro-services/.env.example` para `micro-services/.env` (opcional)
- **THEN** todos os valores (ou defaults do compose) permitem que o ambiente suba sem erros de variáveis faltantes

### Requirement: README com arquitetura e instruções

Um arquivo `README.md` na raiz do repositório SHALL conter: (1) visão geral da loja em uma frase; (2) diagrama ou descrição da arquitetura em microsserviços com os seis serviços, Postgres, Kafka e Redis; (3) explicação de como a saga de pedido orquestra os serviços; (4) list of the stack tecnológico (Spring Boot 4.1.1, Java 25, Postgres 17, Kafka, Redis, Debezium); (5) instruções passo a passo para rodar `docker compose up --build`, listar as portas e URLs acessíveis, e descrever como testar uma saga ponta a ponta; (6) referências aos contratos (event-contracts.md, api-contracts.md, outbox-debezium.md).

#### Scenario: README fornece visão geral clara
- **WHEN** um novo desenvolvedor abre o `README.md`
- **THEN** entende em dois parágrafos o que é a loja, como está arquitetada (qual serviço faz o quê), e onde encontrar os contratos

#### Scenario: README explica como rodar o ambiente
- **WHEN** o desenvolvedor segue as instruções do README
- **THEN** consegue executar `docker compose up --build` na pasta correta, aguardar a subida, acessar o gateway em `localhost:8080`, e validar que pelo menos uma rota responde

#### Scenario: README descreve saga ponta a ponta
- **WHEN** o desenvolvedor lê a seção de testes
- **THEN** encontra um exemplo de como criar um usuário, adicionar produto ao carrinho, fazer uma compra, e ver o pedido passar por inventory, payment e shipment via eventos Kafka

### Requirement: Versões de imagens Docker são fixas

Todos os serviços e infraestrutura no `docker-compose.yaml` MUST usar tags de versão específicas (não `latest`), com as versões confirmadas como existentes nos respectivos registries antes de serem fixadas. Postgres MUST ser versão 17, Kafka versão recente estável, Redis versão 7.4 ou superior, e Debezium versão 3.x ou superior.

#### Scenario: Compor não usa tags latest
- **WHEN** o arquivo `docker-compose.yaml` é analisado
- **THEN** cada serviço tem uma tag de versão específica (p.ex., `postgres:17.0`, `kafka:3.7.0`) e nenhum usa `latest` ou versão flutuante

#### Scenario: Imagens com tags específicas existem nos registries
- **WHEN** o desenvolvedor tenta fazer `docker compose pull`
- **THEN** todas as imagens com as tags fixadas existem e são baixadas com sucesso, sem erros de tag não encontrada

