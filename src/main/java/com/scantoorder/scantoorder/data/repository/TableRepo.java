package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.Table;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TableRepo extends JpaRepository<Table, String> {
}
