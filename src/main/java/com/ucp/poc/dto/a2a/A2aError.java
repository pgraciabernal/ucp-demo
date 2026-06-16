package com.ucp.poc.dto.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * A2A Error Response
 *
 * Structured error information for A2A protocol failures
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2aError {

    /**
     * Error code (e.g., VALIDATION_ERROR, NOT_FOUND, INTERNAL_ERROR)
     */
    private String code;

    /**
     * Human-readable error message
     */
    private String message;

    /**
     * HTTP status code equivalent (for compatibility)
     */
    private Integer statusCode;

    /**
     * Additional error details
     */
    private Map<String, Object> details;

    /**
     * Retry strategy hint
     */
    private RetryHint retryHint;

    public enum RetryHint {
        NO_RETRY,       // Don't retry (permanent failure)
        RETRY_IMMEDIATE, // Retry immediately
        RETRY_WITH_BACKOFF, // Retry with exponential backoff
        RETRY_AFTER_DELAY // Retry after specified delay
    }
}
