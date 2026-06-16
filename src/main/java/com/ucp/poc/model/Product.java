package com.ucp.poc.model;

import com.ucp.poc.util.ISOCurrencyValidator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Product Model - Schema.org Product compliant
 *
 * Represents a product in the catalog with complete metadata for
 * e-commerce operations and AI agent interactions.
 *
 * @see <a href="https://schema.org/Product">Schema.org Product</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    /**
     * Internal product identifier (UUID)
     */
    private String id;

    /**
     * Stock Keeping Unit - External product identifier
     * Used for inventory management and external system integration
     */
    private String sku;

    /**
     * Global Trade Item Number (GTIN)
     * Standard product identifier (EAN, UPC, ISBN, etc.)
     * Optional but recommended for product matching
     */
    private String gtin;

    /**
     * Product name
     */
    private String name;

    /**
     * Product description
     */
    private String description;

    /**
     * Brand or manufacturer name
     * Example: "Nike", "Apple", "Samsung"
     */
    private String brand;

    /**
     * Product price (base price before discounts)
     */
    private BigDecimal price;

    /**
     * ISO 4217 currency code
     */
    private String currency;

    /**
     * Product images (multiple images support)
     * First image is the primary/thumbnail image
     */
    @Builder.Default
    private List<String> images = new ArrayList<>();

    /**
     * Stock quantity available
     */
    private Integer stockQuantity;

    /**
     * Product availability status
     * Values: "InStock", "OutOfStock", "PreOrder", "Discontinued"
     */
    @Builder.Default
    private String availability = "InStock";

    /**
     * Product condition
     */
    @Builder.Default
    private ProductCondition condition = ProductCondition.NEW;

    /**
     * Product category
     */
    private String category;

    /**
     * Product weight in kilograms (optional, for shipping)
     */
    private BigDecimal weight;

    /**
     * Product dimensions: width in centimeters (optional, for shipping)
     */
    private BigDecimal width;

    /**
     * Product dimensions: height in centimeters (optional, for shipping)
     */
    private BigDecimal height;

    /**
     * Product dimensions: depth in centimeters (optional, for shipping)
     */
    private BigDecimal depth;

    /**
     * Legacy support: Returns first image URL
     * @deprecated Use getImages() instead
     */
    @Deprecated
    public String getImageUrl() {
        return images != null && !images.isEmpty() ? images.get(0) : null;
    }

    /**
     * Legacy support: Sets single image URL
     * @deprecated Use setImages() instead
     */
    @Deprecated
    public void setImageUrl(String imageUrl) {
        if (imageUrl != null) {
            this.images = new ArrayList<>();
            this.images.add(imageUrl);
        }
    }

    /**
     * Gets primary/thumbnail image
     */
    public String getPrimaryImage() {
        return images != null && !images.isEmpty() ? images.get(0) : null;
    }

    /**
     * Checks if product is in stock
     */
    public boolean isInStock() {
        return stockQuantity != null && stockQuantity > 0 && "InStock".equals(availability);
    }

    /**
     * Checks if product has sufficient stock
     */
    public boolean hasStock(int quantity) {
        return stockQuantity != null && stockQuantity >= quantity && "InStock".equals(availability);
    }

    /**
     * Validates product data
     */
    public void validate() {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Product price must be non-negative");
        }
        if (currency != null) {
            ISOCurrencyValidator.validate(currency);
        }
        if (stockQuantity != null && stockQuantity < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative");
        }
    }

    /**
     * Normalizes product data
     */
    public void normalize() {
        if (name != null) name = name.trim();
        if (description != null) description = description.trim();
        if (brand != null) brand = brand.trim();
        if (sku != null) sku = sku.trim().toUpperCase();
        if (gtin != null) gtin = gtin.trim();
        if (currency != null) currency = ISOCurrencyValidator.normalize(currency);
        if (category != null) category = category.trim();
    }

    /**
     * Returns product display name with brand
     */
    public String getFullName() {
        if (brand != null && !brand.isEmpty()) {
            return brand + " " + name;
        }
        return name;
    }
}

