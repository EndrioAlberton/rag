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

package dev.orion.rag.domain.port.out;

/**
 * Port for sending messages to messaging platforms.
 * Driven port (out) — the domain defines the contract, infrastructure implements it.
 */
public interface MessageSenderPort {

    /**
     * Sends a text message to a recipient.
     *
     * @param to               recipient identifier
     * @param text             message content
     * @param overridePhoneId  business phone number ID override (or null for default)
     * @return true if sent successfully
     */
    boolean sendTextMessage(String to, String text, String overridePhoneId);

    /**
     * Sends a typing indicator / read receipt to a recipient.
     *
     * @param to               recipient identifier
     * @param messageId        the message ID to mark as read
     * @param overridePhoneId  business phone number ID override (or null for default)
     * @return true if sent successfully
     */
    boolean sendTypingIndicator(String to, String messageId, String
        overridePhoneId);

    /**
     * Sends an emoji reaction to a message.
     *
     * @param to               recipient identifier
     * @param messageId        the message ID to react to
     * @param emoji            the emoji to send (empty string to clear)
     * @param overridePhoneId  business phone number ID override (or null for default)
     * @return true if sent successfully
     */
    boolean sendReaction(String to, String messageId, String emoji, String
        overridePhoneId);
}
