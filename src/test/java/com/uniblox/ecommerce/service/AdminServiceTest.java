package com.uniblox.ecommerce.service;

import com.uniblox.ecommerce.dto.AdminReportResponse;
import com.uniblox.ecommerce.repository.CouponRepository;
import com.uniblox.ecommerce.repository.OrderItemRepository;
import com.uniblox.ecommerce.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private AdminService adminService;

    @Test
    void generateReport_CalculatesCorrectly() {
        when(orderRepository.countSuccessfulOrders()).thenReturn(10L);
        when(orderRepository.sumGrossRevenue()).thenReturn(new BigDecimal("1000.00"));
        when(orderRepository.sumTotalDiscounts()).thenReturn(new BigDecimal("100.00"));
        when(orderRepository.sumNetRevenue()).thenReturn(new BigDecimal("900.00"));

        when(couponRepository.countByIsRedeemed(false)).thenReturn(3L);
        when(couponRepository.countByIsRedeemed(true)).thenReturn(2L);

        when(orderItemRepository.getPurchasedQuantityByProduct()).thenReturn(List.of(
            new Object[]{"Product A", 5L},
            new Object[]{"Product B", 3L}
        ));

        AdminReportResponse report = adminService.generateReport();

        assertNotNull(report);
        assertEquals(10L, report.getTotalPlacedOrders());
        assertEquals(new BigDecimal("1000.00"), report.getGrossRevenue());
        assertEquals(new BigDecimal("100.00"), report.getTotalDiscountsGranted());
        assertEquals(new BigDecimal("900.00"), report.getNetRevenue());
        assertEquals(5L, report.getCouponsGenerated());
        assertEquals(3L, report.getCouponsAvailable());
        assertEquals(2L, report.getCouponsRedeemed());
        assertEquals(5L, report.getPurchasedQuantityByProduct().get("Product A"));
        assertEquals(3L, report.getPurchasedQuantityByProduct().get("Product B"));
    }
}
