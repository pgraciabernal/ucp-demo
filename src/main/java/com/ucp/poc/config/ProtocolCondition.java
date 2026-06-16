package com.ucp.poc.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Conditions for enabling/disabling controllers based on protocol configuration
 * REST-only mode
 */
public class ProtocolCondition {

    /**
     * Condition that checks if REST protocol is enabled
     * Returns true if mode is REST or BOTH
     */
    public static class RestEnabled implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String mode = context.getEnvironment().getProperty("ucp.protocol.mode", "REST");
            return "REST".equalsIgnoreCase(mode) || "BOTH".equalsIgnoreCase(mode);
        }
    }
}
