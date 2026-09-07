package com.uniblox.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniblox.ecommerce.dto.CartItemRequest;
import com.uniblox.ecommerce.dto.CartResponse;
import com.uniblox.ecommerce.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
public class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartService cartService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createCart_ReturnsCreated() throws Exception {
        CartResponse mockResponse = CartResponse.builder().cartId(1L).status("OPEN").build();
        when(cartService.createCart()).thenReturn(mockResponse);

        mockMvc.perform(post("/api/carts"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cartId").value(1));
    }

    @Test
    void getCart_ReturnsCart() throws Exception {
        CartResponse mockResponse = CartResponse.builder().cartId(1L).totalAmount(new BigDecimal("100.00")).build();
        when(cartService.getCart(1L)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/carts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(100.00));
    }

    @Test
    void addItem_ReturnsUpdatedCart() throws Exception {
        CartItemRequest request = new CartItemRequest();
        request.setProductId(1L);
        request.setQuantity(2);

        CartResponse mockResponse = CartResponse.builder().cartId(1L).build();
        when(cartService.addItem(eq(1L), any(CartItemRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/carts/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value(1));
    }
}
