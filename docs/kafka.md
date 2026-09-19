# Conectando os micro-serviços ao Kafka do docker-compose

Stack verificada neste repo: **Spring Boot 4.1.1**, **spring-kafka 4.1.1**, **Java 25**,
imagem `apache/kafka:latest` (KRaft, sem Zookeeper).

No Boot 4 o starter correto é o que você já colocou nos `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-kafka</artifactId>
</dependency>
```

Ele traz `spring-kafka` + o módulo de auto-configuração `spring-boot-kafka` (no Boot 3 era
preciso declarar `org.springframework.kafka:spring-kafka` avulso — não é mais o caminho).
Para testes já está no `pom.xml` o `spring-boot-starter-kafka-test`.

---

## 1. O broker atual não aceita conexão (corrigir primeiro)

O `micro-services/docker-compose.yaml` está assim:

```yaml
KAFKA_LISTENERS: PLAINTEXT://localhost:9092,CONTROLLER://localhost:9093
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
# ...e nenhum bloco `ports:`
```

Dois problemas somados:

1. **Sem `ports:`** — a 9092 não é publicada no host, então nada de fora do contêiner chega nela.
2. **`KAFKA_LISTENERS` com `localhost`** — o socket fica preso na loopback *de dentro* do
   contêiner. Mesmo publicando a porta, o Docker não teria onde encaminhar o tráfego.

Existe ainda uma armadilha clássica do Kafka: o cliente conecta no `bootstrap-servers`, mas o
broker responde com o **`advertised.listeners`**, e é para *esse* endereço que o cliente
reconecta de verdade. Se o anunciado for um host que o cliente não resolve, a conexão morre
depois do handshake, com um erro confuso de timeout de metadata.

Como seus serviços rodam **no host** (o `application.properties` aponta para
`localhost:5432`), você precisa de um listener anunciado como `localhost:9092`. A configuração
abaixo já deixa pronto também o listener interno da rede Docker, para quando você
containerizar os serviços:

```yaml
  broker:
    image: apache/kafka:latest
    container_name: broker
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      # bind em 0.0.0.0 — aceita conexão de qualquer interface
      KAFKA_LISTENERS: HOST://0.0.0.0:9092,DOCKER://0.0.0.0:9093,CONTROLLER://0.0.0.0:9094
      # o que o broker anuncia para cada tipo de cliente
      KAFKA_ADVERTISED_LISTENERS: HOST://localhost:9092,DOCKER://broker:9093
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,HOST:PLAINTEXT,DOCKER:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: DOCKER
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9094
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_NUM_PARTITIONS: 3
    volumes:
      - kafkadata:/var/lib/kafka/data
    healthcheck:
      test: ["CMD-SHELL", "/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10

volumes:
  pgdata:
  kafkadata:
```

Resumo de quem usa qual endereço:

| Cliente | `bootstrap-servers` |
|---|---|
| Serviço rodando no host (situação de hoje) | `localhost:9092` |
| Serviço rodando como contêiner no mesmo compose | `broker:9093` |
| CLI dentro do contêiner do broker | `localhost:9092` |

> `KAFKA_INTER_BROKER_LISTENER_NAME` é obrigatório quando existe mais de um listener
> não-controller — sem ele o broker não sobe. E o listener `CONTROLLER` **nunca** entra no
> `advertised.listeners`.

Subir e conferir:

```bash
cd micro-services
docker compose up -d broker
docker compose logs -f broker         # espere "Kafka Server started"
docker exec -it broker /opt/kafka/bin/kafka-broker-api-versions.sh \
  --bootstrap-server localhost:9092
```

---

## 2. Configuração nos serviços (`application.properties`)

### 2.1 Bloco pronto para colar

Igual em todos os cinco serviços:

```properties
# --- Kafka ---
spring.kafka.bootstrap-servers=localhost:9092

spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer

spring.kafka.consumer.group-id=${spring.application.name}
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JacksonJsonDeserializer

spring.kafka.listener.concurrency=3
```

Mais uma linha por evento, essa sim diferente em cada serviço (ver 2.5):

```properties
# no order (produtor)
spring.kafka.producer.properties.spring.json.type.mapping=\
  orderCreated:ecommerce_event_driven.order.modules.order.infra.outbound.messaging.events.OrderCreatedEvent

# no inventory (consumidor)
spring.kafka.consumer.properties.spring.json.type.mapping=\
  orderCreated:ecommerce_event_driven.inventory.modules.product.infra.inbound.messaging.events.OrderCreatedEvent
```

> **Use `JacksonJson*`, não `Json*`.** Em spring-kafka 4.x, `JsonSerializer` e
> `JsonDeserializer` estão anotados com `@Deprecated(forRemoval = true, since = "4.0")` — são a
> versão Jackson 2 (`com.fasterxml.jackson`). O Boot 4 usa Jackson 3 (`tools.jackson`), e as
> classes atuais são `JacksonJsonSerializer` / `JacksonJsonDeserializer`. Os nomes das
> propriedades `spring.json.*` são idênticos nas duas.

### 2.2 O que é obrigatório e por quê

| Linha | O que acontece sem ela |
|---|---|
| `producer.value-serializer` | O default é `StringSerializer`: publicar um record explode com `ClassCastException` |
| `consumer.value-deserializer` | Idem no consumo — chega `String`, o listener espera o record |
| `consumer.group-id` | O container não sobe: todo `@KafkaListener` precisa de um grupo (na property ou no atributo `groupId` da anotação) |
| `type.mapping` (ou equivalente da 2.5) | O consumidor procura a classe do *produtor*, que não existe no pacote dele |

O `auto-offset-reset=earliest` não é obrigatório, mas o default é `latest` — um consumidor que
sobe depois do evento publicado simplesmente não vê nada, e isso parece bug de conexão.

### 2.3 O que já vem por default (não escreva)

Verificado em Boot 4.1.1 / spring-kafka 4.1.1 / kafka-clients 4.2.1:

| Propriedade | Default | Onde está definido |
|---|---|---|
| `spring.kafka.bootstrap-servers` | `localhost:9092` | `KafkaProperties:71` |
| `producer.key-serializer` | `StringSerializer` | `KafkaProperties:523` |
| `consumer.key-deserializer` | `StringDeserializer` | `KafkaProperties:300` |
| `producer.acks` | `all` | `ProducerConfig:393` (desde o Kafka 3.0) |
| `producer.properties.enable.idempotence` | `true` | `ProducerConfig:529` (desde o Kafka 3.0) |
| `consumer.enable-auto-commit` | forçado a `false` | `KafkaMessageListenerContainer.determineAutoCommit()` grava `false` quando você não configura nada |

O `bootstrap-servers` continua no bloco acima de propósito, mesmo sendo redundante hoje: é o
valor que muda por ambiente (`localhost:9092` no host, `broker:9093` em contêiner), e deixá-lo
implícito esconde a configuração mais importante do arquivo.

### 2.4 Ajustes opcionais

- **`listener.concurrency=3`** — default é 1. Casa com `KAFKA_NUM_PARTITIONS: 3`; passar disso
  não ajuda, porque uma partição só é lida por um consumidor do grupo.
- **`listener.ack-mode=record`** — default é `BATCH` (commit ao fim de cada `poll()`). Commit
  por registro reduz reprocessamento após um crash, ao custo de mais commits. Se o consumidor
  já é idempotente, o default basta.
- **`ErrorHandlingDeserializer`** — tecnicamente opcional (daria para apontar
  `value-deserializer` direto para o `JacksonJsonDeserializer`), mas está no bloco principal de
  propósito: sem ele, uma mensagem com JSON inválido estoura *antes* do listener, o offset não
  avança e o consumer entra em loop infinito no mesmo registro (o clássico "poison pill"). Com
  ele, o erro vira um payload nulo que o error handler trata normalmente. As propriedades
  `spring.json.*` continuam valendo — são repassadas ao delegate.

Sobre o `group-id`: cada micro-serviço é um grupo de consumo. Serviços diferentes recebem
cópias do mesmo evento; duas instâncias do *mesmo* serviço dividem as partições entre si.

### 2.5 O tipo do evento entre serviços diferentes

O `JacksonJsonSerializer` grava o nome completo da classe no header `__TypeId__`. Como `order` e
`inventory` têm pacotes diferentes, o consumidor não encontra essa classe. Três saídas:

**A) Mapear tipos lógicos** — é o que está no bloco da 2.1:

```properties
spring.kafka.producer.properties.spring.json.type.mapping=orderCreated:<FQCN no produtor>
spring.kafka.consumer.properties.spring.json.type.mapping=orderCreated:<FQCN no consumidor>
```

O que viaja no header é o apelido (`orderCreated`), não o pacote — então o produtor pode
refatorar pacote sem quebrar ninguém. Funciona com vários tipos no mesmo serviço, que é o caso
aqui.

**B) Fixar um tipo único no consumidor:**

```properties
spring.kafka.producer.properties.spring.json.add.type.headers=false
spring.kafka.consumer.properties.spring.json.use.type.headers=false
spring.kafka.consumer.properties.spring.json.value.default.type=<FQCN no consumidor>
```

Mais curto, mas é **um tipo por consumer factory** — some assim que o serviço passar a consumir
um segundo tópico com outro payload.

**C) Módulo compartilhado de contratos** — um artefato `contracts` com os records de evento,
dependência dos cinco serviços. O FQCN passa a ser o mesmo dos dois lados e nada disso é
preciso. Em troca, cria acoplamento de build: mudar um evento obriga a republicar o módulo.

**Sobre `spring.json.trusted.packages`:** só é necessária na quarta situação — headers com o
FQCN cru, sem mapeamento. O default confiável é apenas `java.util` e `java.lang`
(`DefaultJacksonJavaTypeMapper:54`), então qualquer classe sua é recusada com
`IllegalArgumentException: The class ... is not in the trusted packages`. Com as opções A, B ou
C acima, a checagem nem roda: `getClassIdType()` consulta o mapeamento antes e retorna direto.

---

## 3. Encaixe na arquitetura hexagonal do projeto

As pastas `application/ports/outbound/messaging`, `infra/outbound/messaging` e
`infra/inbound/messaging` já existem em cada módulo. O Kafka fica **só na infra**:

```
modules/order/
├── application/ports/outbound/messaging/
│   └── OrderEventPublisherPort.java      <- interface, sem Kafka
├── application/usecases/
│   └── CreateOrderUseCase.java           <- depende da port
├── domain/models/
│   └── Order.java
└── infra/
    ├── outbound/messaging/
    │   ├── OrderEventKafkaPublisher.java <- adapter, usa KafkaTemplate
    │   └── events/OrderCreatedEvent.java <- DTO de transporte (record)
    └── inbound/messaging/
        └── OrderCreatedKafkaListener.java
```

Mesma convenção `*Port` / `*Adapter` que você já usa nos repositórios.

### 3.1 Port (application) — não conhece Kafka

```java
package ecommerce_event_driven.order.modules.order.application.ports.outbound.messaging;

import ecommerce_event_driven.order.modules.order.domain.models.Order;

public interface OrderEventPublisherPort {
    void publishOrderCreated(Order order);
}
```

### 3.2 Evento (infra) — contrato de fio, versione com cuidado

```java
package ecommerce_event_driven.order.modules.order.infra.outbound.messaging.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderCreatedEvent(
        String eventId,
        Instant occurredAt,
        Long orderId,
        Long customerId,
        BigDecimal total,
        List<Item> items
) {
    public record Item(Long productId, int quantity) {}
}
```

Use `record` com tipos simples. **Não publique a entidade JPA nem o model de domínio**: o
contrato do tópico é público entre serviços e vira acoplamento permanente — qualquer refactor
interno passaria a quebrar consumidores.

### 3.3 Adapter de saída (producer)

> **Superado para evento de domínio.** Desde o ADR-001 (`docs/outbox-debezium.md`), evento de
> domínio é gravado na tabela `outbox`, na transação que muda o estado, e publicado pelo
> Debezium. O adapter com `KafkaTemplate` abaixo fica como referência e continua valendo só
> para publicar na DLT.

```java
package ecommerce_event_driven.order.modules.order.infra.outbound.messaging;

import ecommerce_event_driven.order.modules.order.application.ports.outbound.messaging.OrderEventPublisherPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.modules.order.infra.outbound.messaging.events.OrderCreatedEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventKafkaPublisher implements OrderEventPublisherPort {

    public static final String TOPIC = "ecommerce.order.created.v1";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventKafkaPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                order.getId(),
                order.getCustomerId(),
                order.getTotal(),
                order.getItems().stream()
                        .map(i -> new OrderCreatedEvent.Item(i.getProductId(), i.getQuantity()))
                        .toList()
        );

        // a key define a partição -> garante ordem por pedido
        kafkaTemplate.send(TOPIC, String.valueOf(order.getId()), event);
    }
}
```

O `KafkaTemplate<String, Object>` já vem pronto do auto-configure — não precisa declarar bean.

**A key importa**: o Kafka só garante ordem *dentro de uma partição*. Usando `orderId` como
key, todos os eventos de um mesmo pedido caem na mesma partição e chegam na ordem correta.

### 3.4 Adapter de entrada (consumer)

```java
package ecommerce_event_driven.inventory.modules.product.infra.inbound.messaging;

import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases.ReserveStockUseCase;
import ecommerce_event_driven.inventory.modules.product.infra.inbound.messaging.events.OrderCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedKafkaListener {

    private final ReserveStockUseCase reserveStock;

    public OrderCreatedKafkaListener(ReserveStockUseCase reserveStock) {
        this.reserveStock = reserveStock;
    }

    @KafkaListener(topics = "ecommerce.order.created.v1", groupId = "inventory")
    public void onOrderCreated(OrderCreatedEvent event,
                               @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        reserveStock.execute(event.orderId(), /* ... */);
    }
}
```

Não é preciso nenhuma classe `@Configuration` nem `@EnableKafka`: o starter liga o
suporte a `@KafkaListener` automaticamente.

**Idempotência não é opcional.** O Kafka entrega *at-least-once*: rebalance, retry ou redeploy
fazem a mesma mensagem chegar duas vezes. Guarde o `eventId` numa tabela de eventos
processados e ignore repetidos, ou torne a operação naturalmente idempotente.

### 3.5 Criação dos tópicos

Em dev, `spring.kafka.admin.auto-create` já é `true` por padrão: basta declarar beans
`NewTopic` e o Boot cria os tópicos no startup.

```java
package ecommerce_event_driven.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic orderCreatedTopic() {
        return TopicBuilder.name("ecommerce.order.created.v1")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
```

Em produção, crie tópicos por processo controlado — auto-criação por causa de um typo no nome
vira tópico fantasma silencioso.

Convenção de nome sugerida: `ecommerce.<agregado>.<fato-no-passado>.v<versão>`.
Mudança incompatível de payload = tópico novo `.v2`, nunca alteração no lugar.

---

## 4. Erros, retry e DLT

Por padrão o Boot instala um `DefaultErrorHandler` que reprocessa a mensagem algumas vezes e,
persistindo o erro, apenas loga. Para retry com backoff e Dead Letter Topic, use
`@RetryableTopic` no listener:

```java
@RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltTopicSuffix = "-dlt"
)
@KafkaListener(topics = "ecommerce.order.created.v1", groupId = "inventory")
public void onOrderCreated(OrderCreatedEvent event) { ... }

@DltHandler
public void onDlt(OrderCreatedEvent event,
                  @Header(KafkaHeaders.ORIGINAL_TOPIC) String topic) {
    log.error("Evento enviado para DLT, tópico original {}: {}", topic, event);
}
```

Isso cria tópicos auxiliares `...-retry-0`, `...-retry-1` e `...-dlt` automaticamente — assim o
consumidor não fica bloqueado enquanto uma mensagem problemática espera o próximo retry.

---

## 5. Verificar que funcionou

Com o broker de pé:

```bash
# listar tópicos
docker exec -it broker /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list

# ler o que o serviço publicou (desde o início)
docker exec -it broker /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic ecommerce.order.created.v1 --from-beginning --property print.key=true

# publicar um evento na mão para testar o consumidor
docker exec -it broker /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic ecommerce.order.created.v1 --property "parse.key=true" --property "key.separator=:"

# lag do grupo de consumo
docker exec -it broker /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group inventory
```

Para enxergar o handshake do lado Spring:

```properties
logging.level.org.springframework.kafka=INFO
logging.level.org.apache.kafka.clients.NetworkClient=DEBUG
```

### Sintomas comuns

| Sintoma | Causa provável |
|---|---|
| `Connection to node -1 could not be established` | broker sem `ports:` publicado, ou não subiu |
| Conecta e depois trava em `Timeout expired ... metadata` | `advertised.listeners` anuncia host que o cliente não resolve |
| `The class ... is not in the trusted packages` | header com FQCN cru — falta o `type.mapping` da seção 2.5 |
| `ClassNotFoundException` no consumidor com o pacote do produtor | mesma causa: o `type.mapping` não está nos dois lados |
| `ClassCastException` ao publicar | `value-serializer` ainda no default `StringSerializer` |
| Consumidor relendo a mesma mensagem sem parar | poison pill sem `ErrorHandlingDeserializer` |
| Listener nunca recebe nada | nome do tópico divergente, `auto-offset-reset=latest` (default), ou `group-id` já com offsets consumidos |
| Eventos fora de ordem | key nula — mensagens espalhadas entre as partições |

---

## 6. Testes

O `spring-boot-starter-kafka-test` já está no `pom.xml` de todos os serviços:

```java
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "ecommerce.order.created.v1")
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class OrderEventKafkaPublisherTest { ... }
```

Para testes mais próximos do real, Testcontainers com `org.testcontainers:kafka` sobe um broker
de verdade e evita divergência entre o embedded e produção.

---

## Checklist de ativação

1. [ ] Corrigir o serviço `broker` no `docker-compose.yaml` (seção 1)
2. [ ] `docker compose up -d broker` e validar com `kafka-broker-api-versions.sh`
3. [ ] Colar o bloco da seção 2.1 no `application.properties` de cada serviço
4. [ ] Definir os eventos como `record` em `infra/**/messaging/events`
5. [ ] Declarar o `type.mapping` nos dois lados de cada evento (seção 2.5)
6. [ ] Criar as ports em `application/ports/outbound/messaging` e os adapters em `infra`
7. [ ] Declarar os `NewTopic` de cada serviço produtor
8. [ ] Garantir idempotência no consumidor antes de ligar em ambiente compartilhado
