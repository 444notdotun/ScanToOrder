package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MenuRepository extends JpaRepository<Menu, String> {
    @Query("SELECT m FROM Menu m WHERE m.tenant.tenantId = :tenantId AND m.isActive = true ORDER BY m.menuId")
    java.util.List<Menu> findActiveMenus(@Param("tenantId") String tenantId);

    default Optional<Menu> findActiveMenu(String tenantId) {
        return findActiveMenus(tenantId).stream().findFirst();
    }
}
