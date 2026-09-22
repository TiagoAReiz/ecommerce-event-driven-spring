# syntax=docker/dockerfile:1

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# As dependencias vem antes do codigo de proposito: enquanto o pom nao muda,
# esta camada fica em cache e o rebuild nao volta na rede.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- runtime ----------
# JRE, nao JDK: a imagem final nao precisa de compilador.
FROM eclipse-temurin:25-jre
WORKDIR /app

# Processo sem privilegio. Se alguem escapar da aplicacao, nao cai como root.
RUN useradd --system --create-home --uid 1001 spring

COPY --from=build --chown=spring:spring /build/target/*.jar app.jar
COPY --chown=spring:spring docker-entrypoint.sh ./
# A pasta das chaves nasce aqui, com dono: /app e do root e o processo roda sem
# privilegio, entao ele nao conseguiria criar o diretorio em tempo de execucao.
RUN chmod +x docker-entrypoint.sh && mkdir -p keys && chown spring:spring keys
USER spring

ENTRYPOINT ["./docker-entrypoint.sh"]
