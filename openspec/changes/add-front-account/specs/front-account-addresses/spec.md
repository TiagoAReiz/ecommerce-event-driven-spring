# Spec Delta: Front-end — Endereços de Entrega

## Purpose

Implementa CRUD de endereços de entrega com validação de CEP (8 dígitos sem máscara), UF (2 letras) e tratamento da imutabilidade de endereços (PUT/PATCH criam novo, deletam antigo).

## ADDED Requirements

### Requirement: Listar endereços de forma paginada

O front-end SHALL carregar e exibir todos os endereços do usuário em lista paginada.

#### Scenario: Página de endereços carrega
- **WHEN** usuário autenticado acessa `/account/addresses`
- **THEN** a página chama `GET /users/me/addresses?page=0&size=20&sort=createdAt,desc` e exibe lista de cards de endereço

#### Scenario: Lista renderiza com sucesso
- **WHEN** `GET /users/me/addresses` retorna `200` com `{ content: [...], page: {...} }`
- **THEN** cada endereço é exibido em card com: etiqueta (ex.: "Casa", "Trabalho"), CEP, cidade, estado, rua, número, opções de editar/deletar

#### Scenario: Lista vazia
- **WHEN** usuário não tem endereços cadastrados
- **THEN** mostra mensagem "Nenhum endereço cadastrado" com botão destacado "Adicionar Endereço"

#### Scenario: Paginação
- **WHEN** usuário tem mais de 20 endereços
- **THEN** exibe controles de paginação (anterior/próximo, números de página); ao clicar em página N, refaz query com `page=N`

#### Scenario: Ordenação
- **WHEN** página de endereços renderiza
- **THEN** ordem padrão é por `createdAt,desc` (mais recentes primeiro)

#### Scenario: Erro de rede ao carregar
- **WHEN** `GET /users/me/addresses` falha
- **THEN** mostra skeleton/loader; TanStack Query retenta automaticamente

#### Scenario: Usuário removido (404)
- **WHEN** `GET /users/me/addresses` retorna `404` (token aponta para usuário deletado)
- **THEN** redireciona para `/login` com mensagem "Sua conta foi removida"

### Requirement: Visualizar detalhes de um endereço

O front-end SHALL exibir o endereço completo em modal ou página detalhada.

#### Scenario: Clicar em endereço na lista
- **WHEN** usuário clica em um card de endereço
- **THEN** abre modal ou navega para `/account/addresses/{id}` mostrando todos os dados: nome, CEP, país, estado, cidade, rua, número, data de criação

#### Scenario: Detalhes carregam com sucesso
- **WHEN** `GET /users/me/addresses/{id}` retorna `200`
- **THEN** modal exibe o endereço completo com botões "Editar" e "Deletar"

#### Scenario: Endereço não encontrado (404)
- **WHEN** `GET /users/me/addresses/{id}` retorna `404` (endereço removido ou de outro usuário)
- **THEN** mostra erro "Endereço não encontrado" e volta para a lista

#### Scenario: Fechar modal
- **WHEN** usuário clica em X ou clica fora do modal
- **THEN** modal fecha; volta para lista de endereços

### Requirement: Criar novo endereço

O front-end SHALL permitir criação de novo endereço com validação de CEP e UF.

#### Scenario: Botão "Novo Endereço"
- **WHEN** usuário está na página de endereços
- **THEN** botão destacado "Adicionar Endereço" ou "+ Novo Endereço" é visível (azul principal)

#### Scenario: Abrir formulário de criação
- **WHEN** usuário clica em "Novo Endereço"
- **THEN** redireciona para `/account/addresses/new` ou abre modal com formulário vazio

#### Scenario: Formulário com campos obrigatórios
- **WHEN** formulário de criação renderiza
- **THEN** campos: `name` (opcional, ≤60 chars), `zipcode` (obrigatório, 8 dígitos), `country` (default BR, opcional), `state` (obrigatório, 2 letras quando BR), `city`, `street` (obrigatórios), `number` (opcional, default "s/n")

#### Scenario: Validação de CEP (8 dígitos, sem máscara)
- **WHEN** usuário digita no campo CEP
- **THEN** campo aceita só números; rejeita não-numéricos; se menos de 8 dígitos, mostra aviso "CEP deve ter 8 dígitos"; se 8 dígitos, valida com backend (opcional: verificar se CEP existe)

#### Scenario: Máscara visual no CEP
- **WHEN** usuário digita CEP
- **THEN** (opcional) exibe visual `XXXXX-XXX` mas valor armazenado é sempre sem hífen: `01310100`

#### Scenario: Validação de UF (2 letras)
- **WHEN** `country=BR` e usuário digita em `state`
- **THEN** campo aceita só 2 letras maiúsculas (ex.: "SP", "RJ"); rejeita números ou caracteres especiais; se menos de 2 caracteres, mostra aviso

#### Scenario: Campos obrigatórios vazios
- **WHEN** usuário deixa `zipcode`, `state`, `city` ou `street` vazios
- **THEN** botão "Salvar Endereço" fica disabled; asterisco ou aviso marca campos obrigatórios

#### Scenario: Enviar formulário de criação
- **WHEN** todos os campos válidos e usuário clica "Salvar Endereço"
- **THEN** envia `POST /users/me/addresses` com `{ name?, zipcode, country, state, city, street, number? }`

#### Scenario: Sucesso na criação (201)
- **WHEN** `POST /users/me/addresses` retorna `201` com novo endereço e `Location` header
- **THEN** redireciona para `/account/addresses` e mostra toast "Endereço criado com sucesso"; novo endereço aparece no topo da lista (via TanStack Query invalidation)

#### Scenario: CEP inexistente (422)
- **WHEN** `POST /users/me/addresses` retorna `422` (CEP validado no backend como inexistente)
- **THEN** mostra erro no campo CEP: "CEP não encontrado. Verifique o número."

#### Scenario: UF incompatível com cidade (422)
- **WHEN** `POST /users/me/addresses` retorna `422` (UF não corresponde à cidade)
- **THEN** mostra erro: "UF não corresponde à cidade informada"

#### Scenario: Erro de rede ao criar
- **WHEN** `POST /users/me/addresses` falha
- **THEN** mostra toast "Erro ao criar endereço"; formulário permanece aberto para retry

#### Scenario: Limite de endereços (429)
- **WHEN** usuário tenta criar mais de 20 endereços em 1 hora
- **THEN** retorna `429`; mostra aviso "Muitos endereços criados. Aguarde um pouco e tente novamente."

### Requirement: Editar endereço

O front-end SHALL permitir editar um endereço com `PUT` (substituição completa) ou `PATCH` (parcial), no mesmo recurso.

#### Scenario: Botão "Editar" em endereço
- **WHEN** usuário clica em "Editar" em um card de endereço
- **THEN** navega para `/account/addresses/{id}/edit` com formulário prefill com dados do endereço

#### Scenario: Carregar dados para edição
- **WHEN** formulário de edição abre
- **THEN** chama `GET /users/me/addresses/{id}` e popula todos os campos

#### Scenario: Formulário de edição com PUT
- **WHEN** usuário altera campos e clica "Salvar Endereço"
- **THEN** envia `PUT /users/me/addresses/{id}` com todos os campos (campo omitido vira `null`)

#### Scenario: Formulário de edição com PATCH
- **WHEN** usuário altera alguns campos e clica "Salvar Endereço"
- **THEN** envia `PATCH /users/me/addresses/{id}` com só os campos alterados

#### Scenario: Endereço atualizado
- **WHEN** `PUT` ou `PATCH` retorna `200` com o endereço atualizado no mesmo `id`
- **THEN** a lista é invalidada e refeita, e o front mostra "Endereço atualizado"

#### Scenario: Endereço não encontrado (404)
- **WHEN** `GET /users/me/addresses/{id}` ou `PUT`/`PATCH` retorna `404`
- **THEN** mostra erro "Endereço não encontrado"; volta para lista

#### Scenario: Validação igual ao criar
- **WHEN** usuário edita formulário
- **THEN** mesmas regras: CEP 8 dígitos, UF 2 letras, campos obrigatórios; botão "Salvar" disabled se inválido

#### Scenario: Erro de rede ao editar
- **WHEN** `PUT`/`PATCH` falha
- **THEN** mostra toast "Erro ao atualizar endereço"; formulário permanece aberto

### Requirement: Deletar endereço com confirmação

O front-end SHALL exigir confirmação antes de deletar endereço.

#### Scenario: Botão "Deletar" em endereço
- **WHEN** usuário clica em "Deletar" em um card ou no modal de detalhes
- **THEN** abre diálogo de confirmação: "Tem certeza que deseja remover este endereço?"

#### Scenario: Confirmar deleção
- **WHEN** usuário clica em "Sim, remover" no diálogo
- **THEN** envia `DELETE /users/me/addresses/{id}`

#### Scenario: Deleção bem-sucedida (204)
- **WHEN** `DELETE /users/me/addresses/{id}` retorna `204`
- **THEN** remove endereço da lista (TanStack Query invalida `['addresses', 'list']`); mostra toast "Endereço removido"

#### Scenario: Endereço em shipment ativo (409)
- **WHEN** `DELETE /users/me/addresses/{id}` retorna `409` (ADDRESS_IN_ACTIVE_SHIPMENT)
- **THEN** mostra alerta: "Este endereço está sendo usado em um envio ativo e não pode ser removido"

#### Scenario: Endereço não encontrado (404)
- **WHEN** `DELETE /users/me/addresses/{id}` retorna `404`
- **THEN** mostra aviso "Endereço já foi removido"; lista é atualizada

#### Scenario: Cancelar deleção
- **WHEN** usuário clica em "Cancelar" no diálogo
- **THEN** fecha diálogo; endereço permanece

#### Scenario: Erro de rede ao deletar
- **WHEN** `DELETE /users/me/addresses/{id}` falha
- **THEN** mostra toast "Erro ao remover endereço. Tente novamente."; diálogo permanece aberto

### Requirement: Validação de CEP com 8 dígitos, sem máscara

O front-end SHALL validar CEP como exatamente 8 dígitos numéricos, sem hiphen ou espaço.

#### Scenario: CEP com 8 dígitos válido
- **WHEN** usuário digita `01310100`
- **THEN** campo aceita; sem erro

#### Scenario: CEP com menos de 8 dígitos
- **WHEN** usuário digita `0131010` (7 dígitos)
- **THEN** mostra aviso "CEP deve ter 8 dígitos"

#### Scenario: CEP com máscara (com hiphen)
- **WHEN** usuário cola ou tenta digitar `01310-100` (com hiphen)
- **THEN** (opção 1) campo rejeita; (opção 2) aceita e remove hiphen automaticamente

#### Scenario: CEP com espaço
- **WHEN** usuário tenta `01310 100` (com espaço)
- **THEN** campo rejeita ou remove espaço automaticamente

#### Scenario: CEP com caracteres não-numéricos
- **WHEN** usuário tenta digitar letras ou símbolos
- **THEN** campo rejeita (input `inputMode="numeric"` ou `type="number"`)

#### Scenario: CEP vazio
- **WHEN** campo CEP deixado em branco
- **THEN** marca como obrigatório; botão "Salvar" disabled

### Requirement: Validação de UF (2 letras maiúsculas)

O front-end SHALL validar UF como exatamente 2 letras maiúsculas quando `country=BR`.

#### Scenario: UF válido
- **WHEN** usuário digita `SP` (2 letras maiúsculas)
- **THEN** campo aceita; sem erro

#### Scenario: UF com letras minúsculas
- **WHEN** usuário digita `sp`
- **THEN** (opção 1) campo rejeita; (opção 2) converte automaticamente para `SP`

#### Scenario: UF com 1 letra apenas
- **WHEN** usuário digita `S`
- **THEN** mostra aviso "UF deve ter 2 letras"

#### Scenario: UF com 3 ou mais letras
- **WHEN** usuário tenta digitar `SPP`
- **THEN** campo rejeita a terceira letra (maxLength=2); ou mostra erro

#### Scenario: UF com números
- **WHEN** usuário tenta `S1`
- **THEN** campo rejeita números

#### Scenario: País não é BR
- **WHEN** usuário muda `country` para outro (ex.: "US")
- **THEN** validação de UF é relaxada (campo aceita formato diferente); rótulo muda para "State" ou similar

#### Scenario: UF vazio quando obrigatório
- **WHEN** `country=BR` e campo UF deixado em branco
- **THEN** marca como obrigatório; botão "Salvar" disabled

### Requirement: Cache e invalidação com TanStack Query

O front-end SHALL gerenciar cache de endereços e invalidar automaticamente após mutação.

#### Scenario: Primeira carga de lista
- **WHEN** usuário acessa `/account/addresses`
- **THEN** TanStack Query faz `GET /users/me/addresses` e armazena em cache `['addresses', 'list']`

#### Scenario: Segunda carga usa cache
- **WHEN** usuário navega para outra página e volta para `/account/addresses`
- **THEN** cache é reutilizado (sem fazer request); dados aparecem instantaneamente

#### Scenario: Após criar endereço
- **WHEN** `POST /users/me/addresses` completa com `201`
- **THEN** cache `['addresses', 'list']` é invalidado; query é refeita; novo endereço aparece na lista

#### Scenario: Após editar endereço
- **WHEN** `PUT`/`PATCH /users/me/addresses/{id}` completa com `200`
- **THEN** cache `['addresses', 'list']` é invalidado; novo `id` é cacheado; lista refaz query

#### Scenario: Após deletar endereço
- **WHEN** `DELETE /users/me/addresses/{id}` completa com `204`
- **THEN** cache `['addresses', 'list']` é invalidado; endereço desaparece da lista (ou lista refaz query)

#### Scenario: Stale time
- **WHEN** cache é criado
- **THEN** dados não são considerados "stale" por 5 min (ou configurável); depois disso, próxima navegação refaz query

#### Scenario: Refetch em error
- **WHEN** query falha com erro de rede
- **THEN** TanStack Query retenta com backoff exponencial (ex.: 1 s, 2 s, 4 s)
