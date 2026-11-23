#!/usr/bin/env bash
# Build completo do Next (Linux/macOS): front-end (testes + build) e back-end (testes + jars).
# Uso: ./scripts/build.sh [--sem-testes]
set -euo pipefail
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
SEM_TESTES="${1:-}"

cd "$RAIZ/frontend/next-app"
[ -d node_modules ] || npm ci
[ "$SEM_TESTES" = "--sem-testes" ] || npx vitest run
npx vue-tsc --build
npx vite build

cd "$RAIZ/backend"
chmod +x mvnw
if [ "$SEM_TESTES" = "--sem-testes" ]; then ./mvnw -B -DskipTests package; else ./mvnw -B verify; fi

echo
echo "Artefatos:"
echo "  backend/next-configuracao/target/next-configuracao.jar"
echo "  backend/next-bff/target/next-bff.jar (inclui o next-app em /static)"
