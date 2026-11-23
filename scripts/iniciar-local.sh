#!/usr/bin/env bash
# Sobe o Next localmente (Linux/macOS): módulo em 8081 (H2 modo Oracle + dados fictícios) e BFF em 8080.
# Abra http://localhost:8080  — pré-requisito: ./scripts/build.sh
set -euo pipefail
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
mkdir -p "$RAIZ/logs"
"$JAVA" -jar "$RAIZ/backend/next-configuracao/target/next-configuracao.jar" > "$RAIZ/logs/next-configuracao.log" 2>&1 &
PID=$!
trap 'kill $PID 2>/dev/null || true' EXIT
for _ in $(seq 1 60); do curl -fs http://localhost:8081/actuator/health >/dev/null && break; sleep 2; done
echo "next-bff em http://localhost:8080 (Ctrl+C para encerrar)"
"$JAVA" -jar "$RAIZ/backend/next-bff/target/next-bff.jar"
