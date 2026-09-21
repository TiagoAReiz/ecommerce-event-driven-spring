# Spec Delta: Front-end — Perfil do Usuário

## Purpose

Implementa visualização e edição do perfil pessoal do usuário, incluindo validação de CPF e telefone, e exclusão de conta com confirmação explícita.

## ADDED Requirements

### Requirement: Visualizar perfil completo

O front-end SHALL exibir todos os dados do perfil do usuário autenticado, incluindo contagem de endereços.

#### Scenario: Página de perfil renderiza
- **WHEN** usuário autenticado acessa `/account` ou `/account/profile`
- **THEN** a página chama `GET /users/me` e exibe: nome, email, CPF (com máscara para exibição), telefone, foto de perfil, contagem de endereços cadastrados, datas de criação/atualização

#### Scenario: Dados carregam com sucesso
- **WHEN** `GET /users/me` retorna `200`
- **THEN** todos os campos são populados; botão "Editar" fica visível

#### Scenario: Usuário removido (404)
- **WHEN** `GET /users/me` retorna `404` (token aponta para usuário deletado)
- **THEN** a app redireciona para `/login` com mensagem "Sua conta foi removida"

#### Scenario: Erro de rede no carregamento
- **WHEN** `GET /users/me` falha com timeout ou `5xx`
- **THEN** mostra skeleton/loader; retenta automaticamente com TanStack Query

#### Scenario: Foto de perfil (opcional)
- **WHEN** usuário tem `photoUrl` preenchido
- **THEN** mostra a imagem em avatar circular (ex.: foto do Google)

#### Scenario: Sem foto de perfil
- **WHEN** `photoUrl` é null ou vazio
- **THEN** mostra avatar genérico (letra inicial do nome ou ícone de usuário)

### Requirement: Editar dados pessoais

O front-end SHALL permitir edição de `name`, `cpf`, `phone` e `photoUrl`, com validações locais.

#### Scenario: Abrir modo de edição
- **WHEN** usuário clica em botão "Editar Perfil"
- **THEN** redireciona para `/account/profile/edit` ou abre formulário in-place; campos ficam editáveis

#### Scenario: Campos editáveis
- **WHEN** formulário de edição renderiza
- **THEN** os campos `name`, `cpf`, `phone`, `photoUrl` podem ser alterados; `email` e `googleSub` **não são exibidos** ou estão disabled com aviso "Controlado pelo Google"

#### Scenario: Validação de CPF local
- **WHEN** usuário digita 11 caracteres no campo CPF
- **THEN** a app valida o CPF com algoritmo de dígitos verificadores (módulo 11); se inválido, mostra erro em vermelho: "CPF inválido"

#### Scenario: Validação de telefone (E.164)
- **WHEN** usuário digita no campo telefone
- **THEN** a app valida contra padrão `^\+[1-9]\d{7,14}$`; se inválido, mostra erro: "Telefone deve estar no formato internacional (+5511999998888)"

#### Scenario: Formulário com erro local
- **WHEN** CPF ou telefone têm erro de validação local
- **THEN** botão "Salvar" fica disabled; erro é exibido sob o campo

#### Scenario: Envio bem-sucedido (200)
- **WHEN** todos os campos são válidos e usuário clica "Salvar"
- **THEN** envia `PATCH /users/me` com `{ name?, cpf?, phone?, photoUrl? }`; se retorna `200`, redireciona para perfil (view mode) com toast "Perfil atualizado"

#### Scenario: CPF duplicado (409)
- **WHEN** `PATCH /users/me` retorna `409 DUPLICATE_CPF`
- **THEN** mostra erro em vermelho no campo CPF: "Este CPF já está cadastrado"

#### Scenario: Erro de validação no backend (422)
- **WHEN** `PATCH /users/me` retorna `422 INVALID_CPF`
- **THEN** mostra erro: "CPF inválido"

#### Scenario: CPF com dígitos verificadores inválidos (local + backend)
- **WHEN** usuário digita CPF com 11 dígitos mas dígitos verificadores errados
- **THEN** validação local rejeita; se passasse (por não ter lógica robusta), backend retorna `422 INVALID_CPF`

#### Scenario: Erro de rede ao salvar
- **WHEN** `PATCH /users/me` falha com timeout ou `5xx`
- **THEN** mostra toast "Erro ao salvar. Tente novamente"; formulário permanece aberto

#### Scenario: Campo vazio
- **WHEN** usuário deixa campo obrigatório vazio
- **THEN** validação local rejeita (ex.: `name` obrigatório); botão "Salvar" disabled

### Requirement: Deletar conta com confirmação

O front-end SHALL exigir confirmação explícita antes de permitir deletar a conta.

#### Scenario: Link "Deletar Conta"
- **WHEN** usuário está na página de perfil
- **THEN** um link/botão "Deletar Conta" é visível na parte inferior (em vermelho/warning)

#### Scenario: Abrir diálogo de confirmação
- **WHEN** usuário clica em "Deletar Conta"
- **THEN** abre modal com aviso em vermelho: "Esta ação é irreversível. Todos os seus dados pessoais serão removidos. Seus pedidos passados permanecerão no sistema como histórico."

#### Scenario: Confirmação textual obrigatória
- **WHEN** o diálogo está aberto
- **THEN** mostra campo de entrada com placeholder "Digite 'Sim, remova minha conta' para confirmar"; botão "Remover Conta" fica disabled até que o texto seja digitado exatamente

#### Scenario: Digitação exata requerida
- **WHEN** usuário digita qualquer coisa diferente de "Sim, remova minha conta"
- **THEN** botão permanece disabled; comparação é case-sensitive

#### Scenario: Digitação correta
- **WHEN** usuário digita "Sim, remova minha conta" exatamente
- **THEN** botão "Remover Conta" fica enabled (vermelho/perigoso)

#### Scenario: Deletar conta bem-sucedido (204)
- **WHEN** usuário clica em "Remover Conta" após digitar confirmação
- **THEN** envia `DELETE /users/me`; se retorna `204`, limpa token, invalida cache, mostra toast "Conta removida" e redireciona para `/login` com mensagem "Sua conta foi removida com sucesso"

#### Scenario: Conta da loja (409 STORE_OWNER_ACCOUNT)
- **WHEN** `DELETE /users/me` retorna `409` com código `STORE_OWNER_ACCOUNT`
- **THEN** mostra alerta: "Você é o proprietário da loja. Sua conta não pode ser removida pela API."

#### Scenario: Pedido em aberto (409 genérico)
- **WHEN** `DELETE /users/me` retorna `409` com mensagem sobre pedido pendente
- **THEN** mostra alerta: "Você possui pedidos em aberto. Finalize-os antes de remover a conta."

#### Scenario: Conta já removida (404)
- **WHEN** `DELETE /users/me` retorna `404`
- **THEN** mostra mensagem "Sua conta já foi removida" e redireciona para `/login`

#### Scenario: Erro de rede ao deletar
- **WHEN** `DELETE /users/me` falha com timeout ou `5xx`
- **THEN** mostra toast "Erro ao remover conta. Tente novamente"; diálogo permanece aberto para retry

#### Scenario: Cancelar deleção
- **WHEN** usuário clica em botão "Cancelar" no diálogo
- **THEN** fecha o diálogo; perfil permanece intacto

### Requirement: Validação de CPF com dígitos verificadores

O front-end SHALL validar CPF localmente usando o algoritmo de módulo 11 da Receita Federal.

#### Scenario: CPF com 11 dígitos válidos
- **WHEN** usuário digita CPF `12345678901` (exemplo fictício válido)
- **THEN** validação passa; sem erro de formato

#### Scenario: CPF com menos de 11 dígitos
- **WHEN** usuário digita menos de 11 dígitos (ex.: `123456789`)
- **THEN** mostra erro: "CPF deve ter 11 dígitos"

#### Scenario: CPF com mais de 11 dígitos
- **WHEN** usuário digita mais de 11 dígitos
- **THEN** campo rejeita (input com `maxLength=11`) ou mostra erro

#### Scenario: CPF com caracteres não-numéricos
- **WHEN** usuário tenta digitar letras ou símbolos
- **THEN** input rejeita (input `inputMode="numeric"` ou `type="number"`); ou aceita e remove caracteres não-numéricos

#### Scenario: Dígitos verificadores inválidos
- **WHEN** usuário digita CPF com 11 dígitos corretos em formato, mas verificadores errados (ex.: `12345678999`)
- **THEN** validação de módulo 11 falha; mostra erro: "CPF inválido"

#### Scenario: Validação é executada onChange
- **WHEN** usuário digita no campo CPF
- **THEN** erro aparece em tempo real (após sair do campo ou onChange, dependendo do UX design)

### Requirement: Validação de telefone em E.164

O front-end SHALL validar telefone no padrão E.164 internacional.

#### Scenario: Telefone válido Brasil
- **WHEN** usuário digita `+5511999998888`
- **THEN** validação passa (formato E.164 com código BR)

#### Scenario: Telefone sem código de país
- **WHEN** usuário digita `11999998888` (sem +55)
- **THEN** mostra erro: "Inclua o código do país (ex.: +55)"

#### Scenario: Telefone com 0 inválido
- **WHEN** usuário tenta `+550119999988` (duplo 0)
- **THEN** mostra erro ou aceita (dependendo de rigor)

#### Scenario: Comprimento fora do intervalo
- **WHEN** usuário digita `+5` (muito curto) ou `+5511999998888999` (muito longo)
- **THEN** mostra erro: "Telefone deve estar entre 8 e 15 dígitos (sem +)"

#### Scenario: Máscara visual
- **WHEN** usuário digita no campo telefone
- **THEN** (opcional) máscara visual mostra formato `+55 (11) 9 9999-8888`; valor armazenado é sempre sem máscara: `+5511999998888`

### Requirement: Foto de perfil (opcional)

O front-end SHALL permitir upload ou URL de foto de perfil.

#### Scenario: Upload de foto
- **WHEN** usuário clica em avatar para alterar foto
- **THEN** (tbd) abre seletor de arquivo ou input URL; após escolher, faz upload e salva URL em `photoUrl`

#### Scenario: URL externa
- **WHEN** usuário fornece URL de foto (ex.: Google Photos)
- **THEN** `PATCH /users/me` com `photoUrl: "https://..."` é enviado; backend aceita

#### Scenario: Preview antes de salvar
- **WHEN** usuário seleciona foto ou fornece URL
- **THEN** mostra preview da foto no formulário; ainda não foi enviado ao servidor

#### Scenario: Remover foto
- **WHEN** usuário clica em "Remover foto"
- **THEN** envia `PATCH /users/me` com `photoUrl: null`; avatar volta ao padrão genérico

#### Scenario: Falha no upload (5xx)
- **WHEN** upload de foto falha
- **THEN** mostra erro; formulário permanece aberto para retry (sem descartar outros campos editados)
