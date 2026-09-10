package com.tsukinotes.api.repository;

import com.tsukinotes.api.domain.ItemTag;
import com.tsukinotes.api.domain.ItemTagId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemTagRepository extends JpaRepository<ItemTag, ItemTagId> {
}
