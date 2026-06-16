package com.ucp.poc.model;

import com.ucp.poc.dto.PostalAddress;
import com.ucp.poc.util.ISOCurrencyValidator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Model - Schema.org Order compliant
 *
 * Represents a completed order with full customer, payment, and fulfillment information.
 *
 * @see <a href="https://schema.org/Order">Schema.org Order</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    /**
     * Internal order identifier (UUID)
     */
    private String orderId;

    /**
     * Human-readable order number for customer reference
     * Example: "ORD-2024-001234"
     */
    private String orderNumber;

    /**
     * Confirmation number for customer support
     * Example: "CONF-ABC123XYZ"
     */
    private String confirmationNumber;

    /**
     * Customer identifier
     */
    private String customerId;

    /**
     * Customer email for communications
     */
    private String customerEmail;

    /**
     * Customer display name
     */
    private String customerName;

    /**
     * Order items (products and quantities)
     */
    private List<CartItem> items;

    /**
     * Billing address (REQUIRED for invoices)
     */
    private PostalAddress billingAddress;

    /**
     * Shipping address (REQUIRED for fulfillment)
     */
    private PostalAddress shippingAddress;

    /**
     * Order subtotal (before tax and shipping)
     */
    private BigDecimal subtotal;

    /**
     * Tax amount
     */
    private BigDecimal tax;

    /**
     * Shipping cost
     */
    private BigDecimal shippingCost;

    /**
     * Discount amount (if any)
     */
    private BigDecimal discount;

    /**
     * Discount code used (if any)
     */
    private String discountCode;

    /**
     * Order total (subtotal + tax + shipping - discount)
     */
    private BigDecimal total;

    /**
     * ISO 4217 currency code
     */
    private String currency;

    /**
     * Order status (PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED)
     */
    private OrderStatus status;

    /**
     * Shipping method
     * Example: "standard", "express", "overnight"
     */
    private String shippingMethod;

    /**
     * Tracking number for shipment
     */
    private String trackingNumber;

    /**
     * Tracking URL for shipment
     */
    private String trackingUrl;

    /**
     * Estimated delivery date
     */
    private LocalDateTime estimatedDeliveryDate;

    /**
     * Actual delivery date (when confirmed)
     */
    private LocalDateTime deliveredAt;

    /**
     * Payment identifier
     */
    private String paymentId;

    /**
     * Payment method display info (e.g., "VISA ending in 4242")
     */
    private String paymentMethodDisplay;

    /**
     * Timestamp when order was created
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp when order was last updated
     */
    private LocalDateTime updatedAt;

    /**
     * Timestamp when order was confirmed
     */
    private LocalDateTime confirmedAt;

    /**
     * Timestamp when order was shipped
     */
    private LocalDateTime shippedAt;

    /**
     * Order notes or special instructions
     */
    private String notes;

    /**
     * Validates the order data
     */
    public void validate() {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }
        if (billingAddress == null) {
            throw new IllegalArgumentException("Billing address is required");
        }
        if (shippingAddress == null) {
            throw new IllegalArgumentException("Shipping address is required");
        }
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Order total must be positive");
        }
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }

        // Validate addresses
        billingAddress.validate();
        shippingAddress.validate();

        // Validate currency
        ISOCurrencyValidator.validate(currency);

        // Validate email if present
        if (customerEmail != null && !customerEmail.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new IllegalArgumentException("Invalid customer email format");
        }
    }

    /**
     * Normalizes order data
     */
    public void normalize() {
        if (currency != null) {
            currency = ISOCurrencyValidator.normalize(currency);
        }
        if (customerEmail != null) {
            customerEmail = customerEmail.trim().toLowerCase();
        }
        if (customerName != null) {
            customerName = customerName.trim();
        }
        if (shippingMethod != null) {
            shippingMethod = shippingMethod.trim().toLowerCase();
        }
        if (billingAddress != null) {
            billingAddress.normalize();
        }
        if (shippingAddress != null) {
            shippingAddress.normalize();
        }
    }

    /**
     * Calculates order total from components
     */
    public void calculateTotal() {
        BigDecimal sub = subtotal != null ? subtotal : BigDecimal.ZERO;
        BigDecimal tx = tax != null ? tax : BigDecimal.ZERO;
        BigDecimal ship = shippingCost != null ? shippingCost : BigDecimal.ZERO;
        BigDecimal disc = discount != null ? discount : BigDecimal.ZERO;

        total = sub.add(tx).add(ship).subtract(disc);
    }

    /**
     * Checks if order can be cancelled
     */
    public boolean isCancellable() {
        return status == OrderStatus.PENDING || status == OrderStatus.CONFIRMED;
    }

    /**
     * Checks if order is in a final state
     */
    public boolean isFinal() {
        return status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED;
    }

    /**
     * Gets order status display text
     */
    public String getStatusDisplay() {
        if (status == null) {
            return "Unknown";
        }
        return switch (status) {
            case PENDING -> "Pending";
            case CONFIRMED -> "Confirmed";
            case PROCESSING -> "Processing";
            case SHIPPED -> "Shipped";
            case DELIVERED -> "Delivered";
            case CANCELLED -> "Cancelled";
        };
    }
}
