package com.ucp.poc.controller;

import com.ucp.poc.config.ProtocolConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Agent Card Controller - Provides discovery information for AI agents
 * Following UCP protocol for agent discovery
 */
@RestController
@RequestMapping("/.well-known")
@RequiredArgsConstructor
public class AgentCardController {

    private final ProtocolConfiguration protocolConfig;

    @Value("${ucp.version:2026-04-08}")
    private String ucpVersion;

    @Value("${ucp.business.name:UCP Demo Store}")
    private String businessName;

    @GetMapping("/agent.json")
    public ResponseEntity<Map<String, Object>> getAgentCard() {
        Map<String, Object> agentCard = new HashMap<>();

        agentCard.put("name", businessName + " Agent");
        agentCard.put("description", "A business agent supporting UCP protocols for commerce operations");
        agentCard.put("version", ucpVersion);

        agentCard.put("protocol", "rest");

        // Protocol endpoints
        Map<String, Object> endpoints = new HashMap<>();
        if (protocolConfig.isRestEnabled()) {
            endpoints.put("rest", "http://localhost:8080/ucp");
            endpoints.put("ucp_discovery", "http://localhost:8080/.well-known/ucp");
        }
        agentCard.put("endpoints", endpoints);

        // Capabilities
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("products", Map.of(
                "description", "Product catalog browsing and search",
                "endpoint", "/ucp/catalog/search"
        ));
        capabilities.put("carts", Map.of(
                "description", "Multi-item shopping carts",
                "endpoint", "/carts"
        ));
        capabilities.put("checkout", Map.of(
                "description", "Multi-item cart checkout with payment processing",
                "endpoint", "/checkout-sessions"
        ));
        capabilities.put("orders", Map.of(
                "description", "Order management and tracking",
                "endpoint", "/ucp/orders"
        ));
        capabilities.put("payments", Map.of(
                "description", "Payment processing and authorization",
                "endpoint", "/ucp/payments"
        ));

        agentCard.put("capabilities", capabilities);

        // Extensions
        agentCard.put("extensions", Map.of(
                "cart", "multi-item",
                "payment", java.util.List.of("VISA", "AMEX", "MASTERCARD"),
                "shipping", java.util.List.of("standard", "express"),
                "currency", "USD"
        ));

        // Contact information
        agentCard.put("contact", Map.of(
                "email", "support@ucpdemo.com",
                "url", "http://localhost:8080"
        ));

        return ResponseEntity.ok(agentCard);
    }
}
