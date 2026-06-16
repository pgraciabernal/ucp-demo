package com.ucp.poc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * UCP Schema Validator
 *
 * Validates request/response objects against JSON schemas advertised in UCP discovery.
 * Features:
 * - Fetches schemas from URLs in discovery endpoint
 * - Caches schemas in memory for performance
 * - Validates objects against cached schemas
 * - Returns validation errors with JSONPath field locations
 * - Handles schema fetch failures gracefully
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UcpSchemaValidator {

    private final UcpProfileService profileService;
    private final ObjectMapper objectMapper;

    // Schema cache: capability name -> JsonSchema
    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    // HTTP client for fetching schemas
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // JSON Schema factory
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    /**
     * Validate an object against a capability schema
     *
     * @param capabilityName UCP capability name (e.g., "dev.ucp.shopping.checkout")
     * @param data Object to validate
     * @return ValidationResult with success status and any errors
     */
    public ValidationResult validate(String capabilityName, Object data) {
        try {
            // Get schema for capability
            JsonSchema schema = getSchema(capabilityName);
            if (schema == null) {
                log.warn("No schema available for capability: {}. Allowing request.", capabilityName);
                return ValidationResult.success();
            }

            // Convert data to JsonNode
            JsonNode jsonNode = objectMapper.valueToTree(data);

            // Validate
            Set<ValidationMessage> errors = schema.validate(jsonNode);

            if (errors.isEmpty()) {
                return ValidationResult.success();
            }

            // Convert errors to ValidationError objects
            List<ValidationError> validationErrors = errors.stream()
                    .map(this::convertValidationMessage)
                    .collect(Collectors.toList());

            return ValidationResult.failure(validationErrors);

        } catch (Exception e) {
            log.error("Error validating against schema for capability: {}", capabilityName, e);
            // Allow request on validation error (graceful degradation)
            return ValidationResult.success();
        }
    }

    /**
     * Validate a JSON string against a capability schema
     *
     * @param capabilityName UCP capability name
     * @param jsonString JSON string to validate
     * @return ValidationResult
     */
    public ValidationResult validateJson(String capabilityName, String jsonString) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            return validate(capabilityName, jsonNode);
        } catch (IOException e) {
            log.error("Error parsing JSON for validation", e);
            return ValidationResult.failure(List.of(
                    ValidationError.builder()
                            .field("$")
                            .message("Invalid JSON: " + e.getMessage())
                            .type("json_parse_error")
                            .build()
            ));
        }
    }

    /**
     * Get schema for capability (from cache or fetch)
     */
    private JsonSchema getSchema(String capabilityName) {
        // Check cache first
        if (schemaCache.containsKey(capabilityName)) {
            return schemaCache.get(capabilityName);
        }

        // Fetch schema
        UcpProfileService.Capability capability = profileService.getCapability(capabilityName);
        if (capability == null || capability.getSchema() == null) {
            log.debug("No schema URL for capability: {}", capabilityName);
            return null;
        }

        String schemaUrl = capability.getSchema();
        JsonSchema schema = fetchSchema(schemaUrl);

        if (schema != null) {
            schemaCache.put(capabilityName, schema);
            log.info("Cached schema for capability: {} from URL: {}", capabilityName, schemaUrl);
        }

        return schema;
    }

    /**
     * Fetch schema from URL
     */
    private JsonSchema fetchSchema(String schemaUrl) {
        try {
            log.debug("Fetching schema from URL: {}", schemaUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(schemaUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Failed to fetch schema from {}: HTTP {}", schemaUrl, response.statusCode());
                return null;
            }

            // Parse and create schema
            JsonNode schemaNode = objectMapper.readTree(response.body());
            return schemaFactory.getSchema(schemaNode);

        } catch (Exception e) {
            log.warn("Error fetching schema from {}: {}", schemaUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Load schema from classpath resource (for local testing)
     *
     * @param capabilityName Capability name
     * @param resourcePath Classpath resource path (e.g., "/schemas/checkout.json")
     */
    public void loadSchemaFromResource(String capabilityName, String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.warn("Schema resource not found: {}", resourcePath);
                return;
            }

            JsonNode schemaNode = objectMapper.readTree(is);
            JsonSchema schema = schemaFactory.getSchema(schemaNode);
            schemaCache.put(capabilityName, schema);
            log.info("Loaded schema for capability: {} from resource: {}", capabilityName, resourcePath);

        } catch (Exception e) {
            log.error("Error loading schema from resource: {}", resourcePath, e);
        }
    }

    /**
     * Clear schema cache
     */
    public void clearCache() {
        schemaCache.clear();
        log.info("Schema cache cleared");
    }

    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cached_schemas", schemaCache.size());
        stats.put("capabilities", new ArrayList<>(schemaCache.keySet()));
        return stats;
    }

    /**
     * Convert NetworkNT ValidationMessage to our ValidationError
     */
    private ValidationError convertValidationMessage(ValidationMessage message) {
        String fieldPath = "$";
        if (message.getInstanceLocation() != null) {
            fieldPath = message.getInstanceLocation().toString();
        }

        return ValidationError.builder()
                .field(fieldPath)
                .message(message.getMessage())
                .type(message.getType())
                .build();
    }

    /**
     * Validation Result
     */
    @Data
    public static class ValidationResult {
        private final boolean valid;
        private final List<ValidationError> errors;

        public static ValidationResult success() {
            return new ValidationResult(true, Collections.emptyList());
        }

        public static ValidationResult failure(List<ValidationError> errors) {
            return new ValidationResult(false, errors);
        }

        /**
         * Convert to UCP error response format
         */
        public Map<String, Object> toErrorDetails() {
            Map<String, Object> details = new HashMap<>();
            details.put("validation_errors", errors.stream()
                    .map(error -> {
                        Map<String, Object> errorMap = new HashMap<>();
                        errorMap.put("field", error.getField());
                        errorMap.put("message", error.getMessage());
                        errorMap.put("type", error.getType());
                        return errorMap;
                    })
                    .collect(Collectors.toList()));
            return details;
        }
    }

    /**
     * Validation Error
     */
    @Data
    @lombok.Builder
    public static class ValidationError {
        private final String field;      // JSONPath to the field (e.g., "$.line_items[0].quantity")
        private final String message;    // Human-readable error message
        private final String type;       // Error type (e.g., "required", "type", "pattern")
    }
}
