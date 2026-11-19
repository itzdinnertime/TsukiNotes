-- The text inside uploaded text and code files, so the My files search can look inside them.
-- Kept in its own table (not in item_search_view) so large files don't bloat the materialized
-- view or slow down its refresh. Filled on upload; files uploaded before this migration are
-- filled in when the API starts (ItemTextBackfill).
CREATE TABLE item_texts (
    item_id INTEGER PRIMARY KEY REFERENCES items(id) ON DELETE CASCADE,
    body TEXT NOT NULL
);

-- Trigram index: lets "contains" searches (LOWER(body) LIKE '%word%') use an index instead of
-- reading every file's text. pg_trgm ships with PostgreSQL and is a trusted extension, so the
-- database owner can enable it.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX item_texts_body_trgm_idx ON item_texts USING GIN (LOWER(body) gin_trgm_ops);
