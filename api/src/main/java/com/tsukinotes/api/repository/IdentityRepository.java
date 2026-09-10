package com.tsukinotes.api.repository;

import com.tsukinotes.api.domain.Identity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdentityRepository extends JpaRepository<Identity, Long> {
    Optional<Identity> findByName(String name);
}
