package com.ucp.poc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UcpSchemaValidator
 */
@ExtendWith(MockitoExtension.class)
class UcpSchemaValidatorTest {

    @Mock
    private UcpProfileService profileService;

    private UcpSchemaValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        validator = new UcpSchemaValidator(profileService, objectMapper);

        // Load sample schema from classpath
        validator.loadSchemaFromResource(
                "dev.ucp.shopping.checkout",
                "/schemas/checkout-sample.json"
        );
    }

    @Test
    void testValidCheckoutRequest() {
        // Valid checkout request
        Map<String, Object> request = Map.of(
                "line_items", List.of(
                        Map.of(
                                "item", Map.of("id", "prod-123", "title", "Test Product"),
                                "quantity", 2
                        )
                ),
                "buyer", Map.of(
                        "first_name", "John",
                        "last_name", "Doe",
                        "email", "john.doe@example.com",
                        "phone_number", "+14155551234"
                ),
                "context", Map.of(
                        "currency", "USD",
                        "language", "en-US",
                        "address_country", "US"
                )
        );

        UcpSchemaValidator.ValidationResult result = validator.validate(
                "dev.ucp.shopping.checkout",
                request
        );

        assertTrue(result.isValid(), "Valid request should pass validation");
        assertTrue(result.getErrors().isEmpty(), "Should have no errors");
    }

    @Test
    void testInvalidCheckoutRequest_MissingLineItems() {
        // Missing required field: line_items
        Map<String, Object> request = Map.of(
                "buyer", Map.of("email", "john.doe@example.com")
        );

        UcpSchemaValidator.ValidationResult result = validator.validate(
                "dev.ucp.shopping.checkout",
                request
        );

        assertFalse(result.isValid(), "Request without line_items should fail");
        assertFalse(result.getErrors().isEmpty(), "Should have errors");

        // Check error contains reference to missing field
        boolean hasLineItemsError = result.getErrors().stream()
                .anyMatch(error -> error.getMessage().contains("line_items") ||
                                 error.getMessage().contains("required"));
        assertTrue(hasLineItemsError, "Should have error about missing line_items");
    }

    @Test
    void testInvalidCheckoutRequest_InvalidQuantity() {
        // Invalid quantity (must be >= 1)
        Map<String, Object> request = Map.of(
                "line_items", List.of(
                        Map.of(
                                "item", Map.of("id", "prod-123"),
                                "quantity", 0  // Invalid
                        )
                )
        );

        UcpSchemaValidator.ValidationResult result = validator.validate(
                "dev.ucp.shopping.checkout",
                request
        );

        assertFalse(result.isValid(), "Request with invalid quantity should fail");

        // Check error field path
        boolean hasQuantityError = result.getErrors().stream()
                .anyMatch(error -> error.getField().contains("quantity"));
        assertTrue(hasQuantityError, "Should have error about quantity");
    }

    @Test
    void testInvalidCheckoutRequest_InvalidEmail() {
        Map<String, Object> request = Map.of(
                "line_items", List.of(
                        Map.of(
                                "item", Map.of("id", "prod-123"),
                                "quantity", 1
                        )
                ),
                "buyer", Map.of(
                        "email", "not-an-email"  // Invalid email format
                )
        );

        UcpSchemaValidator.ValidationResult result = validator.validate(
                "dev.ucp.shopping.checkout",
                request
        );

        assertFalse(result.isValid(), "Request with invalid email should fail");

        boolean hasEmailError = result.getErrors().stream()
                .anyMatch(error -> error.getField().contains("email"));
        assertTrue(hasEmailError, "Should have error about email");
    }

    @Test
    void testInvalidCheckoutRequest_InvalidCurrency() {
        Map<String, Object> request = Map.of(
                "line_items", List.of(
                        Map.of(
                                "item", Map.of("id", "prod-123"),
                                "quantity", 1
                        )
                ),
                "context", Map.of(
                        "currency", "US"  // Invalid: must be 3 letters
                )
        );

        UcpSchemaValidator.ValidationResult result = validator.validate(
                "dev.ucp.shopping.checkout",
                request
        );

        assertFalse(result.isValid(), "Request with invalid currency should fail");

        boolean hasCurrencyError = result.getErrors().stream()
                .anyMatch(error -> error.getField().contains("currency"));
        assertTrue(hasCurrencyError, "Should have error about currency");
    }

    @Test
    void testValidateJson_ValidJson() {
        String json = """
                {
                    "line_items": [
                        {
                            "item": {"id": "prod-123"},
                            "quantity": 2
                        }
                    ]
                }
                """;

        UcpSchemaValidator.ValidationResult result = validator.validateJson(
                "dev.ucp.shopping.checkout",
                json
        );

        assertTrue(result.isValid(), "Valid JSON should pass validation");
    }

    @Test
    void testValidateJson_InvalidJson() {
        String json = "{ invalid json }";

        UcpSchemaValidator.ValidationResult result = validator.validateJson(
                "dev.ucp.shopping.checkout",
                json
        );

        assertFalse(result.isValid(), "Invalid JSON should fail validation");
        assertEquals(1, result.getErrors().size(), "Should have one error");
        assertEquals("json_parse_error", result.getErrors().get(0).getType());
    }

    @Test
    void testValidation_NoSchemaAvailable() {
        // Capability without schema should allow request (graceful degradation)
        Map<String, Object> request = Map.of("anything", "goes");

        UcpSchemaValidator.ValidationResult result = validator.validate(
                "dev.ucp.unknown.capability",
                request
        );

        assertTrue(result.isValid(), "Should allow request when schema not available");
    }

    @Test
    void testValidationResult_ToErrorDetails() {
        Map<String, Object> request = Map.of(
                "line_items", List.of(
                        Map.of(
                                "item", Map.of("id", "prod-123"),
                                "quantity", -1  // Invalid
                        )
                )
        );

        UcpSchemaValidator.ValidationResult result = validator.validate(
                "dev.ucp.shopping.checkout",
                request
        );

        assertFalse(result.isValid());

        // Convert to error details
        Map<String, Object> errorDetails = result.toErrorDetails();

        assertNotNull(errorDetails);
        assertTrue(errorDetails.containsKey("validation_errors"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) errorDetails.get("validation_errors");
        assertFalse(errors.isEmpty());

        // Check error structure
        Map<String, Object> firstError = errors.get(0);
        assertTrue(firstError.containsKey("field"));
        assertTrue(firstError.containsKey("message"));
        assertTrue(firstError.containsKey("type"));
    }

    @Test
    void testCacheStats() {
        Map<String, Object> stats = validator.getCacheStats();

        assertNotNull(stats);
        assertTrue(stats.containsKey("cached_schemas"));
        assertTrue(stats.containsKey("capabilities"));

        // Should have at least one schema (loaded in setUp)
        Integer cachedCount = (Integer) stats.get("cached_schemas");
        assertTrue(cachedCount >= 1, "Should have at least one cached schema");
    }

    @Test
    void testClearCache() {
        // Load a schema
        validator.loadSchemaFromResource(
                "test.capability",
                "/schemas/checkout-sample.json"
        );

        Map<String, Object> statsBefore = validator.getCacheStats();
        Integer countBefore = (Integer) statsBefore.get("cached_schemas");
        assertTrue(countBefore > 0);

        // Clear cache
        validator.clearCache();

        Map<String, Object> statsAfter = validator.getCacheStats();
        Integer countAfter = (Integer) statsAfter.get("cached_schemas");
        assertEquals(0, countAfter, "Cache should be empty after clear");
    }

    @Test
    void testMinimalValidRequest() {
        // Minimal valid request (only required fields)
        Map<String, Object> request = Map.of(
                "line_items", List.of(
                        Map.of(
                                "item", Map.of("id", "prod-123"),
                                "quantity", 1
                        )
                )
        );

        UcpSchemaValidator.ValidationResult result = validator.validate(
                "dev.ucp.shopping.checkout",
                request
        );

        assertTrue(result.isValid(), "Minimal valid request should pass");
    }

    @Test
    void testEmptyLineItems() {
        // Empty line_items array (should fail: minItems: 1)
        Map<String, Object> request = Map.of(
                "line_items", List.of()
        );

        UcpSchemaValidator.ValidationResult result = validator.validate(
                "dev.ucp.shopping.checkout",
                request
        );

        assertFalse(result.isValid(), "Empty line_items should fail");
    }
}
