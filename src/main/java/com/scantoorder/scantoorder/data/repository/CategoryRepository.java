package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {
}
