package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.OrderItem;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderItemRepo extends JpaRepository<OrderItem, String> {
    @Query("""
        SELECT oi FROM OrderItem oi
        JOIN FETCH oi.order o
        WHERE o.orderStatus = :status AND o.tenant.tenantId = :tenantId AND oi.swappedOut = false
        ORDER BY COALESCE(o.swappedAt, o.createdAt) ASC
    """)
    List<OrderItem> findAllByOrderStatusWithOrder(@Param("status") OrderStatus status, @Param("tenantId") String tenantId);

    @Query("""
        SELECT oi FROM OrderItem oi
        JOIN FETCH oi.order o
        WHERE o.orderStatus IN :statuses AND o.tenant.tenantId = :tenantId AND oi.swappedOut = false
        ORDER BY COALESCE(o.swappedAt, o.createdAt) ASC
    """)
    List<OrderItem> findAllByOrderStatusesWithOrder(@Param("statuses") List<OrderStatus> statuses, @Param("tenantId") String tenantId);

    // What was cooked and carried lately: each delivered order's lines, with the order's own times.
    @Query("""
        SELECT oi FROM OrderItem oi
        JOIN FETCH oi.order o
        JOIN FETCH oi.item i
        WHERE o.tenant.tenantId = :tenantId AND o.orderStatus = com.scantoorder.scantoorder.data.model.OrderStatus.DELIVERED
          AND o.paidAt IS NOT NULL AND o.deliveredAt IS NOT NULL AND o.deliveredAt >= :since AND oi.swappedOut = false
    """)
    List<OrderItem> findDeliveredSince(@Param("tenantId") String tenantId, @Param("since") LocalDateTime since);

    List<OrderItem> findByOrder_OrderId(String orderId);

    boolean existsByPlate_Id(String plateId);

    boolean existsByItem_ItemId(String itemId);
}
