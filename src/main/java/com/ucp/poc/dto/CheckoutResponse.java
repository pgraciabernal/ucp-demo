package com.ucp.poc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Checkout Response DTO
 *
 * Complete checkout response with order details, payment confirmation, and tracking information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResponse {
    /**
     * Response status: "success" or "error"
     */
    private String status;

    /**
     * Human-readable message
     */
    private String message;

    /**
     * Internal order identifier (UUID)
     */
    private String orderId;

    /**
     * Human-readable order number
     * Example: "ORD-2024-00001"
     */
    private String orderNumber;

    /**
     * Confirmation number for customer support
     * Example: "CONF-ABC123XYZ"
     */
    private String confirmationNumber;

    /**
     * Order permalink URL
     * Customer can use this to view order details
     */
    private String orderUrl;

    /**
     * Payment identifier
     */
    private String paymentId;

    /**
     * Transaction identifier from payment processor
     */
    private String transactionId;

    /**
     * Order total amount
     */
    private BigDecimal total;

    /**
     * ISO 4217 currency code
     */
    private String currency;

    /**
     * Order status
     * Example: "PENDING", "CONFIRMED", "PROCESSING"
     */
    private String orderStatus;

    /**
     * Payment status
     * Example: "AUTHORIZED", "CAPTURED", "FAILED"
     */
    private String paymentStatus;

    /**
     * Shipping method selected
     * Example: "standard", "express", "overnight"
     */
    private String shippingMethod;

    /**
     * Estimated delivery date
     */
    private LocalDateTime estimatedDeliveryDate;

    /**
     * Tracking number (null initially, updated when shipped)
     */
    private String trackingNumber;

    /**
     * Tracking URL (null initially, updated when shipped)
     */
    private String trackingUrl;

    /**
     * Error code (for failed checkouts)
     */
    private String errorCode;

    /**
     * Error details (for failed checkouts)
     */
    private String errorDetails;

    /**
     * Checks if checkout was successful
     */
    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status);
    }

    /**
     * Checks if checkout failed
     */
    public boolean isError() {
        return "error".equalsIgnoreCase(status);
    }
}

