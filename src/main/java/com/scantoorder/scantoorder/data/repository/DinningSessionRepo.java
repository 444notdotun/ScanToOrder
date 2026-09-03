package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.DinningSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DinningSessionRepo extends JpaRepository<DinningSession,String> {
    Optional<DinningSession> findDinningSessionBySessionId(String sessionId);
    long countBySessionStatus(com.scantoorder.scantoorder.data.model.DinningSessionStatus status);
    Optional<DinningSession> findFirstByTableId_TableIdAndSessionStatusOrderByCreatedAtDesc(String tableId, com.scantoorder.scantoorder.data.model.DinningSessionStatus status);
    Optional<DinningSession> findFirstByClaimedSeatIds(String seatId);
}
