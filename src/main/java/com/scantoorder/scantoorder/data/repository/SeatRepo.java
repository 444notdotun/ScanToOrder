package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepo extends JpaRepository<Seat, String> {
    Optional<Seat> findSeatBySeatId(String seatId);

    Optional<List<Seat>> findSeatByTableId(RestaurantTable tableId);

    Optional<Seat> findSeatBySeatNumber(String seatNumber);
}
