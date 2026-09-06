package com.uniblox.ecommerce.service;

import com.uniblox.ecommerce.dto.AdminReportResponse;
import com.uniblox.ecommerce.repository.CouponRepository;
import com.uniblox.ecommerce.repository.OrderItemRepository;
import com.uniblox.ecommerce.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CouponRepository couponRepository;

    @Transactional(readOnly = true)
    public AdminReportResponse generateReport() {
        BigDecimal grossRevenue = orderRepository.sumGrossRevenue();
        BigDecimal totalDiscounts = orderRepository.sumTotalDiscounts();
        BigDecimal netRevenue = orderRepository.sumNetRevenue();
        long totalOrders = orderRepository.countSuccessfulOrders();

        long redeemedCoupons = couponRepository.countByIsRedeemed(true);
        long availableCoupons = couponRepository.countByIsRedeemed(false);
        long generatedCoupons = redeemedCoupons + availableCoupons;

        List<Object[]> purchasedRaw = orderItemRepository.getPurchasedQuantityByProduct();
        Map<String, Long> purchasedQuantityByProduct = new HashMap<>();
        
        for (Object[] row : purchasedRaw) {
            String productName = (String) row[0];
            Long totalQty = ((Number) row[1]).longValue();
            purchasedQuantityByProduct.put(productName, totalQty);
        }

        return AdminReportResponse.builder()
                .purchasedQuantityByProduct(purchasedQuantityByProduct)
                .grossRevenue(grossRevenue)
                .totalDiscountsGranted(totalDiscounts)
                .netRevenue(netRevenue)
                .couponsGenerated(generatedCoupons)
                .couponsAvailable(availableCoupons)
                .couponsRedeemed(redeemedCoupons)
                .totalPlacedOrders(totalOrders)
                .build();
    }
}
