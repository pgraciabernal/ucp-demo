package com.ucp.poc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucp.poc.config.ProtocolCondition;
import com.ucp.poc.dto.UcpCheckoutSessionRequest;
import com.ucp.poc.model.CartItem;
import com.ucp.poc.service.CheckoutSessionService;
import com.ucp.poc.service.ProductService;
import com.ucp.poc.util.UcpResponseWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Cart Controller - UCP Compliant
 *
 * Implements cart management following UCP specification.
 * Carts are temporary shopping containers that can be converted to checkout sessions.
 *
 * Endpoints:
 * - POST /carts - Create cart
 * - PUT /carts/{id} - Update cart
 * - POST /carts/{id}/cancel - Cancel cart
 */
@Slf4j
@RestController
@RequestMapping("/carts")
@RequiredArgsConstructor
@Conditional(ProtocolCondition.RestEnabled.class)
public class CartController {

    private final CheckoutSessionService sessionService;
    private final ProductService productService;
    private final ObjectMapper objectMapper;

    @Value("${ucp.business.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Create cart
     * POST /carts
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createCart(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "UCP-Agent", required = false) String ucpAgent) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lineItems = (List<Map<String, Object>>) request.get("line_items");

            log.info("Creating cart with {} line items (UCP-Agent: {})",
                    lineItems != null ? lineItems.size() : 0, ucpAgent != null ? ucpAgent : "not specified");

            // Validate request
            if (lineItems == null || lineItems.isEmpty()) {
                return ResponseEntity.badRequest().body(UcpResponseWrapper.error(
                        "validation_error",
                        "line_items is required and cannot be empty"
                ));
            }

            // Extract currency from first line item
            String currency = "USD";
            if (!lineItems.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> unitPrice = (Map<String, Object>) lineItems.get(0).get("unit_price");
                if (unitPrice != null && unitPrice.get("currency") != null) {
                    currency = (String) unitPrice.get("currency");
                }
            }

            // Build cart items from line items
            List<Map.Entry<String, Integer>> productQuantities = lineItems.stream()
                    .map(item -> new AbstractMap.SimpleEntry<>(
                            (String) item.get("product_id"),
                            ((Number) item.get("quantity")).intValue()))
                    .collect(Collectors.toList());

            List<CartItem> cartItems = sessionService.buildCartItems(productQuantities);

            // Generate cart ID
            String cartId = "cart_" + UUID.randomUUID().toString().substring(0, 8);

            // Calculate totals
            BigDecimal subtotal = cartItems.stream()
                    .map(CartItem::getTotalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Build response
            Map<String, Object> response = buildCartResponse(cartId, cartItems, currency, subtotal);

            // Wrap in UCP envelope
            Map<String, Object> envelope = UcpResponseWrapper.wrap(
                    response,
                    "dev.ucp.shopping.checkout",
                    "dev.ucp.shopping.cart"
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(envelope);

        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(UcpResponseWrapper.error(
                    "validation_error",
                    e.getMessage()
            ));

        } catch (Exception e) {
            log.error("Error creating cart", e);
            return ResponseEntity.status(500).body(UcpResponseWrapper.error(
                    "cart_creation_error",
                    "Failed to create cart: " + e.getMessage()
            ));
        }
    }

    /**
     * Build cart response in UCP format
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildCartResponse(String cartId, List<CartItem> cartItems,
                                                   String currency, BigDecimal subtotal) {
        Map<String, Object> response = new LinkedHashMap<>();

        // Cart ID
        response.put("id", cartId);

        // Line items
        List<Map<String, Object>> lineItems = new ArrayList<>();
        for (int i = 0; i < cartItems.size(); i++) {
            CartItem item = cartItems.get(i);
            Map<String, Object> lineItem = new LinkedHashMap<>();

            // Line item ID
            lineItem.put("id", "li_" + (i + 1));

            // Item details
            Map<String, Object> itemDetails = new LinkedHashMap<>();
            itemDetails.put("id", item.getProductId());
            itemDetails.put("title", item.getProductName());
            itemDetails.put("price", convertToCents(item.getUnitPrice()));
            if (item.getImageUrl() != null) {
                itemDetails.put("image_url", item.getImageUrl());
            }
            lineItem.put("item", itemDetails);

            // Quantity
            lineItem.put("quantity", item.getQuantity());

            // Totals for this line item
            List<Map<String, Object>> itemTotals = new ArrayList<>();

            Map<String, Object> itemSubtotal = new LinkedHashMap<>();
            itemSubtotal.put("type", "subtotal");
            itemSubtotal.put("amount", convertToCents(item.getTotalPrice()));
            itemTotals.add(itemSubtotal);

            Map<String, Object> itemTotal = new LinkedHashMap<>();
            itemTotal.put("type", "total");
            itemTotal.put("amount", convertToCents(item.getTotalPrice()));
            itemTotals.add(itemTotal);

            lineItem.put("totals", itemTotals);

            lineItems.add(lineItem);
        }
        response.put("line_items", lineItems);

        // Currency
        response.put("currency", currency);

        // Cart totals
        List<Map<String, Object>> totals = new ArrayList<>();

        Map<String, Object> subtotalObj = new LinkedHashMap<>();
        subtotalObj.put("type", "subtotal");
        subtotalObj.put("amount", convertToCents(subtotal));
        totals.add(subtotalObj);

        Map<String, Object> totalObj = new LinkedHashMap<>();
        totalObj.put("type", "total");
        totalObj.put("amount", convertToCents(subtotal));
        totalObj.put("display_text", "Estimated total (taxes calculated at checkout)");
        totals.add(totalObj);

        response.put("totals", totals);

        // Continue URL
        response.put("continue_url", baseUrl + "/checkout?cart=" + cartId);

        // Expiration (1 hour from now)
        ZonedDateTime expiresAt = ZonedDateTime.now().plusHours(1);
        response.put("expires_at", expiresAt.toString());

        return response;
    }

    /**
     * Update cart
     * PUT /carts/{id}
     */
    @PutMapping("/{cartId}")
    public ResponseEntity<Map<String, Object>> updateCart(
            @PathVariable String cartId,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "UCP-Agent", required = false) String ucpAgent) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lineItems = (List<Map<String, Object>>) request.get("line_items");

            log.info("Updating cart {} with {} line items (UCP-Agent: {})",
                    cartId, lineItems != null ? lineItems.size() : 0, ucpAgent != null ? ucpAgent : "not specified");

            // Validate request
            if (lineItems == null) {
                return ResponseEntity.badRequest().body(UcpResponseWrapper.error(
                        "validation_error",
                        "line_items is required"
                ));
            }

            // Extract currency from first line item
            String currency = "USD";
            if (!lineItems.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> unitPrice = (Map<String, Object>) lineItems.get(0).get("unit_price");
                if (unitPrice != null && unitPrice.get("currency") != null) {
                    currency = (String) unitPrice.get("currency");
                }
            }

            // Build cart items from line items
            List<Map.Entry<String, Integer>> productQuantities = lineItems.stream()
                    .map(item -> new AbstractMap.SimpleEntry<>(
                            (String) item.get("product_id"),
                            ((Number) item.get("quantity")).intValue()))
                    .collect(Collectors.toList());

            List<CartItem> cartItems = sessionService.buildCartItems(productQuantities);

            // Calculate totals
            BigDecimal subtotal = cartItems.stream()
                    .map(CartItem::getTotalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Build response
            Map<String, Object> response = buildCartResponse(cartId, cartItems, currency, subtotal);

            // Wrap in UCP envelope
            Map<String, Object> envelope = UcpResponseWrapper.wrap(
                    response,
                    "dev.ucp.shopping.checkout",
                    "dev.ucp.shopping.cart"
            );

            return ResponseEntity.ok(envelope);

        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(UcpResponseWrapper.error(
                    "validation_error",
                    e.getMessage()
            ));

        } catch (Exception e) {
            log.error("Error updating cart", e);
            return ResponseEntity.status(500).body(UcpResponseWrapper.error(
                    "cart_update_error",
                    "Failed to update cart: " + e.getMessage()
            ));
        }
    }

    /**
     * Cancel cart
     * POST /carts/{id}/cancel
     */
    @PostMapping("/{cartId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelCart(
            @PathVariable String cartId,
            @RequestHeader(value = "UCP-Agent", required = false) String ucpAgent) {
        try {
            log.info("Cancelling cart {} (UCP-Agent: {})",
                    cartId, ucpAgent != null ? ucpAgent : "not specified");

            // Build cancellation response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id", cartId);
            response.put("status", "cancelled");
            response.put("message", "Cart has been cancelled");

            // Wrap in UCP envelope
            Map<String, Object> envelope = UcpResponseWrapper.wrap(
                    response,
                    "dev.ucp.shopping.checkout",
                    "dev.ucp.shopping.cart"
            );

            return ResponseEntity.ok(envelope);

        } catch (Exception e) {
            log.error("Error cancelling cart", e);
            return ResponseEntity.status(500).body(UcpResponseWrapper.error(
                    "cart_cancel_error",
                    "Failed to cancel cart: " + e.getMessage()
            ));
        }
    }

    /**
     * Convert BigDecimal to cents (signed integer)
     */
    private Long convertToCents(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.multiply(new BigDecimal("100")).longValue();
    }
}
