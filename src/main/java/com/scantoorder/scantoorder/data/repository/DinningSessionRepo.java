package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.DinningSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DinningSessionRepo extends JpaRepository<DinningSession,String> {
    DinningSession findDinningSessionBySessionId(String sessionId);
}
