package com.uniblox.ecommerce.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CartResponse {
    private Long cartId;
    private String status;
    private List<CartItemDto> items;
    private BigDecimal totalAmount;
}
