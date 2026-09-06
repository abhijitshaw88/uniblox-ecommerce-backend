package com.uniblox.ecommerce.controller;

import com.uniblox.ecommerce.dto.CartItemRequest;
import com.uniblox.ecommerce.dto.CartResponse;
import com.uniblox.ecommerce.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse createCart() {
        return cartService.createCart();
    }

    @GetMapping("/{id}")
    public CartResponse getCart(@PathVariable Long id) {
        return cartService.getCart(id);
    }

    @PostMapping("/{id}/items")
    public CartResponse addItem(@PathVariable Long id, @Valid @RequestBody CartItemRequest request) {
        return cartService.addItem(id, request);
    }

    @PutMapping("/{id}/items/{productId}")
    public CartResponse updateItemQuantity(
            @PathVariable Long id,
            @PathVariable Long productId,
            @RequestParam Integer quantity) {
        return cartService.updateItemQuantity(id, productId, quantity);
    }

    @DeleteMapping("/{id}/items/{productId}")
    public CartResponse removeItem(
            @PathVariable Long id,
            @PathVariable Long productId) {
        return cartService.removeItem(id, productId);
    }
}
