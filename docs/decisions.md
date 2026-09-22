# Registro de decisões

Toda decisão de produto ou de arquitetura tomada ao longo do projeto, com o porquê. As decisões de
cada funcionalidade estão detalhadas no `design.md` da change correspondente em
`openspec/changes/`; aqui fica o índice e o raciocínio.

## Produto

| Decisão | Por quê |
|---|---|
| Loja única, não marketplace: um só vendedor (`owner`) | É o produto que se quer. Simplifica checkout (um pedido, uma origem, um envio) e elimina repasse e cadastro de vendedor |
| Sem papel `admin` | Não existe quem administre; a gestão (produtos, estoque, estornos, despacho) é do `owner` |
| Categorias só por migration, API somente leitura | Consequência de não haver `admin` |
| O `owner` nasce pela configuração `app.store.owner-email` | Seed por migration é impossível: `owner.id_user` tem FK para `users`, e o usuário só existe depois do primeiro login com o Google |
| Frete = `ceil(km em linha reta) × R$ 1,00` a partir da loja | Sem transportadora integrada; regra simples e previsível |
| Origem do frete é o endereço da loja em configuração, já com latitude/longitude | Só o destino precisa ser geocodificado |
| A loja despacha e o **comprador** confirma a entrega; confirmação automática após 15 dias | Sem rastreio automático. Se a loja pudesse marcar "entregue", fecharia pedidos de quem não recebeu |
| Avaliação pertence ao produto; só avalia quem recebeu | Não existe reputação de vendedor numa loja única |
| Endereço é imutável: editar cria um novo e remove o antigo | Um pedido aponta para o endereço; editá-lo entre o checkout e o pagamento mudaria o destino de um frete já cobrado |
| Foto de produto sempre por URL; upload de arquivo fora do escopo | Evita storage de objetos no escopo atual |
| Pagamento pelo Mercado Pago (PIX, cartão tokenizado no front, Checkout Pro) | Meio de pagamento do mercado-alvo; o backend nunca recebe número de cartão |
| Sem credencial do Mercado Pago, o `payment` sobe em **modo fake** que aprova na hora | A saga inteira fica demonstrável localmente sem conta no provedor |
| Remover a conta não consulta pedidos abertos; pedidos são mantidos | Pedido é registro histórico; consultar o `order` síncrono só para isso acoplaria os serviços |

## Arquitetura

| Decisão | Por quê |
|---|---|
| Só o `api-gateway` publica porta | Trava de rede: nada da internet chega aos serviços |
| Dois tokens: browser `aud=front`, interno `aud=internal`, emitidos só pelo gateway | O token do usuário nunca é repassado; cada chamada interna leva um token novo e de vida curta |
| O token interno carrega o conjunto de escopos do **papel** (não da rota) | Simplifica a borda; os serviços continuam checando escopo por rota |
| Chamadas servidor-a-servidor usam token de serviço (`POST /auth/service-token`, credencial por cliente) | Consumidores Kafka não têm token de usuário; e o token do usuário não deve abrir rotas internas |
| Sessão por Bearer, sem cookie; refresh deslizante com o próprio token e teto de 7 dias (`auth_time`) | Pedido explícito; o teto impede que um token vazado se renove para sempre |
| Token de login entregue em fragmento (`#token=`), não em query string | Fragmento não vai ao servidor nem ao header `Referer` |
| Proxy próprio com `RestClient` em vez de Spring Cloud Gateway | Dependência grande e incerta no Spring Boot 4.1 |
| O `order` orquestra a saga; os demais serviços nunca reagem a eventos uns dos outros | Tópicos diferentes não têm ordem entre si; a máquina de estados fica num lugar só |
| Envio só nasce em `order.confirmed` (pago **e** com estoque baixado) | Evita envio de pedido sem estoque |
| Todo estorno automático sai por `order.refund.requested` | Um caminho só para cancelamento, pagamento tardio e pagamento em dobro |
| Reserva vencida no pagamento é recomprometida se houver estoque; senão cancela e estorna sozinho | Nenhum caso da saga depende de intervenção manual |
| Outbox transacional + Debezium (ADR-001, `docs/outbox-debezium.md`) | O evento sai se, e somente se, a transação commitou, e na ordem de commit |
| Retry de consumidor bloqueante na partição + DLT `<tópico>-dlt` | `@RetryableTopic` quebraria a ordem por pedido |
| Consumidores decidem pelo estado atual, não pelo `eventId` | Republicação gera `eventId` novo; o estado de negócio é a fonte da idempotência |
| Não existe `user.updated`; só `user.deleted` (LGPD) altera snapshot | Snapshot é a verdade da época |
| Redis só como cache e idempotência; queda do Redis não derruba rota | Redis é otimização, não dependência de disponibilidade (exceto denylist de sessão) |
| Geocodificação: BrasilAPI com fallback na Nominatim (OpenStreetMap) | Muitos CEPs não têm coordenada na BrasilAPI e travariam o checkout |
| ETag/304 e moderação de texto de avaliação não implementados | Fora do escopo atual; nenhum fluxo depende deles |

## Correções encontradas na verificação ponta a ponta

A stack inteira foi levantada em Docker e a saga percorrida do carrinho à avaliação. O que
quebrou no caminho, e o que ficou decidido:

| Problema | Decisão |
|---|---|
| `POST /auth/service-token` anunciava `expiresIn: 300` com TTL real de 1 min | O `expiresIn` sai do token emitido. Cliente que cacheia pelo valor anunciado usava um token vencido e levava 401 nas chamadas internas |
| Toda rota `PATCH` respondia 503 na borda | O proxy usa `JdkClientHttpRequestFactory`: o `HttpURLConnection` do `SimpleClientHttpRequestFactory` recusa `PATCH` |
| `stock.committed` era gravado depois da transação de baixa | A escrita na outbox é `MANDATORY`: fora de transação ela estoura **depois** do estoque baixado, e a reentrega cai em "já commitado" sem publicar. O evento passou para dentro da transação |
| `order.confirmed` sem `addressId`/`freightCost` e `order.delivered` sem `items` | O payload do evento é contrato: o consumidor não volta ao produtor por HTTP para completar o que faltou |
| Projeções de envio e pagamento gravadas salvando o pedido inteiro | Cada projeção tem seu próprio `UPDATE`. Salvando o agregado, dois eventos concorrentes se sobrescrevem: o estorno voltava de `refunded` para `cancelled` |
| Falha de consumo só aparecia quando o registro chegava ao DLT | `RetryListener` em todos os serviços, registrando tópico, partição, offset e causa a cada tentativa |
| Corpo fora do contrato virava 500 | Campo obrigatório ausente responde 400, e a validação acontece antes de gravar o pagamento pendente para não queimar a `Idempotency-Key` |
| `isOwner` fixo em `false` no cancelamento | O papel vem do token; `processing` → `cancelled` é transição da loja, como o contrato define |
| Avaliação gravada sem nome do autor | O cliente do `user` não mandava o token de serviço e levava 401 silencioso |
| Carrinho de conta removida continuava de pé | A limpeza filtrava por `cart_items.cart_id`, coluna inexistente: o evento morria no DLT. O log de retry passou a mostrar a causa mais específica |

## Processo

| Decisão | Por quê |
|---|---|
| Planejamento em OpenSpec (`openspec/`): uma change por serviço/fase, com proposal, specs em delta, design e tasks validados com `openspec validate --strict` | Cada funcionalidade tem o porquê, o contrato e as tarefas rastreáveis |
| Implementação em duas ondas; cada change num git worktree próprio | Changes da primeira onda não se tocam; a segunda depende da primeira no mesmo serviço |
| Toda change passa por revisão antes do merge: compilação, busca de stubs e leitura dos pontos críticos | Várias entregas marcavam tarefa como concluída com resposta fixa; as correções estão na seção "Correções da revisão" de cada `design.md` |

## CI

| Decisão | Por quê |
|---|---|
| Um job por microsserviço no GitHub Actions, com `fail-fast: false` | O serviço que quebrou aparece sozinho; os outros continuam sendo testados |
| O job sobe um Postgres de verdade | Os testes carregam o contexto Spring, que abre o pool e roda o Flyway. Banco em memória não vale: o schema usa `ENUM` e `jsonb` do Postgres |
| Kafka fica de fora do job | O listener não bloqueia o boot: sem broker ele entra em retry e o contexto sobe igual. Subir um broker só para isso seria custo sem cobertura |
| O gateway gera um par de chaves descartável no job | As chaves de assinatura não são versionadas; o contexto não sobe sem elas |
| `mvnw` versionado com bit de execução | Sem ele o runner Linux para em "Permission denied" |

## Front

| Decisão | Por quê |
|---|---|
| Next.js (App Router) + TypeScript | A vitrine precisa aparecer em busca, e é o Next que dá renderização no servidor sem montar um segundo backend |
| Vitrine renderizada no servidor; o que depende de sessão, no navegador | O catálogo é público e sai pronto no HTML. A sessão é um Bearer no `sessionStorage`: não há cookie para o servidor ler, então tentar renderizar tela autenticada no servidor só criaria um caminho que nunca funciona |
| Home e listagem por requisição, sem congelar no build | O catálogo nasce vazio e é cadastrado com a loja no ar: uma página gerada no build mostraria a vitrine vazia para quem chegasse primeiro, e preço e estoque mudam o tempo todo |
| Duas variáveis para o endereço da API: `NEXT_PUBLIC_API_URL` e `INTERNAL_API_URL` | O navegador fala com `localhost` e o servidor do Next fala com `api-gateway` dentro da rede do compose. Uma variável só quebraria um dos dois |
| Porta 3000, fixa | O gateway só aceita uma origem no CORS e só devolve o login para ela (`app.front-url`). Outra porta quebra login e todas as chamadas |
| Token no `sessionStorage`, nunca em cookie | O contrato é Bearer no header. `sessionStorage` morre com a aba; `localStorage` sobreviveria além da sessão sem motivo |
| Token capturado do fragmento e apagado da barra de endereços na hora | Fragmento não vai ao servidor nem ao `Referer`; apagar evita que fique no histórico ou num print |
| Um cliente HTTP só, com Bearer, `Idempotency-Key` e envelope RFC 9457 | Espalhar isso por tela garante que uma esqueça, e um `401` silencioso vira tela quebrada |
| Estado de servidor no TanStack Query, sem store global | O que a tela mostra é cópia do servidor; cache, revalidação e invalidação já são o problema que a biblioteca resolve |
| Cores só por tokens em `src/index.css` | Sem isso cada tela inventa o seu azul e a identidade se perde na terceira tela |
| Uma pasta por área (`features/<área>`), cada uma dona das suas telas | Permitiu implementar as cinco áreas em paralelo sem que duas mexessem no mesmo arquivo |
| Uma fronteira de Suspense no layout, em vez de uma por página | As telas com sessão leem a query string, e sem a fronteira o Next não consegue pré-renderizar nenhuma página que passe pela casca |
| Acompanhamento do PIX e do pedido relê o recurso, com parada | Ler `GET /payments/{id}` a cada 5 s é barato; `sync` tem limite de 1 por minuto no contrato. Para em estado final e não roda com a aba oculta |
| Cartão tokenizado no navegador pelo SDK do Mercado Pago | O número do cartão nunca chega ao nosso backend — é o que mantém o PCI-DSS fora desta aplicação |

### Defeitos que o front revelou no backend

| Defeito | Correção |
|---|---|
| O CORS do gateway declarava origem e métodos, mas nenhum header | Sem `allowedHeaders`, o preflight de qualquer chamada com `Authorization` era recusado: **nenhuma** tela de navegador conseguiria falar com a API |
| `PUT /cart/items/{idProduct}` exigia `idProduct` também no corpo | Reaproveitava o corpo do `POST` e respondia `400` para quem seguisse o contrato |

## Subir a stack

| Decisão | Por quê |
|---|---|
| Um `docker-compose.yaml` na raiz, com backend e vitrine | `docker compose up -d --build` é o único comando para ter a loja inteira no ar; não há um segundo arquivo nem um diretório certo de onde rodar |
| Sobe sem `.env` e sem chave: tudo tem default no compose | Um clone novo tinha que copiar dois `.env` e gerar um par RSA antes de qualquer coisa. O que falta agora são só os segredos que ligam Google e Mercado Pago |
| O gateway gera um par RSA descartável no primeiro boot, se não houver | As chaves de assinatura não são versionadas, e sem elas o serviço não sobe. O log avisa que é descartável: as sessões morrem quando o container é recriado |
| As chaves ficam num volume nomeado, não num bind somente leitura | Num clone novo a pasta não existe no host, e o bind read-only impedia o próprio container de gerar o par |
| `restart: unless-stopped` nos serviços | Quem sobe antes do banco aceitar conexão morria de vez e exigia um segundo `compose up`; agora volta sozinho |
| No Docker o front usa `npm install`, não `npm ci` | O lock nasce no Windows e não lista os binários nativos de Linux (o `lightningcss` do Tailwind), e o `ci` recusa a instalação |
| `.gitattributes` fixa LF em `*.sh` e `mvnw` | Script com CRLF leva um CR no shebang e o container responde "no such file or directory" ao tentar executá-lo |
