package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.EventPlate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventPlateRepo extends JpaRepository<EventPlate, String> {
    List<EventPlate> findByEvent_IdOrderByNameAsc(String eventId);

    Optional<EventPlate> findByIdAndEvent_Id(String id, String eventId);
}
