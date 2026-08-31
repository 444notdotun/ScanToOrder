package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.ServiceCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceCallRepo extends JpaRepository<ServiceCall, String> {
}
