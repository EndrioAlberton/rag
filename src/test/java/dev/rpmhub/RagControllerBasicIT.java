/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */

package dev.rpmhub;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class RagControllerBasicIT {

    @Test
    @DisplayName("Basic endpoint test - status code and content type")
    void testBasicEndpointResponse() {
        // Simple test of memory endpoint which is faster
        given()
            .when()
                .queryParam("session", "basic-test-session")
                .get("/ai/memory")
            .then()
                .statusCode(204); // Expect 204 for non-existent session
    }

    @Test
    @DisplayName("Chatbot endpoint test - header validation only")
    void testChatbotEndpointHeaders() {
        given()
            .when()
                .queryParam("session", "header-test")
                .queryParam("prompt", "Teste simples")
                .get("/ai/chatbot")
            .then()
                .statusCode(200)
                .header("Content-Type", "text/event-stream");
    }

    @Test
    @DisplayName("Ask endpoint test - header validation only")
    void testAskEndpointHeaders() {
        given()
            .when()
                .queryParam("session", "ask-header-test")
                .queryParam("prompt", "Pergunta sobre Vue.js")
                .get("/ai/ask")
            .then()
                .statusCode(200)
                .header("Content-Type", "text/event-stream");
    }

    @Test
    @DisplayName("Context endpoint test - MCP course-aware retrieval")
    void testContextEndpoint() {
        given()
            .when()
                .queryParam("session", "context-test")
                .queryParam("prompt", "variáveis em JavaScript")
                .queryParam("maxResults", 3)
                .get("/ai/context")
            .then()
                .statusCode(200)
                .header("Content-Type", "application/json");
    }

    @Test
    @DisplayName("Vue.js specific test - question about components")
    void testVueComponentsQuestion() {
        given()
            .when()
                .queryParam("session", "vue-components-test")
                .queryParam("prompt", "Como criar componentes Vue.js com props?")
                .get("/ai/chatbot")
            .then()
                .statusCode(200)
                .header("Content-Type", "text/event-stream");
    }

    @Test
    @DisplayName("Vue.js test - question about directives")
    void testVueDirectivesQuestion() {
        given()
            .when()
                .queryParam("session", "vue-directives-test")
                .queryParam("prompt", "Explique as diretivas v-if e v-for do Vue.js")
                .get("/ai/ask")
            .then()
                .statusCode(200)
                .header("Content-Type", "text/event-stream");
    }

    @Test
    @DisplayName("Vue.js test - question about reactivity")
    void testVueReactivityQuestion() {
        given()
            .when()
                .queryParam("session", "vue-reactivity-test")
                .queryParam("prompt", "Como funciona o sistema de reatividade do Vue.js?")
                .get("/ai/chatbot")
            .then()
                .statusCode(200)
                .header("Content-Type", "text/event-stream");
    }

    @Test
    @DisplayName("Vue.js test - question about Composition API")
    void testVueCompositionApiQuestion() {
        given()
            .when()
                .queryParam("session", "vue-composition-api-test")
                .queryParam("prompt", "Qual a diferença entre Options API e Composition API no Vue.js?")
                .get("/ai/ask")
            .then()
                .statusCode(200)
                .header("Content-Type", "text/event-stream");
    }

    @Test
    @DisplayName("Special characters in session test")
    void testSpecialCharactersInSession() {
        given()
            .when()
                .queryParam("session", "test-session-àçéíõú")
                .queryParam("prompt", "Teste com caracteres especiais")
                .get("/ai/chatbot")
            .then()
                .statusCode(200)
                .header("Content-Type", "text/event-stream");
    }

    @Test
    @DisplayName("UUID session test")
    void testUuidSession() {
        String uuid = java.util.UUID.randomUUID().toString();
        given()
            .when()
                .queryParam("session", uuid)
                .queryParam("prompt", "Vue.js test with UUID session")
                .get("/ai/ask")
            .then()
                .statusCode(200)
                .header("Content-Type", "text/event-stream");
    }

    @Test
    @DisplayName("Prompt with JSON-like content test")
    void testJsonLikePrompt() {
        given()
            .when()
                .queryParam("session", "json-test")
                .queryParam("prompt", "Como passar dados JSON para componentes Vue.js: {name: 'test', value: 123}?")
                .get("/ai/chatbot")
            .then()
                .statusCode(200)
                .header("Content-Type", "text/event-stream");
    }
}