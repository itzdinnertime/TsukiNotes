-- enums
CREATE TYPE item_type AS ENUM ('LINK','NOTE','IMAGE','FILE','PASSWORD');
CREATE TYPE user_role  AS ENUM ('OWNER','ADMIN','VIEWER');

-- users
CREATE TABLE users (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email         TEXT NOT NULL UNIQUE,
  username      TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  avatar_url    TEXT,
  role          user_role NOT NULL DEFAULT 'OWNER',
  is_active     BOOLEAN NOT NULL DEFAULT TRUE,
  last_login_at TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- channels
CREATE TABLE channels (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name        TEXT NOT NULL,
  description TEXT,
  icon        TEXT,
  color_hex   TEXT DEFAULT '#7F77DD',
  position    INTEGER NOT NULL DEFAULT 0,
  is_private  BOOLEAN NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- items
CREATE TABLE items (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  channel_id     UUID NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
  created_by     UUID NOT NULL REFERENCES users(id),
  type           item_type NOT NULL DEFAULT 'LINK',
  title          TEXT NOT NULL,
  content        TEXT,
  url            TEXT,
  unique_key     TEXT,
  thumbnail_url  TEXT,
  search_vector  TSVECTOR,
  is_pinned      BOOLEAN NOT NULL DEFAULT FALSE,
  is_archived    BOOLEAN NOT NULL DEFAULT FALSE,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_item_unique_key UNIQUE (unique_key)
);

-- tags
CREATE TABLE tags (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  item_id    UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
  value      TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_item_tag UNIQUE (item_id, value)
);

-- auth tokens
CREATE TABLE auth_tokens (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  access_token  TEXT NOT NULL,
  refresh_token TEXT NOT NULL UNIQUE,
  expires_at    TIMESTAMPTZ NOT NULL,
  is_revoked    BOOLEAN NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- item previews
CREATE TABLE item_previews (
  item_id     UUID PRIMARY KEY REFERENCES items(id) ON DELETE CASCADE,
  og_title    TEXT,
  og_desc     TEXT,
  og_image    TEXT,
  favicon_url TEXT,
  fetched_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- indexes
CREATE INDEX idx_items_channel    ON items(channel_id);
CREATE INDEX idx_items_unique_key ON items(unique_key);
CREATE INDEX idx_items_updated_at ON items(updated_at);  -- for Rust polling
CREATE INDEX idx_items_search_vec ON items USING GIN(search_vector);
CREATE INDEX idx_tags_value       ON tags  USING GIN(to_tsvector('simple', value));
CREATE INDEX idx_tags_item        ON tags(item_id);

-- auto-update tsvector on title + content change
CREATE OR REPLACE FUNCTION update_search_vector() RETURNS TRIGGER AS $$
BEGIN
  NEW.search_vector :=
    setweight(to_tsvector('english', COALESCE(NEW.title,'')),   'A') ||
    setweight(to_tsvector('english', COALESCE(NEW.content,'')), 'B') ||
    setweight(to_tsvector('english', COALESCE(NEW.url,'')),     'C');
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_items_search_vector
  BEFORE INSERT OR UPDATE ON items
  FOR EACH ROW EXECUTE FUNCTION update_search_vector();