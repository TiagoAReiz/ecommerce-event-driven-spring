# syntax=docker/dockerfile:1

# ---------- build ----------
FROM node:24-alpine AS build
WORKDIR /build

# As dependencias vem antes do codigo: enquanto o lock nao muda, esta camada
# fica em cache e o rebuild nao volta na rede.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# A base da API entra no bundle em tempo de build: e um app estatico, nao ha
# servidor para ler variavel de ambiente depois.
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# ---------- runtime ----------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
