package com.ucp.poc.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * UCP Response Wrapper Utility
 *
 * Wraps responses with UCP envelope containing version and capabilities.
 * All UCP-compliant responses must include this envelope.
 *
 * @see <a href="https://ucp.dev/latest/specification/">UCP Specification</a>
 */
@Component
public class UcpResponseWrapper {

    private static String ucpVersion;

    @Value("${ucp.version}")
    public void setUcpVersion(String version) {
        ucpVersion = version;
    }

    /**
     * Wraps a response with UCP envelope
     *
     * @param data The response data
     * @param capabilities List of UCP capabilities used
     * @return Map containing ucp envelope and data
     */
    public static Map<String, Object> wrap(Object data, String... capabilities) {
        Map<String, Object> response = new LinkedHashMap<>();

        // Add UCP envelope
        Map<String, Object> ucp = new LinkedHashMap<>();
        ucp.put("version", ucpVersion);

        // Capabilities as object: {"capability.name": [{"version": "2026-01-23"}]}
        if (capabilities != null && capabilities.length > 0) {
            Map<String, Object> capsObject = new LinkedHashMap<>();
            for (String capability : capabilities) {
                List<Map<String, String>> versions = new ArrayList<>();
                Map<String, String> versionObj = new LinkedHashMap<>();
                versionObj.put("version", ucpVersion);
                versions.add(versionObj);
                capsObject.put(capability, versions);
            }
            ucp.put("capabilities", capsObject);
        }

        response.put("ucp", ucp);

        // Add data
        if (data instanceof Map) {
            // Merge map data directly
            response.putAll((Map<String, Object>) data);
        } else if (data != null) {
            // Wrap single object as data field
            response.put("data", data);
        }

        return response;
    }

    /**
     * Wraps a list response with UCP envelope
     *
     * @param items List of items
     * @param itemsKey Key name for the list (e.g., "products", "orders")
     * @param capabilities List of UCP capabilities
     * @return Map containing ucp envelope and items
     */
    public static Map<String, Object> wrapList(List<?> items, String itemsKey, String... capabilities) {
        Map<String, Object> response = new LinkedHashMap<>();

        // Add UCP envelope
        Map<String, Object> ucp = new LinkedHashMap<>();
        ucp.put("version", ucpVersion);

        // Capabilities as object: {"capability.name": [{"version": "2026-01-23"}]}
        if (capabilities != null && capabilities.length > 0) {
            Map<String, Object> capsObject = new LinkedHashMap<>();
            for (String capability : capabilities) {
                List<Map<String, String>> versions = new ArrayList<>();
                Map<String, String> versionObj = new LinkedHashMap<>();
                versionObj.put("version", ucpVersion);
                versions.add(versionObj);
                capsObject.put(capability, versions);
            }
            ucp.put("capabilities", capsObject);
        }

        response.put("ucp", ucp);

        // Add items
        response.put(itemsKey, items);

        return response;
    }

    /**
     * Creates UCP error response
     *
     * @param errorType Error type (e.g., "validation_error", "not_found")
     * @param message Error message
     * @param details Optional error details
     * @return UCP error response
     */
    public static Map<String, Object> error(String errorType, String message, Map<String, Object> details) {
        Map<String, Object> response = new LinkedHashMap<>();

        // Add UCP envelope
        Map<String, Object> ucp = new LinkedHashMap<>();
        ucp.put("version", ucpVersion);
        response.put("ucp", ucp);

        // Add error
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", errorType);
        error.put("message", message);

        if (details != null && !details.isEmpty()) {
            error.put("details", details);
        }

        response.put("error", error);

        return response;
    }

    /**
     * Creates UCP error response (simple version)
     *
     * @param errorType Error type
     * @param message Error message
     * @return UCP error response
     */
    public static Map<String, Object> error(String errorType, String message) {
        return error(errorType, message, null);
    }
}
