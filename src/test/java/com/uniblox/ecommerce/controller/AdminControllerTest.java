package com.uniblox.ecommerce.controller;

import com.uniblox.ecommerce.dto.AdminReportResponse;
import com.uniblox.ecommerce.dto.CouponResponse;
import com.uniblox.ecommerce.service.AdminService;
import com.uniblox.ecommerce.service.CouponService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private CouponService couponService;

    @Test
    void generateCoupon_ReturnsCreated() throws Exception {
        when(couponService.generateMilestoneCoupon()).thenReturn(CouponResponse.builder().build());

        mockMvc.perform(post("/api/admin/coupons/generate"))
                .andExpect(status().isCreated());
    }

    @Test
    void getSummaryReport_ReturnsOk() throws Exception {
        when(adminService.generateReport()).thenReturn(AdminReportResponse.builder().build());

        mockMvc.perform(get("/api/admin/reports/summary"))
                .andExpect(status().isOk());
    }
}
