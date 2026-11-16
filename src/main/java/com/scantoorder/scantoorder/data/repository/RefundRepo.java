package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.Refund;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepo extends JpaRepository<Refund, String> {
    Optional<Refund> findByPayment_Id(String paymentId);

    Optional<Refund> findByOrder_OrderId(String orderId);

    List<Refund> findByTenant_TenantIdOrderByCreatedAtDesc(String tenantId);

    List<Refund> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Refund r WHERE r.id = :id AND r.tenant.tenantId = :tenantId")
    Optional<Refund> findForUpdate(@Param("id") String id, @Param("tenantId") String tenantId);

    // The guest's own door: the id and the token together, nothing else.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Refund r WHERE r.id = :id AND r.guestToken = :token")
    Optional<Refund> findForUpdateByToken(@Param("id") String id, @Param("token") String token);

    @Query("SELECT r FROM Refund r WHERE r.id = :id AND r.guestToken = :token")
    Optional<Refund> findByToken(@Param("id") String id, @Param("token") String token);

    // Overdue, or reported missing by the guest and not yet confirmed.
    @Query("""
            SELECT r FROM Refund r WHERE (r.status = com.scantoorder.scantoorder.data.model.RefundStatus.DUE AND r.createdAt < :cutoff)
               OR (r.status = com.scantoorder.scantoorder.data.model.RefundStatus.SETTLED AND r.guestReportedMissingAt IS NOT NULL)
            ORDER BY r.createdAt ASC""")
    List<Refund> findProblems(@Param("cutoff") LocalDateTime cutoff);
}
