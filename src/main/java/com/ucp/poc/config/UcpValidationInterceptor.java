package com.ucp.poc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucp.poc.service.UcpSchemaValidator;
import com.ucp.poc.util.UcpResponseWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * UCP Validation Interceptor
 *
 * Intercepts requests to UCP endpoints and validates request bodies against
 * the appropriate JSON schemas from the UCP discovery endpoint.
 *
 * Features:
 * - Validates POST/PATCH/PUT requests to UCP endpoints
 * - Returns 400 Bad Request with validation errors if invalid
 * - Logs validation failures for monitoring
 * - Gracefully handles validation errors (allows request if schema unavailable)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UcpValidationInterceptor implements HandlerInterceptor {

    private final UcpSchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Only validate write operations on UCP endpoints
        if (!isWriteMethod(method) || !isUcpEndpoint(path)) {
            return true;
        }

        // Get capability name for this endpoint
        String capabilityName = getCapabilityForEndpoint(path, method);
        if (capabilityName == null) {
            log.debug("No capability mapping for endpoint: {} {}", method, path);
            return true;
        }

        // Read request body
        String requestBody = getRequestBody(request);
        if (requestBody == null || requestBody.isEmpty()) {
            log.debug("Empty request body for {} {}", method, path);
            return true;
        }

        log.debug("Validating request to {} {} against capability: {}", method, path, capabilityName);

        // Validate request body
        UcpSchemaValidator.ValidationResult validationResult =
                schemaValidator.validateJson(capabilityName, requestBody);

        if (!validationResult.isValid()) {
            log.warn("Validation failed for {} {}: {} errors",
                    method, path, validationResult.getErrors().size());

            // Build error response
            Map<String, Object> errorResponse = UcpResponseWrapper.error(
                    "schema_validation_error",
                    "Request does not match the UCP schema for capability: " + capabilityName,
                    validationResult.toErrorDetails()
            );

            // Send error response
            sendErrorResponse(response, 400, errorResponse);
            return false;
        }

        log.debug("Validation successful for {} {}", method, path);
        return true;
    }

    /**
     * Check if method is a write operation
     */
    private boolean isWriteMethod(String method) {
        return "POST".equalsIgnoreCase(method) ||
               "PUT".equalsIgnoreCase(method) ||
               "PATCH".equalsIgnoreCase(method);
    }

    /**
     * Check if path is a UCP endpoint
     */
    private boolean isUcpEndpoint(String path) {
        return path.startsWith("/checkout-sessions") ||
               path.startsWith("/ucp/");
    }

    /**
     * Get capability name for endpoint
     */
    private String getCapabilityForEndpoint(String path, String method) {
        // Checkout sessions
        if (path.startsWith("/checkout-sessions")) {
            if (path.endsWith("/complete")) {
                return "dev.ucp.shopping.checkout"; // Complete uses same schema
            }
            if ("POST".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
                return "dev.ucp.shopping.checkout";
            }
        }

        // Add more endpoint mappings as needed
        // Example:
        // if (path.startsWith("/ucp/products")) {
        //     return "dev.ucp.shopping.products";
        // }

        return null;
    }

    /**
     * Read request body from request
     */
    private String getRequestBody(HttpServletRequest request) {
        try {
            // Check if request is already wrapped
            if (request instanceof ContentCachingRequestWrapper) {
                ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
                byte[] content = wrapper.getContentAsByteArray();
                if (content.length > 0) {
                    return new String(content, StandardCharsets.UTF_8);
                }
            }

            // Read from input stream (note: this consumes the stream)
            return request.getReader().lines()
                    .reduce("", (accumulator, line) -> accumulator + line);

        } catch (IOException e) {
            log.error("Error reading request body", e);
            return null;
        }
    }

    /**
     * Send error response
     */
    private void sendErrorResponse(HttpServletResponse response, int status, Map<String, Object> errorBody)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String json = objectMapper.writeValueAsString(errorBody);
        response.getWriter().write(json);
        response.getWriter().flush();
    }
}
