# RAG Test - Sistema RAG com IA Generativa

Este projeto implementa um sistema RAG (Retrieval-Augmented Generation)
utilizando Quarkus, LangChain4j e Ollama para criar um chatbot inteligente que
pode responder perguntas baseadas em documentos ingeridos.

## Diretório do RAG

O diretório `src/main/resources/rag` contém documentos de exemplo para
ingestão e teste do sistema. Você pode adicionar seus próprios documentos neste
diretório para expandir o conhecimento do chatbot.

## 🛠️ Tecnologias Utilizadas

- **Java 21** - Linguagem de programação
- **Quarkus 3.26.2** - Framework para aplicações Java na nuvem
- **LangChain4j** - Framework para integração com IA
- **Ollama** - Plataforma para executar modelos de IA localmente
- **Chroma** - Banco de dados vetorial para embeddings
- **Redis** - Cache e gerenciamento de memória
- **Maven** - Gerenciamento de dependências

## 📋 Pré-requisitos

- Java 21 ou superior
- Maven 3.8+
- Ollama instalado e configurado
- Docker e Docker Compose

## 🚀 Instalação e Configuração

### 1. Instalação do Ollama

```bash
# macOS
brew install ollama

# Linux
curl -fsSL https://ollama.com/install.sh | sh

# Windows
# Baixe o instalador em https://ollama.com/download/windows
```

### 2. Download dos Modelos de IA no Ollama

```bash
# Modelo para chat
ollama pull gemma3:1b

# Modelo para embeddings
ollama pull all-minilm:33m
```

### 3. Inicialização dos Serviços

```bash
# Inicie o Ollama
ollama serve

# Clone o repositório
git clone https://github.com/rodrigoprestesmachado/rag.git
cd rag
```

## 🔧 Execução Local

### Modo de Desenvolvimento

**⚠️ Importante:** Antes de executar o Quarkus em modo dev, você precisa fazer o build do frontend se quiser usar a interface web servida pelo Quarkus. As variáveis de ambiente do frontend (como `VITE_GOOGLE_CLIENT_ID`) são embutidas no código durante o build.

#### 1. Build do Frontend (Obrigatório para interface web)

```bash
# Navegue até o diretório do frontend
cd frontend

# Configure as variáveis de ambiente (crie o arquivo .env se não existir)
# Veja frontend/README.md para mais detalhes sobre as variáveis necessárias

# Instale as dependências (se ainda não fez)
npm install

# Faça o build do frontend
npm run build
```

**Nota:** O build gera os arquivos estáticos em `src/main/resources/META-INF/resources/` que serão servidos pelo Quarkus. Se você modificar as variáveis de ambiente no `frontend/.env`, será necessário fazer um novo build para que as mudanças sejam refletidas.

#### 2. Executar Quarkus em Modo Dev

```bash
# Volte para a raiz do projeto
cd ..

# Execute a aplicação em modo de desenvolvimento
./mvnw quarkus:dev
```

***Nota:*** o Chroma e o Redis serão iniciados automaticamente via Dev Services do
Quarkus. O Dev Services é uma funcionalidade do Quarkus que facilita o
desenvolvimento local, iniciando automaticamente serviços como bancos de dados,
filas de mensagens, caches, entre outros, sem a necessidade de configuração.
Porém, é necessário ter o Docker instalado para que o Quarkus possa criar e
gerenciar esses containers.

### Interface com o Usuário

Se você quiser testar a interface do chat, basta pressionar a tecla `w` no
terminal quando a aplicação estiver em execução que o Quarkus irá abrir a
interface web no endereço e porta: <http://localhost:8080/>.

### Modo de Produção

Empacote e execute a aplicação:

```bash
# Compilar
./mvnw package

# Executar JAR
java -jar target/quarkus-app/quarkus-run.jar

# Ou criar e executar uber-jar
./mvnw package -Dquarkus.package.jar.type=uber-jar
java -jar target/*-runner.jar
```

## 🐳 Execução com Docker

### Opção 1: Executável JVM

```bash
# Build da aplicação
./mvnw package

# Build da imagem Docker
docker build -f src/main/docker/Dockerfile.jvm -t rag:jvm .

# Executar container
docker run -i --rm -p 8080:8080 \
  -e QUARKUS_LANGCHAIN4J_OLLAMA_BASE_URL=http://host.docker.internal:11434/ \
  rag:jvm
```

### Opção 2: Executável Nativo

```bash
# Build nativo
./mvnw package -Dnative -Dquarkus.native.container-build=true

# Build da imagem Docker
docker build -f src/main/docker/Dockerfile.native -t rag:native .

# Executar container
docker run -i --rm -p 8080:8080 \
  -e QUARKUS_LANGCHAIN4J_OLLAMA_BASE_URL=http://host.docker.internal:11434/ \
  rag:native
```

### Opção 3: Docker Compose (Recomendado)

Crie um arquivo `docker-compose.yml`:

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

Execute com:

```bash
docker-compose up -d
```

## 🏗️ Arquitetura

O projeto segue os princípios da **Arquitetura Hexagonal (Clean Architecture)**,
organizando o código em camadas bem definidas:

### Estrutura do Projeto

```text
src/main/java/dev/rpmhub/
├── domain/                 # Núcleo da aplicação
│   ├── model/             # Entidades de domínio
│   ├── port/              # Interfaces/Contratos
│   └── usecase/           # Casos de uso/Regras de negócio
├── application/           # Camada de aplicação
│   ├── adapter/           # Adaptadores de aplicação
│   └── rest/              # Controladores REST
└── infrastructure/        # Camada de infraestrutura
    ├── adapter/           # Adaptadores externos
    ├── config/            # Configurações
    ├── repository/        # Implementações de repositório
    └── service/           # Serviços de infraestrutura
```

### Princípios da Arquitetura Hexagonal

A **Arquitetura Hexagonal** (também conhecida como **Ports and Adapters**) é um
padrão arquitetural que promove a separação de responsabilidades e o baixo
acoplamento entre as camadas da aplicação. Este projeto implementa os seguintes
conceitos:

#### 🔹 **Camada de Domínio (Core)**

O núcleo da aplicação, contendo a lógica de negócio pura e independente de frameworks externos:

- **Entidades de Domínio**: Classes como `ChatMessage`, `RagQuery`, `RagResponse`, `ConversationMemory`
- **Casos de Uso**: Orquestram a lógica de negócio (`ChatbotUseCase`, `AskQuestionUseCase`)
- **Portas (Interfaces)**: Contratos que definem como o domínio se comunica com o mundo externo

```text
domain/
├── model/
│   ├── AIRequest.java          # Requisição para IA
│   ├── ChatMessage.java        # Mensagem de chat
│   ├── ConversationMemory.java # Memória da conversa
│   ├── RagQuery.java          # Query RAG
│   └── RagResponse.java       # Resposta RAG
├── port/
│   ├── AIService.java         # Interface para serviços de IA
│   ├── EmbeddingRepository.java # Interface para repositório de embeddings
│   └── MemoryService.java     # Interface para serviços de memória
└── usecase/
    ├── AskQuestionUseCase.java     # Caso de uso: perguntas
    ├── ChatbotUseCase.java         # Caso de uso: chatbot
    └── IngestDocumentsUseCase.java # Caso de uso: ingestão
```

#### 🔹 **Camada de Aplicação**

Coordena a interação entre o domínio e o mundo externo:

- **Controladores REST**: Criam os endpoints da API (`RagController`)

#### 🔹 **Camada de Infraestrutura**

Implementa os detalhes técnicos e integrações externas:

- **Adaptadores**: Implementações concretas das portas
(`AIServiceAdapter`, `LangChainAIService`)
- **Repositórios**: Implementa a persistência de dados. Nesta aplicação, os
repositórios são responsáveis por armazenar e recuperar informações de
embeddings e também o histórico de uma conversa (`EmbeddingRepositoryImpl`,
`MemoryServiceImpl`)
- **Serviços**: Implementação de serviços externo da aplicação. O sistema
necessita do Apache PDFBox (`PDFExtractorService`) para fazer a extração
correta de PDFs.

#### 🔹 **Vantagens da Arquitetura Hexagonal neste Projeto**

1. **Testabilidade**: O domínio pode ser testado isoladamente através de mocks das portas
2. **Flexibilidade**: Fácil troca de provedores de IA (Ollama → OpenAI → Azure)
3. **Manutenibilidade**: Alterações em frameworks não afetam a lógica de negócio
4. **Independência**: O core da aplicação não depende de bibliotecas externas

#### 🔹 **Fluxo de Dados**

```text
HTTP Request → REST Controller → Use Case → Domain Logic → Port Interface → Adapter → External Service
                     ↓
HTTP Response ← REST Controller ← Use Case ← Domain Logic ← Port Interface ← Adapter ← External Service
```

#### 🔹 **Benefícios Específicos para Sistemas RAG**

A arquitetura hexagonal é especialmente valiosa em sistemas RAG devido à natureza evolutiva e experimental da IA:

1. **Experimentação com Modelos**: Facilita testes com diferentes LLMs (Ollama, OpenAI, Claude) sem alterar a lógica de negócio
2. **Múltiplas Estratégias de Embedding**: Permite comparar diferentes algoritmos de vetorização (sentence-transformers, OpenAI embeddings, etc.)
3. **Bancos Vetoriais Intercambiáveis**: Suporte fácil para Chroma, Pinecone, Weaviate ou Qdrant
4. **Estratégias de Chunking**: Implementação de diferentes abordagens para divisão de documentos
5. **Memória Adaptável**: Alternância entre Redis, banco relacional ou memória em processo
6. **Processamento de Documentos**: Extensibilidade para PDF, Word, HTML, etc.

```text
Sistema RAG Hexagonal:

┌─────────────────────────┐
│    REST Controllers     │ ← Camada de Interface
├─────────────────────────┤
│      Use Cases          │ ← Orquestração RAG
│  • ChatbotUseCase       │
│  • AskQuestionUseCase   │
│  • IngestUseCase        │
├─────────────────────────┤
│       Ports             │ ← Contratos
│  • AIService            │
│  • EmbeddingRepository  │
│  • MemoryService        │
├─────────────────────────┤
│      Adapters           │ ← Implementações
│  • OllamaAdapter        │
│  • ChromaAdapter        │
│  • RedisAdapter         │
└─────────────────────────┘
```

### Casos de Uso Principais

- **ChatbotUseCase**: Implementa conversas com memória de contexto
- **AskQuestionUseCase**: Responde perguntas baseadas em documentos
- **IngestDocumentsUseCase**: Processa e indexa documentos

### 🔍 **Exemplos Práticos da Arquitetura**

#### Cenário 1: Troca de Provedor de IA

```java
// Domínio define o contrato (Port)
public interface AIService {
    Multi<String> generateResponse(String prompt, List<ChatMessage> context);
}

// Infraestrutura implementa diferentes adaptadores
@ApplicationScoped
public class OllamaAdapter implements AIService { ... }

@ApplicationScoped  
public class OpenAIAdapter implements AIService { ... }

// Caso de uso permanece inalterado
@ApplicationScoped
public class ChatbotUseCase {
    @Inject AIService aiService; // Injeção por interface
}
```

#### Cenário 2: Testabilidade do Domínio

```java
// Teste unitário usando mock da porta
@Test
void shouldGenerateResponseWithMemory() {
    // Given
    AIService mockAI = Mockito.mock(AIService.class);
    MemoryService mockMemory = Mockito.mock(MemoryService.class);
    ChatbotUseCase useCase = new ChatbotUseCase(null, mockAI, mockMemory);
    
    // When & Then - testa apenas lógica de negócio
    // sem dependências externas
}
```

#### Cenário 3: Evolução do Sistema RAG

```java
// Nova funcionalidade: adicionar suporte a múltiplos embeddings
public interface EmbeddingRepository {
    // Método existente
    List<Document> findSimilar(String query, int limit);
    
    // Novo método - não quebra implementações existentes
    List<Document> findSimilarWithMetadata(String query, int limit, Map<String, Object> filters);
}
```

## 📚 Uso da API

### Endpoints Disponíveis

#### 1. Chatbot com Memória

```bash
# Conversa com contexto mantido por sessão
curl "http://localhost:8080/ai/chatbot?session=user123&prompt=Olá, como você pode me ajudar?"
```

#### 2. Perguntas sobre Documentos

```bash
# Consulta baseada em documentos ingeridos
curl "http://localhost:8080/ai/ask?session=user123&prompt=O que é Vue.js?"
```

#### 3. Gerenciamento de Memória

```bash
# Obter histórico da conversa
curl "http://localhost:8080/ai/memory?session=user123"
```

### Exemplos de Uso

```javascript
// JavaScript/Frontend
const response = await fetch(
  'http://localhost:8080/ai/chatbot?session=user123&prompt=Explique IA generativa'
);

const reader = response.body.getReader();
const decoder = new TextDecoder();

while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  
  const chunk = decoder.decode(value);
  console.log(chunk); // Resposta em streaming
}
```

## ⚙️ Configuração

### Principais Configurações (`application.properties`)

```properties
# Porta da aplicação
quarkus.http.port=8080

# Configuração do Ollama
quarkus.langchain4j.ollama.base-url=http://localhost:11434/
quarkus.langchain4j.ollama.chat-model.model-id=gemma3:1b
quarkus.langchain4j.ollama.embedding-model.model-id=all-minilm:33m
quarkus.langchain4j.ollama.devservices.enabled=false

# Configuração RAG
rag.location=src/main/resources/rag
rag.context=Vue.js
quarkus.langchain4j.embedding-model.provider=ollama

# Chroma (Banco Vetorial)
quarkus.langchain4j.chroma.collection-name=chatbot
quarkus.langchain4j.chroma.timeout=30000

# Redis (Cache/Memória)
quarkus.redis.devservices.enabled=true

# Gerenciamento de Memória
memory.default.max-messages=100
memory.ttl.hours=48
```

## 🧪 Testes

```bash
# Executar todos os testes
./mvnw test

# Testes de integração
./mvnw verify -Dskip.integration.tests=false

# Testes com perfil nativo
./mvnw verify -Dnative
```

## 📖 Documentação Adicional

- [Guia do Quarkus](https://quarkus.io/guides/)
- [LangChain4j Documentation](https://docs.langchain4j.dev/)
- [Ollama Documentation](https://ollama.com/docs/)

## 🤝 Contribuindo

1. Faça um fork do projeto
2. Crie uma branch para sua feature (`git checkout -b feature/nova-feature`)
3. Commit suas mudanças (`git commit -m 'Adiciona nova feature'`)
4. Push para a branch (`git push origin feature/nova-feature`)
5. Abra um Pull Request

## 📄 Licença

Este projeto contém informações confidenciais e proprietárias.
Cópia, distribuição ou uso não autorizado deste arquivo ou seu conteúdo é estritamente proibido.

© 2025 Rodrigo Prestes Machado. Todos os direitos reservados.
