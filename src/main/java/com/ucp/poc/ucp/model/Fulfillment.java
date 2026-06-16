package com.ucp.poc.ucp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * UCP Fulfillment Object
 *
 * Represents fulfillment methods and destinations.
 *
 * @see <a href="https://ucp.dev/latest/specification">UCP Specification</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fulfillment {
    /**
     * Available fulfillment methods
     */
    @Builder.Default
    private List<FulfillmentMethod> methods = new ArrayList<>();

    /**
     * Fulfillment destinations (shipping addresses)
     */
    @Builder.Default
    private List<FulfillmentDestination> destinations = new ArrayList<>();

    /**
     * Fulfillment Method
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FulfillmentMethod {
        /**
         * Method ID (e.g., "standard", "express")
         */
        private String id;

        /**
         * Display label
         */
        private String label;

        /**
         * Cost
         */
        private BigDecimal cost;

        /**
         * Whether this method is selected
         */
        private Boolean selected;

        /**
         * Estimated delivery time
         */
        private String estimated_delivery;
    }

    /**
     * Fulfillment Destination (Shipping Address)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FulfillmentDestination {
        /**
         * Postal address
         */
        private Address address;

        /**
         * Contact information
         */
        private Contact contact;

        /**
         * Address structure
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Address {
            private String country;      // ISO 3166-1 alpha-2
            private String region;       // State/province
            private String city;
            private String postal_code;
            private String line1;        // Street address
            private String line2;        // Apt, suite, etc.
        }

        /**
         * Contact structure
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Contact {
            private String name;
            private String phone;
            private String email;
        }
    }
}
