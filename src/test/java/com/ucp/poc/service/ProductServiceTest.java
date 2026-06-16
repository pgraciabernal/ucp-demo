package com.ucp.poc.service;

import com.ucp.poc.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProductServiceTest {

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService();
        productService.initializeProducts();
    }

    @Test
    void shouldInitializeWithSampleProducts() {
        List<Product> products = productService.getAllProducts();
        assertEquals(6, products.size());
    }

    @Test
    void shouldGetProductById() {
        Optional<Product> product = productService.getProduct("prod-001");
        assertTrue(product.isPresent());
        assertEquals("Adidas SL72 Shoes", product.get().getName());
    }

    @Test
    void shouldReturnEmptyForNonExistentProduct() {
        Optional<Product> product = productService.getProduct("non-existent");
        assertFalse(product.isPresent());
    }

    @Test
    void shouldGetProductsByCategory() {
        List<Product> footwear = productService.getProductsByCategory("Footwear");
        assertEquals(3, footwear.size());
    }

    @Test
    void shouldUpdateStockWhenAvailable() {
        boolean updated = productService.updateStock("prod-001", 5);
        assertTrue(updated);

        Product product = productService.getProduct("prod-001").get();
        assertEquals(40, product.getStockQuantity()); // 45 - 5
    }

    @Test
    void shouldNotUpdateStockWhenInsufficient() {
        boolean updated = productService.updateStock("prod-001", 100);
        assertFalse(updated);

        Product product = productService.getProduct("prod-001").get();
        assertEquals(45, product.getStockQuantity()); // Unchanged
    }

    @Test
    void shouldCheckIfInStock() {
        assertTrue(productService.isInStock("prod-001", 10));
        assertFalse(productService.isInStock("prod-001", 100));
        assertFalse(productService.isInStock("non-existent", 1));
    }
}
