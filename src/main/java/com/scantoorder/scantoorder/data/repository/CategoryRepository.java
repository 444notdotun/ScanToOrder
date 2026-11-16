package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {
    Optional<Category> findByTenant_TenantIdAndCategoryName(String tenantId, String categoryName);
    List<Category> findAllByTenant_TenantIdAndIsActiveTrue(String tenantId);
    Optional<Category> findByCategoryIdAndTenant_TenantId(String categoryId, String tenantId);
    List<Category> findAllByIsActiveTrue();
    Optional<Category> findCategoryByCategoryId(String categoryId);
}
