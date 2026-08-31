package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepo extends JpaRepository<Payment, String> {
    Optional<Payment> findByReference(String reference);
    Optional<Payment> findByOrderOrderId(String orderId);
    java.util.List<Payment> findByOrderDinningSessionSessionIdAndStatus(String sessionId, com.scantoorder.scantoorder.data.model.PaymentStatus status);
}
