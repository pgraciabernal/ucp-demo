package com.ucp.poc.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Discovery Helper Controller
 *
 * Provides server-side mDNS discovery for browser clients.
 * Since browsers cannot do mDNS directly due to security restrictions,
 * this endpoint scans the local network and returns discovered UCP businesses.
 */
@RestController
@RequestMapping("/api/discovery")
@Slf4j
public class DiscoveryHelperController {

    private static final String UCP_SERVICE_TYPE = "_ucp._tcp.local.";
    private static final int DEFAULT_TIMEOUT_SECONDS = 5;
    private static final int CACHE_TTL_SECONDS = 30;

    // Simple cache to avoid repeated scans
    private static volatile List<DiscoveredBusiness> cachedBusinesses = null;
    private static volatile LocalDateTime cacheTime = null;

    /**
     * Scan local network for UCP businesses
     *
     * GET /api/discovery/scan?timeout=5&forceRefresh=false
     *
     * Returns list of discovered UCP businesses on the local network.
     * Results are cached for 30 seconds to avoid excessive scanning.
     *
     * @param timeout Scan timeout in seconds (default: 5)
     * @param forceRefresh Force a new scan, ignoring cache
     * @return List of discovered businesses
     */
    @GetMapping("/scan")
    public ResponseEntity<List<DiscoveredBusiness>> scanNetwork(
            @RequestParam(defaultValue = "5") int timeout,
            @RequestParam(defaultValue = "false") boolean forceRefresh) {

        log.info("Discovery scan requested (timeout={}s, forceRefresh={})", timeout, forceRefresh);

        // Check cache
        if (!forceRefresh && isCacheValid()) {
            log.info("Returning cached results ({} businesses)", cachedBusinesses.size());
            return ResponseEntity.ok(cachedBusinesses);
        }

        // Perform scan
        try {
            List<DiscoveredBusiness> businesses = performMdnsScan(timeout);

            // Update cache
            cachedBusinesses = businesses;
            cacheTime = LocalDateTime.now();

            log.info("Discovery scan complete: found {} businesses", businesses.size());
            return ResponseEntity.ok(businesses);

        } catch (IOException e) {
            log.error("Failed to scan network: {}", e.getMessage(), e);
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    /**
     * Clear the discovery cache
     *
     * GET /api/discovery/clear-cache
     */
    @GetMapping("/clear-cache")
    public ResponseEntity<Map<String, String>> clearCache() {
        cachedBusinesses = null;
        cacheTime = null;
        log.info("Discovery cache cleared");
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Discovery cache cleared"
        ));
    }

    /**
     * Perform actual mDNS scan
     */
    private List<DiscoveredBusiness> performMdnsScan(int timeoutSeconds) throws IOException {
        List<DiscoveredBusiness> businesses = Collections.synchronizedList(new ArrayList<>());
        Set<String> discoveredNames = Collections.synchronizedSet(new HashSet<>());
        CompletableFuture<Void> scanComplete = new CompletableFuture<>();

        JmDNS jmdns = null;

        try {
            // Create JmDNS instance
            InetAddress localHost = InetAddress.getLocalHost();
            jmdns = JmDNS.create(localHost);

            log.debug("Starting mDNS scan for {} services", UCP_SERVICE_TYPE);

            // Add service listener
            jmdns.addServiceListener(UCP_SERVICE_TYPE, new ServiceListener() {
                @Override
                public void serviceAdded(ServiceEvent event) {
                    log.debug("UCP service detected: {}", event.getName());
                    // Request service info
                    event.getDNS().requestServiceInfo(event.getType(), event.getName(), 1000);
                }

                @Override
                public void serviceResolved(ServiceEvent event) {
                    log.debug("UCP service resolved: {}", event.getName());

                    ServiceInfo info = event.getInfo();
                    if (info != null) {
                        try {
                            // Check if already discovered (avoid duplicates from multiple interfaces)
                            String serviceName = info.getName();

                            // Use synchronized check-and-add to prevent race conditions
                            synchronized (discoveredNames) {
                                if (discoveredNames.contains(serviceName)) {
                                    log.debug("Service {} already discovered, skipping duplicate", serviceName);
                                    return;
                                }
                                discoveredNames.add(serviceName);
                            }

                            DiscoveredBusiness business = parseServiceInfo(info);
                            businesses.add(business);
                            log.info("Discovered: {} at {}", business.getName(), business.getUrl());
                        } catch (Exception e) {
                            log.warn("Failed to parse service info: {}", e.getMessage());
                        }
                    }
                }

                @Override
                public void serviceRemoved(ServiceEvent event) {
                    log.debug("UCP service removed: {}", event.getName());
                }
            });

            // Wait for scan to complete (with timeout)
            try {
                scanComplete.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Timeout is expected - we just want to wait for the duration
                log.debug("Scan timeout reached ({}s)", timeoutSeconds);
            }

        } finally {
            if (jmdns != null) {
                jmdns.close();
            }
        }

        return businesses;
    }

    /**
     * Parse ServiceInfo into DiscoveredBusiness
     */
    private DiscoveredBusiness parseServiceInfo(ServiceInfo info) {
        DiscoveredBusiness business = new DiscoveredBusiness();

        // Basic info
        business.setName(info.getName());
        business.setServiceType(info.getType());
        business.setPort(info.getPort());

        // Get host address
        String host = null;
        if (info.getHostAddresses() != null && info.getHostAddresses().length > 0) {
            host = info.getHostAddresses()[0];
        } else if (info.getInetAddresses() != null && info.getInetAddresses().length > 0) {
            host = info.getInetAddresses()[0].getHostAddress();
        }

        if (host != null) {
            business.setHost(host);
            business.setUrl(String.format("http://%s:%d", host, info.getPort()));
        }

        // Parse TXT records
        Map<String, String> properties = new HashMap<>();
        Enumeration<String> keys = info.getPropertyNames();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            String value = info.getPropertyString(key);
            properties.put(key, value);
        }

        business.setProtocol(properties.getOrDefault("protocol", "unknown"));
        business.setVersion(properties.getOrDefault("version", "unknown"));
        business.setDescription(properties.getOrDefault("description", ""));
        business.setDiscoveryPath(properties.getOrDefault("path", "/.well-known/ucp"));

        // Set discovery time
        business.setDiscoveredAt(LocalDateTime.now());

        // Mark as available
        business.setAvailable(true);

        return business;
    }

    /**
     * Check if cache is still valid
     */
    private boolean isCacheValid() {
        if (cachedBusinesses == null || cacheTime == null) {
            return false;
        }

        LocalDateTime expiryTime = cacheTime.plusSeconds(CACHE_TTL_SECONDS);
        return LocalDateTime.now().isBefore(expiryTime);
    }

    /**
     * Discovered Business DTO
     */
    public static class DiscoveredBusiness {
        private String name;
        private String serviceType;
        private String host;
        private int port;
        private String url;
        private String protocol;
        private String version;
        private String description;
        private String discoveryPath;
        private boolean available;
        private LocalDateTime discoveredAt;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getServiceType() {
            return serviceType;
        }

        public void setServiceType(String serviceType) {
            this.serviceType = serviceType;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getDiscoveryPath() {
            return discoveryPath;
        }

        public void setDiscoveryPath(String discoveryPath) {
            this.discoveryPath = discoveryPath;
        }

        public boolean isAvailable() {
            return available;
        }

        public void setAvailable(boolean available) {
            this.available = available;
        }

        public LocalDateTime getDiscoveredAt() {
            return discoveredAt;
        }

        public void setDiscoveredAt(LocalDateTime discoveredAt) {
            this.discoveredAt = discoveredAt;
        }
    }
}
