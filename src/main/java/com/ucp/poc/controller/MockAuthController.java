package com.ucp.poc.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/auth")
public class MockAuthController {

    // Simulation of the web authorization interface (Login/Consent)
    @GetMapping("/authorize")
    public RedirectView authorize(
            @RequestParam("response_type") String responseType,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "scope", required = false) String scope) {

        // Generate a dummy authorization code
        String mockAuthCode = "spl_mock_auth_code_" + UUID.randomUUID().toString().substring(0, 8);

        // Construct the redirect URL back to the Personal Agent.
        StringBuilder targetUrl = new StringBuilder(redirectUri);
        targetUrl.append("?code=").append(mockAuthCode);
        if (state != null) {
            targetUrl.append("&state=").append(state);
        }

        // Automatically redirects, simulating that the user has accepted the permissions with a single click.
        return new RedirectView(targetUrl.toString());
    }

    // Token Exchange Endpoint (REST API)
    @PostMapping(value = "/token", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "client_id", required = false) String clientId) {

        // Standard OAuth 2.0 / OIDC response structure
        Map<String, Object> response = new HashMap<>();

        if ("authorization_code".equals(grantType)) {
            response.put("access_token", "poppy_mock_access_token_" + UUID.randomUUID().toString());
            response.put("token_type", "Bearer");
            response.put("expires_in", 3600); // 1 hour expiration
            response.put("refresh_token", "poppy_mock_refresh_token_" + UUID.randomUUID().toString());
            response.put("scope", "poppy:read poppy:write");

            return ResponseEntity.ok(response);
        }

        // If the grant_type is incorrect or not supported
        response.put("error", "unsupported_grant_type");
        return ResponseEntity.badRequest().body(response);
    }
}
