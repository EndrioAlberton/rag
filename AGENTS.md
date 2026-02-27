# AGENTS.md

## Cursor Cloud specific instructions

### Project overview

RAG (Retrieval-Augmented Generation) Chatbot — a Java 21 / Quarkus 3.31.4 backend with a Vue.js 3 / Vuetify frontend. See `README.md` and `frontend/README.md` for full details.

### Services

| Service | Port | Notes |
|---------|------|-------|
| Quarkus backend | 8081 | `./mvnw quarkus:dev -Dquarkus.console.enabled=false` |
| PostgreSQL+PGVector, Redis | auto | Started automatically by Quarkus Dev Services via Docker/Testcontainers |
| Frontend (Vite dev) | 5173 | `cd frontend && npm run dev` (optional — built frontend is served by Quarkus) |

### Key caveats

- **Docker is required.** Quarkus Dev Services uses Testcontainers to start PostgreSQL+PGVector and Redis. The Docker daemon must be running before `./mvnw quarkus:dev`. In the cloud VM, start it with `sudo dockerd &>/tmp/dockerd.log &` and wait a few seconds.
- **Docker in Docker (cloud VM).** The VM runs inside a Firecracker container. Docker needs `fuse-overlayfs` storage driver and `iptables-legacy`. These are configured during initial setup.
- **`OPENAI_API_KEY` is required.** The backend uses OpenAI (gpt-4o-mini + text-embedding-3-small). Without this key, the app will start but AI endpoints will fail.
- **Frontend build before Quarkus dev.** Run `cd frontend && npm install && npm run build` before `./mvnw quarkus:dev` so the UI is served from `src/main/resources/META-INF/resources/`.
- **Port conflict during tests.** Do not run `./mvnw verify` while Quarkus dev mode is running on port 8081 — the test instance will fail to bind.
- **No ESLint/lint configuration** exists in this project.
- **Tests are integration tests only** (`*IT.java`) — they run via `./mvnw verify`, not `./mvnw test`. They require Docker for Testcontainers and `OPENAI_API_KEY` for AI endpoints.
- **Startup takes ~60-90s** on first run due to Docker image pulls (PostgreSQL+PGVector, Redis, Testcontainers Ryuk) and document ingestion (scraping 19 URLs + local files).
- **Authentication.** The frontend login requires an external "Orion Users" service at `http://localhost:8080` which is not part of this repo. The RAG chatbot API endpoints (`/ai/chatbot`, `/ai/ask`, `/ai/memory`) can be tested directly via curl without authentication.
