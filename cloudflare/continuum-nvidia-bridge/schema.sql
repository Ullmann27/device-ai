-- Additive migration: preserves all previous D1 rows/tables.
CREATE TABLE IF NOT EXISTS jarvis_cron_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  cron_time TEXT NOT NULL,
  action TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS continuum_nvidia_runs (
  id TEXT PRIMARY KEY NOT NULL,
  requested_at_ms INTEGER NOT NULL,
  requested_at_utc TEXT NOT NULL,
  topic TEXT NOT NULL,
  status TEXT NOT NULL,
  completed_at_ms INTEGER,
  model TEXT,
  result_excerpt TEXT,
  provider_request_id TEXT
);
CREATE INDEX IF NOT EXISTS idx_continuum_nvidia_runs_time ON continuum_nvidia_runs(requested_at_ms);
