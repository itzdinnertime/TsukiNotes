package com.tsukinotes.api.repository;

import com.tsukinotes.api.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {
    boolean existsByIdentityIdAndUniqueKey(Long identityId, String uniqueKey);

    Optional<Item> findByIdAndIdentityId(Long id, Long identityId);

    /** item_tags has no ON DELETE CASCADE, so an item's tag links must be removed before the item. */
    @Modifying
    @Query(value = "DELETE FROM item_tags WHERE item_id = :itemId", nativeQuery = true)
    void deleteTagLinks(@Param("itemId") Long itemId);

    /** Stores the text inside an uploaded text/code file so searches can look inside it. */
    @Modifying
    @Query(value = """
            INSERT INTO item_texts (item_id, body) VALUES (:itemId, :body)
            ON CONFLICT (item_id) DO UPDATE SET body = EXCLUDED.body""", nativeQuery = true)
    void saveText(@Param("itemId") Long itemId, @Param("body") String body);

    /** Text/code uploads whose text hasn't been stored yet (uploaded before text search existed). */
    @Query(value = """
            SELECT items.id AS id, items.stored_file_name AS storedFileName FROM items
            WHERE items.stored_file_name IS NOT NULL AND items.type IN ('text', 'code')
              AND NOT EXISTS (SELECT 1 FROM item_texts WHERE item_texts.item_id = items.id)""", nativeQuery = true)
    List<UploadMissingText> findUploadsMissingText();

    interface UploadMissingText {
        Long getId();

        String getStoredFileName();
    }

    /**
     * For those of the given items whose file text contains the query (ignoring case), a short
     * extract around the first match, on one line, with "…" where it was cut.
     */
    @Query(value = """
            SELECT m.item_id AS itemId,
                   CASE WHEN m.pos > :context + 1 THEN '…' ELSE '' END
                   || REGEXP_REPLACE(SUBSTRING(m.body FROM GREATEST(m.pos - :context, 1)
                                               FOR LEAST(m.pos - 1, :context) + CHAR_LENGTH(:query) + :context),
                                     '\\s+', ' ', 'g')
                   || CASE WHEN m.pos - 1 + CHAR_LENGTH(:query) + :context < CHAR_LENGTH(m.body) THEN '…' ELSE '' END
                   AS snippet
            FROM (SELECT item_id, body, STRPOS(LOWER(body), LOWER(:query)) AS pos
                  FROM item_texts WHERE item_id IN (:itemIds)) m
            WHERE m.pos > 0""", nativeQuery = true)
    List<TextMatch> findTextMatches(@Param("itemIds") Collection<Long> itemIds,
                                    @Param("query") String query,
                                    @Param("context") int context);

    interface TextMatch {
        Long getItemId();

        String getSnippet();
    }
}
