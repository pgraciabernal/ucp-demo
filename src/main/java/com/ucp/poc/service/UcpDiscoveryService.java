package com.ucp.poc.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * UCP Local Discovery Service using mDNS/DNS-SD
 *
 * Advertises this UCP business on the local network so agents can
 * automatically discover it without needing to know the URL.
 *
 * Uses JmDNS (Java implementation of mDNS/Bonjour) to broadcast
 * service information on the local network.
 *
 * Service Type: _ucp._tcp.local.
 *
 * This allows agents to:
 * 1. Discover all UCP businesses on the local network
 * 2. Get business name, port, and base URL
 * 3. Query /.well-known/ucp for full capabilities
 */
@Service
@Slf4j
public class UcpDiscoveryService {

    @Value("${ucp.business.name:UCP Demo Store}")
    private String businessName;

    @Value("${ucp.business.description:A proof of concept store}")
    private String businessDescription;

    @Value("${server.port:8080}")
    private int port;

    @Value("${ucp.protocol.mode:REST}")
    private String protocolMode;

    @Value("${ucp.discovery.enabled:true}")
    private boolean discoveryEnabled;

    private JmDNS jmdns;
    private ServiceInfo serviceInfo;

    /**
     * Initialize mDNS service and register this UCP business
     * Called automatically after Spring context initialization
     */
    @PostConstruct
    public void registerService() {
        if (!discoveryEnabled) {
            log.info("UCP local discovery is disabled");
            return;
        }

        try {
            log.info("Starting UCP local discovery service...");

            // Get local IP address
            InetAddress localHost = InetAddress.getLocalHost();
            log.info("Local address: {}", localHost.getHostAddress());

            // Create JmDNS instance
            jmdns = JmDNS.create(localHost);

            // Build service name (must be unique on the network)
            String serviceName = sanitizeServiceName(businessName);

            // Service type for UCP businesses
            String serviceType = "_ucp._tcp.local.";

            // TXT record with additional metadata
            Map<String, String> txtRecords = new HashMap<>();
            txtRecords.put("version", "1.0");
            txtRecords.put("protocol", protocolMode.toLowerCase());
            txtRecords.put("description", businessDescription);
            txtRecords.put("path", "/.well-known/ucp");

            // Add protocol-specific info
            if ("A2A".equalsIgnoreCase(protocolMode)) {
                txtRecords.put("a2a_endpoint", "/a2a/rpc");
            } else if ("REST".equalsIgnoreCase(protocolMode)) {
                txtRecords.put("rest_base", "/ucp");
            }

            // Create ServiceInfo
            serviceInfo = ServiceInfo.create(
                    serviceType,
                    serviceName,
                    port,
                    0,  // weight
                    0,  // priority
                    txtRecords
            );

            // Register the service
            jmdns.registerService(serviceInfo);

            log.info("✅ UCP local discovery service registered successfully");
            log.info("   Service Name: {}", serviceName);
            log.info("   Service Type: {}", serviceType);
            log.info("   Port: {}", port);
            log.info("   Protocol: {}", protocolMode);
            log.info("   Discoverable as: {}._ucp._tcp.local.", serviceName);

        } catch (IOException e) {
            log.error("❌ Failed to register UCP discovery service: {}", e.getMessage(), e);
            log.warn("Local network discovery will not be available");
            log.warn("Agents will need to connect directly via URL");
        }
    }

    /**
     * Unregister mDNS service when application shuts down
     * Called automatically before Spring context destruction
     */
    @PreDestroy
    public void unregisterService() {
        if (jmdns != null) {
            try {
                log.info("Unregistering UCP discovery service...");

                if (serviceInfo != null) {
                    jmdns.unregisterService(serviceInfo);
                }

                jmdns.close();

                log.info("✅ UCP discovery service unregistered successfully");
            } catch (Exception e) {
                log.error("Error unregistering UCP discovery service: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Sanitize service name for mDNS compatibility
     * - Remove special characters
     * - Replace spaces with hyphens
     * - Limit length
     */
    private String sanitizeServiceName(String name) {
        return name
                .replaceAll("[^a-zA-Z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .toLowerCase()
                .substring(0, Math.min(name.length(), 63));
    }

    /**
     * Get discovery service status
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", discoveryEnabled);
        status.put("registered", jmdns != null && serviceInfo != null);

        if (serviceInfo != null) {
            status.put("serviceName", serviceInfo.getName());
            status.put("serviceType", serviceInfo.getType());
            status.put("port", port);
            status.put("protocol", protocolMode);
        }

        return status;
    }
}
