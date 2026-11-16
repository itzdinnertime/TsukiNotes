ALTER TABLE identities ADD COLUMN email VARCHAR(255);
ALTER TABLE identities ADD COLUMN password_hash VARCHAR(255);

CREATE UNIQUE INDEX identities_email_unique ON identities (LOWER(email));
