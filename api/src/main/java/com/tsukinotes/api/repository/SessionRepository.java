package com.tsukinotes.api.repository;

import com.tsukinotes.api.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {
    Optional<Session> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);
}
