package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepo extends JpaRepository<Payment, String> {
    Optional<Payment> findByReference(String reference);

    // The row is locked so two notices for one payment (webhook and verify) cannot both act on it.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM Payment p WHERE p.reference = :reference")
    Optional<Payment> findForUpdateByReference(@org.springframework.data.repository.query.Param("reference") String reference);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM Payment p WHERE p.reference = :reference AND p.tenant.tenantId = :tenantId")
    Optional<Payment> findForUpdateByReferenceAndTenant(@org.springframework.data.repository.query.Param("reference") String reference,
                                                        @org.springframework.data.repository.query.Param("tenantId") String tenantId);

    java.util.List<Payment> findByOrder_OrderIdAndStatusIn(String orderId, java.util.Collection<com.scantoorder.scantoorder.data.model.PaymentStatus> statuses);

    java.util.List<Payment> findByTenant_TenantIdAndStatusOrderByUpdatedAtAsc(String tenantId, com.scantoorder.scantoorder.data.model.PaymentStatus status);
    Optional<Payment> findByReferenceAndTenant_TenantId(String reference, String tenantId);
    Optional<Payment> findByOrderOrderId(String orderId);
    java.util.List<Payment> findByOrderDinningSessionSessionIdAndStatusIn(String sessionId, java.util.Collection<com.scantoorder.scantoorder.data.model.PaymentStatus> statuses);

    java.util.List<Payment> findByOrderDinningSessionSessionIdAndStatus(String sessionId, com.scantoorder.scantoorder.data.model.PaymentStatus status);
}
