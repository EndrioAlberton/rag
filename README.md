# RAG - RAG System with Generative AI

This project implements a RAG (Retrieval-Augmented Generation) system
using Quarkus, LangChain4j and OpenAI to create an intelligent chatbot that
can answer questions based on ingested documents.

## Project Layout

```text
rag/
├── frontend/              # Vue.js 3 + Vuetify frontend
├── src/main/java/         # Quarkus backend (Java 21)
├── src/main/resources/
│   ├── rag/               # Sample documents for ingestion
│   └── db/migration/      # Flyway migrations (PostgreSQL)
├── docs/                  # Documentation (MCP, TestPlan)
└── pom.xml
```

The `src/main/resources/rag` directory contains sample documents for
ingestion and system testing. You can add your own documents to this
directory to expand the chatbot's knowledge.

## 🛠️ Technologies Used

- **Java 21** - Programming language
- **Quarkus 3.31.4** - Framework for cloud-native Java applications
- **LangChain4j** - Framework for AI integration
- **OpenAI** - LLM (gpt-4o-mini) and embeddings (text-embedding-3-small)
- **PostgreSQL + PGVector** - Vector database for embeddings (via LangChain4j)
- **Redis** - Cache and memory management
- **Maven** - Dependency management

## 📋 Prerequisites

- Java 21 or higher
- Maven 3.8+
- Docker (for PostgreSQL+PGVector and Redis via Quarkus Dev Services)
- **OPENAI_API_KEY** - Required for AI endpoints

## 🚀 Installation and Configuration

### 1. Environment

```bash
# Required: OpenAI API key for chat and embeddings
export OPENAI_API_KEY=your-openai-api-key

# Clone the repository
git clone https://github.com/rodrigoprestesmachado/rag.git
cd rag
```

## 🔧 Local Execution

### Development Mode

**⚠️ Important:** Before running Quarkus in dev mode, you need to build the frontend if you want to use the web interface served by Quarkus. Frontend environment variables (such as `VITE_GOOGLE_CLIENT_ID`) are embedded in the code during the build.

#### 1. Frontend Build (Required for web interface)

```bash
# Navigate to the frontend directory
cd frontend

# Configure environment variables (create .env file if it doesn't exist)
# See frontend/README.md for details on required variables

# Install dependencies (if you haven't already)
npm install

# Build the frontend
npm run build
```

**Note:** The build generates static files in `src/main/resources/META-INF/resources/` that will be served by Quarkus. If you modify environment variables in `frontend/.env`, you will need to do a new build for changes to take effect.

#### 2. Run Quarkus in Dev Mode

```bash
# Return to project root
cd ..

# Run the application in development mode
./mvnw quarkus:dev
```

***Note:*** PostgreSQL (with PGVector extension) and Redis are started automatically via Quarkus Dev Services. Docker must be installed and running for Quarkus to create and manage these containers.

### User Interface

If you want to test the chat interface, simply press the `w` key in the
terminal when the application is running and Quarkus will open the
web interface at: <http://localhost:8081/>.

### Production Mode

Package and run the application:

```bash
# Compile
./mvnw package

# Run JAR
java -jar target/quarkus-app/quarkus-run.jar

# Or create and run uber-jar
./mvnw package -Dquarkus.package.jar.type=uber-jar
java -jar target/*-runner.jar
```

## 🐳 Docker Execution

### Option 1: JVM Executable

```bash
# Build the application
./mvnw package

# Build Docker image
docker build -f src/main/docker/Dockerfile.jvm -t rag:jvm .

# Run container (requires OPENAI_API_KEY and PostgreSQL/Redis URLs)
docker run -i --rm -p 8081:8081 \
  -e OPENAI_API_KEY=$OPENAI_API_KEY \
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/rag_db \
  -e QUARKUS_DATASOURCE_REACTIVE_URL=postgresql://host.docker.internal:5432/rag_db \
  -e QUARKUS_REDIS_HOSTS=redis://host.docker.internal:6379 \
  rag:jvm
```

### Option 2: Native Executable

```bash
# Native build
./mvnw package -Dnative -Dquarkus.native.container-build=true

# Build Docker image
docker build -f src/main/docker/Dockerfile.native -t rag:native .

# Run container (same env vars as JVM)
docker run -i --rm -p 8081:8081 \
  -e OPENAI_API_KEY=$OPENAI_API_KEY \
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/rag_db \
  -e QUARKUS_DATASOURCE_REACTIVE_URL=postgresql://host.docker.internal:5432/rag_db \
  -e QUARKUS_REDIS_HOSTS=redis://host.docker.internal:6379 \
  rag:native
```

### Option 3: Docker Compose (Recommended)

Create a `docker-compose.yml` file:

```yaml
version: '3.8'
services:
  rag:
    build:
      context: .
      dockerfile: src/main/docker/Dockerfile.jvm
    ports:
      - "8081:8081"
    environment:
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://postgres:5432/rag_db
      - QUARKUS_DATASOURCE_REACTIVE_URL=postgresql://postgres:5432/rag_db
      - QUARKUS_REDIS_HOSTS=redis://redis:6379
    depends_on:
      - redis
      - postgres

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  postgres:
    image: pgvector/pgvector:pg17
    environment:
      POSTGRES_USER: quarkus
      POSTGRES_PASSWORD: quarkus
      POSTGRES_DB: rag_db
    ports:
      - "5432:5432"
```

Run with:

```bash
docker-compose up -d
```

## 🏗️ Architecture

The project follows **Hexagonal Architecture (Clean Architecture)** principles,
organizing the code in well-defined layers:

### Project Structure

```text
src/main/java/dev/rpmhub/
├── domain/                 # Application core
│   ├── model/             # Domain entities
│   ├── port/              # Interfaces/Contracts
│   └── usecase/           # Use cases/Business rules
├── application/           # Application layer
│   ├── mcp/               # MCP tools (course-aware Q&A)
│   └── rest/              # REST controllers
└── infrastructure/        # Infrastructure layer
    ├── adapter/           # External adapters (AI, etc.)
    ├── repository/        # Repository implementations
    ├── service/           # Infrastructure services
    └── util/              # Utilities
```

### Hexagonal Architecture Principles

**Hexagonal Architecture** (also known as **Ports and Adapters**) is an
architectural pattern that promotes separation of concerns and loose
coupling between application layers. This project implements the following
concepts:

#### 🔹 **Domain Layer (Core)**

The application core, containing pure business logic independent of external frameworks:

- **Domain Entities**: Classes such as `ChatMessage`, `RagQuery`, `RagResponse`, `ConversationMemory`
- **Use Cases**: Orchestrate business logic (`ChatbotUseCase`, `AskQuestionUseCase`)
- **Ports (Interfaces)**: Contracts that define how the domain communicates with the external world

```text
domain/
├── model/
│   ├── AIRequest.java          # AI request
│   ├── ChatMessage.java        # Chat message
│   ├── ConversationMemory.java # Conversation memory
│   ├── RagQuery.java           # RAG query
│   └── RagResponse.java        # RAG response
├── port/
│   ├── AIService.java          # Interface for AI services
│   ├── EmbeddingRepository.java # Interface for embedding repository
│   └── MemoryService.java      # Interface for memory services
└── usecase/
    ├── AskQuestionUseCase.java     # Use case: questions
    ├── ChatbotUseCase.java         # Use case: chatbot
    └── IngestDocumentsUseCase.java # Use case: ingestion
```

#### 🔹 **Application Layer**

Coordinates interaction between the domain and the external world:

- **REST Controllers**: Create API endpoints (`RagController`)

#### 🔹 **Infrastructure Layer**

Implements technical details and external integrations:

- **Adapters**: Concrete implementations of ports
(`AIServiceAdapter`, `LangChainAIService`)
- **Repositories**: Implements data persistence. In this application, repositories
are responsible for storing and retrieving embedding information and
conversation history (`EmbeddingRepositoryImpl`, `MemoryServiceImpl`)
- **Services**: Implementation of external application services. The system
requires Apache PDFBox (`PDFExtractorService`) for proper PDF extraction.

#### 🔹 **Hexagonal Architecture Benefits in This Project**

1. **Testability**: The domain can be tested in isolation through port mocks
2. **Flexibility**: Easy switching of AI providers (Ollama → OpenAI → Azure)
3. **Maintainability**: Framework changes do not affect business logic
4. **Independence**: The application core does not depend on external libraries

#### 🔹 **Data Flow**

```text
HTTP Request → REST Controller → Use Case → Domain Logic → Port Interface → Adapter → External Service
                     ↓
HTTP Response ← REST Controller ← Use Case ← Domain Logic ← Port Interface ← Adapter ← External Service
```

#### 🔹 **Specific Benefits for RAG Systems**

Hexagonal architecture is especially valuable in RAG systems due to the evolutionary and experimental nature of AI:

1. **Model Experimentation**: Facilitates testing with different LLMs (Ollama, OpenAI, Claude) without changing business logic
2. **Multiple Embedding Strategies**: Allows comparing different vectorization algorithms (sentence-transformers, OpenAI embeddings, etc.)
3. **Interchangeable Vector Databases**: Easy support for PostgreSQL+PGVector, Pinecone, Weaviate or Qdrant
4. **Chunking Strategies**: Implementation of different approaches for document splitting
5. **Adaptable Memory**: Switching between Redis, relational database or in-process memory
6. **Document Processing**: Extensibility for PDF, Word, HTML, etc.

```text
Hexagonal RAG System:

┌─────────────────────────┐
│    REST Controllers     │ ← Interface Layer
│    MCP Tools            │
├─────────────────────────┤
│      Use Cases          │ ← RAG Orchestration
│  • ChatbotUseCase       │
│  • AskQuestionUseCase   │
│  • IngestUseCase        │
├─────────────────────────┤
│       Ports             │ ← Contracts
│  • AIService            │
│  • EmbeddingRepository  │
│  • MemoryService        │
├─────────────────────────┤
│      Adapters           │ ← Implementations
│  • OpenAIAdapter        │
│  • PGVector (Embedding) │
│  • RedisAdapter         │
└─────────────────────────┘
```

### Main Use Cases

- **ChatbotUseCase**: Implements conversations with context memory
- **AskQuestionUseCase**: Answers questions based on documents
- **IngestDocumentsUseCase**: Processes and indexes documents

### 🔍 **Architecture Practical Examples**

#### Scenario 1: AI Provider Switching

```java
// Domain defines the contract (Port)
public interface AIService {
    Multi<String> generateResponse(String prompt, List<ChatMessage> context);
}

// Infrastructure implements different adapters
@ApplicationScoped
public class LangChainAIService implements AIService { ... }  // OpenAI, Azure, etc.

// Use case remains unchanged
@ApplicationScoped
public class ChatbotUseCase {
    @Inject AIService aiService; // Injection by interface
}
```

#### Scenario 2: Domain Testability

```java
// Unit test using port mock
@Test
void shouldGenerateResponseWithMemory() {
    // Given
    AIService mockAI = Mockito.mock(AIService.class);
    MemoryService mockMemory = Mockito.mock(MemoryService.class);
    ChatbotUseCase useCase = new ChatbotUseCase(null, mockAI, mockMemory);
    
    // When & Then - test only business logic
    // without external dependencies
}
```

#### Scenario 3: RAG System Evolution

```java
// New feature: add support for multiple embeddings
public interface EmbeddingRepository {
    // Existing method
    List<Document> findSimilar(String query, int limit);
    
    // New method - does not break existing implementations
    List<Document> findSimilarWithMetadata(String query, int limit, Map<String, Object> filters);
}
```

## 📚 API Usage

### Available Endpoints

#### 1. Chatbot with Memory

```bash
# Conversation with context maintained per session
curl "http://localhost:8081/ai/chatbot?session=user123&prompt=Hello, how can you help me?"
```

#### 2. Questions about Documents

```bash
# Query based on ingested documents
curl "http://localhost:8081/ai/ask?session=user123&prompt=What is Vue.js?"
```

#### 3. Memory Management

```bash
# Get conversation history
curl "http://localhost:8081/ai/memory?session=user123"
```

#### 4. Context Retrieval (MCP / Course-Aware Q&A)

```bash
# Retrieve semantically relevant course content for a query (used by MCP tools)
curl "http://localhost:8081/ai/context?session=user123&prompt=What%20are%20variables%20in%20JavaScript?&maxResults=5"
```

### Usage Examples

```javascript
// JavaScript/Frontend
const response = await fetch(
  'http://localhost:8081/ai/chatbot?session=user123&prompt=Explain generative AI'
);

const reader = response.body.getReader();
const decoder = new TextDecoder();

while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  
  const chunk = decoder.decode(value);
  console.log(chunk); // Streaming response
}
```

## ⚙️ Configuration

### Main Configuration (`application.properties`)

```properties
# Application port
quarkus.http.port=8081

# OpenAI (LLM + embeddings)
quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
quarkus.langchain4j.openai.chat-model.model-name=gpt-4o-mini
quarkus.langchain4j.openai.embedding-model.model-name=text-embedding-3-small
quarkus.langchain4j.embedding-model.provider=openai

# PostgreSQL + PGVector (vector database for embeddings)
quarkus.datasource.db-kind=postgresql
quarkus.langchain4j.pgvector.dimension=1536
# Dev Services starts PostgreSQL+PGVector automatically when Docker is available

# Redis (Cache/Memory)
quarkus.redis.devservices.enabled=true

# RAG configuration
rag.location=src/main/resources/rag
rag.context=JavaScript

# Memory Management
memory.default.max-messages=100
memory.ttl.hours=48
```

## 🧪 Tests

```bash
# Run all tests
./mvnw test

# Integration tests
./mvnw verify -Dskip.integration.tests=false

# Tests with native profile
./mvnw verify -Dnative
```

## 🔌 MCP Server (Course-Aware Q&A)

The Quarkus backend includes an MCP (Model Context Protocol) server for integrating the RAG system with LLM platforms (e.g., Claude, Cursor). It exposes tools for course-aware question answering via HTTP/SSE.

**Endpoint:** `http://localhost:8081/mcp/sse` (or `/mcp` for Streamable HTTP)

**Tools:**
- `retrieve_course_context` — Retrieves semantically relevant course content for a query
- `ask_course_question` — Asks a question and returns the RAG-generated answer

See [docs/MCP.md](docs/MCP.md) for Cursor/Claude configuration.

## 📖 Additional Documentation

- [Quarkus Guide](https://quarkus.io/guides/)
- [LangChain4j Documentation](https://docs.langchain4j.dev/)
- [OpenAI API](https://platform.openai.com/docs/)
- [PGVector](https://github.com/pgvector/pgvector)

## 🤝 Contributing

1. Fork the project
2. Create a branch for your feature (`git checkout -b feature/new-feature`)
3. Commit your changes (`git commit -m 'Add new feature'`)
4. Push to the branch (`git push origin feature/new-feature`)
5. Open a Pull Request

## 📄 License

This project contains confidential and proprietary information.
Unauthorized copying, distribution, or use of this file or its contents is strictly prohibited.

© 2025 Rodrigo Prestes Machado. All rights reserved.
