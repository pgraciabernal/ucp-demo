package com.ucp.poc.ucp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UCP Payment Info
 *
 * Represents payment information in checkout session.
 *
 * @see <a href="https://ucp.dev/latest/specification">UCP Specification</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInfo {
    /**
     * Payment method
     */
    private PaymentMethod method;

    /**
     * Payment handler configuration
     */
    private PaymentHandler handler;

    /**
     * Payment status
     * Values: "incomplete", "authorized", "captured", "failed"
     */
    private String status;

    /**
     * Payment Method
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethod {
        /**
         * Method type (e.g., "card", "paypal")
         */
        private String type;

        /**
         * Payment token from processor
         */
        private String token;

        /**
         * Card details (for display)
         */
        private CardDetails card;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CardDetails {
            private String brand;     // "visa", "mastercard", etc.
            private String last4;     // Last 4 digits
            private String exp_month;
            private String exp_year;
        }
    }

    /**
     * Payment Handler configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentHandler {
        /**
         * Handler ID (e.g., "com.stripe.payment")
         */
        private String id;

        /**
         * Handler configuration
         */
        private Object config;
    }
}
