package com.ucp.poc.controller;

import com.ucp.poc.service.UcpDiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Discovery Status Controller
 * Provides information about the local network discovery service
 */
@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
public class DiscoveryStatusController {

    private final UcpDiscoveryService discoveryService;

    /**
     * Get local discovery service status
     *
     * GET /api/discovery/status
     *
     * Returns information about whether mDNS service discovery is active
     * and how this business can be discovered on the local network.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getDiscoveryStatus() {
        Map<String, Object> status = discoveryService.getStatus();

        return ResponseEntity.ok(Map.of(
                "discovery", status,
                "instructions", Map.of(
                        "description", "This service advertises itself on the local network using mDNS/DNS-SD",
                        "serviceType", "_ucp._tcp.local.",
                        "clients", Map.of(
                                "macOS", "Use 'dns-sd -B _ucp._tcp' or Bonjour Browser",
                                "linux", "Use 'avahi-browse _ucp._tcp' or 'avahi-discover'",
                                "windows", "Use Bonjour Browser or ZeroConf browser",
                                "nodejs", "Use 'npm install mdns' or 'bonjour' package",
                                "python", "Use 'python-zeroconf' package"
                        )
                )
        ));
    }
}
