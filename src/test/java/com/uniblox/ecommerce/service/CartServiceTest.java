package com.uniblox.ecommerce.service;

import com.uniblox.ecommerce.dto.CartItemRequest;
import com.uniblox.ecommerce.dto.CartResponse;
import com.uniblox.ecommerce.exception.BadRequestException;
import com.uniblox.ecommerce.exception.ConflictException;
import com.uniblox.ecommerce.exception.ResourceNotFoundException;
import com.uniblox.ecommerce.model.Cart;
import com.uniblox.ecommerce.model.CartItem;
import com.uniblox.ecommerce.model.CartStatus;
import com.uniblox.ecommerce.model.Product;
import com.uniblox.ecommerce.repository.CartItemRepository;
import com.uniblox.ecommerce.repository.CartRepository;
import com.uniblox.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartService cartService;

    private Cart openCart;
    private Product product;

    @BeforeEach
    void setUp() {
        openCart = Cart.builder().id(1L).status(CartStatus.OPEN).build();
        product = Product.builder().id(100L).name("Test Product").price(new BigDecimal("50.00")).availableInventory(10).build();
    }

    @Test
    void createCart_ReturnsNewCart() {
        when(cartRepository.save(any(Cart.class))).thenReturn(openCart);

        CartResponse response = cartService.createCart();

        assertNotNull(response);
        assertEquals(1L, response.getCartId());
        assertEquals("OPEN", response.getStatus());
        assertTrue(response.getItems().isEmpty());
    }

    @Test
    void addItem_ValidItem_AddsToCart() {
        CartItemRequest request = new CartItemRequest();
        request.setProductId(100L);
        request.setQuantity(2);

        when(cartRepository.findById(1L)).thenReturn(Optional.of(openCart));
        when(productRepository.findById(100L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(1L, 100L)).thenReturn(Optional.empty());
        when(cartItemRepository.findByCartId(1L)).thenReturn(List.of(
                CartItem.builder().cartId(1L).productId(100L).quantity(2).build()
        ));

        CartResponse response = cartService.addItem(1L, request);

        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals(new BigDecimal("100.00"), response.getTotalAmount());
        verify(cartItemRepository, times(1)).save(any(CartItem.class));
    }

    @Test
    void addItem_ToCheckedOutCart_ThrowsConflictException() {
        Cart checkedOutCart = Cart.builder().id(2L).status(CartStatus.CHECKED_OUT).build();
        CartItemRequest request = new CartItemRequest();
        request.setProductId(100L);
        request.setQuantity(1);

        when(cartRepository.findById(2L)).thenReturn(Optional.of(checkedOutCart));

        assertThrows(ConflictException.class, () -> cartService.addItem(2L, request));
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addItem_InvalidQuantity_ThrowsBadRequestException() {
        CartItemRequest request = new CartItemRequest();
        request.setProductId(100L);
        request.setQuantity(0);

        when(cartRepository.findById(1L)).thenReturn(Optional.of(openCart));
        when(productRepository.findById(100L)).thenReturn(Optional.of(product));

        assertThrows(BadRequestException.class, () -> cartService.addItem(1L, request));
    }

    @Test
    void updateItemQuantity_SetsQuantityToZero_RemovesItem() {
        when(cartRepository.findById(1L)).thenReturn(Optional.of(openCart));

        cartService.updateItemQuantity(1L, 100L, 0);

        verify(cartItemRepository, times(1)).deleteByCartIdAndProductId(1L, 100L);
    }
}
