package com.ucp.poc.controller;

import com.ucp.poc.dto.a2a.A2aMessage;
import com.ucp.poc.service.A2aMessageRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.UUID;

/**
 * A2A WebSocket Message Handler
 *
 * Handles asynchronous A2A messages over WebSocket
 * Supports long-lived connections for real-time agent interactions
 */
@Controller
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ucp.protocol", name = "a2a.enabled", havingValue = "true", matchIfMissing = true)
public class A2aWebSocketController {

    private final A2aMessageRouter messageRouter;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle incoming A2A messages over WebSocket
     *
     * Messages sent to /app/a2a/message are routed here
     * Responses are sent back to the same client
     */
    @MessageMapping("/a2a/message")
    @SendTo("/topic/a2a/responses")
    public A2aMessage handleWebSocketMessage(A2aMessage request) {
        log.info("Received A2A WebSocket message: operation={}, messageId={}",
            request.getOperation(), request.getMessageId());

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

        // Send response back to sender
        sendDirectResponse(request.getFrom(), response);

        return response;
    }

    /**
     * Send direct response to specific agent (private channel)
     */
    private void sendDirectResponse(String agentId, A2aMessage response) {
        if (agentId != null && !agentId.isBlank()) {
            String destination = "/queue/a2a/" + agentId;
            messagingTemplate.convertAndSend(destination, response);
            log.debug("Sent A2A response to agent {} via {}", agentId, destination);
        }
    }

    /**
     * Broadcast notification to all connected agents
     */
    public void broadcastNotification(A2aMessage notification) {
        messagingTemplate.convertAndSend("/topic/a2a/notifications", notification);
        log.info("Broadcast A2A notification: {}", notification.getOperation());
    }
}
