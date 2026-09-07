package com.uniblox.ecommerce.service;

import com.uniblox.ecommerce.dto.CouponResponse;
import com.uniblox.ecommerce.exception.BadRequestException;
import com.uniblox.ecommerce.model.Coupon;
import com.uniblox.ecommerce.repository.CouponRepository;
import com.uniblox.ecommerce.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CouponServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private CouponService couponService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(couponService, "milestoneN", 5L);
        ReflectionTestUtils.setField(couponService, "discountPercentageX", new BigDecimal("10.0"));
    }

    @Test
    void generateMilestoneCoupon_Eligible_GeneratesCoupon() {
        when(orderRepository.countSuccessfulOrders()).thenReturn(10L); 
        when(couponRepository.existsByMilestoneOrderNumber(10L)).thenReturn(false); 

        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CouponResponse response = couponService.generateMilestoneCoupon();

        assertNotNull(response);
        assertTrue(response.getCode().startsWith("OFF10-"));
        verify(couponRepository, times(1)).save(any(Coupon.class));
    }

    @Test
    void generateMilestoneCoupon_NotEligible_ThrowsBadRequest() {
        when(orderRepository.countSuccessfulOrders()).thenReturn(10L); 
        when(couponRepository.existsByMilestoneOrderNumber(10L)).thenReturn(true); 

        assertThrows(BadRequestException.class, () -> couponService.generateMilestoneCoupon());
        verify(couponRepository, never()).save(any(Coupon.class));
    }
}
