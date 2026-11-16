package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepo extends JpaRepository<Tenant, String> {
    Optional<Tenant> findBySlug(String slug);
    boolean existsBySlug(String slug);

    boolean existsByNormalizedName(String normalizedName);

    Optional<Tenant> findByNormalizedName(String normalizedName);
    Optional<Tenant> findByEmailIgnoreCase(String email);
    List<Tenant> findByStatusOrderByCreatedAtDesc(TenantStatus status);
    List<Tenant> findAllByOrderByCreatedAtDesc();
}
