package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.OrderItem;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepo extends JpaRepository<OrderItem, String> {
    @Query("""
        SELECT oi FROM OrderItem oi
        JOIN FETCH oi.order o
        WHERE o.orderStatus = :status AND o.tenant.tenantId = :tenantId
        ORDER BY o.createdAt ASC
    """)
    List<OrderItem> findAllByOrderStatusWithOrder(@Param("status") OrderStatus status, @Param("tenantId") String tenantId);

    @Query("""
        SELECT oi FROM OrderItem oi
        JOIN FETCH oi.order o
        WHERE o.orderStatus IN :statuses AND o.tenant.tenantId = :tenantId
        ORDER BY o.createdAt ASC
    """)
    List<OrderItem> findAllByOrderStatusesWithOrder(@Param("statuses") List<OrderStatus> statuses, @Param("tenantId") String tenantId);

    List<OrderItem> findByOrder_OrderId(String orderId);
}
