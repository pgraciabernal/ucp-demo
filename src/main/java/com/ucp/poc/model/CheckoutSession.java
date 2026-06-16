package com.ucp.poc.model;

import com.ucp.poc.dto.PostalAddress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Checkout Session Entity
 *
 * Represents a UCP checkout session with lifecycle management.
 * Supports progressive disclosure pattern.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSession {

    /**
     * Session ID (unique)
     */
    private String sessionId;

    /**
     * Session status
     */
    @Builder.Default
    private SessionStatus status = SessionStatus.INCOMPLETE;

    /**
     * Currency
     */
    private String currency;

    /**
     * Line items
     */
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    /**
     * Buyer information (progressive)
     */
    private String buyerFirstName;
    private String buyerLastName;
    private String buyerEmail;
    private String buyerPhone;

    /**
     * Context
     */
    private String contextCountry;
    private String contextLanguage;
    private String contextCurrency;

    /**
     * Fulfillment
     */
    private PostalAddress shippingAddress;
    private String shippingMethod;

    /**
     * Payment instrument reference
     */
    private String paymentInstrumentId;
    private String paymentHandlerId;
    private String paymentType;

    /**
     * Calculated totals
     */
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal shippingCost;
    private BigDecimal total;

    /**
     * Associated order (after completion)
     */
    private String orderId;

    /**
     * Timestamps
     */
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;

    /**
     * Session Status Enum
     */
    public enum SessionStatus {
        /**
         * Session created but missing required information
         */
        INCOMPLETE,

        /**
         * Session requires buyer handoff (3DS, additional auth)
         */
        REQUIRES_ESCALATION,

        /**
         * Session has all required data, ready to complete
         */
        READY_FOR_COMPLETE,

        /**
         * Completion in progress (payment processing)
         */
        COMPLETE_IN_PROGRESS,

        /**
         * Session successfully completed, order created
         */
        COMPLETED,

        /**
         * Session canceled
         */
        CANCELED,

        /**
         * Session expired
         */
        EXPIRED
    }

    /**
     * Check if session is ready for completion
     */
    public boolean isReadyForCompletion() {
        return status == SessionStatus.READY_FOR_COMPLETE &&
                items != null && !items.isEmpty() &&
                buyerEmail != null && !buyerEmail.isEmpty() &&
                paymentInstrumentId != null;
    }

    /**
     * Check if session is expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Calculate totals from items
     */
    public void calculateTotals() {
        this.subtotal = items.stream()
                .map(CartItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Simple tax calculation (10%)
        this.tax = subtotal.multiply(new BigDecimal("0.10"));

        // Simple shipping calculation
        if (shippingMethod != null) {
            switch (shippingMethod.toLowerCase()) {
                case "express":
                    this.shippingCost = new BigDecimal("15.00");
                    break;
                case "overnight":
                    this.shippingCost = new BigDecimal("25.00");
                    break;
                default:
                    this.shippingCost = new BigDecimal("5.00");
            }
        } else {
            this.shippingCost = BigDecimal.ZERO;
        }

        this.total = subtotal.add(tax).add(shippingCost);
    }

    /**
     * Update status based on available data
     */
    public void updateStatus() {
        if (status == SessionStatus.COMPLETED || status == SessionStatus.CANCELED) {
            return; // Terminal states
        }

        if (isExpired()) {
            this.status = SessionStatus.EXPIRED;
            return;
        }

        // Check if we have payment instrument
        if (paymentInstrumentId != null && !paymentInstrumentId.isEmpty()) {
            this.status = SessionStatus.READY_FOR_COMPLETE;
            return;
        }

        // Check if we have minimum buyer info
        if (buyerEmail != null && !buyerEmail.isEmpty()) {
            this.status = SessionStatus.INCOMPLETE; // Still missing payment
            return;
        }

        // Default
        this.status = SessionStatus.INCOMPLETE;
    }
}
