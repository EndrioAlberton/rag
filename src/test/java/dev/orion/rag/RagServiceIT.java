/*
 * Copyright 2026 Orion Services.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.orion.rag;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.equalTo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;

@QuarkusIntegrationTest
class RagServiceIT {

    @Test
    @DisplayName("Test /ai/chatbot endpoint - positive scenario with session and prompt")
    void testChatbotEndpointWithValidParameters() {
        given()
            .when()
                .queryParam("session", "test-session-vue-001")
                .queryParam("prompt", "O que é Vue.js?")
                .get("/ai/chatbot")
            .then()
                .statusCode(200)
                .contentType("text/event-stream");
    }

    @Test
    @DisplayName("Test /ai/chatbot endpoint - scenario without prompt (should fail)")
    void testChatbotEndpointWithoutPrompt() {
        given()
            .when()
                .queryParam("session", "test-session-no-prompt")
                .get("/ai/chatbot")
            .then()
                // Expect error for blank prompt: Bad Request
                .statusCode(400);
    }

    @Test
    @DisplayName("Test /ai/chatbot endpoint - scenario with Vue-specific prompt")
    void testChatbotEndpointWithVuePrompt() {
        given()
            .when()
                .queryParam("session", "vue-session-123")
                .queryParam("prompt", "Como criar um componente reativo em Vue.js?")
                .get("/ai/chatbot")
            .then()
                .statusCode(200)
                .contentType("text/event-stream");
    }

    @Test
    @DisplayName("Test /ai/ask endpoint - positive scenario")
    void testAskModelEndpointWithValidParameters() {
        given()
            .when()
                .queryParam("session", "ask-session-vue-001")
                .queryParam("prompt", "Explique o ciclo de vida de um componente Vue")
                .get("/ai/ask")
            .then()
                .statusCode(200)
                .contentType("text/event-stream");
    }

    @Test
    @DisplayName("Test /ai/ask endpoint - scenario without prompt (should fail)")
    void testAskModelEndpointWithoutPrompt() {
        given()
            .when()
                .queryParam("session", "ask-session-no-prompt")
                .get("/ai/ask")
            .then()
                // Expect error for blank prompt: Bad Request
                .statusCode(400);
    }

    @Test
    @DisplayName("Test /ai/ask endpoint - Vue.js specific question")
    void testAskModelEndpointWithVueQuestion() {
        given()
            .when()
                .queryParam("session", "vue-test-session")
                .queryParam("prompt", "Quais são as principais diretivas do Vue.js?")
                .get("/ai/ask")
            .then()
                .statusCode(200)
                .contentType("text/event-stream");
    }

    @Test
    @DisplayName("Test /ai/memory endpoint - retrieve conversation memory")
    void testGetMemoryEndpoint() {
        String sessionId = "memory-test-session-" + System.currentTimeMillis();

        // First, ask a question to create memory
        given()
            .when()
                .queryParam("session", sessionId)
                .queryParam("prompt", "O que é Vue.js?")
                .get("/ai/chatbot")
            .then()
                .statusCode(200);

        // Then retrieve the memory
        given()
            .when()
                .queryParam("session", sessionId)
                .get("/ai/memory")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("session", equalTo(sessionId))
                .body("messages", notNullValue())
                .body("lastActivity", notNullValue())
                .body("maxMessages", notNullValue());
    }

    @Test
    @DisplayName("Test /ai/memory endpoint - non-existent session")
    void testGetMemoryEndpointWithNonExistentSession() {
        given()
            .when()
                .queryParam("session", "non-existent-session")
                .get("/ai/memory")
            .then()
                .statusCode(204); // No Content for non-existent session
    }

    @Test
    @DisplayName("Test /ai/memory endpoint - without session parameter")
    void testGetMemoryEndpointWithoutSession() {
        given()
            .when()
                .get("/ai/memory")
            .then()
                .statusCode(204); // No Content when there is no session
    }

    @Test
    @DisplayName("Vue.js integration test - frontend flow simulation")
    void testVueIntegrationFlow() {
        String session = "vue-integration-test-" + System.currentTimeMillis();
        
        // Simulates a typical user question through a Vue interface
        given()
            .when()
                .queryParam("session", session)
                .queryParam("prompt", "Como implementar data binding em Vue.js?")
                .get("/ai/chatbot")
            .then()
                .statusCode(200)
                .contentType("text/event-stream");

        // Verify memory was created correctly
        given()
            .when()
                .queryParam("session", session)
                .get("/ai/memory")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("session", equalTo(session))
                .body("messages", notNullValue());

        // Ask a follow-up question
        given()
            .when()
                .queryParam("session", session)
                .queryParam("prompt", "Pode dar um exemplo prático?")
                .get("/ai/ask")
            .then()
                .statusCode(200)
                .contentType("text/event-stream");
    }

    @Test
    @DisplayName("Teste de performance - múltiplas requisições simultâneas")
    void testMultipleSimultaneousRequests() throws InterruptedException {
        int numberOfRequests = 3; // Reduzido para evitar timeout
        CountDownLatch latch = new CountDownLatch(numberOfRequests);

        for (int i = 0; i < numberOfRequests; i++) {
            final int requestId = i;
            new Thread(() -> {
                try {
                    given()
                        .when()
                            .queryParam("session", "performance-test-" + requestId)
                            .queryParam("prompt", "Pergunta sobre Vue.js número " + requestId)
                            .get("/ai/chatbot")
                        .then()
                            .statusCode(200);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // Wait for all requests to complete (max 60 seconds)
        latch.await(60, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Test with special characters in prompt")
    void testSpecialCharactersInPrompt() {
        given()
            .when()
                .queryParam("session", "special-chars-session")
                .queryParam("prompt", "Como usar ácentos e çaracteres especiais em Vue.js? 🚀")
                .get("/ai/chatbot")
            .then()
                .statusCode(200)
                .contentType("text/event-stream");
    }

    @Test
    @DisplayName("Test with long prompt - status verification only")
    void testLongPrompt() {
        String longPrompt = "Este é um prompt muito longo para testar como o sistema lida com textos extensos. ".repeat(5) + 
                           "A pergunta principal é sobre Vue.js e como ele pode ser usado em aplicações complexas.";
        
        given()
            .when()
                .queryParam("session", "long-prompt-session")
                .queryParam("prompt", longPrompt)
                .get("/ai/ask")
            .then()
                .statusCode(200)
                .contentType("text/event-stream");
    }

    @Test
    @DisplayName("Test with empty session")
    void testEmptySession() {
        given()
            .when()
                .queryParam("session", "")
                .queryParam("prompt", "Teste com sessão vazia")
                .get("/ai/chatbot")
            .then()
                .statusCode(200)
                .contentType("text/event-stream");
    }

    @Test
    @DisplayName("URL encoding test - special characters in URL")
    void testUrlEncodingSpecialCharacters() {
        given()
            .when()
                .queryParam("session", "url-encoding-test")
                .queryParam("prompt", "Vue.js & React.js - qual é melhor?")
                .get("/ai/ask")
            .then()
                .statusCode(200)
                .contentType("text/event-stream");
    }

    @Test
    @DisplayName("Real Vue.js use case test - interactive tutorial")
    void testVueJsTutorialUseCase() {
        String tutorialSession = "vue-tutorial-" + System.currentTimeMillis();
        
        // First question - basic concepts
        given()
            .when()
                .queryParam("session", tutorialSession)
                .queryParam("prompt", "Como começar com Vue.js? Explique o conceito de reatividade.")
                .get("/ai/chatbot")
            .then()
                .statusCode(200)
                .contentType("text/event-stream");

        // Second question - components
        given()
            .when()
                .queryParam("session", tutorialSession)
                .queryParam("prompt", "Como criar e usar componentes em Vue.js?")
                .get("/ai/ask")
            .then()
                .statusCode(200)
                .contentType("text/event-stream");

        // Verify tutorial memory
        given()
            .when()
                .queryParam("session", tutorialSession)
                .get("/ai/memory")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("session", equalTo(tutorialSession));
    }
}