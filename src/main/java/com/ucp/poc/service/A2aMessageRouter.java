package com.ucp.poc.service;

import com.ucp.poc.dto.a2a.A2aError;
import com.ucp.poc.dto.a2a.A2aMessage;
import com.ucp.poc.dto.a2a.A2aOperationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A2A Message Router Service
 *
 * Routes A2A messages to appropriate internal REST handlers
 * Maintains message correlation and async response tracking
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class A2aMessageRouter {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, A2aMessage> pendingResponses = new ConcurrentHashMap<>();

    /**
     * Route an incoming A2A message to the appropriate handler
     */
    public A2aMessage routeMessage(A2aMessage request) {
        log.info("Routing A2A message: {} -> {}", request.getOperation(), request.getMessageId());

        try {
            // Validate request
            if (request.getOperation() == null || request.getOperation().isBlank()) {
                return buildErrorResponse(request, "INVALID_REQUEST",
                    "Operation is required", 400, A2aError.RetryHint.NO_RETRY);
            }

            // Look up operation definition
            A2aOperationRegistry.OperationDefinition opDef =
                A2aOperationRegistry.getOperation(request.getOperation());

            if (opDef == null) {
                return buildErrorResponse(request, "OPERATION_NOT_FOUND",
                    "Operation not supported: " + request.getOperation(), 404, A2aError.RetryHint.NO_RETRY);
            }

            // Route to internal REST handler
            return routeToRestHandler(request, opDef);

        } catch (Exception e) {
            log.error("Error routing A2A message: {}", e.getMessage(), e);
            return buildErrorResponse(request, "INTERNAL_ERROR",
                "Failed to process message: " + e.getMessage(), 500, A2aError.RetryHint.RETRY_WITH_BACKOFF);
        }
    }

    /**
     * Route message to internal REST handler
     */
    private A2aMessage routeToRestHandler(A2aMessage request,
                                           A2aOperationRegistry.OperationDefinition opDef) {
        try {
            // Parse REST equivalent (e.g., "POST /ucp/catalog/search")
            String[] parts = opDef.getRestEquivalent().split(" ", 2);
            String method = parts[0];
            String path = parts[1];

            // Replace path parameters with values from payload
            String resolvedPath = resolvePathParameters(path, request.getPayload());

            // Build URL
            String baseUrl = "http://localhost:8080"; // Internal routing
            String url = baseUrl + resolvedPath;

            // Add query parameters for GET requests
            if ("GET".equals(method) && request.getPayload() != null) {
                UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
                request.getPayload().forEach((key, value) -> {
                    if (!path.contains("{" + key + "}")) { // Not a path param
                        builder.queryParam(key, value);
                    }
                });
                url = builder.toUriString();
            }

            // Execute REST call
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<?> response;

            switch (method) {
                case "GET":
                    response = restTemplate.exchange(url, HttpMethod.GET,
                        new HttpEntity<>(headers), Map.class);
                    break;
                case "POST":
                    response = restTemplate.exchange(url, HttpMethod.POST,
                        new HttpEntity<>(request.getPayload(), headers), Map.class);
                    break;
                case "PUT":
                    response = restTemplate.exchange(url, HttpMethod.PUT,
                        new HttpEntity<>(request.getPayload(), headers), Map.class);
                    break;
                case "DELETE":
                    response = restTemplate.exchange(url, HttpMethod.DELETE,
                        new HttpEntity<>(headers), Map.class);
                    break;
                default:
                    return buildErrorResponse(request, "UNSUPPORTED_METHOD",
                        "HTTP method not supported: " + method, 405, A2aError.RetryHint.NO_RETRY);
            }

            // Build success response
            return buildSuccessResponse(request, response.getBody());

        } catch (Exception e) {
            log.error("Error calling REST handler: {}", e.getMessage(), e);
            return buildErrorResponse(request, "HANDLER_ERROR",
                "Failed to execute operation: " + e.getMessage(), 500, A2aError.RetryHint.RETRY_WITH_BACKOFF);
        }
    }

    /**
     * Resolve path parameters from payload
     */
    private String resolvePathParameters(String path, Map<String, Object> payload) {
        if (payload == null) {
            return path;
        }

        String resolved = path;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (resolved.contains(placeholder)) {
                resolved = resolved.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }
        return resolved;
    }

    /**
     * Build success response message
     */
    private A2aMessage buildSuccessResponse(A2aMessage request, Object responseBody) {
        return A2aMessage.builder()
            .messageId(UUID.randomUUID().toString())
            .type(A2aMessage.MessageType.RESPONSE)
            .correlationId(request.getMessageId())
            .from("ucp-demo-store")
            .to(request.getFrom())
            .operation(request.getOperation())
            .payload(responseBody instanceof Map ? (Map<String, Object>) responseBody : Map.of("data", responseBody))
            .metadata(A2aMessage.MessageMetadata.builder()
                .timestamp(Instant.now())
                .protocolVersion("2026-04-08")
                .build())
            .build();
    }

    /**
     * Build error response message
     */
    private A2aMessage buildErrorResponse(A2aMessage request, String errorCode,
                                          String errorMessage, int statusCode, A2aError.RetryHint retryHint) {
        return A2aMessage.builder()
            .messageId(UUID.randomUUID().toString())
            .type(A2aMessage.MessageType.ERROR)
            .correlationId(request != null ? request.getMessageId() : null)
            .from("ucp-demo-store")
            .to(request != null ? request.getFrom() : "unknown")
            .operation(request != null ? request.getOperation() : null)
            .error(A2aError.builder()
                .code(errorCode)
                .message(errorMessage)
                .statusCode(statusCode)
                .retryHint(retryHint)
                .build())
            .metadata(A2aMessage.MessageMetadata.builder()
                .timestamp(Instant.now())
                .protocolVersion("2026-04-08")
                .build())
            .build();
    }

    /**
     * Get supported operations
     */
    public Map<String, A2aOperationRegistry.OperationDefinition> getSupportedOperations() {
        return A2aOperationRegistry.getAllOperations();
    }
}
