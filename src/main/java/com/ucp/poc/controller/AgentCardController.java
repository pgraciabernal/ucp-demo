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

        // Protocol list
        List<String> protocols = new ArrayList<>();
        if (protocolConfig.isRestEnabled()) {
            protocols.add("rest");
        }
        if (protocolConfig.isA2aEnabled()) {
            protocols.add("a2a");
        }
        agentCard.put("protocol", protocols.size() == 1 ? protocols.get(0) : protocols);

        // Protocol endpoints
        Map<String, Object> endpoints = new HashMap<>();
        if (protocolConfig.isRestEnabled()) {
            endpoints.put("rest", "http://localhost:8080/ucp");
            endpoints.put("ucp_discovery", "http://localhost:8080/.well-known/ucp");
        }
        if (protocolConfig.isA2aEnabled()) {
            Map<String, Object> a2aEndpoints = new HashMap<>();
            if (protocolConfig.getA2a().isHttpEnabled()) {
                a2aEndpoints.put("http", "http://localhost:8080/a2a/message");
            }
            if (protocolConfig.getA2a().isWebsocketEnabled()) {
                a2aEndpoints.put("websocket", "ws://localhost:8080/a2a/ws");
            }
            a2aEndpoints.put("capabilities", "http://localhost:8080/a2a/capabilities");
            endpoints.put("a2a", a2aEndpoints);
        }
        agentCard.put("endpoints", endpoints);

        // Capabilities
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("products", Map.of(
                "description", "Product catalog browsing and search",
                "endpoint", "/ucp/products"
        ));
        capabilities.put("checkout", Map.of(
                "description", "Multi-item cart checkout with payment processing",
                "endpoint", "/ucp/checkout"
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
