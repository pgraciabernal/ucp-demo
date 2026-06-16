package com.ucp.poc.controller;

import com.ucp.poc.dto.a2a.A2aMessage;
import com.ucp.poc.dto.a2a.A2aOperationRegistry;
import com.ucp.poc.service.A2aMessageRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A2A (Agent-to-Agent) Protocol Controller
 *
 * Provides both synchronous (HTTP POST) and asynchronous (WebSocket) A2A messaging
 * Coexists with REST endpoints - agents can choose their preferred protocol
 */
@RestController
@RequestMapping("/a2a")
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ucp.protocol", name = "a2a.enabled", havingValue = "true", matchIfMissing = true)
public class A2aController {

    private final A2aMessageRouter messageRouter;

    /**
     * Synchronous A2A message endpoint (HTTP POST)
     *
     * Agents can send A2A messages via HTTP POST for request-response patterns
     * This is simpler than WebSocket for stateless interactions
     */
    @PostMapping("/message")
    public ResponseEntity<A2aMessage> handleMessage(@RequestBody A2aMessage request) {
        log.info("Received A2A message: operation={}, messageId={}", request.getOperation(), request.getMessageId());

        // Validate message
        if (request.getMessageId() == null || request.getMessageId().isBlank()) {
            request.setMessageId(UUID.randomUUID().toString());
        }

        if (request.getMetadata() == null) {
            request.setMetadata(A2aMessage.MessageMetadata.builder()
                .timestamp(Instant.now())
                .protocolVersion("2026-04-08")
                .build());
        }

        // Route message
        A2aMessage response = messageRouter.routeMessage(request);

        // Return response
        return ResponseEntity.ok(response);
    }

    /**
     * Get A2A capabilities
     *
     * Returns list of supported operations and their descriptions
     */
    @GetMapping("/capabilities")
    public ResponseEntity<Map<String, Object>> getCapabilities() {
        Map<String, A2aOperationRegistry.OperationDefinition> operations =
            messageRouter.getSupportedOperations();

        Map<String, Object> capabilities = Map.of(
            "protocol", "A2A",
            "version", "2026-04-08",
            "transport", Map.of(
                "http", Map.of(
                    "enabled", true,
                    "endpoint", "/a2a/message",
                    "method", "POST"
                ),
                "websocket", Map.of(
                    "enabled", true,
                    "endpoint", "/a2a/ws"
                )
            ),
            "operations", operations,
            "messageFormat", Map.of(
                "request", Map.of(
                    "messageId", "string (UUID)",
                    "type", "REQUEST",
                    "from", "string (agent identifier)",
                    "to", "string (business identifier)",
                    "operation", "string (see operations list)",
                    "payload", "object (operation-specific)",
                    "metadata", "object (optional)"
                ),
                "response", Map.of(
                    "messageId", "string (UUID)",
                    "type", "RESPONSE or ERROR",
                    "correlationId", "string (matches request messageId)",
                    "payload", "object (operation result)",
                    "error", "object (only for ERROR type)"
                )
            )
        );

        return ResponseEntity.ok(capabilities);
    }

    /**
     * Health check for A2A endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "protocol", "A2A",
            "transport", "HTTP + WebSocket",
            "timestamp", Instant.now()
        ));
    }
}
