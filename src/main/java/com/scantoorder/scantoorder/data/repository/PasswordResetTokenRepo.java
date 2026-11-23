package com.scantoorder.scantoorder.data.repository;

import com.scantoorder.scantoorder.data.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepo extends JpaRepository<PasswordResetToken, String> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // Uses one token. Zero rows changed means someone else used it first.
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.id = :id and t.usedAt is null and t.expiresAt > :now")
    int markUsed(@Param("id") String id, @Param("now") Instant now);

    // Every unused token of this person, used up: a new request or a finished reset ends the older links.
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.worker.workerId = :workerId and t.usedAt is null")
    int endAllFor(@Param("workerId") String workerId, @Param("now") Instant now);
}
