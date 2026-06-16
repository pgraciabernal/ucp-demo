package com.ucp.poc.dto;

import com.ucp.poc.model.CartItem;
import com.ucp.poc.model.Order;
import com.ucp.poc.model.Payment;
import com.ucp.poc.ucp.model.Buyer;
import com.ucp.poc.ucp.model.Fulfillment;
import com.ucp.poc.ucp.model.LineItem;
import com.ucp.poc.ucp.model.TotalItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * UCP-Compliant Order DTO
 *
 * Follows UCP order schema with snake_case naming and proper structure.
 *
 * @see <a href="https://ucp.dev/schemas/order.json">UCP Order Schema</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UcpOrder {
    /**
     * Order ID
     */
    private String id;

    /**
     * Order number (human-readable)
     */
    private String order_number;

    /**
     * Order status
     * Values: "pending", "confirmed", "processing", "shipped", "delivered", "cancelled"
     */
    private String status;

    /**
     * Line items
     */
    @Builder.Default
    private List<LineItem> line_items = new ArrayList<>();

    /**
     * Totals breakdown
     */
    @Builder.Default
    private List<TotalItem> totals = new ArrayList<>();

    /**
     * Buyer information
     */
    private Buyer buyer;

    /**
     * Fulfillment information
     */
    private Fulfillment fulfillment;

    /**
     * Payment information
     */
    private PaymentSummary payment;

    /**
     * Order timestamps
     */
    private String created_at;
    private String updated_at;
    private String completed_at;

    /**
     * Order permalink URL
     */
    private String permalink_url;

    /**
     * Payment Summary
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSummary {
        /**
         * Payment ID
         */
        private String id;

        /**
         * Payment status
         */
        private String status;

        /**
         * Payment method type
         */
        private String method_type;

        /**
         * Transaction ID from payment processor
         */
        private String transaction_id;
    }

    /**
     * Converts from internal Order model to UCP format
     */
    public static UcpOrder fromOrder(Order order, String baseUrl) {
        UcpOrderBuilder builder = UcpOrder.builder()
                .id(order.getOrderId())
                .order_number(order.getOrderNumber())
                .status(order.getStatus() != null ? order.getStatus().name().toLowerCase() : "pending")
                .created_at(order.getCreatedAt() != null ? order.getCreatedAt().toString() : null)
                .updated_at(order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : null)
                .permalink_url(baseUrl + "/ucp/orders/" + order.getOrderId());

        // Line items
        if (order.getItems() != null) {
            List<LineItem> lineItems = order.getItems().stream()
                    .map(UcpOrder::convertCartItemToLineItem)
                    .collect(Collectors.toList());
            builder.line_items(lineItems);
        }

        // Totals (convert to minor units - cents)
        List<TotalItem> totals = new ArrayList<>();
        totals.add(TotalItem.builder()
                .type("subtotal")
                .amount(convertToCents(order.getSubtotal()))
                .currency(order.getCurrency())
                .build());

        if (order.getTax() != null && order.getTax().compareTo(BigDecimal.ZERO) > 0) {
            totals.add(TotalItem.builder()
                    .type("tax")
                    .amount(convertToCents(order.getTax()))
                    .currency(order.getCurrency())
                    .build());
        }

        if (order.getShippingCost() != null && order.getShippingCost().compareTo(BigDecimal.ZERO) > 0) {
            totals.add(TotalItem.builder()
                    .type("shipping")
                    .amount(convertToCents(order.getShippingCost()))
                    .currency(order.getCurrency())
                    .build());
        }

        totals.add(TotalItem.builder()
                .type("total")
                .amount(convertToCents(order.getTotal()))
                .currency(order.getCurrency())
                .build());

        builder.totals(totals);

        // Buyer
        if (order.getCustomerId() != null) {
            Buyer.BuyerBuilder buyerBuilder = Buyer.builder();

            if (order.getBillingAddress() != null) {
                buyerBuilder.email(order.getBillingAddress().getEmail());

                Buyer.Name name = Buyer.Name.builder()
                        .given_name(order.getBillingAddress().getFirstName())
                        .family_name(order.getBillingAddress().getLastName())
                        .full_name(order.getBillingAddress().getFirstName() + " " +
                                order.getBillingAddress().getLastName())
                        .build();
                buyerBuilder.name(name);
            }

            builder.buyer(buyerBuilder.build());
        }

        // Fulfillment
        if (order.getShippingAddress() != null || order.getShippingMethod() != null) {
            Fulfillment.FulfillmentBuilder fulfillmentBuilder = Fulfillment.builder();

            if (order.getShippingAddress() != null) {
                PostalAddress shippingAddr = order.getShippingAddress();

                Fulfillment.FulfillmentDestination destination = Fulfillment.FulfillmentDestination.builder()
                        .address(Fulfillment.FulfillmentDestination.Address.builder()
                                .country(shippingAddr.getCountryCode())
                                .region(shippingAddr.getRegion())
                                .city(shippingAddr.getCity())
                                .postal_code(shippingAddr.getPostalCode())
                                .line1(shippingAddr.getAddressLine1())
                                .line2(shippingAddr.getAddressLine2())
                                .build())
                        .contact(Fulfillment.FulfillmentDestination.Contact.builder()
                                .name(shippingAddr.getFirstName() + " " + shippingAddr.getLastName())
                                .email(shippingAddr.getEmail())
                                .phone(shippingAddr.getPhoneNumber())
                                .build())
                        .build();

                fulfillmentBuilder.destinations(List.of(destination));
            }

            if (order.getShippingMethod() != null) {
                Fulfillment.FulfillmentMethod method = Fulfillment.FulfillmentMethod.builder()
                        .id(order.getShippingMethod())
                        .label(formatShippingMethodName(order.getShippingMethod()))
                        .cost(order.getShippingCost() != null ? order.getShippingCost() : null)
                        .selected(true)
                        .build();

                fulfillmentBuilder.methods(List.of(method));
            }

            builder.fulfillment(fulfillmentBuilder.build());
        }

        return builder.build();
    }

    /**
     * Converts CartItem to UCP LineItem (prices in minor units - cents)
     */
    private static LineItem convertCartItemToLineItem(CartItem cartItem) {
        return LineItem.builder()
                .item(LineItem.ItemReference.builder()
                        .id(cartItem.getProductId())
                        .build())
                .quantity(cartItem.getQuantity())
                .unit_price(convertToCents(cartItem.getUnitPrice()))
                .total_price(convertToCents(cartItem.getTotalPrice()))
                .name(cartItem.getProductName())
                .build();
    }

    /**
     * Convert BigDecimal (dollars) to Long (cents)
     */
    private static Long convertToCents(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.multiply(new BigDecimal("100")).longValue();
    }

    /**
     * Formats shipping method name
     */
    private static String formatShippingMethodName(String method) {
        if (method == null) return "Standard Shipping";
        switch (method.toLowerCase()) {
            case "standard":
                return "Standard Shipping";
            case "express":
                return "Express Shipping";
            case "overnight":
                return "Overnight Shipping";
            default:
                return method;
        }
    }

    /**
     * Adds payment information to the order
     */
    public static UcpOrder withPayment(UcpOrder order, Payment payment) {
        if (payment != null) {
            order.setPayment(PaymentSummary.builder()
                    .id(payment.getPaymentId())
                    .status(payment.getStatus() != null ? payment.getStatus().name().toLowerCase() : "pending")
                    .method_type("card") // Payment model doesn't have method, default to card
                    .transaction_id(payment.getTransactionId())
                    .build());
        }
        return order;
    }
}
