package com.ucp.poc.config;

import com.ucp.poc.service.PoppyTokenStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class PoppySecurityInterceptor implements HandlerInterceptor {

    private final PoppyTokenStore tokenStore;

    public PoppySecurityInterceptor(PoppyTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Exclude options (CORS Preflight) for compatibility
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // Reads require poppy:read, any state-changing request requires poppy:write
        String requiredScope = "GET".equalsIgnoreCase(request.getMethod())
                ? PoppyTokenStore.SCOPE_READ
                : PoppyTokenStore.SCOPE_WRITE;

        // Extract Authorization header, should start with "Bearer "
        String authHeader = request.getHeader("Authorization");
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null;

        // Validate token issued by MockAuthController
        PoppyTokenStore.Grant grant = tokenStore.findAccessToken(token).orElse(null);
        if (grant == null) {
            response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
            writeError(response, HttpStatus.UNAUTHORIZED, "invalid_token", "Poppy access token is not valid.");
            return false;
        }

        if (!grant.hasScope(requiredScope)) {
            response.setHeader("WWW-Authenticate",
                    "Bearer error=\"insufficient_scope\", scope=\"" + requiredScope + "\"");
            writeError(response, HttpStatus.FORBIDDEN, "insufficient_scope",
                    grant.email() + " has not granted the '" + requiredScope + "' scope.");
            return false;
        }

        return true; // Token valid, applies request to controller
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String error, String description)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + error + "\", \"error_description\": \"" + description + "\"}");
    }
}
