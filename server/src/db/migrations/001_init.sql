CREATE TABLE users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  nickname TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('user', 'admin')),
  created_at INTEGER NOT NULL
);

CREATE TABLE songs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  slug TEXT NOT NULL UNIQUE,
  title TEXT NOT NULL,
  artist TEXT NOT NULL,
  search_key TEXT NOT NULL,
  duration_ms INTEGER NOT NULL CHECK (duration_ms > 0),
  genre TEXT NOT NULL DEFAULT 'Інше',
  lyrics TEXT NOT NULL DEFAULT '',
  audio_source TEXT NOT NULL,
  cover_url TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER
);
CREATE UNIQUE INDEX idx_songs_updated ON songs (updated_at);
CREATE INDEX idx_songs_genre ON songs (genre) WHERE deleted_at IS NULL;

CREATE TABLE favorites (
  user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  song_id INTEGER NOT NULL REFERENCES songs (id) ON DELETE CASCADE,
  deleted INTEGER NOT NULL DEFAULT 0 CHECK (deleted IN (0, 1)),
  client_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (user_id, song_id)
);
CREATE UNIQUE INDEX idx_favorites_updated ON favorites (updated_at);

CREATE TABLE performances (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  client_id TEXT NOT NULL,
  song_id INTEGER NOT NULL REFERENCES songs (id) ON DELETE CASCADE,
  performed_at INTEGER NOT NULL,
  duration_ms INTEGER NOT NULL CHECK (duration_ms > 0),
  score INTEGER CHECK (score IS NULL OR score BETWEEN 0 AND 100),
  deleted INTEGER NOT NULL DEFAULT 0 CHECK (deleted IN (0, 1)),
  client_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  UNIQUE (user_id, client_id)
);
CREATE UNIQUE INDEX idx_performances_updated ON performances (updated_at);
CREATE INDEX idx_performances_song_score ON performances (song_id, score) WHERE deleted = 0 AND score IS NOT NULL;

CREATE TABLE integration_cache (
  cache_key TEXT PRIMARY KEY,
  payload TEXT NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE INDEX idx_cache_expires ON integration_cache (expires_at);
