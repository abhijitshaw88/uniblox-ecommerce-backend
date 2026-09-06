package com.uniblox.ecommerce.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private Long orderId;
    private Long cartId;
    private BigDecimal grossTotal;
    private BigDecimal discountAmount;
    private BigDecimal netTotal;
    private String couponCode;
    private String status;
    private LocalDateTime createdAt;
    private List<OrderItemDto> items;
}
