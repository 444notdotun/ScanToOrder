package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableRepo extends JpaRepository<RestaurantTable, String> {
    Optional<RestaurantTable> findByTableIdAndTenant_TenantId(String tableId, String tenantId);

    Optional<RestaurantTable> findByTableNumberAndTenant_TenantId(String tableNumber, String tenantId);

    Optional<RestaurantTable> findByTenant_TenantIdAndDisplayNumber(String tenantId, Integer displayNumber);

    boolean existsByTenant_TenantIdAndDisplayNumber(String tenantId, Integer displayNumber);

    @Query("SELECT COUNT(t) FROM RestaurantTable t WHERE t.tenant.tenantId = :tenantId AND t.isActive = true")
    List<RestaurantTable> findByEvent_IdAndIsActiveTrueOrderByDisplayNumberAsc(String eventId);

    long countActive(@Param("tenantId") String tenantId);

    @Query("SELECT t FROM RestaurantTable t WHERE t.tenant.tenantId = :tenantId AND t.isActive = true ORDER BY t.displayNumber")
    List<RestaurantTable> findAllActive(@Param("tenantId") String tenantId);

    @Query("SELECT t.displayNumber FROM RestaurantTable t WHERE t.tenant.tenantId = :tenantId AND t.displayNumber IS NOT NULL ORDER BY t.displayNumber")
    List<Integer> findUsedDisplayNumbers(@Param("tenantId") String tenantId);
}
