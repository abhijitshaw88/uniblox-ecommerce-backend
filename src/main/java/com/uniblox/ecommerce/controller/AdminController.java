package com.uniblox.ecommerce.controller;

import com.uniblox.ecommerce.dto.AdminReportResponse;
import com.uniblox.ecommerce.dto.CouponResponse;
import com.uniblox.ecommerce.service.AdminService;
import com.uniblox.ecommerce.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final CouponService couponService;

    @PostMapping("/coupons/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse generateCoupon() {
        return couponService.generateMilestoneCoupon();
    }

    @GetMapping("/reports/summary")
    public AdminReportResponse getSummaryReport() {
        return adminService.generateReport();
    }
}
