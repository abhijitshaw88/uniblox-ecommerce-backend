package com.uniblox.ecommerce.integration;

import com.uniblox.ecommerce.dto.CartItemRequest;
import com.uniblox.ecommerce.dto.CheckoutRequest;
import com.uniblox.ecommerce.dto.OrderResponse;
import com.uniblox.ecommerce.model.Product;
import com.uniblox.ecommerce.repository.CartRepository;
import com.uniblox.ecommerce.repository.OrderRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class CheckoutIdempotencyIntegrationTest {

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartRepository cartRepository;

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .name("Normal Product")
                .price(new BigDecimal("50.00"))
                .availableInventory(10)
                .build();
        product = productRepository.save(product);
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        cartRepository.deleteAll();
    }

    @Test
    void testCheckoutIdempotency_SameKeyReturnsSameOrder() {
        Long cartId = cartService.createCart().getCartId();
        
        CartItemRequest itemRequest = new CartItemRequest();
        itemRequest.setProductId(product.getId());
        itemRequest.setQuantity(1);
        cartService.addItem(cartId, itemRequest);

        CheckoutRequest checkoutRequest = new CheckoutRequest();
        checkoutRequest.setCartId(cartId);
        
        String idempotencyKey = UUID.randomUUID().toString();

        // 1st Attempt: Should succeed and create the order
        long initialOrderCount = orderRepository.count();
        OrderResponse response1 = checkoutService.checkout(checkoutRequest, idempotencyKey);
        
        assertNotNull(response1);
        assertNotNull(response1.getOrderId());
        assertEquals(initialOrderCount + 1, orderRepository.count());
        
        int inventoryAfterFirstCheckout = productRepository.findById(product.getId()).orElseThrow().getAvailableInventory();
        assertEquals(9, inventoryAfterFirstCheckout);

        // 2nd Attempt: Same idempotency key, should NOT create another order, but return the exact same response
        OrderResponse response2 = checkoutService.checkout(checkoutRequest, idempotencyKey);
        
        assertNotNull(response2);
        assertEquals(response1.getOrderId(), response2.getOrderId(), "Order ID should be identical due to idempotency");
        
        // Inventory should remain unchanged (still 9, not 8)
        int inventoryAfterSecondCheckout = productRepository.findById(product.getId()).orElseThrow().getAvailableInventory();
        assertEquals(9, inventoryAfterSecondCheckout, "Inventory should not be deducted again on a retried request");

        // Total orders in DB should remain the same
        assertEquals(initialOrderCount + 1, orderRepository.count(), "No new orders should be created");
    }
}
