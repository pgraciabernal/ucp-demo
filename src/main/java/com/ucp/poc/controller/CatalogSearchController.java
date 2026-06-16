package com.ucp.poc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucp.poc.config.ProtocolCondition;
import com.ucp.poc.dto.UcpProduct;
import com.ucp.poc.model.Product;
import com.ucp.poc.service.ProductService;
import com.ucp.poc.util.UcpResponseWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Catalog Search Controller - UCP Compliant
 *
 * Implements catalog search endpoint following UCP specification.
 * Supports text search, category filtering, price ranges, and pagination.
 *
 * Endpoint: GET /ucp/catalog/search
 */
@Slf4j
@RestController
@RequestMapping("/ucp/catalog")
@RequiredArgsConstructor
@Conditional(ProtocolCondition.RestEnabled.class)
public class CatalogSearchController {

    private final ProductService productService;
    private final ObjectMapper objectMapper;

    @Value("${ucp.version}")
    private String ucpVersion;
    private static final String CATALOG_SEARCH_CAPABILITY = "dev.ucp.shopping.catalog.search";
    private static final String CATALOG_LOOKUP_CAPABILITY = "dev.ucp.shopping.catalog.lookup";

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    /**
     * Search catalog
     * GET /ucp/catalog/search
     *
     * @param query Search query (matches name, description, brand)
     * @param category Filter by category
     * @param minPrice Minimum price in cents
     * @param maxPrice Maximum price in cents
     * @param limit Results per page (default: 20, max: 100)
     * @param offset Pagination offset (default: 0)
     * @param sortBy Sort field: price, title, relevance (default: relevance)
     * @param sortOrder Sort order: asc, desc (default: asc)
     * @param ucpAgent UCP-Agent header
     * @return Search results with pagination and filters
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(name = "min_price", required = false) Long minPrice,
            @RequestParam(name = "max_price", required = false) Long maxPrice,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(name = "sort_by", defaultValue = "relevance") String sortBy,
            @RequestParam(name = "sort_order", defaultValue = "asc") String sortOrder,
            @RequestHeader(value = "UCP-Agent", required = false) String ucpAgent) {

        long startTime = System.currentTimeMillis();

        try {
            log.info("Catalog search: q='{}', category='{}', price=[{}-{}], limit={}, offset={} (UCP-Agent: {})",
                    q, category, minPrice, maxPrice, limit, offset, ucpAgent != null ? ucpAgent : "not specified");

            // Validate parameters
            if (limit < 1 || limit > MAX_LIMIT) {
                return ResponseEntity.badRequest().body(UcpResponseWrapper.error(
                        "validation_error",
                        "Limit must be between 1 and " + MAX_LIMIT
                ));
            }

            if (offset < 0) {
                return ResponseEntity.badRequest().body(UcpResponseWrapper.error(
                        "validation_error",
                        "Offset must be non-negative"
                ));
            }

            if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
                return ResponseEntity.badRequest().body(UcpResponseWrapper.error(
                        "validation_error",
                        "min_price must be less than or equal to max_price"
                ));
            }

            // Get all products
            List<Product> allProducts = productService.getAllProducts();

            // Apply filters
            List<Product> filteredProducts = applyFilters(allProducts, q, category, minPrice, maxPrice);

            // Apply sorting
            filteredProducts = applySorting(filteredProducts, sortBy, sortOrder);

            // Calculate pagination
            int totalResults = filteredProducts.size();
            int fromIndex = Math.min(offset, totalResults);
            int toIndex = Math.min(offset + limit, totalResults);
            boolean hasMore = toIndex < totalResults;

            // Get page of results
            List<Product> pageResults = filteredProducts.subList(fromIndex, toIndex);

            // Convert to UCP format
            List<UcpProduct> ucpProducts = UcpProduct.fromProducts(pageResults);

            // Convert products to maps and add options/variants
            List<Map<String, Object>> productsWithOptionsVariants = new ArrayList<>();
            for (UcpProduct ucpProduct : ucpProducts) {
                Map<String, Object> productMap = convertToMap(ucpProduct);

                // Add options (empty for simple products)
                productMap.putIfAbsent("options", new ArrayList<>());

                // Add variants (empty for simple products)
                productMap.putIfAbsent("variants", new ArrayList<>());

                productsWithOptionsVariants.add(productMap);
            }

            // Build final response with UCP envelope
            Map<String, Object> finalResponse = new LinkedHashMap<>();

            // UCP envelope
            Map<String, Object> ucp = new LinkedHashMap<>();
            ucp.put("version", ucpVersion);

            Map<String, Object> capabilities = new LinkedHashMap<>();
            capabilities.put(CATALOG_SEARCH_CAPABILITY, List.of(Map.of("version", ucpVersion)));
            ucp.put("capabilities", capabilities);

            finalResponse.put("ucp", ucp);

            // Products (not "results")
            finalResponse.put("products", productsWithOptionsVariants);

            // Pagination
            Map<String, Object> pagination = new LinkedHashMap<>();
            pagination.put("total_count", totalResults);
            pagination.put("has_next_page", hasMore);
            if (hasMore) {
                // Add cursor for next page (base64 encoded offset)
                String cursor = Base64.getEncoder().encodeToString(
                    String.format("{\"offset\":%d}", offset + limit).getBytes()
                );
                pagination.put("cursor", cursor);
            }
            finalResponse.put("pagination", pagination);

            log.info("Returning search response with {} products, ucp={}, pagination={}",
                    productsWithOptionsVariants.size(),
                    finalResponse.containsKey("ucp"),
                    finalResponse.containsKey("pagination"));
            log.info("Response keys: {}", finalResponse.keySet());
            log.info("UCP envelope: {}", finalResponse.get("ucp"));

            return ResponseEntity.ok(finalResponse);

        } catch (Exception e) {
            log.error("Error during catalog search", e);

            // Return simple error response without UCP envelope
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Failed to execute search: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Apply search and filter criteria
     */
    private List<Product> applyFilters(List<Product> products, String query,
                                       String category, Long minPrice, Long maxPrice) {
        return products.stream()
                .filter(p -> matchesQuery(p, query))
                .filter(p -> matchesCategory(p, category))
                .filter(p -> matchesPriceRange(p, minPrice, maxPrice))
                .collect(Collectors.toList());
    }

    /**
     * Check if product matches search query
     * Handles plurals and partial word matches
     */
    private boolean matchesQuery(Product product, String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }

        String lowerQuery = query.toLowerCase().trim();

        // Remove trailing 's' to handle plurals (shirts -> shirt)
        String singularQuery = lowerQuery.endsWith("s") && lowerQuery.length() > 2
                ? lowerQuery.substring(0, lowerQuery.length() - 1)
                : lowerQuery;

        String name = product.getName() != null ? product.getName().toLowerCase() : "";
        String description = product.getDescription() != null ? product.getDescription().toLowerCase() : "";
        String brand = product.getBrand() != null ? product.getBrand().toLowerCase() : "";
        String category = product.getCategory() != null ? product.getCategory().toLowerCase() : "";

        // Check if query or singular form matches
        return name.contains(lowerQuery) || name.contains(singularQuery)
                || description.contains(lowerQuery) || description.contains(singularQuery)
                || brand.contains(lowerQuery) || brand.contains(singularQuery)
                || category.contains(lowerQuery) || category.contains(singularQuery);
    }

    /**
     * Check if product matches category filter
     */
    private boolean matchesCategory(Product product, String category) {
        if (category == null || category.trim().isEmpty()) {
            return true;
        }
        return product.getCategory() != null &&
                product.getCategory().equalsIgnoreCase(category);
    }

    /**
     * Check if product matches price range
     */
    private boolean matchesPriceRange(Product product, Long minPrice, Long maxPrice) {
        if (product.getPrice() == null) {
            return false;
        }

        // Convert price to cents
        long priceInCents = product.getPrice().multiply(new BigDecimal("100")).longValue();

        if (minPrice != null && priceInCents < minPrice) {
            return false;
        }

        if (maxPrice != null && priceInCents > maxPrice) {
            return false;
        }

        return true;
    }

    /**
     * Apply sorting to results
     */
    private List<Product> applySorting(List<Product> products, String sortBy, String sortOrder) {
        boolean ascending = "asc".equalsIgnoreCase(sortOrder);

        Comparator<Product> comparator;

        switch (sortBy.toLowerCase()) {
            case "price":
                comparator = Comparator.comparing(
                        p -> p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO
                );
                break;

            case "title":
            case "name":
                comparator = Comparator.comparing(
                        p -> p.getName() != null ? p.getName() : "",
                        String.CASE_INSENSITIVE_ORDER
                );
                break;

            case "relevance":
            default:
                // For relevance, keep original order (could implement relevance scoring)
                return products;
        }

        if (!ascending) {
            comparator = comparator.reversed();
        }

        return products.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    /**
     * Get product by ID (UCP catalog lookup capability)
     *
     * GET /ucp/catalog/lookup?id={productId}
     * Returns single product object with options and variants
     */
    @GetMapping("/lookup")
    public ResponseEntity<Map<String, Object>> lookupProduct(
            @RequestParam(required = true) String id,
            @RequestHeader(value = "UCP-Agent", required = false) String ucpAgent) {

        log.info("Catalog lookup: id='{}' (UCP-Agent: {})",
                id, ucpAgent != null ? ucpAgent : "not specified");

        return productService.getProduct(id)
                .map(product -> {
                    UcpProduct ucpProduct = UcpProduct.fromProduct(product);

                    // Build UCP response
                    Map<String, Object> response = new LinkedHashMap<>();

                    // UCP envelope (uses catalog.lookup capability)
                    Map<String, Object> ucp = new LinkedHashMap<>();
                    ucp.put("version", ucpVersion);

                    Map<String, Object> capabilities = new LinkedHashMap<>();
                    capabilities.put(CATALOG_LOOKUP_CAPABILITY, List.of(Map.of("version", ucpVersion)));
                    ucp.put("capabilities", capabilities);

                    response.put("ucp", ucp);

                    // Convert UcpProduct to Map for manipulation
                    Map<String, Object> productMap = convertToMap(ucpProduct);

                    // Add options (for simple products, empty array)
                    productMap.putIfAbsent("options", new ArrayList<>());

                    // Add variants (for simple products, empty array)
                    productMap.putIfAbsent("variants", new ArrayList<>());

                    response.put("product", productMap);

                    log.info("Returning lookup response for product: {}", id);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    log.warn("Product not found: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * Convert object to Map using ObjectMapper
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(Object obj) {
        return objectMapper.convertValue(obj, Map.class);
    }
}
