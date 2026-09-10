CREATE TABLE item_tags (
    item_id INTEGER REFERENCES items(id) NOT NULL,
    tag_id INTEGER REFERENCES tags(id) NOT NULL,
    PRIMARY KEY (item_id, tag_id)
);
