CREATE VIEW item_search_view AS
SELECT
    items.id,
    items.identity_id,
    items.unique_key,
    items.type,
    items.content,
    items.created_at,
    array_agg(tags.name) AS tags
FROM items
JOIN item_tags ON items.id = item_tags.item_id
JOIN tags ON item_tags.tag_id = tags.id
GROUP BY items.id;
