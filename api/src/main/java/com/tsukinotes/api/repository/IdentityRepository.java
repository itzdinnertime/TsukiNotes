package com.tsukinotes.api.repository;

import com.tsukinotes.api.domain.Identity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityRepository extends JpaRepository<Identity, Long> {
}
