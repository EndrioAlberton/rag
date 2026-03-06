/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.application.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO para o payload do webhook da WhatsApp Cloud API (Meta).
 * Structure based on official documentation:
 * https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks/components
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsAppWebhookPayload {

    @JsonProperty("object")
    public String object;

    @JsonProperty("entry")
    public List<WebhookEntry> entry;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookEntry {
        @JsonProperty("id")
        public String id;

        @JsonProperty("changes")
        public List<WebhookChange> changes;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookChange {
        @JsonProperty("value")
        public WebhookValue value;

        @JsonProperty("field")
        public String field;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookValue {
        @JsonProperty("messaging_product")
        public String messagingProduct;

        @JsonProperty("metadata")
        public WebhookMetadata metadata;

        @JsonProperty("contacts")
        public List<WebhookContact> contacts;

        @JsonProperty("messages")
        public List<WebhookMessage> messages;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookMetadata {
        @JsonProperty("display_phone_number")
        public String displayPhoneNumber;

        @JsonProperty("phone_number_id")
        public String phoneNumberId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookContact {
        @JsonProperty("profile")
        public WebhookProfile profile;

        @JsonProperty("wa_id")
        public String waId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookProfile {
        @JsonProperty("name")
        public String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookMessage {
        @JsonProperty("from")
        public String from;

        @JsonProperty("id")
        public String id;

        @JsonProperty("timestamp")
        public String timestamp;

        @JsonProperty("type")
        public String type;

        @JsonProperty("text")
        public WebhookText text;

        @JsonProperty("audio")
        public WebhookAudio audio;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookText {
        @JsonProperty("body")
        public String body;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookAudio {
        @JsonProperty("id")
        public String id;

        @JsonProperty("mime_type")
        public String mimeType;

        @JsonProperty("sha256")
        public String sha256;
    }
}
