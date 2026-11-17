package com.tsukinotes.api.repository;

import com.tsukinotes.api.domain.ItemSearchView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemSearchViewRepository extends JpaRepository<ItemSearchView, Long> {

    @Query(value = "SELECT * FROM item_search_view WHERE identity_id = :identityId AND tags @> CAST(:tags AS varchar[]) ORDER BY created_at DESC", nativeQuery = true)
    List<ItemSearchView> searchByTags(@Param("identityId") Long identityId, @Param("tags") String[] tags);

    /**
     * The user's items, newest first, optionally narrowed to one folder and/or a search text.
     * The text matches (case-insensitively, anywhere) the title, the filename / content, or any tag.
     * Pass an empty query for "no text filter" and folderId = 0 for "all folders".
     */
    @Query(value = """
            SELECT v.* FROM item_search_view v
            WHERE v.identity_id = :identityId
              AND (:folderId = 0 OR :folderId = ANY (v.folder_ids))
              AND (:query = ''
                   OR STRPOS(LOWER(v.unique_key), LOWER(:query)) > 0
                   OR STRPOS(LOWER(v.content), LOWER(:query)) > 0
                   OR EXISTS (SELECT 1 FROM UNNEST(v.tags) AS t(name) WHERE STRPOS(LOWER(t.name), LOWER(:query)) > 0))
            ORDER BY v.created_at DESC
            """, nativeQuery = true)
    List<ItemSearchView> findForUser(@Param("identityId") Long identityId,
                                     @Param("folderId") long folderId,
                                     @Param("query") String query);
}
