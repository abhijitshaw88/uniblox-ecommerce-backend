package com.uniblox.ecommerce.controller;

import com.uniblox.ecommerce.dto.CheckoutRequest;
import com.uniblox.ecommerce.dto.OrderResponse;
import com.uniblox.ecommerce.exception.BadRequestException;
import com.uniblox.ecommerce.service.CheckoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping
    public OrderResponse checkout(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {
        
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new BadRequestException("Header X-Idempotency-Key is required");
        }
        
        return checkoutService.checkout(request, idempotencyKey);
    }
}
