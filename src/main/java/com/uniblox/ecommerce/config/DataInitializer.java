package com.uniblox.ecommerce.config;

import com.uniblox.ecommerce.model.Product;
import com.uniblox.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final ProductRepository productRepository;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            if (productRepository.count() == 0) {
                log.info("Seeding initial products...");
                
                List<Product> products = List.of(
                        Product.builder().name("Wireless Headphones").price(new BigDecimal("150.00")).availableInventory(50).build(),
                        Product.builder().name("Smartphone").price(new BigDecimal("799.99")).availableInventory(20).build(),
                        Product.builder().name("Laptop").price(new BigDecimal("1200.00")).availableInventory(10).build(),
                        Product.builder().name("Gaming Mouse").price(new BigDecimal("60.00")).availableInventory(100).build(),
                        Product.builder().name("Limited Edition Keyboard").price(new BigDecimal("250.00")).availableInventory(2).build() // Limited inventory
                );

                productRepository.saveAll(products);
                log.info("Seeded 5 products into the database.");
            }
        };
    }
}
