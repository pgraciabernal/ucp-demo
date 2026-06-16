package com.ucp.poc.model;

import com.ucp.poc.util.ISOCurrencyValidator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment Model - PCI DSS Compliant
 *
 * Represents a payment transaction with secure tokenization.
 * NEVER stores raw card numbers or sensitive payment data.
 *
 * SECURITY NOTICE:
 * - paymentToken contains tokenized payment method reference
 * - cardLast4 and cardBrand are safe for display
 * - Raw card data must NEVER be stored in this model
 *
 * @see <a href="https://www.pcisecuritystandards.org/">PCI DSS Standards</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    /**
     * Unique payment identifier
     */
    private String paymentId;

    /**
     * Associated order identifier
     */
    private String orderId;

    /**
     * Payment amount
     */
    private BigDecimal amount;

    /**
     * ISO 4217 currency code (e.g., USD, EUR, GBP)
     */
    private String currency;

    /**
     * Payment status (PENDING, AUTHORIZED, CAPTURED, FAILED, REFUNDED)
     */
    private PaymentStatus status;

    /**
     * Payment method type identifier
     * Examples: "credit_card", "paypal", "google_pay", "apple_pay"
     */
    private String paymentMethodType;

    /**
     * Tokenized payment method reference from payment processor
     * NEVER contains raw card numbers
     *
     * Example: "tok_1234567890abcdef" (Stripe token)
     */
    private String paymentToken;

    /**
     * Last 4 digits of card (safe to display)
     * Example: "4242"
     */
    private String cardLast4;

    /**
     * Card brand (safe to display)
     * Example: "VISA", "MASTERCARD", "AMEX", "DISCOVER"
     */
    private String cardBrand;

    /**
     * Payment service provider identifier
     * Example: "stripe", "paypal", "square", "adyen"
     */
    private String paymentProvider;

    /**
     * Payment gateway transaction ID
     */
    private String transactionId;

    /**
     * Authorization code from payment processor
     */
    private String authorizationCode;

    /**
     * Amount authorized (may differ from captured amount)
     */
    private BigDecimal authorizedAmount;

    /**
     * Amount actually captured/charged
     */
    private BigDecimal capturedAmount;

    /**
     * Amount refunded (if any)
     */
    private BigDecimal refundedAmount;

    /**
     * Reason for refund (if refunded)
     */
    private String refundReason;

    /**
     * Error code if payment failed
     */
    private String errorCode;

    /**
     * Error message if payment failed
     */
    private String errorMessage;

    /**
     * Timestamp when payment was created
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp when payment was authorized
     */
    private LocalDateTime authorizedAt;

    /**
     * Timestamp when payment was captured
     */
    private LocalDateTime capturedAt;

    /**
     * Timestamp when payment was refunded (if applicable)
     */
    private LocalDateTime refundedAt;

    /**
     * Validates the payment data
     */
    public void validate() {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }
        ISOCurrencyValidator.validate(currency);

        if (paymentMethodType == null || paymentMethodType.trim().isEmpty()) {
            throw new IllegalArgumentException("Payment method type is required");
        }

        // For credit card payments, ensure tokenization
        if ("credit_card".equalsIgnoreCase(paymentMethodType)) {
            if (paymentToken == null || paymentToken.trim().isEmpty()) {
                throw new IllegalArgumentException("Payment token is required for credit card payments");
            }
            // SECURITY: Verify no raw card numbers are present
            // Check for 13-19 consecutive digits (credit card numbers)
            if (paymentToken.matches(".*\\d{13,19}.*")) {
                throw new IllegalArgumentException(
                    String.format("SECURITY VIOLATION: Raw card number detected in payment token. " +
                        "Token must be tokenized (e.g., tok_xxx). Received token: %s",
                        paymentToken.length() > 20 ? paymentToken.substring(0, 20) + "..." : paymentToken)
                );
            }
        }
    }

    /**
     * Normalizes payment data
     */
    public void normalize() {
        if (currency != null) {
            currency = ISOCurrencyValidator.normalize(currency);
        }
        if (paymentMethodType != null) {
            paymentMethodType = paymentMethodType.trim().toLowerCase();
        }
        if (cardBrand != null) {
            cardBrand = cardBrand.trim().toUpperCase();
        }
        if (paymentProvider != null) {
            paymentProvider = paymentProvider.trim().toLowerCase();
        }
    }

    /**
     * Returns display-safe payment method information
     */
    public String getDisplayPaymentMethod() {
        if (cardLast4 != null && cardBrand != null) {
            return cardBrand + " ending in " + cardLast4;
        }
        return paymentMethodType != null ? paymentMethodType : "Unknown";
    }

    /**
     * Checks if payment is successfully completed
     */
    public boolean isSuccessful() {
        return status == PaymentStatus.CAPTURED || status == PaymentStatus.AUTHORIZED;
    }

    /**
     * Checks if payment can be refunded
     */
    public boolean isRefundable() {
        if (status != PaymentStatus.CAPTURED) {
            return false;
        }
        if (refundedAmount != null && refundedAmount.compareTo(capturedAmount) >= 0) {
            return false; // Already fully refunded
        }
        return true;
    }

    /**
     * Gets remaining refundable amount
     */
    public BigDecimal getRefundableAmount() {
        if (!isRefundable()) {
            return BigDecimal.ZERO;
        }
        BigDecimal refunded = refundedAmount != null ? refundedAmount : BigDecimal.ZERO;
        BigDecimal captured = capturedAmount != null ? capturedAmount : amount;
        return captured.subtract(refunded);
    }
}
