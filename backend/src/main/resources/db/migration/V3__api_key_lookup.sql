-- HAM-15: keep the existing eight-character DISPLAY prefix for API parity,
-- but never use it to search: every generated key begins with "hamco_sk_".
-- A SHA-256 digest of the entire 256-bit random secret is safe to index and
-- narrows authentication to one BCrypt candidate. Old keys cannot be
-- backfilled because only their BCrypt hash remains; NULL permits a bounded
-- legacy fallback until they are rotated. Revocation still reads the row.
ALTER TABLE api_keys ADD COLUMN lookup_digest varchar(64);
CREATE UNIQUE INDEX ux_api_keys_lookup_digest ON api_keys(lookup_digest)
    WHERE lookup_digest IS NOT NULL;
