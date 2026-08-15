package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepo extends JpaRepository<Item, String> {
}
