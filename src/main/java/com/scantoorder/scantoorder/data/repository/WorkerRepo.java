package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkerRepo extends JpaRepository<Worker, String> {
    Optional<Worker> findByUsername(String username);
    boolean existsByUsername(String username);
}
