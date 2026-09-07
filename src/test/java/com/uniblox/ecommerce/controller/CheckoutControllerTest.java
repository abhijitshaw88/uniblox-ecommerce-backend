package com.uniblox.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniblox.ecommerce.dto.CheckoutRequest;
import com.uniblox.ecommerce.dto.OrderResponse;
import com.uniblox.ecommerce.service.CheckoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(CheckoutController.class)
public class CheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CheckoutService checkoutService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void checkout_ValidRequest_ReturnsOrder() throws Exception {
        CheckoutRequest request = new CheckoutRequest();
        request.setCartId(1L);

        OrderResponse response = OrderResponse.builder().orderId(100L).build();
        when(checkoutService.checkout(any(CheckoutRequest.class), eq("test-key"))).thenReturn(response);

        mockMvc.perform(post("/api/checkout")
                        .header("X-Idempotency-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(100L));
    }

    @Test
    void checkout_MissingHeader_ReturnsBadRequest() throws Exception {
        CheckoutRequest request = new CheckoutRequest();
        request.setCartId(1L);

        mockMvc.perform(post("/api/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
