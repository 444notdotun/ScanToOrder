package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.Event;
import com.scantoorder.scantoorder.data.model.EventStatus;
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
public interface EventRepo extends JpaRepository<Event, String> {
    Optional<Event> findByIdAndTenant_TenantId(String id, String tenantId);

    List<Event> findByTenant_TenantIdOrderByStartsAtDesc(String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id AND e.tenant.tenantId = :tenantId")
    Optional<Event> findForUpdate(@Param("id") String id, @Param("tenantId") String tenantId);

    List<Event> findByStatusInAndEndsAtBefore(java.util.Collection<EventStatus> statuses, LocalDateTime time);
}
