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
import java.util.Optional;

@Repository
public interface OrderRepo extends JpaRepository<Order, String> {
    Order findOrderByOrderId(String orderId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT o FROM Order o WHERE o.orderId = :orderId AND o.tenant.tenantId = :tenantId")
    Optional<Order> findForUpdate(@org.springframework.data.repository.query.Param("orderId") String orderId,
                                  @org.springframework.data.repository.query.Param("tenantId") String tenantId);
    List<Order> findByTenant_TenantIdAndOrderStatusOrderByUpdatedAtAsc(String tenantId, OrderStatus status);
    java.util.Optional<Order> findByOrderIdAndTenant_TenantId(String orderId, String tenantId);
    List<Order> findByOrderStatus(OrderStatus status);

    List<Order> findByEventGuest_IdAndOrderStatusIn(String guestId, java.util.Collection<OrderStatus> statuses);

    boolean existsByEventGuest_IdAndOrderStatus(String guestId, OrderStatus status);

    long countByEventGuest_IdAndOrderStatus(String guestId, OrderStatus status);
    List<Order> findByOrderStatusAndUpdatedAtBefore(OrderStatus status, java.time.LocalDateTime time);
    List<Order> findByDinningSessionSessionId(String sessionId);
    List<Order> findByDinningSessionSessionIdAndOrderStatus(String sessionId, OrderStatus status);
    List<Order> findBySessionSeat_IdAndOrderStatus(String sessionSeatId, OrderStatus status);
    long countByDinningSessionSessionIdAndOrderStatusIn(String sessionId, java.util.Collection<OrderStatus> statuses);
}
