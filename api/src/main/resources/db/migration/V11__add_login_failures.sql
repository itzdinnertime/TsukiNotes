-- One row per failed login, used to limit repeated attempts by email and by IP address.
-- Rows older than a day are cleaned up automatically by the API.
CREATE TABLE login_failures (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    ip_address VARCHAR(64) NOT NULL,
    attempted_at TIMESTAMP NOT NULL
);

CREATE INDEX login_failures_email_idx ON login_failures (email, attempted_at);
CREATE INDEX login_failures_ip_idx ON login_failures (ip_address, attempted_at);
