# Spec Delta: Payment UI

## Propósito

Interface de pagamento com três modalidades integradas ao Mercado Pago: PIX (QR code + código copiável com polling), cartão tokenizado pelo Bricks (número nunca vai ao backend), Checkout Pro (redirecionamento). Tratamento de status, erros específicos e comportamento fake.

## ADDED Requirements

### Requirement: Carregamento de configuração do Mercado Pago

O sistema SHALL buscar `publicKey`, `environment` e `enabledMethods` antes de exibir tela de pagamento.

#### Cenário: GET /payments/config ao abrir tela de pagamento

- **QUANDO** usuário clica "Pagar" após confirmar pedido
- **ENTÃO** GET /payments/config é chamado (público, sem autenticação)
- **E** resposta traz `{publicKey, environment, locale, currency, enabledMethods}`
- **E** se `environment === "sandbox"` ou `"fake"`, UI ajusta (ex.: aviso "Ambiente de teste")
- **E** Bricks é carregado dinamicamente com `publicKey` quando necessário

#### Cenário: Erro ao carregar config

- **QUANDO** GET /payments/config retorna `500` (MP_PUBLIC_KEY não configurada)
- **ENTÃO** aviso: "Não foi possível inicializar pagamento. Tente novamente."
- **E** botão "Tentar Novamente" recarrega GET /payments/config

#### Cenário: Ambiente fake

- **QUANDO** `environment === "fake"`
- **ENTÃO** todas as operações funcionam sem MP real
- **E** PIX: botão "Aprovar no Mercado Pago (Fake)" simula aprovação
- **E** Cartão: qualquer número válido é aceito
- **E** Checkout Pro: redireciona e retorna normalmente

### Requirement: Seleção de método de pagamento

O sistema SHALL prover abas ou dropdown para escolher entre PIX, Cartão ou Checkout Pro.

#### Cenário: Abas de método

- **QUANDO** tela de pagamento carrega
- **ENTÃO** três abas aparecem: "PIX", "Cartão", "Checkout Pro"
- **E** PIX é selecionada por padrão
- **E** conteúdo muda ao clicar em cada aba
- **E** estado da aba selecionada é preservado em sessionStorage

#### Cenário: Métodos desabilitados não aparecem

- **QUANDO** `enabledMethods = ["pix", "credit_card"]` (sem checkout_pro)
- **ENTÃO** apenas 2 abas aparecem
- **E** aba "Checkout Pro" é escondida

### Requirement: Pagamento por PIX

O sistema SHALL exibir QR code, código copiável, link de ticket e polling periódico até aprovação ou expiração.

#### Cenário: Formulário PIX com email e CPF

- **QUANDO** aba PIX é selecionada
- **ENTÃO** form exibe:
  - Email (pré-preenchido do perfil do usuário)
  - CPF (máscara 000.000.000-00; validação de dígito local)
  - Botão "Gerar PIX"
- **E** se usuário não tem email ou CPF, campos ficam vazios e obrigatórios

#### Cenário: Gerar PIX com POST /payments

- **QUANDO** usuário clica "Gerar PIX"
- **ENTÃO** POST /payments é chamado com:
  ```json
  {
    "idOrder": 3301,
    "method": "pix",
    "payer": { "email": "user@ex.com", "identification": { "type": "CPF", "number": "12345678901" } }
  }
  ```
- **E** header `Idempotency-Key` é incluído (gerado ou reutilizado)
- **E** spinner "Gerando PIX..." aparece
- **E** resposta `201` traz:
  ```json
  {
    "id": 9901,
    "status": "pending",
    "detail": { "qrCode": "...", "qrCodeBase64": "...", "ticketUrl": "...", "expiresAt": "2026-09-17T14:33:11Z" }
  }
  ```

#### Cenário: Exibição de QR code

- **QUANDO** POST /payments retorna sucesso com PIX
- **ENTÃO** QR code é exibido grande (200×200px ou maior)
- **E** gerado de `qrCodeBase64` (img src=data:...)
- **E** com fallback: se qrCodeBase64 não funcionar, exibir aviso e link de ticketUrl

#### Cenário: Código copiável

- **QUANDO** PIX é gerado
- **ENTÃO** código (qrCode) é exibido em monospace font
- **E** botão "Copiar" ao lado copia para clipboard
- **E** toast verde "Código copiado!" aparece por 2 segundos
- **E** ao clicar "Copiar", ícone muda de temporariamente (ex.: checkmark)

#### Cenário: Link para app do Mercado Pago

- **QUANDO** PIX é gerado
- **ENTÃO** link "Abrir no Mercado Pago" (ticketUrl) é exibido
- **E** clique abre em aba nova

#### Cenário: Exibição de expiração

- **QUANDO** PIX é gerado
- **ENTÃO** relógio visual mostra "Válido por 30 minutos" ou contagem regressiva
- **E** ao expirar (now > expiresAt), aviso muda para "PIX expirou"
- **E** botão "Gerar Novo PIX" reaparece habilitado

#### Cenário: Acompanhamento do status do PIX

- **QUANDO** PIX é gerado
- **ENTÃO** a tela relê `GET /payments/{id}` a cada 5 segundos, que é leitura barata
- **E** `POST /payments/{id}/sync` é usado só como reconciliação, no máximo uma vez por minuto,
  porque o contrato devolve `429` acima disso
- **E** o acompanhamento para quando o status vira final ou quando `expiresAt` passa
- **E** se `status: "captured"` → tela de confirmação do pedido
- **E** se `status: "failed"` → aviso "PIX não foi pago" com botão "Gerar Novo PIX"

#### Cenário: Parada do polling

- **QUANDO** status muda para `"captured"`, `"failed"` ou `"cancelled"`
- **ENTÃO** polling para imediatamente
- **E** user não é mantido em tela de pagamento indefinidamente

#### Cenário: Rate limit na reconciliação

- **QUANDO** `POST /payments/{id}/sync` devolve `429` com `Retry-After`
- **ENTÃO** a reconciliação espera o tempo indicado antes de tentar de novo
- **E** a leitura periódica de `GET /payments/{id}` continua normalmente, sem aviso na tela

### Requirement: Pagamento por Cartão (Tokenizado)

O sistema SHALL integrar Bricks do Mercado Pago para tokenização segura; número de cartão nunca sai do navegador para backend.

#### Cenário: Carregar Bricks dinamicamente

- **QUANDO** aba Cartão é selecionada pela primeira vez
- **ENTÃO** SDK Bricks do MP é carregado via `<script>`
- **E** se falha carregar (ex.: CDN inacessível), fallback para "Usar Checkout Pro" ou erro claro
- **E** `publicKey` do GET /payments/config é passado para Bricks

#### Cenário: Formulário com Bricks

- **QUANDO** Bricks carrega com sucesso
- **ENTÃO** form é renderizado com:
  - Campo de cartão (gerenciado por Bricks; exibe ícone de bandeira)
  - Campo de validade (MM/YY)
  - Campo de CVV
  - Campo de nome do titular
  - Email (pré-preenchido)
  - CPF (máscara; validação local)
  - Seleção de parcelamento (dropdown com "1x R$ 724,80", "3x R$ 241,60", etc.)
  - Botão "Pagar com Cartão"
- **E** Bricks gerencia validação de campo (ex.: card number length, CVV format)

#### Cenário: Carregamento de parcelamentos com GET /payments/methods

- **QUANDO** aba Cartão abre
- **ENTÃO** GET /payments/methods?amount=724.80 é chamado (amount = totalCost do pedido)
- **E** resposta traz métodos com `installments[]`
- **E** dropdown exibe opções sem juros (ex.: "1x", "3x", "6x")
- **E** se houver parcelas com juros, exibem com aviso "Inclui juros"

#### Cenário: Tokenização no front com Bricks

- **QUANDO** usuário clica "Pagar com Cartão"
- **ENTÃO** Bricks chama método `tokenize()`
- **E** se validação falha (ex.: CVV inválido), Bricks exibe erro inline
- **E** se tokenização bem-sucedida, token de uso único é retornado

#### Cenário: POST /payments com token

- **QUANDO** token é obtido
- **ENTÃO** POST /payments é chamado com:
  ```json
  {
    "idOrder": 3301,
    "method": "credit_card",
    "token": "ff8080814c11e237...",
    "paymentMethodId": "master",
    "issuerId": "24",
    "installments": 3,
    "payer": { "email": "user@ex.com", "identification": { "type": "CPF", "number": "12345678901" } }
  }
  ```
- **E** header `Idempotency-Key` é incluído
- **E** spinner "Processando..." aparece

#### Cenário: Cartão aprovado (201 captured)

- **QUANDO** POST /payments retorna `201` com `status: "captured"`
- **ENTÃO** exibição muda para confirmação de pagamento
- **E** dados do cartão (brand, last4, installments) são mostrados
- **E** transição automática para tela de confirmação do pedido

#### Cenário: Cartão recusado (201 failed)

- **QUANDO** POST /payments retorna `201` com `status: "failed"` e statusDetail específico
- **ENTÃO** mensagem amigável é exibida baseada em `statusDetail`:
  - `cc_rejected_call_for_authorize` → "Sua operadora recusou. Ligue para seu banco."
  - `cc_rejected_insufficient_amount` → "Saldo insuficiente na conta/limite."
  - `cc_rejected_bad_filled_security_code` → "Código de segurança (CVV) inválido."
  - `cc_rejected_invalid_installments` → "Parcelamento não disponível para este cartão."
- **E** form de cartão permanece para tentar novamente
- **E** botão "Tentar Outro Cartão" limpa Bricks e permite novo número

#### Cenário: Token expirado (410)

- **QUANDO** POST /payments retorna `410` (token válido por ~7 dias no MP)
- **ENTÃO** aviso: "Sessão de pagamento expirou. Gere um novo token."
- **E** botão "Gerar Novo Token" refaz tokenização do Bricks

#### Cenário: CPF inválido (422)

- **QUANDO** POST /payments retorna `422` com indicação de CPF inválido
- **ENTÃO** aviso: "CPF inválido. Verifique e tente novamente."
- **E** campo CPF fica em destaque com borda vermelha

### Requirement: Pagamento por Checkout Pro

O sistema SHALL redirecionar para URL do MP e permitir retorno automático.

#### Cenário: Formulário Checkout Pro simples

- **QUANDO** aba "Checkout Pro" é selecionada
- **ENTÃO** form exibe:
  - Email (pré-preenchido)
  - Resumo de custos
  - Botão "Pagar com Checkout Pro"

#### Cenário: Redirecionamento para initPoint

- **QUANDO** usuário clica "Pagar com Checkout Pro"
- **ENTÃO** POST /payments é chamado com:
  ```json
  {
    "idOrder": 3301,
    "method": "checkout_pro",
    "payer": { "email": "user@ex.com" }
  }
  ```
- **E** resposta traz `detail.initPoint` (URL do MP)
- **E** `window.location.href = initPoint` redireciona para Mercado Pago
- **E** spinner "Redirecionando..." aparece brevemente

#### Cenário: Retorno do Checkout Pro

- **QUANDO** usuário completa pagamento no MP (ou cancela)
- **ENTÃO** MP redireciona de volta para URL de retorno configurada (ex.: `/orders/{id}`)
- **E** GET /orders/{id} é chamado para verificar `payment.status`
- **E** se `status: "captured"`, tela de confirmação é exibida
- **E** se `status: "pending"` ou `"failed"`, aviso sobre status e opção de entrar em contato

### Requirement: Tela de confirmação e transição

O sistema SHALL exibir confirmação de pagamento e permitir ir para acompanhamento do pedido.

#### Cenário: Confirmação após pagamento capturado

- **QUANDO** pagamento (PIX, Cartão ou Checkout Pro) retorna `status: "captured"`
- **ENTÃO** tela exibe:
  - Ícone de checkmark verde
  - "Pagamento confirmado!"
  - Número do pedido
  - Valor pago
  - Método usado (PIX, Mastercard, etc.)
  - Botão "Ver Acompanhamento do Pedido" → `/orders/{id}`
  - Botão "Continuar Comprando" → `/products`

#### Cenário: Confirmação após Checkout Pro com status simulado

- **QUANDO** retorno do Checkout Pro com status incerto
- **ENTÃO** aviso: "Verificando status do pagamento..."
- **E** polling automático de `GET /orders/{id}` até `payment.status` ser `"captured"` ou timeout

### Requirement: Tratamento de erros específicos de pagamento

O sistema SHALL exibir mensagens claras para cada código de erro.

#### Cenário: Pedido não em pending

- **QUANDO** POST /payments retorna `409` (pedido já tem pagamento ou não está em pending)
- **ENTÃO** aviso: "Este pedido já foi pago ou não pode ser pago agora."
- **E** botão "Voltar" vai para `/orders/{id}`

#### Cenário: Valor abaixo do mínimo

- **QUANDO** POST /payments retorna `422` (pedido com valor < mínimo do método)
- **ENTÃO** aviso: "Valor mínimo para PIX é R$ 0.01"
- **E** sugestão: "Complemente o carrinho"

#### Cenário: Rate limit do Mercado Pago

- **QUANDO** POST /payments retorna `429`
- **ENTÃO** aviso: "Muitas tentativas. Aguarde alguns minutos e tente novamente."
- **E** retry automático com exponential backoff

#### Cenário: MP indisponível

- **QUANDO** POST /payments retorna `503` (MP fora do ar)
- **ENTÃO** aviso: "Mercado Pago temporariamente indisponível. Tente novamente em alguns minutos."
- **E** botão "Tentar Novamente" refaz POST com mesma Idempotency-Key

#### Cenário: Timeout na comunicação com MP

- **QUANDO** POST /payments retorna `504` ou timeout local > 15s
- **ENTÃO** aviso: "Tempo esgotado na comunicação. Tente novamente."
- **E** retry automático; se falhar, sugestão de confirmar pagamento com POST /payments/{id}/sync

### Requirement: Sincronização manual de pagamento

O sistema SHALL permitir sincronizar status com MP se webhooks se perderam.

#### Cenário: Botão "Verificar Status" em tela de pagamento pending

- **QUANDO** user voltou e vê pagamento ainda em `pending` há muito tempo
- **ENTÃO** botão "Verificar Status" aparece
- **E** clique faz POST /payments/{id}/sync
- **E** se retornar `status: "captured"`, transição automática para confirmação

## RENAMED Requirements

Nenhuma mudança.
