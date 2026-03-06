# RAG Test - RAG System with Generative AI

This project implements a RAG (Retrieval-Augmented Generation) system
using Quarkus, LangChain4j and Ollama to create an intelligent chatbot that
can answer questions based on ingested documents.

## RAG Directory

The `src/main/resources/rag` directory contains sample documents for
ingestion and system testing. You can add your own documents to this
directory to expand the chatbot's knowledge.

## 🛠️ Technologies Used

- **Java 21** - Programming language
- **Quarkus 3.26.2** - Framework for cloud-native Java applications
- **LangChain4j** - Framework for AI integration
- **Ollama** - Platform to run AI models locally
- **Chroma** - Vector database for embeddings
- **Redis** - Cache and memory management
- **Maven** - Dependency management

## 📋 Prerequisites

- Java 21 or higher
- Maven 3.8+
- Ollama installed and configured
- Docker and Docker Compose

## 🚀 Installation and Configuration

### 1. Ollama Installation

```bash
# macOS
brew install ollama

# Linux
curl -fsSL https://ollama.com/install.sh | sh

# Windows
# Download the installer from https://ollama.com/download/windows
```

### 2. Download AI Models in Ollama

```bash
# Chat model
ollama pull gemma3:1b

# Embeddings model
ollama pull all-minilm:33m
```

### 3. Service Initialization

```bash
# Start Ollama
ollama serve

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

***Note:*** Chroma and Redis will be started automatically via Quarkus Dev Services. Dev Services is a Quarkus feature that facilitates local development by automatically starting services such as databases, message queues, caches, and more, without configuration. However, Docker must be installed for Quarkus to create and manage these containers.

### User Interface

If you want to test the chat interface, simply press the `w` key in the
terminal when the application is running and Quarkus will open the
web interface at: <http://localhost:8080/>.

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

# Run container
docker run -i --rm -p 8080:8080 \
  -e QUARKUS_LANGCHAIN4J_OLLAMA_BASE_URL=http://host.docker.internal:11434/ \
  rag:jvm
```

### Option 2: Native Executable

```bash
# Native build
./mvnw package -Dnative -Dquarkus.native.container-build=true

# Build Docker image
docker build -f src/main/docker/Dockerfile.native -t rag:native .

# Run container
docker run -i --rm -p 8080:8080 \
  -e QUARKUS_LANGCHAIN4J_OLLAMA_BASE_URL=http://host.docker.internal:11434/ \
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
      - "8080:8080"
    environment:
      - QUARKUS_LANGCHAIN4J_OLLAMA_BASE_URL=http://host.docker.internal:11434/
    depends_on:
      - redis
      - chroma

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  chroma:
    image: chromadb/chroma:latest
    ports:
      - "8000:8000"
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
│   ├── adapter/           # Application adapters
│   └── rest/              # REST controllers
└── infrastructure/        # Infrastructure layer
    ├── adapter/           # External adapters
    ├── config/            # Configuration
    ├── repository/        # Repository implementations
    └── service/           # Infrastructure services
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
3. **Interchangeable Vector Databases**: Easy support for Chroma, Pinecone, Weaviate or Qdrant
4. **Chunking Strategies**: Implementation of different approaches for document splitting
5. **Adaptable Memory**: Switching between Redis, relational database or in-process memory
6. **Document Processing**: Extensibility for PDF, Word, HTML, etc.

```text
Hexagonal RAG System:

┌─────────────────────────┐
│    REST Controllers     │ ← Interface Layer
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
│  • OllamaAdapter        │
│  • ChromaAdapter        │
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
public class OllamaAdapter implements AIService { ... }

@ApplicationScoped  
public class OpenAIAdapter implements AIService { ... }

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
curl "http://localhost:8080/ai/chatbot?session=user123&prompt=Hello, how can you help me?"
```

#### 2. Questions about Documents

```bash
# Query based on ingested documents
curl "http://localhost:8080/ai/ask?session=user123&prompt=What is Vue.js?"
```

#### 3. Memory Management

```bash
# Get conversation history
curl "http://localhost:8080/ai/memory?session=user123"
```

### Usage Examples

```javascript
// JavaScript/Frontend
const response = await fetch(
  'http://localhost:8080/ai/chatbot?session=user123&prompt=Explain generative AI'
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
quarkus.http.port=8080

# Ollama configuration
quarkus.langchain4j.ollama.base-url=http://localhost:11434/
quarkus.langchain4j.ollama.chat-model.model-id=gemma3:1b
quarkus.langchain4j.ollama.embedding-model.model-id=all-minilm:33m
quarkus.langchain4j.ollama.devservices.enabled=false

# RAG configuration
rag.location=src/main/resources/rag
rag.context=Vue.js
quarkus.langchain4j.embedding-model.provider=ollama

# Chroma (Vector Database)
quarkus.langchain4j.chroma.collection-name=chatbot
quarkus.langchain4j.chroma.timeout=30000

# Redis (Cache/Memory)
quarkus.redis.devservices.enabled=true

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

## 📖 Additional Documentation

- [Quarkus Guide](https://quarkus.io/guides/)
- [LangChain4j Documentation](https://docs.langchain4j.dev/)
- [Ollama Documentation](https://ollama.com/docs/)

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
