package com.ucp.poc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * UCP Complete Request
 *
 * Request to complete a checkout session with payment information.
 * POST /checkout-sessions/{id}/complete
 *
 * Spec: https://ucp.dev/schemas/checkout.json
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UcpCompleteRequest {

    /**
     * Payment instruments (REQUIRED for completion)
     */
    private PaymentRequest payment;

    /**
     * Updated buyer information (OPTIONAL)
     */
    private UcpCheckoutSessionRequest.BuyerRequest buyer;

    /**
     * Fulfillment information (OPTIONAL extension)
     */
    private FulfillmentRequest fulfillment;

    /**
     * Payment Request
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentRequest {
        /**
         * Payment instruments array (REQUIRED)
         */
        private List<PaymentInstrument> instruments;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PaymentInstrument {
            /**
             * Instrument ID (REQUIRED)
             */
            private String id;

            /**
             * Payment handler ID (REQUIRED)
             */
            private String handler_id;

            /**
             * Payment type (REQUIRED)
             * Examples: card, digital_wallet, bank_transfer
             */
            private String type;

            /**
             * Selected flag (REQUIRED)
             */
            private Boolean selected;

            /**
             * Payment credential (REQUIRED)
             */
            private Credential credential;

            /**
             * Billing address (CONDITIONAL - depends on business requirements)
             */
            private Address billing_address;

            /**
             * Display information (OPTIONAL)
             */
            private Display display;

            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class Credential {
                /**
                 * Credential type (REQUIRED)
                 * Values: PAYMENT_GATEWAY, MERCHANT_OF_RECORD
                 */
                private String type;

                /**
                 * Encrypted token (REQUIRED)
                 */
                private String token;

                /**
                 * Additional credential data
                 */
                private Map<String, Object> data;
            }

            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class Address {
                /**
                 * Apartment, suite, unit, building, floor, etc.
                 */
                private String extended_address;

                /**
                 * Street address (REQUIRED for physical addresses)
                 */
                private String street_address;

                /**
                 * City (REQUIRED)
                 */
                private String address_locality;

                /**
                 * State/province/region
                 */
                private String address_region;

                /**
                 * ISO 3166-1 alpha-2 country code (REQUIRED)
                 */
                private String address_country;

                /**
                 * Postal/ZIP code
                 */
                private String postal_code;

                /**
                 * Optional name fields
                 */
                private String first_name;
                private String last_name;
                private String phone_number;
            }

            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class Display {
                /**
                 * Card brand (visa, mastercard, amex, etc.)
                 */
                private String brand;

                /**
                 * Last 4 digits
                 */
                private String last_digits;

                /**
                 * Expiry month (1-12)
                 */
                private Integer exp_month;

                /**
                 * Expiry year (4 digits)
                 */
                private Integer exp_year;
            }
        }
    }

    /**
     * Fulfillment Request (dev.ucp.shopping.fulfillment extension)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FulfillmentRequest {
        /**
         * Fulfillment methods
         */
        private List<FulfillmentMethod> methods;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FulfillmentMethod {
            /**
             * Method type (REQUIRED)
             * Values: shipping, pickup, digital
             */
            private String type;

            /**
             * Selected flag
             */
            private Boolean selected;

            /**
             * Destinations (for shipping)
             */
            private List<Destination> destinations;

            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class Destination {
                /**
                 * Apartment, suite, etc.
                 */
                private String extended_address;

                /**
                 * Street address
                 */
                private String street_address;

                /**
                 * City
                 */
                private String address_locality;

                /**
                 * State/province
                 */
                private String address_region;

                /**
                 * ISO 3166-1 alpha-2 country code
                 */
                private String address_country;

                /**
                 * Postal/ZIP code
                 */
                private String postal_code;

                /**
                 * Recipient name
                 */
                private String first_name;
                private String last_name;

                /**
                 * Contact phone (E.164 format)
                 */
                private String phone_number;
            }
        }
    }

    /**
     * Validate required fields
     */
    public void validate() {
        if (payment == null || payment.getInstruments() == null || payment.getInstruments().isEmpty()) {
            throw new IllegalArgumentException("payment.instruments is required");
        }

        boolean hasSelected = false;
        for (PaymentRequest.PaymentInstrument instrument : payment.getInstruments()) {
            if (instrument.getId() == null || instrument.getId().isEmpty()) {
                throw new IllegalArgumentException("payment.instruments[].id is required");
            }
            if (instrument.getHandler_id() == null || instrument.getHandler_id().isEmpty()) {
                throw new IllegalArgumentException("payment.instruments[].handler_id is required");
            }
            if (instrument.getType() == null || instrument.getType().isEmpty()) {
                throw new IllegalArgumentException("payment.instruments[].type is required");
            }
            if (instrument.getCredential() == null) {
                throw new IllegalArgumentException("payment.instruments[].credential is required");
            }
            if (instrument.getCredential().getType() == null) {
                throw new IllegalArgumentException("payment.instruments[].credential.type is required");
            }
            if (instrument.getCredential().getToken() == null) {
                throw new IllegalArgumentException("payment.instruments[].credential.token is required");
            }
            if (Boolean.TRUE.equals(instrument.getSelected())) {
                hasSelected = true;
            }
        }

        if (!hasSelected) {
            throw new IllegalArgumentException("At least one payment instrument must have selected=true");
        }
    }
}
