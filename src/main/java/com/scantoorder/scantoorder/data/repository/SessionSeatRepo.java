package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.data.model.SessionSeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SessionSeatRepo extends JpaRepository<SessionSeat, String> {

    @Query("SELECT ss FROM SessionSeat ss WHERE ss.seat.seatId = :seatId AND ss.status IN :statuses")
    Optional<SessionSeat> findBySeatAndStatusIn(@Param("seatId") String seatId,
                                                @Param("statuses") Collection<SessionSeatStatus> statuses);

    // Ids only, so nothing is cached before the seat rows are locked.
    @Query("SELECT ss.session.sessionId FROM SessionSeat ss WHERE ss.seat.seatId = :seatId AND ss.status IN :statuses")
    Optional<String> findSessionIdBySeatAndStatusIn(@Param("seatId") String seatId,
                                                    @Param("statuses") Collection<SessionSeatStatus> statuses);

    @Query("SELECT ss FROM SessionSeat ss WHERE ss.session.sessionId = :sessionId AND ss.status IN :statuses ORDER BY ss.seat.position")
    List<SessionSeat> findBySessionAndStatusIn(@Param("sessionId") String sessionId,
                                               @Param("statuses") Collection<SessionSeatStatus> statuses);

    @Query("SELECT ss.seat.seatId FROM SessionSeat ss WHERE ss.session.sessionId = :sessionId AND ss.status IN :statuses")
    List<String> findSeatIdsBySessionAndStatusIn(@Param("sessionId") String sessionId,
                                                 @Param("statuses") Collection<SessionSeatStatus> statuses);

    @Query("SELECT ss FROM SessionSeat ss WHERE ss.seat.tableId.tableId = :tableId AND ss.status IN :statuses ORDER BY ss.seat.position")
    List<SessionSeat> findByTableAndStatusIn(@Param("tableId") String tableId,
                                             @Param("statuses") Collection<SessionSeatStatus> statuses);

    @Query("SELECT ss.seat.seatId FROM SessionSeat ss WHERE ss.status = :status AND ss.heldUntil < :time")
    List<String> findSeatIdsByStatusAndHeldUntilBefore(@Param("status") SessionSeatStatus status,
                                                       @Param("time") LocalDateTime time);

    @Query("SELECT COUNT(ss) FROM SessionSeat ss WHERE ss.session.sessionId = :sessionId AND ss.status = :status")
    long countBySessionAndStatus(@Param("sessionId") String sessionId, @Param("status") SessionSeatStatus status);
}
