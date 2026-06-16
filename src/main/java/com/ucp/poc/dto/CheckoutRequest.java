package com.ucp.poc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Checkout Request DTO
 *
 * Complete checkout request with customer information, items, addresses, and payment details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {
    /**
     * Customer identifier
     */
    private String customerId;

    /**
     * Customer email (REQUIRED for order confirmation)
     */
    private String customerEmail;

    /**
     * Customer phone number (optional)
     */
    private String customerPhone;

    /**
     * Order items
     */
    private List<ItemRequest> items;

    /**
     * Payment method type or card brand
     * Example: "VISA", "MASTERCARD", "AMEX"
     */
    private String paymentMethod;

    /**
     * Payment token (from payment processor)
     * For secure payment processing, use tokenized payment
     */
    private String paymentToken;

    /**
     * Shipping method
     * Example: "standard", "express", "overnight"
     */
    private String shippingMethod;

    /**
     * Billing address (REQUIRED)
     */
    private PostalAddress billingAddress;

    /**
     * Shipping address (REQUIRED)
     * If not provided, defaults to billing address
     */
    private PostalAddress shippingAddress;

    /**
     * ISO 4217 currency code
     * Example: "USD", "EUR", "GBP"
     */
    private String currency;

    /**
     * Discount/coupon code (optional)
     */
    private String discountCode;

    /**
     * Item Request (individual cart item)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRequest {
        /**
         * Product identifier
         */
        private String productId;

        /**
         * Quantity
         */
        private Integer quantity;

        /**
         * Selected product options/variants (optional)
         * Example: {"size": "L", "color": "Blue"}
         */
        private Map<String, String> selectedOptions;
    }

    /**
     * Returns shipping address, defaulting to billing address if not set
     */
    public PostalAddress getEffectiveShippingAddress() {
        return shippingAddress != null ? shippingAddress : billingAddress;
    }

    /**
     * Validates the checkout request
     */
    public void validate() {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        if (customerEmail == null || customerEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer email is required");
        }
        if (!customerEmail.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new IllegalArgumentException("Invalid email format");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("At least one item is required");
        }
        if (billingAddress == null) {
            throw new IllegalArgumentException("Billing address is required");
        }
        if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
            throw new IllegalArgumentException("Payment method is required");
        }

        // Validate addresses
        billingAddress.validate();
        if (shippingAddress != null) {
            shippingAddress.validate();
        }
    }
}

