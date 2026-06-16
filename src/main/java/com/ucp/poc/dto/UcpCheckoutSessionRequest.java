package com.ucp.poc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * UCP Checkout Session Request (CREATE)
 *
 * Official UCP specification compliant request for creating a checkout session.
 * Follows progressive disclosure pattern - minimal required, can be enriched later.
 *
 * Spec: https://ucp.dev/schemas/checkout.json
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UcpCheckoutSessionRequest {

    /**
     * Line items (REQUIRED)
     */
    private List<LineItemRequest> line_items;

    /**
     * Buyer information (OPTIONAL - progressive disclosure)
     */
    private BuyerRequest buyer;

    /**
     * Context for localization and intent (OPTIONAL but recommended)
     */
    private ContextRequest context;

    /**
     * Fraud prevention signals (OPTIONAL)
     */
    private Map<String, String> signals;

    /**
     * Attribution data (OPTIONAL)
     */
    private Map<String, String> attribution;

    /**
     * Line Item Request
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItemRequest {
        /**
         * Item reference (REQUIRED)
         */
        private ItemReference item;

        /**
         * Quantity (REQUIRED)
         */
        private Integer quantity;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ItemReference {
            /**
             * Product ID/SKU (REQUIRED)
             */
            private String id;
        }
    }

    /**
     * Buyer Request (flat structure per UCP spec)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BuyerRequest {
        /**
         * First name (OPTIONAL)
         */
        private String first_name;

        /**
         * Last name (OPTIONAL)
         */
        private String last_name;

        /**
         * Email (OPTIONAL but typically required before completion)
         */
        private String email;

        /**
         * Phone number in E.164 format (OPTIONAL)
         * Example: "+14155552671"
         */
        private String phone_number;
    }

    /**
     * Context Request
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextRequest {
        /**
         * ISO 3166-1 alpha-2 country code
         */
        private String address_country;

        /**
         * Region/state code
         */
        private String address_region;

        /**
         * Postal/ZIP code
         */
        private String postal_code;

        /**
         * Buyer's background context/intent
         */
        private String intent;

        /**
         * IETF BCP 47 language tag (e.g., "en", "fr-CA")
         */
        private String language;

        /**
         * ISO 4217 currency code
         */
        private String currency;

        /**
         * Eligibility claims (reverse-domain naming)
         */
        private List<String> eligibility;
    }

    /**
     * Validate required fields
     */
    public void validate() {
        if (line_items == null || line_items.isEmpty()) {
            throw new IllegalArgumentException("line_items is required and cannot be empty");
        }

        for (LineItemRequest item : line_items) {
            if (item.getItem() == null || item.getItem().getId() == null || item.getItem().getId().isEmpty()) {
                throw new IllegalArgumentException("line_item.item.id is required");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("line_item.quantity must be positive");
            }
        }
    }
}
