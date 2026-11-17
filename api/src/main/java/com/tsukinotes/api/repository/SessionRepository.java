package com.tsukinotes.api.repository;

import com.tsukinotes.api.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {
    Optional<Session> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);

    @Modifying
    @Query(value = "DELETE FROM sessions WHERE identity_id = :identityId", nativeQuery = true)
    void deleteByIdentityId(@Param("identityId") Long identityId);
}
