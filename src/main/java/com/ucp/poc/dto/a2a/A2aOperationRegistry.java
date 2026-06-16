package com.ucp.poc.dto.a2a;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A2A Operation Registry
 *
 * Maps A2A operation names to REST endpoint equivalents
 * Enables dynamic operation discovery and routing
 */
public class A2aOperationRegistry {

    private static final Map<String, OperationDefinition> operations = new HashMap<>();

    static {
        // Catalog operations
        register("catalog.search", "GET /ucp/catalog/search", "Search product catalog");
        register("catalog.product.get", "GET /ucp/products/{id}", "Get product details");
        register("catalog.products.list", "GET /ucp/products", "List all products");
        register("catalog.lookup", "GET /ucp/catalog/lookup", "Lookup product by ID");

        // Cart operations
        register("cart.add", "POST /ucp/cart/add", "Add item to cart");
        register("cart.get", "GET /ucp/cart", "Get cart contents");
        register("cart.update", "PUT /ucp/cart/items/{id}", "Update cart item quantity");
        register("cart.remove", "DELETE /ucp/cart/items/{id}", "Remove item from cart");
        register("cart.clear", "DELETE /ucp/cart", "Clear entire cart");

        // Checkout operations
        register("checkout.session.create", "POST /ucp/checkout/session", "Create checkout session");
        register("checkout.session.update", "POST /ucp/checkout/session/{id}", "Update checkout session");
        register("checkout.session.complete", "POST /ucp/checkout/session/{id}/complete", "Complete checkout");
        register("checkout.session.get", "GET /ucp/checkout/session/{id}", "Get checkout session status");

        // Order operations
        register("order.get", "GET /ucp/orders/{id}", "Get order details");
        register("order.list", "GET /ucp/orders", "List customer orders");

        // Payment operations
        register("payment.process", "POST /ucp/payments", "Process payment");
        register("payment.get", "GET /ucp/payments/{id}", "Get payment status");

        // Discovery operations
        register("discovery.profile", "GET /ucp/profile", "Get UCP profile");
        register("discovery.capabilities", "GET /.well-known/agent.json", "Get agent card");
    }

    public static void register(String operation, String restEquivalent, String description) {
        operations.put(operation, new OperationDefinition(operation, restEquivalent, description));
    }

    public static OperationDefinition getOperation(String operation) {
        return operations.get(operation);
    }

    public static Set<String> getSupportedOperations() {
        return operations.keySet();
    }

    public static Map<String, OperationDefinition> getAllOperations() {
        return new HashMap<>(operations);
    }

    @Data
    public static class OperationDefinition {
        private final String operation;
        private final String restEquivalent;
        private final String description;
    }
}
