package com.ucp.poc.controller;

import com.ucp.poc.config.ProtocolConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller to provide protocol configuration information to the UI
 */
@RestController
@RequestMapping("/api/protocol")
@RequiredArgsConstructor
public class ProtocolInfoController {

    private final ProtocolConfiguration protocolConfig;

    /**
     * Get current protocol configuration
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getProtocolInfo() {
        return ResponseEntity.ok(Map.of(
                "mode", protocolConfig.getMode(),
                "restEnabled", protocolConfig.isRestEnabled(),
                "description", protocolConfig.getActiveProtocolsDescription()
        ));
    }
}
