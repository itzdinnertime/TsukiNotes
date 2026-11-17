package com.tsukinotes.api.repository;

import com.tsukinotes.api.domain.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    /** A folder plus how many items are in it. */
    interface FolderSummary {
        Long getId();

        String getName();

        Long getItemCount();
    }

    @Query(value = """
            SELECT f.id AS "id", f.name AS "name", COUNT(fi.item_id) AS "itemCount"
            FROM folders f
            LEFT JOIN item_folders fi ON fi.folder_id = f.id
            WHERE f.identity_id = :identityId
            GROUP BY f.id
            ORDER BY LOWER(f.name)
            """, nativeQuery = true)
    List<FolderSummary> findSummaries(@Param("identityId") Long identityId);

    boolean existsByIdentityIdAndNameIgnoreCase(Long identityId, String name);

    Optional<Folder> findByIdAndIdentityId(Long id, Long identityId);

    List<Folder> findByIdInAndIdentityId(Collection<Long> ids, Long identityId);

    @Modifying
    @Query(value = "INSERT INTO item_folders (item_id, folder_id) VALUES (:itemId, :folderId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void addItemToFolder(@Param("itemId") Long itemId, @Param("folderId") Long folderId);
}
