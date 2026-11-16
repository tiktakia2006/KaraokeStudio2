ALTER TABLE users ADD COLUMN failed_logins INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

CREATE TABLE refresh_tokens (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL UNIQUE,
  family TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  revoked_at INTEGER
);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_family ON refresh_tokens (family);
CREATE INDEX idx_refresh_expires ON refresh_tokens (expires_at);

CREATE TABLE recordings (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  client_id TEXT NOT NULL,
  mime TEXT NOT NULL,
  size_bytes INTEGER NOT NULL CHECK (size_bytes > 0),
  sha256 TEXT NOT NULL,
  file_name TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL,
  UNIQUE (user_id, client_id)
);

CREATE INDEX idx_favorites_user_updated ON favorites (user_id, updated_at);
CREATE INDEX idx_performances_user_updated ON performances (user_id, updated_at);
CREATE INDEX idx_performances_user_client ON performances (user_id, client_id);
