package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.DinningSession;
import com.scantoorder.scantoorder.data.model.DinningSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DinningSessionRepo extends JpaRepository<DinningSession,String> {
    Optional<DinningSession> findDinningSessionBySessionId(String sessionId);
    long countBySessionStatus(DinningSessionStatus status);
    long countBySessionStatusAndTenant_TenantId(DinningSessionStatus status, String tenantId);
    Optional<DinningSession> findBySessionIdAndTenant_TenantId(String sessionId, String tenantId);
    List<DinningSession> findBySessionStatus(DinningSessionStatus status);
    List<DinningSession> findByTableId_TableIdAndSessionStatusOrderByCreatedAtAsc(String tableId, DinningSessionStatus status);
}
