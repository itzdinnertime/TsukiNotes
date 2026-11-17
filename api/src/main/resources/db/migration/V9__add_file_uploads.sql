-- Uploaded files live on disk under a random name; these columns describe them.
-- They stay NULL for link/note items that aren't uploads.
ALTER TABLE items ADD COLUMN stored_file_name VARCHAR(64);
ALTER TABLE items ADD COLUMN original_file_name VARCHAR(255);
ALTER TABLE items ADD COLUMN content_type VARCHAR(100);
ALTER TABLE items ADD COLUMN size_bytes BIGINT;

DROP VIEW item_search_view;

CREATE VIEW item_search_view AS
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
    array_remove(array_agg(tags.name), NULL) AS tags
FROM items
LEFT JOIN item_tags ON items.id = item_tags.item_id
LEFT JOIN tags ON item_tags.tag_id = tags.id
GROUP BY items.id;
