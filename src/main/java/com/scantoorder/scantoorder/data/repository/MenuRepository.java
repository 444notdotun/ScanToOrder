package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MenuRepository extends JpaRepository<Menu, String> {
}
