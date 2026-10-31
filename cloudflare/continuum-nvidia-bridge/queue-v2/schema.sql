CREATE TABLE IF NOT EXISTS jarvis_cron_log (
 id INTEGER PRIMARY KEY AUTOINCREMENT, cron_time TEXT NOT NULL, action TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS continuum_nvidia_control (
 id INTEGER PRIMARY KEY CHECK(id=1),
 state TEXT NOT NULL CHECK(state IN ('running','disabled')),
 blocked_until_ms INTEGER NOT NULL DEFAULT 0,
 last_error TEXT
);
INSERT OR IGNORE INTO continuum_nvidia_control(id,state,blocked_until_ms) VALUES(1,'running',0);
CREATE TABLE IF NOT EXISTS continuum_nvidia_jobs (
 sequence INTEGER PRIMARY KEY AUTOINCREMENT,
 id TEXT NOT NULL UNIQUE,
 requested_at_ms INTEGER NOT NULL,
 requested_at_utc TEXT NOT NULL,
 topic TEXT NOT NULL,
 status TEXT NOT NULL,
 reserved_tokens INTEGER NOT NULL,
 completed_at_ms INTEGER,
 result_excerpt TEXT,
 provider_request_id TEXT
);
CREATE INDEX IF NOT EXISTS idx_nvidia_jobs_time ON continuum_nvidia_jobs(requested_at_ms);
CREATE INDEX IF NOT EXISTS idx_nvidia_jobs_state ON continuum_nvidia_jobs(status);
