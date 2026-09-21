# Front da loja

Vitrine e área da loja em React, consumindo o `api-gateway` dos microsserviços.

Vite + React + TypeScript, React Router, TanStack Query e Tailwind. Identidade branco e azul,
com os tokens de cor em `src/index.css` — nenhuma tela inventa cor própria.

## A porta 3000 não é opcional

O gateway só aceita **uma** origem no CORS e só devolve o login para ela: `app.front-url`,
que por padrão é `http://localhost:3000`. Rodar o front em outra porta quebra o login e todas
as chamadas. Para mudar, mude `FRONT_URL` no ambiente do gateway junto.

## Rodar

```bash
npm install
npm run dev     # http://localhost:3000
```

Com o backend de pé (`docker compose up -d` em `micro-services/`). Ou suba tudo junto, front
incluído:

```bash
cd ../micro-services
docker compose up -d --build
```

## Segredos que faltam

O projeto sobe e funciona sem nenhum deles, com as ressalvas abaixo.

| Onde | Variável | Sem ela |
|---|---|---|
| `micro-services/api-gateway/.env` | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | não há login: o Google recusa a autorização. É o único segredo realmente necessário para usar a loja |
| `micro-services/.env` | `STORE_OWNER_EMAIL` | o serviço `user` não sobe. O dono da loja é quem logar com este e-mail |
| `micro-services/.env` | `MP_ACCESS_TOKEN`, `MP_PUBLIC_KEY` | o pagamento roda em modo `fake`: o fluxo inteiro funciona e aprova, mas nada vai ao Mercado Pago. O cartão real exige a `publicKey`, porque a tokenização acontece no navegador |
| `front/.env` | `VITE_API_URL` | usa `http://localhost:8080`, que é o padrão local do gateway |

No Google Cloud, o redirect autorizado do OAuth é
`http://localhost:8080/login/oauth2/code/google` — do **gateway**, não do front. O gateway
devolve o token para `http://localhost:3000/callback#token=...`.

## Como o token funciona aqui

O login volta com o token no fragmento da URL (`#token=`), que nunca vai ao servidor nem ao
`Referer`. A tela de callback guarda o token, limpa a barra de endereços e a sessão passa a
viajar no header `Authorization`. Não há cookie, e o token vive no `sessionStorage`: morre com
a aba. A renovação é deslizante, disparada uns minutos antes de expirar.

## Organização

```
src/
  app/          casca de navegacao, rotas e guardas de sessao e papel
  components/   kit visual compartilhado
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
