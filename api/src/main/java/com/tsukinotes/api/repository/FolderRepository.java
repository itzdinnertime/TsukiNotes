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

    /** For renames: is the name taken by one of the user's *other* folders? */
    boolean existsByIdentityIdAndNameIgnoreCaseAndIdNot(Long identityId, String name, Long id);

    Optional<Folder> findByIdAndIdentityId(Long id, Long identityId);

    List<Folder> findByIdInAndIdentityId(Collection<Long> ids, Long identityId);

    @Modifying
    @Query(value = "INSERT INTO item_folders (item_id, folder_id) VALUES (:itemId, :folderId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void addItemToFolder(@Param("itemId") Long itemId, @Param("folderId") Long folderId);

    @Modifying
    @Query(value = "DELETE FROM item_folders WHERE item_id = :itemId", nativeQuery = true)
    void removeItemFromAllFolders(@Param("itemId") Long itemId);

    /** Uploaded files in this folder that aren't in any other folder (deleting the folder would strand them). */
    @Query(value = """
            SELECT COUNT(*) FROM items i
            JOIN item_folders f ON f.item_id = i.id AND f.folder_id = :folderId
            WHERE i.stored_file_name IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM item_folders o WHERE o.item_id = i.id AND o.folder_id <> :folderId)
            """, nativeQuery = true)
    long countUploadsOnlyInFolder(@Param("folderId") Long folderId);
}
