# syntax=docker/dockerfile:1

# ---------- build ----------
FROM node:24-alpine AS build
WORKDIR /build

# As dependencias vem antes do codigo: enquanto o lock nao muda, esta camada
# fica em cache e o rebuild nao volta na rede.
COPY package.json package-lock.json ./
# npm install, e nao npm ci: o lock nasce no Windows e nao lista os binarios
# nativos de Linux (lightningcss, do Tailwind), entao o ci recusa a instalacao.
RUN npm install --no-audit --no-fund

COPY . .

# NEXT_PUBLIC_* e inlinado no bundle em tempo de build: e o endereco que o
# NAVEGADOR usa. O endereco que o servidor do Next usa e lido em tempo de
# execucao (INTERNAL_API_URL), porque de dentro da rede do compose o host e outro.
ARG NEXT_PUBLIC_API_URL=http://localhost:8080
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
RUN npm run build

# ---------- runtime ----------
FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production

# Processo sem privilegio: se alguem escapar da aplicacao, nao cai como root.
RUN addgroup -g 1001 nodejs && adduser -u 1001 -G nodejs -S nextjs

COPY --from=build /build/.next/standalone ./
COPY --from=build /build/.next/static ./.next/static
COPY --from=build /build/public ./public

USER nextjs
EXPOSE 3000
ENV PORT=3000 HOSTNAME=0.0.0.0
CMD ["node", "server.js"]
