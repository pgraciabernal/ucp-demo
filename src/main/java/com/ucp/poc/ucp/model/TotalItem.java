package com.ucp.poc.ucp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * UCP Total Item
 *
 * Represents a single line in the totals breakdown.
 *
 * @see <a href="https://ucp.dev/latest/specification">UCP Specification</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotalItem {
    /**
     * Type of total
     * Values: "subtotal", "tax", "shipping", "discount", "total"
     */
    private String type;

    /**
     * Amount for this total line (in minor units - cents)
     */
    private Long amount;

    /**
     * Currency code (ISO 4217)
     */
    private String currency;

    /**
     * Optional label for display
     */
    private String label;

    /**
     * Optional description
     */
    private String description;
}
