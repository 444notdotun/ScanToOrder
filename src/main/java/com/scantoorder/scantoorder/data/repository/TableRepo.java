package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TableRepo extends JpaRepository<RestaurantTable, String> {

    Optional<RestaurantTable> findByTableNumber(String tableNumber);
}
