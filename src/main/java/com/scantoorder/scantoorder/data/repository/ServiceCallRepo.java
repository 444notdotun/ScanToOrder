package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.ServiceCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceCallRepo extends JpaRepository<ServiceCall, String> {
    java.util.List<ServiceCall> findAllByTenant_TenantId(String tenantId);
    java.util.List<ServiceCall> findByTenant_TenantIdAndServiceStatusInOrderByCreatedAtAsc(String tenantId, java.util.Collection<com.scantoorder.scantoorder.data.model.ServiceStatus> statuses);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT c FROM ServiceCall c WHERE c.serviceCallId = :id AND c.tenant.tenantId = :tenantId")
    java.util.Optional<ServiceCall> findForUpdate(@org.springframework.data.repository.query.Param("id") String id, @org.springframework.data.repository.query.Param("tenantId") String tenantId);

    java.util.List<ServiceCall> findByTenant_TenantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String tenantId, java.time.LocalDateTime from, java.time.LocalDateTime to);

    java.util.Optional<ServiceCall> findByServiceCallIdAndTenant_TenantId(String serviceCallId, String tenantId);
}
