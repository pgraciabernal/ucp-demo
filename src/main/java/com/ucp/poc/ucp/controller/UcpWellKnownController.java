package com.ucp.poc.ucp.controller;

import com.ucp.poc.service.UcpProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * UCP Well-Known Controller
 *
 * Provides UCP standard discovery endpoint following the specification.
 * Endpoint: /.well-known/ucp
 * Spec: https://ucp.dev/latest/specification
 *
 * This is the UCP-spec-compliant endpoint with proper ucp envelope structure.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class UcpWellKnownController {

    private final UcpProfileService profileService;

    /**
     * UCP Profile Discovery
     *
     * GET /.well-known/ucp
     *
     * Returns the UCP profile with version, services, capabilities, and payment handlers
     */
    @GetMapping("/.well-known/ucp")
    public ResponseEntity<Map<String, Object>> getUcpProfile() {
        log.info("GET /.well-known/ucp - UCP Profile discovery");

        UcpProfileService.UcpProfile profile = profileService.getProfile();

        Map<String, Object> response = new HashMap<>();
        Map<String, Object> ucp = new HashMap<>();

        ucp.put("version", profile.getVersion());
        ucp.put("services", convertServices(profile.getServices()));
        ucp.put("capabilities", convertCapabilities(profile.getCapabilities()));
        ucp.put("payment_handlers", convertPaymentHandlers(profile.getPaymentHandlers()));

        response.put("ucp", ucp);

        log.debug("Returning UCP profile with {} services", profile.getServices().size());
        return ResponseEntity.ok(response);
    }

    /**
     * Convert services to response format
     */
    private Map<String, List<Map<String, Object>>> convertServices(
            Map<String, List<UcpProfileService.ServiceEndpoint>> services) {
        Map<String, List<Map<String, Object>>> result = new HashMap<>();

        services.forEach((key, endpoints) -> {
            List<Map<String, Object>> convertedEndpoints = endpoints.stream()
                    .map(endpoint -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("transport", endpoint.getTransport());
                        map.put("endpoint", endpoint.getEndpoint());
                        map.put("profile", endpoint.getProfile());
                        if (endpoint.getMethods() != null && !endpoint.getMethods().isEmpty()) {
                            map.put("methods", String.join(", ", endpoint.getMethods()));
                        }
                        return map;
                    })
                    .collect(Collectors.toList());
            result.put(key, convertedEndpoints);
        });

        return result;
    }

    /**
     * Convert capabilities to response format
     */
    private Map<String, Map<String, Object>> convertCapabilities(
            Map<String, UcpProfileService.Capability> capabilities) {
        Map<String, Map<String, Object>> result = new HashMap<>();

        capabilities.forEach((key, capability) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("version", capability.getVersion());
            if (capability.getSchema() != null) {
                map.put("schema", capability.getSchema());
            }
            if (capability.getExtendsCapability() != null) {
                map.put("extends", capability.getExtendsCapability());
            }
            if (capability.getDescription() != null) {
                map.put("description", capability.getDescription());
            }
            result.put(key, map);
        });

        return result;
    }

    /**
     * Convert payment handlers to response format
     */
    private Map<String, Map<String, Object>> convertPaymentHandlers(
            Map<String, UcpProfileService.PaymentHandler> handlers) {
        Map<String, Map<String, Object>> result = new HashMap<>();

        handlers.forEach((key, handler) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", handler.getName());
            map.put("supported_methods", handler.getSupportedMethods());
            map.put("url", handler.getUrl());
            map.put("public_key", handler.getPublicKey());
            result.put(key, map);
        });

        return result;
    }
}
