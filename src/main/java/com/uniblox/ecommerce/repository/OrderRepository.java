package com.uniblox.ecommerce.repository;

import com.uniblox.ecommerce.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = 'SUCCESS'")
    long countSuccessfulOrders();
    
    @Query("SELECT COALESCE(SUM(o.grossTotal), 0) FROM Order o WHERE o.status = 'SUCCESS'")
    BigDecimal sumGrossRevenue();
    
    @Query("SELECT COALESCE(SUM(o.discountAmount), 0) FROM Order o WHERE o.status = 'SUCCESS'")
    BigDecimal sumTotalDiscounts();
    
    @Query("SELECT COALESCE(SUM(o.netTotal), 0) FROM Order o WHERE o.status = 'SUCCESS'")
    BigDecimal sumNetRevenue();
}
