# Tasks

## 1. Postgres e imagens

- [ ] 1.1 Em `micro-services/docker-compose.yaml`, adicionar ao serviço `db` o `command: ["postgres", "-c", "wal_level=logical", "-c", "max_replication_slots=10", "-c", "max_wal_senders=10"]`; verificar com `docker compose config` sem erro (se docker indisponível, validar YAML com `python -c "import yaml,sys;yaml.safe_load(open(sys.argv[1]))" micro-services/docker-compose.yaml`)
- [ ] 1.2 Fixar as tags de `apache/kafka`, `redis` e da nova imagem `quay.io/debezium/connect` consultando os registries (design.md "Versões fixas"); verificar que cada tag aparece na resposta HTTP do registry

## 2. Debezium

- [ ] 2.1 Criar `micro-services/debezium/register.sh` exatamente como `docs/outbox-debezium.md` §6.5.3; verificar `sh -n micro-services/debezium/register.sh`
- [ ] 2.2 Criar os cinco `micro-services/debezium/connectors/{user,inventory,order,payment,shipment}-outbox.json` com os valores por serviço do design.md; verificar que os cinco são JSON válido (`python -m json.tool`)
- [ ] 2.3 Adicionar os serviços `connect` e `connect-init` ao compose conforme `docs/outbox-debezium.md` §6.5.2, sem `ports:`; verificar YAML válido

## 3. Variáveis dos serviços

- [ ] 3.1 Reescrever o bloco `environment:` de `api-gateway`, `user`, `inventory`, `order`, `payment` e `shipment` com o contrato de variáveis do design.md, e `depends_on` com `db` healthy, `broker` e `redis`; verificar que nenhuma das variáveis antigas `SPRING_*` sobrou (`grep SPRING_ micro-services/docker-compose.yaml` vazio)
- [ ] 3.2 Criar `micro-services/.env.example` (STORE_OWNER_EMAIL, MP_*, *_CLIENT_SECRET, FRONT_URL, DEBEZIUM_DB_*) e `micro-services/api-gateway/.env.example` (GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET), cada variável com comentário; verificar que `.env` continua ignorado (`git check-ignore micro-services/.env`)

## 4. README de portfólio

- [ ] 4.1 Criar `README.md` na raiz (substituindo o atual) com: resumo curto em inglês no topo; visão geral em português; diagrama de arquitetura (mermaid) com gateway, 5 serviços, Kafka, Debezium, Redis, Postgres; tabela de serviços/portas/responsabilidades; saga do checkout (mermaid sequence resumido de `docs/event-contracts.md` §10.1); decisões de arquitetura (loja única, outbox+Debezium, token interno aud=internal, orquestração pelo order, retry bloqueante+DLT); stack; como rodar (`cp .env.example .env`, chave RSA do gateway, `docker compose up --build`, Google OAuth); links para os quatro documentos de `docs/` e para `openspec/`; verificar que todo link relativo aponta para arquivo existente

## 5. Verificação

- [ ] 5.1 Validar o compose (`docker compose -f micro-services/docker-compose.yaml config` ou o parse YAML da 1.1) e confirmar que só `api-gateway` (além de `db`/`broker` de dev) tem `ports:`; este change não toca Java, então não há `mvn compile` a rodar
