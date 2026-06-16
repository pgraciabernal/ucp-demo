package com.ucp.poc.ucp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UCP Buyer Object
 *
 * Represents buyer information in a checkout session.
 *
 * @see <a href="https://ucp.dev/latest/specification">UCP Specification</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Buyer {
    /**
     * Buyer email address (required for order confirmation)
     */
    private String email;

    /**
     * Buyer name (optional)
     */
    private Name name;

    /**
     * Buyer phone number (optional)
     */
    private String phone;

    /**
     * Nested Name structure
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Name {
        private String given_name;  // First name
        private String family_name; // Last name
        private String full_name;   // Full name
    }
}
