-- One-time links for resetting a forgotten password. Only a SHA-256 hash of each token is stored.
-- Rows are kept for a day after creation (used to limit how many reset emails an account gets),
-- then cleaned up automatically by the API.
CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    identity_id INTEGER NOT NULL REFERENCES identities(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP
);

CREATE INDEX password_reset_tokens_identity_idx ON password_reset_tokens (identity_id, created_at);
