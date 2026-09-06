package com.uniblox.ecommerce.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniblox.ecommerce.dto.CheckoutRequest;
import com.uniblox.ecommerce.dto.OrderItemDto;
import com.uniblox.ecommerce.dto.OrderResponse;
import com.uniblox.ecommerce.exception.BadRequestException;
import com.uniblox.ecommerce.exception.ConflictException;
import com.uniblox.ecommerce.exception.ResourceNotFoundException;
import com.uniblox.ecommerce.model.*;
import com.uniblox.ecommerce.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CouponRepository couponRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderResponse checkout(CheckoutRequest request, String idempotencyKey) {
        // 1. Check Idempotency Key
        Optional<IdempotencyRecord> existingRecord = idempotencyRecordRepository.findById(idempotencyKey);
        if (existingRecord.isPresent()) {
            log.info("Returning cached response for idempotency key: {}", idempotencyKey);
            try {
                return objectMapper.readValue(existingRecord.get().getResponseBody(), OrderResponse.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to deserialize idempotency record", e);
            }
        }

        // 2. Validate Cart
        Cart cart = cartRepository.findById(request.getCartId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        if (cart.getStatus() == CartStatus.CHECKED_OUT) {
            throw new ConflictException("Cart is already checked out");
        }

        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        // 3. Process Items and Inventory
        BigDecimal grossTotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        List<OrderItemDto> orderItemDtos = new ArrayList<>();

        for (CartItem cartItem : cartItems) {
            // Use Pessimistic Lock to ensure inventory is not oversold concurrently
            Product product = productRepository.findByIdWithPessimisticLock(cartItem.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + cartItem.getProductId()));

            if (product.getAvailableInventory() < cartItem.getQuantity()) {
                throw new BadRequestException("Insufficient inventory for product: " + product.getName() 
                    + ". Requested: " + cartItem.getQuantity() + ", Available: " + product.getAvailableInventory());
            }

            // Deduct inventory
            product.setAvailableInventory(product.getAvailableInventory() - cartItem.getQuantity());
            productRepository.save(product);

            BigDecimal lineTotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            grossTotal = grossTotal.add(lineTotal);

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .unitPrice(product.getPrice())
                    .quantity(cartItem.getQuantity())
                    .lineTotal(lineTotal)
                    .build();
            orderItems.add(orderItem);

            orderItemDtos.add(OrderItemDto.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(product.getPrice())
                    .lineTotal(lineTotal)
                    .build());
        }

        // 4. Process Coupon
        BigDecimal discountAmount = BigDecimal.ZERO;
        Coupon appliedCoupon = null;

        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            appliedCoupon = couponRepository.findByCodeWithPessimisticLock(request.getCouponCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + request.getCouponCode()));

            if (appliedCoupon.getIsRedeemed()) {
                throw new ConflictException("Coupon has already been redeemed");
            }

            discountAmount = grossTotal.multiply(appliedCoupon.getDiscountPercentage().divide(BigDecimal.valueOf(100)))
                    .setScale(2, RoundingMode.HALF_UP);
            
            // Discount cannot make total negative
            if (discountAmount.compareTo(grossTotal) > 0) {
                discountAmount = grossTotal;
            }

            appliedCoupon.setIsRedeemed(true);
            couponRepository.save(appliedCoupon);
        }

        BigDecimal netTotal = grossTotal.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);

        // 5. Create Order
        Order order = Order.builder()
                .cartId(cart.getId())
                .grossTotal(grossTotal)
                .discountAmount(discountAmount)
                .netTotal(netTotal)
                .couponCode(appliedCoupon != null ? appliedCoupon.getCode() : null)
                .status(OrderStatus.SUCCESS)
                .build();
        
        order = orderRepository.save(order);

        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            orderItemRepository.save(item);
        }

        // 6. Finalize Cart
        cart.setStatus(CartStatus.CHECKED_OUT);
        cartRepository.save(cart);

        // 7. Build Response
        OrderResponse response = OrderResponse.builder()
                .orderId(order.getId())
                .cartId(cart.getId())
                .grossTotal(grossTotal)
                .discountAmount(discountAmount)
                .netTotal(netTotal)
                .couponCode(order.getCouponCode())
                .status(order.getStatus().name())
                .createdAt(order.getCreatedAt())
                .items(orderItemDtos)
                .build();

        // 8. Save Idempotency Record
        try {
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .key(idempotencyKey)
                    .orderId(order.getId())
                    .responseBody(objectMapper.writeValueAsString(response))
                    .build();
            idempotencyRecordRepository.save(record);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize response for idempotency", e);
        }

        return response;
    }
}
