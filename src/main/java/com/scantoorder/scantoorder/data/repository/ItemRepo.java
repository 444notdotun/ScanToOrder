package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRepo extends JpaRepository<Item, String> {
    List<Item> findAllByCategoryIdAndIsAvailableTrue(Category categoryId);
}
