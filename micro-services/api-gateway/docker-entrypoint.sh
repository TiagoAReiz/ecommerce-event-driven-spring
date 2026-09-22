#!/bin/sh
set -e

# Par de chaves de assinatura. Nao vai para o repositorio, entao num clone novo
# ele nao existe: sem gerar aqui, a stack nao sobe sem um passo manual antes.
# Em producao as chaves sao montadas de fora e este bloco nao roda.
if [ ! -f keys/jwt-private.pem ]; then
    echo "AVISO: gerando par RSA descartavel em keys/ -- as sessoes morrem no proximo recreate deste container."
    mkdir -p keys
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/jwt-private.pem
    openssl rsa -pubout -in keys/jwt-private.pem -out keys/jwt-public.pem
fi

exec java -jar app.jar
