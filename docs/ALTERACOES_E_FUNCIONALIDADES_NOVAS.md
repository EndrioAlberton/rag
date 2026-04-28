# Alterações e funcionalidades novas (projeto TCC)

Documento resumo das principais **novas funcionalidades** e **mudanças** realizadas no sistema RAG (backend Quarkus/Java) e no frontend (Vue).

---

## 1) Qualidade de resposta (RAG) + “não inventar”

- **Fontes consultadas (formatadas)**
  - Antes: resposta podia anexar um “dump” grande do contexto recuperado.
  - Agora: no final da resposta aparece apenas lista limpa **“Fontes consultadas”**.
  - Implementação: utilitário `RagSourceFormatter` extrai fontes a partir de linhas `[Fonte: ...]`.

- **Handoff (suporte humano) quando não há base**
  - Regra: se não há contexto recuperado no RAG (sem trechos), o sistema deve **orientar suporte humano**.
  - Contato:
    - E-mail: `comunicacao@poa.ifrs.edu.br`
    - Telefone: `(51) 3930-6002`

- **Bloqueio de “resposta genérica” quando não há contexto**
  - Problema: modelo podia responder com “conhecimento geral” mesmo sem evidência na base e só depois avisar que não encontrou.
  - Agora: se `ragResult` está vazio → **não chama o LLM**. Retorna direto a mensagem de “não encontrei informação suficiente” + suporte humano.
  - Arquivos: `AskQuestionUseCase` e `ChatbotUseCase`.

---

## 2) Auditoria/monitoramento (logs) + métricas de dashboard

- **Request logs ampliados**
  - Em `request_logs`, foram adicionados campos para:
    - `rag_score` (score do RAG)
    - `handoff_required` (boolean)
    - `handoff_reason` (texto)
  - Objetivo: medir qualidade do RAG, lacunas de base e casos de encaminhamento.

- **Dashboard de métricas**
  - Endpoint backend: `GET /ai/dashboard/metrics`
  - Métricas:
    - total de requisições
    - total de conversas
    - total de handoffs
    - total de likes/dislikes
  - Frontend: rota `/dashboard` com visualização dos números.

---

## 3) Avaliação de respostas (Like/Dislike) — por pergunta/resposta

- **Interface**
  - Botões **Like** e **Dislike** aparecem em mensagens da IA.
  - Feedback é associado à **última pergunta do usuário** (mesma conversa).

- **Persistência**
  - Tabela `request_log_feedback` guarda feedback e vínculo com `request_logs`.
  - Modelo final: salvar **snapshot**:
    - `user_message` (pergunta)
    - `llm_response` (resposta da IA)
    - `liked` (boolean: `true` like / `false` dislike)
  - Além do vínculo:
    - `request_log_id`, `user_id`, `conversation_id`

- **Endpoints**
  - `POST /ai/feedback` registra feedback (LIKE/DISLIKE) para a resposta correspondente.

- **Ajuste importante no frontend (histórico)**
  - Problema: ao recarregar conversa, mensagens vinham sem `canFeedback` e sem `_userMessage`, então botões não apareciam.
  - Fix: `loadHistory()` reconstrói metadados (última pergunta do user → `_userMessage`) e habilita `canFeedback` em respostas carregadas do histórico.
  - Arquivo: `frontend/src/components/ChatInterface.vue`.

---

## 4) Origem das fontes (links oficiais IFRS)

- **Mapeamento de arquivos para links oficiais**
  - Quando a fonte for PPC/Anexo/FAQ institucional, a saída “Fontes consultadas” deve mostrar o **link oficial**:
    - PPC SSI: `https://poa.ifrs.edu.br/attachments/article/2808/PPC_SSI_POA_Final_Aprovado.docx.pdf`
    - Anexo Resolução: `https://poa.ifrs.edu.br/attachments/article/2808/Res_2025_Anexo.pdf`
    - Dúvidas e Respostas: `https://poa.ifrs.edu.br/index.php/duvidas-respostas`
  - Implementação: `RagSourceFormatter` formata a fonte e cria link quando reconhece o nome.

---

## 5) Ingestão e “de onde vem as fontes”

- **Metadados do embedding**
  - Cada chunk gravado no pgvector salva `metadata.source`:
    - para arquivo local: nome do arquivo (ex.: `PPC_SSI...pdf`)
    - para URL: a própria URL
  - Tabela: `embeddings` (coluna `metadata` JSON).

- **URLs configuradas para scrape no startup**
  - Config: `rag.scrape.urls` em `src/main/resources/application.properties`
  - Componente: `ScrapeUrlsStartupObserver` executa ingestão automática no startup quando lista não vazia.
  - Observação: se uma URL já foi ingerida, ela permanece no DB até apagar embeddings/volume ou deletar linhas do `embeddings`.

---

## 6) UX/Auth (login) — ajustes no frontend

- **Regras de senha no frontend**
  - Ajuste para não exigir complexidade (maiúscula/número/símbolo) quando o backend não exige.
  - Arquivos: `frontend/src/components/Login.vue` e `frontend/src/components/Register.vue`.

---

## 7) Arquivos/documentos gerados para o RAG

- **Lista completa de pré‑requisitos (PPC)**
  - Arquivo base: `docs/SSI_PPC_PREREQUISITOS_LISTA_COMPLETA.md`
  - PDF gerado para ingestão: `docs/SSI_PPC_PREREQUISITOS_LISTA_COMPLETA.pdf`

---

## 8) Migrações de banco (Flyway)

- As mudanças acima foram implementadas via migrations em `src/main/resources/db/migration/`.
- Observação prática: em ambientes já aplicados, a abordagem correta é **criar nova migration**. Em reset total (apagando volume), pode-se ajustar a migration anterior e recriar o DB do zero.

