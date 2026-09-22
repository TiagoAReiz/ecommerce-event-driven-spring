# Front da loja

Vitrine e área da loja em Next.js, consumindo o `api-gateway` dos microsserviços.

Next.js 16 (App Router) + TypeScript, TanStack Query e Tailwind. Identidade branco e azul,
com os tokens de cor em `src/app/globals.css` — nenhuma tela inventa cor própria.

## O que roda no servidor e o que roda no navegador

A vitrine pública (home, listagem e detalhe de produto) é renderizada no servidor: é a parte
que precisa aparecer em busca, e os dados já saem no HTML. O servidor busca o conteúdo e a
tela recebe como `initialData`, então o TanStack Query só revalida depois, sem piscar
esqueleto na frente do usuário.

Tudo que depende de sessão fica no navegador, porque o token é um Bearer guardado lá — não há
cookie para o servidor ler.

## A porta 3000 não é opcional

O gateway só aceita **uma** origem no CORS e só devolve o login para ela: `app.front-url`,
que por padrão é `http://localhost:3000`. Rodar o front em outra porta quebra o login e todas
as chamadas. Para mudar, mude `FRONT_URL` no ambiente do gateway junto.

## Rodar

Tudo junto, a partir da raiz do repositório — backend, Kafka, Debezium e a vitrine:

```bash
docker compose up -d --build
```

Sobe sem preparo nenhum: sem `.env`, sem gerar chave, sem criar banco. O que falta são só os
segredos da tabela abaixo.

Só o front, contra um backend já de pé:

```bash
npm install
npm run dev     # http://localhost:3000
```

## Segredos que faltam

| Onde | Variável | Sem ela |
|---|---|---|
| `micro-services/api-gateway/.env` ou o ambiente | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | não há login: o compose sobe com um marcador e o Google recusa a autorização. É o único segredo realmente necessário para usar a loja |
| `.env` na raiz ou o ambiente | `MP_ACCESS_TOKEN`, `MP_PUBLIC_KEY` | o pagamento roda em modo `fake`: o fluxo inteiro funciona e aprova, mas nada vai ao Mercado Pago. O cartão real exige a `publicKey`, porque a tokenização acontece no navegador |
| `.env` na raiz ou o ambiente | `STORE_OWNER_EMAIL` | vale `dono@loja.local`. O dono da loja é quem logar com este e-mail |
| ambiente do front | `NEXT_PUBLIC_API_URL` | usa `http://localhost:8080`. É o endereço que o **navegador** usa, e entra no bundle no build |
| ambiente do front | `INTERNAL_API_URL` | usa o mesmo do navegador. É o endereço que o **servidor do Next** usa; no compose vale `http://api-gateway:8080` |

No Google Cloud, o redirect autorizado do OAuth é
`http://localhost:8080/login/oauth2/code/google` — do **gateway**, não do front. O gateway
devolve o token para `http://localhost:3000/callback#token=...`.

> As chaves de assinatura do gateway não são versionadas. Se não existirem, o container gera um
> par descartável no primeiro boot e avisa no log: as sessões morrem quando o container é
> recriado. Em produção, monte chaves de verdade em `keys/`.

## Como o token funciona aqui

O login volta com o token no fragmento da URL (`#token=`), que nunca vai ao servidor nem ao
`Referer`. A tela de callback guarda o token, limpa a barra de endereços e a sessão passa a
viajar no header `Authorization`. Não há cookie, e o token vive no `sessionStorage`: morre com
a aba. A renovação é deslizante, disparada uns minutos antes de expirar.

## Organização

```
src/
  app/          rotas do App Router; a vitrine renderiza no servidor
  components/   kit visual, casca de navegacao e guarda de sessao
  lib/          cliente HTTP, sessao, formatadores
  features/
    catalog/    vitrine publica
    account/    login, perfil e enderecos
    checkout/   carrinho, checkout e pagamento
    orders/     pedidos, envios e avaliacoes
    store/      area do dono da loja
```

Cada área consome as rotas de `docs/api-contracts.md`. As propostas que originaram cada tela
estão em `openspec/changes/add-front-*`.
