# REST Assured Tests for RAG Endpoints with Vue.js Focus

This project contains automated tests using REST Assured to validate the RAG (Retrieval-Augmented Generation) application endpoints with special focus on Vue.js-related questions.

## Test Structure

### `RagServiceTest.java`
Comprehensive tests for all endpoints:

#### Tested Endpoints

**1. `/ai/chatbot` - Server-Sent Events**
- ✅ Test with valid parameters (session + prompt)
- ✅ Test without prompt (should fail with status 500)
- ✅ Test with Vue.js-specific prompt
- ✅ Test with special characters in prompt
- ✅ Test with long prompt
- ✅ Test with empty session

**2. `/ai/ask` - Server-Sent Events**
- ✅ Test with valid parameters
- ✅ Test without prompt (should fail with status 500)
- ✅ Test with Vue.js-specific question
- ✅ URL encoding test with special characters

**3. `/ai/memory` - JSON Response**
- ✅ Test existing memory retrieval
- ✅ Test with non-existent session (status 204)
- ✅ Test without session parameter (status 204)

#### Special Scenarios

**Vue.js Integration**
- ✅ Full frontend flow simulation
- ✅ Interactive tutorial with multiple questions
- ✅ Memory persistence verification between requests

**Performance**
- ✅ Multiple simultaneous requests test
- ✅ Timeout and long request handling test

### `RagControllerBasicTest.java`
Fast and focused tests specifically for Vue.js:

#### Vue.js Specific Tests
- ✅ **Components**: "How to create Vue.js components with props?"
- ✅ **Directives**: "Explain the v-if and v-for directives in Vue.js"
- ✅ **Reactivity**: "How does the Vue.js reactivity system work?"
- ✅ **Composition API**: "What is the difference between Options API and Composition API?"

#### Validation Tests
- ✅ HTTP headers validation
- ✅ Content-Type validation (text/event-stream for SSE)
- ✅ Correct status codes validation

## How to Run Tests

### Run all basic tests (faster):
```bash
./mvnw verify -Dtest=RagControllerBasicIT
```

### Run Vue.js specific tests:
```bash
./mvnw verify -Dtest="RagControllerBasicIT#testVue*"
```

### Run a specific test:
```bash
./mvnw verify -Dtest="RagControllerBasicIT#testVueComponentsQuestion"
```

### Run all complete tests:
```bash
./mvnw verify -Dtest=RagServiceIT
```

## Test Characteristics

### Content-Type Validation
Tests validate that:
- `/ai/chatbot` and `/ai/ask` endpoints return `text/event-stream` (SSE)
- `/ai/memory` endpoint returns `application/json`

### Expected Status Codes
- **200**: Valid requests with prompt
- **204**: Memory endpoint for non-existent sessions
- **500**: Requests without prompt (blank text)

### Vue.js Test Cases

#### 1. **Vue.js Components**
```http
GET /ai/chatbot?session=vue-components-test&prompt=Como criar componentes Vue.js com props?
```

#### 2. **Vue.js Directives**
```http
GET /ai/ask?session=vue-directives-test&prompt=Explique as diretivas v-if e v-for do Vue.js
```

#### 3. **Reactivity System**
```http
GET /ai/chatbot?session=vue-reactivity-test&prompt=Como funciona o sistema de reatividade do Vue.js?
```

#### 4. **Composition API vs Options API**
```http
GET /ai/ask?session=vue-composition-api-test&prompt=Qual a diferença entre Options API e Composition API no Vue.js?
```

### Integration Flow Tested

1. **Initial question** about Vue.js via `/ai/chatbot`
2. **Memory verification** via `/ai/memory`
3. **Follow-up question** via `/ai/ask`
4. **Conversation persistence** validation

## Test Data Used

### Knowledge Base
Tests use Vue.js documents including:
- `instancia.pdf`: Documentation about Vue instances
- `eventos.txt`: Information about events and handlers

### Test Sessions
Each test uses unique sessions with identifier prefixes:
- `vue-*`: Vue.js specific tests
- `test-*`: General tests
- `performance-*`: Performance tests
- Timestamps are used to ensure uniqueness

## Best Practices Implemented

### ✅ Test Isolation
Each test uses a unique session to avoid interference

### ✅ Configured Timeouts
Tests with appropriate timeouts to avoid infinite execution

### ✅ Complete Validation
- Status codes
- HTTP headers
- Content-Type
- Response structure

### ✅ Error Cases
Tests for failure scenarios (empty prompt, non-existent session)

### ✅ Special Characters
Validation with accents, emojis and special characters

### ✅ Performance
Multiple simultaneous requests tests

## Test Results

Example of successful output:
```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Tests validate that the RAG application is working correctly for:
- ✅ Answering Vue.js questions
- ✅ Maintaining conversation context
- ✅ Handling special characters
- ✅ Returning correct formats (SSE/JSON)
- ✅ Managing user sessions

## Important Notes

### Server-Sent Events (SSE)
Chat endpoints return event streams, not plain text. Tests validate:
- Content-Type: `text/event-stream`
- Status 200 for stream start
- Appropriate headers for SSE

### Service Dependencies
Tests depend on:
- Ollama (AI model)
- ChromaDB (vector database)
- Redis (cache/memory)

However, these services are started automatically via Testcontainers during tests.
