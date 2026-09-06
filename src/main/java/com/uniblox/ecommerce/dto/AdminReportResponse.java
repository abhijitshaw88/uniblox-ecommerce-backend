package com.uniblox.ecommerce.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class AdminReportResponse {
    private Map<String, Long> purchasedQuantityByProduct;
    private BigDecimal grossRevenue;
    private BigDecimal totalDiscountsGranted;
    private BigDecimal netRevenue;
    private long couponsGenerated;
    private long couponsAvailable;
    private long couponsRedeemed;
    private long totalPlacedOrders;
}
