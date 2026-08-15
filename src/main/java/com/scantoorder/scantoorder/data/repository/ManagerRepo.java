package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.Manager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ManagerRepo extends JpaRepository<Manager, String> {
    Optional<Manager> findManagerBy(String username);
}
