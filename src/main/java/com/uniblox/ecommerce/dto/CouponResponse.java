package com.uniblox.ecommerce.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CouponResponse {
    private String code;
    private BigDecimal discountPercentage;
    private Long milestoneOrderNumber;
}
