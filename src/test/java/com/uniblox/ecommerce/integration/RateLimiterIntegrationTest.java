package com.uniblox.ecommerce.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniblox.ecommerce.dto.CheckoutRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class RateLimiterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void checkoutEndpoint_ExceedsRateLimit_Returns429TooManyRequests() throws Exception {
        CheckoutRequest request = new CheckoutRequest();
        request.setCartId(999L); // Dummy cart ID

        String payload = objectMapper.writeValueAsString(request);

        // We configured a limit of 5 requests per second in application.properties.
        // Therefore, the first 5 requests should get through the rate limiter.
        // (They will return 404 Not Found because cart 999 doesn't exist, which proves they reached the service).
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/checkout")
                            .header("X-Idempotency-Key", "key-" + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isNotFound()); // Bypassed rate limiter, hit the service
        }

        // The 6th request within the same second should be blocked by Resilience4j
        // and return a 429 Too Many Requests status.
        mockMvc.perform(post("/api/checkout")
                        .header("X-Idempotency-Key", "key-blocked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isTooManyRequests());
    }
}
