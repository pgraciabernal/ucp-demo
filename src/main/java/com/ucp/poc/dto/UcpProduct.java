package com.ucp.poc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ucp.poc.model.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * UCP-Compliant Product DTO
 *
 * Follows UCP product schema with snake_case naming and proper structure.
 *
 * @see <a href="https://ucp.dev/schemas/product.json">UCP Product Schema</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpProduct {
    /**
     * Product ID
     */
    private String id;

    /**
     * URL-friendly product handle/slug
     */
    private String handle;

    /**
     * Product title (display name)
     */
    private String title;

    /**
     * Product name (alias for title, for backwards compatibility)
     */
    private String name;

    /**
     * Product description (structured)
     */
    private DescriptionInfo description;

    /**
     * Product URL
     */
    private String url;

    /**
     * Brand name
     */
    private String brand;

    /**
     * SKU (Stock Keeping Unit)
     */
    private String sku;

    /**
     * GTIN (Global Trade Item Number)
     */
    private String gtin;

    /**
     * Price information (single price)
     */
    private PriceInfo price;

    /**
     * Price range (min/max)
     */
    @JsonProperty("price_range")
    private PriceRangeInfo priceRange;

    /**
     * Product images (deprecated, use media)
     */
    @Builder.Default
    private List<ImageInfo> images = new ArrayList<>();

    /**
     * Product media (images, videos, etc.)
     */
    @Builder.Default
    private List<MediaInfo> media = new ArrayList<>();

    /**
     * Product categories
     */
    @Builder.Default
    private List<CategoryInfo> categories = new ArrayList<>();

    /**
     * Inventory information
     */
    private InventoryInfo inventory;

    /**
     * Product category
     */
    private String category;

    /**
     * Product condition
     */
    private String condition;

    /**
     * Physical dimensions (optional, for shipping)
     */
    private DimensionsInfo dimensions;

    /**
     * Description Information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DescriptionInfo {
        /**
         * Plain text description
         */
        private String plain;

        /**
         * HTML description (optional)
         */
        private String html;
    }

    /**
     * Price Information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceInfo {
        /**
         * Price amount in minor units (cents)
         */
        private Long amount;

        /**
         * ISO 4217 currency code
         */
        private String currency;
    }

    /**
     * Price Range Information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceRangeInfo {
        /**
         * Minimum price
         */
        private PriceInfo min;

        /**
         * Maximum price
         */
        private PriceInfo max;
    }

    /**
     * Media Information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaInfo {
        /**
         * Media type (image, video)
         */
        private String type;

        /**
         * Media URL
         */
        private String url;

        /**
         * Alt text for accessibility
         */
        @JsonProperty("alt_text")
        private String altText;
    }

    /**
     * Category Information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryInfo {
        /**
         * Category value/ID
         */
        private String value;

        /**
         * Taxonomy (google_product_category, shopify, merchant)
         */
        private String taxonomy;
    }

    /**
     * Image Information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageInfo {
        /**
         * Image URL
         */
        private String url;

        /**
         * Image type (primary, alternate, thumbnail)
         */
        private String type;

        /**
         * Alt text for accessibility
         */
        private String alt;
    }

    /**
     * Inventory Information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryInfo {
        /**
         * Available quantity
         */
        private Integer available;

        /**
         * Availability status
         */
        private String status;
    }

    /**
     * Dimensions Information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionsInfo {
        /**
         * Weight in kilograms
         */
        private String weight;

        /**
         * Width in centimeters
         */
        private String width;

        /**
         * Height in centimeters
         */
        private String height;

        /**
         * Depth in centimeters
         */
        private String depth;

        /**
         * Unit of measurement
         */
        private String unit;
    }

    /**
     * Converts from internal Product model to UCP format
     */
    public static UcpProduct fromProduct(Product product) {
        String productName = product.getName();
        String productId = product.getId();

        UcpProductBuilder builder = UcpProduct.builder()
                .id(productId)
                .handle(generateHandle(productName))
                .title(productName)
                .name(productName)
                .brand(product.getBrand())
                .sku(product.getSku())
                .gtin(product.getGtin())
                .category(product.getCategory())
                .condition(product.getCondition() != null ? product.getCondition().name().toLowerCase() : "new")
                .url("https://business.example.com/products/" + productId);

        // Description
        if (product.getDescription() != null) {
            builder.description(DescriptionInfo.builder()
                    .plain(product.getDescription())
                    .build());
        }

        // Price (convert to minor units - cents)
        if (product.getPrice() != null) {
            String currency = product.getCurrency() != null ? product.getCurrency() : "USD";
            long amountInCents = product.getPrice().multiply(new BigDecimal("100")).longValue();

            builder.price(PriceInfo.builder()
                    .amount(amountInCents)
                    .currency(currency)
                    .build());

            // Price range (same min/max for simple products)
            builder.priceRange(PriceRangeInfo.builder()
                    .min(PriceInfo.builder().amount(amountInCents).currency(currency).build())
                    .max(PriceInfo.builder().amount(amountInCents).currency(currency).build())
                    .build());
        }

        // Media (convert from images)
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            List<MediaInfo> mediaList = new ArrayList<>();
            List<ImageInfo> imageList = new ArrayList<>();

            for (int i = 0; i < product.getImages().size(); i++) {
                String imageUrl = product.getImages().get(i);

                // Media (new format)
                mediaList.add(MediaInfo.builder()
                        .type("image")
                        .url(imageUrl)
                        .altText(productName)
                        .build());

                // Images (legacy format)
                imageList.add(ImageInfo.builder()
                        .url(imageUrl)
                        .type(i == 0 ? "primary" : "alternate")
                        .alt(productName)
                        .build());
            }

            builder.media(mediaList);
            builder.images(imageList);
        }

        // Categories
        if (product.getCategory() != null) {
            List<CategoryInfo> categoryList = new ArrayList<>();
            categoryList.add(CategoryInfo.builder()
                    .value(product.getCategory())
                    .taxonomy("merchant")
                    .build());
            builder.categories(categoryList);
        }

        // Inventory
        builder.inventory(InventoryInfo.builder()
                .available(product.getStockQuantity())
                .status(product.getAvailability() != null ? product.getAvailability().toLowerCase() : "in_stock")
                .build());

        // Dimensions (if available)
        if (product.getWeight() != null || product.getWidth() != null ||
                product.getHeight() != null || product.getDepth() != null) {
            builder.dimensions(DimensionsInfo.builder()
                    .weight(product.getWeight() != null ? product.getWeight().toPlainString() : null)
                    .width(product.getWidth() != null ? product.getWidth().toPlainString() : null)
                    .height(product.getHeight() != null ? product.getHeight().toPlainString() : null)
                    .depth(product.getDepth() != null ? product.getDepth().toPlainString() : null)
                    .unit("metric")
                    .build());
        }

        return builder.build();
    }

    /**
     * Generate URL-friendly handle from product name
     */
    private static String generateHandle(String name) {
        if (name == null) return null;
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    /**
     * Converts list of Products to UCP format
     */
    public static List<UcpProduct> fromProducts(List<Product> products) {
        return products.stream()
                .map(UcpProduct::fromProduct)
                .collect(Collectors.toList());
    }
}
