package com.ucp.poc.ucp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * UCP Line Item
 *
 * Represents a product item in the checkout session.
 *
 * @see <a href="https://ucp.dev/latest/specification">UCP Specification</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LineItem {
    /**
     * Item reference
     */
    private ItemReference item;

    /**
     * Quantity
     */
    private Integer quantity;

    /**
     * Unit price (in minor units - cents)
     */
    private Long unit_price;

    /**
     * Total price (unit_price * quantity) (in minor units - cents)
     */
    private Long total_price;

    /**
     * Item name (for display)
     */
    private String name;

    /**
     * Item description
     */
    private String description;

    /**
     * Image URL
     */
    private String image_url;

    /**
     * Item Reference
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemReference {
        /**
         * Item ID
         */
        private String id;

        /**
         * Item SKU (optional)
         */
        private String sku;
    }
}
