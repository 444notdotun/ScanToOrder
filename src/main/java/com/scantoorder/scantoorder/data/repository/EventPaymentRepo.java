package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.EventPayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventPaymentRepo extends JpaRepository<EventPayment, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM EventPayment p WHERE p.reference = :reference")
    Optional<EventPayment> findForUpdateByReference(@Param("reference") String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM EventPayment p WHERE p.reference = :reference AND p.tenant.tenantId = :tenantId")
    Optional<EventPayment> findForUpdateByReferenceAndTenant(@Param("reference") String reference, @Param("tenantId") String tenantId);

    boolean existsByEvent_IdAndStatus(String eventId, com.scantoorder.scantoorder.data.model.PaymentStatus status);
}
