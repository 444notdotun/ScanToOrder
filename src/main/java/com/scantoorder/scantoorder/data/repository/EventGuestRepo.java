package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.EventGuest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventGuestRepo extends JpaRepository<EventGuest, String> {
    List<EventGuest> findByEvent_IdOrderByCreatedAtAsc(String eventId);

    long countByEvent_Id(String eventId);

    boolean existsByEvent_IdAndCode(String eventId, String code);

    Optional<EventGuest> findByIdAndEvent_Id(String id, String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM EventGuest g WHERE g.id = :id AND g.event.id = :eventId")
    Optional<EventGuest> findForUpdate(@Param("id") String id, @Param("eventId") String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM EventGuest g WHERE g.event.id = :eventId AND g.code = :code")
    Optional<EventGuest> findForUpdateByCode(@Param("eventId") String eventId, @Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM EventGuest g WHERE g.sessionSeat.id = :sessionSeatId")
    Optional<EventGuest> findForUpdateBySessionSeat(@Param("sessionSeatId") String sessionSeatId);

    List<EventGuest> findByAssignedSeat_SeatId(String seatId);
}
