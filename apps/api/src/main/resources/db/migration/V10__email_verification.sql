ALTER TABLE local_credentials
 ADD COLUMN verification_hash TEXT UNIQUE,
 ADD COLUMN verification_expires_at TIMESTAMPTZ,
 ADD COLUMN verification_sent_at TIMESTAMPTZ;
