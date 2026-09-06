package com.uniblox.ecommerce.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckoutRequest {
    @NotNull(message = "Cart ID is required")
    private Long cartId;

    private String couponCode;
}
