# MCP Server - Course-Aware Q&A

O backend Quarkus inclui um servidor MCP (Model Context Protocol) integrado via **quarkus-mcp-server-http**, permitindo que plataformas (Claude, Cursor) acessem ferramentas RAG para Q&A contextualizado por curso.

## Tecnologias

- **Java 21** + **Quarkus 3.31.4**
- **LangChain4j** (embedding, RAG)
- **quarkus-mcp-server-http** (HTTP/SSE e Streamable HTTP)

## Endpoints

| Transporte | URL |
|------------|-----|
| SSE (legado) | `http://localhost:8081/mcp/sse` |
| Streamable HTTP | `http://localhost:8081/mcp` |

## Ferramentas disponíveis

### `retrieve_course_context`

Recupera conteúdo semanticamente relevante do curso para uma pergunta em linguagem natural.

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `query` | string | Pergunta ou tópico em linguagem natural |
| `session` | string (opcional) | Identificador de sessão/curso (default: "default") |
| `maxResults` | number (opcional) | Máximo de chunks de contexto (1-20, default: 5) |

### `ask_course_question`

Faz uma pergunta sobre o conteúdo do curso. O backend RAG recupera contexto e gera a resposta.

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `query` | string | Pergunta sobre o curso |
| `session` | string (opcional) | Identificador de sessão/curso (default: "default") |

## Configuração no Cursor

Adicione ao `~/.cursor/mcp.json` (ou equivalente):

```json
{
  "mcpServers": {
    "rag": {
      "url": "http://localhost:8081/mcp/sse"
    }
  }
}
```

Ou para Streamable HTTP (se suportado):

```json
{
  "mcpServers": {
    "rag": {
      "url": "http://localhost:8081/mcp"
    }
  }
}
```

**Nota:** O backend Quarkus deve estar rodando (`./mvnw quarkus:dev`) antes de conectar o Cursor.

## Requisitos

- Docker (para PostgreSQL+PGVector e Redis via Dev Services)
- `OPENAI_API_KEY` configurado
