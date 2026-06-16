package com.ucp.poc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * UCP Checkout Session Response
 *
 * Official UCP specification compliant response for checkout sessions.
 *
 * Spec: https://ucp.dev/schemas/checkout.json
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UcpCheckoutSessionResponse {

    /**
     * Checkout session ID (REQUIRED)
     */
    private String id;

    /**
     * Session status (REQUIRED)
     * Values: incomplete, requires_escalation, ready_for_complete, complete_in_progress, completed, canceled
     */
    private String status;

    /**
     * ISO 4217 currency code (REQUIRED)
     */
    private String currency;

    /**
     * Line items with expanded details (REQUIRED)
     */
    @Builder.Default
    private List<LineItemResponse> line_items = new ArrayList<>();

    /**
     * Totals breakdown (REQUIRED - must include subtotal and total)
     */
    @Builder.Default
    private List<TotalItemResponse> totals = new ArrayList<>();

    /**
     * Legal/policy links (REQUIRED)
     */
    @Builder.Default
    private List<LinkResponse> links = new ArrayList<>();

    /**
     * Buyer information (OPTIONAL - progressive disclosure)
     */
    private BuyerResponse buyer;

    /**
     * Messages (errors, warnings, info)
     */
    @Builder.Default
    private List<MessageResponse> messages = new ArrayList<>();

    /**
     * Escalation URL (REQUIRED when status = requires_escalation)
     */
    private String continue_url;

    /**
     * Session expiration (RFC 3339 timestamp)
     */
    private String expires_at;

    /**
     * Payment information (after completion)
     */
    private PaymentResponse payment;

    /**
     * Order confirmation (when status = completed)
     */
    private OrderResponse order;

    /**
     * Line Item Response
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItemResponse {
        /**
         * Line item ID
         */
        private String id;

        /**
         * Item details (REQUIRED)
         */
        private ItemDetails item;

        /**
         * Quantity
         */
        private Integer quantity;

        /**
         * Line-level totals
         */
        @Builder.Default
        private List<TotalItemResponse> totals = new ArrayList<>();

        /**
         * Parent line item ID (for bundled items)
         */
        private String parent_id;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ItemDetails {
            /**
             * Product ID/SKU
             */
            private String id;

            /**
             * Product title (REQUIRED)
             */
            private String title;

            /**
             * Unit price (REQUIRED)
             * UCP spec: integer in minor units (cents)
             */
            private Long price;

            /**
             * Product image URL
             */
            private String image_url;

            /**
             * Product description
             */
            private String description;
        }
    }

    /**
     * Total Item Response
     * UCP uses SIGNED INTEGERS (cents) not decimals
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TotalItemResponse {
        /**
         * Total type (REQUIRED)
         * Well-known: subtotal, discount, items_discount, fulfillment, tax, fee, total
         */
        private String type;

        /**
         * Amount in cents (REQUIRED)
         * Signed integer: positive = additive, negative = subtractive
         */
        private Long amount;

        /**
         * Display text (OPTIONAL)
         */
        private String display_text;

        /**
         * Nested line breakdown (OPTIONAL)
         */
        private List<TotalItemResponse> lines;
    }

    /**
     * Amount Response (price object)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmountResponse {
        /**
         * Amount in cents (signed integer)
         */
        private Long amount;

        /**
         * ISO 4217 currency code
         */
        private String currency;
    }

    /**
     * Link Response
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkResponse {
        /**
         * Link type (REQUIRED)
         * Examples: terms, privacy, support
         */
        private String type;

        /**
         * URL (REQUIRED)
         */
        private String url;

        /**
         * Display title (OPTIONAL)
         */
        private String title;
    }

    /**
     * Buyer Response (flat structure per UCP spec)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BuyerResponse {
        private String first_name;
        private String last_name;
        private String email;
        private String phone_number;
    }

    /**
     * Message Response
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        /**
         * Message level
         * Values: info, warning, error
         */
        private String level;

        /**
         * Message code
         */
        private String code;

        /**
         * Human-readable message
         */
        private String text;

        /**
         * Error severity (for level=error)
         * Values: recoverable, requires_buyer_input, requires_buyer_review, unrecoverable
         */
        private String severity;

        /**
         * Field reference (for validation errors)
         */
        private String field;
    }

    /**
     * Payment Response
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentResponse {
        /**
         * Payment ID (after completion)
         */
        private String id;

        /**
         * Payment status (after completion)
         */
        private String status;

        /**
         * Available payment handlers (REQUIRED in create response)
         */
        @Builder.Default
        private List<Object> handlers = new ArrayList<>();

        /**
         * Payment instruments (empty in create, populated in complete)
         */
        @Builder.Default
        private List<Object> instruments = new ArrayList<>();
    }

    /**
     * Order Response
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderResponse {
        /**
         * Order ID
         */
        private String id;

        /**
         * Order label/number
         */
        private String label;

        /**
         * Order permalink URL
         */
        private String permalink_url;
    }

    /**
     * Helper: Add info message
     */
    public void addInfoMessage(String code, String text) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(MessageResponse.builder()
                .level("info")
                .code(code)
                .text(text)
                .build());
    }

    /**
     * Helper: Add error message
     */
    public void addErrorMessage(String code, String text, String severity) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(MessageResponse.builder()
                .level("error")
                .code(code)
                .text(text)
                .severity(severity)
                .build());
    }
}
