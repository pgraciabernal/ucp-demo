package com.ucp.poc.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UCP Profile Service
 *
 * Manages UCP profile configuration and schema validation.
 * Provides access to capabilities, payment handlers, and service endpoints
 * for /.well-known/ucp discovery endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UcpProfileService {

    // Cached profile
    private UcpProfile cachedProfile;

    /**
     * Get UCP profile (builds from current configuration)
     */
    public UcpProfile getProfile() {
        if (cachedProfile == null) {
            cachedProfile = buildProfile();
        }
        return cachedProfile;
    }

    /**
     * Refresh profile
     */
    public UcpProfile refresh() {
        cachedProfile = buildProfile();
        log.info("UCP profile refreshed");
        return cachedProfile;
    }

    /**
     * Build UCP profile from current configuration
     */
    private UcpProfile buildProfile() {
        UcpProfile profile = new UcpProfile();
        profile.setVersion("2026-04-08");

        // Services
        Map<String, List<ServiceEndpoint>> services = new HashMap<>();
        ServiceEndpoint checkoutEndpoint = new ServiceEndpoint();
        checkoutEndpoint.setTransport("rest");
        checkoutEndpoint.setEndpoint("/checkout-sessions");
        checkoutEndpoint.setProfile("/.well-known/ucp");
        checkoutEndpoint.setMethods(List.of("POST", "GET", "PATCH"));
        services.put("dev.ucp.shopping", List.of(checkoutEndpoint));
        profile.setServices(services);

        // Capabilities
        Map<String, Capability> capabilities = new HashMap<>();

        Capability checkoutCap = new Capability();
        checkoutCap.setVersion("1.0");
        checkoutCap.setSchema(null);  // No external schema for POC
        checkoutCap.setDescription("Checkout sessions with progressive disclosure");
        capabilities.put("dev.ucp.shopping.checkout", checkoutCap);

        Capability fulfillmentCap = new Capability();
        fulfillmentCap.setExtendsCapability("dev.ucp.shopping.checkout");
        fulfillmentCap.setVersion("1.0");
        fulfillmentCap.setSchema(null);  // No external schema for POC
        fulfillmentCap.setDescription("Shipping and fulfillment methods");
        capabilities.put("dev.ucp.shopping.fulfillment", fulfillmentCap);

        // Catalog capability (UCP standard)
        Capability catalogCap = new Capability();
        catalogCap.setVersion("1.0");
        catalogCap.setSchema(null);  // No external schema for POC
        catalogCap.setDescription("Product catalog browsing");
        capabilities.put("dev.ucp.catalog", catalogCap);

        profile.setCapabilities(capabilities);

        // Payment Handlers
        Map<String, PaymentHandler> handlers = new HashMap<>();
        String mockHandlerId = "8c9202bd-63cc-4241-8d24-d57ce69ea31c";

        PaymentHandler mockHandler = new PaymentHandler();
        mockHandler.setName("Mock Payment Gateway");
        mockHandler.setSupportedMethods(List.of("card", "digital_wallet"));
        mockHandler.setUrl(null);  // No external URL for POC
        mockHandler.setPublicKey("pk_mock_test_" + System.currentTimeMillis());

        handlers.put(mockHandlerId, mockHandler);
        profile.setPaymentHandlers(handlers);

        return profile;
    }

    /**
     * Get capability by name
     */
    public Capability getCapability(String name) {
        UcpProfile profile = getProfile();
        return profile.getCapabilities().get(name);
    }

    /**
     * Get payment handler by ID
     */
    public PaymentHandler getPaymentHandler(String handlerId) {
        UcpProfile profile = getProfile();
        return profile.getPaymentHandlers().get(handlerId);
    }

    /**
     * Validate that a handler ID is valid
     */
    public boolean isValidPaymentHandler(String handlerId) {
        if (handlerId == null || handlerId.isEmpty()) {
            return false;
        }
        UcpProfile profile = getProfile();
        return profile.getPaymentHandlers().containsKey(handlerId);
    }

    /**
     * Get checkout endpoint
     */
    public String getCheckoutEndpoint() {
        UcpProfile profile = getProfile();
        List<ServiceEndpoint> shoppingServices = profile.getServices().get("dev.ucp.shopping");
        if (shoppingServices != null && !shoppingServices.isEmpty()) {
            return shoppingServices.get(0).getEndpoint();
        }
        return "/checkout-sessions"; // Default
    }

    /**
     * Get all registered payment handler IDs
     */
    public List<String> getPaymentHandlerIds() {
        return List.copyOf(getProfile().getPaymentHandlers().keySet());
    }

    /**
     * Get all payment handlers as list (for checkout session response)
     */
    public List<Object> getPaymentHandlers() {
        UcpProfile profile = getProfile();
        return new ArrayList<>(profile.getPaymentHandlers().values());
    }

    /**
     * UCP Profile
     */
    @Data
    public static class UcpProfile {
        private String version;
        private Map<String, List<ServiceEndpoint>> services;
        private Map<String, Capability> capabilities;
        private Map<String, PaymentHandler> paymentHandlers;
    }

    /**
     * Service Endpoint
     */
    @Data
    public static class ServiceEndpoint {
        private String transport;
        private String endpoint;
        private String profile;
        private List<String> methods;
    }

    /**
     * Capability
     */
    @Data
    public static class Capability {
        private String version;
        private String schema;
        private String description;
        private String extendsCapability;
    }

    /**
     * Payment Handler
     */
    @Data
    public static class PaymentHandler {
        private String name;
        private List<String> supportedMethods;
        private String url;
        private String publicKey;
    }
}
