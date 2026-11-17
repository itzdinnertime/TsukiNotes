-- The original view used an inner join, so items without tags were left out.
-- LEFT JOIN keeps them; array_remove turns their {NULL} tag list into an empty array.
DROP VIEW item_search_view;

CREATE VIEW item_search_view AS
SELECT
    items.id,
    items.identity_id,
    items.unique_key,
    items.type,
    items.content,
    items.created_at,
    array_remove(array_agg(tags.name), NULL) AS tags
FROM items
LEFT JOIN item_tags ON items.id = item_tags.item_id
LEFT JOIN tags ON item_tags.tag_id = tags.id
GROUP BY items.id;
