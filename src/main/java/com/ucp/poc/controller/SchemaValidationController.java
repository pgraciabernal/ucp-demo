package com.ucp.poc.controller;

import com.ucp.poc.service.UcpSchemaValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Schema Validation Management Controller
 *
 * Provides endpoints to manage and monitor JSON schema validation.
 * Useful for testing and debugging.
 *
 * Enable with: ucp.validation.management.enabled=true
 */
@Slf4j
@RestController
@RequestMapping("/admin/schema-validation")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ucp.validation.management.enabled", havingValue = "true")
public class SchemaValidationController {

    private final UcpSchemaValidator schemaValidator;

    /**
     * Get schema cache statistics
     * GET /admin/schema-validation/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = schemaValidator.getCacheStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Clear schema cache
     * POST /admin/schema-validation/clear-cache
     */
    @PostMapping("/clear-cache")
    public ResponseEntity<Map<String, Object>> clearCache() {
        schemaValidator.clearCache();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Schema cache cleared");

        return ResponseEntity.ok(response);
    }

    /**
     * Validate arbitrary JSON against a capability schema
     * POST /admin/schema-validation/validate/{capabilityName}
     *
     * Request body: JSON to validate
     */
    @PostMapping("/validate/{capabilityName}")
    public ResponseEntity<Map<String, Object>> validateJson(
            @PathVariable String capabilityName,
            @RequestBody String jsonBody) {

        log.info("Manual validation request for capability: {}", capabilityName);

        UcpSchemaValidator.ValidationResult result = schemaValidator.validateJson(capabilityName, jsonBody);

        Map<String, Object> response = new HashMap<>();
        response.put("capability", capabilityName);
        response.put("valid", result.isValid());

        if (!result.isValid()) {
            response.put("errors", result.getErrors());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Load schema from classpath resource
     * POST /admin/schema-validation/load-resource
     *
     * Request body:
     * {
     *   "capability": "dev.ucp.shopping.checkout",
     *   "resourcePath": "/schemas/checkout-sample.json"
     * }
     */
    @PostMapping("/load-resource")
    public ResponseEntity<Map<String, Object>> loadSchemaFromResource(
            @RequestBody Map<String, String> request) {

        String capability = request.get("capability");
        String resourcePath = request.get("resourcePath");

        if (capability == null || resourcePath == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Missing required fields: capability, resourcePath");
            return ResponseEntity.badRequest().body(error);
        }

        schemaValidator.loadSchemaFromResource(capability, resourcePath);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Schema loaded from resource");
        response.put("capability", capability);
        response.put("resourcePath", resourcePath);

        return ResponseEntity.ok(response);
    }
}
