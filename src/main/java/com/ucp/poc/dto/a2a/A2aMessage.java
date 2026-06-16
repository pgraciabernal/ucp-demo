package com.ucp.poc.dto.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * A2A (Agent-to-Agent) Protocol Message
 *
 * Base message format for asynchronous agent communications.
 * Supports request/response patterns with correlation tracking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2aMessage {

    /**
     * Unique message identifier (UUID)
     */
    private String messageId;

    /**
     * Message type (request, response, error, notification)
     */
    private MessageType type;

    /**
     * Correlation ID for request-response tracking
     */
    private String correlationId;

    /**
     * Source agent identifier
     */
    private String from;

    /**
     * Destination agent/business identifier
     */
    private String to;

    /**
     * Operation being requested (e.g., "catalog.search", "checkout.create")
     */
    private String operation;

    /**
     * Message payload (operation-specific data)
     */
    private Map<String, Object> payload;

    /**
     * Message metadata (headers, timestamps, routing info)
     */
    private MessageMetadata metadata;

    /**
     * Error details (only present for error type messages)
     */
    private A2aError error;

    public enum MessageType {
        REQUEST,    // Initial request from agent
        RESPONSE,   // Successful response
        ERROR,      // Error response
        NOTIFICATION // One-way notification (no response expected)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MessageMetadata {
        private Instant timestamp;
        private String protocolVersion;
        private Integer ttl; // Time-to-live in seconds
        private String replyTo; // Address for async responses
        private Map<String, String> headers;
    }
}
