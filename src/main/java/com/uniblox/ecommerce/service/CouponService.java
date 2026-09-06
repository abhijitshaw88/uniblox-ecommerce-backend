package com.uniblox.ecommerce.service;

import com.uniblox.ecommerce.dto.CouponResponse;
import com.uniblox.ecommerce.exception.BadRequestException;
import com.uniblox.ecommerce.model.Coupon;
import com.uniblox.ecommerce.repository.CouponRepository;
import com.uniblox.ecommerce.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final OrderRepository orderRepository;

    @Value("${app.coupon.milestone-n:5}")
    private long milestoneN;

    @Value("${app.coupon.discount-percentage-x:10.0}")
    private BigDecimal discountPercentageX;

    @Transactional
    public CouponResponse generateMilestoneCoupon() {
        long totalOrders = orderRepository.countSuccessfulOrders();

        if (totalOrders == 0 || totalOrders < milestoneN) {
            throw new BadRequestException("Milestone not reached yet. Total successful orders: " + totalOrders);
        }

        // Determine the highest milestone reached
        long currentMilestone = totalOrders / milestoneN;
        long milestoneOrderNumber = currentMilestone * milestoneN;

        if (couponRepository.existsByMilestoneOrderNumber(milestoneOrderNumber)) {
            throw new BadRequestException("Coupon for milestone " + milestoneOrderNumber + " has already been generated.");
        }

        String code = "OFF" + discountPercentageX.intValue() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Coupon coupon = Coupon.builder()
                .code(code)
                .discountPercentage(discountPercentageX)
                .isRedeemed(false)
                .milestoneOrderNumber(milestoneOrderNumber)
                .build();

        coupon = couponRepository.save(coupon);

        return CouponResponse.builder()
                .code(coupon.getCode())
                .discountPercentage(coupon.getDiscountPercentage())
                .milestoneOrderNumber(coupon.getMilestoneOrderNumber())
                .build();
    }
}
