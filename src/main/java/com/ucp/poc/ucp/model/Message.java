package com.ucp.poc.ucp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UCP Message
 *
 * Represents a message (error, warning, info) in the checkout session.
 *
 * @see <a href="https://ucp.dev/latest/specification">UCP Specification</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    /**
     * Message type
     * Values: "error", "warning", "info"
     */
    private String type;

    /**
     * Error/message code
     */
    private String code;

    /**
     * Human-readable message content
     */
    private String content;

    /**
     * Severity level
     * Values: "error", "warning", "info"
     */
    private String severity;

    /**
     * JSON path to the field that caused the message
     * Example: "/line_items/0/item"
     */
    private String path;

    /**
     * Helper method to create error message
     */
    public static Message error(String code, String content, String path) {
        return Message.builder()
                .type("error")
                .code(code)
                .content(content)
                .severity("error")
                .path(path)
                .build();
    }

    /**
     * Helper method to create warning message
     */
    public static Message warning(String code, String content) {
        return Message.builder()
                .type("warning")
                .code(code)
                .content(content)
                .severity("warning")
                .build();
    }

    /**
     * Helper method to create info message
     */
    public static Message info(String code, String content) {
        return Message.builder()
                .type("info")
                .code(code)
                .content(content)
                .severity("info")
                .build();
    }
}
