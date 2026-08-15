package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepo extends JpaRepository<Transaction, String> {
}
