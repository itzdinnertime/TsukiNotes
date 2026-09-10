CREATE TABLE items (
    id SERIAL PRIMARY KEY,
    identity_id INTEGER REFERENCES identities(id) NOT NULL,
    unique_key VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    content VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (identity_id, unique_key)
);
