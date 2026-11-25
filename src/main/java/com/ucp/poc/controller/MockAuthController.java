package com.ucp.poc.controller;

import com.ucp.poc.service.PoppyTokenStore;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/auth")
public class MockAuthController {

    private final PoppyTokenStore tokenStore;

    public MockAuthController(PoppyTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    // Simulation of the web authorization interface (Login/Consent)
    @GetMapping("/authorize")
    public RedirectView authorize(
            @RequestParam("response_type") String responseType,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "login_hint", required = false) String loginHint) {

        // Construct the redirect URL back to the Personal Agent.
        UriComponentsBuilder target = UriComponentsBuilder.fromUriString(redirectUri);
        if (state != null) {
            target.queryParam("state", state);
        }

        // The email identifies the demo account; unknown accounts are denied
        Set<String> allowed = tokenStore.allowedScopes(loginHint).orElse(null);
        if (allowed == null) {
            return deny(target, "Unknown account: " + loginHint);
        }

        // Grant only the requested scopes that this account is allowed to delegate
        Set<String> requested = scope == null || scope.isBlank()
                ? allowed
                : Arrays.stream(scope.trim().split("\\s+")).collect(Collectors.toSet());
        Set<String> granted = new TreeSet<>(requested);
        granted.retainAll(allowed);
        if (granted.isEmpty()) {
            return deny(target, loginHint + " cannot grant any of the requested scopes");
        }

        String code = tokenStore.issueAuthorizationCode(
                new PoppyTokenStore.Grant(loginHint.trim().toLowerCase(), granted));

        // Automatically redirects, simulating that the user has accepted the permissions with a single click.
        return new RedirectView(target.queryParam("code", code).encode().toUriString());
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

        if (!"authorization_code".equals(grantType)) {
            // If the grant_type is incorrect or not supported
            response.put("error", "unsupported_grant_type");
            return ResponseEntity.badRequest().body(response);
        }

        PoppyTokenStore.Grant grant = tokenStore.redeemAuthorizationCode(code).orElse(null);
        if (grant == null) {
            response.put("error", "invalid_grant");
            response.put("error_description", "Authorization code is invalid or has already been used.");
            return ResponseEntity.badRequest().body(response);
        }

        response.put("access_token", tokenStore.issueAccessToken(grant));
        response.put("token_type", "Bearer");
        response.put("expires_in", 3600); // 1 hour expiration
        response.put("refresh_token", "poppy_mock_refresh_token_" + UUID.randomUUID().toString());
        response.put("scope", String.join(" ", grant.scopes()));
        response.put("email", grant.email());

        return ResponseEntity.ok(response);
    }

    private RedirectView deny(UriComponentsBuilder target, String description) {
        return new RedirectView(target
                .queryParam("error", "access_denied")
                .queryParam("error_description", description)
                .encode()
                .toUriString());
    }
}
