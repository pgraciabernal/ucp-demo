package com.ucp.poc.ucp.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * UCP Response Wrapper
 *
 * Wraps all UCP responses with the required "ucp" envelope containing
 * version and negotiated capabilities.
 *
 * @see <a href="https://ucp.dev/latest/specification">UCP Specification</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UcpResponse<T> {
    /**
     * UCP metadata envelope
     */
    private UcpEnvelope ucp;

    /**
     * Response payload (CheckoutSession, etc.)
     * Unwrapped so fields appear at root level
     */
    @JsonUnwrapped
    private T payload;

    /**
     * UCP Envelope
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UcpEnvelope {
        /**
         * UCP protocol version
         */
        @Builder.Default
        private String version = "2026-04-08";

        /**
         * Negotiated capabilities
         */
        @Builder.Default
        private Map<String, Object> capabilities = new HashMap<>();

        /**
         * Payment handlers configuration
         */
        private Map<String, Object> payment_handlers;
    }

    /**
     * Creates a UCP response with default envelope
     */
    public static <T> UcpResponse<T> of(T payload) {
        return UcpResponse.<T>builder()
                .ucp(UcpEnvelope.builder()
                        .version("2026-04-08")
                        .capabilities(getDefaultCapabilities())
                        .build())
                .payload(payload)
                .build();
    }

    /**
     * Creates a UCP response with negotiated capabilities
     */
    public static <T> UcpResponse<T> withCapabilities(T payload, Map<String, Object> capabilities) {
        return UcpResponse.<T>builder()
                .ucp(UcpEnvelope.builder()
                        .version("2026-04-08")
                        .capabilities(capabilities)
                        .build())
                .payload(payload)
                .build();
    }

    /**
     * Default capabilities for this business
     */
    private static Map<String, Object> getDefaultCapabilities() {
        Map<String, Object> capabilities = new HashMap<>();

        // dev.ucp.shopping.checkout capability
        Map<String, Object> checkoutCap = new HashMap<>();
        checkoutCap.put("version", "1.0");
        checkoutCap.put("schema", "https://ucp.dev/schemas/checkout.json");
        capabilities.put("dev.ucp.shopping.checkout", checkoutCap);

        // dev.ucp.shopping.fulfillment capability
        Map<String, Object> fulfillmentCap = new HashMap<>();
        fulfillmentCap.put("extends", "dev.ucp.shopping.checkout");
        fulfillmentCap.put("version", "1.0");
        capabilities.put("dev.ucp.shopping.fulfillment", fulfillmentCap);

        return capabilities;
    }
}
