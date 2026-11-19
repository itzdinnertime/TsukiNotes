-- Search rows are now kept up to date ONE ITEM AT A TIME instead of rebuilding them all.
--
-- V13 made item_search_view a materialized view: searches got fast (GIN index on tags), but every
-- change rebuilt all of it with REFRESH MATERIALIZED VIEW, which at 100k items took ~0.9 s per save.
-- PostgreSQL can't refresh part of a materialized view, so it becomes an ordinary table with the
-- same name, columns and indexes, and triggers recompute just the row of the item that changed.
-- Searches read exactly the same thing as before; the application code is unchanged.

DROP TRIGGER items_refresh_search_view ON items;
DROP TRIGGER item_tags_refresh_search_view ON item_tags;
DROP TRIGGER item_folders_refresh_search_view ON item_folders;
DROP TRIGGER tags_refresh_search_view ON tags;
DROP TRIGGER items_truncate_refresh_search_view ON items;
DROP TRIGGER item_folders_truncate_refresh_search_view ON item_folders;
DROP TRIGGER item_tags_truncate_refresh_search_view ON item_tags;
DROP FUNCTION refresh_item_search_view_once();
DROP FUNCTION refresh_item_search_view_now();
DROP MATERIALIZED VIEW item_search_view;

CREATE TABLE item_search_view (
    id INTEGER PRIMARY KEY,
    identity_id INTEGER NOT NULL,
    unique_key VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    content VARCHAR(255) NOT NULL,
    created_at TIMESTAMP,
    has_file BOOLEAN NOT NULL,
    original_file_name VARCHAR(255),
    content_type VARCHAR(100),
    size_bytes BIGINT,
    tags VARCHAR[] NOT NULL,
    folder_ids BIGINT[] NOT NULL
);

-- Multi-tag AND search.
CREATE INDEX item_search_view_tags_idx ON item_search_view USING GIN (tags);
-- "My files": one user's items, newest first.
CREATE INDEX item_search_view_identity_created_idx ON item_search_view (identity_id, created_at DESC);

-- The definition of a search row (what the view used to be), for one item or for all (NULL).
CREATE FUNCTION item_search_rows(only_item_id INTEGER)
RETURNS SETOF item_search_view
LANGUAGE sql STABLE AS $$
    SELECT
        items.id,
        items.identity_id,
        items.unique_key,
        items.type,
        items.content,
        items.created_at,
        items.stored_file_name IS NOT NULL,
        items.original_file_name,
        items.content_type,
        items.size_bytes,
        array_remove(array_agg(tags.name), NULL)::VARCHAR[],
        COALESCE(
            (SELECT array_agg(item_folders.folder_id::bigint ORDER BY item_folders.folder_id)
             FROM item_folders WHERE item_folders.item_id = items.id),
            '{}'::bigint[]
        )
    FROM items
    LEFT JOIN item_tags ON items.id = item_tags.item_id
    LEFT JOIN tags ON item_tags.tag_id = tags.id
    WHERE only_item_id IS NULL OR items.id = only_item_id
    GROUP BY items.id
$$;

-- Recomputes one item's search row (or removes it if the item is gone).
CREATE FUNCTION sync_item_search(changed_item_id INTEGER) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    -- Two transactions changing the same item at once (e.g. adding different tags) must not
    -- overwrite each other's row. Locking the item makes the second wait for the first to commit;
    -- each statement below then takes a fresh snapshot, so it sees the first one's changes.
    PERFORM 1 FROM items WHERE id = changed_item_id FOR NO KEY UPDATE;
    DELETE FROM item_search_view WHERE id = changed_item_id;
    INSERT INTO item_search_view SELECT * FROM item_search_rows(changed_item_id);
END;
$$;

CREATE FUNCTION item_search_on_item_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM item_search_view WHERE id = OLD.id;
    ELSE
        PERFORM sync_item_search(NEW.id);
    END IF;
    RETURN NULL;
END;
$$;

-- For item_tags and item_folders, whose rows point at an item through item_id.
CREATE FUNCTION item_search_on_link_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        PERFORM sync_item_search(OLD.item_id);
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') AND (TG_OP = 'INSERT' OR NEW.item_id <> OLD.item_id) THEN
        PERFORM sync_item_search(NEW.item_id);
    END IF;
    RETURN NULL;
END;
$$;

-- Renaming a tag changes the rows of every item that has it.
CREATE FUNCTION item_search_on_tag_rename() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    tagged_item_id INTEGER;
BEGIN
    FOR tagged_item_id IN SELECT item_id FROM item_tags WHERE tag_id = NEW.id LOOP
        PERFORM sync_item_search(tagged_item_id);
    END LOOP;
    RETURN NULL;
END;
$$;

-- TRUNCATE skips row triggers, so it rebuilds everything (rare: tests and manual clean-ups).
CREATE FUNCTION item_search_rebuild_all() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    DELETE FROM item_search_view;
    INSERT INTO item_search_view SELECT * FROM item_search_rows(NULL);
    RETURN NULL;
END;
$$;

CREATE TRIGGER items_sync_search AFTER INSERT OR UPDATE OR DELETE ON items
    FOR EACH ROW EXECUTE FUNCTION item_search_on_item_change();
CREATE TRIGGER item_tags_sync_search AFTER INSERT OR UPDATE OR DELETE ON item_tags
    FOR EACH ROW EXECUTE FUNCTION item_search_on_link_change();
CREATE TRIGGER item_folders_sync_search AFTER INSERT OR UPDATE OR DELETE ON item_folders
    FOR EACH ROW EXECUTE FUNCTION item_search_on_link_change();
CREATE TRIGGER tags_sync_search AFTER UPDATE OF name ON tags
    FOR EACH ROW EXECUTE FUNCTION item_search_on_tag_rename();

CREATE TRIGGER items_truncate_sync_search AFTER TRUNCATE ON items
    FOR EACH STATEMENT EXECUTE FUNCTION item_search_rebuild_all();
CREATE TRIGGER item_tags_truncate_sync_search AFTER TRUNCATE ON item_tags
    FOR EACH STATEMENT EXECUTE FUNCTION item_search_rebuild_all();
CREATE TRIGGER item_folders_truncate_sync_search AFTER TRUNCATE ON item_folders
    FOR EACH STATEMENT EXECUTE FUNCTION item_search_rebuild_all();

-- Fill it with every existing item.
INSERT INTO item_search_view SELECT * FROM item_search_rows(NULL);
