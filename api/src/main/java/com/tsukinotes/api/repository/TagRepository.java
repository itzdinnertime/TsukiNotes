package com.tsukinotes.api.repository;

import com.tsukinotes.api.domain.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {
}
