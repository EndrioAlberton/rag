#!/usr/bin/env bash
# Sobe o stack definido em docker-compose.yml na raiz do repositório.
# A ordem de arranque (Flyway no RAG antes do Orion Users) está garantida pelo Compose:
# orion-users depende de rag com condition: service_healthy.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
exec docker compose up --build "$@"
