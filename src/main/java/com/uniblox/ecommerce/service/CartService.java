package com.uniblox.ecommerce.service;

import com.uniblox.ecommerce.dto.CartItemDto;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Transactional
    public CartResponse createCart() {
        Cart cart = Cart.builder()
                .status(CartStatus.OPEN)
                .build();
        cart = cartRepository.save(cart);
        return buildCartResponse(cart, new ArrayList<>());
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long cartId) {
        Cart cart = getCartOrThrow(cartId);
        List<CartItem> items = cartItemRepository.findByCartId(cartId);
        return buildCartResponse(cart, items);
    }

    @Transactional
    public CartResponse addItem(Long cartId, CartItemRequest request) {
        Cart cart = getCartOrThrow(cartId);
        validateCartIsOpen(cart);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (request.getQuantity() <= 0) {
            throw new BadRequestException("Quantity must be greater than zero");
        }

        Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndProductId(cartId, product.getId());
        
        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + request.getQuantity());
            cartItemRepository.save(item);
        } else {
            CartItem newItem = CartItem.builder()
                    .cartId(cart.getId())
                    .productId(product.getId())
                    .quantity(request.getQuantity())
                    .build();
            cartItemRepository.save(newItem);
        }

        return getCart(cartId);
    }

    @Transactional
    public CartResponse updateItemQuantity(Long cartId, Long productId, Integer quantity) {
        Cart cart = getCartOrThrow(cartId);
        validateCartIsOpen(cart);

        if (quantity < 0) {
            throw new BadRequestException("Quantity cannot be negative");
        }

        if (quantity == 0) {
            return removeItem(cartId, productId);
        }

        CartItem item = cartItemRepository.findByCartIdAndProductId(cartId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found in cart"));

        item.setQuantity(quantity);
        cartItemRepository.save(item);

        return getCart(cartId);
    }

    @Transactional
    public CartResponse removeItem(Long cartId, Long productId) {
        Cart cart = getCartOrThrow(cartId);
        validateCartIsOpen(cart);

        cartItemRepository.deleteByCartIdAndProductId(cartId, productId);
        return getCart(cartId);
    }

    private Cart getCartOrThrow(Long cartId) {
        return cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));
    }

    private void validateCartIsOpen(Cart cart) {
        if (cart.getStatus() != CartStatus.OPEN) {
            throw new ConflictException("Cart is already checked out");
        }
    }

    private CartResponse buildCartResponse(Cart cart, List<CartItem> items) {
        List<CartItemDto> itemDtos = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem item : items) {
            // Live pricing: calculate totals based on current product price
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found for id: " + item.getProductId()));
            
            BigDecimal lineTotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            
            totalAmount = totalAmount.add(lineTotal);

            itemDtos.add(CartItemDto.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(item.getQuantity())
                    .unitPrice(product.getPrice())
                    .lineTotal(lineTotal)
                    .build());
        }

        return CartResponse.builder()
                .cartId(cart.getId())
                .status(cart.getStatus().name())
                .items(itemDtos)
                .totalAmount(totalAmount)
                .build();
    }
}
