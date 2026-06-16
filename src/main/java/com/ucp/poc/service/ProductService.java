package com.ucp.poc.service;

import com.ucp.poc.model.Product;
import com.ucp.poc.model.ProductCondition;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProductService {

    private final Map<String, Product> products = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeProducts() {
        // Initialize Adidas product catalog
        addProduct(Product.builder()
                .id("prod-001")
                .sku("ADIDAS-SL72-BLU-001")
                .gtin("4063699123456")
                .name("SL 72 RS Shoes")
                .brand("Adidas")
                .description("Classic retro running shoes with suede and nylon upper, inspired by the 1972 Munich Olympics")
                .price(new BigDecimal("110.00"))
                .currency("USD")
                .images(Arrays.asList(
                    "https://assets.adidas.com/images/w_766,h_766,f_auto,q_auto,fl_lossy,c_fill,g_auto/bad9647ac68648c193681dac9a9f3475_9366/Zapatilla_SL_72_RS_Azul_IG2132_02_standard_hover.jpg",
                    "https://assets.adidas.com/images/w_766,h_766,f_auto,q_auto,fl_lossy,c_fill,g_auto/bad9647ac68648c193681dac9a9f3475_9366/Zapatilla_SL_72_RS_Azul_IG2132_01_standard.jpg"
                ))
                .stockQuantity(45)
                .availability("InStock")
                .condition(ProductCondition.NEW)
                .category("Footwear")
                .weight(new BigDecimal("0.8"))
                .build());

        addProduct(Product.builder()
                .id("prod-002")
                .sku("ADIDAS-HDBLL-BLU-002")
                .gtin("4063699234567")
                .name("Handball Spezial Shoes")
                .brand("Adidas")
                .description("Iconic indoor court shoes with suede upper and gum rubber outsole")
                .price(new BigDecimal("100.00"))
                .currency("USD")
                .images(Arrays.asList(
                    "https://assets.adidas.com/images/w_766,h_766,f_auto,q_auto,fl_lossy,c_fill,g_auto/24d6e4bd92a24af289a0a96d0101f83d_9366/Zapatilla_Handball_Spezial_Azul_BD7633_02_standard_hover.jpg"
                ))
                .stockQuantity(60)
                .availability("InStock")
                .condition(ProductCondition.NEW)
                .category("Footwear")
                .weight(new BigDecimal("0.75"))
                .build());

        addProduct(Product.builder()
                .id("prod-003")
                .sku("ADIDAS-SAMBA-WHT-003")
                .gtin("4063699345678")
                .name("Samba OG Shoes")
                .brand("Adidas")
                .description("Legendary soccer-inspired sneakers with premium leather upper and signature 3-Stripes")
                .price(new BigDecimal("100.00"))
                .currency("USD")
                .images(Arrays.asList(
                    "https://assets.adidas.com/images/w_766,h_766,f_auto,q_auto,fl_lossy,c_fill,g_auto/ec595635a2994adea094a8bf0117ef1a_9366/Zapatilla_Samba_OG_Blanco_B75806_02_standard_hover.jpg"
                ))
                .stockQuantity(80)
                .availability("InStock")
                .condition(ProductCondition.NEW)
                .category("Footwear")
                .weight(new BigDecimal("0.7"))
                .build());

        addProduct(Product.builder()
                .id("prod-004")
                .sku("ADIDAS-CLIM-BLK-004")
                .gtin("4063699456789")
                .name("Climacool T-Shirt")
                .brand("Adidas")
                .description("Breathable performance t-shirt with Climacool technology for optimal ventilation")
                .price(new BigDecimal("35.00"))
                .currency("USD")
                .images(Arrays.asList(
                    "https://assets.adidas.com/images/w_766,h_766,f_auto,q_auto,fl_lossy,c_fill,g_auto/2ed9502fbdff4322b6513ab604a592d2_9366/Camiseta_Club_3-Stripes_Tennis_Climacool_Negro_JN7093_23_hover_model.jpg"
                ))
                .stockQuantity(120)
                .availability("InStock")
                .condition(ProductCondition.NEW)
                .category("Apparel")
                .weight(new BigDecimal("0.2"))
                .build());

        addProduct(Product.builder()
                .id("prod-005")
                .sku("ADIDAS-TERX-BLK-005")
                .gtin("4063699567890")
                .name("Terrex Multi T-Shirt")
                .brand("Adidas")
                .description("Technical outdoor t-shirt with moisture-wicking fabric, perfect for hiking and trail running")
                .price(new BigDecimal("40.00"))
                .currency("USD")
                .images(Arrays.asList(
                    "https://assets.adidas.com/images/w_766,h_766,f_auto,q_auto,fl_lossy,c_fill,g_auto/efe29d07fb97448482660d70c024cb29_9366/Camiseta_estampada_Terrex_Multi_CLIMACOOL_Negro_KF1189_23_hover_model.jpg"
                ))
                .stockQuantity(90)
                .availability("InStock")
                .condition(ProductCondition.NEW)
                .category("Apparel")
                .weight(new BigDecimal("0.25"))
                .build());

        addProduct(Product.builder()
                .id("prod-006")
                .sku("ADIDAS-SOCK-WHT-006")
                .gtin("4063699678901")
                .name("3-Stripes Crew Socks (3 Pairs)")
                .brand("Adidas")
                .description("Classic crew socks with iconic 3-Stripes design, cushioned footbed for comfort")
                .price(new BigDecimal("18.00"))
                .currency("USD")
                .images(Arrays.asList(
                    "https://assets.adidas.com/images/w_766,h_766,f_auto,q_auto,fl_lossy,c_fill,g_auto/1723f686e0db4ac5a7ec3fed74231948_9366/Calcetin_clasico_3-Stripes_3_Pairs_Blanco_JV7403_41_detail_hover.jpg"
                ))
                .stockQuantity(200)
                .availability("InStock")
                .condition(ProductCondition.NEW)
                .category("Accessories")
                .weight(new BigDecimal("0.15"))
                .build());
    }

    public void addProduct(Product product) {
        products.put(product.getId(), product);
    }

    public Optional<Product> getProduct(String productId) {
        return Optional.ofNullable(products.get(productId));
    }

    public List<Product> getAllProducts() {
        return new ArrayList<>(products.values());
    }

    public List<Product> getProductsByCategory(String category) {
        return products.values().stream()
                .filter(p -> p.getCategory().equalsIgnoreCase(category))
                .toList();
    }

    public boolean updateStock(String productId, int quantity) {
        Product product = products.get(productId);
        if (product != null && product.getStockQuantity() >= quantity) {
            product.setStockQuantity(product.getStockQuantity() - quantity);
            return true;
        }
        return false;
    }

    public boolean isInStock(String productId, int quantity) {
        Product product = products.get(productId);
        return product != null && product.getStockQuantity() >= quantity;
    }
}
