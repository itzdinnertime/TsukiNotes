-- Folders a user creates to organise their items (e.g. one per class). An item can be in several folders.
CREATE TABLE folders (
    id SERIAL PRIMARY KEY,
    identity_id INTEGER NOT NULL REFERENCES identities(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Folder names are unique per user, ignoring case.
CREATE UNIQUE INDEX folders_identity_name_unique ON folders (identity_id, LOWER(name));

-- Deleting a folder (or an item) removes the link, never the other side.
CREATE TABLE item_folders (
    item_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    folder_id INTEGER NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
    PRIMARY KEY (item_id, folder_id)
);

CREATE INDEX item_folders_folder_id_idx ON item_folders (folder_id);

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
