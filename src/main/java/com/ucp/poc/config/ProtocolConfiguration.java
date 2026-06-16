package com.ucp.poc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for UCP protocol
 * Supports REST and A2A protocols (coexisting)
 */
@Configuration
@ConfigurationProperties(prefix = "ucp.protocol")
@Data
public class ProtocolConfiguration {

    /**
     * Protocol mode: REST, A2A, or BOTH
     * - REST: Only REST endpoints enabled
     * - A2A: Only A2A messaging enabled
     * - BOTH: Both protocols enabled (agents can choose)
     */
    private String mode = "BOTH";

    /**
     * A2A-specific configuration
     */
    private A2aConfig a2a = new A2aConfig();

    @Data
    public static class A2aConfig {
        /**
         * Enable A2A protocol
         */
        private boolean enabled = true;

        /**
         * Enable WebSocket transport for A2A
         */
        private boolean websocketEnabled = true;

        /**
         * Enable HTTP POST transport for A2A
         */
        private boolean httpEnabled = true;

        /**
         * Maximum message size in bytes
         */
        private long maxMessageSize = 1024 * 1024; // 1MB

        /**
         * Message TTL in seconds
         */
        private int messageTtl = 300; // 5 minutes
    }

    /**
     * Check if REST is enabled
     */
    public boolean isRestEnabled() {
        return "REST".equalsIgnoreCase(mode) || "BOTH".equalsIgnoreCase(mode);
    }

    /**
     * Check if A2A is enabled
     */
    public boolean isA2aEnabled() {
        return ("A2A".equalsIgnoreCase(mode) || "BOTH".equalsIgnoreCase(mode)) && a2a.isEnabled();
    }

    /**
     * Get current active protocol as a descriptive string
     */
    public String getActiveProtocolsDescription() {
        List<String> protocols = new ArrayList<>();
        if (isRestEnabled()) {
            protocols.add("REST");
        }
        if (isA2aEnabled()) {
            protocols.add("A2A");
        }
        return protocols.isEmpty() ? "NONE" : String.join(" + ", protocols);
    }

    /**
     * RestTemplate bean for internal routing
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
