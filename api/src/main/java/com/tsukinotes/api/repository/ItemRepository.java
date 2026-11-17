package com.tsukinotes.api.repository;

import com.tsukinotes.api.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {
    boolean existsByIdentityIdAndUniqueKey(Long identityId, String uniqueKey);

    Optional<Item> findByIdAndIdentityId(Long id, Long identityId);

    /** item_tags has no ON DELETE CASCADE, so an item's tag links must be removed before the item. */
    @Modifying
    @Query(value = "DELETE FROM item_tags WHERE item_id = :itemId", nativeQuery = true)
    void deleteTagLinks(@Param("itemId") Long itemId);
}
