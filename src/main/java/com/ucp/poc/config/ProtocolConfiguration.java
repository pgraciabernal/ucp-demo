package com.ucp.poc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for UCP protocol
 * The store exposes REST (JSON) endpoints only
 */
@Configuration
@ConfigurationProperties(prefix = "ucp.protocol")
@Data
public class ProtocolConfiguration {

    /**
     * Protocol mode: REST is the only supported value
     */
    private String mode = "REST";

    /**
     * Check if REST is enabled
     */
    public boolean isRestEnabled() {
        return "REST".equalsIgnoreCase(mode);
    }

    /**
     * Get current active protocol as a descriptive string
     */
    public String getActiveProtocolsDescription() {
        return isRestEnabled() ? "REST" : "NONE";
    }
}
