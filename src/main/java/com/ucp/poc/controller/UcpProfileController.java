package com.ucp.poc.controller;

import com.ucp.poc.config.ProtocolConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UCP Profile Controller - Provides detailed UCP protocol configuration
 */
@RestController
@RequestMapping("/ucp")
@RequiredArgsConstructor
public class UcpProfileController {

    private final ProtocolConfiguration protocolConfig;

    @Value("${ucp.version:2026-04-08}")
    private String ucpVersion;

    @Value("${ucp.business.name:UCP Demo Store}")
    private String businessName;

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getUcpProfile() {
        Map<String, Object> profile = new HashMap<>();

        profile.put("protocolVersion", ucpVersion);
        profile.put("businessName", businessName);
        profile.put("businessId", "demo-store-001");

        // Protocol capabilities
        Map<String, Object> protocols = new HashMap<>();
        if (protocolConfig.isRestEnabled()) {
            protocols.put("rest", Map.of(
                "enabled", true,
                "baseUrl", "/ucp",
                "documentation", "/v3/api-docs"
            ));
        }
        profile.put("protocols", protocols);

        // Supported operations
        profile.put("supportedOperations", List.of(
                "productSearch",
                "productDetails",
                "addToCart",
                "checkout",
                "payment",
                "orderStatus"
        ));

        // Payment methods (Mock: VISA and AMEX succeed, MASTERCARD fails)
        profile.put("paymentMethods", List.of(
                Map.of(
                        "type", "credit_card",
                        "brand", "VISA",
                        "displayName", "Visa",
                        "status", "active",
                        "mockBehavior", "success"
                ),
                Map.of(
                        "type", "credit_card",
                        "brand", "AMEX",
                        "displayName", "American Express",
                        "status", "active",
                        "mockBehavior", "success"
                ),
                Map.of(
                        "type", "credit_card",
                        "brand", "MASTERCARD",
                        "displayName", "Mastercard",
                        "status", "active",
                        "mockBehavior", "fail"
                )
        ));

        // Shipping options
        profile.put("shippingOptions", List.of(
                Map.of(
                        "method", "standard",
                        "name", "Standard Shipping",
                        "estimatedDays", "5-7",
                        "cost", "9.99"
                ),
                Map.of(
                        "method", "express",
                        "name", "Express Shipping",
                        "estimatedDays", "2-3",
                        "cost", "19.99"
                )
        ));

        // Tax calculation
        profile.put("taxCalculation", Map.of(
                "type", "percentage",
                "rate", 0.08,
                "description", "Standard sales tax"
        ));

        // Currency
        profile.put("currency", "USD");

        // Integration points
        profile.put("endpoints", Map.of(
                "products", "/ucp/products",
                "checkout", "/ucp/checkout",
                "orders", "/ucp/orders",
                "payments", "/ucp/payments"
        ));

        return ResponseEntity.ok(profile);
    }
}
