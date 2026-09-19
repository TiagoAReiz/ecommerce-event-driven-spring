#!/bin/sh
# Registra cada conector de /connectors e espera ele ficar RUNNING.
# O conector falha enquanto o servico nao rodou o Flyway (publicacao ainda
# nao existe); reiniciar ate dar certo e mais simples que orquestrar a subida.
set -eu
CONNECT=http://connect:8083

for file in /connectors/*.json; do
  name=$(basename "$file" .json)
  curl -fsS -X PUT -H "Content-Type: application/json" \
       --data @"$file" "$CONNECT/connectors/$name/config" > /dev/null

  for attempt in $(seq 1 30); do
    state=$(curl -fsS "$CONNECT/connectors/$name/status" | grep -o '"state":"[A-Z]*"' | tail -1)
    case "$state" in
      *RUNNING*) echo "$name: RUNNING"; break ;;
      *FAILED*)  curl -fsS -X POST "$CONNECT/connectors/$name/restart?includeTasks=true&onlyFailed=true" > /dev/null ;;
    esac
    [ "$attempt" = 30 ] && { echo "$name: nao subiu"; exit 1; }
    sleep 10
  done
done
