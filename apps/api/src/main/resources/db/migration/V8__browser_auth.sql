CREATE TABLE auth_identities (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), provider TEXT NOT NULL,
 issuer TEXT NOT NULL, external_subject TEXT NOT NULL,
 account_id UUID REFERENCES accounts(id) ON DELETE CASCADE,
 email TEXT NOT NULL DEFAULT '', email_verified BOOLEAN NOT NULL DEFAULT false,
 UNIQUE(provider, issuer, external_subject)
);
CREATE UNIQUE INDEX auth_identities_local_email ON auth_identities (lower(email)) WHERE provider = 'local';
CREATE TABLE local_credentials (
 identity_id UUID PRIMARY KEY REFERENCES auth_identities(id) ON DELETE CASCADE,
 password_hash TEXT NOT NULL
);
CREATE TABLE browser_sessions (
 id_hash TEXT PRIMARY KEY, identity_id UUID REFERENCES auth_identities(id) ON DELETE CASCADE,
 csrf_token TEXT NOT NULL, expires_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE oauth_transactions (
 state_hash TEXT PRIMARY KEY, session_hash TEXT NOT NULL REFERENCES browser_sessions(id_hash) ON DELETE CASCADE,
 provider TEXT NOT NULL, verifier TEXT NOT NULL, nonce TEXT NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL
);
