package com.tsukinotes.api.repository;

import com.tsukinotes.api.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {
    boolean existsByIdentityIdAndUniqueKey(Long identityId, String uniqueKey);

    Optional<Item> findByIdAndIdentityId(Long id, Long identityId);
}
