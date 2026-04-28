# Plano — Agentes por professor + RAG por disciplina + acesso via Moodle

Objetivo: professor (owner) controla **contexto/prompt** e **arquivos** do RAG para um “agente do professor”. Alunos da disciplina acessam esse agente dentro do Moodle para tirar dúvidas.

---

## Visão geral (ideia central)

- **Agent** = “assistente do professor” ligado a uma **disciplina** (ex: SSI / BD / Web).
- **Professor**:
  - define **system prompt** (identidade/regras do assistente)
  - gerencia **documentos** (PPC, FAQ, PDFs, slides, etc.)
  - dispara **reindex/ingestão** do RAG
- **Aluno**:
  - abre chat do **Agent da disciplina**
  - recebe resposta baseada na **base daquela disciplina** (sem vazar conteúdo de outras)
- **Moodle**:
  - aponta (link/iframe) para o Agent correto
  - (opcional) passa identidade/role (Teacher/Learner) via integração

---

## Requisitos principais

- **Multi-agentes (multi-tenant)**: mesma aplicação suporta vários Agents.
- **Isolamento de base**: busca vetorial sempre filtrada por `agentId`.
- **Controle de acesso**:
  - Teacher/Owner: CRUD Agent + CRUD documentos + reindex
  - Learner: chat somente
- **Integração Moodle**: alunos entram pelo Moodle já no Agent certo (por disciplina).

---

## Modelo de dados (backend)

### Tabela `agent`
- `id` (uuid)
- `name` (nome do agente; ex: “SSI — PPC+FAQ”)
- `moodle_course_id` (id do curso/disciplina no Moodle) **ou** `discipline_code`
- `owner_user_id` (professor)
- `system_prompt` (texto do prompt do professor)
- `created_at`, `updated_at`

### Tabela `agent_document`
- `id` (uuid)
- `agent_id` (fk)
- `filename`
- `content_type`
- `size`
- `storage_path` (onde arquivo fica)
- `uploaded_by`
- `uploaded_at`

### Embeddings / chunks (pgvector)
- adicionar `agent_id` em chunks/embeddings (ou tabela nova por agent)
- **regra**: busca semântica sempre `WHERE agent_id = :agentId`

---

## Storage de documentos (2 opções)

### Opção A — filesystem (simples p/ TCC)
- salvar em `data/agents/{agentId}/...`
- fácil backup, fácil inspeção

### Opção B — Postgres (bytea)
- tudo no DB
- pior para arquivos grandes

Recomendado: **Opção A**.

---

## Endpoints (API) — professor

### Agent
- `POST /agents` — cria Agent
- `GET /agents/{id}` — detalhes
- `PUT /agents/{id}` — edita `systemPrompt`, nome, curso/disciplina
- `DELETE /agents/{id}` — remove Agent (opcional)

### Documentos
- `POST /agents/{id}/documents` — upload (multipart)
- `GET /agents/{id}/documents` — lista
- `DELETE /agents/{id}/documents/{docId}` — remove
- `POST /agents/{id}/reindex` — reprocessa docs do Agent (re-embed)

---

## Endpoints (API) — aluno (chat)

- `POST /agents/{agentId}/users/{userId}/conversations` — cria conversa
- `POST /agents/{agentId}/conversations/{conversationId}/messages` — envia pergunta e recebe resposta (stream)

Obs: conversa/mensagens precisam guardar `agentId` (pra não misturar histórico entre Agents).

---

## Fluxo RAG (por Agent)

1. Aluno envia pergunta para `agentId`.
2. Backend gera embedding da pergunta.
3. Backend busca top-k chunks **somente daquele agent**:
   - `SELECT ... WHERE agent_id = :agentId ORDER BY distance LIMIT k`
4. Backend monta prompt:
   - `system = agent.system_prompt` (fallback para default)
   - `user = "Histórico... Contexto (RAG)... pergunta..."`
5. Modelo responde (stream).

---

## Autorização (Teacher vs Learner)

### Caminho A — Integração Moodle real (LTI 1.3)
- Moodle lança ferramenta (tool) via OIDC/JWT.
- Token traz:
  - `roles` (Instructor/Learner)
  - `context` (courseId)
- Backend decide permissões automaticamente.

### Caminho B — Integração simples (token assinado) — bom p/ protótipo
- Admin/professor gera URL com token:
  - payload: `agentId`, `role=teacher|student`, `exp`
- Moodle só embute link/iframe.
- Backend valida assinatura, libera UI correta.

Recomendado p/ TCC (MVP): **Caminho B**, e documentar **Caminho A** como evolução.

---

## Frontend (UI)

### Modo professor (owner/teacher)
- tela “Config do Agent”
  - editor de `system_prompt`
  - upload/lista/remove documentos
  - botão “Reindex”
  - status da última ingestão

### Modo aluno (learner)
- só chat
- indicador do Agent (disciplina) atual

---

## Integração com Moodle (prático)

### MVP (sem LTI)
- Moodle adiciona **URL externa** para:
  - `https://seu-rag/app?agentId=...`
  - ou `https://seu-rag/moodle/launch?token=...`

### Evolução (LTI 1.3)
- Moodle Tool Configuration
- SSO + roles + course mapping automático

---

## Entrega incremental (pra não travar)

### MVP 1 — Agent + prompt por agent + chat por agent
- criar `Agent`
- conversas/mensagens guardam `agentId`
- prompt vem do Agent

### MVP 2 — upload docs + embeddings por agent
- upload documentos
- reindex
- busca vetorial filtrada por `agentId`

### MVP 3 — painel professor
- UI teacher para prompt + docs + reindex

### MVP 4 — Moodle
- MVP: token assinado (launch)
- evolução: LTI 1.3

---

## Observações do projeto atual (ponto de partida)

- Hoje o prompt está fixo em `LangChainAIService.DEFAULT_SYSTEM_MESSAGE`.
- Hoje ingestão lê docs de um diretório (logs mostram `'/work/rag'`).
- Para suportar “um agente por professor/disciplina” no mesmo backend, precisa:
  - persistir `system_prompt` por agent
  - persistir docs por agent
  - filtrar embeddings por `agentId`

