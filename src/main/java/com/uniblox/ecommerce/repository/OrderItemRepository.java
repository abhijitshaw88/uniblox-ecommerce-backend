package com.uniblox.ecommerce.repository;

import com.uniblox.ecommerce.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);

    @Query("SELECT oi.productName, SUM(oi.quantity) FROM OrderItem oi GROUP BY oi.productName")
    List<Object[]> getPurchasedQuantityByProduct();
}
