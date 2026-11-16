package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepo extends JpaRepository<Seat, String> {

    Optional<Seat> findSeatBySeatId(String seatId);

    List<Seat> findByTableIdOrderByPositionAsc(RestaurantTable tableId);

    List<Seat> findByTenant_TenantId(String tenantId);

    Optional<Seat> findBySeatIdAndTenant_TenantId(String seatId, String tenantId);

    @Query("SELECT s.seatId FROM Seat s WHERE s.tableId.tableId = :tableId")
    List<String> findSeatIdsByTable(@Param("tableId") String tableId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.seatId = :seatId")
    Optional<Seat> findForUpdate(@Param("seatId") String seatId);

    // Always ordered by id so two callers locking the same seats cannot deadlock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.seatId IN :seatIds ORDER BY s.seatId")
    List<Seat> findAllForUpdate(@Param("seatIds") Collection<String> seatIds);
}
