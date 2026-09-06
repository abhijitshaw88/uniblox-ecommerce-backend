package com.uniblox.ecommerce.integration;

import com.uniblox.ecommerce.dto.CartItemRequest;
import com.uniblox.ecommerce.dto.CheckoutRequest;
import com.uniblox.ecommerce.model.Cart;
import com.uniblox.ecommerce.model.CartStatus;
import com.uniblox.ecommerce.model.Product;
import com.uniblox.ecommerce.repository.CartRepository;
import com.uniblox.ecommerce.repository.ProductRepository;
import com.uniblox.ecommerce.service.CartService;
import com.uniblox.ecommerce.service.CheckoutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class CheckoutConcurrencyIntegrationTest {

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    private Product limitedProduct;

    @BeforeEach
    void setUp() {
        // Create a product with strictly limited inventory (only 2 in stock)
        limitedProduct = Product.builder()
                .name("Limited Edition Sneaker")
                .price(new BigDecimal("100.00"))
                .availableInventory(2)
                .build();
        limitedProduct = productRepository.save(limitedProduct);
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
        cartRepository.deleteAll();
    }

    @Test
    void testConcurrentCheckout_PreventsOverselling() throws InterruptedException {
        int numberOfConcurrentUsers = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfConcurrentUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfConcurrentUsers);

        AtomicInteger successfulCheckouts = new AtomicInteger(0);
        AtomicInteger failedCheckouts = new AtomicInteger(0);

        // Prepare 10 carts, all trying to buy 1 unit of the limited product
        for (int i = 0; i < numberOfConcurrentUsers; i++) {
            final Long cartId = cartService.createCart().getCartId();
            
            CartItemRequest itemRequest = new CartItemRequest();
            itemRequest.setProductId(limitedProduct.getId());
            itemRequest.setQuantity(1);
            cartService.addItem(cartId, itemRequest);

            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for the signal to start all at once
                    
                    CheckoutRequest checkoutRequest = new CheckoutRequest();
                    checkoutRequest.setCartId(cartId);
                    
                    checkoutService.checkout(checkoutRequest, UUID.randomUUID().toString());
                    successfulCheckouts.incrementAndGet();
                } catch (Exception e) {
                    failedCheckouts.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        // Wait for all threads to finish
        endLatch.await();
        executorService.shutdown();

        // Assertions
        // Since there were only 2 items in stock, exactly 2 checkouts should succeed
        assertEquals(2, successfulCheckouts.get(), "Exactly 2 checkouts should succeed");
        // The remaining 8 should fail due to insufficient inventory
        assertEquals(8, failedCheckouts.get(), "Exactly 8 checkouts should fail");

        // Verify database state: Inventory must be exactly 0 (not negative)
        Product updatedProduct = productRepository.findById(limitedProduct.getId()).orElseThrow();
        assertEquals(0, updatedProduct.getAvailableInventory(), "Inventory should never drop below zero");
    }
}
