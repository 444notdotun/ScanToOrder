package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableRepo extends JpaRepository<RestaurantTable, String> {
    Optional<RestaurantTable> findByTableNumber(String tableNumber);
    boolean existsByTableNumber(String tableNumber);
    boolean existsByDisplayNumber(Integer displayNumber);

    @Query("SELECT t FROM RestaurantTable t WHERE t.isActive = true ORDER BY t.displayNumber")
    List<RestaurantTable> findAllActive();

    @Query("SELECT t.displayNumber FROM RestaurantTable t WHERE t.displayNumber IS NOT NULL ORDER BY t.displayNumber")
    List<Integer> findUsedDisplayNumbers();
}
