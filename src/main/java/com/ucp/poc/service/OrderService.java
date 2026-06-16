package com.ucp.poc.service;

import com.ucp.poc.dto.PostalAddress;
import com.ucp.poc.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final ProductService productService;
    private final TaxCalculationService taxCalculationService;
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong orderCounter = new AtomicLong(1);

    /**
     * Creates an order with billing and shipping addresses
     */
    public Order createOrder(String customerId, List<CartItem> items,
                            PostalAddress billingAddress, PostalAddress shippingAddress,
                            String shippingMethod) {
        // Validate stock availability
        for (CartItem item : items) {
            if (!productService.isInStock(item.getProductId(), item.getQuantity())) {
                throw new IllegalStateException("Product " + item.getProductId() + " is out of stock");
            }
        }

        // Validate addresses
        if (billingAddress != null) {
            billingAddress.normalize();
            billingAddress.validate();
        }
        if (shippingAddress != null) {
            shippingAddress.normalize();
            shippingAddress.validate();
        }

        // Calculate totals
        BigDecimal subtotal = items.stream()
                .map(CartItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate tax based on shipping address
        BigDecimal tax = shippingAddress != null
                ? taxCalculationService.calculateTax(shippingAddress, subtotal)
                : BigDecimal.ZERO;

        // Calculate shipping cost (simplified)
        BigDecimal shippingCost = calculateShippingCost(shippingMethod);

        BigDecimal total = subtotal.add(tax).add(shippingCost);

        // Generate identifiers
        String orderId = "order-" + UUID.randomUUID().toString().substring(0, 8);
        String orderNumber = String.format("ORD-%d-%05d",
                LocalDateTime.now().getYear(), orderCounter.getAndIncrement());
        String confirmationNumber = "CONF-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

        // Create order
        Order order = Order.builder()
                .orderId(orderId)
                .orderNumber(orderNumber)
                .confirmationNumber(confirmationNumber)
                .customerId(customerId)
                .items(new ArrayList<>(items))
                .billingAddress(billingAddress)
                .shippingAddress(shippingAddress)
                .subtotal(subtotal)
                .tax(tax)
                .shippingCost(shippingCost)
                .total(total)
                .currency("USD")
                .status(OrderStatus.PENDING)
                .shippingMethod(shippingMethod)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        orders.put(orderId, order);
        log.info("Order created: {} ({})", orderNumber, orderId);
        return order;
    }

    /**
     * Creates an order (legacy method for backward compatibility)
     */
    @Deprecated
    public Order createOrder(String customerId, List<CartItem> items) {
        return createOrder(customerId, items, null, null, "standard");
    }

    /**
     * Calculates shipping cost based on method
     */
    private BigDecimal calculateShippingCost(String shippingMethod) {
        if (shippingMethod == null) {
            return new BigDecimal("5.00"); // Default standard shipping
        }
        return switch (shippingMethod.toLowerCase()) {
            case "express" -> new BigDecimal("15.00");
            case "overnight" -> new BigDecimal("25.00");
            default -> new BigDecimal("5.00"); // Standard
        };
    }

    public Optional<Order> getOrder(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    public List<Order> getOrdersByCustomer(String customerId) {
        return orders.values().stream()
                .filter(o -> o.getCustomerId().equals(customerId))
                .toList();
    }

    public Order updateOrderStatus(String orderId, OrderStatus status) {
        Order order = orders.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    public void confirmOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order != null && order.getStatus() == OrderStatus.PENDING) {
            // Update stock
            for (CartItem item : order.getItems()) {
                productService.updateStock(item.getProductId(), item.getQuantity());
            }
            order.setStatus(OrderStatus.CONFIRMED);
            order.setUpdatedAt(LocalDateTime.now());
        }
    }

    public void cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order != null) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setUpdatedAt(LocalDateTime.now());
        }
    }
}
