package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemRepo extends JpaRepository<Item, String> {
    List<Item> findAllByCategoryIdAndIsAvailableTrue(Category categoryId);
    List<Item> findAllByCategoryId(Category categoryId);

    @Query("SELECT i FROM Item i JOIN FETCH i.categoryId c WHERE i.isAvailable = true AND c.isActive = true")
    List<Item> findAllActiveItemsWithCategory();

    Optional<Item> findItemsByItemName(String itemName);
}
