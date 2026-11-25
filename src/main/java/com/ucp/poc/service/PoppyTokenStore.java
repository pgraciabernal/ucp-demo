package com.ucp.poc.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for the Poppy mock OAuth server.
 *
 * Holds the demo accounts (email -> scopes they may delegate) and the
 * authorization codes and access tokens issued to personal agents.
 */
@Component
public class PoppyTokenStore {

    public static final String SCOPE_READ = "poppy:read";
    public static final String SCOPE_WRITE = "poppy:write";

    // Demo accounts: a read-only user and a user allowed to buy
    private static final Map<String, Set<String>> DEMO_USERS = Map.of(
            "reader@example.com", Set.of(SCOPE_READ),
            "buyer@example.com", Set.of(SCOPE_READ, SCOPE_WRITE)
    );

    public record Grant(String email, Set<String> scopes) {
        public boolean hasScope(String scope) {
            return scopes.contains(scope);
        }
    }

    private final Map<String, Grant> authorizationCodes = new ConcurrentHashMap<>();
    private final Map<String, Grant> accessTokens = new ConcurrentHashMap<>();

    public Optional<Set<String>> allowedScopes(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(DEMO_USERS.get(email.trim().toLowerCase()));
    }

    public String issueAuthorizationCode(Grant grant) {
        String code = "poppy_mock_auth_code_" + UUID.randomUUID().toString().substring(0, 8);
        authorizationCodes.put(code, grant);
        return code;
    }

    // Authorization codes are single use
    public Optional<Grant> redeemAuthorizationCode(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(authorizationCodes.remove(code));
    }

    public String issueAccessToken(Grant grant) {
        String token = "poppy_mock_access_token_" + UUID.randomUUID();
        accessTokens.put(token, grant);
        return token;
    }

    public Optional<Grant> findAccessToken(String token) {
        return token == null ? Optional.empty() : Optional.ofNullable(accessTokens.get(token));
    }
}
