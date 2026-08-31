package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import jdk.jfr.Registered;
import org.aspectj.weaver.ast.Or;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepo extends JpaRepository<Order, String> {
    Order findOrderByOrderId(String orderId);
    List<Order> findByOrderStatus(OrderStatus status);
    List<Order> findByOrderStatusAndUpdatedAtBefore(OrderStatus status, java.time.LocalDateTime time);
    List<Order> findByDinningSessionSessionId(String sessionId);
}
