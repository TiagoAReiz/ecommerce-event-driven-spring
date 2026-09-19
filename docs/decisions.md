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

## Processo

| Decisão | Por quê |
|---|---|
| Planejamento em OpenSpec (`openspec/`): uma change por serviço/fase, com proposal, specs em delta, design e tasks validados com `openspec validate --strict` | Cada funcionalidade tem o porquê, o contrato e as tarefas rastreáveis |
| Implementação em duas ondas; cada change num git worktree próprio | Changes da primeira onda não se tocam; a segunda depende da primeira no mesmo serviço |
| Toda change passa por revisão antes do merge: compilação, busca de stubs e leitura dos pontos críticos | Várias entregas marcavam tarefa como concluída com resposta fixa; as correções estão na seção "Correções da revisão" de cada `design.md` |
