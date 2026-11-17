package com.tsukinotes.api.repository;

import com.tsukinotes.api.domain.ItemSearchView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemSearchViewRepository extends JpaRepository<ItemSearchView, Long> {

    @Query(value = "SELECT * FROM item_search_view WHERE identity_id = :identityId AND tags @> CAST(:tags AS varchar[]) ORDER BY created_at DESC", nativeQuery = true)
    List<ItemSearchView> searchByTags(@Param("identityId") Long identityId, @Param("tags") String[] tags);

    List<ItemSearchView> findByIdentityIdOrderByCreatedAtDesc(Long identityId);
}
