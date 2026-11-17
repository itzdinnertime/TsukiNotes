-- Search used to read a plain view, which re-aggregates every item's tags and folders on every
-- query. It is now a MATERIALIZED view: the aggregated rows are stored, and a GIN index on the
-- tags array makes multi-tag AND search (tags @> '{a,b}') an index lookup instead of a full scan.
-- Same name and columns as before, so the application code reading it is unchanged.

DROP VIEW item_search_view;

CREATE MATERIALIZED VIEW item_search_view AS
SELECT
    items.id,
    items.identity_id,
    items.unique_key,
    items.type,
    items.content,
    items.created_at,
    items.stored_file_name IS NOT NULL AS has_file,
    items.original_file_name,
    items.content_type,
    items.size_bytes,
    array_remove(array_agg(tags.name), NULL) AS tags,
    COALESCE(
        (SELECT array_agg(item_folders.folder_id::bigint ORDER BY item_folders.folder_id)
         FROM item_folders WHERE item_folders.item_id = items.id),
        '{}'::bigint[]
    ) AS folder_ids
FROM items
LEFT JOIN item_tags ON items.id = item_tags.item_id
LEFT JOIN tags ON item_tags.tag_id = tags.id
GROUP BY items.id;

-- Required for REFRESH ... CONCURRENTLY (which lets searches keep reading during a refresh).
CREATE UNIQUE INDEX item_search_view_id_idx ON item_search_view (id);
-- Multi-tag AND search.
CREATE INDEX item_search_view_tags_idx ON item_search_view USING GIN (tags);
-- "My files": one user's items, newest first.
CREATE INDEX item_search_view_identity_created_idx ON item_search_view (identity_id, created_at DESC);

-- Keeping it up to date: any change to items, their tags or their folders refreshes the view
-- once, when the changing transaction commits. The triggers are DEFERRED, so they all run at
-- commit after every change is made; the first one refreshes and the rest see the flag and skip.
-- The refresh runs inside the same transaction, so the change and the refreshed view become
-- visible together: a search right after an upload always finds it.
CREATE FUNCTION refresh_item_search_view_once() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF current_setting('tsukinotes.search_view_refreshed', true) IS DISTINCT FROM 'on' THEN
        REFRESH MATERIALIZED VIEW CONCURRENTLY item_search_view;
        PERFORM set_config('tsukinotes.search_view_refreshed', 'on', true); -- true = this transaction only
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER items_refresh_search_view
    AFTER INSERT OR UPDATE OR DELETE ON items
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION refresh_item_search_view_once();
CREATE CONSTRAINT TRIGGER item_tags_refresh_search_view
    AFTER INSERT OR UPDATE OR DELETE ON item_tags
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION refresh_item_search_view_once();
CREATE CONSTRAINT TRIGGER item_folders_refresh_search_view
    AFTER INSERT OR UPDATE OR DELETE ON item_folders
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION refresh_item_search_view_once();
CREATE CONSTRAINT TRIGGER tags_refresh_search_view
    AFTER UPDATE OR DELETE ON tags
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION refresh_item_search_view_once();

-- TRUNCATE skips row triggers, so it gets its own (immediate) refresh.
CREATE FUNCTION refresh_item_search_view_now() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    REFRESH MATERIALIZED VIEW item_search_view;
    RETURN NULL;
END;
$$;

CREATE TRIGGER items_truncate_refresh_search_view
    AFTER TRUNCATE ON items FOR EACH STATEMENT EXECUTE FUNCTION refresh_item_search_view_now();
CREATE TRIGGER item_folders_truncate_refresh_search_view
    AFTER TRUNCATE ON item_folders FOR EACH STATEMENT EXECUTE FUNCTION refresh_item_search_view_now();
CREATE TRIGGER item_tags_truncate_refresh_search_view
    AFTER TRUNCATE ON item_tags FOR EACH STATEMENT EXECUTE FUNCTION refresh_item_search_view_now();
