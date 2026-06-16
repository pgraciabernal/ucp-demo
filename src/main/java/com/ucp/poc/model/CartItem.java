package com.ucp.poc.model;

import com.ucp.poc.util.ISOCurrencyValidator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Cart Item Model
 *
 * Represents an item in a shopping cart with complete product details
 * to avoid additional product lookups during cart operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {
    /**
     * Product identifier
     */
    private String productId;

    /**
     * Product SKU (for external reference)
     */
    private String sku;

    /**
     * Product name
     */
    private String productName;

    /**
     * Product brand (optional)
     */
    private String brand;

    /**
     * Short product description (optional)
     */
    private String description;

    /**
     * Primary product image URL
     */
    private String imageUrl;

    /**
     * Quantity ordered
     */
    private Integer quantity;

    /**
     * Unit price (per item)
     */
    private BigDecimal unitPrice;

    /**
     * ISO 4217 currency code
     */
    private String currency;

    /**
     * Total price (unitPrice * quantity)
     */
    private BigDecimal totalPrice;

    /**
     * Selected product options/variants
     * Example: {"size": "L", "color": "Blue"}
     */
    @Builder.Default
    private Map<String, String> selectedOptions = new HashMap<>();

    /**
     * Product availability indicator
     */
    @Builder.Default
    private Boolean inStock = true;

    /**
     * Available quantity at time of adding to cart
     */
    private Integer availableQuantity;

    /**
     * Calculates total price based on unit price and quantity
     */
    public void calculateTotalPrice() {
        if (unitPrice != null && quantity != null) {
            totalPrice = unitPrice.multiply(new BigDecimal(quantity));
        }
    }

    /**
     * Validates cart item data
     */
    public void validate() {
        if (productId == null || productId.trim().isEmpty()) {
            throw new IllegalArgumentException("Product ID is required");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Unit price must be non-negative");
        }
        if (currency != null) {
            ISOCurrencyValidator.validate(currency);
        }
    }

    /**
     * Normalizes cart item data
     */
    public void normalize() {
        if (sku != null) sku = sku.trim().toUpperCase();
        if (productName != null) productName = productName.trim();
        if (brand != null) brand = brand.trim();
        if (currency != null) currency = ISOCurrencyValidator.normalize(currency);
    }

    /**
     * Returns display name with brand if available
     */
    public String getDisplayName() {
        if (brand != null && !brand.isEmpty()) {
            return brand + " " + productName;
        }
        return productName;
    }

    /**
     * Returns display text for selected options
     * Example: "Size: L, Color: Blue"
     */
    public String getOptionsDisplay() {
        if (selectedOptions == null || selectedOptions.isEmpty()) {
            return null;
        }
        return selectedOptions.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
    }

    /**
     * Checks if item has variants selected
     */
    public boolean hasVariants() {
        return selectedOptions != null && !selectedOptions.isEmpty();
    }

    /**
     * Gets variant value by key
     */
    public String getVariantValue(String key) {
        return selectedOptions != null ? selectedOptions.get(key) : null;
    }

    /**
     * Sets variant value
     */
    public void setVariantValue(String key, String value) {
        if (selectedOptions == null) {
            selectedOptions = new HashMap<>();
        }
        selectedOptions.put(key, value);
    }
}

